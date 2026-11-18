-- ================= Part 12b: who's online =================
-- Each open phone checks in every 30 seconds; seen in the last 90 is online.
begin;
alter table public.c4tb_players add column if not exists last_seen timestamptz;

create or replace function public.c4tb_ping(p_name text, p_token text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  pid uuid := public.c4tb_auth(p_name, p_token);
begin
  if pid is not null then update public.c4tb_players set last_seen = now() where id = pid; end if;
  return (select coalesce(jsonb_agg(name order by name), '[]') from public.c4tb_players
           where last_seen > now() - interval '90 seconds');
end $$;

grant execute on function public.c4tb_ping(text, text) to anon, authenticated;
comment on function public.c4tb_ping(text, text) is '4 The Boys tournament';
commit;
