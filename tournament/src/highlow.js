  /* ============================================================
     HIGH OR LOW — one deck. Nine cards go out face up as nine piles.
     Pick a pile and call the next card HIGHER or LOWER than it, then it flips.
       Right: the card goes on that pile.
       Wrong: that pile is dead. Same rank is wrong. Nothing else is lost.
     Lose every pile and the bet is gone.
     The pay is set by how many calls you've got right: nothing until 12, then
     25% of the bet, and every right call after that multiplies it by 1.118
     (your bet back at 25 right, about 8x for all 43). Cash out any time once
     you're paid; the deck running out pays what you've reached.
     TIE: a side bet you can drop on before any flip. If the card matches the
     pile's rank it pays the odds of that, from the cards left (about 97% back);
     either way it comes off after that one flip. It never touches your piles or
     the main bet.
     Played as well as it can be (the likeliest pile every time, and knowing
     when to stop on the last pile) it returns about 98% of what's bet.
     Campaign and Play Testing; the tournament hides it until its server lists it.
     ============================================================ */
  var HL = (function () {
    var START = 12, BASE = 0.25, GROW = 1.118;
    function val(c) { return c.rank === "A" ? 14 : c.rank === "K" ? 13 : c.rank === "Q" ? 12 : c.rank === "J" ? 11 : parseInt(c.rank, 10); }
    /** What [n] right calls pay, times the bet. */
    function pay(n) { return n < START ? 0 : Math.floor(100 * BASE * Math.pow(GROW, n - START) + 1e-9) / 100; }
    /** The tie bet on a pile showing [top]: what it pays to 1, from the cards left (0 = can't happen). */
    function tiePays(top, deck) {
      var t = val(top), same = 0;
      deck.forEach(function (c) { if (val(c) === t) same++; });
      if (!same || !deck.length) return 0;
      return Math.max(1, Math.floor(0.97 * deck.length / same - 1));
    }
    /** The odds of each call on a pile showing [top], from what's left in [deck]. */
    function odds(top, deck) {
      var t = val(top), hi = 0, lo = 0;
      deck.forEach(function (c) { var v = val(c); if (v > t) hi++; else if (v < t) lo++; });
      var n = deck.length || 1;
      return { hi: hi / n, lo: lo / n };
    }
    return { val: val, pay: pay, odds: odds, tiePays: tiePays, START: START, GROW: GROW };
  })();
  window.HL = HL;

  var HighLow = (function () {
    var GOLD = "#FFD24D", DIM = "rgba(245,241,232,.7)", BLUE = "#35C8FF";
    var bankroll = FREEPLAY.buyIn, selectedChip = 25;
    // betting | play | flip | result
    var phase = "betting";
    var bet = 0, lastBet = 0, stake = 0, pot = 0, calls = 0;
    var tie = 0, tieNet = 0;
    var deck = [], piles = [], sel = -1, flash = null, outcome = null, net = 0;
    var timers = [];
    function t(ms, fn) { timers.push(setTimeout(fn, ms)); }
    function clearTimers() { timers.forEach(clearTimeout); timers = []; }
    function syncWallet() {
      if (Shell.mode !== "campaign") return;
      Shell.setBankroll(bankroll);
      Shell.demoteIfBroke();
    }
    function alive() { return piles.filter(function (p) { return !p.dead; }).length; }
    function setMessage(text, tone) {
      var m = $("hl-message");
      m.textContent = text;
      m.className = "uth-message" + (tone ? " " + tone : "");
    }
    function money(v) { var f = Math.floor(v); return f >= 1e6 && typeof shortAmt === "function" ? shortAmt(f) : fmt(f); }

    // ---------- render ----------
    function renderTop() {
      $("hl-bankroll").textContent = fmt(bankroll);
      $("hl-risk").textContent = fmt(phase === "play" || phase === "flip" ? stake + tie : 0);
      if (window.purseWin) {
        var purse = document.querySelector("#screen-highlow .purse");
        if (phase === "result" && outcome) purseWin.net(purse, net, true);
        else purseWin.clear(purse);
      }
      var live = phase === "play" || phase === "flip";
      $("hl-pot").textContent = live || phase === "result" ? "$" + money(pot) : "—";
      var togo = HL.START - calls;
      $("hl-potx").textContent = !live ? (phase === "result" ? calls + " RIGHT" : "")
        : togo > 0 ? togo + " MORE RIGHT TO GET PAID" : "×" + HL.pay(calls).toFixed(2) + " · NEXT ×" + HL.pay(calls + 1).toFixed(2);
      if (live && tieNet) $("hl-potx").textContent += " · TIE " + (tieNet > 0 ? "+" : "−") + money(Math.abs(tieNet));
      $("hl-alive").textContent = piles.length ? alive() : 9;
      var d = $("hl-deck"), left = piles.length ? deck.length : 43;
      d.innerHTML = '<div class="stack">' + (left > 2 ? '<div class="card"><div class="inner"><div class="face back"></div></div></div>' : "") +
        (left > 1 ? '<div class="card"><div class="inner"><div class="face back"></div></div></div>' : "") +
        (left > 0 ? '<div class="card"><div class="inner"><div class="face back"></div></div></div>' : "") + '</div><div class="n"><i>' + left + "</i> LEFT</div>";
    }
    function renderGrid() {
      var g = $("hl-grid"); g.innerHTML = "";
      for (var i = 0; i < 9; i++) (function (k) {
        var p = piles[k], b = document.createElement("button");
        b.type = "button"; b.className = "hl-pile";
        if (!p) { b.innerHTML = '<div class="depth" style="left:0;top:0;opacity:.35"></div>'; b.disabled = true; g.appendChild(b); return; }
        if (p.dead) b.classList.add("dead");
        if (k === sel && !p.dead) b.classList.add("sel");
        if (flash && flash.pile === k) b.classList.add(flash.ok ? "hit" : "miss");
        if (p.cards.length > 1) { var dp = document.createElement("div"); dp.className = "depth"; b.appendChild(dp); }
        var top = p.cards[p.cards.length - 1];
        var el = cardEl(top, !(flash && flash.pile === k && flash.down));
        b.appendChild(el);
        if (p.cards.length > 1) { var c = document.createElement("span"); c.className = "cnt"; c.textContent = p.cards.length; b.appendChild(c); }
        b.addEventListener("click", function () { choose(k); });
        g.appendChild(b);
      })(i);
    }
    function renderCalls() {
      var h = $("hl-calls"); h.innerHTML = "";
      if (phase === "betting" || phase === "result" || !piles.length) {
        var s = document.createElement("div"); s.className = "hint";
        s.textContent = phase === "result" ? "" : "PICK A PILE · CALL THE NEXT CARD · SAME RANK IS WRONG";
        h.appendChild(s); return;
      }
      var p = piles[sel], o = p && !p.dead ? HL.odds(p.cards[p.cards.length - 1], deck) : null;
      [["hi", "HIGHER", o && o.hi], ["lo", "LOWER", o && o.lo]].forEach(function (x) {
        var b = document.createElement("button");
        b.type = "button";
        b.className = "hl-call " + x[0];
        b.disabled = phase !== "play" || !o || !x[2];
        b.innerHTML = "<b>" + (x[0] === "hi" ? "▲ " : "▼ ") + x[1] + "</b><i>" + (!o ? "PICK A PILE" : x[2] ? Math.round(x[2] * 100) + "% CHANCE" : "CAN'T BE") + "</i>";
        b.addEventListener("click", function () { call(x[0]); });
        h.appendChild(b);
      });
    }
    function renderSpot() {
      var host = $("hl-spot"); host.innerHTML = "";
      var open = phase === "betting";
      var pp = phase === "play" && sel >= 0 && piles[sel] && !piles[sel].dead ? piles[sel] : null;
      var tp = pp ? HL.tiePays(pp.cards[pp.cards.length - 1], deck) : 0;
      var tieCap = pp ? (tp ? "TIE · PAYS " + tp + ":1" : "TIE · CAN'T HIT") : "TIE · NEXT FLIP";
      [["bet", "BET", phase === "betting" ? bet : stake], ["tie", tieCap, tie]].forEach(function (x) {
        var v = x[2], b = document.createElement("button");
        b.type = "button"; b.className = "hl-spot" + (x[0] === "tie" ? " tie" : ""); b.id = "hl-" + x[0] + "spot";
        b.disabled = x[0] === "tie" ? !(open || phase === "play") : !open;
        b.innerHTML = '<span class="ring">' + (v > 0 ? '<span class="placed">' + chipHtml(v, 50) + "</span>" : '<span class="empty">TAP</span>') +
          '</span><span class="cap">' + x[1] + "</span>";
        b.addEventListener("click", function () { place(x[0]); });
        host.appendChild(b);
      });
      if ((open || phase === "play") && typeof chipDrag === "function") chipDrag(host, { map: open ? { "#hl-betspot": "bet", "#hl-tiespot": "tie" } : { "#hl-tiespot": "tie" },
        live: function () { return phase === "betting" || phase === "play"; }, chip: function () { return selectedChip; },
        get: function (k) { return k === "tie" ? tie : bet; }, set: function (k, x) { if (k === "tie") tie = x; else bet = x; },
        add: function (k, v) { var q = selectedChip; selectedChip = v; try { place(k); } finally { selectedChip = q; } },
        rack: "#hl-rack, #hl-actions", say: setMessage, done: renderAll });
    }
    function renderRack() {
      var host = $("hl-rack"); host.innerHTML = "";
      if (phase !== "betting" && phase !== "play") return;      // in play the chips are for the tie bet
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
      var a = $("hl-actions"); a.innerHTML = "";
      if (phase === "betting") {
        a.appendChild(actionBtn("CLEAR", DIM, clearBet, bet + tie <= 0));
        a.appendChild(actionBtn(bet > 0 ? "DEAL · $" + fmt(bet) : "DEAL", GOLD, deal, bet <= 0 || bet > bankroll));
      } else if (phase === "play" || phase === "flip") {
        a.appendChild(actionBtn(pot > 0 ? "CASH OUT · $" + money(pot) : "PAID FROM " + HL.START + " RIGHT CALLS", GOLD, cashOut, phase !== "play" || !(pot > 0)));
      } else if (phase === "result") {
        a.appendChild(actionBtn("NEW GAME", GOLD, again));
      }
    }
    function renderRebuy() {
      $("hl-rebuy").classList.toggle("on", Shell.mode === "testing" && bankroll < 25 && phase === "betting");
    }
    function renderAll() { renderTop(); renderGrid(); renderCalls(); renderSpot(); renderRack(); renderActions(); renderRebuy(); }

    // ---------- betting ----------
    function place(which) {
      var side = which === "tie";
      if (phase !== "betting" && !(side && phase === "play")) return;
      var lim = Shell.limits(), want = Math.min(selectedChip, bankroll - (phase === "betting" ? bet : 0) - tie);
      if (phase === "play" && !side) return;
      if (want <= 0) { setMessage("That's all your chips"); return; }
      var ok = allow(lim, want, side ? tie : bet, side);
      if (ok <= 0) { setMessage(refusal(lim, side)); return; }
      if (side) tie += ok; else bet += ok;
      setMessage(side ? "Tie bet $" + fmt(tie) + " rides the next flip" : "Deal when you're ready");
      if (phase === "play") { renderTop(); renderSpot(); renderRack(); } else renderAll();
    }
    function clearBet() { if (phase !== "betting") return; bet = 0; tie = 0; setMessage("Place your bet"); renderAll(); }

    // ---------- the game ----------
    function deal() {
      if (phase !== "betting" || bet <= 0 || bet > bankroll) return;
      bankroll -= bet; syncWallet();
      stake = bet; lastBet = bet; pot = 0; calls = 0; net = 0; outcome = null; flash = null; tieNet = 0;
      deck = buildShoe(1);
      piles = [];
      phase = "flip";
      setMessage("Dealing the piles…");
      renderAll();
      for (var i = 0; i < 9; i++) (function (k) {
        t(90 + k * 110, function () { piles.push({ cards: [deck.pop()], dead: false }); renderGrid(); renderTop(); });
      })(i);
      t(90 + 9 * 110 + 200, function () {
        phase = "play"; sel = -1;
        setMessage("Pick a pile, then call the next card");
        renderAll();
      });
    }
    function choose(k) {
      if (phase !== "play" || !piles[k] || piles[k].dead) return;
      sel = k; flash = null;
      var o = HL.odds(piles[k].cards[piles[k].cards.length - 1], deck);
      setMessage("Higher: " + Math.round(o.hi * 100) + "% · Lower: " + Math.round(o.lo * 100) + "%");
      renderGrid(); renderCalls(); renderSpot();
    }
    function call(dir) {
      if (phase !== "play" || sel < 0 || !piles[sel] || piles[sel].dead || !deck.length) return;
      var p = piles[sel], top = p.cards[p.cards.length - 1], o = HL.odds(top, deck);
      if (!(dir === "hi" ? o.hi : o.lo)) return;
      // the tie bet goes down on this flip, at the odds as they stand
      var tieBet = tie > 0 && bankroll >= tie ? tie : 0, tp = tieBet ? HL.tiePays(top, deck) : 0;
      if (tieBet && !tp) tieBet = 0;      // can't hit on this pile: the chips stay on the spot for the next one
      if (tieBet) { bankroll -= tieBet; tie = 0; syncWallet(); }
      var c = deck.pop(), ok = dir === "hi" ? HL.val(c) > HL.val(top) : HL.val(c) < HL.val(top);
      var tieHit = tieBet && HL.val(c) === HL.val(top);
      p.cards.push(c);
      phase = "flip";
      flash = { pile: sel, ok: ok, down: true };
      setMessage("…");
      renderAll();
      t(60, function () {        // over it goes
        flash.down = false;
        var el = $("hl-grid").children[flash.pile].querySelector(".card");
        if (el) el.classList.add("up");
      });
      t(620, function () {
        if (tieBet) {
          if (tieHit) { bankroll += tieBet * (tp + 1); tieNet += tieBet * tp; syncWallet(); }
          else tieNet -= tieBet;
        }
        if (tieHit) setMessage("TIE! " + tp + " to 1 — +$" + fmt(tieBet * tp) + (alive() > 1 ? " · that pile's dead" : ""), "win");
        else if (ok) {
          calls++;
          pot = stake * HL.pay(calls);
          var togo = HL.START - calls;
          setMessage(togo > 0 ? "Right! " + togo + " more to get paid" : togo === 0 ? "Right! You're getting paid — 1× your bet" : "Right! Now ×" + HL.pay(calls).toFixed(2), "win");
        } else {
          p.dead = true;
          var left = alive();
          if (!tieHit) setMessage(left ? "Wrong — that pile's dead · " + left + " left" : "Wrong — that was your last pile", "lose");
        }
        if (!alive()) return finish("bust");
        if (!deck.length) return finish("clear");
        phase = "play";
        if (piles[sel].dead) sel = -1;
        renderAll();
      });
    }
    function finish(how) {
      outcome = how;
      var back = how === "bust" ? 0 : Math.floor(pot);
      if (back > 0) { bankroll += back; syncWallet(); }
      net = back - stake + tieNet;
      phase = "result"; sel = -1;
      var msg = how === "clear" ? "DECK CLEARED! " + calls + " right — ×" + HL.pay(calls).toFixed(2)
        : how === "bust" ? "Out of piles — the pot's gone"
        : net > 0 ? "Cashed out +$" + fmt(net) : net === 0 ? "Cashed out — you broke even" : "Cashed out $" + fmt(back);
      if (Shell.mode === "campaign" && bankroll >= Shell.getGoal()) msg = "🏆 GOAL REACHED!";
      setMessage(msg, net > 0 ? "win" : net < 0 ? "lose" : "");
      renderAll();
      if (Shell.mode === "campaign" && bankroll >= Shell.getGoal()) Shell.gate(refresh);
    }
    function cashOut() { if (phase !== "play" || !(pot > 0)) return; finish("cash"); }
    function again() {
      if (phase !== "result") return;
      clearTimers();
      phase = "betting"; piles = []; deck = []; sel = -1; flash = null; outcome = null; stake = 0; pot = 0; calls = 0; tie = 0; tieNet = 0;
      var lim = Shell.limits();
      bet = Math.min(allow(lim, lastBet, 0, false), bankroll);
      setMessage(bet > 0 ? "Same bet — deal, or change it" : "Place your bet");
      renderAll();
      Shell.gate(refresh);
    }
    function refresh() {
      if (Shell.mode === "campaign") bankroll = Shell.getBankroll();
      if (phase === "betting" && bet + tie > bankroll) { bet = 0; tie = 0; }
      renderAll();
    }
    function enter() {
      clearTimers();
      phase = "betting"; piles = []; deck = []; sel = -1; flash = null; outcome = null; stake = 0; pot = 0; calls = 0; bet = 0; lastBet = 0; tie = 0; tieNet = 0;
      bankroll = Shell.mode === "campaign" ? Shell.getBankroll() : FREEPLAY.buyIn;
      setMessage("Place your bet");
      renderAll();
      Shell.gate(refresh);
    }
    $("hl-buyin").addEventListener("click", function () {
      if (phase !== "betting" || bankroll >= 25) return;
      freshStack(function (v) { bankroll = v; setMessage("Place your bet"); renderAll(); });
    });
    (function () { var from = document.querySelector("#screen-miniuth .mu-bg"); if (from) $("hl-bg").src = from.src; })();
    $("hl-back").addEventListener("click", function () {
      // a run in progress stays put: cash out (or save your seat) first
      if (phase === "play" || phase === "flip") { setMessage(pot > 0 ? "Cash out first — or save your seat" : "Finish the game — or save your seat", "lose"); return; }
      Shell.show("othermenu");
    });
    $("om-highlow").addEventListener("click", function () { HighLow.enter(); Shell.show("highlow"); });
    (function () {
      var show = Shell.show;
      Shell.show = function (name) {
        if (name === "othermenu") $("om-highlow").style.display = typeof TT !== "undefined" && TT.on && !(TT.hasGame && TT.hasGame("High or Low")) ? "none" : "";
        return show.apply(this, arguments);
      };
    })();
    return {
      busy: function () { return phase === "play" || phase === "flip"; },
      credit: function (x) { bankroll += x; syncWallet(); if (phase === "betting" && bet > bankroll) bet = 0; renderAll(); },
      seatOk: function () { return phase !== "flip"; },
      cash: function () { return bankroll; },
      enter: enter
    };
  })();
