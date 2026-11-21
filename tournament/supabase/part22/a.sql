-- ================= Part 22: Pai Gow joins the tournament (22 games) =================
-- The list of games that score points, in one place the game can ask for.
-- Run this BEFORE a points week starts: a game's points for a day come from where it falls
-- among all the games, so adding one mid-week would change days already played.
create or replace function public.c4tb_games()
returns text[] language sql immutable as $$
  select array[
    'Punto Banco', 'Dragon Tiger', 'Free Bet Blackjack', 'Blazing 777', 'Double Down Madness',
    'Ultimate Texas Hold''em', 'DJ Wild', 'Shootout', 'Mini UTH', 'I Luv Suits', 'Kirby',
    'Double Zero Roulette', 'Lightning Roulette', 'Double Ball Roulette', 'Crapless Craps', 'Destroyer',
    'Keno', 'Money Wheel', 'Sic Bo', 'Plinko', 'Mines', 'Pai Gow'];
$$;

create or replace function public.c4tb_game_pts(p_day date, p_game text)
returns int language sql immutable as $$
  select case when r < 3 then 3 when r < 8 then 2 else 1 end
    from (select count(*) as r from unnest(public.c4tb_games()) g
     where g <> p_game and public.c4tb_game_hash(p_day, g) < public.c4tb_game_hash(p_day, p_game)) x;
$$;
grant execute on function public.c4tb_games(), public.c4tb_game_pts(date, text) to anon, authenticated;
comment on function public.c4tb_games() is '4 The Boys tournament';
comment on function public.c4tb_game_pts(date, text) is '4 The Boys tournament';
