-- 4 The Boys · weekly tournament
-- Paste this whole file into Supabase → SQL Editor → New query → Run.
-- Safe to run again: it replaces the functions and leaves existing data alone.
--
-- Weeks run Sunday 12:00 AM to Saturday 11:59 PM, Eastern time.
-- Everyone starts the week with $5,000. Up to 3 markers a week
-- ($5,000 in, $7,500 owed). Bust with all 3 signed and you're out.
-- Score = cash minus what is still owed on markers.
--
-- The tables are locked (row level security, no policies). The site only
-- talks to the functions at the bottom, and each one checks the player's
-- name and the device token that claimed it.

-- ---------- settings ----------
create table if not exists public.tt_config (
  id          int primary key default 1 check (id = 1),
  first_week  date   not null,            -- the Sunday the first tournament starts
  start_cash  bigint not null default 5000,
  max_markers int    not null default 3,
  marker_owed bigint not null default 7500,
  tz          text   not null default 'America/New_York'
);
insert into public.tt_config (first_week) values ('2026-11-08')
  on conflict (id) do nothing;

-- ---------- players: a name, locked to the phone that claimed it ----------
create table if not exists public.tt_players (
  id         uuid primary key default gen_random_uuid(),
  name       text not null check (char_length(name) between 2 and 16),
  token_hash text not null,
  created_at timestamptz not null default now()
);
create unique index if not exists tt_players_name_ci on public.tt_players (lower(name));

-- ---------- one row per player per week ----------
create table if not exists public.tt_entries (
  player_id  uuid not null references public.tt_players (id) on delete cascade,
  week       date not null,               -- that week's Sunday
  cash       bigint  not null,
  owed       bigint  not null default 0,
  markers    int     not null default 0,
  is_out     boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (player_id, week)
);

-- ---------- one row per player per day: where the day started and where it is ----------
create table if not exists public.tt_days (
  player_id uuid not null references public.tt_players (id) on delete cascade,
  week      date   not null,
  day       date   not null,
  start_net bigint not null,
  net       bigint not null,
  primary key (player_id, day)
);
create index if not exists tt_days_week on public.tt_days (week, day);

alter table public.tt_config  enable row level security;
alter table public.tt_players enable row level security;
alter table public.tt_entries enable row level security;
alter table public.tt_days    enable row level security;
revoke all on public.tt_config, public.tt_players, public.tt_entries, public.tt_days from anon, authenticated;

-- ---------- helpers ----------
create or replace function public.tt_today()
returns date language sql stable as $$
  select (now() at time zone (select tz from public.tt_config where id = 1))::date
$$;

create or replace function public.tt_week_of(d date)
returns date language sql immutable as $$
  select d - extract(dow from d)::int   -- back to Sunday
$$;

create or replace function public.tt_hash(p_token text)
returns text language sql immutable as $$
  select encode(sha256(convert_to(p_token, 'UTF8')), 'hex')
$$;

create or replace function public.tt_auth(p_name text, p_token text)
returns uuid language sql stable security definer set search_path = public as $$
  select id from public.tt_players
   where lower(name) = lower(btrim(p_name)) and token_hash = public.tt_hash(p_token)
$$;

-- Makes sure this week's row exists, starting at $5,000.
create or replace function public.tt_ensure(p_player uuid, p_week date, p_day date)
returns public.tt_entries language plpgsql security definer set search_path = public as $$
declare
  c public.tt_config;
  e public.tt_entries;
begin
  select * into c from public.tt_config where id = 1;
  insert into public.tt_entries (player_id, week, cash)
    values (p_player, p_week, c.start_cash)
    on conflict (player_id, week) do nothing;
  select * into e from public.tt_entries where player_id = p_player and week = p_week;
  insert into public.tt_days (player_id, week, day, start_net, net)
    values (p_player, p_week, p_day, e.cash - e.owed, e.cash - e.owed)
    on conflict (player_id, day) do nothing;
  return e;
end $$;

-- Everything the phone needs to know about the week it is in.
create or replace function public.tt_frame(p_today date)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  c  public.tt_config;
  wk date := public.tt_week_of(p_today);
begin
  select * into c from public.tt_config where id = 1;
  return jsonb_build_object(
    'status',      case when wk < c.first_week then 'upcoming' else 'live' end,
    'week',        greatest(wk, c.first_week),
    'week_no',     case when wk < c.first_week then 1 else (wk - c.first_week) / 7 + 1 end,
    'day',         p_today,
    'day_no',      extract(dow from p_today)::int + 1,         -- 1 = Sunday … 7 = Saturday
    'starts_at',   (c.first_week::timestamp at time zone c.tz),
    'ends_at',     ((greatest(wk, c.first_week) + 7)::timestamp at time zone c.tz),
    'day_ends_at', ((p_today + 1)::timestamp at time zone c.tz),
    'now',         now(),
    'start_cash',  c.start_cash,
    'max_markers', c.max_markers
  );
end $$;

-- ---------- what the site calls ----------

