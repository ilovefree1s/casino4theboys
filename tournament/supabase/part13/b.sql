-- ================= Part 13b: last seen =================
-- The ping now answers with everyone's last check-in, so the board can say
-- "5m ago" by the people who aren't on right now.
create or replace function public.c4tb_ping(p_name text, p_token text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  pid uuid := public.c4tb_auth(p_name, p_token);
begin
  if pid is not null then update public.c4tb_players set last_seen = now() where id = pid; end if;
  return (select coalesce(jsonb_agg(jsonb_build_object('name', name,
                    's', greatest(0, extract(epoch from now() - last_seen))::int) order by name), '[]')
            from public.c4tb_players where last_seen is not null);
end $$;
