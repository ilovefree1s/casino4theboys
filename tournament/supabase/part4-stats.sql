-- ================= Part 4: game stats =================
-- Which games each player won and lost on, per week. Everyone's unlock
-- once the week is over; during the week a player sees only their own.
begin;

do $guard$
begin
  if exists (select 1 from pg_class c join pg_namespace s on s.oid = c.relnamespace
              where s.nspname = 'public' and c.relname = 'c4tb_stats'
                and coalesce(obj_description(c.oid, 'pg_class'), '') <> '4 The Boys tournament') then
    raise exception 'Stopped: c4tb_stats already exists and is not ours';
  end if;
end $guard$;

create table if not exists public.c4tb_stats (
  player_id  uuid not null references public.c4tb_players (id) on delete cascade,
  week       date not null,
  game       text not null check (char_length(game) between 1 and 40),
  net        bigint not null default 0,
  best       bigint not null default 0,
  updated_at timestamptz not null default now(),
  primary key (player_id, week, game)
);
alter table public.c4tb_stats enable row level security;
revoke all on public.c4tb_stats from anon, authenticated;
comment on table public.c4tb_stats is '4 The Boys tournament';

create or replace function public.c4tb_save_stats(p_name text, p_token text, p_week date, p_stats jsonb)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  pid uuid := public.c4tb_auth(p_name, p_token);
  wk date := public.c4tb_week_of(public.c4tb_today());
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  if p_week is distinct from wk then return jsonb_build_object('ok', false, 'error', 'rollover'); end if;
  if jsonb_typeof(p_stats) <> 'object' or (select count(*) from jsonb_object_keys(p_stats)) > 40 then
    return jsonb_build_object('ok', false, 'error', 'bad_stats');
  end if;
  insert into public.c4tb_stats (player_id, week, game, net, best)
  select pid, wk, left(key, 40), coalesce((value->>'net')::bigint, 0), greatest(coalesce((value->>'best')::bigint, 0), 0)
    from jsonb_each(p_stats)
   where jsonb_typeof(value) = 'object' and char_length(key) between 1 and 40
  on conflict (player_id, week, game) do update
    set net = excluded.net, best = greatest(public.c4tb_stats.best, excluded.best), updated_at = now();
  return jsonb_build_object('ok', true);
end $$;

-- A finished week: everyone. The week in progress: only the asker.
create or replace function public.c4tb_get_stats(p_name text, p_token text, p_week date)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  pid uuid := public.c4tb_auth(p_name, p_token);
  cur date := public.c4tb_week_of(public.c4tb_today());
  done boolean := p_week < cur;
  rows jsonb;
begin
  select coalesce(jsonb_agg(jsonb_build_object('name', p.name, 'game', s.game, 'net', s.net, 'best', s.best)
                            order by p.name, s.game), '[]')
    into rows
    from public.c4tb_stats s join public.c4tb_players p on p.id = s.player_id
   where s.week = p_week and (done or s.player_id = pid);
  return jsonb_build_object('ok', true, 'week', p_week, 'final', done, 'rows', rows);
end $$;

grant execute on function public.c4tb_save_stats(text, text, date, jsonb),
                          public.c4tb_get_stats(text, text, date) to anon, authenticated;
comment on function public.c4tb_save_stats(text, text, date, jsonb) is '4 The Boys tournament';
comment on function public.c4tb_get_stats(text, text, date) is '4 The Boys tournament';

commit;
