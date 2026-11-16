-- Switch to daily rules now: everybody's day starts over at a fresh $5,000.
update public.c4tb_config set daily_from = public.c4tb_today() where id = 1;
update public.c4tb_entries
   set cash = 5000, owed = 0, markers = 0, is_out = false, day = public.c4tb_today(), updated_at = now()
 where week = public.c4tb_week_of(public.c4tb_today());
update public.c4tb_days set start_net = 5000, net = 5000 where day = public.c4tb_today();
