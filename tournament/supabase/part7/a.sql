alter table public.c4tb_players add column if not exists pin_hash text;
create or replace function public.c4tb_signin(p_name text, p_token text, p_pin text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  n text := regexp_replace(btrim(coalesce(p_name, '')), '\s+', ' ', 'g');
  ph text; pl c4tb_players;
begin
  if char_length(coalesce(p_token, '')) < 24 then return jsonb_build_object('ok', false, 'error', 'bad_token'); end if;
  if char_length(n) < 2 or char_length(n) > 16 or n !~ '^[A-Za-z0-9 ._''-]+$' then return jsonb_build_object('ok', false, 'error', 'bad_name'); end if;
  if coalesce(p_pin, '') !~ '^[0-9]{4}$' then return jsonb_build_object('ok', false, 'error', 'bad_pin'); end if;
  ph := c4tb_hash(lower(n) || ':' || p_pin);
  select * into pl from c4tb_players where lower(name) = lower(n);
  if not found then
    insert into c4tb_players (name, token_hash, pin_hash) values (n, c4tb_hash(p_token), ph) returning * into pl;
    return jsonb_build_object('ok', true, 'name', pl.name);
  end if;
  if pl.token_hash = c4tb_hash(p_token) or pl.token_hash = 'released' or pl.pin_hash = ph then
    update c4tb_players set token_hash = c4tb_hash(p_token), pin_hash = coalesce(pin_hash, ph) where id = pl.id;
    return jsonb_build_object('ok', true, 'name', pl.name);
  end if;
  return jsonb_build_object('ok', false, 'error', case when pl.pin_hash is null then 'taken' else 'pin' end);
exception when unique_violation then
  return jsonb_build_object('ok', false, 'error', 'taken');
end $$;
grant execute on function public.c4tb_signin(text, text, text) to anon, authenticated;
comment on function public.c4tb_signin(text, text, text) is '4 The Boys tournament';
