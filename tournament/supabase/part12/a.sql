-- ================= Part 12: shout-outs =================
-- A big hit (50x the bet or more) is announced at everybody else's table.
begin;

do $guard$
begin
  if exists (select 1 from pg_class c join pg_namespace s on s.oid = c.relnamespace
              where s.nspname = 'public' and c.relname = 'c4tb_shouts'
                and coalesce(obj_description(c.oid, 'pg_class'), '') <> '4 The Boys tournament') then
    raise exception 'Stopped: c4tb_shouts already exists and is not ours';
  end if;
end $guard$;

create table if not exists public.c4tb_shouts (
  id         bigserial primary key,
  player_id  uuid not null references public.c4tb_players (id) on delete cascade,
  at         timestamptz not null default now(),
  game       text not null check (char_length(game) between 1 and 40),
  amount     bigint not null check (amount > 0),
  mult       int not null check (mult >= 1)
);
create index if not exists c4tb_shouts_at on public.c4tb_shouts (at);
alter table public.c4tb_shouts enable row level security;
revoke all on public.c4tb_shouts from anon, authenticated;
comment on table public.c4tb_shouts is '4 The Boys tournament';

create or replace function public.c4tb_shout(p_name text, p_token text, p_game text, p_amount bigint, p_mult int)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  pid uuid := public.c4tb_auth(p_name, p_token);
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  if coalesce(p_amount, 0) <= 0 or coalesce(p_mult, 0) < 50 then return jsonb_build_object('ok', false, 'error', 'small'); end if;
  -- one shout every 20 seconds a player, at most
  if exists (select 1 from public.c4tb_shouts where player_id = pid and at > now() - interval '20 seconds') then
    return jsonb_build_object('ok', false, 'error', 'soon');
  end if;
  insert into public.c4tb_shouts (player_id, game, amount, mult)
  values (pid, left(coalesce(p_game, 'a table'), 40), least(p_amount, 1000000000000), least(p_mult, 1000000));
  delete from public.c4tb_shouts where at < now() - interval '2 days';
  return jsonb_build_object('ok', true);
end $$;

-- Everything newer than [p_after] from the last 15 minutes, oldest first.
create or replace function public.c4tb_shouts_since(p_after bigint)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', s.id, 'name', p.name, 'game', s.game,
                                               'amount', s.amount, 'mult', s.mult) order by s.id), '[]')
    from (select * from public.c4tb_shouts
           where id > coalesce(p_after, 0) and at > now() - interval '15 minutes'
           order by id limit 20) s
    join public.c4tb_players p on p.id = s.player_id;
$$;

grant execute on function public.c4tb_shout(text, text, text, bigint, int),
                          public.c4tb_shouts_since(bigint) to anon, authenticated;
comment on function public.c4tb_shout(text, text, text, bigint, int) is '4 The Boys tournament';
comment on function public.c4tb_shouts_since(bigint) is '4 The Boys tournament';

commit;
