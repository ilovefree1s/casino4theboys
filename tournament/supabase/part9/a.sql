alter table public.c4tb_entries add column if not exists best_cash bigint;
update public.c4tb_entries set best_cash = greatest(coalesce(best_cash, 0), cash, 5000);
create or replace function public.c4tb_highs(p_week date)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('name', p.name,
           'best', greatest(coalesce(e.best_cash, 0), e.cash, c.start_cash))
         order by greatest(coalesce(e.best_cash, 0), e.cash, c.start_cash) desc, p.name), '[]')
  from c4tb_entries e join c4tb_players p on p.id = e.player_id, c4tb_config c
  where e.week = p_week and p_week < c4tb_week_of(c4tb_today())
$$;
grant execute on function public.c4tb_highs(date) to anon, authenticated;
comment on function public.c4tb_highs(date) is '4 The Boys tournament';
