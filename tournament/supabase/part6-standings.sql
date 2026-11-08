-- 4 The Boys tournament · Part 6: standings update once a day, at midnight.
-- Other players' money is only ever sent as it stood at the last midnight.
-- Your own money (with today's profit) is sent only to your own phone.
-- Only replaces/adds c4tb_ functions; no tables or data change.
begin;

create or replace function public.c4tb_standings(p_name text, p_token text)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  pid   uuid := public.c4tb_auth(p_name, p_token);
  d     date := public.c4tb_today();
  f     jsonb := public.c4tb_frame(d);
  c     public.c4tb_config;
  wk    date := (f->>'week')::date;
  sd    date := d - 1;                         -- the day the standings show
  sw    date := public.c4tb_week_of(d - 1);    -- and its week (last week's, on a Sunday)
  rows  jsonb;
  wins  jsonb;
  champ jsonb;
  me    jsonb;
  e     public.c4tb_entries;
  td    public.c4tb_days;
begin
  select * into c from public.c4tb_config where id = 1;
  if f->>'status' = 'upcoming' then
    select coalesce(jsonb_agg(jsonb_build_object('name', name) order by created_at), '[]')
      into rows from public.c4tb_players;
    return f || jsonb_build_object('players', rows);
  end if;

  -- everyone as of last midnight: their money at the end of the last day they
  -- played up to then, and what they made on that day itself
  if sw >= c.first_week then
    select coalesce(jsonb_agg(r order by (r->>'net')::bigint desc, r->>'name'), '[]') into rows from (
      select jsonb_build_object(
               'name', p.name,
               'net', (select t2.net from public.c4tb_days t2
                        where t2.player_id = p.id and t2.week = sw and t2.day <= sd
                        order by t2.day desc limit 1),
               'day', coalesce((select t3.net - t3.start_net from public.c4tb_days t3
                                 where t3.player_id = p.id and t3.day = sd), 0)) r
        from public.c4tb_players p
       where exists (select 1 from public.c4tb_days t1 where t1.player_id = p.id and t1.week = sw and t1.day <= sd)
    ) s;
  else
    rows := '[]';
  end if;

  select coalesce(jsonb_agg(w order by w->>'day'), '[]') into wins from (
    select distinct on (t.day)
      jsonb_build_object('day', t.day, 'name', p.name, 'profit', t.net - t.start_net) w
      from public.c4tb_days t join public.c4tb_players p on p.id = t.player_id
     where t.week = wk and t.day < d and t.net > t.start_net
     order by t.day, t.net - t.start_net desc, p.name
  ) s;

  select jsonb_build_object('name', p.name, 'net', e2.cash - e2.owed, 'week', e2.week) into champ
    from public.c4tb_entries e2 join public.c4tb_players p on p.id = e2.player_id
   where e2.week = wk - 7
   order by e2.cash - e2.owed desc, p.name
   limit 1;

  -- you, right now (only ever sent to your own phone)
  if pid is not null then
    select * into e from public.c4tb_entries where player_id = pid and week = wk;
    if found then
      select * into td from public.c4tb_days where player_id = pid and day = d;
      me := jsonb_build_object('name', (select name from public.c4tb_players where id = pid),
              'cash', e.cash, 'owed', e.owed, 'markers', e.markers, 'out', e.is_out,
              'net', e.cash - e.owed, 'today', case when td.player_id is null then 0 else td.net - td.start_net end);
    else
      me := jsonb_build_object('name', (select name from public.c4tb_players where id = pid), 'fresh', true);
    end if;
  end if;

  return f || jsonb_build_object('rows', rows, 'as_of', sd, 'final', sw < wk and sw >= c.first_week,
                                 'daily_winners', wins, 'last_champion', champ, 'me', me);
end $$;

-- the old board, for anyone on an old copy of the app: same rules, no live numbers
create or replace function public.c4tb_board()
returns jsonb language sql stable security definer set search_path = public as $$
  select public.c4tb_standings(null, null)
$$;

grant execute on function public.c4tb_standings(text, text) to anon, authenticated;
comment on function public.c4tb_standings(text, text) is '4 The Boys tournament';
comment on function public.c4tb_board() is '4 The Boys tournament';

commit;
