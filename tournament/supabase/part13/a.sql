-- ================= Part 13: duels =================
-- One player challenges another to 10 hands of the same game for a pot each
-- side puts up. Higher profit over their 10 takes both; a tie hands both back.
-- Open until midnight: a player who finished their 10 against one who didn't
-- wins; neither finished, both get their money back. Money moves on the
-- phones (the wallet lives there): each side claims its debit and its credit
-- once, here, and applies what it is told.
begin;

do $guard$
begin
  if exists (select 1 from pg_class c join pg_namespace s on s.oid = c.relnamespace
              where s.nspname = 'public' and c.relname = 'c4tb_duels'
                and coalesce(obj_description(c.oid, 'pg_class'), '') <> '4 The Boys tournament') then
    raise exception 'Stopped: c4tb_duels already exists and is not ours';
  end if;
end $guard$;

create table if not exists public.c4tb_duels (
  id          bigserial primary key,
  day         date not null,
  challenger  uuid not null references public.c4tb_players (id) on delete cascade,
  opponent    uuid not null references public.c4tb_players (id) on delete cascade,
  game        text not null check (char_length(game) between 1 and 40),
  pot         bigint not null check (pot > 0),
  status      text not null default 'pending',   -- pending, active, declined, done, expired
  c_hands     int not null default 0,
  c_net       bigint not null default 0,
  o_hands     int not null default 0,
  o_net       bigint not null default 0,
  winner      uuid,
  c_debited   boolean not null default false,
  o_debited   boolean not null default false,
  c_credited  boolean not null default false,
  o_credited  boolean not null default false,
  created_at  timestamptz not null default now(),
  settled_at  timestamptz
);
create index if not exists c4tb_duels_day on public.c4tb_duels (day);
alter table public.c4tb_duels enable row level security;
revoke all on public.c4tb_duels from anon, authenticated;
comment on table public.c4tb_duels is '4 The Boys tournament';

-- Settle whatever midnight has closed.
create or replace function public.c4tb_duel_expire()
returns void language plpgsql volatile security definer set search_path = public as $$
declare d date := public.c4tb_today();
begin
  update public.c4tb_duels set status = 'expired', settled_at = now()
   where status = 'pending' and day < d;
  update public.c4tb_duels
     set status = case when (c_hands >= 10) <> (o_hands >= 10) then 'done' else 'expired' end,
         winner = case when c_hands >= 10 and o_hands < 10 then challenger
                       when o_hands >= 10 and c_hands < 10 then opponent end,
         settled_at = now()
   where status = 'active' and day < d;
end $$;

create or replace function public.c4tb_duel_row(x public.c4tb_duels)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object('id', x.id, 'day', x.day, 'game', x.game, 'pot', x.pot, 'status', x.status,
    'challenger', (select name from public.c4tb_players where id = x.challenger),
    'opponent', (select name from public.c4tb_players where id = x.opponent),
    'c_hands', x.c_hands, 'c_net', x.c_net, 'o_hands', x.o_hands, 'o_net', x.o_net,
    'winner', (select name from public.c4tb_players where id = x.winner),
    'c_debited', x.c_debited, 'o_debited', x.o_debited, 'c_credited', x.c_credited, 'o_credited', x.o_credited);
$$;

create or replace function public.c4tb_duel_challenge(p_name text, p_token text, p_opponent text, p_game text, p_pot bigint)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  pid uuid := public.c4tb_auth(p_name, p_token);
  oid uuid; d date := public.c4tb_today();
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  perform public.c4tb_duel_expire();
  select id into oid from public.c4tb_players where lower(name) = lower(btrim(p_opponent));
  if oid is null or oid = pid then return jsonb_build_object('ok', false, 'error', 'no_player'); end if;
  if coalesce(p_pot, 0) < 100 or p_pot > 1000000000000 then return jsonb_build_object('ok', false, 'error', 'bad_pot'); end if;
  if exists (select 1 from public.c4tb_duels where status in ('pending', 'active')
              and (challenger in (pid, oid) or opponent in (pid, oid))) then
    return jsonb_build_object('ok', false, 'error', 'busy');
  end if;
  insert into public.c4tb_duels (day, challenger, opponent, game, pot)
  values (d, pid, oid, left(p_game, 40), p_pot);
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.c4tb_duel_answer(p_name text, p_token text, p_id bigint, p_accept boolean)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare pid uuid := public.c4tb_auth(p_name, p_token); x public.c4tb_duels;
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  perform public.c4tb_duel_expire();
  update public.c4tb_duels set status = case when p_accept then 'active' else 'declined' end,
         settled_at = case when p_accept then null else now() end
   where id = p_id and opponent = pid and status = 'pending'
  returning * into x;
  if x.id is null then return jsonb_build_object('ok', false, 'error', 'gone'); end if;
  return jsonb_build_object('ok', true, 'duel', public.c4tb_duel_row(x));
end $$;

