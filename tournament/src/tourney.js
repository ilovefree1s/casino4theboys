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
    function k(name) { return "ftt." + TT.week + "." + name; }
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
      var w = readWallet(), wk = TT.week;
      rpc("c4tb_sync", {
        p_name: myName(), p_token: token(), p_week: wk,
        p_cash: w.cash, p_owed: w.owed, p_markers: w.markers, p_out: w.out
      }, keepalive).then(function (j) {
        inflight = false;
        clearTimeout(retry);
        if (j.error === "rollover") return weekOver();
        if (j.week && j.week !== wk) return weekOver();
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
    $("tt-over-go").addEventListener("click", function () {
      store.setStr("ftt.showboard", "1");
      location.reload();
    });
    setInterval(function () {
      if (frame && frame.status === "live" && TT.on && Date.parse(frame.ends_at) <= now()) weekOver();
    }, 15000);

    // ---- the front-page lane ----
    function laneSay(line, play, playOn) {
      $("tt-line").textContent = line;
      $("tt-play").textContent = play;
      $("tt-play").disabled = !playOn;
    }
    function refreshLane() {
      if (!ready) return laneSay("NOT SET UP YET", "SOON", false);
      return rpc("c4tb_board", {}).then(function (b) {
        board = b; frame = b;
        paintLane();
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
      $("tt-tag").textContent = "WEEK " + b.week_no + " · " + (b.day_no === 7 ? "FINAL DAY" : "DAY " + b.day_no + " OF 7");
      // the first time the app is opened in a new week, the rules come up
      var seenRules = "ftt.rules." + b.week;
      if (!store.getStr(seenRules, "")) { store.setStr(seenRules, "1"); showRules(b); }
      var row = null, rank = 0;
      b.rows.forEach(function (r, i) { if (me && r.name.toLowerCase() === me.toLowerCase()) { row = r; rank = i + 1; } });
      if (!me) return laneSay(b.rows.length + " PLAYING · ENDS IN " + left(b.ends_at), "JOIN", true);
      if (row && row.out) return laneSay("OUT FOR THE WEEK · FINISHED #" + rank, "OUT", false);
      if (!row) return laneSay("FRESH $" + fmt(b.start_cash) + " WAITING · ENDS IN " + left(b.ends_at), "PLAY", true);
      laneSay("#" + rank + " OF " + b.rows.length + " · " + money(row.net) + " · TODAY " + signed(row.today), "PLAY", true);
    }

    // ---- game stats: every move of the purse, put down to the table it happened at ----
    var GAMES = {
      "bm-punto": "Punto Banco", "bm-dragontiger": "Dragon Tiger",
      "bjm-freebet": "Free Bet Blackjack", "bjm-blazing": "Blazing 777", "bjm-doubledown": "Double Down Madness",
      "pk-uth": "Ultimate Texas Hold'em", "pk-djwild": "DJ Wild", "pk-shootout": "Shootout",
      "pk-mini": "Mini UTH", "pk-suits": "I Luv Suits",
      "rlm-classic": "Double Zero Roulette", "rlm-lightning": "Lightning Roulette", "rlm-doubleball": "Double Ball Roulette",
      "cm-crapless": "Crapless Craps", "cm-destroyer": "Destroyer",
      "om-keno": "Keno", "om-wheel": "Money Wheel", "om-sicbo": "Sic Bo", "om-plinko": "Plinko", "om-mines": "Mines"
    };
    var statsDirty = false;
    // capture, so the game is named before its own click handler deals the first hand
    document.addEventListener("click", function (e) {
      var b = e.target.closest && e.target.closest("button[id]");
      if (b && GAMES[b.id]) TT.game = GAMES[b.id];
    }, true);
    function readStats() {
      try { return JSON.parse(store.getStr(k("stats"), "{}")) || {}; } catch (e) { return {}; }
    }
    /** The purse moved by [delta] at the table you are at. Markers do not count. */
    TT.track = function (delta) {
      if (!TT.on || TT.quiet || !TT.game || !delta || !TT.week) return;
      var s = readStats(), g = s[TT.game] || (s[TT.game] = { net: 0, best: 0 });
      g.net += delta;
      if (delta > g.best) g.best = delta;
      store.setStr(k("stats"), JSON.stringify(s));
      statsDirty = true;
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
      $("tt-join-err").textContent = "";
      $("tt-join").classList.add("on");
      setTimeout(function () { $("tt-name").focus(); }, 60);
      $("tt-join-go").onclick = function () {
        var n = $("tt-name").value.trim().replace(/\s+/g, " ");
        if (n.length < 2 || n.length > 16 || !/^[A-Za-z0-9 ._'-]+$/.test(n)) {
          $("tt-join-err").textContent = "2–16 letters or numbers.";
          return;
        }
        $("tt-join-go").disabled = true;
        rpc("c4tb_join", { p_name: n, p_token: token() }).then(function (j) {
          $("tt-join-go").disabled = false;
          if (!j.ok) {
            $("tt-join-err").textContent = j.error === "taken"
              ? "That name is already taken on another phone."
              : "2–16 letters or numbers.";
            return;
          }
          store.setStr("ftt.name", j.name);
          $("tt-join").classList.remove("on");
          if (then) then(); else refreshLane();
        }, function () {
          $("tt-join-go").disabled = false;
          $("tt-join-err").textContent = "Can't reach the tournament. Check your signal.";
        });
      };
    }
    $("tt-join-x").addEventListener("click", function () { $("tt-join").classList.remove("on"); });
    $("tt-name").addEventListener("keydown", function (e) { if (e.key === "Enter") $("tt-join-go").click(); });

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
        var local = store.getStr(k("dirty"), "");
        if (!local) writeWallet({ cash: s.cash, owed: s.owed, markers: s.markers, out: s.out });
        if (s.out) { store.setStr(k("out"), "1"); refreshLane(); return showBoard(); }
        TT.on = true; TT.closed = false;
        Shell.mode = "campaign";
        Shell.showLobby();
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
      var leader = b.rows[0];
      $("tt-bsub").textContent = finalDay
        ? (leader ? "LEADING: " + leader.name.toUpperCase() + " · " : "") + "WINNER AT MIDNIGHT · " + left(b.ends_at)
        : "TODAY RESETS IN " + left(b.day_ends_at) + " · WEEK ENDS IN " + left(b.ends_at);

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

      if (tab === "stats") return paintStats(b);
      var rows = b.rows.slice();
      if (tab === "today") rows.sort(function (a, c) { return c.today - a.today || c.net - a.net || a.name.localeCompare(c.name); });
      rows.forEach(function (r, i) {
        var tags = [];
        if (r.owed) tags.push("OWES $" + fmt(r.owed) + " · " + Math.ceil(r.owed / 7500) + "/" + b.max_markers + " MARKERS");
        if (r.out) tags.push("OUT");
        var v = tab === "today" ? signed(r.today) : money(r.net);
        var el = row(i + 1, r.name, tags.join(" · "), v, r.name.toLowerCase() === me);
        if (r.out) el.classList.add("out");
        if (tab === "today") el.classList.add(r.today > 0 ? "up" : r.today < 0 ? "down" : "flat");
        list.appendChild(el);
      });
      if (!rows.length) list.appendChild(empty("Nobody has played yet this week."));
    }
    /*
     * The stats tab. Last week's, everyone's, once it is final: each game with
     * what the boys made or lost on it in all and who did best and worst there,
     * then each player's best and worst game. Under that, your own games this week.
     */
    var statsCache = {};
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
      var have = want.every(function (w) { return statsCache[w]; });
      if (!have) {
        list.appendChild(empty("Loading stats…"));
        Promise.all(want.map(getStats)).then(function () { if (tab === "stats") paintStats(b); },
          function () { list.innerHTML = ""; list.appendChild(empty("Can't reach the stats right now.")); });
        return;
      }
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
    function heading(text) {
      var el = document.createElement("div"); el.className = "tt-sechead"; el.textContent = text; return el;
    }
    function row(rank, name, tags, value, mine) {
      var el = document.createElement("div");
      el.className = "tt-row" + (mine ? " me" : "");
      var a = document.createElement("span"); a.className = "rk"; a.textContent = rank;
      var mid = document.createElement("span"); mid.className = "who";
      var n = document.createElement("b"); n.textContent = name.toUpperCase() + (mine ? " (YOU)" : "");
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
        if (tab === "stats" && board) delete statsCache[board.week];
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

    TT.showBoard = showBoard;
    TT.board = function () { return board; };
    TT.frame = function () { return frame; };
    TT.money = money; TT.signed = signed;
    refreshLane();
  })();
