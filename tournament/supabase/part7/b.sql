create or replace function public.c4tb_pin(p_name text, p_token text, p_pin text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare pid uuid := c4tb_auth(p_name, p_token); nm text;
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  select name into nm from c4tb_players where id = pid;
  if p_pin is null then
    return jsonb_build_object('ok', true, 'has_pin', (select pin_hash is not null from c4tb_players where id = pid));
  end if;
  if p_pin !~ '^[0-9]{4}$' then return jsonb_build_object('ok', false, 'error', 'bad_pin'); end if;
  update c4tb_players set pin_hash = c4tb_hash(lower(nm) || ':' || p_pin) where id = pid;
  return jsonb_build_object('ok', true, 'has_pin', true);
end $$;
grant execute on function public.c4tb_pin(text, text, text) to anon, authenticated;
comment on function public.c4tb_pin(text, text, text) is '4 The Boys tournament';
update public.c4tb_players set token_hash = 'released' where lower(name) = 'jachob';
