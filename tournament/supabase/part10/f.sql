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
  -- last week's winner, scored by that week's own rules
  champ := c4tb_snap(wk - 1, wk - 7) -> 0;
  if champ is not null then champ := champ || jsonb_build_object('week', wk - 7); end if;
  return f || jsonb_build_object(
    'rows', case when sw >= c.first_week then c4tb_snap(d - 1, sw) else '[]'::jsonb end,
    'as_of', d - 1, 'final', sw < wk and sw >= c.first_week,
    'daily_winners', wins, 'last_champion', champ,
    'me', c4tb_me(c4tb_auth(p_name, p_token), wk, d));
end $$;