create or replace function public.c4tb_duel_progress(p_name text, p_token text, p_id bigint, p_hands int, p_net bigint)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare pid uuid := public.c4tb_auth(p_name, p_token); x public.c4tb_duels; h int := least(greatest(coalesce(p_hands, 0), 0), 10);
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  perform public.c4tb_duel_expire();
  select * into x from public.c4tb_duels where id = p_id and status = 'active' for update;
  if x.id is null then return jsonb_build_object('ok', false, 'error', 'gone'); end if;
  if x.challenger = pid and h >= x.c_hands then
    update public.c4tb_duels set c_hands = h, c_net = coalesce(p_net, 0) where id = p_id returning * into x;
  elsif x.opponent = pid and h >= x.o_hands then
    update public.c4tb_duels set o_hands = h, o_net = coalesce(p_net, 0) where id = p_id returning * into x;
  end if;
  if x.c_hands >= 10 and x.o_hands >= 10 then
    update public.c4tb_duels set status = 'done', settled_at = now(),
           winner = case when c_net > o_net then challenger when o_net > c_net then opponent end
     where id = p_id returning * into x;
  end if;
  return jsonb_build_object('ok', true, 'duel', public.c4tb_duel_row(x));
end $$;

-- Claim this side's money move, once. Debit: the pot goes in. Credit: what
-- comes back (both pots to the winner, your own on a tie or a wash), less
-- the pot if it was never put in.
create or replace function public.c4tb_duel_claim(p_name text, p_token text, p_id bigint, p_kind text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  pid uuid := public.c4tb_auth(p_name, p_token); x public.c4tb_duels;
  mine_c boolean; deb boolean; cred boolean; back bigint;
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  perform public.c4tb_duel_expire();
  select * into x from public.c4tb_duels where id = p_id and pid in (challenger, opponent) for update;
  if x.id is null then return jsonb_build_object('ok', false, 'error', 'gone'); end if;
  mine_c := x.challenger = pid;
  deb := case when mine_c then x.c_debited else x.o_debited end;
  cred := case when mine_c then x.c_credited else x.o_credited end;
  if p_kind = 'debit' then
    if deb or cred or x.status not in ('active', 'done', 'expired') then return jsonb_build_object('ok', false); end if;
    if mine_c then update public.c4tb_duels set c_debited = true where id = p_id;
    else update public.c4tb_duels set o_debited = true where id = p_id; end if;
    return jsonb_build_object('ok', true, 'amount', -x.pot);
  end if;
  if cred or x.status not in ('done', 'expired') then return jsonb_build_object('ok', false); end if;
  back := case when x.status = 'expired' or x.winner is null then x.pot
               when x.winner = pid then x.pot * 2 else 0 end;
  if not deb then back := back - x.pot; end if;
  if mine_c then update public.c4tb_duels set c_debited = true, c_credited = true where id = p_id;
  else update public.c4tb_duels set o_debited = true, o_credited = true where id = p_id; end if;
  return jsonb_build_object('ok', true, 'amount', back, 'duel', public.c4tb_duel_row(x));
end $$;

-- Mine: today's, plus any older one I haven't been paid out on.
create or replace function public.c4tb_duels_mine(p_name text, p_token text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare pid uuid := public.c4tb_auth(p_name, p_token);
begin
  if pid is null then return '[]'::jsonb; end if;
  perform public.c4tb_duel_expire();
  return (select coalesce(jsonb_agg(public.c4tb_duel_row(x) order by x.id), '[]')
            from public.c4tb_duels x
           where pid in (x.challenger, x.opponent)
             and (x.day = public.c4tb_today()
                  or (x.challenger = pid and x.c_debited and not x.c_credited)
                  or (x.opponent = pid and x.o_debited and not x.o_credited)));
end $$;

-- Everyone's today, for the board.
create or replace function public.c4tb_duels_today()
returns jsonb language plpgsql volatile security definer set search_path = public as $$
begin
  perform public.c4tb_duel_expire();
  return (select coalesce(jsonb_agg(public.c4tb_duel_row(x) order by x.id desc), '[]')
            from public.c4tb_duels x where x.day = public.c4tb_today() and x.status <> 'declined');
end $$;

grant execute on function public.c4tb_duel_challenge(text, text, text, text, bigint),
                          public.c4tb_duel_answer(text, text, bigint, boolean),
                          public.c4tb_duel_progress(text, text, bigint, int, bigint),
                          public.c4tb_duel_claim(text, text, bigint, text),
                          public.c4tb_duels_mine(text, text),
                          public.c4tb_duels_today() to anon, authenticated;
revoke all on function public.c4tb_duel_expire() from public, anon, authenticated;
revoke all on function public.c4tb_duel_row(public.c4tb_duels) from public, anon, authenticated;
comment on function public.c4tb_duel_challenge(text, text, text, text, bigint) is '4 The Boys tournament';
comment on function public.c4tb_duel_answer(text, text, bigint, boolean) is '4 The Boys tournament';
comment on function public.c4tb_duel_progress(text, text, bigint, int, bigint) is '4 The Boys tournament';
comment on function public.c4tb_duel_claim(text, text, bigint, text) is '4 The Boys tournament';
comment on function public.c4tb_duels_mine(text, text) is '4 The Boys tournament';
comment on function public.c4tb_duels_today() is '4 The Boys tournament';
comment on function public.c4tb_duel_expire() is '4 The Boys tournament';
comment on function public.c4tb_duel_row(public.c4tb_duels) is '4 The Boys tournament';

commit;
