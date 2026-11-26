  /* ============================================================
     3 SHOT POKER — one deck. You get two cards; three more, the SHOTS, go
     down face down. Put up the 1st Shot, and the 5 Shot side bet if you like.
     Look at your two cards, then FOLD (the 1st Shot is gone) or RAISE: put up
     the 2nd and 3rd Shots, each the size of the first.
     Then the shots turn over. Each one plays as a three-card hand with your two
     cards, and each bet is paid on its own hand off the table below; under a
     pair it loses. There is no dealer hand to beat.
     The 5 SHOT reads all five cards, and plays out even if you fold.
     Pay table 3 (Wizard of Odds): 5.18% house edge, 5 Shot 5.99%.
     Raise with a pair, any two suited cards, or any straight draw.
     Campaign and Play Testing; the tournament hides it until its server lists it.
     ============================================================ */
  var TSE = (function () {
    // Pay table 3 (Wizard of Odds): paid to one; a hand under a pair loses
    var SHOT = [{ key: "mini", name: "MINI ROYAL", pays: 40 }, { key: "sf", name: "STR FLUSH", pays: 30 }, { key: "trips", name: "3 OF A KIND", pays: 10 },
                { key: "straight", name: "STRAIGHT", pays: 5 }, { key: "flush", name: "FLUSH", pays: 2 }, { key: "pair", name: "PAIR", pays: 1 }];
    var FIVE = [{ key: "royal", name: "ROYAL", pays: 1000 }, { key: "sf", name: "STR FLUSH", pays: 500 }, { key: "quads", name: "4 OF A KIND", pays: 200 },
                { key: "full", name: "FULL HOUSE", pays: 100 }, { key: "flush", name: "FLUSH", pays: 50 }, { key: "straight", name: "STRAIGHT", pays: 10 },
                { key: "trips", name: "3 OF A KIND", pays: 5 }, { key: "two", name: "TWO PAIR", pays: 2 }, { key: "tens", name: "10s OR BETTER", pays: 1 }];
    function v(c) { return c.rank === "A" ? 14 : c.rank === "K" ? 13 : c.rank === "Q" ? 12 : c.rank === "J" ? 11 : parseInt(c.rank, 10); }
    function find(list, k) { for (var i = 0; i < list.length; i++) if (list[i].key === k) return list[i]; return null; }
    /** A run in [vs] (distinct values), the ace playing high or low. */
    function run(vs) {
      var s = vs.slice().sort(function (a, b) { return a - b; });
      for (var i = 1; i < s.length; i++) if (s[i] !== s[i - 1] + 1) break;
      if (i === s.length) return true;
      if (s[s.length - 1] === 14) { var lo = [1].concat(s.slice(0, -1)); for (i = 1; i < lo.length; i++) if (lo[i] !== lo[i - 1] + 1) return false; return true; }
      return false;
    }
    /** The best a three-card shot makes, or null. */
    function three(cs) {
      var vs = cs.map(v), fl = cs[0].suit === cs[1].suit && cs[1].suit === cs[2].suit;
      var distinct = vs[0] !== vs[1] && vs[1] !== vs[2] && vs[0] !== vs[2];
      var st = distinct && run(vs);
      if (st && fl) return find(SHOT, vs.indexOf(14) >= 0 && vs.indexOf(13) >= 0 ? "mini" : "sf");
      if (vs[0] === vs[1] && vs[1] === vs[2]) return find(SHOT, "trips");
      if (st) return find(SHOT, "straight");
      if (fl) return find(SHOT, "flush");
      if (!distinct) return find(SHOT, "pair");
      return null;
    }
    /** The 5 SHOT: both hole cards and all three shots, or null. */
    function five(cs) {
      var vs = cs.map(v), cnt = {};
      vs.forEach(function (x) { cnt[x] = (cnt[x] || 0) + 1; });
      var n = Object.keys(cnt).map(function (k) { return cnt[k]; }).sort(function (a, b) { return b - a; });
      var fl = cs.every(function (c) { return c.suit === cs[0].suit; }), st = n.length === 5 && run(vs);
      if (st && fl) return find(FIVE, Math.min.apply(null, vs) === 10 ? "royal" : "sf");
      if (n[0] === 4) return find(FIVE, "quads");
      if (n[0] === 3 && n[1] === 2) return find(FIVE, "full");
      if (fl) return find(FIVE, "flush");
      if (st) return find(FIVE, "straight");
      if (n[0] === 3) return find(FIVE, "trips");
      if (n[0] === 2 && n[1] === 2) return find(FIVE, "two");
      if (n[0] === 2) { for (var k in cnt) if (cnt[k] === 2 && +k >= 10) return find(FIVE, "tens"); }
      return null;
    }
    return { SHOT: SHOT, FIVE: FIVE, three: three, five: five, v: v };
  })();
  window.TSE = TSE;

  var ThreeShot = (function () {
    var TEAL = "#29E0C0", GOLD = "#FFD24D", DIM = "rgba(245,241,232,.7)";
    var bankroll = FREEPLAY.buyIn, selectedChip = 25;
    // betting | dealing | decide | reveal | result
    var phase = "betting";
    var bets = { s1: 0, five: 0 }, last = { s1: 0, five: 0 }, raised = false, folded = false;
    var hole = [], shots = [], shown = 0, hits = [null, null, null], fiveHit = null;
    var paid = { s: [0, 0, 0], five: 0, fiveBet: 0, stake: 0 };
    var timers = [];
    function t(ms, fn) { timers.push(setTimeout(fn, ms)); }
    function clearTimers() { timers.forEach(clearTimeout); timers = []; }
    function total() { return bets.s1 + bets.five; }
    function atRisk() { return phase === "betting" || phase === "result" ? 0 : bets.s1 * (raised ? 3 : folded ? 0 : 1) + paid.fiveBet; }
    function syncWallet() {
      if (Shell.mode !== "campaign") return;
      Shell.setBankroll(bankroll);
      Shell.demoteIfBroke();
    }
    function setMessage(text, tone) {
      var m = $("ts-message");
      m.textContent = text;
      m.className = "uth-message" + (tone ? " " + tone : "");
    }
    function signed(v) { var a = Math.abs(v), s = a >= 100000 && typeof shortAmt === "function" ? shortAmt(a) : fmt(a); return (v > 0 ? "+$" : v < 0 ? "−$" : "$") + s; }

    // ---------- render ----------
    function renderTop() {
      $("ts-bankroll").textContent = fmt(bankroll);
      $("ts-risk").textContent = fmt(atRisk());
      if (window.purseWin) {
        var purse = document.querySelector("#screen-threeshot .purse");
        if (phase === "result") purseWin.net(purse, net(), true);
        else purseWin.clear(purse);
      }
    }
    function net() { return paid.s[0] + paid.s[1] + paid.s[2] + paid.five; }
    var ORD = ["1ST", "2ND", "3RD"];
    function renderCards() {
      var sh = $("ts-shots"), ho = $("ts-hole");
      sh.innerHTML = ""; ho.innerHTML = "";
      for (var i = 0; i < 3; i++) {
        var col = document.createElement("div"), cap = document.createElement("div");
        col.className = "ts-shot"; cap.className = "cap";
        if (shots[i]) col.appendChild(cardEl(shots[i], i < shown));
        else { var s = document.createElement("div"); s.className = "slot"; col.appendChild(s); }
        var head = "<b>" + ORD[i] + " SHOT</b>";
        if (i < shown && raised) {
          var h = hits[i];
          cap.innerHTML = head + (h ? h.name + " · " + h.pays + ":1" : "NO PAIR");
          cap.classList.add(h ? "win" : "lose");
          if (h) col.classList.add("win");
        } else cap.innerHTML = head + "&nbsp;";
        col.appendChild(cap);
        sh.appendChild(col);
      }
      if (hole.length) hole.forEach(function (c) { ho.appendChild(cardEl(c, true)); });
      else for (i = 0; i < 2; i++) { var sl = document.createElement("div"); sl.className = "slot"; ho.appendChild(sl); }
    }
    function pill(net, staked) {
      if (phase !== "result" || !(staked > 0)) return "";
      return '<span class="dj-pill ' + (net > 0 ? "w" : net < 0 ? "l" : "p") + '"><span class="amt">' + (net > 0 ? "+" : net < 0 ? "−" : "") +
        (net ? (Math.abs(net) >= 100000 && typeof shortAmt === "function" ? shortAmt(Math.abs(net)) : fmt(Math.abs(net))) : "PUSH") + "</span></span>";
    }
    function spot(id, cls, cap, v, tap, pl) {
      var host = $(id); host.innerHTML = "";
      var b = document.createElement("button");
      b.type = "button"; b.className = "ts-spot" + (cls ? " " + cls : ""); b.disabled = !tap;
      b.setAttribute("data-spot", id.replace("ts-spot-", ""));
      b.innerHTML = '<span class="ring">' + (v > 0 ? '<span class="placed">' + chipHtml(v, 42) + "</span>" : '<span class="empty">' + (cls.indexOf("raise") >= 0 ? "RAISE" : "TAP") + "</span>") +
        '</span><span class="cap">' + cap + "</span>" + (pl || "");
      if (tap) b.addEventListener("click", function () { place(b.getAttribute("data-spot")); });
      host.appendChild(b);
    }
    function renderSpots() {
      var open = phase === "betting", s = phase === "betting" ? bets.s1 : bets.s1;
      spot("ts-spot-five", "five", "5 SHOT", phase === "betting" ? bets.five : paid.fiveBet, open, pill(paid.five, paid.fiveBet));
      spot("ts-spot-s1", "", "1ST", s, open, pill(paid.s[0], s));
      spot("ts-spot-s2", "raise" + (raised ? " on" : ""), "2ND", raised ? s : 0, false, raised ? pill(paid.s[1], s) : "");
      spot("ts-spot-s3", "raise" + (raised ? " on" : ""), "3RD", raised ? s : 0, false, raised ? pill(paid.s[2], s) : "");
      if (open) chipDrag($("screen-threeshot"), { map: { '.ts-spot[data-spot="s1"]': "s1", '.ts-spot[data-spot="five"]': "five" },
        live: function () { return phase === "betting"; }, chip: function () { return selectedChip; },
        get: function (k) { return bets[k]; }, set: function (k, v) { bets[k] = v; },
        add: function (k, v) { var q = selectedChip; selectedChip = v; try { place(k); } finally { selectedChip = q; } },
        rack: "#ts-rack, #ts-actions", say: setMessage, done: renderAll });
    }
    function renderRack() {
      var host = $("ts-rack"); host.innerHTML = "";
      if (phase !== "betting") return;
      var rack = document.createElement("div");
      rack.className = "uth-rack";
      selectedChip = clampChip(selectedChip, bankroll);
      chipsFor(bankroll, Shell.limits()).forEach(function (v) {
        var cb = document.createElement("button");
        cb.className = "chip-btn" + (selectedChip === v ? " sel" : "");
        cb.innerHTML = chipFace(v);
        cb.addEventListener("click", function () { selectedChip = v; renderRack(); });
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
    function renderActions() {
      var a = $("ts-actions"); a.innerHTML = "";
      if (phase === "betting") {
        a.appendChild(actionBtn("CLEAR", DIM, clearBets, total() <= 0));
        a.appendChild(actionBtn(bets.s1 > 0 ? "DEAL · $" + fmt(total()) : "DEAL", GOLD, deal, bets.s1 <= 0 || total() > bankroll));
      } else if (phase === "decide") {
        var cost = bets.s1 * 2;
        a.appendChild(actionBtn("FOLD", DIM, fold));
        a.appendChild(actionBtn(bankroll >= cost ? "RAISE · $" + fmt(cost) : "RAISE · NEED $" + fmt(cost), GOLD, raise, bankroll < cost));
      } else if (phase === "result") {
        a.appendChild(actionBtn("NEW HAND", GOLD, nextHand));
      }
    }
    function renderFiveLine() {
      var el = $("ts-fiveline");
      el.className = "pg-bonusline";
      if (phase !== "result" || !paid.fiveBet) { el.textContent = ""; return; }
      if (fiveHit) el.textContent = "5 SHOT · " + (fiveHit.key === "royal" ? "ROYAL FLUSH" : fiveHit.name) + " · " + signed(paid.five);
      else { el.textContent = "5 SHOT · NOTHING THIS TIME"; el.classList.add("miss"); }
    }
    // what each bet pays, on the felt the whole time; the lines that hit light up
    (function () {
      TSE.SHOT.forEach(function (b) {
        var d = document.createElement("div"); d.setAttribute("data-k", b.key);
        d.innerHTML = "<span>" + b.name + "</span><b>" + b.pays + "</b>";
        $("ts-pays-rows").appendChild(d);
      });
      TSE.FIVE.forEach(function (b) {
        var d = document.createElement("div"); d.setAttribute("data-k", b.key);
        d.innerHTML = "<span>" + b.name + "</span><b>" + fmt(b.pays) + "</b>";
        $("ts-five-rows").appendChild(d);
      });
    })();
    function renderPays() {
      var lit = {};
      if (raised) hits.forEach(function (h, i) { if (h && i < shown) lit[h.key] = 1; });
      [].forEach.call($("ts-pays-rows").children, function (d) { d.classList.toggle("hit", !!lit[d.getAttribute("data-k")]); });
      var fk = phase === "result" && paid.fiveBet && fiveHit ? fiveHit.key : null;
      [].forEach.call($("ts-five-rows").children, function (d) { d.classList.toggle("hit", d.getAttribute("data-k") === fk); });
      $("ts-pays").classList.toggle("live", (phase === "betting" ? bets.s1 : bets.s1) > 0);
      $("ts-fivepays").classList.toggle("live", (phase === "betting" ? bets.five : paid.fiveBet) > 0);
    }
    function renderRebuy() {
      $("ts-rebuy").classList.toggle("on", Shell.mode === "testing" && bankroll < 25 && phase === "betting");
    }
    function renderAll() { renderTop(); renderCards(); renderSpots(); renderRack(); renderActions(); renderFiveLine(); renderPays(); renderRebuy(); }

    // ---------- betting ----------
    function place(which) {
      if (phase !== "betting") return;
      var lim = Shell.limits(), isSide = which === "five";
      var want = Math.min(selectedChip, bankroll - total());
      if (want <= 0) { setMessage("That's all your chips"); return; }
      var ok = allow(lim, want, bets[which], isSide);
      if (ok <= 0) { setMessage(refusal(lim, isSide)); return; }
      bets[which] += ok;
      setMessage(bets.s1 > 0 ? "Deal when you're ready · keep " + fmt(bets.s1 * 2) + " to raise" : "Put down the 1st Shot too");
      renderAll();
    }
    function clearBets() {
      if (phase !== "betting") return;
      bets = { s1: 0, five: 0 };
      setMessage("Place your bet");
      renderAll();
    }

    // ---------- the hand ----------
    function deal() {
      if (phase !== "betting" || bets.s1 <= 0 || total() > bankroll) return;
      bankroll -= total(); syncWallet();
      last = { s1: bets.s1, five: bets.five };
      var d = buildShoe(1);
      hole = []; shots = []; shown = 0; hits = [null, null, null]; fiveHit = null; raised = false; folded = false;
      paid = { s: [0, 0, 0], five: 0, fiveBet: bets.five, stake: bets.s1 };
      var h = [d.pop(), d.pop()], sh = [d.pop(), d.pop(), d.pop()];
      phase = "dealing";
      setMessage("Dealing…");
      renderAll();
      [0, 1].forEach(function (k) { t(80 + k * 140, function () { hole.push(h[k]); renderCards(); }); });
      [0, 1, 2].forEach(function (k) { t(360 + k * 120, function () { shots.push(sh[k]); renderCards(); }); });
      t(360 + 3 * 120 + 120, function () {
        phase = "decide";
        setMessage("Raise to play all three shots — or fold");
        renderAll();
      });
    }
    function flipNext(then) {
      var el = $("ts-shots").children[shown] && $("ts-shots").children[shown].querySelector(".card");
      if (el) el.classList.add("up");
      t(420, function () { shown++; renderAll(); then(); });
    }
    function revealAll(i) {
      if (shown >= 3) return t(raised ? 450 : 350, settle);
      flipNext(function () {
        if (raised && hits[shown - 1]) setMessage(ORD[shown - 1] + " shot · " + hits[shown - 1].name + "!", "win");
        t(raised ? 380 : 140, function () { revealAll(); });
      });
    }
    function raise() {
      var cost = bets.s1 * 2;
      if (phase !== "decide" || bankroll < cost) return;
      bankroll -= cost; syncWallet();
      raised = true;
      for (var i = 0; i < 3; i++) hits[i] = TSE.three([hole[0], hole[1], shots[i]]);
      phase = "reveal";
      setMessage("Three shots…");
      renderAll();
      t(300, revealAll);
    }
    function fold() {
      if (phase !== "decide") return;
      folded = true;
      phase = "reveal";
      setMessage(paid.fiveBet ? "Folded — the 5 Shot still plays" : "Folded");
      renderAll();
      t(300, revealAll);
    }
    function settle() {
      var back = 0, s = bets.s1;
      if (raised) hits.forEach(function (h, i) {
        if (h) { paid.s[i] = s * h.pays; back += s * (h.pays + 1); } else paid.s[i] = -s;
      });
      else paid.s[0] = -s;
      if (paid.fiveBet) {
        fiveHit = TSE.five([hole[0], hole[1], shots[0], shots[1], shots[2]]);
        if (fiveHit) { paid.five = paid.fiveBet * fiveHit.pays; back += paid.fiveBet * (fiveHit.pays + 1); }
        else paid.five = -paid.fiveBet;
      }
      if (back > 0) { bankroll += back; syncWallet(); }
      var n = net(), won = hits.filter(function (h) { return h; }).length;
      var msg = !raised ? (paid.five > 0 ? "Folded · the 5 Shot paid " + signed(paid.five) : "Folded · the 1st Shot's gone")
        : won === 0 ? "No pair on any shot"
        : (won === 3 ? "All three shots hit! " : won + " of 3 shots hit · ") + (n ? signed(n) : "even");
      phase = "result";
      if (Shell.mode === "campaign" && bankroll >= Shell.getGoal()) msg = "🏆 GOAL REACHED!";
      setMessage(msg, n > 0 ? "win" : n < 0 ? "lose" : "");
      renderAll();
      if (Shell.mode === "campaign" && bankroll >= Shell.getGoal()) Shell.gate(refresh);
    }
    function nextHand() {
      if (phase !== "result") return;
      clearTimers();
      phase = "betting";
      hole = []; shots = []; shown = 0; hits = [null, null, null]; fiveHit = null; raised = false; folded = false;
      paid = { s: [0, 0, 0], five: 0, fiveBet: 0, stake: 0 };
      // the same bets go back down, as far as the purse and the room allow
      var lim = Shell.limits(), left = bankroll;
      bets.s1 = Math.min(allow(lim, last.s1, 0, false), left); left -= bets.s1;
      bets.five = bets.s1 > 0 ? Math.min(allow(lim, last.five, 0, true), left) : 0;
      setMessage(bets.s1 > 0 ? "Same bet — deal, or change it" : "Place your bet");
      renderAll();
      Shell.gate(refresh);
    }
    function refresh() {
      if (Shell.mode === "campaign") bankroll = Shell.getBankroll();
      if (phase === "betting" && total() > bankroll) bets = { s1: 0, five: 0 };
      renderAll();
    }
    function enter() {
      clearTimers();
      phase = "betting";
      hole = []; shots = []; shown = 0; hits = [null, null, null]; fiveHit = null; raised = false; folded = false;
      paid = { s: [0, 0, 0], five: 0, fiveBet: 0, stake: 0 };
      bets = { s1: 0, five: 0 }; last = { s1: 0, five: 0 };
      bankroll = Shell.mode === "campaign" ? Shell.getBankroll() : FREEPLAY.buyIn;
      setMessage("Place your bet");
      renderAll();
      Shell.gate(refresh);
    }
    $("ts-buyin").addEventListener("click", function () {
      if (phase !== "betting" || bankroll >= 25) return;
      freshStack(function (v) { bankroll = v; setMessage("Place your bet"); renderAll(); });
    });
    (function () { var from = document.querySelector("#screen-miniuth .mu-bg"); if (from) $("ts-bg").src = from.src; })();
    $("ts-back").addEventListener("click", function () {
      if (phase !== "betting" && phase !== "result") { setMessage("Finish the hand first", "lose"); return; }
      Shell.show("poker");
    });
    $("pk-threeshot").addEventListener("click", function () { ThreeShot.enter(); Shell.show("threeshot"); });
    (function () {
      var show = Shell.show;
      Shell.show = function (name) {
        if (name === "poker") $("pk-threeshot").style.display = typeof TT !== "undefined" && TT.on && !(TT.hasGame && TT.hasGame("3 Shot Poker")) ? "none" : "";
        return show.apply(this, arguments);
      };
    })();
    return {
      busy: function () { return phase !== "betting" && phase !== "result"; },
      credit: function (x) { bankroll += x; syncWallet(); if (phase === "betting" && total() > bankroll) bets = { s1: 0, five: 0 }; renderAll(); },
      seatOk: function () { return phase === "betting" || phase === "result"; },
      cash: function () { return bankroll; },
      enter: enter
    };
  })();
