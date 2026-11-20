-- ================= Part 20: a game's points for the day are a proper shuffle =================
-- Every day the 21 games are shuffled: 3 are worth 3 points, 5 are worth 2, the rest are worth 1.
-- No pattern from day to day.
-- (The game works out the same numbers.)
create or replace function public.c4tb_game_hash(p_day date, p_game text)
returns bigint language plpgsql immutable as $$
declare
  s text := p_day::text || '|' || p_game;
  h bigint := 2166136261;
  i int;
begin
  for i in 1..length(s) loop
    h := ((h # ascii(substr(s, i, 1))) * 16777619) & 4294967295;
  end loop;
  h := h # (h >> 15);
  h := (h * 739982445) & 4294967295;
  h := h # (h >> 12);
  h := (h * 695872825) & 4294967295;
  h := h # (h >> 15);
  return h;
end $$;

create or replace function public.c4tb_game_pts(p_day date, p_game text)
returns int language sql immutable as $$
  select case when r < 3 then 3 when r < 8 then 2 else 1 end
    from (select count(*) as r from unnest(array[
      'Punto Banco', 'Dragon Tiger', 'Free Bet Blackjack', 'Blazing 777', 'Double Down Madness',
      'Ultimate Texas Hold''em', 'DJ Wild', 'Shootout', 'Mini UTH', 'I Luv Suits', 'Kirby',
      'Double Zero Roulette', 'Lightning Roulette', 'Double Ball Roulette', 'Crapless Craps', 'Destroyer',
      'Keno', 'Money Wheel', 'Sic Bo', 'Plinko', 'Mines']) g
     where g <> p_game and public.c4tb_game_hash(p_day, g) < public.c4tb_game_hash(p_day, p_game)) x;
$$;
grant execute on function public.c4tb_game_pts(date, text), public.c4tb_game_hash(date, text) to anon, authenticated;
comment on function public.c4tb_game_pts(date, text) is '4 The Boys tournament';
comment on function public.c4tb_game_hash(date, text) is '4 The Boys tournament';
