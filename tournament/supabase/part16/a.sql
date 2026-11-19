-- ================= Part 16: the message board =================
-- Justin posts, everybody reads. Shows on the board's MESSAGES tab.
begin;

do $guard$
begin
  if exists (select 1 from pg_class c join pg_namespace s on s.oid = c.relnamespace
              where s.nspname = 'public' and c.relname = 'c4tb_messages'
                and coalesce(obj_description(c.oid, 'pg_class'), '') <> '4 The Boys tournament') then
    raise exception 'Stopped: c4tb_messages already exists and is not ours';
  end if;
end $guard$;

create table if not exists public.c4tb_messages (
  id         bigserial primary key,
  body       text not null check (char_length(body) between 1 and 1000),
  created_at timestamptz not null default now()
);
alter table public.c4tb_messages enable row level security;
revoke all on public.c4tb_messages from anon, authenticated;
comment on table public.c4tb_messages is '4 The Boys tournament';

create or replace function public.c4tb_msgs()
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', id, 'body', body, 's',
                  greatest(0, extract(epoch from now() - created_at))::int) order by id desc), '[]')
    from (select * from public.c4tb_messages order by id desc limit 50) m;
$$;

create or replace function public.c4tb_msg_post(p_name text, p_token text, p_body text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare pid uuid := public.c4tb_auth(p_name, p_token);
begin
  if pid is null or not exists (select 1 from public.c4tb_players where id = pid and lower(name) = 'justin') then
    return jsonb_build_object('ok', false, 'error', 'not_allowed');
  end if;
  if coalesce(char_length(btrim(p_body)), 0) not between 1 and 1000 then
    return jsonb_build_object('ok', false, 'error', 'length');
  end if;
  insert into public.c4tb_messages (body) values (btrim(p_body));
  return jsonb_build_object('ok', true);
end $$;

create or replace function public.c4tb_msg_delete(p_name text, p_token text, p_id bigint)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare pid uuid := public.c4tb_auth(p_name, p_token);
begin
  if pid is null or not exists (select 1 from public.c4tb_players where id = pid and lower(name) = 'justin') then
    return jsonb_build_object('ok', false, 'error', 'not_allowed');
  end if;
  delete from public.c4tb_messages where id = p_id;
  return jsonb_build_object('ok', true);
end $$;

grant execute on function public.c4tb_msgs(), public.c4tb_msg_post(text, text, text),
                          public.c4tb_msg_delete(text, text, bigint) to anon, authenticated;
comment on function public.c4tb_msgs() is '4 The Boys tournament';
comment on function public.c4tb_msg_post(text, text, text) is '4 The Boys tournament';
comment on function public.c4tb_msg_delete(text, text, bigint) is '4 The Boys tournament';

commit;
