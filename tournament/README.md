# 4 The Boys · Casino Games

The casino, plus a week-long tournament with a shared leaderboard.

## Tournament rules
- Runs Sunday 12:00 AM to Saturday 11:59 PM, Eastern time. The first week starts Nov 8, 2026.
- Everyone starts the week with $5,000.
- Up to 3 markers a week ($5,000 in, $7,500 owed). Bust after the 3rd and you're out for the week.
- Score is cash minus what you still owe on markers.
- The board shows today's profit (resets at midnight) and the week's totals. The top score at midnight Saturday is the champion.

## Setup
1. Supabase → SQL Editor → paste `supabase/tournament.sql` → Run.
2. Rebuild the page with your project's URL and anon key:
   `python3 src/build.py src/original.html index.html https://YOUR-PROJECT.supabase.co YOUR_ANON_KEY`
3. GitHub → Settings → Pages → Deploy from branch `main`, folder `/ (root)`.

The anon key is meant to be public. Never put the service_role key in this repo.
