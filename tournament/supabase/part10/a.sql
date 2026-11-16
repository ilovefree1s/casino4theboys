alter table public.c4tb_config add column if not exists daily_from date;
alter table public.c4tb_entries add column if not exists day date;
create or replace function public.c4tb_ensure(p_player uuid, p_week date, p_day date)
returns public.c4tb_entries language plpgsql security definer set search_path = public as $$
declare c public.c4tb_config; e public.c4tb_entries;
begin
  select * into c from public.c4tb_config where id = 1;
  insert into public.c4tb_entries (player_id, week, cash, day)
    values (p_player, p_week, c.start_cash, p_day)
    on conflict (player_id, week) do nothing;
  select * into e from public.c4tb_entries where player_id = p_player and week = p_week;
  -- daily rules: every new day is a fresh $5,000 with no markers
  if c.daily_from is not null and p_day >= c.daily_from and e.day is distinct from p_day then
    update public.c4tb_entries
       set cash = c.start_cash, owed = 0, markers = 0, is_out = false, day = p_day, updated_at = now()
     where player_id = p_player and week = p_week
    returning * into e;
  end if;
  insert into public.c4tb_days (player_id, week, day, start_net, net)
    values (p_player, p_week, p_day, e.cash - e.owed, e.cash - e.owed)
    on conflict (player_id, day) do nothing;
  return e;
end $$;
