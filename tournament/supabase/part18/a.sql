-- Who's already taken: the join sheet greys out those faces.
create or replace function public.c4tb_names()
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(name order by name), '[]') from public.c4tb_players;
$$;
grant execute on function public.c4tb_names() to anon, authenticated;
comment on function public.c4tb_names() is '4 The Boys tournament';
