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
