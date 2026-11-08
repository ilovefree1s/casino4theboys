create or replace function public.c4tb_roster()
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('name', p.name) order by p.created_at), '[]')
  from c4tb_players p
$$;
grant execute on function public.c4tb_roster() to anon, authenticated;
comment on function public.c4tb_roster() is '4 The Boys tournament';
