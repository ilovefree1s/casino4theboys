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
    function k(name) { return "ftt." + TT.week + (TT.day ? "." + TT.day : "") + "." + name; }
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
      store.setStr(k("dirty"), "1");
      clearTimeout(timer);
      timer = setTimeout(push, soon ? 0 : 1200);
    };
    function push(keepalive) {
      if (!TT.week || TT.closed) return;
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
      $("tt-over").classList.add("on");
    }
    /** Midnight under daily rules: today's money is locked in, tomorrow starts at $5,000. */
    function dayOver() {
      if (TT.closed) return;
      TT.closed = true;
      $("tt-over-big").textContent = "A NEW DAY";
      $("tt-over-sub").textContent = "Midnight. Yesterday's profit is locked in, and today starts fresh at $5,000.";
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
      var PICKED = { "2026-11-17": ["Ultimate Texas Hold'em", "Blazing 777"] };
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
        var p = pool(), m = p.length, i = ((n * 2) % m + m) % m;
        return day >= "2026-11-17" ? [p[i], p[(i + 10) % m]] : [p[i]];
      }
      /** The day's hot games, said out loud. */
      function today(day) { return list(day).join(" & "); }
      /** The share of a win's profit the house adds on top: all of it, then half from 18 November. */
      function rate(day) { day = day || TT.day || (frame && frame.day) || ""; return 1; }
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
        if (delta < 0) { sess.out += -delta; return; }
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
          mark(profit);
        }, 0);
      }
      /**
       * The extra shows as one more result pill under the table's own, where the
       * table has pills; elsewhere as a small tag under the bankroll that fades.
       */
      function mark(profit) {
        var scr = document.querySelector(".screen.on");
        var x = "🔥 ×" + tagX().replace("×", ""), amt = "+" + fmt(profit);
        var host = scr && scr.querySelector(".dj-pills, #dd-results");
        if (host && host.children.length) {
          host.insertAdjacentHTML("beforeend", '<div class="dj-pill w" style="border-color:#FF8A3D"><span class="nm" style="color:#FFB238">' +
            x + '</span><span class="amt">' + amt + "</span></div>");
          return;
        }
        var t = document.createElement("div");
        t.textContent = x + "  " + amt;
        t.style.cssText = "position:fixed;right:max(16px,env(safe-area-inset-right,0px));top:calc(env(safe-area-inset-top,0px) + 74px);z-index:9999;" +
          "padding:4px 12px;border-radius:999px;border:1.5px solid #FF8A3D;background:rgba(20,8,4,.9);color:#FFD24D;" +
          "font:800 12px/1.3 system-ui,sans-serif;letter-spacing:.04em;pointer-events:none;transition:opacity .4s;opacity:1";
        document.body.appendChild(t);
        setTimeout(function () { t.style.opacity = "0"; }, 2600);
        setTimeout(function () { t.remove(); }, 3200);
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
      /** A 🔥 2× tag on the hot game's own button, wherever it's listed. */
      function paintBadges() {
        var g = TT.on && TT.day ? list() : [];
        Object.keys(GAMES).forEach(function (id) {
          var btn = $(id);
          if (!btn) return;
          var tag = btn.querySelector(".tt-hot2x");
          var on = g.indexOf(GAMES[id]) >= 0;
          if (!on) { if (tag) tag.remove(); btn.classList.remove("tt-hotbtn"); return; }
          btn.classList.add("tt-hotbtn");
          if (tag && tag.textContent !== "🔥 " + tagX() + " TODAY") { tag.remove(); tag = null; }
          if (!tag) {
            tag = document.createElement("span"); tag.className = "tt-hot2x";
            tag.textContent = "🔥 " + tagX() + " TODAY";
            btn.appendChild(tag);
          }
        });
      }
      function paintBanner() {
        var line = $("lb-campaign-line"), el = $("tt-hotline");
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
      return { today: today, tagX: tagX, sit: sit, track: track, end: end, extra: extra, setExtra: setExtra,
               banner: function () { paintBanner(); paintBadges(); } };
    })();

    // ---- the front-page lane ----
    function laneSay(line, play, playOn) {
      $("tt-line").textContent = line;
      $("tt-play").textContent = play;
      $("tt-play").disabled = !playOn;
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
      var seenRules = "ftt.rules" + (b.daily ? "2." : ".") + b.week;
      if (!store.getStr(seenRules, "")) { store.setStr(seenRules, "1"); showRules(b); }
      var you = b.me, rank = 0;
      if (!b.final) b.rows.forEach(function (r, i) { if (me && r.name.toLowerCase() === me.toLowerCase()) rank = i + 1; });
      if (!me) return laneSay("ENDS IN " + left(b.ends_at), "JOIN", true);
      if (you && you.out && !b.daily) return laneSay("OUT FOR THE WEEK", "OUT", false);
      if (!you || you.fresh) return laneSay("FRESH $" + fmt(b.start_cash) + " WAITING · ENDS IN " + left(b.ends_at), "PLAY", true);
      if (b.daily && you.out) return laneSay("OUT FOR TODAY · FRESH $" + fmt(b.start_cash) + " AT MIDNIGHT", "OUT", false);
      if (b.daily) return laneSay("BEST 3 DAYS " + money(you.score || 0) + " · TODAY " + signed(you.today) +
              (rank ? " · #" + rank : "") + " · 🔥 " + hot.today(b.day).toUpperCase() + " " + hot.tagX(b.day), "PLAY", true);
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
      shout.track(delta);
      duel.track(delta);
    };
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
    /* ---- duels: 10 hands of the same game against another player, for a pot ----
     * The server keeps the duel; the phone counts its own hands and money at
     * that game, and moves the pot in and out of the wallet when the server
     * says to (once each way). */
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
        if (d.status === "pending" && !c && !asked[d.id]) {
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
      if (!rowEl) return;
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
      if (document.hidden || !myName() || !token()) return;
      rpc("c4tb_ping", { p_name: myName(), p_token: token() }).then(function (list) {
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
          p_shown: n.shown || 0, p_backed: n.backed || 0, p_real: n.real || 0, p_scams: n.scams || 0 }).catch(function () {});
      }, 1500);
    };
    function pushStats() {
      if (!statsDirty || !TT.week) return;
      statsDirty = false;
      rpc("c4tb_save_stats", { p_name: myName(), p_token: token(), p_week: TT.week, p_stats: readStats() })
        .catch(function () { statsDirty = true; });
    }

    // ---- the rules ----
    function showRules(b) {
      b = b || board;
      $("tt-rules-head").textContent = b && b.status === "live"
        ? "WEEK " + b.week_no + " · IT'S ON" : "THE TOURNAMENT";
      $("tt-rules").classList.add("on");
      $("tt-rules").scrollTop = 0;
    }
    $("tt-rules-ok").addEventListener("click", function () { $("tt-rules").classList.remove("on"); });
    $("tt-ruleslink").addEventListener("click", function () { showRules(); });

    // ---- joining ----
    function askName(then) {
      $("tt-name").value = myName();
      $("tt-pin").value = "";
      $("tt-join-err").textContent = "";
      $("tt-join").classList.add("on");
      setTimeout(function () { $("tt-name").focus(); }, 60);
      $("tt-join-go").onclick = function () {
        var n = $("tt-name").value.trim().replace(/\s+/g, " ");
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
              j.error === "pin" ? "Wrong PIN for that name." :
              j.error === "taken" ? "That name is taken, and it has no PIN yet. Ask Justin to free it." :
              j.error === "bad_pin" ? "The PIN is 4 digits." : "2–16 letters or numbers.";
            return;
          }
          store.setStr("ftt.name", j.name);
          store.setStr("ftt.haspin", "1");
          $("tt-join").classList.remove("on");
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
        var local = store.getStr(k("dirty"), "");
        // bonuses won at the hot game before a reload: the server's figure has them, a local one gets them now
        if (local && hot.extra()) store.set(k("bankroll"), Math.round(store.get(k("bankroll"), 0)) + hot.extra());
        hot.setExtra(0);
        if (!local) {
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
        if (s.out) { store.setStr(k("out"), "1"); refreshLane(); return showBoard(); }
        TT.on = true; TT.closed = false;
        Shell.mode = "campaign";
        Shell.showLobby();
        hot.banner();
        if (local) push();
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

      if (b.status === "upcoming") {
        $("tt-bhead").textContent = "WEEK 1";
        $("tt-bsub").textContent = "STARTS SUNDAY 12:00 AM · IN " + left(b.starts_at);
        b.players.forEach(function (p, i) { list.appendChild(row(i + 1, p.name, "SIGNED UP", "", p.name.toLowerCase() === me)); });
        if (!b.players.length) list.appendChild(empty("Nobody's signed up yet."));
        return;
      }
      var finalDay = b.day_no === 7;
      $("tt-bhead").textContent = "WEEK " + b.week_no + " · " + (finalDay ? "FINAL DAY" : "DAY " + b.day_no + " OF 7");
      $("tt-bsub").textContent = (finalDay ? "WINNER AT MIDNIGHT · " + left(b.ends_at)
                                           : "STANDINGS UPDATE AT MIDNIGHT · NEXT IN " + left(b.day_ends_at));
      // you, live - nobody else sees this
      var you = $("tt-you"), y = b.me;
      you.hidden = !(y && !y.fresh);
      if (y && !y.fresh) {
        $("tt-you-net").textContent = money(y.net);
        $("tt-you-today").textContent = "TODAY " + signed(y.today);
        $("tt-you-today").className = y.today > 0 ? "up" : y.today < 0 ? "down" : "";
        $("tt-you-tags").textContent = (b.daily ? "BEST 3 DAYS " + money(y.score || 0) + " · " : "") +
          (y.owed ? "OWES $" + fmt(y.owed) + " · " + Math.ceil(y.owed / 7500) + "/" + b.max_markers + " MARKERS · " : "") +
          (y.out ? (b.daily ? "OUT FOR TODAY · " : "OUT FOR THE WEEK · ") : "") + "ONLY YOU SEE THIS UNTIL MIDNIGHT";
      }

      if (b.last_champion) {
        champ.hidden = false;
        $("tt-champ-name").textContent = b.last_champion.name.toUpperCase();
        $("tt-champ-net").textContent = money(b.last_champion.net);
        var seen = "ftt.champseen." + b.last_champion.week;
        if (!store.getStr(seen, "")) {
          store.setStr(seen, "1");
          $("tt-crown-wk").textContent = "WEEK " + (b.week_no - 1) + " CHAMPION";
          $("tt-crown-name").textContent = b.last_champion.name.toUpperCase();
          $("tt-crown-net").textContent = money(b.last_champion.net);
          $("tt-crown").classList.add("on");
        }
      }
      b.daily_winners.forEach(function (w) {
        var c = document.createElement("span");
        c.className = "tt-win";
        var d = document.createElement("b"); d.textContent = dayName(w.day);
        var n = document.createElement("span"); n.textContent = w.name.toUpperCase() + " " + signed(w.profit);
        c.appendChild(d); c.appendChild(n);
        wins.appendChild(c);
      });

      // the first tab is yesterday's day, or last week's final standings until this week has a midnight
      var dayBtn = document.querySelector('#tt-tabs [data-t="day"]');
      if (dayBtn) dayBtn.textContent = b.final ? "LAST WEEK" : "YESTERDAY";
      if (tab === "stats") return paintStats(b);
      if (b.final) {
        if (tab === "day") {
          var hiw = highs(lastWeekOf(b));
          if (b.rows.length) list.appendChild(heading("WEEK " + (b.week_no - 1) + " · FINAL STANDINGS"));
          b.rows.forEach(function (r, i) {
            var t = hiw && hiw[r.name.toLowerCase()] ? "HIGHEST CASH $" + fmt(hiw[r.name.toLowerCase()]) : "";
            list.appendChild(row(i + 1, r.name, t, money(r.net), r.name.toLowerCase() === me));
          });
          if (!b.rows.length) list.appendChild(empty("No standings from last week."));
        } else {
          // a new week: everybody's name, no money until the first midnight
          var all = roster || [];
          if (all.length) list.appendChild(heading("WEEK " + b.week_no + " · " + all.length + " PLAYERS · MONEY SHOWS AT MIDNIGHT"));
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
                               : b.daily ? "BEST 3 DAYS ADDED UP · AS OF " + asOf + " MIDNIGHT" : "THE WEEK · AS OF " + asOf + " MIDNIGHT"));
      var rows = b.rows.slice();
      if (dayTab) rows.sort(function (a, c) { return c.day - a.day || c.net - a.net || a.name.localeCompare(c.name); });
      var hi = b.final ? highs(lastWeekOf(b)) : null;
      rows.forEach(function (r, i) {
        var v = dayTab ? signed(r.day) : money(r.net);
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
        if (!rows.length && rest.length) list.appendChild(heading("THE WEEK · " + roster.length + " PLAYERS · MONEY SHOWS AT MIDNIGHT"));
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
        list.appendChild(row("", r.name, "BACKED " + r.backed + " · REAL " + r.real + " · SCAMS " + r.scams,
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
      paintDuels(list);
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
            if (top.net > 0) bits.push("BEST " + top.name.toUpperCase() + " " + signed(top.net));
            if (low.net < 0) bits.push("WORST " + low.name.toUpperCase() + " " + signed(low.net));
            var big = x.rs.reduce(function (m, r) { return r.best > m.best ? r : m; }, x.rs[0]);
            if (big.best > 0) bits.push("BIGGEST HIT " + big.name.toUpperCase() + " $" + fmt(big.best));
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
    }
    /** Everyone signed up, by lowercase name. */
    function players() {
      var m = {};
      (roster || []).forEach(function (p) { m[String(p.name || p).toLowerCase()] = 1; });
      ((board && board.rows) || []).forEach(function (p) { m[String(p.name).toLowerCase()] = 1; });
      return m;
    }
    function heading(text) {
      var el = document.createElement("div"); el.className = "tt-sechead"; el.textContent = text; return el;
    }
    function row(rank, name, tags, value, mine) {
      var el = document.createElement("div");
      el.className = "tt-row" + (mine ? " me" : "");
      var a = document.createElement("span"); a.className = "rk"; a.textContent = rank;
      var mid = document.createElement("span"); mid.className = "who";
      var n = document.createElement("b"); n.textContent = name.toUpperCase() + (mine ? " (YOU)" : "");
      if (players()[String(name).toLowerCase()]) el.setAttribute("data-player", name);
      var lk = String(name).toLowerCase();
      if (online[lk]) {
        var dot = document.createElement("i"); dot.className = "tt-on"; dot.title = "Online";
        n.insertBefore(dot, n.firstChild);
      } else if (lastSeen[lk]) {
        var gd = document.createElement("i"); gd.className = "tt-on off"; gd.title = "Last on " + ago(lastSeen[lk]);
        n.insertBefore(gd, n.firstChild);
        var ag = document.createElement("span"); ag.className = "tt-ago"; ag.textContent = " " + ago(lastSeen[lk]);
        n.appendChild(ag);
      }
      var t = document.createElement("small"); t.textContent = tags;
      mid.appendChild(n); if (tags) mid.appendChild(t);
      var v = document.createElement("span"); v.className = "val"; v.textContent = value;
      el.appendChild(a); el.appendChild(mid); el.appendChild(v);
      return el;
    }
    function empty(text) {
      var el = document.createElement("div"); el.className = "tt-empty"; el.textContent = text; return el;
    }
    document.querySelectorAll("#tt-tabs button").forEach(function (x) {
      x.addEventListener("click", function () {
        tab = x.getAttribute("data-t");
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
