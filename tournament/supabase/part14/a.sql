-- ================= Part 14: duels scored by return =================
-- A duel is won on profit for every dollar bet over the 10 hands, so a
-- $25 player and a $1M player are on the same footing.
begin;
alter table public.c4tb_duels add column if not exists c_bet bigint not null default 0;
alter table public.c4tb_duels add column if not exists o_bet bigint not null default 0;

create or replace function public.c4tb_duel_ret(net bigint, bet bigint)
returns numeric language sql immutable as $$
  select case when coalesce(bet, 0) > 0 then net::numeric / bet else 0 end;
$$;

create or replace function public.c4tb_duel_row(x public.c4tb_duels)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object('id', x.id, 'day', x.day, 'game', x.game, 'pot', x.pot, 'status', x.status,
    'challenger', (select name from public.c4tb_players where id = x.challenger),
    'opponent', (select name from public.c4tb_players where id = x.opponent),
    'c_hands', x.c_hands, 'c_net', x.c_net, 'c_bet', x.c_bet, 'o_hands', x.o_hands, 'o_net', x.o_net, 'o_bet', x.o_bet,
    'winner', (select name from public.c4tb_players where id = x.winner),
    'c_debited', x.c_debited, 'o_debited', x.o_debited, 'c_credited', x.c_credited, 'o_credited', x.o_credited);
$$;

create or replace function public.c4tb_duel_progress2(p_name text, p_token text, p_id bigint, p_hands int, p_net bigint, p_bet bigint)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare pid uuid := public.c4tb_auth(p_name, p_token); x public.c4tb_duels; h int := least(greatest(coalesce(p_hands, 0), 0), 10);
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  perform public.c4tb_duel_expire();
  select * into x from public.c4tb_duels where id = p_id and status = 'active' for update;
  if x.id is null then return jsonb_build_object('ok', false, 'error', 'gone'); end if;
  if x.challenger = pid and h >= x.c_hands then
    update public.c4tb_duels set c_hands = h, c_net = coalesce(p_net, 0), c_bet = greatest(coalesce(p_bet, 0), 0)
     where id = p_id returning * into x;
  elsif x.opponent = pid and h >= x.o_hands then
    update public.c4tb_duels set o_hands = h, o_net = coalesce(p_net, 0), o_bet = greatest(coalesce(p_bet, 0), 0)
     where id = p_id returning * into x;
  end if;
  if x.c_hands >= 10 and x.o_hands >= 10 then
    update public.c4tb_duels set status = 'done', settled_at = now(),
           winner = case when public.c4tb_duel_ret(c_net, c_bet) > public.c4tb_duel_ret(o_net, o_bet) then challenger
                         when public.c4tb_duel_ret(o_net, o_bet) > public.c4tb_duel_ret(c_net, c_bet) then opponent end
     where id = p_id returning * into x;
  end if;
  return jsonb_build_object('ok', true, 'duel', public.c4tb_duel_row(x));
end $$;

grant execute on function public.c4tb_duel_progress2(text, text, bigint, int, bigint, bigint) to anon, authenticated;
comment on function public.c4tb_duel_progress2(text, text, bigint, int, bigint, bigint) is '4 The Boys tournament';
comment on function public.c4tb_duel_ret(bigint, bigint) is '4 The Boys tournament';
commit;
