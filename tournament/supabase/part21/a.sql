-- ================= Part 21: you have to be up $5,000 on a game ($15,000 or more) to lead it or take its points =================
-- Who's on top of each game on a day (today: live; a past day: final).
create or replace function public.c4tb_game_leaders(p_day date)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('game', t.game, 'name', p.name, 'cash', t.cash,
                                               'pts', public.c4tb_game_pts(p_day, t.game)) order by t.game), '[]')
    from (select distinct on (game) game, player_id, cash
            from public.c4tb_game_wallets where day = p_day and cash >= 15000
           order by game, cash desc, updated_at asc) t
    join public.c4tb_players p on p.id = t.player_id;
$$;

-- The week's points: every finished day's game winners, plus what each player earned all week.
create or replace function public.c4tb_points_board(p_week date)
returns jsonb language sql stable security definer set search_path = public as $$
  with w as (
    select distinct on (day, game) day, game, player_id, public.c4tb_game_pts(day, game) as pts
      from public.c4tb_game_wallets
     where week = p_week and day < public.c4tb_today() and cash >= 15000   -- you have to be up $5,000 to take a game
     order by day, game, cash desc, updated_at asc
  ), pts as (
    select player_id, sum(pts)::int as points, count(*)::int as wins from w group by player_id
  ), earned as (
    select player_id, sum(cash - 10000)::bigint as money from public.c4tb_game_wallets
     where week = p_week group by player_id
  )
  select coalesce(jsonb_agg(jsonb_build_object('name', p.name, 'points', coalesce(pts.points, 0),
                                               'wins', coalesce(pts.wins, 0), 'money', e.money)
                            order by coalesce(pts.points, 0) desc, e.money desc, p.name), '[]')
    from earned e join public.c4tb_players p on p.id = e.player_id
    left join pts on pts.player_id = e.player_id;
$$;
