  /* ============================================================
     KIRBY — three-card guts with a dummy hand.
     Everyone antes into one pot and gets three cards. On the count of three
     each player holds or drops. The best hand that held takes the pot, as
     long as it beats Kirby, the dummy hand that always plays; every other
     hand that held pays the winner the pot amount too. If Kirby beats them
     all, every hand that held matches the pot and it rides to the next deal.
     Once a hand takes the pot, everyone antes again. If everybody drops,
     everybody antes again into the same pot.
     Ranks: straight flush, three of a kind, pair, high card. Plain straights
     and flushes count for nothing. 2s wild is a switch.
     ============================================================ */
  var KB = (function () {
    var SF = 3, TRIPS = 2, PAIR = 1, HIGH = 0;
    var CAT = ["High Card", "Pair", "Three of a Kind", "Straight Flush"];
    var NAMES = { 14: "A", 13: "K", 12: "Q", 11: "J", 10: "10" };
    function val(r) { return r === "A" ? 14 : r === "K" ? 13 : r === "Q" ? 12 : r === "J" ? 11 : parseInt(r, 10); }
    function nm(v) { return NAMES[v] || String(v); }
    function pack(cat, a, b, c) { return ((cat * 15 + (a || 0)) * 15 + (b || 0)) * 15 + (c || 0); }
    /** Three plain cards, as {v, s}. */
    function score3(cs) {
      var v = cs.map(function (c) { return c.v; }).sort(function (x, y) { return y - x; });
      if (v[0] === v[1] && v[1] === v[2]) return pack(TRIPS, v[0]);
      var flush = cs[0].s === cs[1].s && cs[1].s === cs[2].s;
      var top = 0;
      if (v[0] - 1 === v[1] && v[1] - 1 === v[2]) top = v[0];
      else if (v[0] === 14 && v[1] === 3 && v[2] === 2) top = 3;     // A-2-3
      if (flush && top) return pack(SF, top);
      if (v[0] === v[1]) return pack(PAIR, v[0], v[2]);
      if (v[1] === v[2]) return pack(PAIR, v[1], v[0]);
      return pack(HIGH, v[0], v[1], v[2]);
    }
    var ALL = [];
    for (var s = 0; s < 4; s++) for (var r = 2; r <= 14; r++) ALL.push({ v: r, s: s });
    var SUITIX = { "♠": 0, "♥": 1, "♦": 2, "♣": 3 };
    /** A dealt hand's score; with wilds on, each 2 (and a joker, if one
        ever turns up) becomes whatever card makes the best hand. */
    function score(cards, wild) {
      var fixed = [], wilds = 0;
      cards.forEach(function (c) {
        if (wild && (c.joker || c.rank === "2")) wilds++;
        else fixed.push({ v: val(c.rank), s: SUITIX[c.suit] });
      });
      if (wilds === 0) return score3(fixed);
      if (wilds === 3) return pack(SF, 14);
      var best = -1;
      if (wilds === 1) {
        for (var i = 0; i < ALL.length; i++) best = Math.max(best, score3(fixed.concat([ALL[i]])));
      } else {
        for (var a = 0; a < ALL.length; a++)
          for (var b = a; b < ALL.length; b++)
            best = Math.max(best, score3(fixed.concat([ALL[a], ALL[b]])));
      }
      return best;
    }
    function catOf(sc) { return Math.floor(sc / 3375); }
    function name(sc) {
      var cat = catOf(sc), a = Math.floor(sc / 225) % 15;
      if (cat === SF) return a === 14 ? "Mini Royal" : nm(a) + "-high Straight Flush";
      if (cat === TRIPS) return "Three " + nm(a) + "s";
      if (cat === PAIR) return "Pair of " + nm(a) + "s";
      return nm(a) + " High";
    }
    // How a hand stacks up against a random one, for the computer players.
    var SAMPLE = {};
    function sample(wild) {
      var k = wild ? "w" : "n";
      if (SAMPLE[k]) return SAMPLE[k];
      var out = [];
      for (var n = 0; n < 6000; n++) out.push(score(buildShoe(1).slice(0, 3), wild));
      out.sort(function (x, y) { return x - y; });
      return (SAMPLE[k] = out);
    }
    function strength(sc, wild) {
      var a = sample(wild), lo = 0, hi = a.length;
      while (lo < hi) { var m = (lo + hi) >> 1; if (a[m] < sc) lo = m + 1; else hi = m; }
      return lo / a.length;
    }
    return { score: score, name: name, catOf: catOf, strength: strength, CAT: CAT };
  })();
  window.KB = KB;   // exposed so the rules can be exercised directly

  var Kirby = (function () {
    var ORANGE = "#FF9A3D", GOLD = "#FFD24D", DIM = "rgba(255,154,61,.6)",
        LOSS = "#FF4D4D", BRASS = "#D9B23A";
    // The four computer players, each with how good a hand it wants to hold.
    var BOTS = [
      { name: "SAL", want: 0.80 }, { name: "LEFTY", want: 0.70 },
      { name: "MO", want: 0.86 }, { name: "DEUCE", want: 0.62 }
    ];
    var SEATS = BOTS.length + 1;          // you are seat 0
    var bankroll = FREEPLAY.buyIn, selectedChip = 25;
    // betting | dealing | decide | count | showdown | owe | result
    var phase = "betting";
    var t0 = { pot: 0, ante: 0, fresh: true, wild: false };
    var table = null;                      // pot, ante, fresh (ante due), wild
    var hands = [], kirby = [], scores = [], kirbyScore = 0;
    var hold = [], youChose = null, outcome = null, owed = 0, results = [];
    var timers = [];
    var memo = {};                          // free play's table, kept apart

    function t(ms, fn) { timers.push(setTimeout(fn, ms)); }
    function clearTimers() { timers.forEach(clearTimeout); timers = []; }
    function syncWallet() {
      if (Shell.mode !== "campaign") return;
      Shell.setBankroll(bankroll);
      Shell.demoteIfBroke();
    }
    // The pot belongs to the wallet it was played from, so it is kept under
    // that wallet's own name: the campaign's, this week's tournament's, or
    // free play's (which only lasts while the page is open).
    function key() { return Shell.mode === "campaign" ? wkey("kirby") : null; }
    function load() {
      var k = key(), v = null;
      if (k) { try { v = JSON.parse(localStorage.getItem(k) || "null"); } catch (e) {} }
      else v = memo.table || null;
      table = v && typeof v.pot === "number" ? v : JSON.parse(JSON.stringify(t0));
    }
    function save() {
      var k = key();
      if (k) { try { localStorage.setItem(k, JSON.stringify(table)); } catch (e) {} }
      else memo.table = JSON.parse(JSON.stringify(table));
    }

    // ---------- render ----------
    function setMessage(text, tone) {
      var m = $("kb-message");
      m.textContent = text;
      m.className = "uth-message" + (tone ? " " + tone : "");
    }
    function slot(host) { var s = document.createElement("div"); s.className = "slot"; host.appendChild(s); }
    function renderTop() {
      $("kb-bankroll").textContent = fmt(bankroll);
      $("kb-potsm").textContent = fmt(table.pot);
      $("kb-pot").textContent = "$" + fmt(table.pot);
      $("kb-potsub").textContent =
        table.fresh ? (table.pot > 0 ? "EVERYBODY DROPPED · ANTE AGAIN $" + fmt(table.ante) : "ANTE TO START")
          : "POT RIDES · NO ANTE · ANTE WAS $" + fmt(table.ante);
    }
    function label(id, text, badge, cls) {
      var l = $(id);
      l.className = "uth-label" + (cls ? " " + cls : "") + (id === "kb-you-label" && !cls ? " gold" : "");
      l.innerHTML = '<span class="nm">' + text + "</span>" + (badge ? '<span class="badge">' + badge + "</span>" : "");
    }
    var shown = function () { return phase === "showdown" || phase === "owe" || phase === "result"; };
    function renderCards() {
      var k = $("kb-kirby"); k.innerHTML = "";
      for (var i = 0; i < 3; i++) {
        if (kirby[i]) k.appendChild(cardEl(kirby[i], shown() && hold.some(Boolean))); else slot(k);
      }
      var kirbyWon = outcome && outcome.kirby;
      $("kb-kirby").parentNode.classList.toggle("win", !!kirbyWon);
      label("kb-kirby-label", "KIRBY", shown() && kirby.length && hold.some(Boolean) ? KB.name(kirbyScore) : null, kirbyWon ? "lose" : "");
      // the computer players
      var seats = $("kb-seats"); seats.innerHTML = "";
      BOTS.forEach(function (b, j) {
        var seat = j + 1, el = document.createElement("div");
        var cls = "kb-seat", tag = "";
        if (phase === "showdown" || phase === "owe" || phase === "result" || phase === "count2") {
          if (hold[seat]) { cls += " hold"; tag = "HOLD"; } else { cls += " drop"; tag = "DROP"; }
        }
        if (outcome && outcome.net[seat]) {
          if (outcome.net[seat] > 0) { cls += " win"; tag = "+" + fmt(outcome.net[seat]); }
          else { cls += " lose"; tag = "PAYS " + fmt(-outcome.net[seat]); }
        }
        el.className = cls;
        el.innerHTML = '<div class="hd"><span class="nm">' + b.name + '</span><span class="tag">' + tag + "</span></div>";
        var c = document.createElement("div"); c.className = "cards kb-sm";
        for (var i = 0; i < 3; i++) {
          if (hands[seat] && hands[seat][i]) c.appendChild(cardEl(hands[seat][i], shown() && hold[seat]));
          else slot(c);
        }
        el.appendChild(c);
        var h = document.createElement("div"); h.className = "hand";
        h.textContent = shown() && hold[seat] ? KB.name(scores[seat]) : "";
        el.appendChild(h);
        seats.appendChild(el);
      });
      // you
      var y = $("kb-you"); y.innerHTML = "";
      for (var q = 0; q < 3; q++) {
        if (hands[0] && hands[0][q]) y.appendChild(cardEl(hands[0][q], true)); else slot(y);
      }
      y.classList.toggle("you-held", youChose === "hold" && phase !== "decide");
      var youCls = outcome && outcome.net[0] > 0 ? "win" : outcome && outcome.net[0] < 0 ? "lose" : "";
      label("kb-you-label", "YOU" + (youChose && phase !== "decide" && phase !== "dealing" ? " · " + (youChose === "hold" ? "HOLD" : "DROP") : ""),
        hands[0] ? KB.name(scores[0]) : null, youCls);
    }
    function renderOpts() {
      var o = $("kb-opts"); o.innerHTML = "";
      var b = document.createElement("button");
      b.className = "kb-toggle" + (table.wild ? " on" : "");
      b.textContent = "2s WILD · " + (table.wild ? "ON" : "OFF");
      // the switch only moves between games, never with a pot riding
      b.disabled = !(phase === "betting" && table.pot === 0);
      b.addEventListener("click", function () {
        if (phase !== "betting" || table.pot !== 0) return;
        table.wild = !table.wild; save();
        setMessage(table.wild ? "2s are wild" : "2s are just 2s");
        renderOpts();
      });
      o.appendChild(b);
    }
    function picking() { return phase === "betting" && table.pot === 0; }
    function renderRack() {
      var host = $("kb-rack"); host.innerHTML = "";
      if (!picking()) return;
      var rack = document.createElement("div");
      rack.className = "uth-rack";
      selectedChip = clampChip(selectedChip, bankroll);
      chipsFor(bankroll, Shell.limits()).forEach(function (v) {
        var cb = document.createElement("button");
        cb.className = "chip-btn" + (selectedChip === v ? " sel" : "");
        cb.innerHTML = chipFace(v);
        cb.addEventListener("click", function () { selectedChip = v; renderRack(); renderActions(); });
        rack.appendChild(cb);
      });
      host.appendChild(rack);
    }
    function actionBtn(text, color, fn, off) {
      var b = document.createElement("button");
      b.className = "uth-btn";
      b.style.borderColor = color; b.style.color = color;
      b.textContent = text; b.disabled = !!off;
      b.addEventListener("click", fn);
      return b;
    }
    function anteNow() { return picking() ? selectedChip : table.ante; }
    function renderActions() {
      var a = $("kb-actions"); a.innerHTML = "";
      if (phase === "betting") {
        var due = table.fresh ? anteNow() : 0;
        a.appendChild(actionBtn(due ? "ANTE $" + fmt(due) + " · DEAL" : "DEAL · POT $" + fmt(table.pot),
          GOLD, deal, due > bankroll || (picking() && due <= 0)));
      } else if (phase === "decide") {
        a.appendChild(actionBtn("DROP", LOSS, function () { choose("drop"); }));
        a.appendChild(actionBtn("HOLD", GOLD, function () { choose("hold"); }));
      } else if (phase === "result") {
        a.appendChild(actionBtn(table.pot === 0 ? "NEW GAME" : "NEXT HAND · POT $" + fmt(table.pot), GOLD, nextHand));
      }
    }
    function renderRebuy() {
      var broke = Shell.mode === "testing" && bankroll < 25 && phase === "betting" && table.pot === 0;
      $("kb-rebuy").classList.toggle("on", broke);
    }
    function renderAll() { renderTop(); renderCards(); renderOpts(); renderRack(); renderActions(); renderRebuy(); }

    // ---------- the deal ----------
    function deal() {
      if (phase !== "betting") return;
      if (table.fresh) {
        var ante = anteNow();
        if (picking()) {
          var lim = Shell.limits();
          ante = allow(lim, ante, 0, false);
          if (ante <= 0) { setMessage(refusal(lim, false)); return; }
          table.ante = ante;
        }
        if (bankroll < table.ante) { setMessage("Not enough for the ante"); return; }
        bankroll -= table.ante;
        syncWallet();
        table.pot += table.ante * SEATS;     // the four at the table ante with you
        table.fresh = false;
        save();
      }
      var shoe = buildShoe(1);
      hands = []; kirby = []; scores = []; hold = []; youChose = null; outcome = null; results = [];
      for (var s = 0; s < SEATS; s++) hands.push([]);
      phase = "dealing";
      $("kb-count").textContent = "";
      setMessage("Dealing…");
      renderAll();
      // one at a time round the table, Kirby last, three times
      var step = 0;
      for (var r = 0; r < 3; r++) {
        for (var p = 0; p <= SEATS; p++) {
          (function (seat) {
            t(90 + step * 70, function () {
              var c = shoe.pop();
              if (seat === SEATS) kirby.push(c); else hands[seat].push(c);
              renderCards();
            });
          })(p);
          step++;
        }
      }
      t(140 + step * 70, function () {
        for (var s = 0; s < SEATS; s++) scores[s] = KB.score(hands[s], table.wild);
        kirbyScore = KB.score(kirby, table.wild);
        phase = "decide";
        setMessage("Hold or drop?");
        renderAll();
      });
    }
    function botHolds(seat) {
      var b = BOTS[seat - 1];
      var want = b.want + (Math.random() - 0.5) * 0.1;
      return KB.strength(scores[seat], table.wild) >= want;
    }
    function choose(which) {
      if (phase !== "decide") return;
      youChose = which;
      hold[0] = which === "hold";
      for (var s = 1; s < SEATS; s++) hold[s] = botHolds(s);
      phase = "count";
      setMessage(which === "hold" ? "Holding…" : "Dropping…");
      renderAll();
      $("kb-count").textContent = "1";
      t(450, function () { $("kb-count").textContent = "2"; });
      t(900, function () { $("kb-count").textContent = "3!"; });
      t(1300, function () {
        $("kb-count").textContent = "";
        phase = "showdown";
        renderAll();
        t(900, showdown);
      });
    }

    // ---------- showdown ----------
    function showdown() {
      var P = table.pot, held = [];
      for (var s = 0; s < SEATS; s++) if (hold[s]) held.push(s);
      var net = []; for (var q = 0; q < SEATS; q++) net.push(0);
      outcome = { net: net, kirby: false, none: false };
      if (!held.length) {
        // nobody played: same pot, everybody antes again
        outcome.none = true;
        table.fresh = true;
        save();
        return finish("Everybody dropped — ante again", "");
      }
      var best = Math.max.apply(null, held.map(function (s) { return scores[s]; }));
      var payers;
      if (kirbyScore >= best) {
        // Kirby holds up (a tie goes to Kirby): everyone who held matches the pot
        outcome.kirby = true;
        payers = held;
      } else {
        // A hand beat Kirby and everyone: it takes the pot, every other hand
        // that held pays it the pot amount too, and the next deal antes fresh.
        var winners = held.filter(function (s) { return scores[s] === best; });
        payers = held.filter(function (s) { return scores[s] !== best; });
        outcome.toWinner = true;
        var share = Math.floor(P * (1 + payers.length) / winners.length);
        winners.forEach(function (s) { net[s] += share; });
        table.pot = 0;
        if (net[0] > 0) { bankroll += share; syncWallet(); }
      }
      payers.forEach(function (s) { if (s !== 0) { net[s] -= P; if (!outcome.toWinner) table.pot += P; } });
      save();
      if (payers.indexOf(0) >= 0) { owed = P; return collect(); }
      settled();
    }
    /** You held and lost: the pot gets matched, with a marker if it takes one. */
    function collect() {
      if (bankroll >= owed) return pay(owed);
      if (Shell.mode === "campaign" && Shell.canTakeMarker()) {
        phase = "owe";
        renderAll();
        $("kb-owe-amt").textContent = "$" + fmt(owed);
        $("kb-owe-sub").textContent = "You have $" + fmt(bankroll) + ". Take a marker to cover it, or pay what you have.";
        $("kb-owe").classList.add("on");
        return;
      }
      pay(bankroll);
    }
    function pay(amount) {
      $("kb-owe").classList.remove("on");
      amount = Math.min(amount, bankroll);
      bankroll -= amount;
      syncWallet();
      if (!outcome.toWinner) table.pot += amount;   // to Kirby's pot, or straight to the hand that beat you
      outcome.net[0] -= amount;
      outcome.short = owed - amount;
      owed = 0;
      save();
      settled();
    }
    $("kb-owe-marker").addEventListener("click", function () {
      if (phase !== "owe") return;
      Shell.takeMarker();
      bankroll = Shell.getBankroll();
      collect();
    });
    $("kb-owe-all").addEventListener("click", function () {
      if (phase !== "owe") return;
      pay(bankroll);
    });
    function settled() {
      if (table.pot === 0) { table.fresh = true; save(); }
      var you = outcome.net[0];
      var msg = outcome.kirby
        ? (hold[0] ? "Kirby wins with " + KB.name(kirbyScore) + " — you match $" + fmt(-you)
                   : "Kirby wins — the pot grows to $" + fmt(table.pot))
        : you > 0 ? "You take the pot! +$" + fmt(you)
        : hold[0] ? "Beat — you pay the winner $" + fmt(-you)
        : (function () {
            var w = []; for (var s = 1; s < SEATS; s++) if (outcome.net[s] > 0) w.push(BOTS[s - 1].name);
            return w.join(" & ") + " takes the pot";
          })();
      if (outcome.short > 0) msg += " (all you had)";
      finish(msg, you > 0 ? "win" : you < 0 ? "lose" : "");
    }
    function finish(msg, tone) {
      phase = "result";
      if (Shell.mode === "campaign" && bankroll >= Shell.getGoal()) msg = "🏆 GOAL REACHED!";
      setMessage(msg, tone);
      renderAll();
      if (Shell.mode === "campaign" && bankroll >= Shell.getGoal()) Shell.gate(refresh);
    }
    function nextHand() {
      if (phase !== "result") return;
      clearTimers();
      phase = "betting";
      hands = []; kirby = []; scores = []; hold = []; youChose = null; outcome = null;
      if (table.pot === 0) { table.fresh = true; save(); }
      setMessage(table.pot === 0 ? "Pick your ante" : table.fresh ? "Ante again — same pot" : "Pot rides — deal");
      renderAll();
      Shell.gate(refresh);
    }
    function refresh() {
      if (Shell.mode === "campaign") bankroll = Shell.getBankroll();
      renderAll();
    }
    function enter() {
      clearTimers();
      $("kb-owe").classList.remove("on");
      $("kb-count").textContent = "";
      phase = "betting";
      hands = []; kirby = []; scores = []; hold = []; youChose = null; outcome = null;
      bankroll = Shell.mode === "campaign" ? Shell.getBankroll() : FREEPLAY.buyIn;
      load();
      setMessage(table.pot === 0 ? "Pick your ante" : table.fresh ? "Ante again — same pot" : "Pot rides — deal");
      renderAll();
      Shell.gate(refresh);
    }
    $("kb-buyin").addEventListener("click", function () {
      if (phase !== "betting" || table.pot !== 0 || bankroll >= 25) return;
      freshStack(function (v) { bankroll = v; setMessage("Pick your ante"); renderAll(); });
    });
    $("kb-back").addEventListener("click", function () { Shell.show("poker"); });
    $("pk-kirby").addEventListener("click", function () { Kirby.enter(); Shell.show("kirby"); });
    return {
      busy: function () { return phase !== "betting" && phase !== "result"; },
      enter: enter
    };
  })();
