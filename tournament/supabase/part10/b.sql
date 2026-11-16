create or replace function public.c4tb_frame(p_today date)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
  c  public.c4tb_config;
  wk date := public.c4tb_week_of(p_today);
begin
  select * into c from public.c4tb_config where id = 1;
  return jsonb_build_object(
    'status',      case when wk < c.first_week then 'upcoming' else 'live' end,
    'week',        greatest(wk, c.first_week),
    'week_no',     case when wk < c.first_week then 1 else (wk - c.first_week) / 7 + 1 end,
    'day',         p_today,
    'day_no',      extract(dow from p_today)::int + 1,
    'starts_at',   (c.first_week::timestamp at time zone c.tz),
    'ends_at',     ((greatest(wk, c.first_week) + 7)::timestamp at time zone c.tz),
    'day_ends_at', ((p_today + 1)::timestamp at time zone c.tz),
    'now',         now(),
    'start_cash',  c.start_cash,
    'max_markers', c.max_markers,
    'daily',       c.daily_from is not null and p_today >= c.daily_from
  );
end $$;
