  /* ================= THE TOURNAMENT =================
   * A week-long contest on its own wallet. It plays exactly like the
   * campaign - same rooms, same markers, same tables - so the shell runs in
   * campaign mode with TT.on set, and the wallet's keys move to that week.
   * Supabase keeps the score: every change to the purse is sent up, the
   * board is read back down, and the server decides what week and day it is.
   */
  (function () {
    var URL_ = TT_CONFIG.url.replace(/\/+$/, ""), KEY = TT_CONFIG.key;
    var ready = !!(URL_ && KEY);
    var skew = 0;            // server clock minus this phone's
    var frame = null;        // the week, as the server last described it
    var board = null;        // the last board read
    var timer = null, retry = null, inflight = false, again = false;
    var DAYS = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"];

    function money(n) { return (n < 0 ? "-$" : "$") + fmt(Math.abs(n)); }
    /** A duel score: profit for every dollar bet, as a percent. */
    function pct(net, bet) {
      if (!bet) return "+0%";
      var v = Math.round(net / bet * 100);
      return (v > 0 ? "+" : "") + v + "%";
    }
    function signed(n) { return (n > 0 ? "+" : n < 0 ? "-" : "") + "$" + fmt(Math.abs(n)); }
    // the board's own, short past a million: $53.4Q, +$9.06M
    function amt(n) { n = Math.abs(n); return "$" + (n >= 1e6 && typeof shortAmt === "function" ? shortAmt(n) : fmt(n)); }
    function moneyS(n) { return (n < 0 ? "-" : "") + amt(n); }
    function signedS(n) { return (n > 0 ? "+" : n < 0 ? "-" : "") + amt(n); }
    /** The line beside CLOSE: a small label over what it counts down to. */
    function subline(label, value) {
      var el = $("tt-bsub"); el.textContent = label;
      if (value) { var v = document.createElement("b"); v.textContent = value; el.appendChild(v); }
    }
    function now() { return Date.now() + skew; }
    function left(iso) {
      var ms = Date.parse(iso) - now();
      if (!(ms > 0)) return "0M";
      var m = Math.floor(ms / 60000), d = Math.floor(m / 1440), h = Math.floor(m % 1440 / 60);
      return d > 0 ? d + "D " + h + "H" : h > 0 ? h + "H " + (m % 60) + "M" : Math.max(1, m) + "M";
    }
    function dayName(iso) { return DAYS[new Date(iso + "T12:00:00Z").getUTCDay()]; }

    function token() {
      var t = store.getStr("ftt.token", "");
      if (t && t.length >= 24) return t;
      var a = new Uint8Array(24);
      (window.crypto || window.msCrypto).getRandomValues(a);
      t = Array.prototype.map.call(a, function (b) { return ("0" + b.toString(16)).slice(-2); }).join("");
      store.setStr("ftt.token", t);
      return t;
    }
    function myName() { return store.getStr("ftt.name", ""); }

    function rpc(fn, args, keepalive) {
      return fetch(URL_ + "/rest/v1/rpc/" + fn, {
        method: "POST", keepalive: !!keepalive,
        // a publishable key (sb_publishable_…) goes in apikey alone; an older
        // anon key is a token and goes in Authorization too
        headers: /^sb_/.test(KEY)
          ? { "apikey": KEY, "Content-Type": "application/json" }
          : { "apikey": KEY, "Authorization": "Bearer " + KEY, "Content-Type": "application/json" },
        body: JSON.stringify(args || {})
      }).then(function (r) {
        if (!r.ok) throw new Error("http " + r.status);
        return r.json();
      }).then(function (j) {
        if (j && j.now) skew = Date.parse(j.now) - Date.now();
        return j;
      });
    }

    // ---- the wallet, by week ----
    /* From the week of 2026-11-22 the tournament is played for points: every
       game has its own $10,000 wallet per player, fresh every day, with free
       rebuys, and whoever is on top of a game at midnight takes its 1-3 points. */
    var POINTS_FROM = "2026-11-22", POINTS_CASH = 10000;
    function isPoints(week) { return !!week && String(week) >= POINTS_FROM; }
    TT.points = false;
    function gslug(g) { return String(g || "lobby").toLowerCase().replace(/[^a-z0-9]+/g, ""); }
    /** A game's points for a day, 1-3: the same sum the server does. */
    function ghash(day, game) {
      // a proper scramble of the day and the game's name
      var s = String(day) + "|" + game, h = 2166136261;
      for (var i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619) >>> 0; }
      h ^= h >>> 15; h = Math.imul(h, 739982445) >>> 0;
      h ^= h >>> 12; h = Math.imul(h, 695872825) >>> 0;
      h ^= h >>> 15;
      return h >>> 0;
    }
    function gpts(day, game) {
      // the day's shuffle: where this game falls among all 21 decides its points, so each day
      // exactly 3 games are worth 3, 5 are worth 2, and the rest are worth 1
      var mine = ghash(day, game), below = 0;
      Object.keys(GAMES).forEach(function (id) {
        var g = GAMES[id];
        if (g !== game && ghash(day, g) < mine) below++;
      });
      return below < 3 ? 3 : below < 8 ? 2 : 1;
    }
    TT.gpts = gpts;
    function k(name) {
      var base = "ftt." + TT.week + (TT.day ? "." + TT.day : "") + ".";
      if (TT.points && name === "bankroll") {
        // the wallet of the game you're at (or last sat down at); a new one starts at $10,000
        var key = base + "g." + gslug(TT.game) + ".bankroll";
        if (store.getStr(key, "") === "") store.set(key, POINTS_CASH);
        return key;
      }
      return base + name;
    }
    function gameKey(g) { var was = TT.game; TT.game = g; var key = k("bankroll"); TT.game = was; return key; }
    var dirtyGames = {};
    function kw(name) { return "ftt." + TT.week + "." + name; }
    function readWallet() {
      return {
        cash: Math.max(0, Math.round(store.get(k("bankroll"), frame ? frame.start_cash : 5000))),
        owed: Math.max(0, Math.round(store.get(k("debt"), 0))),
        markers: Math.round(store.get(k("markers"), 0)),
        out: store.getStr(k("out"), "") === "1"
      };
    }
    function writeWallet(s) {
      store.set(k("bankroll"), s.cash);
      store.set(k("debt"), s.owed);
      store.set(k("markers"), s.markers);
      store.setStr(k("out"), s.out ? "1" : "");
      store.setStr(k("dirty"), "");
    }

    TT.key = k;
    TT.markers = function () { return Math.round(store.get(k("markers"), 0)); };
    /** Markers still owed on: the campaign's rule, 3 at a time, paid back frees one. */
    TT.unpaid = function () { return Math.ceil(Math.max(0, store.get(k("debt"), 0)) / 7500); };
    TT.isOut = function () { return store.getStr(k("out"), "") === "1"; };
    TT.max = function () { return frame ? frame.max_markers : 3; };
    TT.signMarker = function () { store.set(k("markers"), TT.markers() + 1); TT.changed(true); };
    TT.knockOut = function () { store.setStr(k("out"), "1"); TT.changed(true); };

    /** The purse moved: send it up shortly, or right now. */
    TT.changed = function (soon) {
      if (!ready || !TT.week) return;
      if (TT.points && TT.game) dirtyGames[TT.game] = 1;
      store.setStr(k("dirty"), "1");
      clearTimeout(timer);
      timer = setTimeout(push, soon ? 0 : 1200);
    };
    /** Points week: every game wallet that moved goes up on its own. */
    function pushGames(keepalive) {
      var games = Object.keys(dirtyGames);
      dirtyGames = {};
      if (!games.length) { store.setStr(k("dirty"), ""); return; }
      var left = games.length;
      games.forEach(function (g) {
        rpc("c4tb_game_sync", { p_name: myName(), p_token: token(), p_week: TT.week, p_day: TT.day,
          p_game: g, p_cash: Math.round(store.get(gameKey(g), POINTS_CASH)) }, keepalive).then(function (j) {
          if (j.error === "auth") return signedOut();
          if (j.error === "rollover") return dayOver();
          if (--left === 0 && !Object.keys(dirtyGames).length) store.setStr(k("dirty"), "");
        }, function () {
          dirtyGames[g] = 1;
          clearTimeout(retry);
          retry = setTimeout(push, 10000);
        });
      });
      if (statsDirty) pushStats();
    }
    function push(keepalive) {
      if (!TT.week || TT.closed) return;
      if (TT.points) return pushGames(keepalive);
      if (inflight) { again = true; return; }
      inflight = true;
      var w = readWallet(), wk = TT.week, daily = !!TT.day;
      var args = {
        p_name: myName(), p_token: token(), p_week: wk,
        // a hot-game bonus still being earned at the table counts toward the day already
        p_cash: w.cash + hot.extra(), p_owed: w.owed, p_markers: w.markers, p_out: w.out
      };
      if (daily) args.p_day = TT.day;
      rpc(daily ? "c4tb_sync_day" : "c4tb_sync", args, keepalive).then(function (j) {
        inflight = false;
        clearTimeout(retry);
        // the server doesn't know this phone any more (the name moved to another
        // phone, or was freed): keep everything here, and get the player signed
        // back in with name and PIN, then send what didn't save
        if (j.error === "auth") return signedOut();
        if (j.week && j.week !== wk) return weekOver();
        if (j.error === "rollover") return j.daily ? dayOver() : weekOver();
        frame = j;
        // the server's word is final on markers and on being out
        if (j.ok || j.error === "out") {
          store.set(k("markers"), j.markers);
          if (j.out) store.setStr(k("out"), "1");
          if (!again) store.setStr(k("dirty"), "");
        }
        if (j.ok) pushStats();
        if (again) { again = false; push(); }
      }, function () {
        inflight = false;
        clearTimeout(retry);
        retry = setTimeout(push, 10000);
      });
    }
    var askingBack = false;
    function signedOut() {
      if (askingBack) return;
      askingBack = true;
      askName(function () { askingBack = false; push(); refreshLane(); });
      $("tt-join-err").textContent = "This phone got signed out, so your last bets didn't save. Enter your name and PIN to save them.";
    }
    document.addEventListener("visibilitychange", function () {
      if (document.hidden) { if (TT.week && store.getStr(k("dirty"), "")) push(true); }
      else refreshLane();
    });

    /** Midnight Saturday: the week is closed. Nothing more is written to it. */
    function weekOver() {
      if (TT.closed) return;
      TT.closed = true;
      // what starts next: a points week gives every game its own $10,000
      var nextWk = "";
      try { var nd = new Date((frame && frame.week) + "T12:00:00Z"); nd.setUTCDate(nd.getUTCDate() + 7); nextWk = nd.toISOString().slice(0, 10); } catch (e) {}
      $("tt-over-big").textContent = "THE WEEK IS OVER";
      $("tt-over-sub").textContent = isPoints(nextWk)
        ? "Midnight Saturday. The board is final. Next up is a points week: every game starts at $10,000."
        : "Midnight Saturday. The board is final and a new week starts at $5,000.";
      $("tt-over-go").textContent = "SEE WHO WON";
      $("tt-over").classList.add("on");
    }
    /** Midnight under daily rules: today's money is locked in, tomorrow starts at $5,000. */
    function dayOver() {
      if (TT.closed) return;
      TT.closed = true;
      $("tt-over-big").textContent = "A NEW DAY";
      $("tt-over-sub").textContent = TT.points
        ? "Midnight. Whoever was on top of each game took its points, and every game starts fresh at $10,000."
        : "Midnight. Yesterday's profit is locked in, and today starts fresh at $5,000.";
      $("tt-over-go").textContent = "SEE THE BOARD";
      $("tt-over").classList.add("on");
    }
    $("tt-over-go").addEventListener("click", function () {
      store.setStr("ftt.showboard", "1");
      location.reload();
    });
    // once the day or week has closed, any tap brings the notice back up:
    // nothing played after that point is saved, so it shouldn't look like it is
    document.addEventListener("click", function (e) {
      if (!TT.on || !TT.closed || $("tt-over").classList.contains("on")) return;
      if (e.target.closest && e.target.closest("#tt-over")) return;
      e.preventDefault(); e.stopImmediatePropagation();
      $("tt-over").classList.add("on");
    }, true);
    setInterval(function () {
      if (frame && frame.status === "live" && TT.on && Date.parse(frame.ends_at) <= now()) weekOver();
      else if (frame && frame.daily && TT.on && TT.day && Date.parse(frame.day_ends_at) <= now()) dayOver();
    }, 15000);

    /* ---- the hot game of the day ----
     * The day's hot games pay extra: every winning hand or spin there pays its
     * profit again, in full (2×). The table keeps its own stack while you play, so the
     * second helping is held beside it, counted toward your day the moment
     * it's won, and lands in your bankroll when you get up.
     * A win's profit is read off the purse: what came back, less what went
     * out on that hand. A hand ends when the table goes quiet again.
     * The two dice games are left out: bets that stay up across rolls can't
     * be read that way.
     */
    var hot = (function () {
      var sess = null, tipEl = null, tipT = 0, poll = 0;
      var SKIP = { "Crapless Craps": 1, "Destroyer": 1 };
      // the table each game is played at, for telling when a hand is over
      var TABLE = { "Punto Banco": "Baccarat", "Dragon Tiger": "DragonTiger", "Free Bet Blackjack": "Blackjack",
        "Blazing 777": "Blackjack", "Double Down Madness": "DoubleDown", "Ultimate Texas Hold'em": "Holdem",
        "DJ Wild": "DjWild", "Shootout": "Shootout", "Mini UTH": "MiniUth", "I Luv Suits": "IluvSuits", "Kirby": "Kirby",
        "Double Zero Roulette": "Roulette", "Lightning Roulette": "Roulette", "Double Ball Roulette": "Roulette",
        "Keno": "Keno", "Money Wheel": "Wheel", "Sic Bo": "SicBo", "Plinko": "Plinko", "Mines": "Mines" };
      function tableOf(game) {
        try {
          return { Baccarat: Baccarat, DragonTiger: DragonTiger, Blackjack: Blackjack, DoubleDown: DoubleDown, Holdem: Holdem,
            DjWild: DjWild, Shootout: Shootout, MiniUth: MiniUth, IluvSuits: IluvSuits, Kirby: Kirby, Roulette: Roulette,
            Keno: Keno, Wheel: Wheel, SicBo: SicBo, Plinko: Plinko, Mines: Mines }[TABLE[game]];
        } catch (e) { return null; }
      }
      // days the house picked by hand
      var PICKED = { "2026-11-17": ["Ultimate Texas Hold'em", "Blazing 777"], "2026-11-19": ["Free Bet Blackjack", "Mini UTH"], "2026-11-20": ["Punto Banco", "Mines"],
                     "2026-11-21": ["Blazing 777", "Lightning Roulette"] };   // Justin swapped Free Bet for the 777s
      function pool() {
        return Object.keys(GAMES).map(function (id) { return GAMES[id]; }).filter(function (g) { return !SKIP[g]; });
      }
      /**
       * Everyone gets the same games: the days walk the list two at a time.
       * From Tuesday 17 November there are two a day, the second ten places
       * along from the first, so the pair never doubles up on one game.
       */
      function list(day) {
        day = day || TT.day || (frame && frame.day);
        if (!day) return [];
        var n = Math.round((Date.parse(day + "T12:00:00Z") - Date.parse("2026-01-01T12:00:00Z")) / 86400000);
        if (PICKED[day]) return PICKED[day].slice();
        // from the points week on: each week the games are shuffled once and dealt out two a day,
        // so no game is hot twice in the same week
        if (day >= "2026-11-22") {
          var t = Date.parse(day + "T12:00:00Z"), dow = new Date(t).getUTCDay();
          var wk = new Date(t - dow * 86400000).toISOString().slice(0, 10);
          var seed = 0, c;
          for (c = 0; c < wk.length; c++) seed = (seed * 31 + wk.charCodeAt(c)) >>> 0;
          var rnd = function () {
            seed = (seed + 0x6D2B79F5) >>> 0;
            var x = Math.imul(seed ^ (seed >>> 15), 1 | seed);
            x = (x + Math.imul(x ^ (x >>> 7), 61 | x)) ^ x;
            return ((x ^ (x >>> 14)) >>> 0) / 4294967296;
          };
          var deck = pool();
          for (c = deck.length - 1; c > 0; c--) { var j = Math.floor(rnd() * (c + 1)), tmp = deck[c]; deck[c] = deck[j]; deck[j] = tmp; }
          return [deck[dow * 2], deck[dow * 2 + 1]];
        }
        var p = pool(), m = p.length, i = ((n * 2) % m + m) % m;
        return day >= "2026-11-17" ? [p[i], p[(i + 10) % m]] : [p[i]];
      }
      /** The day's hot games, said out loud. */
      function today(day) { return list(day).join(" & "); }
      /** The share of a win's profit the house adds on top: all of it (2×) this week, half (1.5×) from the points week on. */
      function rate(day) { day = day || TT.day || (frame && frame.day) || ""; return day >= "2026-11-22" ? 0.5 : 1; }
      function tagX(day) { return rate(day) === 1 ? "2×" : "1.5×"; }
      function say(text, ms) {
        if (!tipEl) {
          tipEl = document.createElement("div");
          tipEl.style.cssText = "position:fixed;left:50%;top:16%;transform:translateX(-50%);z-index:9999;max-width:86vw;" +
            "padding:12px 18px;border-radius:12px;background:rgba(20,8,4,.95);border:1.5px solid #FF8A3D;color:#FFD24D;" +
            "font:800 15px/1.35 system-ui,sans-serif;letter-spacing:.04em;text-align:center;pointer-events:none;transition:opacity .3s;opacity:0";
          document.body.appendChild(tipEl);
        }
        tipEl.textContent = text; tipEl.style.opacity = "1";
        clearTimeout(tipT); tipT = setTimeout(function () { tipEl.style.opacity = "0"; }, ms || 3200);
      }
      // the second helping not yet in the bankroll, kept with the day's wallet
      function extra() { return Math.max(0, Math.round(store.get(k("hotextra"), 0))); }
      function setExtra(v) { store.set(k("hotextra"), Math.max(0, Math.round(v))); }
      function sit(game) {
        end();
        if (!TT.on || !TT.day || TT.closed || list().indexOf(game) < 0) return;
        sess = { game: game, screen: null, out: 0, busy: false, table: tableOf(game) };
        clearInterval(poll);
        poll = setInterval(function () {
          if (!sess || !sess.table || !sess.table.busy) return;
          var b = !!sess.table.busy();
          if (sess.busy && !b) sess.out = 0;      // that hand is over: what's left on it was lost
          sess.busy = b;
        }, 200);
        say("🔥 HOT GAME — every win at " + game.toUpperCase() + " pays " + tagX() + " today");
      }
      function track(delta) {
        if (!sess || TT.game !== sess.game) return;
        if (delta < 0) {
          sess.out += -delta;
          // a new bet going down: the last hand's 2x sum comes off the money window
          var pw = document.querySelector(".screen.on .purse[data-hot]");
          if (pw && window.purseWin) purseWin.clear(pw);
          return;
        }
        var won = Math.max(0, delta - sess.out);
        sess.out = Math.max(0, sess.out - delta);
        var profit = Math.floor(won * rate());
        if (profit <= 0) return;
        var table = sess.table, game = sess.game;
        // the win pays again, onto the table's own stack, once the table has finished paying it
        setTimeout(function () {
          if (!TT.on || TT.closed) return;
          if (table && table.credit) {
            TT.quiet = true;
            try { table.credit(profit); } finally { TT.quiet = false; }
            TT.changed(true);
          } else setExtra(extra() + profit);
          mark(profit, won);
        }, 0);
      }
      /**
       * The extra shows as one more result pill under the table's own, where the
       * table has pills; elsewhere as a small tag under the bankroll that fades.
       */
      function mark(profit, won) {
        var scr = document.querySelector(".screen.on");
        var x = "🔥 ×" + tagX().replace("×", ""), amt = "+" + fmt(profit);
        // any table with a money window: the doubling shows up there (a pill in the felt pushes the bets down)
        if (scr && scr.querySelector(".purse") && window.purseWin) {
          purseWin.hot(scr.querySelector(".purse"), won, "×" + tagX().replace("×", ""), won + profit);
          return;
        }
        var host = scr && scr.querySelector(".dj-pills, #dd-results");
        if (host && host.children.length) {
          host.insertAdjacentHTML("beforeend", '<div class="dj-pill w" style="border-color:#FF8A3D"><span class="nm" style="color:#FFB238">' +
            x + '</span><span class="amt">' + amt + "</span></div>");
          return;
        }
        // tables with no money window (roulette, craps): said plainly, in the middle, long enough to read
        var t = document.createElement("div");
        t.textContent = "🔥 HOT GAME " + tagX() + " · " + amt + " MORE";
        t.style.cssText = "position:fixed;left:50%;transform:translateX(-50%);top:calc(env(safe-area-inset-top,0px) + 96px);z-index:9999;white-space:nowrap;" +
          "padding:8px 16px;border-radius:999px;border:2px solid #FF8A3D;background:rgba(20,8,4,.95);color:#FFD24D;" +
          "font:800 15px/1.3 system-ui,sans-serif;letter-spacing:.04em;pointer-events:none;transition:opacity .4s;opacity:1;box-shadow:0 0 18px rgba(255,120,40,.6)";
        document.body.appendChild(t);
        setTimeout(function () { t.style.opacity = "0"; }, 4200);
        setTimeout(function () { t.remove(); }, 4800);
      }
      /** Up from the table: the bonuses won there go into the bankroll. */
      function end() {
        clearInterval(poll);
        var game = sess && sess.game;
        sess = null;
        var won = extra();
        if (!won || !TT.on || TT.closed) return;
        setExtra(0);
        TT.quiet = true;
        Shell.setBankroll(Shell.getBankroll() + won);
        TT.quiet = false;
        TT.changed(true);
        say("🔥 HOT GAME BONUSES +$" + fmt(won) + " ADDED TO YOUR BANKROLL" + (game ? "" : ""));
      }
      // the first screen after sitting down is the table; any other screen means you got up
      var show = Shell.show;
      Shell.show = function (name) {
        var r = show.apply(this, arguments);
        if (sess) { if (!sess.screen) sess.screen = name; else if (name !== sess.screen) end(); }
        if (name === "lobby") paintBanner();
        paintBadges();
        return r;
      };
      /* ---- fire on a hot game's PLAY circle: embers thrown off the rim that rise, narrow and burn out ---- */
      var SPARK = null, fireRaf = 0, fireLast = 0;
      function sprite(rgb) {
        var c = document.createElement("canvas"); c.width = c.height = 64;
        var x = c.getContext("2d"), g = x.createRadialGradient(32, 32, 0, 32, 32, 32);
        g.addColorStop(0, "rgba(" + rgb + ",1)"); g.addColorStop(0.35, "rgba(" + rgb + ",.55)"); g.addColorStop(1, "rgba(" + rgb + ",0)");
        x.fillStyle = g; x.fillRect(0, 0, 64, 64); return c;
      }
      function burn() { if (!fireRaf) { fireLast = 0; fireRaf = requestAnimationFrame(fireFrame); } }
      function fireFrame(now) {
        fireRaf = 0;
        var cvs = document.querySelectorAll(".tt-fire"), live = 0;
        if (!cvs.length) return;
        if (!SPARK) SPARK = [sprite("255,226,120"), sprite("255,140,24"), sprite("236,62,8"), sprite("120,22,4")];
        var dt = Math.min(0.05, fireLast ? (now - fireLast) / 1000 : 0.016); fireLast = now;
        for (var i = 0; i < cvs.length; i++) {
          var cv = cvs[i], pl = cv.parentNode;
          if (!pl || cv.offsetParent === null) continue;                       // its menu isn't up: nothing to draw
          live++;
          var d = pl.offsetWidth; if (!d) continue;
          var dpr = Math.min(2, window.devicePixelRatio || 1), W = d * 2.2, H = d * 2.15, cx = W / 2, cy = H - d * 0.78, R = d / 2;
          if (cv._d !== d) { cv._d = d; cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr); cv.style.width = W + "px"; cv.style.height = H + "px";
                             cv.style.left = (d - W) / 2 - pl.clientLeft + "px"; cv.style.top = (R - cy) - pl.clientTop + "px"; cv._p = []; }
          var P = cv._p, c = cv.getContext("2d"), k, p;
          // feed it all round the rim; what starts low climbs the sides, what starts high makes the tips
          for (var n = 0; n < 8; n++) {
            var a = Math.random() * 6.283, rr = R * (0.98 + Math.random() * 0.2), up = Math.sin(a) < 0 ? 1 : 0.75;
            P.push({ x: cx + Math.cos(a) * rr, y: cy + Math.sin(a) * rr, vx: Math.cos(a) * d * 0.3, vy: -d * (0.5 + Math.random() * 0.9) * up,
                     age: 0, life: 0.38 + Math.random() * 0.42, s: d * (0.2 + Math.random() * 0.2), ph: Math.random() * 6.28 });
          }
          c.setTransform(dpr, 0, 0, dpr, 0, 0); c.globalCompositeOperation = "source-over"; c.clearRect(0, 0, W, H); c.globalCompositeOperation = "lighter";
          for (k = P.length - 1; k >= 0; k--) {
            p = P[k]; p.age += dt;
            var t = p.age / p.life;
            if (t >= 1) { P.splice(k, 1); continue; }
            p.vx += ((cx - p.x) * 2.4 + Math.sin(now / 110 + p.ph) * d * 2.2) * dt;          // lean in to a point, and waver
            p.vx *= 1 - 2.5 * dt; p.vy -= d * 1.0 * dt;
            p.x += p.vx * dt; p.y += p.vy * dt;
            var sz = p.s * (1.15 - t * 0.85);
            c.globalAlpha = (t < 0.1 ? t / 0.1 : 1 - (t - 0.1) / 0.9) * 0.5;
            c.drawImage(SPARK[t < 0.22 ? 0 : t < 0.5 ? 1 : t < 0.8 ? 2 : 3], p.x - sz / 2, p.y - sz / 2, sz, sz);
          }
          // nothing burns inside the circle itself
          c.globalAlpha = 1; c.globalCompositeOperation = "destination-out";
          c.beginPath(); c.arc(cx, cy, R - 1.5, 0, 6.283); c.fill();
          c.globalAlpha = 1;
        }
        if (live && !(window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches)) fireRaf = requestAnimationFrame(fireFrame);
        else if (!live) setTimeout(function () { if (document.querySelector(".tt-fire")) burn(); }, 500);     // look again when a menu opens
      }
      /** A 🔥 2× tag on the hot game's own button, wherever it's listed. */
      function paintBadges() {
        var g = TT.on && TT.day ? list() : [];
        Object.keys(GAMES).forEach(function (id) {
          var btn = $(id);
          if (!btn) return;
          var tag = btn.querySelector(".tt-hot2x");
          var on = g.indexOf(GAMES[id]) >= 0;
          var pl = btn.querySelector(".pk-play, .play"), fire = pl && pl.querySelector(".tt-fire");
          var mx = pl && pl.querySelector(".tt-x");
          if (!on) { if (tag) tag.remove(); if (fire) fire.remove(); if (mx) mx.remove(); btn.classList.remove("tt-hotbtn"); return; }
          if (pl && !mx) { mx = document.createElement("span"); mx.className = "tt-x"; pl.appendChild(mx); }
          if (mx && mx.textContent !== tagX() + "$") mx.textContent = tagX() + "$";
          btn.classList.add("tt-hotbtn");
          if (pl && !fire) { fire = document.createElement("canvas"); fire.className = "tt-fire"; fire.setAttribute("aria-hidden", "true"); pl.insertBefore(fire, pl.firstChild); }
          burn();
          if (tag) tag.remove();          // the old pill: the gold number on the circle says it now
        });
      }
      function paintBanner() {
        var line = $("lb-campaign-line"), el = $("tt-hotline");
        // the lobby no longer announces the hot games: the front page's tags and the fire on the games' own buttons do
        if (el) el.remove();
        return;
        if (!line) return;
        if (!el) {
          el = document.createElement("div"); el.id = "tt-hotline";
          el.style.cssText = "margin:8px 0 2px;padding:9px 12px;border-radius:12px;border:1.5px solid #FF8A3D;" +
            "background:linear-gradient(90deg,rgba(255,110,40,.22),rgba(255,210,77,.12));color:#FFD24D;" +
            "font:800 12px/1.3 system-ui,sans-serif;letter-spacing:.08em;text-align:center";
          line.parentNode.insertBefore(el, line.nextSibling);
        }
        var g = TT.on && TT.day ? today() : "";
        el.hidden = !g;
        el.style.display = g ? "" : "none";
        if (g) el.textContent = "🔥 " + (g.indexOf(" & ") >= 0 ? "HOT GAMES" : "HOT GAME") + " TODAY · " + g.toUpperCase() + " · WINS PAY " + tagX();
      }
      return { today: today, list: list, tagX: tagX, sit: sit, track: track, end: end, extra: extra, setExtra: setExtra, tableOf: tableOf,
               banner: function () { paintBanner(); paintBadges(); } };
    })();

    // ---- the front-page lane ----
    function laneSay(line, play, playOn) {
      $("tt-line").textContent = line;
      $("tt-play").textContent = play;
      $("tt-play").disabled = !playOn;
      // one plain line: the tidy card's pieces step aside
      $("tt-line").hidden = false; $("tt-tag").hidden = false;
      ["tt-place", "tt-hots", "tt-stats", "tt-lane-days"].forEach(function (id) { $(id).hidden = true; });
    }
    /** One cell per day of the week with the face of whoever won it (an empty ring if nobody has yet); the board leads with last week's champ. */
    function dayStrip(host, b, withChamp) {
      host.innerHTML = "";
      var cell = function (cls, name, label, tip) {
        var c = document.createElement("span"); c.className = "tt-day " + cls;
        if (name) { var im = document.createElement("img"); im.alt = ""; im.src = faceOf(String(name).toLowerCase()); c.appendChild(im); }
        else c.appendChild(document.createElement("i"));
        var l = document.createElement("b"); l.textContent = label; c.appendChild(l);
        if (tip) c.title = tip;
        return c;
      };
      var lc = b.last_champion, champName = lc && (isPoints(lc.week) ? ((pointsCache[lc.week] || [])[0] || {}).name : lc.name);
      if (withChamp && champName) host.appendChild(cell("champ", champName, "CHAMP", shown(champName) + " won last week"));
      var won = {}; (b.daily_winners || []).forEach(function (w) { won[w.day] = w; });
      for (var di = 0; di < 7; di++) {
        var dt = new Date(b.week + "T12:00:00Z"); dt.setUTCDate(dt.getUTCDate() + di);
        var w = won[dt.toISOString().slice(0, 10)];
        host.appendChild(w ? cell("", w.name, DAYS[di].charAt(0), shown(w.name) + " " + signedS(w.profit)) : cell("no", null, DAYS[di].charAt(0)));
      }
    }
    var todayLead = null;  // who leads each game today, for the front page's "today's points"
    var hotGo = null;      // a hot game's tag was tapped on the front page: the room to open once you're in
    function nth(n) { var t = n % 100; return n + (t > 10 && t < 14 ? "th" : ["th", "st", "nd", "rd"][n % 10] || "th"); }
    /**
     * The front page once you're playing: your place beside the title, today's hot games under it,
     * then the day, one number of yours, and a second in a box over BOARD.
     */
    function laneCard(c) {
      $("tt-play").textContent = "PLAY"; $("tt-play").disabled = false;
      $("tt-line").hidden = true; $("tt-tag").hidden = true;
      $("tt-place").hidden = !c.place; $("tt-place-n").textContent = c.place ? nth(c.place) : "";
      var hots = $("tt-hots"); hots.hidden = !c.hot.length; hots.innerHTML = "";
      // each hot game is a button: into the tournament and straight to that game's room
      c.hot.forEach(function (g) {
        var t = document.createElement("button"); t.type = "button"; t.className = "g"; t.textContent = "🔥 " + g.toUpperCase();
        t.onclick = function () {
          var id = Object.keys(GAMES).filter(function (i) { return GAMES[i] === g; })[0] || "";
          var room = { bm: "bacmenu", bjm: "bjmenu", pk: "poker", rlm: "rlmenu", cm: "crapsmenu", om: "othermenu" }[id.split("-")[0]];
          if ($("tt-play").disabled) return;
          hotGo = room || null;
          $("tt-play").click();
        };
        hots.appendChild(t);
      });
      var x = document.createElement("b"); x.textContent = c.x + "$"; hots.appendChild(x);
      var ld = $("tt-lane-days"); ld.hidden = !c.week;
      if (c.week) dayStrip(ld, c.week, false);
      $("tt-stats").hidden = false;
      $("tt-st-day").textContent = c.day;
      $("tt-st-k").textContent = c.k; $("tt-st-v").textContent = c.v; $("tt-st-v").className = c.cls || "";
      $("tt-st-bk").textContent = c.bk; $("tt-st-bv").textContent = c.bv;
    }
    var roster = null;   // everyone signed up: names only
    function refreshLane() {
      if (!ready) return laneSay("NOT SET UP YET", "SOON", false);
      // standings as of last midnight for everyone; your own money live, for you only
      var ask = myName() ? rpc("c4tb_standings", { p_name: myName(), p_token: token() }) : rpc("c4tb_board", {});
      rpc("c4tb_roster", {}).then(function (r) { roster = r; paintLane(); if ($("tt-board").classList.contains("on")) paintBoard(); }, function () {});
      return ask.then(function (b) {
        board = b; frame = b;
        paintLane();
        if (!$("tt-rules").classList.contains("on")) checkPin();
        if (store.getStr("ftt.showboard", "")) { store.setStr("ftt.showboard", ""); showBoard(); }
        if ($("tt-board").classList.contains("on")) paintBoard();
        return b;
      }, function () { laneSay("CAN'T REACH THE TOURNAMENT", "RETRY", true); });
    }
    function paintLane() {
      var b = board, me = myName();
      if (!b) return;
      if (b.status === "upcoming") {
        $("tt-tag").textContent = "WEEK 1 · " + b.players.length + " SIGNED UP";
        return laneSay("STARTS SUNDAY 12:00 AM · IN " + left(b.starts_at),
                       me ? "YOU'RE IN · " + me.toUpperCase() : "JOIN", !me);
      }
      $("tt-tag").textContent = "WEEK " + b.week_no + " · " + (b.day_no === 7 ? "FINAL DAY" : "DAY " + b.day_no + " OF 7") +
        (roster ? " · " + roster.length + " PLAYERS" : "");
      // the first time the app is opened in a new week, the rules come up
      var seenRules = "ftt.rules" + (isPoints(b.week) ? "3." : b.daily ? "2." : ".") + b.week;
      if (!store.getStr(seenRules, "")) { store.setStr(seenRules, "1"); showRules(b); }
      var you = b.me, rank = 0;
      if (!b.final) b.rows.forEach(function (r, i) { if (me && r.name.toLowerCase() === me.toLowerCase()) rank = i + 1; });
      if (!me) return laneSay("ENDS IN " + left(b.ends_at), "JOIN", true);
      if (you && you.out && !b.daily) return laneSay("OUT FOR THE WEEK", "OUT", false);
      if ((!you || you.fresh) && !isPoints(b.week)) return laneSay("FRESH $" + fmt(b.start_cash) + " WAITING · ENDS IN " + left(b.ends_at), "PLAY", true);
      if (isPoints(b.week)) {
        var pb = pointsCache[b.week], mine2 = null, rk = 0;
        if (!pb) getPoints(b.week).then(paintLane);
        (pb || []).forEach(function (r, i) { if (r.name.toLowerCase() === me.toLowerCase()) { mine2 = r; rk = i + 1; } });
        // today's points: what the games you lead right now are worth (yours at midnight if nobody passes you)
        if (!todayLead || todayLead.day !== b.day || Date.now() - todayLead.at > 30000) {
          var keep = todayLead && todayLead.day === b.day ? todayLead.rows : [];
          todayLead = { day: b.day, at: Date.now(), rows: keep };
          rpc("c4tb_game_leaders", { p_day: b.day }).then(function (j) { todayLead.rows = Array.isArray(j) ? j : []; paintLane(); }, function () {});
        }
        var todayPts = 0;
        todayLead.rows.forEach(function (x) { if (String(x.name).toLowerCase() === me.toLowerCase()) todayPts += gpts(b.day, x.game); });
        var wkPts = mine2 ? mine2.points : 0;
        return laneCard({ place: mine2 && mine2.points > 0 ? rk : 0, hot: hot.list(b.day), x: hot.tagX(b.day), day: b.day_no + " OF 7",
                          k: "TODAY'S POINTS", v: todayPts + (todayPts === 1 ? " PT" : " PTS"), cls: "gold",
                          bk: "WEEK'S POINTS", bv: wkPts + (wkPts === 1 ? " PT" : " PTS") });
      }
      if (b.daily && you.out) return laneSay("OUT FOR TODAY · FRESH $" + fmt(b.start_cash) + " AT MIDNIGHT", "OUT", false);
      if (b.daily) return laneCard({ place: rank, hot: hot.list(b.day), x: hot.tagX(b.day), day: b.day_no + " OF 7",
                                     k: "TODAY", v: signedS(you.today), cls: you.today > 0 ? "up" : you.today < 0 ? "down" : "",
                                     bk: "BEST 3 DAYS", bv: moneyS(you.score || 0), week: b });
      laneSay(money(you.net) + " · TODAY " + signed(you.today) +
              (rank ? " · #" + rank + " AT MIDNIGHT" : ""), "PLAY", true);
    }

    // ---- game stats: every move of the purse, put down to the table it happened at ----
    var GAMES = {
      "bm-punto": "Punto Banco", "bm-dragontiger": "Dragon Tiger",
      "bjm-freebet": "Free Bet Blackjack", "bjm-blazing": "Blazing 777", "bjm-doubledown": "Double Down Madness",
      "pk-uth": "Ultimate Texas Hold'em", "pk-djwild": "DJ Wild", "pk-shootout": "Shootout",
      "pk-mini": "Mini UTH", "pk-suits": "I Luv Suits", "pk-kirby": "Kirby",
      "rlm-classic": "Double Zero Roulette", "rlm-lightning": "Lightning Roulette", "rlm-doubleball": "Double Ball Roulette",
      "cm-crapless": "Crapless Craps", "cm-destroyer": "Destroyer",
      "om-keno": "Keno", "om-wheel": "Money Wheel", "om-sicbo": "Sic Bo", "om-plinko": "Plinko", "om-mines": "Mines"
    };
    var statsDirty = false;
    // capture, so the game is named before its own click handler deals the first hand
    document.addEventListener("click", function (e) {
      var b = e.target.closest && e.target.closest("button[id]");
      if (b && GAMES[b.id]) { TT.game = GAMES[b.id]; hot.sit(TT.game); }
    }, true);
    function readStats() {
      try { return JSON.parse(store.getStr(kw("stats"), "{}")) || {}; } catch (e) { return {}; }
    }
    /** The purse moved by [delta] at the table you are at. Markers do not count. */
    TT.track = function (delta) {
      if (!TT.on || TT.quiet || !TT.game || !delta || !TT.week) return;
      var s = readStats(), g = s[TT.game] || (s[TT.game] = { net: 0, best: 0 });
      g.net += delta;
      if (delta > g.best) g.best = delta;
      store.setStr(kw("stats"), JSON.stringify(s));
      statsDirty = true;
      hot.track(delta);
      gifts.track(delta);
      shout.track(delta);
      duel.track(delta);
    };
    /* ---- giveaway prizes from the message board: free chips, and 2x tokens ----
       Chips go into your tournament money (at once if you're in, otherwise the next time you are).
       A 2x token waits in your pocket: after any winning hand a button offers to double that win. */
    var gifts = (function () {
      var TOK = "ftt.x2tokens", OWED = "ftt.owedchips";
      var hand = { out: 0, won: 0, busy: false }, btn = null;
      function num(key) { return Math.max(0, Math.round(+store.getStr(key, "0") || 0)); }
      function tokens() { return num(TOK); }
      function atTable() { return !!document.querySelector(".screen.on .purse"); }
      function toast(text) {
        var t = document.createElement("div");
        t.textContent = text;
        t.style.cssText = "position:fixed;left:50%;transform:translateX(-50%);top:calc(env(safe-area-inset-top,0px) + 74px);z-index:9999;" +
          "padding:6px 14px;border-radius:999px;border:1.5px solid #FFD24D;background:rgba(20,14,4,.95);color:#FFD24D;" +
          "font:800 12px/1.3 system-ui,sans-serif;letter-spacing:.04em;pointer-events:none;transition:opacity .4s;opacity:1;white-space:nowrap";
        document.body.appendChild(t);
        setTimeout(function () { t.style.opacity = "0"; }, 3000);
        setTimeout(function () { t.remove(); }, 3600);
      }
      function credit(x) {
        var w = typeof Walkups !== "undefined" ? Walkups : null;
        if (atTable() && w && w.give(x)) return;
        TT.quiet = true;
        try { Shell.setBankroll(Shell.getBankroll() + x); } finally { TT.quiet = false; }
        TT.changed(true);
      }
      /** Chips won but not yet handed over go in as soon as the tournament money is open. */
      function deliver() {
        var owe = num(OWED);
        if (!owe || !TT.on || TT.closed || !TT.week) return;
        if (TT.points && !atTable()) return;       // points week: into the game you sit down at
        store.setStr(OWED, "0");
        credit(owe);
        toast("🎁 FREE CHIPS  +$" + fmt(owe));
      }
      function award(kind, amount) {
        if (kind === "chips") { store.setStr(OWED, String(num(OWED) + Math.round(amount))); deliver(); }
        else if (kind === "x2") { store.setStr(TOK, String(tokens() + Math.round(amount))); toast("🎟 2× TOKEN ADDED · YOU HAVE " + tokens()); }
      }
      function button() {
        if (!btn) {
          btn = document.createElement("button");
          btn.type = "button"; btn.id = "tt-x2"; btn.style.display = "none";
          btn.onclick = use;
          document.body.appendChild(btn);
        }
        return btn;
      }
      function paint() {
        var b = button(), on = hand.won > 0 && tokens() > 0 && TT.on && !TT.closed && atTable();
        b.style.display = on ? "" : "none";
        if (on) b.textContent = "🎟 DOUBLE IT  +$" + (typeof pillAmt === "function" ? pillAmt(hand.won) : fmt(hand.won)) + " · " + tokens() + " LEFT";
      }
      /** Spend a token: the hand you just won pays again. */
      function use() {
        var won = hand.won;
        if (!(won > 0) || tokens() < 1 || !TT.on || TT.closed) { paint(); return; }
        hand.won = 0;
        store.setStr(TOK, String(tokens() - 1));
        credit(won);
        toast("🎟 2× TOKEN  +$" + fmt(won));
        paint();
      }
      /** The same sums the hot games use: what came back on a hand, less what went out on it. */
      function track(delta) {
        if (delta < 0) { hand.out += -delta; if (hand.won) { hand.won = 0; paint(); } return; }
        var won = Math.max(0, delta - hand.out);
        hand.out = Math.max(0, hand.out - delta);
        if (won > 0) { hand.won += won; paint(); }
      }
      setInterval(function () {
        var t = TT.game ? hot.tableOf(TT.game) : null, b = !!(t && t.busy && t.busy());
        if (hand.busy && !b) hand.out = 0;     // the hand is over: what's left on it was lost
        hand.busy = b;
        if (!atTable() && hand.won) hand.won = 0;
        paint();
        deliver();
      }, 400);
      return { track: track, award: award, tokens: tokens, deliver: deliver };
    })();
    TT.gifts = gifts;

    /* ---- shout-outs: a hit of 50x the bet or more is announced at everybody else's table ----
     * What went out on the hand is counted until the table goes quiet; a payout
     * that clears it by 50 times over is sent up, and everyone else's phone
     * picks it up on its next look. */
    var shout = (function () {
      var out = 0, last = 0, polling = 0;
      function track(delta) {
        if (delta < 0) { out += -delta; return; }
        var won = delta - out, bet = out;
        out = Math.max(0, out - delta);
        if (bet <= 0 || won < bet * 50) return;
        rpc("c4tb_shout", { p_name: myName(), p_token: token(), p_game: TT.game || "a table",
          p_amount: Math.round(won), p_mult: Math.floor(won / bet) }).catch(function () {});
      }
      function look() {
        if (!TT.on || typeof Walkups === "undefined") return;
        var after = Math.round(store.get("ftt.shout.last", 0));
        rpc("c4tb_shouts_since", { p_after: after }).then(function (rows) {
          if (!Array.isArray(rows)) return;
          var me = (myName() || "").toLowerCase();
          rows.forEach(function (x) {
            if (x.id > after) store.set("ftt.shout.last", x.id);
            if (x.name.toLowerCase() !== me) Walkups.announce(x.name, x.game, x.amount, x.mult);
          });
        }, function () {});
      }
      polling = setInterval(look, 15000);
      setTimeout(look, 4000);
      return { track: track, handDone: function () { out = 0; } };
    })();
    /* ---- the points week: leaders on every game button, free rebuys ---- */
    var points = (function () {
      var lead = {}, timerL = 0;
      function leaders() {
        if (!TT.on || !TT.points || !TT.day) return paint();
        rpc("c4tb_game_leaders", { p_day: TT.day }).then(function (rows) {
          lead = {};
          (rows || []).forEach(function (x) { lead[x.game] = x; });
          paint();
        }, function () {});
      }
      /** The leader's face in each game's PLAY circle, their money under it, the game's points on the corner. */
      function paint() {
        Object.keys(GAMES).forEach(function (id) {
          var btn = $(id);
          if (!btn) return;
          var old = btn.querySelector(".tt-lead"); if (old) old.remove();          // the tag builds before 1.64 hung here
          var pl = btn.querySelector(".pk-play, .play");
          if (!pl) return;
          var face = pl.querySelector(".tt-lf"), amt = pl.querySelector(".tt-la"), pt = btn.querySelector(".tt-lp");
          if (pt && pt.parentNode !== btn) { pt.remove(); pt = null; }               // it used to ride on the circle
          if (!TT.on || !TT.points || !TT.day) { [face, amt, pt].forEach(function (e) { if (e) e.remove(); }); pl.classList.remove("tt-led"); return; }
          var g = GAMES[id], x = lead[g], pts = gpts(TT.day, g);
          if (!pt) { pt = document.createElement("span"); pt.className = "tt-lp"; btn.appendChild(pt); }
          if (getComputedStyle(btn).position === "static") btn.style.position = "relative";
          pt.textContent = pts + " PT" + (pts > 1 ? "S" : "");
          pl.classList.toggle("tt-led", !!x);
          if (!x) { if (face) face.remove(); if (amt) amt.remove(); return; }
          var src = faceOf(String(x.name).toLowerCase());
          if (!face) { face = document.createElement("img"); face.className = "tt-lf"; face.alt = ""; pl.appendChild(face); }
          if (face.getAttribute("src") !== src) face.src = src;
          if (!amt) { amt = document.createElement("span"); amt.className = "tt-la"; pl.appendChild(amt); }
          amt.textContent = "$" + (x.cash >= 1e6 && typeof shortAmt === "function" ? shortAmt(x.cash) : fmt(x.cash));
          btn.title = shown(x.name);
        });
      }
      clearInterval(timerL);
      timerL = setInterval(function () { if (TT.on && TT.points && !document.hidden) leaders(); }, 30000);
      document.addEventListener("visibilitychange", function () { if (!document.hidden && TT.on && TT.points) leaders(); });
      // back at a menu: fresh numbers
      var showScreen = Shell.show;
      Shell.show = function (name) {
        var r = showScreen.apply(this, arguments);
        if (TT.on && TT.points && !/^(holdem|djwild|shootout|miniuth|iluvsuits|destroyer|sicbo|roulette|wheel|keno|mines|blackjack|doubledown|baccarat|dragontiger|plinko|kirby|craps)$/.test(name)) leaders();
        return r;
      };

      /* Busted at a game: rebuy back to a fresh $10,000, as if the first never happened. */
      var btn = null;
      function rebuy() {
        var w = typeof Walkups !== "undefined" ? Walkups : null;
        // the table's own stack when you're at one (it's what the rebuy tops up)
        var have = w && w.stack() ? w.stack() : Shell.getBankroll(), add = POINTS_CASH - have;
        if (add <= 0) return;
        if (!(w && w.give(add))) {
          TT.quiet = true;
          try { Shell.setBankroll(POINTS_CASH); } finally { TT.quiet = false; }
          TT.changed(true);
        }
      }
      TT.rebuy = rebuy;
      function rebuyBtn() {
        if (!btn) {
          btn = document.createElement("button");
          btn.type = "button"; btn.id = "tt-rebuy"; btn.textContent = "💸 REBUY · FRESH $10,000";
          btn.style.display = "none";
          btn.onclick = function () { rebuy(); btn.style.display = "none"; };
          document.body.appendChild(btn);
        }
        return btn;
      }
      setInterval(function () {
        var b = rebuyBtn(), on = document.querySelector(".screen.on");
        var atTable = on && on.id !== "screen-menu" && on.id !== "screen-lobby" && TT.game &&
          /holdem|djwild|shootout|miniuth|iluvsuits|destroyer|sicbo|roulette|wheel|keno|mines|blackjack|doubledown|baccarat|dragontiger|plinko|kirby|craps/.test(on.id);
        var lim = Shell.limits ? Shell.limits() : null, min = Math.max(25, (lim && lim.min) || 0);
        var show = TT.on && TT.points && !TT.closed && atTable && Shell.getBankroll() < min;
        b.style.display = show ? "" : "none";
      }, 500);
      return { leaders: leaders, paint: paint };
    })();

    /* ---- duels: 10 hands of the same game against another player, for a pot ----
     * The server keeps the duel; the phone counts its own hands and money at
     * that game, and moves the pot in and out of the wallet when the server
     * says to (once each way). */
    // duels are switched off for now: no new challenges, nothing on the board.
    // One already under way still plays out and pays, so no money gets stuck.
    var DUELS_ON = false;
    var duel = (function () {
      var mine = [], cur = 0, curBet = 0, touched = false, asked = {}, told = {}, claiming = {};
      function prog() { try { return JSON.parse(store.getStr("ftt.duelprog", "{}")) || {}; } catch (e) { return {}; } }
      function setProg(p) { store.setStr("ftt.duelprog", JSON.stringify(p)); }
      function me() { return (myName() || "").toLowerCase(); }
      function isC(d) { return String(d.challenger).toLowerCase() === me(); }
      function them(d) { return isC(d) ? d.opponent : d.challenger; }
      function live() { return mine.filter(function (d) { return d.status === "active"; })[0] || null; }
      function W() { return typeof Walkups !== "undefined" ? Walkups : null; }
      function give(x) {
        if (!x) return;
        var w = W();
        if (w && w.give(x)) return;
        TT.quiet = true;
        try { Shell.setBankroll(Math.max(0, Shell.getBankroll() + x)); } finally { TT.quiet = false; }
        TT.changed(true);
      }
      function poll() {
        // challenges reach you anywhere in the game once you've joined; money only moves inside the tournament
        if (!myName() || (TT.on && TT.closed)) return;
        rpc("c4tb_duels_mine", { p_name: myName(), p_token: token() }).then(function (list) {
          if (!Array.isArray(list)) return;
          mine = list;
          list.forEach(handle);
          pill();
        }, function () {});
      }
      function handle(d) {
        var c = isC(d), w = W(), other = them(d);
        if (!w) return;
        if (d.status === "pending" && !c && !asked[d.id] && DUELS_ON) {
          asked[d.id] = 1;
          w.ask(other, "⚔️ DUEL", other.toUpperCase() + " challenges you: 10 hands of " + String(d.game).toUpperCase() +
            ", $" + fmt(d.pot) + " each. Best return (profit per dollar bet) takes $" + fmt(d.pot * 2) + ".", "ACCEPT", "DECLINE",
            function () { answer(d, true); }, function () { answer(d, false); }, function () { delete asked[d.id]; });
        }
        if (d.status === "declined" && c && !told["dec" + d.id] && !store.getStr("ftt.duel.dec." + d.id, "")) {
          told["dec" + d.id] = 1;
          store.setStr("ftt.duel.dec." + d.id, "1");   // once, ever, not once a visit
          w.notice("bad", other, "⚔️ DUEL", other.toUpperCase() + " turned down your duel.");
        }
        if (!TT.on) return;   // the wallet isn't loaded yet: the pot moves when you tap PLAY
        if (d.status === "active" && !(c ? d.c_debited : d.o_debited)) {
          claim(d, "debit", function () {
            if (c) w.notice("friend", other, "⚔️ DUEL ON", other.toUpperCase() + " accepted! 10 hands of " +
              String(d.game).toUpperCase() + ". $" + fmt(d.pot) + " is in the pot.");
          });
        }
        if ((d.status === "done" || d.status === "expired") && !(c ? d.c_credited : d.o_credited)) {
          claim(d, "credit", function (r) { result(r.duel || d, r.amount); });
        }
      }
      function claim(d, kind, then) {
        var key = d.id + kind;
        if (claiming[key]) return;
        claiming[key] = 1;
        rpc("c4tb_duel_claim", { p_name: myName(), p_token: token(), p_id: d.id, p_kind: kind }).then(function (r) {
          if (r && r.ok) { give(r.amount); if (then) then(r); }
        }, function () {}).then(function () { delete claiming[key]; });
      }
      function answer(d, yes) {
        var have = TT.on ? Shell.getBankroll() : (board && board.me ? (board.me.cash != null ? board.me.cash : board.me.net) : Infinity);
        if (yes && have < d.pot) {
          W().notice("bad", them(d), "⚔️ DUEL", "You need $" + fmt(d.pot) + " to take this duel.");
          return;
        }
        rpc("c4tb_duel_answer", { p_name: myName(), p_token: token(), p_id: d.id, p_accept: yes }).then(function () { poll(); }, function () {});
      }
      function result(d, amount) {
        var w = W(), other = them(d), c = isC(d);
        var my = pct(c ? d.c_net : d.o_net, c ? d.c_bet : d.o_bet), his = pct(c ? d.o_net : d.c_net, c ? d.o_bet : d.c_bet);
        var myH = c ? d.c_hands : d.o_hands, hisH = c ? d.o_hands : d.c_hands;
        var sc = " (" + my + " vs " + his + " return)", text, kind = "good", snd = "win";
        if (d.status === "expired") { text = "The duel with " + other.toUpperCase() + " ran out at midnight. Pot's back to you."; kind = "friend"; snd = ""; }
        else if (!d.winner) { text = "Tie with " + other.toUpperCase() + sc + ". You get your $" + fmt(d.pot) + " back."; kind = "friend"; snd = ""; }
        else if (String(d.winner).toLowerCase() === me())
          text = (hisH < 10 ? other.toUpperCase() + " never finished their 10 hands. " : "You beat " + other.toUpperCase() + sc + ". ") +
            "You take $" + fmt(d.pot * 2) + "!";
        else { text = (myH < 10 ? "You didn't finish your 10 hands. " : other.toUpperCase() + " beat you" + sc + ". ") + "The $" + fmt(d.pot * 2) + " pot is theirs."; kind = "bad"; snd = "lose"; }
        w.notice(kind, other, "⚔️ DUEL OVER", text, snd);
      }
      /** Money moved at the table you're at. */
      function track(delta) {
        var d = live();
        if (!d || TT.game !== d.game) return;
        var p = prog()[d.id];
        if (p && p.hands >= 10) return;
        cur += delta; touched = true;
        if (delta < 0) curBet += -delta;
      }
      /** A hand just finished: if it was a duel hand, count it. */
      function handDone() {
        var d = live();
        if (!d || !touched || TT.game !== d.game) { cur = 0; curBet = 0; touched = false; return; }
        var all = prog(), p = all[d.id] || { hands: 0, net: 0, bet: 0 };
        if (p.hands >= 10) { cur = 0; curBet = 0; touched = false; return; }
        p.hands++; p.net += cur; p.bet = (p.bet || 0) + curBet; all[d.id] = p; setProg(all);
        cur = 0; curBet = 0; touched = false;
        rpc("c4tb_duel_progress2", { p_name: myName(), p_token: token(), p_id: d.id, p_hands: p.hands, p_net: p.net, p_bet: p.bet })
          .then(function (r) { if (r && r.duel) { mine = mine.map(function (x) { return x.id === d.id ? r.duel : x; }); handle(r.duel); } pill(); }, function () {});
        if (p.hands === 10 && W()) W().notice("friend", them(d), "⚔️ DUEL", "That's your 10 hands: " + pct(p.net, p.bet) +
          " return. Waiting on " + them(d).toUpperCase() + ".");
        pill();
      }
      var pillEl = null;
      function pill() {
        var d = live();
        if (!pillEl) {
          pillEl = document.createElement("div");
          pillEl.style.cssText = "position:fixed;left:max(10px,env(safe-area-inset-left,0px));bottom:calc(env(safe-area-inset-bottom,0px) + 6px);z-index:9997;" +
            "padding:4px 10px;border-radius:999px;border:1.5px solid #FF5E7A;background:rgba(30,6,14,.92);color:#FFD0DA;" +
            "font:800 11px/1.3 system-ui,sans-serif;letter-spacing:.04em;pointer-events:none;max-width:70vw;white-space:nowrap;overflow:hidden;text-overflow:ellipsis";
          document.body.appendChild(pillEl);
        }
        if (!d || !TT.on) { pillEl.style.display = "none"; return; }
        var p = prog()[d.id] || { hands: 0, net: 0, bet: 0 };
        pillEl.style.display = "";
        pillEl.textContent = "⚔️ vs " + them(d).toUpperCase() + " · " + String(d.game).toUpperCase() + " · " +
          Math.min(p.hands, 10) + "/10 · " + pct(p.net, p.bet);
      }
      setInterval(poll, 15000);
      setTimeout(poll, 3000);
      return { poll: poll, track: track, handDone: handDone, busy: function () { return mine.some(function (d) { return d.status === "pending" || d.status === "active"; }); } };
    })();
    TT.handDone = function () { shout.handDone(); duel.handDone(); };

    /* ---- the duel sheet: tap a player on the board ---- */
    var DUEL_GAMES = Object.keys(GAMES).map(function (id) { return GAMES[id]; }).filter(function (g) { return g !== "Crapless Craps"; });
    function duelSheet(name) {
      var ov = $("tt-duel");
      if (!ov) {
        ov = document.createElement("div"); ov.id = "tt-duel"; ov.className = "tt-duelov";
        ov.innerHTML = '<div class="tt-duelbox"><div class="tt-dueltitle"></div>' +
          '<div class="tt-duelsub">10 hands of the same game, played whenever. Best return wins: profit for every dollar bet, so bet size doesn\'t matter. Winner takes the pot. Open until midnight.</div>' +
          '<label>GAME</label><select id="tt-duelgame"></select>' +
          '<label>POT (EACH)</label><div class="tt-duelpots"></div><input id="tt-duelpot" inputmode="numeric" placeholder="Pot">' +
          '<div class="tt-duelmsg" id="tt-duelmsg"></div>' +
          '<div class="tt-duelrow"><button type="button" class="tt-duelno">CANCEL</button><button type="button" class="tt-duelgo">SEND</button></div></div>';
        document.body.appendChild(ov);
        var sel = ov.querySelector("#tt-duelgame");
        DUEL_GAMES.forEach(function (g) { var o = document.createElement("option"); o.value = g; o.textContent = g; sel.appendChild(o); });
        [500, 1000, 2500, 5000, 10000].forEach(function (v) {
          var b = document.createElement("button"); b.type = "button"; b.textContent = "$" + fmt(v);
          b.onclick = function () { ov.querySelector("#tt-duelpot").value = v; };
          ov.querySelector(".tt-duelpots").appendChild(b);
        });
        ov.querySelector(".tt-duelno").onclick = function () { ov.classList.remove("on"); };
        ov.addEventListener("click", function (e) { if (e.target === ov) ov.classList.remove("on"); });
      }
      ov.querySelector(".tt-dueltitle").textContent = "⚔️ DUEL " + name.toUpperCase();
      ov.querySelector("#tt-duelmsg").textContent = duel.busy() ? "You already have a duel going." : "";
      ov.querySelector("#tt-duelpot").value = "";
      if (TT.game && DUEL_GAMES.indexOf(TT.game) >= 0) ov.querySelector("#tt-duelgame").value = TT.game;
      ov.querySelector(".tt-duelgo").onclick = function () {
        var pot = Math.round(+String(ov.querySelector("#tt-duelpot").value).replace(/[^0-9]/g, "")), msg = ov.querySelector("#tt-duelmsg");
        if (!myName()) { msg.textContent = "Join the tournament first."; return; }
        if (!(pot >= 100)) { msg.textContent = "Pick a pot of at least $100."; return; }
        // from the front page the wallet isn't loaded; the board's "you, right now" has it
        var have = TT.on ? Shell.getBankroll() : (board && board.me ? (board.me.cash != null ? board.me.cash : board.me.net) : null);
        if (have != null && pot > have) { msg.textContent = "You only have $" + fmt(have) + "."; return; }
        msg.textContent = "Sending…";
        rpc("c4tb_duel_challenge", { p_name: myName(), p_token: token(), p_opponent: name,
          p_game: ov.querySelector("#tt-duelgame").value, p_pot: pot }).then(function (r) {
          if (r && r.ok) { msg.textContent = "Challenge sent to " + name.toUpperCase() + "!"; setTimeout(function () { ov.classList.remove("on"); }, 1200); duel.poll(); }
          else msg.textContent = r && r.error === "busy" ? "One of you already has a duel going today." : "Couldn't send that one.";
        }, function () { msg.textContent = "Can't reach the server right now."; });
      };
      ov.classList.add("on");
    }
    // tap a player's row on the board to challenge them
    document.addEventListener("click", function (e) {
      var rowEl = e.target.closest && e.target.closest("#tt-rows .tt-row[data-player]");
      if (!rowEl || !DUELS_ON) return;
      var n = rowEl.getAttribute("data-player");
      if (!n || n.toLowerCase() === (myName() || "").toLowerCase() || !myName()) return;
      duelSheet(n);
    });
    /* ---- who's online: each open phone checks in every 30 s; the board puts a green dot by them ---- */
    var online = {}, lastSeen = {};
    /** "5m ago", "3h ago", "2d ago". */
    function ago(t) {
      var s = Math.max(0, (Date.now() - t) / 1000);
      return s < 3600 ? Math.max(1, Math.round(s / 60)) + "m ago" : s < 86400 ? Math.round(s / 3600) + "h ago" : Math.round(s / 86400) + "d ago";
    }
    function ping() {
      if (document.hidden) return;
      // not signed in (just looking at the board): still ask who's on, it just doesn't mark anyone as here
      rpc("c4tb_ping", { p_name: myName() || "", p_token: token() || "" }).then(function (list) {
        if (!Array.isArray(list)) return;
        // [{name, s: seconds since last seen}]; an older server sends names only, all online
        var m = {}, seen = {};
        list.forEach(function (n) {
          if (typeof n === "string") { m[n.toLowerCase()] = 1; return; }
          var k = String(n.name).toLowerCase();
          if (n.s < 90) m[k] = 1; else seen[k] = Date.now() - n.s * 1000;
        });
        var changed = Object.keys(m).sort().join() !== Object.keys(online).sort().join() ||
          Object.keys(seen).length !== Object.keys(lastSeen).length;
        online = m; lastSeen = seen;
        if (changed && $("tt-board").classList.contains("on")) paintBoard();
      }, function () {});
    }
    setInterval(ping, 30000);
    setTimeout(ping, 2500);
    document.addEventListener("visibilitychange", function () { if (!document.hidden) ping(); });
    /* ---- walk-up stats: counted on the phone, sent up a moment later ---- */
    var wuT = 0;
    TT.walkup = function (field) {
      if (!TT.on || !TT.week || TT.closed) return;
      var k = kw("walkups"), w;
      try { w = JSON.parse(store.getStr(k, "{}")) || {}; } catch (e) { w = {}; }
      w[field] = (w[field] || 0) + 1;
      store.setStr(k, JSON.stringify(w));
      clearTimeout(wuT);
      wuT = setTimeout(function () {
        var n; try { n = JSON.parse(store.getStr(k, "{}")) || {}; } catch (e) { n = {}; }
        rpc("c4tb_save_walkups", { p_name: myName(), p_token: token(), p_week: TT.week,
          // the old "real"/"scams" columns now carry paid off / burned
          p_shown: n.shown || 0, p_backed: n.backed || 0, p_real: n.paid || 0, p_scams: n.burned || 0 }).catch(function () {});
      }, 1500);
    };
    function pushStats() {
      if (!statsDirty || !TT.week) return;
      statsDirty = false;
      rpc("c4tb_save_stats", { p_name: myName(), p_token: token(), p_week: TT.week, p_stats: readStats() })
        .catch(function () { statsDirty = true; });
    }

    // ---- the rules ----
    var RULES_DAILY = null;
    function showRules(b) {
      b = setRules(b);
      $("tt-rules-head").textContent = b && b.status === "live"
        ? "WEEK " + b.week_no + " · IT'S ON" : "THE TOURNAMENT";
      $("tt-rules").classList.add("on");
      $("tt-rules").scrollTop = 0;
    }
    /** Puts this week's rules (daily or points) into the rules sheet's list. */
    function setRules(b) {
      b = b || board;
      var ol = document.querySelector("#tt-rules .tt-rlist");
      if (ol && RULES_DAILY === null) RULES_DAILY = { list: ol.innerHTML, sub: $("tt-rules-sub").textContent };
      if (ol && b && isPoints(b.week)) {
        $("tt-rules-sub").textContent = "SUNDAY 12:00 AM – SATURDAY 11:59 PM · EASTERN · PLAYED FOR POINTS";
        ol.innerHTML =
          "<li><b>Every game has its own $10,000.</b> Each table has a separate wallet, and every one of them resets to $10,000 at midnight.</li>" +
          "<li><b>Free rebuys.</b> Bust a game and tap REBUY to start that game over at $10,000. The money you lost doesn't count against you.</li>" +
          "<li><b>Each game is worth 1, 2 or 3 points,</b> shuffled every day: 3 games are worth 3, 5 are worth 2, and the rest are worth 1. The game's button shows its points and who's on top right now: their face in the PLAY circle with their money under it.</li>" +
          "<li><b>At midnight, whoever has the most money on a game takes its points.</b> Only first place scores, and you have to be up at least $5,000 on that game ($15,000 or more).</li>" +
          "<li><b>Most points at the end of the week wins.</b> A tie goes to whoever earned the most money that week.</li>" +
          "<li><b>🔥 Hot games and walk-ups are on.</b> Two random games pay 1.5× on wins every day, and whatever you win or lend comes out of the wallet of the game you're at.</li>" +
          "<li><b>You are your face.</b> When you join, tap your picture and pick a 4-digit PIN. No picture yet? Tap NOT ON HERE and type your name. Your PIN gets you back in if your phone ever forgets you.</li>";
      } else if (ol && RULES_DAILY) { ol.innerHTML = RULES_DAILY.list; $("tt-rules-sub").textContent = RULES_DAILY.sub; }
      return b;
    }
    $("tt-rules-ok").addEventListener("click", function () { $("tt-rules").classList.remove("on"); });
    $("tt-ruleslink").addEventListener("click", function () { showRules(); });

    // ---- joining ----
    // From the points week on, you join as one of the faces (the ones the walk-ups use), not a typed name.
    var FACE_FROM = Date.parse("2026-11-22T05:00:00Z");
    function faceMode() {
      var f = typeof Walkups !== "undefined" && Walkups.faces ? Walkups.faces() : null;
      return f && Object.keys(f).length && (now() >= FACE_FROM || isPoints(board && board.week)) ? f : null;
    }
    /** Which faces are spoken for: everyone who has ever signed in to the tournament, by lower-case face name. */
    var takenAt = 0, takenMap = null;
    TT.takenFaces = function () {
      if (!ready) return Promise.resolve({});
      if (takenMap && Date.now() - takenAt < 60000) return Promise.resolve(takenMap);
      return rpc("c4tb_names", {}).then(function (names) {
        var t = {};
        (Array.isArray(names) ? names : []).forEach(function (x) {
          var lk = String(x).toLowerCase(); t[lk] = 1;
          var al = FACE_ALIAS[lk.replace(/[^a-z0-9]/g, "")]; if (al) t[al] = 1;
        });
        takenMap = t; takenAt = Date.now(); return t;
      }, function () { return takenMap || {}; });
    };
    /** From Settings, for someone with no picture: straight to the name box. */
    TT.signUp = function () { if (ready) askName(function () { refreshLane(); }, true); };
    function askName(then, typeIt) {
      var faces = faceMode(), picked = "", typing = false;
      var grid = $("tt-faces"), sub = document.querySelector("#tt-join .sub");
      $("tt-pin").value = "";
      $("tt-join-err").textContent = "";
      if (faces) {
        if (Walkups.hideNag) Walkups.hideNag();
        $("tt-name").style.display = "none";
        grid.hidden = false; grid.className = ""; grid.innerHTML = "";
        sub.textContent = "Tap your face, then your 4-digit PIN. New here? Pick a PIN now. Back again? Use the one you picked.";
        // no picture yet: type a name instead
        var tn = $("tt-typename");
        tn.hidden = false; tn.textContent = "NOT ON HERE? TYPE YOUR NAME";
        var setTyping = function (on) {
          typing = on;
          grid.hidden = on;
          $("tt-name").style.display = on ? "" : "none";
          tn.textContent = on ? "PICK YOUR FACE INSTEAD" : "NOT ON HERE? TYPE YOUR NAME";
          sub.textContent = on ? "Your name, and a 4-digit PIN. If this phone ever forgets you, your name and PIN get you back in."
                               : "Tap your face, then your 4-digit PIN. New here? Pick a PIN now. Back again? Use the one you picked.";
          $("tt-join-err").textContent = "";
          if (on) { $("tt-name").value = myName(); setTimeout(function () { $("tt-name").focus(); }, 60); }
        };
        tn.onclick = function () { setTyping(!typing); };
        var mine = myName() || store.getStr("c4tb.walkme", "");
        Object.keys(faces).forEach(function (n) {
          var bt = document.createElement("button"); bt.type = "button"; bt.setAttribute("data-face", n);
          var im = document.createElement("img"); im.alt = ""; im.src = faces[n];
          var b = document.createElement("b"); b.textContent = n;
          bt.appendChild(im); bt.appendChild(b);
          bt.onclick = function () {
            if (bt.classList.contains("taken")) {
              picked = n;
              grid.className = "chosen";
              grid.querySelectorAll("button").forEach(function (x) { x.classList.toggle("on", x === bt); });
              $("tt-join-err").textContent = n + " is taken. If this is you, enter your PIN to get back in.";
              $("tt-pin").focus();
              return;
            }
            picked = n;
            grid.className = "chosen";
            grid.querySelectorAll("button").forEach(function (x) { x.classList.toggle("on", x === bt); });
            $("tt-join-err").textContent = "";
            $("tt-pin").focus();
          };
          grid.appendChild(bt);
          if (mine && n.toLowerCase() === mine.toLowerCase()) bt.onclick();
        });
        // faces somebody already has are greyed out (still tappable, so the owner can get back in with the PIN)
        rpc("c4tb_names", {}).then(function (names) {
          if (!Array.isArray(names)) return;
          // a player still under an old made-up name holds the face that name belongs to
          var t = {}; names.forEach(function (x) {
            var lk = String(x).toLowerCase(); t[lk] = 1;
            var al = FACE_ALIAS[lk.replace(/[^a-z0-9]/g, "")]; if (al) t[al] = 1;
          });
          grid.querySelectorAll("button[data-face]").forEach(function (x) {
            var f = x.getAttribute("data-face").toLowerCase();
            x.classList.toggle("taken", !!t[f] && f !== (myName() || "").toLowerCase());
          });
        }, function () {});
        // signed in before under a typed name: open on the name box
        if ((myName() && !picked) || typeIt) setTyping(true);
      } else {
        $("tt-typename").hidden = true;
        $("tt-name").style.display = "";
        grid.hidden = true;
        sub.textContent = "Your name, and a 4-digit PIN. If this phone ever forgets you, your name and PIN get you back in with your money.";
        $("tt-name").value = myName();
        setTimeout(function () { $("tt-name").focus(); }, 60);
      }
      $("tt-join").classList.add("on");
      $("tt-join-go").onclick = function () {
        var n = faces && !typing ? picked : $("tt-name").value.trim().replace(/\s+/g, " ");
        if (faces && !typing && !n) { $("tt-join-err").textContent = "Tap your face first."; return; }
        if (n.length < 2 || n.length > 16 || !/^[A-Za-z0-9 ._'-]+$/.test(n)) {
          $("tt-join-err").textContent = "2–16 letters or numbers.";
          return;
        }
        var pin = $("tt-pin").value.trim();
        if (!/^[0-9]{4}$/.test(pin)) { $("tt-join-err").textContent = "The PIN is 4 digits."; return; }
        $("tt-join-go").disabled = true;
        rpc("c4tb_signin", { p_name: n, p_token: token(), p_pin: pin }).then(function (j) {
          $("tt-join-go").disabled = false;
          if (!j.ok) {
            $("tt-join-err").textContent =
              j.error === "pin" ? (faces ? "Wrong PIN. Someone already took " + n + " — if that's you, use your PIN." : "Wrong PIN for that name.") :
              j.error === "taken" ? "That name is taken, and it has no PIN yet. Ask Justin to free it." :
              j.error === "bad_pin" ? "The PIN is 4 digits." : "2–16 letters or numbers.";
            return;
          }
          store.setStr("ftt.name", j.name);
          store.setStr("ftt.haspin", "1");
          // you're this face now, so the walk-ups never send you to yourself
          if (faces && !typing) { try { store.setStr("c4tb.walkme", n); } catch (e) {} }
          $("tt-join").classList.remove("on");
          // you're in: the piece goes up on the wall while the tournament loads behind it
          if (faces && typeof Intro !== "undefined") {
            try { if (board && board.week) store.setStr("ftt.weekintro", board.week); } catch (e) {}
            try { Intro.play(function () { try { Intro.demo(); } catch (e) {} }); } catch (e) {}
          }
          if (then) then(); else refreshLane();
        }, function () {
          $("tt-join-go").disabled = false;
          $("tt-join-err").textContent = "Can't reach the tournament. Check your signal.";
        });
      };
    }
    $("tt-join-x").addEventListener("click", function () { $("tt-join").classList.remove("on"); askingBack = false; });
    $("tt-name").addEventListener("keydown", function (e) { if (e.key === "Enter") $("tt-pin").focus(); });
    $("tt-pin").addEventListener("keydown", function (e) { if (e.key === "Enter") $("tt-join-go").click(); });

    // ---- players from before PINs: ask once for one ----
    var pinAsked = false;
    function checkPin() {
      if (pinAsked || !ready || !myName() || store.getStr("ftt.haspin", "")) return;
      pinAsked = true;
      rpc("c4tb_pin", { p_name: myName(), p_token: token(), p_pin: null }).then(function (j) {
        if (!j.ok) return;
        if (j.has_pin) { store.setStr("ftt.haspin", "1"); return; }
        $("tt-pin2").value = ""; $("tt-setpin-err").textContent = "";
        $("tt-setpin").classList.add("on");
      }, function () { pinAsked = false; });
    }
    $("tt-setpin-go").addEventListener("click", function () {
      var pin = $("tt-pin2").value.trim();
      if (!/^[0-9]{4}$/.test(pin)) { $("tt-setpin-err").textContent = "The PIN is 4 digits."; return; }
      $("tt-setpin-go").disabled = true;
      rpc("c4tb_pin", { p_name: myName(), p_token: token(), p_pin: pin }).then(function (j) {
        $("tt-setpin-go").disabled = false;
        if (!j.ok) { $("tt-setpin-err").textContent = "Couldn't save it. Try again."; return; }
        store.setStr("ftt.haspin", "1");
        $("tt-setpin").classList.remove("on");
      }, function () {
        $("tt-setpin-go").disabled = false;
        $("tt-setpin-err").textContent = "Can't reach the tournament. Check your signal.";
      });
    });
    $("tt-setpin-x").addEventListener("click", function () { $("tt-setpin").classList.remove("on"); });
    $("tt-pin2").addEventListener("keydown", function (e) { if (e.key === "Enter") $("tt-setpin-go").click(); });

    // ---- walking in ----
    function enter() {
      if (!ready) return;
      if (!myName()) return askName(enter);
      laneSay("CHECKING IN…", "…", false);
      var go = function (s) {
        if (!s.ok && s.error === "auth") {
          store.setStr("ftt.name", "");
          return askName(enter);
        }
        frame = s;
        if (s.status === "upcoming") return refreshLane();
        TT.week = s.week;
        TT.day = s.daily ? s.day : "";
        TT.points = isPoints(s.week);
        var local = store.getStr(k("dirty"), "");
        if (TT.points) {
          rpc("c4tb_game_mine", { p_name: myName(), p_token: token() }).then(function (rows) {
            (rows || []).forEach(function (x) {
              // what's on this phone wins if it hasn't been sent up yet
              if (!dirtyGames[x.game] && !local) store.set(gameKey(x.game), x.cash);
            });
          }, function () {});
        }
        // bonuses won at the hot game before a reload: the server's figure has them, a local one gets them now
        if (local && hot.extra()) store.set(k("bankroll"), Math.round(store.get(k("bankroll"), 0)) + hot.extra());
        hot.setExtra(0);
        if (!local && !TT.points) {
          writeWallet({ cash: s.cash, owed: s.owed, markers: s.markers, out: s.out });
          // the game stats come from the server too, so an old copy on this phone
          // (a name deleted and started over, or played on another phone) isn't sent up again
          var wk = s.week;
          store.setStr(kw("stats"), "{}");
          rpc("c4tb_get_stats", { p_name: myName(), p_token: token(), p_week: wk }).then(function (g) {
            if (!g || !g.ok || TT.week !== wk) return;
            // the server's games, plus anything played while it was answering
            var m = readStats();
            (g.rows || []).forEach(function (r) {
              var x = m[r.game] || { net: 0, best: 0 };
              m[r.game] = { net: x.net + r.net, best: Math.max(x.best, r.best) };
            });
            store.setStr(kw("stats"), JSON.stringify(m));
          }, function () {});
        }
        if (s.out && !TT.points) { store.setStr(k("out"), "1"); refreshLane(); return showBoard(); }
        TT.on = true; TT.closed = false;
        Shell.mode = "campaign";
        Shell.showLobby();
        if (hotGo) { var room = hotGo; hotGo = null; try { Shell.show(room); } catch (e) {} }
        hot.banner();
        if (TT.points) points.leaders();
        if (local) push();
        // already signed up from last week: the first time into a points week they get the piece and the tips too
        if (TT.points && typeof Intro !== "undefined" && store.getStr("ftt.weekintro", "") !== s.week) {
          store.setStr("ftt.weekintro", s.week);
          if (s.day) store.setStr("ftt.dayintro", s.day);
          if (!Intro.isPlaying()) { try { Intro.play(function () { try { Intro.demo(); } catch (e) {} }); } catch (e) {} }
        }
        // a new day of a points week: the first time in, the can writes which day it is
        if (TT.points && s.day && typeof Intro !== "undefined") {
          var seenDay = store.getStr("ftt.dayintro", "");
          if (seenDay !== s.day) {
            store.setStr("ftt.dayintro", s.day);
            if (s.day_no > 1 && !Intro.isPlaying()) {
              try { Intro.play({ quick: true, title: s.day_no === 7 ? "FINAL DAY" : "DAY " + s.day_no,
                                 sub: hot.today(s.day).toUpperCase() + " PAY " + hot.tagX(s.day) }); } catch (e) {}
            }
          }
        }
      };
      rpc("c4tb_state", { p_name: myName(), p_token: token() }).then(go, function () {
        laneSay("CAN'T REACH THE TOURNAMENT", "RETRY", true);
      });
    }
    $("tt-play").addEventListener("click", function () {
      if (board && board.status === "upcoming") return askName();
      if (!board) return refreshLane();
      enter();
    });

    // ---- the board ----
    var tab = "week";
    function showBoard() {
      pointsCache = {}; winnersCache = {};
      msgSeenAtOpen = msgSeen; ping();
      // a message you haven't read yet: the board opens straight onto it
      boardFresh = true;
      var unread = function () { return !!(msgs && msgs[0] && msgs[0].id > msgSeen); };
      if (unread()) { tab = "msgs"; autoMsgs = true; }
      else if (autoMsgs) { tab = "week"; autoMsgs = false; }   // it only jumped there for the message: back to the standings
      loadMsgs().then(function () {
        if (boardFresh && tab !== "msgs" && unread() && $("tt-board").classList.contains("on")) { tab = "msgs"; autoMsgs = true; msgSeenAtOpen = msgSeen; paintBoard(); }
      });
      // the front page's chips lie behind the board too
      try {
        var rain = $("cr-rain");
        if (rain && rain.width) $("tt-board").style.backgroundImage =
          "linear-gradient(rgba(5,4,8,.45), rgba(5,4,8,.6)), url(" + rain.toDataURL("image/jpeg", 0.8) + ")";
      } catch (e) {}
      $("tt-board").classList.add("on");
      paintBoard();
      ping();
      refreshLane();
    }
    function paintBoard() {
      var b = board, me = (myName() || "").toLowerCase();
      var list = $("tt-rows"); list.innerHTML = "";
      if (!b) { $("tt-bhead").textContent = "LOADING…"; return; }
      $("tt-tabs").style.display = b.status === "upcoming" ? "none" : "";
      document.querySelectorAll("#tt-tabs button").forEach(function (x) {
        x.classList.toggle("on", x.getAttribute("data-t") === tab);
      });
      var champ = $("tt-champ"), wins = $("tt-wins");
      champ.hidden = true; wins.innerHTML = "";

      if (tab === "msgs") { $("tt-you").hidden = true; $("tt-ruleslink").hidden = true; $("tt-bhead").textContent = "NEWS & RULES"; subline("FROM JUSTIN"); return paintMsgs(list); }
      if (b.status === "upcoming") {
        $("tt-bhead").textContent = "WEEK 1";
        subline("STARTS SUNDAY", left(b.starts_at).toLowerCase());
        b.players.forEach(function (p, i) { list.appendChild(row(i + 1, p.name, "SIGNED UP", "", p.name.toLowerCase() === me)); });
        if (!b.players.length) list.appendChild(empty("Nobody's signed up yet."));
        return;
      }
      var finalDay = b.day_no === 7;
      $("tt-bhead").textContent = "WEEK " + b.week_no + " · " + (finalDay ? "FINAL DAY" : "DAY " + b.day_no + " OF 7");
      subline(finalDay ? "WINNER IN" : "NEXT UPDATE", left(finalDay ? b.ends_at : b.day_ends_at).toLowerCase());
      // you, live - nobody else sees this (a points week has no one purse to show)
      var you = $("tt-you"), y = isPoints(b.week) ? null : b.me;
      you.hidden = !(y && !y.fresh);
      if (y && !y.fresh) {
        $("tt-you-face").src = faceOf(me);
        $("tt-you-net").textContent = moneyS(y.net);
        $("tt-you-today").textContent = signedS(y.today);
        $("tt-you-today").className = y.today > 0 ? "up" : y.today < 0 ? "down" : "";
        // only what you'd want to know: what you owe, and if you're out
        $("tt-you-tags").textContent = [y.owed ? "OWES " + amt(y.owed) + " · " + Math.ceil(y.owed / 7500) + "/" + b.max_markers + " MARKERS" : "",
          y.out ? (b.daily ? "OUT FOR TODAY" : "OUT FOR THE WEEK") : ""].filter(Boolean).join(" · ");
      }

      if (b.last_champion) {
        champ.hidden = !isPoints(b.week);          // a money week shows the champ as a face on the day strip instead
        $("tt-champ-name").textContent = shown(b.last_champion.name).toUpperCase();
        $("tt-champ-net").textContent = moneyS(b.last_champion.net);
        var seen = "ftt.champseen." + b.last_champion.week;
        if (!store.getStr(seen, "")) {
          store.setStr(seen, "1");
          $("tt-crown-wk").textContent = "WEEK " + (b.week_no - 1) + " CHAMPION";
          $("tt-crown-name").textContent = shown(b.last_champion.name).toUpperCase();
          $("tt-crown-net").textContent = moneyS(b.last_champion.net);
          $("tt-crown").classList.add("on");
        }
      }
      // last week was played for points: its champion is the top of that board
      if (b.last_champion && isPoints(b.last_champion.week)) {
        var lp = pointsCache[b.last_champion.week];
        if (!lp) getPoints(b.last_champion.week).then(function () { if ($("tt-board").classList.contains("on")) paintBoard(); });
        else if (lp[0]) {
          $("tt-champ-name").textContent = shown(lp[0].name).toUpperCase();
          $("tt-champ-net").textContent = lp[0].points + " PTS";
          $("tt-crown-name").textContent = shown(lp[0].name).toUpperCase();
          $("tt-crown-net").textContent = lp[0].points + " PTS";
        }
      }
      // the week at a glance: last week's champ, then one face per day for whoever won it
      if (!isPoints(b.week)) dayStrip(wins, b, true);

      // the first tab is yesterday's day, or last week's final standings until this week has a midnight
      var dayBtn = document.querySelector('#tt-tabs [data-t="day"]');
      if (dayBtn) dayBtn.textContent = b.final ? "LAST WEEK" : "YESTERDAY";
      if (tab === "stats") return paintStats(b);
      $("tt-ruleslink").hidden = tab === "msgs";
      if (tab === "msgs") return paintMsgs(list);
      if (isPoints(b.week) && !b.final) return paintPoints(b, list, me);
      if (b.final) {
        if (tab === "day") {
          var hiw = highs(lastWeekOf(b));
          if (b.rows.length) list.appendChild(heading("WEEK " + (b.week_no - 1) + " · FINAL STANDINGS"));
          b.rows.forEach(function (r, i) {
            var t = hiw && hiw[r.name.toLowerCase()] ? "HIGHEST CASH $" + fmt(hiw[r.name.toLowerCase()]) : "";
            list.appendChild(row(i + 1, r.name, t, moneyS(r.net), r.name.toLowerCase() === me));
          });
          if (!b.rows.length) list.appendChild(empty("No standings from last week."));
        } else {
          // a new week: everybody's name, no money until the first midnight
          var all = roster || [];
          if (all.length) list.appendChild(heading(isPoints(b.week) ? "POINTS COME IN AT MIDNIGHT" : "MONEY SHOWS AT MIDNIGHT"));
          all.forEach(function (p) {
            var el = row(" ", p.name, "", "", p.name.toLowerCase() === me);
            el.classList.add("blank");
            list.appendChild(el);
          });
          if (!all.length) list.appendChild(empty("The first standings come in at midnight."));
        }
        return;
      }
      var dayTab = tab === "day", asOf = dayName(b.as_of);
      if (b.rows.length) list.appendChild(heading(b.final ? "WEEK " + (b.week_no - 1) + " · FINAL STANDINGS"
                               : dayTab ? asOf + " · WHAT EACH PLAYER MADE"
                               : b.daily ? "BEST 3 DAYS ADDED UP" : "THE WEEK"));
      var rows = b.rows.slice();
      if (dayTab) rows.sort(function (a, c) { return c.day - a.day || c.net - a.net || a.name.localeCompare(c.name); });
      var hi = b.final ? highs(lastWeekOf(b)) : null;
      rows.forEach(function (r, i) {
        var v = dayTab ? signedS(r.day) : moneyS(r.net);
        var tag = hi && hi[r.name.toLowerCase()] ? "HIGHEST CASH $" + fmt(hi[r.name.toLowerCase()]) : "";
        var el = row(i + 1, r.name, tag, v, r.name.toLowerCase() === me);
        if (dayTab) el.classList.add(r.day > 0 ? "up" : r.day < 0 ? "down" : "flat");
        list.appendChild(el);
      });
      // everyone else in the tournament gets a row too, left blank until they have a midnight total
      if (!dayTab && roster && !b.final) {
        var listed = {};
        rows.forEach(function (r) { listed[r.name.toLowerCase()] = 1; });
        var rest = roster.filter(function (p) { return !listed[p.name.toLowerCase()]; });
        if (!rows.length && rest.length) list.appendChild(heading("MONEY SHOWS AT MIDNIGHT"));
        rest.forEach(function (p) {
          var el = row(" ", p.name, "", "", p.name.toLowerCase() === me);
          el.classList.add("blank");
          list.appendChild(el);
        });
        if (!rows.length && !rest.length) list.appendChild(empty("Nobody has joined yet."));
      } else if (!rows.length) list.appendChild(empty("The first standings come in at midnight. Until then, only you can see your money."));
    }
    /*
     * The stats tab. Last week's, everyone's, once it is final: each game with
     * what the boys made or lost on it in all and who did best and worst there,
     * then each player's best and worst game. Under that, your own games this week.
     */
    var statsCache = {};
    // each player's highest cash in a finished week, by name
    var highsCache = {};
    function highs(week) {
      if (highsCache[week] === undefined) {
        highsCache[week] = null;
        rpc("c4tb_highs", { p_week: week }).then(function (list) {
          var m = {}; (list || []).forEach(function (h) { m[h.name.toLowerCase()] = h.best; });
          highsCache[week] = { map: m, list: list || [] };
          if ($("tt-board").classList.contains("on")) paintBoard();
        }, function () { delete highsCache[week]; });
      }
      return highsCache[week] && highsCache[week].map;
    }
    // the points week: standings by points, a game's winners by day
    var pointsCache = {}, winnersCache = {};
    function getPoints(week) {
      return rpc("c4tb_points_board", { p_week: week }).then(function (j) { pointsCache[week] = Array.isArray(j) ? j : []; return pointsCache[week]; },
        function () { pointsCache[week] = pointsCache[week] || []; return pointsCache[week]; });
    }
    function getWinners(day) {
      return rpc("c4tb_game_leaders", { p_day: day }).then(function (j) { winnersCache[day] = Array.isArray(j) ? j : []; },
        function () { winnersCache[day] = []; });
    }
    function prevDay(day) { var d = new Date(day + "T12:00:00Z"); d.setUTCDate(d.getUTCDate() - 1); return d.toISOString().slice(0, 10); }
    function paintPoints(b, list, me) {
      var dayTab = tab === "day";
      if (dayTab) {
        var yd = prevDay(b.day);
        if (!winnersCache[yd]) { list.appendChild(empty("Loading…")); getWinners(yd).then(function () { if (tab === "day") paintBoard(); }); return; }
        var ws = winnersCache[yd];
        list.appendChild(heading(dayName(yd) + " · WHO TOOK EACH GAME"));
        if (!ws.length) list.appendChild(empty("Nobody played yesterday."));
        ws.slice().sort(function (a, c) { return c.pts - a.pts || c.cash - a.cash; }).forEach(function (w) {
          list.appendChild(row("", w.game, "👑 " + shown(w.name).toUpperCase() + " · $" + fmt(w.cash), "+" + w.pts + " PT" + (w.pts > 1 ? "S" : ""),
            w.name.toLowerCase() === me));
        });
        return;
      }
      var pb = pointsCache[b.week];
      if (!pb) { list.appendChild(empty("Loading…")); getPoints(b.week).then(function () { if (tab === "week") paintBoard(); }); return; }
      var scored = pb.filter(function (r) { return r.points > 0 || r.money; });
      list.appendChild(heading("POINTS · AS OF " + dayName(b.as_of) + " MIDNIGHT · TIES GO TO MOST MONEY"));
      var listed = {};
      scored.forEach(function (r, i) {
        listed[r.name.toLowerCase()] = 1;
        list.appendChild(row(i + 1, r.name, r.wins + " WIN" + (r.wins === 1 ? "" : "S") + " · " + signed(r.money),
          r.points + " PT" + (r.points === 1 ? "" : "S"), r.name.toLowerCase() === me));
      });
      (roster || []).filter(function (p) { return !listed[p.name.toLowerCase()]; }).forEach(function (p) {
        var el = row(" ", p.name, "", "", p.name.toLowerCase() === me);
        el.classList.add("blank");
        list.appendChild(el);
      });
      if (!scored.length && !(roster || []).length) list.appendChild(empty("Points come in at midnight."));
    }
    // walk-ups: everyone's, live
    var wuCache = {};
    function getWalkups(week) {
      return rpc("c4tb_walkup_board", { p_week: week })
        .then(function (j) { wuCache[week] = Array.isArray(j) ? j : []; return j; }, function () { wuCache[week] = []; });
    }
    var duelsCache = null;
    function getDuels() {
      return rpc("c4tb_duels_today", {}).then(function (j) { duelsCache = Array.isArray(j) ? j : []; }, function () { duelsCache = []; });
    }
    function paintDuels(list) {
      var rows = duelsCache || [];
      list.appendChild(heading("⚔️ DUELS TODAY"));
      if (!rows.length) { list.appendChild(empty("No duels yet. Tap a player on THE WEEK to challenge them.")); return; }
      rows.forEach(function (d) {
        var st = d.status === "pending" ? "WAITING ON " + d.opponent.toUpperCase()
          : d.status === "active" ? d.c_hands + "/10 vs " + d.o_hands + "/10 · " + pct(d.c_net, d.c_bet) + " vs " + pct(d.o_net, d.o_bet)
          : d.status === "expired" ? "RAN OUT"
          : d.winner ? d.winner.toUpperCase() + " WON · " + pct(d.c_net, d.c_bet) + " vs " + pct(d.o_net, d.o_bet) : "TIE";
        list.appendChild(row("", d.challenger + " vs " + d.opponent, String(d.game).toUpperCase() + " · " + st, "$" + fmt(d.pot), false));
      });
    }
    function paintWalkups(list, week) {
      var rows = wuCache[week] || [];
      var tot = rows.reduce(function (a, r) { return a + r.shown; }, 0);
      list.appendChild(heading("WALK-UPS THIS WEEK · " + fmt(tot) + " TOTAL"));
      if (!rows.length) { list.appendChild(empty("Nobody's walked up to anyone yet.")); return; }
      var me = (myName() || "").toLowerCase();
      rows.forEach(function (r) {
        list.appendChild(row("", r.name, "BACKED " + r.backed + " · PAID OFF " + r.real + " · BURNED " + r.scams,
          fmt(r.shown), r.name.toLowerCase() === me));
      });
    }
    function getStats(week) {
      return rpc("c4tb_get_stats", { p_name: myName(), p_token: token(), p_week: week })
        .then(function (j) { statsCache[week] = j; return j; });
    }
    function lastWeekOf(b) {
      var d = new Date(b.week + "T12:00:00Z"); d.setUTCDate(d.getUTCDate() - 7);
      return d.toISOString().slice(0, 10);
    }
    function paintStats(b) {
      var list = $("tt-rows"), me = (myName() || "").toLowerCase();
      list.innerHTML = "";
      var prev = b.week_no > 1 ? lastWeekOf(b) : null;
      var want = [b.week].concat(prev ? [prev] : []);
      var have = want.every(function (w) { return statsCache[w]; }) && wuCache[b.week] && duelsCache;
      if (!have) {
        list.appendChild(empty("Loading stats…"));
        Promise.all(want.map(getStats).concat([getWalkups(b.week), getDuels()])).then(function () { if (tab === "stats") paintStats(b); },
          function () { list.innerHTML = ""; list.appendChild(empty("Can't reach the stats right now.")); });
        return;
      }
      if (DUELS_ON) paintDuels(list);
      paintWalkups(list, b.week);
      if (prev) {
        var fin = statsCache[prev].rows;
        list.appendChild(heading("WEEK " + (b.week_no - 1) + " · FINAL STATS"));
        if (!fin.length) list.appendChild(empty("No stats were kept that week."));
        else {
          var byGame = {}, byName = {};
          fin.forEach(function (r) {
            (byGame[r.game] = byGame[r.game] || []).push(r);
            (byName[r.name] = byName[r.name] || []).push(r);
          });
          Object.keys(byGame).map(function (g) {
            var rs = byGame[g], tot = rs.reduce(function (s, r) { return s + r.net; }, 0);
            rs.sort(function (a, c) { return c.net - a.net; });
            return { g: g, rs: rs, tot: tot };
          }).sort(function (a, c) { return Math.abs(c.tot) - Math.abs(a.tot); }).forEach(function (x) {
            var top = x.rs[0], low = x.rs[x.rs.length - 1], bits = [];
            if (top.net > 0) bits.push("BEST " + shown(top.name).toUpperCase() + " " + signed(top.net));
            if (low.net < 0) bits.push("WORST " + shown(low.name).toUpperCase() + " " + signed(low.net));
            var big = x.rs.reduce(function (m, r) { return r.best > m.best ? r : m; }, x.rs[0]);
            if (big.best > 0) bits.push("BIGGEST HIT " + shown(big.name).toUpperCase() + " $" + fmt(big.best));
            var el = row("", x.g, bits.join(" · "), signed(x.tot), false);
            el.classList.add(x.tot > 0 ? "up" : x.tot < 0 ? "down" : "flat");
            list.appendChild(el);
          });
          var hl = highs(prev) && highsCache[prev].list;
          if (hl && hl.length) {
            list.appendChild(heading("HIGHEST CASH AT ANY POINT"));
            hl.forEach(function (h, i) {
              list.appendChild(row(i + 1, h.name, "", "$" + fmt(h.best), h.name.toLowerCase() === me));
            });
          }
          list.appendChild(heading("EVERYONE'S BEST & WORST GAME"));
          Object.keys(byName).sort().forEach(function (n) {
            var rs = byName[n].slice().sort(function (a, c) { return c.net - a.net; });
            var tot = rs.reduce(function (s, r) { return s + r.net; }, 0);
            var bits = ["BEST " + rs[0].game.toUpperCase() + " " + signed(rs[0].net)];
            if (rs.length > 1) bits.push("WORST " + rs[rs.length - 1].game.toUpperCase() + " " + signed(rs[rs.length - 1].net));
            var el = row("", n, bits.join(" · "), signed(tot), n.toLowerCase() === me);
            el.classList.add(tot > 0 ? "up" : tot < 0 ? "down" : "flat");
            list.appendChild(el);
          });
        }
      }
      list.appendChild(heading("YOUR GAMES THIS WEEK"));
      var mine = statsCache[b.week].rows.slice().sort(function (a, c) { return c.net - a.net; });
      if (!myName()) list.appendChild(empty("Join to start keeping your stats."));
      else if (!mine.length) list.appendChild(empty("Play a few hands and your games show up here."));
      mine.forEach(function (r) {
        var el = row("", r.game, r.best > 0 ? "BIGGEST HIT $" + fmt(r.best) : "", signed(r.net), false);
        el.classList.add(r.net > 0 ? "up" : r.net < 0 ? "down" : "flat");
        list.appendChild(el);
      });
      list.appendChild(empty("Everyone's stats for this week unlock when it ends at midnight Saturday."));
      foldable(list);
    }
    /** Everyone signed up, by lowercase name. */
    function players() {
      var m = {};
      (roster || []).forEach(function (p) { m[String(p.name || p).toLowerCase()] = 1; });
      ((board && board.rows) || []).forEach(function (p) { m[String(p.name).toLowerCase()] = 1; });
      return m;
    }
    /**
     * Every section on the stats tab folds: tap its heading to close or open it.
     * Which ones are closed is kept on the phone, by the heading's words (numbers
     * left out, so "WEEK 2" and "WEEK 3" are the same section).
     */
    function foldKey(text) { return "ftt.fold." + String(text).replace(/[0-9$,.+\-]+/g, "").replace(/\s+/g, " ").trim(); }
    function foldable(list) {
      var kids = Array.prototype.slice.call(list.children), cur = null;
      kids.forEach(function (el) {
        if (el.classList.contains("tt-sechead")) {
          var key = foldKey(el.textContent), shut = store.getStr(key, "") === "1";
          cur = { head: el, rows: [], shut: shut, key: key };
          el.classList.add("fold");
          el.classList.toggle("shut", shut);
          (function (sec) {
            el.onclick = function () {
              sec.shut = !sec.shut;
              store.setStr(sec.key, sec.shut ? "1" : "");
              el.classList.toggle("shut", sec.shut);
              sec.rows.forEach(function (x) { x.style.display = sec.shut ? "none" : ""; });
            };
          })(cur);
        } else if (cur) {
          cur.rows.push(el);
          if (cur.shut) el.style.display = "none";
        }
      });
    }
    function heading(text) {
      var el = document.createElement("div"); el.className = "tt-sechead"; el.textContent = text; return el;
    }
    // this week's made-up names, and whose face they are (from Sunday everyone joins as their face)
    var FACE_ALIAS = { tristyboy: "tristen", darinwooldridge: "darin", juiceman: "juice", ethanj: "ethan",
                       matthewmyer: "matthew", due: "chris", mjax: "max", brad: "grace", bdmarsh: "daneal", maverick: "zach" };
    // these keep the name they typed on the board (they still get their face)
    var KEEP_NAME = {};
    /** The name the board shows: their face's name when we know whose face they are, else what they typed. */
    function shown(name) {
      var lk = String(name || "").toLowerCase(), key = lk.replace(/[^a-z0-9]/g, "");
      if (KEEP_NAME[key]) return String(name);
      var al = FACE_ALIAS[key] || lk;
      var f = typeof Walkups !== "undefined" && Walkups.faces ? Walkups.faces() : {};
      for (var k in f) if (k.toLowerCase() === al) return k;
      return String(name || "");
    }
    function faceOf(lk) {
      lk = FACE_ALIAS[String(lk).replace(/[^a-z0-9]/g, "")] || lk;
      var f = typeof Walkups !== "undefined" && Walkups.faces ? Walkups.faces() : {};
      for (var k in f) if (k.toLowerCase() === lk) return f[k];
      return STICK;   // no picture yet: a stick figure
    }
    var STICK = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA2NCA2NCI+PHJlY3Qgd2lkdGg9IjY0IiBoZWlnaHQ9IjY0IiBmaWxsPSIjZjNlZmU0Ii8+PGcgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjMWExYTFhIiBzdHJva2Utd2lkdGg9IjIuNiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMzIgOWMtOSAwLTE1IDYtMTQuNSAxNC41QzE4IDMyIDI0IDM3IDMyLjUgMzYuNSA0MSAzNiA0NiAzMCA0NS41IDIyIDQ1IDE0IDQwIDkgMzIgOXoiLz48cGF0aCBkPSJNMjYuNSAyMC41bDEgLjNNMzcgMjBsLjguNCIvPjxwYXRoIGQ9Ik0yNiAyOWMyLjUgMS44IDYgMi4yIDkuNS41IDEtLjUgMS42LTEuMiAyLTIiLz48cGF0aCBkPSJNMzIgMzYuNWwuNSAxM00zMi41IDQ5LjVMMjUgNjBNMzIuNSA0OS41TDQwIDU5LjVNMzIgNDJsLTEwIDMuNU0zMiA0Mmw5LjUgNC41Ii8+PHBhdGggZD0iTTI5IDguNWMtMS0zIDEtNC41IDItM00zMy41IDguNWMuNS0zIDMtMy41IDMuNS0yIi8+PC9nPjwvc3ZnPgo=";
    function row(rank, name, tags, value, mine) {
      var el = document.createElement("div");
      el.className = "tt-row" + (mine ? " me" : "");
      // place, then their face (a green ring while they're on), their name, their number
      var a = document.createElement("span"); a.className = "rk"; a.textContent = rank === "" || rank == null ? "" : rank;
      var mid = document.createElement("span"); mid.className = "who";
      var lk = String(name).toLowerCase(), person = !!players()[lk];
      var n = document.createElement("b"); n.textContent = mine && person ? "YOU" : shown(name).toUpperCase();
      el.appendChild(a);
      if (person) {
        el.setAttribute("data-player", name);
        // their picture only while they're on; a grey dot when they're not
        if (online[lk]) {
          var im = document.createElement("img"); im.alt = ""; im.src = faceOf(lk); im.className = "tt-face on"; im.title = "Online";
          el.appendChild(im);
        } else {
          var off = document.createElement("span"); off.className = "tt-off"; off.title = lastSeen[lk] ? "Last on " + ago(lastSeen[lk]) : "Offline";
          off.appendChild(document.createElement("i")); el.appendChild(off);
        }
      }
      var t = document.createElement("small"); t.textContent = tags;
      mid.appendChild(n); if (tags) mid.appendChild(t);
      var v = document.createElement("span"); v.className = "val"; v.textContent = value;
      el.appendChild(mid); el.appendChild(v);
      return el;
    }
    /* ---- the message board: Justin posts, everyone reads; a red dot until you've seen the newest ---- */
    var msgs = null, msgSeen = +store.getStr("ftt.msgseen", "0") || 0, msgSeenAtOpen = msgSeen;
    function isJustin() { return (myName() || "").toLowerCase() === "justin"; }
    function msgDots() {
      var top = msgs && msgs[0] ? msgs[0].id : 0, on = top > msgSeen;
      document.querySelectorAll("#tt-boardbtn .tt-dot, #tt-tabs .tt-dot").forEach(function (d) { d.hidden = !on; });
    }
    function loadMsgs() {
      return rpc("c4tb_msgs", {}).then(function (list) {
        // no board on the server (yet), or it hiccupped: show no posts, never a stuck "Loading…"
        if (!Array.isArray(list)) { if (!msgs) { msgs = []; if ($("tt-board").classList.contains("on") && tab === "msgs") paintBoard(); } return; }
        var got = Date.now();
        list.forEach(function (m) { m.at = got - m.s * 1000; });
        msgs = list; msgDots();
        if ($("tt-board").classList.contains("on") && tab === "msgs") paintBoard();
      }, function () {
        if (!msgs) { msgs = []; if ($("tt-board").classList.contains("on") && tab === "msgs") paintBoard(); }
      });
    }
    function markRead() {
      if (msgs && msgs[0] && msgs[0].id > msgSeen) { msgSeen = msgs[0].id; store.setStr("ftt.msgseen", String(msgSeen)); }
      msgDots();
    }
    /** A post, lightly dressed: "# " a heading, "- " a bullet, **bold**. Everything else is plain text. */
    function fmtMsg(body) {
      var box = document.createElement("div"); box.className = "tt-mbody";
      var ul = null;
      String(body).split("\n").forEach(function (line) {
        var t = line.replace(/\s+$/, "");
        if (/^[-•]\s+/.test(t)) {
          if (!ul) { ul = document.createElement("ul"); box.appendChild(ul); }
          var li = document.createElement("li"); inline(li, t.replace(/^[-•]\s+/, "")); ul.appendChild(li); return;
        }
        ul = null;
        if (/^#\s+/.test(t)) { var h = document.createElement("h4"); inline(h, t.replace(/^#\s+/, "")); box.appendChild(h); return; }
        if (!t) { box.appendChild(document.createElement("br")); return; }
        var p = document.createElement("p"); inline(p, t); box.appendChild(p);
      });
      return box;
    }
    function inline(el, t) {
      t.split(/(\*\*[^*]+\*\*)/).forEach(function (part) {
        if (/^\*\*[^*]+\*\*$/.test(part)) { var b = document.createElement("b"); b.textContent = part.slice(2, -2); el.appendChild(b); }
        else if (part) el.appendChild(document.createTextNode(part));
      });
    }
    /** A giveaway on a post: CLAIM while there are any left, then who got it. */
    function prizeBox(m) {
      var pz = m.prize, me = (myName() || "").toLowerCase();
      var winners = pz.winners || [], mine = me && winners.some(function (w) { return String(w).toLowerCase() === me; });
      var short = typeof shortAmt === "function" ? shortAmt : fmt;
      var what = pz.kind === "chips" ? short(pz.amount) + " CHIPS" : (pz.amount > 1 ? pz.amount + " × " : "") + "2× TOKEN";
      var box = document.createElement("div"); box.className = "tt-prize";
      var b = document.createElement("button"); b.type = "button";
      var left = Math.max(0, (pz.max || 1) - winners.length);
      if (mine) { b.textContent = "✓ YOU GOT IT · " + what; b.disabled = true; b.className = "got"; }
      else if (!left) { b.textContent = "GONE · " + what; b.disabled = true; b.className = "gone"; }
      else if (!me) { b.textContent = "🎁 " + what + " · JOIN THE TOURNAMENT TO CLAIM"; b.disabled = true; }
      else {
        b.textContent = "🎁 CLAIM · " + what + ((pz.max || 1) > 1 ? " · " + left + " LEFT" : "");
        b.onclick = function () {
          b.disabled = true;
          rpc("c4tb_msg_claim", { p_name: myName(), p_token: token(), p_id: m.id }).then(function (r) {
            if (r && r.ok) gifts.award(r.kind, r.amount);
            loadMsgs();
          }, function () { b.disabled = false; b.textContent = "COULDN'T REACH IT · TAP AGAIN"; });
        };
      }
      box.appendChild(b);
      if (winners.length) {
        var w = document.createElement("div"); w.className = "who";
        w.textContent = (winners.length > 1 ? "WINNERS: " : "WINNER: ") + winners.map(function (x) { return shown(x).toUpperCase(); }).join(", ");
        box.appendChild(w);
      }
      return box;
    }
    var rulesWeek = "this", rulesOpen = {}, boardFresh = false, autoMsgs = false;
    function paintMsgs(list) {
      if (isJustin()) {
        var box = document.createElement("div"); box.className = "tt-compose";
        var ta = document.createElement("textarea"); ta.rows = 1; ta.maxLength = 1000; ta.placeholder = "Say something to everybody…";
        ta.value = store.getStr("ftt.msgdraft", "");
        var fit = function () { ta.style.height = "auto"; ta.style.height = Math.min(160, ta.scrollHeight + 2) + "px"; };
        ta.addEventListener("input", function () { store.setStr("ftt.msgdraft", ta.value); fit(); });
        setTimeout(fit, 0);
        var go = document.createElement("button"); go.type = "button"; go.textContent = "POST";
        go.addEventListener("click", function () {
          var t = ta.value.trim(); if (!t) return;
          go.disabled = true;
          rpc("c4tb_msg_post", { p_name: myName(), p_token: token(), p_body: t }).then(function (r) {
            go.disabled = false;
            if (r && r.ok) { store.setStr("ftt.msgdraft", ""); ta.value = ""; loadMsgs().then(markRead); }
            else go.textContent = "COULDN'T POST";
          }, function () { go.disabled = false; go.textContent = "TRY AGAIN"; });
        });
        box.appendChild(ta); box.appendChild(go); list.appendChild(box);
      }
      list.appendChild(heading("NEWS"));
      if (!msgs) { list.appendChild(empty("Loading…")); loadMsgs(); }
      else if (!msgs.length) list.appendChild(empty("Nothing new."));
      (msgs || []).forEach(function (m) {
        var el = document.createElement("div"); el.className = "tt-msg" + (m.id > msgSeenAtOpen ? " new" : "");
        var hd = document.createElement("div"); hd.className = "hd";
        var who = document.createElement("b"); who.textContent = "JUSTIN";
        var when = document.createElement("span"); when.textContent = Date.now() - m.at < 60000 ? "JUST NOW" : ago(m.at).toUpperCase();
        hd.appendChild(who); hd.appendChild(when);
        if (isJustin()) {
          var x = document.createElement("button"); x.type = "button"; x.textContent = "✕"; x.setAttribute("aria-label", "Delete message");
          x.addEventListener("click", function () {
            rpc("c4tb_msg_delete", { p_name: myName(), p_token: token(), p_id: m.id }).then(loadMsgs, function () {});
          });
          hd.appendChild(x);
        }
        el.appendChild(hd); el.appendChild(fmtMsg(m.body));
        if (m.prize) el.appendChild(prizeBox(m));
        list.appendChild(el);
      });
      // and the rules, always there under the news; while next week plays differently, a button for each
      var wk = board && board.week ? String(board.week) : "";
      var nx = wk ? new Date(Date.parse(wk + "T12:00:00Z") + 7 * 864e5).toISOString().slice(0, 10) : "";
      var differs = wk && isPoints(wk) !== isPoints(nx);
      if (!differs) rulesWeek = "this";
      setRules(rulesWeek === "next" ? { week: nx } : null);
      var ol = document.querySelector("#tt-rules .tt-rlist");
      if (ol) {
        // one line a rule; tap a line to open it
        var rh = heading("THE RULES"); rh.classList.add("tt-rhd");
        if (differs) {
          var sw = document.createElement("span"); sw.className = "tt-rpills";
          [["this", "THIS WEEK"], ["next", "NEXT WEEK"]].forEach(function (o) {
            var bt = document.createElement("button"); bt.type = "button"; bt.textContent = o[1];
            if (rulesWeek === o[0]) bt.className = "on";
            bt.onclick = function () { rulesWeek = o[0]; paintBoard(); };
            sw.appendChild(bt);
          });
          rh.appendChild(sw);
        }
        list.appendChild(rh);
        var pts = isPoints(rulesWeek === "next" ? nx : wk);
        var icons = pts ? ["💵", "🔁", "🎯", "🕛", "🏆", "🔥", "🙂"] : ["💵", "🏆", "🔥", "📝", "📋", "📱"];
        [].forEach.call(ol.children, function (li, i) {
          var c = li.cloneNode(true), b = c.querySelector("b");
          var title = b ? b.textContent : "Rule " + (i + 1);
          if (b) c.removeChild(b);
          title = title.replace(/^[^A-Za-z0-9$]+/, "").replace(/[.:,]\s*$/, "");
          if (title === "Markers") title = "Markers: up to 3 at a time";
          var f = c.firstChild;
          if (f && f.nodeType === 3) f.textContent = f.textContent.replace(/^\s*([a-z])/, function (m, ch) { return ch.toUpperCase(); });
          var key = (pts ? "p" : "d") + i;
          var el = document.createElement("div"); el.className = "tt-fold" + (rulesOpen[key] ? " open" : "");
          var hd = document.createElement("button"); hd.type = "button";
          var ic = document.createElement("i"); ic.textContent = icons[i] || "•";
          var tx = document.createElement("b"); tx.textContent = title;
          var ch = document.createElement("em"); ch.textContent = "▸";
          hd.appendChild(ic); hd.appendChild(tx); hd.appendChild(ch);
          var body = document.createElement("p"); body.innerHTML = c.innerHTML;
          hd.onclick = function () { rulesOpen[key] = !rulesOpen[key]; el.classList.toggle("open", !!rulesOpen[key]); };
          el.appendChild(hd); el.appendChild(body); list.appendChild(el);
        });
        if ((isPoints(wk) || isPoints(nx)) && typeof Intro !== "undefined" && Intro.demo) {
          var hw = document.createElement("div"); hw.className = "tt-rweek";
          var hb = document.createElement("button"); hb.type = "button"; hb.className = "on"; hb.textContent = "▶ HOW IT WORKS";
          hb.onclick = function () { Intro.demo(); };
          hw.appendChild(hb); list.appendChild(hw);
        }
      }
      setRules();   // the rules sheet itself stays on this week
      markRead();
    }
    (function () {
      var mb = document.querySelector('#tt-tabs [data-t="msgs"]');
      if (mb) { var d = document.createElement("i"); d.className = "tt-dot"; d.hidden = true; mb.appendChild(d); }
    })();
    setInterval(function () { if (!document.hidden) loadMsgs(); }, 60000);
    setTimeout(loadMsgs, 3000);
    document.addEventListener("visibilitychange", function () { if (!document.hidden) loadMsgs(); });

    function empty(text) {
      var el = document.createElement("div"); el.className = "tt-empty"; el.textContent = text; return el;
    }
    document.querySelectorAll("#tt-tabs button").forEach(function (x) {
      x.addEventListener("click", function () {
        var was = tab;
        boardFresh = false; autoMsgs = false;
        tab = x.getAttribute("data-t");
        if (tab === "msgs" && was !== "msgs") msgSeenAtOpen = msgSeen;
        if (tab === "stats" && board) { delete statsCache[board.week]; delete wuCache[board.week]; duelsCache = null; }
        paintBoard();
      });
    });
    $("tt-boardbtn").addEventListener("click", showBoard);
    $("tt-board-x").addEventListener("click", function () { $("tt-board").classList.remove("on"); });
    $("tt-crown-ok").addEventListener("click", function () { $("tt-crown").classList.remove("on"); });
    // While the front page or the board is up, ask the server again every 20 s,
    // so midnight Sunday (or a new day) opens the doors without a reopen.
    setInterval(function () {
      if (document.hidden) return;
      if ($("tt-board").classList.contains("on") || $("screen-menu").classList.contains("on")) refreshLane();
    }, 20000);
    // and right on the stroke of the start, rather than up to 20 s after it
    setInterval(function () {
      if (document.hidden || !board || board.status !== "upcoming") return;
      if (Date.parse(board.starts_at) <= now()) refreshLane();
      else if ($("screen-menu").classList.contains("on")) paintLane();
    }, 5000);

    // back on the front page: ask again, so the lane is never left saying "checking in"
    new MutationObserver(function () {
      if ($("screen-menu").classList.contains("on")) refreshLane();
    }).observe($("screen-menu"), { attributes: true, attributeFilter: ["class"] });

    TT.showBoard = showBoard;
    TT.board = function () { return board; };
    TT.frame = function () { return frame; };
    TT.money = money; TT.signed = signed;
    refreshLane();
  })();
