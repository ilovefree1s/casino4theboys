create or replace function public.c4tb_snap(sd date, sw date)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(r order by (r->>'net')::bigint desc, r->>'name'), '[]') from (
    select jsonb_build_object('name', p.name,
      'net', (select t2.net from c4tb_days t2 where t2.player_id = p.id and t2.week = sw and t2.day <= sd order by t2.day desc limit 1),
      'day', coalesce((select t3.net - t3.start_net from c4tb_days t3 where t3.player_id = p.id and t3.day = sd), 0)) r
    from c4tb_players p
    where exists (select 1 from c4tb_days t1 where t1.player_id = p.id and t1.week = sw and t1.day <= sd)
  ) s
$$;
revoke all on function public.c4tb_snap(date, date) from public, anon, authenticated;
comment on function public.c4tb_snap(date, date) is '4 The Boys tournament';
create or replace function public.c4tb_me(pid uuid, wk date, d date)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare e c4tb_entries; td c4tb_days; nm text;
begin
  if pid is null then return null; end if;
  select name into nm from c4tb_players where id = pid;
  select * into e from c4tb_entries where player_id = pid and week = wk;
  if not found then return jsonb_build_object('name', nm, 'fresh', true); end if;
  select * into td from c4tb_days where player_id = pid and day = d;
  return jsonb_build_object('name', nm, 'cash', e.cash, 'owed', e.owed, 'markers', e.markers,
    'out', e.is_out, 'net', e.cash - e.owed,
    'today', case when td.player_id is null then 0 else td.net - td.start_net end);
end $$;
revoke all on function public.c4tb_me(uuid, date, date) from public, anon, authenticated;
comment on function public.c4tb_me(uuid, date, date) is '4 The Boys tournament';
create or replace function public.c4tb_standings(p_name text, p_token text)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  d date := c4tb_today(); f jsonb := c4tb_frame(d); c c4tb_config;
  wk date := (f->>'week')::date; sw date := c4tb_week_of(d - 1);
  wins jsonb; champ jsonb;
begin
  select * into c from c4tb_config where id = 1;
  if f->>'status' = 'upcoming' then
    return f || jsonb_build_object('players', (select coalesce(jsonb_agg(jsonb_build_object('name', name) order by created_at), '[]') from c4tb_players));
  end if;
  select coalesce(jsonb_agg(w order by w->>'day'), '[]') into wins from (
    select distinct on (t.day) jsonb_build_object('day', t.day, 'name', p.name, 'profit', t.net - t.start_net) w
    from c4tb_days t join c4tb_players p on p.id = t.player_id
    where t.week = wk and t.day < d and t.net > t.start_net
    order by t.day, t.net - t.start_net desc, p.name) s;
  select jsonb_build_object('name', p.name, 'net', e.cash - e.owed, 'week', e.week) into champ
    from c4tb_entries e join c4tb_players p on p.id = e.player_id
    where e.week = wk - 7 order by e.cash - e.owed desc, p.name limit 1;
  return f || jsonb_build_object(
    'rows', case when sw >= c.first_week then c4tb_snap(d - 1, sw) else '[]'::jsonb end,
    'as_of', d - 1, 'final', sw < wk and sw >= c.first_week,
    'daily_winners', wins, 'last_champion', champ,
    'me', c4tb_me(c4tb_auth(p_name, p_token), wk, d));
end $$;
create or replace function public.c4tb_board()
returns jsonb language sql stable security definer set search_path = public as $$
  select public.c4tb_standings(null, null)
$$;
grant execute on function public.c4tb_standings(text, text) to anon, authenticated;
comment on function public.c4tb_standings(text, text) is '4 The Boys tournament';