-- Claim a name for this phone, or sign back in with the phone that has it.
create or replace function public.tt_join(p_name text, p_token text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  n  text := regexp_replace(btrim(coalesce(p_name, '')), '\s+', ' ', 'g');
  pl public.tt_players;
begin
  if char_length(coalesce(p_token, '')) < 24 then
    return jsonb_build_object('ok', false, 'error', 'bad_token');
  end if;
  if char_length(n) < 2 or char_length(n) > 16 or n !~ '^[A-Za-z0-9 ._''-]+$' then
    return jsonb_build_object('ok', false, 'error', 'bad_name');
  end if;
  select * into pl from public.tt_players where lower(name) = lower(n);
  if found then
    if pl.token_hash = public.tt_hash(p_token) then
      return jsonb_build_object('ok', true, 'name', pl.name);
    end if;
    return jsonb_build_object('ok', false, 'error', 'taken');
  end if;
  insert into public.tt_players (name, token_hash) values (n, public.tt_hash(p_token))
    returning * into pl;
  return jsonb_build_object('ok', true, 'name', pl.name);
exception when unique_violation then
  return jsonb_build_object('ok', false, 'error', 'taken');
end $$;

-- This player's wallet for the current week (created at $5,000 if new).
create or replace function public.tt_state(p_name text, p_token text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  pid uuid := public.tt_auth(p_name, p_token);
  d   date := public.tt_today();
  f   jsonb := public.tt_frame(d);
  e   public.tt_entries;
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  if f->>'status' = 'upcoming' then return f || jsonb_build_object('ok', true); end if;
  e := public.tt_ensure(pid, (f->>'week')::date, d);
  return f || jsonb_build_object('ok', true, 'cash', e.cash, 'owed', e.owed,
                                 'markers', e.markers, 'out', e.is_out);
end $$;

-- Save the wallet. p_week is the week the phone thinks it is playing; once
-- midnight Saturday passes, that week is closed and the phone is told to
-- pick up the new one rather than carry the old balance into it.
create or replace function public.tt_sync(p_name text, p_token text, p_week date,
                                          p_cash bigint, p_owed bigint, p_markers int, p_out boolean)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  pid  uuid := public.tt_auth(p_name, p_token);
  d    date := public.tt_today();
  f    jsonb := public.tt_frame(d);
  c    public.tt_config;
  e    public.tt_entries;
  wk   date;
  mk   int;
  ow   bigint;
  cs   bigint;
  dead boolean;
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  if f->>'status' = 'upcoming' then return f || jsonb_build_object('ok', false, 'error', 'upcoming'); end if;
  wk := (f->>'week')::date;
  if p_week is distinct from wk then
    return public.tt_state(p_name, p_token) || jsonb_build_object('ok', false, 'error', 'rollover');
  end if;
  select * into c from public.tt_config where id = 1;
  e := public.tt_ensure(pid, wk, d);
  if e.is_out then
    return public.tt_state(p_name, p_token) || jsonb_build_object('ok', false, 'error', 'out');
  end if;

  -- markers only go up, and never past the weekly limit
  mk := least(greatest(coalesce(p_markers, 0), e.markers), c.max_markers);
  ow := least(greatest(coalesce(p_owed, 0), 0), mk * c.marker_owed);
  cs := greatest(coalesce(p_cash, 0), 0);
  dead := coalesce(p_out, false) or (cs < 25 and mk >= c.max_markers);

  update public.tt_entries
     set cash = cs, owed = ow, markers = mk, is_out = dead, updated_at = now()
   where player_id = pid and week = wk;

  update public.tt_days set net = cs - ow where player_id = pid and day = d;

  return f || jsonb_build_object('ok', true, 'cash', cs, 'owed', ow, 'markers', mk, 'out', dead);
end $$;

-- The board: everyone's week, everyone's today, the daily winners so far,
-- and last week's champion.
create or replace function public.tt_board()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  d    date := public.tt_today();
  f    jsonb := public.tt_frame(d);
  wk   date := (f->>'week')::date;
  rows jsonb;
  wins jsonb;
  champ jsonb;
begin
  if f->>'status' = 'upcoming' then
    select coalesce(jsonb_agg(jsonb_build_object('name', name) order by created_at), '[]')
      into rows from public.tt_players;
    return f || jsonb_build_object('players', rows);
  end if;

  select coalesce(jsonb_agg(r order by (r->>'net')::bigint desc, r->>'name'), '[]') into rows from (
    select jsonb_build_object(
             'name', p.name, 'cash', e.cash, 'owed', e.owed, 'markers', e.markers, 'out', e.is_out,
             'net', e.cash - e.owed,
             'today', coalesce((select t.net - t.start_net from public.tt_days t
                                 where t.player_id = e.player_id and t.day = d), 0)) r
      from public.tt_entries e join public.tt_players p on p.id = e.player_id
     where e.week = wk
  ) s;

  -- the best day each finished day of this week, if anyone came out ahead
  select coalesce(jsonb_agg(w order by w->>'day'), '[]') into wins from (
    select distinct on (t.day)
           jsonb_build_object('day', t.day, 'name', p.name, 'profit', t.net - t.start_net) w
      from public.tt_days t join public.tt_players p on p.id = t.player_id
     where t.week = wk and t.day < d and t.net > t.start_net
     order by t.day, t.net - t.start_net desc, p.name
  ) s;

  select jsonb_build_object('name', p.name, 'net', e.cash - e.owed, 'week', e.week) into champ
    from public.tt_entries e join public.tt_players p on p.id = e.player_id
   where e.week = wk - 7
   order by e.cash - e.owed desc, p.name
   limit 1;

  return f || jsonb_build_object('rows', rows, 'daily_winners', wins, 'last_champion', champ);
end $$;

revoke all on function public.tt_auth(text, text), public.tt_ensure(uuid, date, date),
                       public.tt_frame(date) from public, anon, authenticated;
grant execute on function public.tt_join(text, text), public.tt_state(text, text),
                          public.tt_sync(text, text, date, bigint, bigint, int, boolean),
                          public.tt_board() to anon, authenticated;
