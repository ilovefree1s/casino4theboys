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
