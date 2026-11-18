-- ================= Part 11: walk-up stats =================
-- Per player, per week: how many people walked up, how many they backed,
-- and how many of those walk-ups were real or scams. Everyone's, live.
begin;

do $guard$
begin
  if exists (select 1 from pg_class c join pg_namespace s on s.oid = c.relnamespace
              where s.nspname = 'public' and c.relname = 'c4tb_walkups'
                and coalesce(obj_description(c.oid, 'pg_class'), '') <> '4 The Boys tournament') then
    raise exception 'Stopped: c4tb_walkups already exists and is not ours';
  end if;
end $guard$;

create table if not exists public.c4tb_walkups (
  player_id  uuid not null references public.c4tb_players (id) on delete cascade,
  week       date not null,
  shown      int not null default 0,
  backed     int not null default 0,
  real       int not null default 0,
  scams      int not null default 0,
  updated_at timestamptz not null default now(),
  primary key (player_id, week)
);
alter table public.c4tb_walkups enable row level security;
revoke all on public.c4tb_walkups from anon, authenticated;
comment on table public.c4tb_walkups is '4 The Boys tournament';

create or replace function public.c4tb_save_walkups(p_name text, p_token text, p_week date,
                                                    p_shown int, p_backed int, p_real int, p_scams int)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  pid uuid := public.c4tb_auth(p_name, p_token);
  wk date := public.c4tb_week_of(public.c4tb_today());
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  if p_week is distinct from wk then return jsonb_build_object('ok', false, 'error', 'rollover'); end if;
  insert into public.c4tb_walkups (player_id, week, shown, backed, real, scams)
  values (pid, wk, least(greatest(coalesce(p_shown, 0), 0), 100000), least(greatest(coalesce(p_backed, 0), 0), 100000),
          least(greatest(coalesce(p_real, 0), 0), 100000), least(greatest(coalesce(p_scams, 0), 0), 100000))
  on conflict (player_id, week) do update
    set shown = greatest(public.c4tb_walkups.shown, excluded.shown),
        backed = greatest(public.c4tb_walkups.backed, excluded.backed),
        real = greatest(public.c4tb_walkups.real, excluded.real),
        scams = greatest(public.c4tb_walkups.scams, excluded.scams),
        updated_at = now();
  return jsonb_build_object('ok', true);
end $$;

-- Everyone's, live, for the board.
create or replace function public.c4tb_walkup_board(p_week date)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('name', p.name, 'shown', w.shown, 'backed', w.backed,
                                               'real', w.real, 'scams', w.scams)
                            order by w.shown desc, p.name), '[]')
    from public.c4tb_walkups w join public.c4tb_players p on p.id = w.player_id
   where w.week = p_week;
$$;

grant execute on function public.c4tb_save_walkups(text, text, date, int, int, int, int),
                          public.c4tb_walkup_board(date) to anon, authenticated;
comment on function public.c4tb_save_walkups(text, text, date, int, int, int, int) is '4 The Boys tournament';
comment on function public.c4tb_walkup_board(date) is '4 The Boys tournament';

commit;
