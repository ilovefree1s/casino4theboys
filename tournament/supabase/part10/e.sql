create or replace function public.c4tb_snap(sd date, sw date)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(r order by (r->>'net')::bigint desc, r->>'name'), '[]') from (
    select jsonb_build_object('name', p.name,
      'net', public.c4tb_score(p.id, sw, sd),
      'day', coalesce((select t3.net - t3.start_net from c4tb_days t3 where t3.player_id = p.id and t3.day = sd), 0)) r
    from c4tb_players p
    where exists (select 1 from c4tb_days t1 where t1.player_id = p.id and t1.week = sw and t1.day <= sd)
  ) s
$$;
create or replace function public.c4tb_me(pid uuid, wk date, d date)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare e c4tb_entries; td c4tb_days; nm text;
begin
  if pid is null then return null; end if;
  select name into nm from c4tb_players where id = pid;
  select * into e from c4tb_entries where player_id = pid and week = wk;
  if not found then return jsonb_build_object('name', nm, 'fresh', true); end if;
  select * into td from c4tb_days where player_id = pid and day = d;
  -- daily rules: a wallet from an earlier day is already a fresh $5,000 today
  if (c4tb_frame(d)->>'daily')::boolean and e.day is distinct from d then
    e.cash := (select start_cash from c4tb_config where id = 1); e.owed := 0; e.markers := 0; e.is_out := false;
  end if;
  return jsonb_build_object('name', nm, 'cash', e.cash, 'owed', e.owed, 'markers', e.markers,
    'out', e.is_out, 'net', e.cash - e.owed,
    'today', case when td.player_id is null then 0 else td.net - td.start_net end,
    'score', public.c4tb_score(pid, wk, d));
end $$;
