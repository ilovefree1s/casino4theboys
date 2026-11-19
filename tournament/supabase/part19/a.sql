-- ================= Part 19: giveaways on the message board =================
-- A post can carry a prize; the first N players to tap CLAIM get it.
begin;

alter table public.c4tb_messages add column if not exists prize_kind   text;     -- 'chips' or 'x2'
alter table public.c4tb_messages add column if not exists prize_amount bigint;   -- chips, or how many 2x tokens
alter table public.c4tb_messages add column if not exists prize_max    int not null default 1;  -- how many winners

create table if not exists public.c4tb_claims (
  message_id bigint not null references public.c4tb_messages (id) on delete cascade,
  player_id  uuid   not null references public.c4tb_players (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (message_id, player_id)
);
alter table public.c4tb_claims enable row level security;
revoke all on public.c4tb_claims from anon, authenticated;
comment on table public.c4tb_claims is '4 The Boys tournament';

create or replace function public.c4tb_msgs()
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', m.id, 'body', m.body,
           's', greatest(0, extract(epoch from now() - m.created_at))::int,
           'prize', case when m.prize_kind is null then null else jsonb_build_object(
              'kind', m.prize_kind, 'amount', m.prize_amount, 'max', m.prize_max,
              'winners', (select coalesce(jsonb_agg(p.name order by c.created_at), '[]')
                            from public.c4tb_claims c join public.c4tb_players p on p.id = c.player_id
                           where c.message_id = m.id)) end)
         order by m.id desc), '[]')
    from (select * from public.c4tb_messages order by id desc limit 50) m;
$$;

create or replace function public.c4tb_msg_claim(p_name text, p_token text, p_id bigint)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare pid uuid := public.c4tb_auth(p_name, p_token); m public.c4tb_messages; n int;
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  select * into m from public.c4tb_messages where id = p_id for update;
  if m.id is null or m.prize_kind is null then return jsonb_build_object('ok', false, 'error', 'gone'); end if;
  if exists (select 1 from public.c4tb_claims where message_id = p_id and player_id = pid) then
    return jsonb_build_object('ok', false, 'error', 'have');
  end if;
  select count(*) into n from public.c4tb_claims where message_id = p_id;
  if n >= m.prize_max then return jsonb_build_object('ok', false, 'error', 'gone'); end if;
  insert into public.c4tb_claims (message_id, player_id) values (p_id, pid);
  return jsonb_build_object('ok', true, 'kind', m.prize_kind, 'amount', m.prize_amount);
end $$;

-- Posting one. Run from the SQL editor only (players can't call it):
--   select public.c4tb_give('First one to tap this gets a free 1M', 'chips', 1000000, 1);
--   select public.c4tb_give('First 3 get a 2x token', 'x2', 1, 3);
create or replace function public.c4tb_give(p_body text, p_kind text, p_amount bigint, p_winners int default 1)
returns bigint language plpgsql volatile security definer set search_path = public as $$
declare new_id bigint;
begin
  -- the game's own connection can never post one, whatever the grants say
  if session_user = 'authenticator' then raise exception 'not allowed'; end if;
  if p_kind not in ('chips', 'x2') then raise exception 'kind must be chips or x2'; end if;
  if coalesce(p_amount, 0) <= 0 then raise exception 'amount must be more than 0'; end if;
  insert into public.c4tb_messages (body, prize_kind, prize_amount, prize_max)
  values (btrim(p_body), p_kind, p_amount, greatest(coalesce(p_winners, 1), 1))
  returning id into new_id;
  return new_id;
end $$;
revoke all on function public.c4tb_give(text, text, bigint, int) from public, anon, authenticated;

grant execute on function public.c4tb_msgs(), public.c4tb_msg_claim(text, text, bigint) to anon, authenticated;
comment on function public.c4tb_msg_claim(text, text, bigint) is '4 The Boys tournament';
comment on function public.c4tb_give(text, text, bigint, int) is '4 The Boys tournament';

commit;
