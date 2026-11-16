-- The old save, from a page that doesn't know the day: under daily rules it
-- can't be trusted, so the phone is told to reload; otherwise it saves as before.
create or replace function public.c4tb_sync(p_name text, p_token text, p_week date,
                                          p_cash bigint, p_owed bigint, p_markers int, p_out boolean)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
begin
  if (public.c4tb_frame(public.c4tb_today())->>'daily')::boolean then
    return public.c4tb_state(p_name, p_token) || jsonb_build_object('ok', false, 'error', 'rollover');
  end if;
  return public.c4tb_sync_day(p_name, p_token, p_week, null, p_cash, p_owed, p_markers, p_out);
end $$;
-- A week's score: under daily rules the best 3 days' profit added up,
-- otherwise the money at the last midnight.
create or replace function public.c4tb_score(pid uuid, sw date, sd date)
returns bigint language sql stable security definer set search_path = public as $$
  select case
    when cf.daily_from is not null and sw >= public.c4tb_week_of(cf.daily_from) then
      (select sum(x) from (select t.net - t.start_net x from c4tb_days t
         where t.player_id = pid and t.week = sw and t.day <= sd order by 1 desc limit 3) b)
    else (select t.net from c4tb_days t where t.player_id = pid and t.week = sw and t.day <= sd order by t.day desc limit 1)
  end from c4tb_config cf where cf.id = 1
$$;
revoke all on function public.c4tb_score(uuid, date, date) from public, anon, authenticated;
