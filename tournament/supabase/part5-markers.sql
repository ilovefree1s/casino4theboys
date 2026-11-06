begin;

create or replace function public.c4tb_sync(p_name text, p_token text, p_week date,
                                          p_cash bigint, p_owed bigint, p_markers int, p_out boolean)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  pid  uuid := public.c4tb_auth(p_name, p_token);
  d    date := public.c4tb_today();
  f    jsonb := public.c4tb_frame(d);
  c    public.c4tb_config;
  e    public.c4tb_entries;
  wk   date;
  mk   int;
  ow   bigint;
  cs   bigint;
  dead boolean;
begin
  if pid is null then return jsonb_build_object('ok', false, 'error', 'auth'); end if;
  if f->>'status' = 'upcoming' then return f || jsonb_build_object('ok', false, 'error', 'upcoming'); end if;
  wk := (f->>'week')::date;
  if p_week is distinct from wk then
    return public.c4tb_state(p_name, p_token) || jsonb_build_object('ok', false, 'error', 'rollover');
  end if;
  select * into c from public.c4tb_config where id = 1;
  e := public.c4tb_ensure(pid, wk, d);
  if e.is_out then
    return public.c4tb_state(p_name, p_token) || jsonb_build_object('ok', false, 'error', 'out');
  end if;

  -- markers: up to max_markers unpaid at a time, like the campaign; paying
  -- back frees room. markers counts every one taken this week.
  mk := least(greatest(coalesce(p_markers, 0), e.markers), 1000);
  ow := least(greatest(coalesce(p_owed, 0), 0), c.max_markers * c.marker_owed);
  cs := greatest(coalesce(p_cash, 0), 0);
  dead := coalesce(p_out, false) or (cs < 25 and ow >= c.max_markers * c.marker_owed);

  update public.c4tb_entries
     set cash = cs, owed = ow, markers = mk, is_out = dead, updated_at = now()
   where player_id = pid and week = wk;

  update public.c4tb_days set net = cs - ow where player_id = pid and day = d;

  return f || jsonb_build_object('ok', true, 'cash', cs, 'owed', ow, 'markers', mk, 'out', dead);
end $$;

comment on function public.c4tb_sync(text, text, date, bigint, bigint, int, boolean) is '4 The Boys tournament';

commit;
