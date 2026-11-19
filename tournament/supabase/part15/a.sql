-- ================= Part 15: the points tournament =================
-- From the week of 2026-11-22: every game has its own $10,000 wallet per
-- player, fresh every day, with free rebuys. Each game is worth 1-3 points a
-- day; whoever has the most on it at midnight takes them. Most points wins
-- the week, most money earned breaks a tie.
begin;

do $guard$
begin
  if exists (select 1 from pg_class c join pg_namespace s on s.oid = c.relnamespace
              where s.nspname = 'public' and c.relname = 'c4tb_game_wallets'
                and coalesce(obj_description(c.oid, 'pg_class'), '') <> '4 The Boys tournament') then
    raise exception 'Stopped: c4tb_game_wallets already exists and is not ours';
  end if;
end $guard$;

create table if not exists public.c4tb_game_wallets (
  player_id  uuid not null references public.c4tb_players (id) on delete cascade,
  week       date not null,
  day        date not null,
  game       text not null check (char_length(game) between 1 and 40),
  cash       bigint not null default 10000,
  updated_at timestamptz not null default now(),
  primary key (player_id, day, game)
);
create index if not exists c4tb_game_wallets_day on public.c4tb_game_wallets (day, game);
create index if not exists c4tb_game_wallets_week on public.c4tb_game_wallets (week);
alter table public.c4tb_game_wallets enable row level security;
revoke all on public.c4tb_game_wallets from anon, authenticated;
comment on table public.c4tb_game_wallets is '4 The Boys tournament';

-- A game's points for a day: 1, 2 or 3, the same sum the phones do.
create or replace function public.c4tb_game_pts(p_day date, p_game text)
returns int language sql immutable as $$
  select 1 + (coalesce(sum(ascii(substr(s, i, 1)) * i), 0) % 3)::int
    from (select p_day::text || p_game as s) x, generate_series(1, length(x.s)) i;
$$;

create or replace function public.c4tb_game_sync(p_name text, p_token text, p_week date, p_day date, p_game text, p_cash bigint)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  pid uuid := public.c4tb_auth(p_name, p_token);
  d date := public.c4tb_today();
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  if p_day is distinct from d or p_week is distinct from public.c4tb_week_of(d) then
    return jsonb_build_object('ok', false, 'error', 'rollover', 'day', d);
  end if;
  if coalesce(char_length(p_game), 0) not between 1 and 40 then return jsonb_build_object('ok', false, 'error', 'game'); end if;
  insert into public.c4tb_game_wallets (player_id, week, day, game, cash)
  values (pid, p_week, d, p_game, least(greatest(coalesce(p_cash, 0), 0), 10000000000000))
  on conflict (player_id, day, game) do update set cash = excluded.cash, updated_at = now();
  return jsonb_build_object('ok', true, 'day', d);
end $$;

-- Today's balances on this player's own games, for a phone that has none.
create or replace function public.c4tb_game_mine(p_name text, p_token text)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare pid uuid := public.c4tb_auth(p_name, p_token);
begin
  if pid is null then return '[]'::jsonb; end if;
  return (select coalesce(jsonb_agg(jsonb_build_object('game', game, 'cash', cash)), '[]')
            from public.c4tb_game_wallets where player_id = pid and day = public.c4tb_today());
end $$;

-- Who's on top of each game on a day (today: live; a past day: final).
create or replace function public.c4tb_game_leaders(p_day date)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('game', t.game, 'name', p.name, 'cash', t.cash,
                                               'pts', public.c4tb_game_pts(p_day, t.game)) order by t.game), '[]')
    from (select distinct on (game) game, player_id, cash
            from public.c4tb_game_wallets where day = p_day and cash > 10000
           order by game, cash desc, updated_at asc) t
    join public.c4tb_players p on p.id = t.player_id;
$$;

-- The week's points: every finished day's game winners, plus what each player earned all week.
create or replace function public.c4tb_points_board(p_week date)
returns jsonb language sql stable security definer set search_path = public as $$
  with w as (
    select distinct on (day, game) day, game, player_id, public.c4tb_game_pts(day, game) as pts
      from public.c4tb_game_wallets
     where week = p_week and day < public.c4tb_today() and cash > 10000   -- you have to be up to take a game
     order by day, game, cash desc, updated_at asc
  ), pts as (
    select player_id, sum(pts)::int as points, count(*)::int as wins from w group by player_id
  ), earned as (
    select player_id, sum(cash - 10000)::bigint as money from public.c4tb_game_wallets
     where week = p_week group by player_id
  )
  select coalesce(jsonb_agg(jsonb_build_object('name', p.name, 'points', coalesce(pts.points, 0),
                                               'wins', coalesce(pts.wins, 0), 'money', e.money)
                            order by coalesce(pts.points, 0) desc, e.money desc, p.name), '[]')
    from earned e join public.c4tb_players p on p.id = e.player_id
    left join pts on pts.player_id = e.player_id;
$$;

grant execute on function public.c4tb_game_sync(text, text, date, date, text, bigint),
                          public.c4tb_game_mine(text, text),
                          public.c4tb_game_leaders(date),
                          public.c4tb_points_board(date),
                          public.c4tb_game_pts(date, text) to anon, authenticated;
comment on function public.c4tb_game_pts(date, text) is '4 The Boys tournament';
comment on function public.c4tb_game_sync(text, text, date, date, text, bigint) is '4 The Boys tournament';
comment on function public.c4tb_game_mine(text, text) is '4 The Boys tournament';
comment on function public.c4tb_game_leaders(date) is '4 The Boys tournament';
comment on function public.c4tb_points_board(date) is '4 The Boys tournament';

commit;
