  /* ============================================================
     BREAKOUT BLACKJACK — six decks, the dealer stands on every 17.
     Bet on the PLAYER or on the DEALER.
       PLAYER: plain blackjack. Hit, stand, double any two, split up to four
       hands (split aces take one card). Blackjack pays 3:2. No insurance.
       DEALER: the player's hand isn't yours to play; it draws to 17 just like
       the dealer's, and then the dealer plays. The bet pays 1:1 when the dealer
       beats the player, or stays up while the player busts; dealer blackjack
       pays 3:2. Both bust, both blackjack, or the player busts against a dealer
       17: push. Anything else (a tie, a dealer bust) loses.
     Side bets: BREAKOUT (both bust, paid by how many cards it took) and SUPER
     TIE (dealer bet only), PAIR UP (either first two cards a pair, any bet).
     Official rules (Nevada Gaming Control Board). As written they're hard on
     the dealer bet: about 13% house edge; Breakout ~18%, Super Tie ~18%,
     Pair Up ~16% (6 decks, from a 4M-hand run).
     Campaign and Play Testing; the tournament hides it until its server lists it.
     ============================================================ */
  var BOE = (function () {
    var DECKS = 6;
    var BREAKOUT = [{ n: 12, name: "12+ CARDS", pays: 250 }, { n: 11, name: "11 CARDS", pays: 150 }, { n: 10, name: "10 CARDS", pays: 100 },
                    { n: 9, name: "9 CARDS", pays: 30 }, { n: 8, name: "8 CARDS", pays: 15 }, { n: 6, name: "6–7 CARDS", pays: 5 }];
    var TIE = [{ key: "bj", name: "BLACKJACK", pays: 25 }, { key: "21", name: "21", pays: 15 }, { key: "20", name: "20", pays: 8 },
               { key: "17", name: "17–19", pays: 3 }, { key: "bust", name: "BOTH BUST", pays: 1 }];
    var PAIRUP = [{ key: "suited", name: "SUITED PAIR", pays: 8 }, { key: "pair", name: "ANY PAIR", pays: 4 }];
    function cv(r) { return r === "A" ? 1 : (r === "10" || r === "J" || r === "Q" || r === "K") ? 10 : parseInt(r, 10); }
    function total(cs) { var h = 0, a = false; cs.forEach(function (c) { h += cv(c.rank); if (c.rank === "A") a = true; }); return a && h + 10 <= 21 ? h + 10 : h; }
    function soft(cs) { var h = 0, a = false; cs.forEach(function (c) { h += cv(c.rank); if (c.rank === "A") a = true; }); return a && h + 10 <= 21; }
    function bj(cs) { return cs.length === 2 && total(cs) === 21; }
    function bust(cs) { return total(cs) > 21; }
    /** The dealer's rule, and the player's hand on a DEALER bet: draw to 17, stand on every 17. */
    function draws(cs) { return total(cs) < 17; }
    /** DEALER bet, what comes back for [b] staked (stake included). Dealer blackjack pays 3:2. */
    function dealerBet(p, d, b) {
      var pb = bj(p), db = bj(d), pt = total(p), dt = total(d);
      if (pb && db) return b;
      if (db) return Math.floor(b * 2.5);
      if (pb) return 0;
      if (pt > 21 && dt > 21) return b;
      if (pt > 21) return dt === 17 ? b : b * 2;
      if (dt > 21) return 0;
      return dt > pt ? b * 2 : 0;
    }
    /** PLAYER bet on one hand: plain blackjack. [natural] = an unsplit two-card 21. */
    function playerBet(p, d, stake, natural) {
      var db = bj(d), pt = total(p), dt = total(d), pb = natural && bj(p);
      if (pb && db) return stake;
      if (pb) return Math.floor(stake * 2.5);
      if (db) return 0;
      if (pt > 21) return 0;
      if (dt > 21 || pt > dt) return stake * 2;
      return pt === dt ? stake : 0;
    }
    function breakout(p, d) {
      if (!(bust(p) && bust(d))) return null;
      var n = p.length + d.length;
      for (var i = 0; i < BREAKOUT.length; i++) if (n >= BREAKOUT[i].n) return BREAKOUT[i];
      return null;
    }
    function superTie(p, d) {
      var pick = function (k) { for (var i = 0; i < TIE.length; i++) if (TIE[i].key === k) return TIE[i]; };
      if (bust(p) && bust(d)) return pick("bust");
      if (bust(p) || bust(d)) return null;
      var pb = bj(p), db = bj(d);
      if (pb || db) return pb && db ? pick("bj") : null;
      var pt = total(p), dt = total(d);
      if (pt !== dt) return null;
      return pick(pt === 21 ? "21" : pt === 20 ? "20" : "17");   // a tie under 17 can't happen: both draw to 17
    }
    function pairUp(p, d) {
      var s = function (cs) { return cs[0].rank === cs[1].rank ? (cs[0].suit === cs[1].suit ? 2 : 1) : 0; };
      var top = Math.max(s(p), s(d));
      return top === 2 ? PAIRUP[0] : top === 1 ? PAIRUP[1] : null;
    }
    return { DECKS: DECKS, BREAKOUT: BREAKOUT, TIE: TIE, PAIRUP: PAIRUP, total: total, soft: soft, bj: bj, bust: bust, draws: draws, cv: cv,
             dealerBet: dealerBet, playerBet: playerBet, breakout: breakout, superTie: superTie, pairUp: pairUp };
  })();
  window.BOE = BOE;

  var Breakout = (function () {
    var ORANGE = "#FF8A1F", GOLD = "#FFD24D", DIM = "rgba(245,241,232,.7)", BLUE = "#35C8FF";
    var shoe = [], RESHUFFLE_AT = 75;      // filled on the first deal: the deck helpers are not set up yet when this runs
    var bankroll = FREEPLAY.buyIn, selectedChip = 25;
    // betting | dealing | player | auto | dealer | result
    var phase = "betting";
    var KEYS = ["player", "dealer", "breakout", "tie", "pair"];
    function none() { return { player: 0, dealer: 0, breakout: 0, tie: 0, pair: 0 }; }
    var bets = none(), last = none(), side = "player";
    var hands = [], cur = 0, dealer = [], holeUp = false, first2 = [];
    var res = null;      // { main, breakout, tie, pair, hit: {...}, tags: [] }
    var timers = [];
    function t(ms, fn) { timers.push(setTimeout(fn, ms)); }
    function clearTimers() { timers.forEach(clearTimeout); timers = []; }
    function sum(o) { return KEYS.reduce(function (a, k) { return a + o[k]; }, 0); }
    function main() { return bets.player + bets.dealer; }
    function staked() { return hands.reduce(function (a, h) { return a + h.stake; }, 0); }
    function atRisk() {
      if (phase === "betting" || phase === "result") return 0;
      return (side === "player" ? staked() : bets.dealer) + bets.breakout + bets.tie + bets.pair;
    }
    function syncWallet() {
      if (Shell.mode !== "campaign") return;
      Shell.setBankroll(bankroll);
      Shell.demoteIfBroke();
    }
    function draw() { if (!shoe.length) shoe = buildShoe(BOE.DECKS); return shoe.pop(); }
    function setMessage(text, tone) {
      var m = $("bo-message");
      m.textContent = text;
      m.className = "uth-message" + (tone ? " " + tone : "");
    }
    function money(v) { var a = Math.abs(v); return a >= 100000 && typeof shortAmt === "function" ? shortAmt(a) : fmt(a); }
    function signed(v) { return (v > 0 ? "+$" : v < 0 ? "−$" : "$") + money(v); }

    // ---------- render ----------
    function renderTop() {
      $("bo-bankroll").textContent = fmt(bankroll);
      $("bo-risk").textContent = fmt(atRisk());
      if (window.purseWin) {
        var purse = document.querySelector("#screen-breakout .purse");
        if (phase === "result" && res) purseWin.net(purse, res.main + res.breakout + res.tie + res.pair, true);
        else purseWin.clear(purse);
      }
    }
    function badge(id, cards, hidden) {
      var b = $(id);
      b.className = "bo-badge";
      if (!cards || !cards.length) { b.textContent = ""; return; }
      var shown = hidden ? cards.slice(0, 1) : cards, tt = BOE.total(shown);
      b.classList.add("on");
      if (!hidden && BOE.bj(cards)) { b.textContent = "BJ"; b.classList.add("bj"); }
      else if (tt > 21) { b.textContent = "BUST"; b.classList.add("bust"); }
      else b.textContent = BOE.soft(shown) && tt < 21 ? (tt - 10) + "/" + tt : tt;
    }
    function handEl(cards, faceUp, cls, tag, tagCls) {
      var h = document.createElement("div"), row = document.createElement("div");
      h.className = "bo-hand" + (cls ? " " + cls : "");
      row.className = "cards";
      if (!cards.length) { var s = document.createElement("div"); s.className = "slot"; row.appendChild(s); }
      cards.forEach(function (c, i) { row.appendChild(cardEl(c, faceUp(i))); });
      h.appendChild(row);
      if (tag != null) { var g = document.createElement("div"); g.className = "tag" + (tagCls ? " " + tagCls : ""); g.textContent = tag; h.appendChild(g); }
      return h;
    }
    /** Long hands overlap so every hand in a row fits across the felt. */
    function squeeze(row) {
      var hs = row.querySelectorAll(".bo-hand"), w = row.clientWidth;
      if (!hs.length || !w) return;
      var room = (w - 14 * (hs.length - 1)) / hs.length - 8;
      [].forEach.call(hs, function (h) {
        var cs = h.querySelectorAll(".card"), n = cs.length;
        if (n < 2) return;
        var cw = cs[0].offsetWidth || 50, need = n * cw + (n - 1) * 4;
        h.style.setProperty("--ov", need <= room ? "4px" : Math.max(-cw * 0.72, (room - cw) / (n - 1) - cw) + "px");
      });
    }
    function renderCards() {
      var d = $("bo-dealer"), p = $("bo-player");
      d.innerHTML = ""; p.innerHTML = "";
      d.appendChild(handEl(dealer, function (i) { return i !== 1 || holeUp; }));
      badge("bo-dbadge", dealer, !holeUp);
      p.classList.toggle("many", hands.length > 2);
      if (!hands.length) p.appendChild(handEl([], null));
      hands.forEach(function (h, k) {
        var tag = null, tc = "";
        if (hands.length > 1) tag = BOE.total(h.cards) > 21 ? "BUST" : String(BOE.total(h.cards));
        if (phase === "result" && res && res.tags[k]) { tag = res.tags[k].text; tc = res.tags[k].cls; }
        else if (hands.length > 1) tag += " · $" + money(h.stake);
        p.appendChild(handEl(h.cards, function () { return true; }, phase === "player" && k === cur && hands.length > 1 ? "act" : "", tag, tc));
      });
      badge("bo-pbadge", hands.length === 1 ? hands[0].cards : hands.length > 1 && phase === "player" ? hands[cur].cards : null, false);
      $("bo-pname").textContent = phase === "betting" ? (bets.dealer > 0 ? "PLAYER · DRAWS LIKE THE DEALER" : "PLAYER")
        : side === "dealer" ? "PLAYER · DRAWS LIKE THE DEALER" : "YOUR HAND";
      squeeze(d); squeeze(p);
    }
    function pill(net, staked) {
      if (phase !== "result" || !(staked > 0)) return "";
      return '<span class="dj-pill ' + (net > 0 ? "w" : net < 0 ? "l" : "p") + '"><span class="amt">' + (net > 0 ? "+" + money(net) : net < 0 ? "−" + money(net) : "PUSH") + "</span></span>";
    }
    var CAPS = { player: "PLAYER", dealer: "DEALER", breakout: "BREAKOUT", tie: "SUPER TIE", pair: "PAIR UP" };
    function renderSpots() {
      var open = phase === "betting", onDealer = open ? bets.player <= 0 : side === "dealer";
      KEYS.forEach(function (k) {
        var host = $("bo-spot-" + k); host.innerHTML = "";
        var isMain = k === "player" || k === "dealer", off = (k === "breakout" || k === "tie") && !onDealer;
        var v = k === "player" && !open ? (side === "player" ? staked() : 0) : bets[k];
        var b = document.createElement("button");
        b.type = "button"; b.className = "bo-spot" + (isMain ? " main" : "") + (off ? " off" : ""); b.disabled = !open;
        b.setAttribute("data-spot", k);
        var net = res ? (k === "player" || k === "dealer" ? (side === k ? res.main : 0) : res[k]) : 0;
        b.innerHTML = '<span class="ring">' + (v > 0 ? '<span class="placed">' + chipHtml(v, isMain ? 46 : 38) + "</span>" : '<span class="empty">TAP</span>') +
          '</span><span class="cap">' + CAPS[k] + "</span>" + pill(net, v);
        b.addEventListener("click", function () { place(k); });
        host.appendChild(b);
      });
      $("bo-brpays").classList.toggle("off", !onDealer);
      if (open) chipDrag($("screen-breakout"), {
        map: { '.bo-spot[data-spot="player"]': "player", '.bo-spot[data-spot="dealer"]': "dealer", '.bo-spot[data-spot="breakout"]': "breakout",
               '.bo-spot[data-spot="tie"]': "tie", '.bo-spot[data-spot="pair"]': "pair" },
        live: function () { return phase === "betting"; }, chip: function () { return selectedChip; },
        get: function (k) { return bets[k]; }, set: function (k, v) { bets[k] = v; },
        add: function (k, v) { var q = selectedChip; selectedChip = v; try { place(k); } finally { selectedChip = q; } },
        rack: "#bo-rack, #bo-actions", say: setMessage, done: function () { tidy(); renderAll(); } });
    }
    function renderRack() {
      var host = $("bo-rack"); host.innerHTML = "";
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
    function canDouble() { var h = hands[cur]; return phase === "player" && h && h.cards.length === 2 && !h.aces && bankroll >= h.stake; }
    function canSplit() {
      var h = hands[cur];
      return phase === "player" && h && h.cards.length === 2 && !h.aces && hands.length < 4 && bankroll >= h.stake &&
        BOE.cv(h.cards[0].rank) === BOE.cv(h.cards[1].rank);
    }
    function renderActions() {
      var a = $("bo-actions"); a.innerHTML = "";
      if (phase === "betting") {
        a.appendChild(actionBtn("CLEAR", DIM, clearBets, sum(bets) <= 0));
        a.appendChild(actionBtn(main() > 0 ? "DEAL · $" + fmt(sum(bets)) : "DEAL", GOLD, deal, main() <= 0 || sum(bets) > bankroll));
      } else if (phase === "player") {
        a.appendChild(actionBtn("HIT", "#57E06A", hit));
        a.appendChild(actionBtn("STAND", "#FF6B6B", stand));
        a.appendChild(actionBtn("DOUBLE", GOLD, dbl, !canDouble()));
        a.appendChild(actionBtn("SPLIT", BLUE, split, !canSplit()));
      } else if (phase === "result") {
        a.appendChild(actionBtn("NEW HAND", GOLD, nextHand));
      }
    }
    function renderSideLine() {
      var el = $("bo-sideline");
      el.className = "pg-bonusline";
      if (phase !== "result" || !res || !(bets.breakout || bets.tie || bets.pair)) { el.textContent = ""; return; }
      var bits = [];
      if (res.hit.breakout && bets.breakout) bits.push("BREAKOUT " + signed(res.breakout));
      if (res.hit.tie && bets.tie) bits.push("SUPER TIE " + signed(res.tie));
      if (res.hit.pair && bets.pair) bits.push("PAIR UP " + signed(res.pair));
      if (bits.length) el.textContent = bits.join("  ·  ");
      else { el.textContent = "SIDE BETS · NOTHING THIS TIME"; el.classList.add("miss"); }
    }
    (function () {
      function row(host, name, pays, key) {
        var d = document.createElement("div"); d.setAttribute("data-k", key);
        d.innerHTML = "<span>" + name + "</span><b>" + pays + "</b>"; $(host).appendChild(d);
      }
      BOE.BREAKOUT.slice().reverse().forEach(function (b) { row("bo-br-rows", b.name, b.pays, b.n); });
      BOE.TIE.forEach(function (b) { row("bo-tie-rows", b.name, b.pays, b.key); });
      BOE.PAIRUP.forEach(function (b) { row("bo-pair-rows", b.name, b.pays, b.key); });
    })();
    function renderPays() {
      var h = phase === "result" && res ? res.hit : {};
      [["bo-br-rows", h.breakout && bets.breakout ? String(h.breakout.n) : null], ["bo-tie-rows", h.tie && bets.tie ? h.tie.key : null],
       ["bo-pair-rows", h.pair && bets.pair ? h.pair.key : null]].forEach(function (x) {
        [].forEach.call($(x[0]).children, function (d) { d.classList.toggle("hit", d.getAttribute("data-k") === x[1]); });
      });
      $("bo-brpays").classList.toggle("live", bets.breakout > 0);
      $("bo-tiepays").classList.toggle("live", bets.tie + bets.pair > 0);
    }
    function renderRebuy() {
      $("bo-rebuy").classList.toggle("on", Shell.mode === "testing" && bankroll < 25 && phase === "betting");
    }
    function renderAll() { renderTop(); renderCards(); renderSpots(); renderRack(); renderActions(); renderSideLine(); renderPays(); renderRebuy(); }

    // ---------- betting ----------
    var ONLY = "Breakout & Super Tie need a DEALER bet";
    /** A PLAYER bet can't carry the two dealer-only side bets: they come back off the felt. */
    function tidy() {
      if (bets.player > 0 && bets.dealer > 0) { bets.dealer += bets.player; bets.player = 0; }
      if (bets.player > 0 && (bets.breakout || bets.tie)) { bets.breakout = 0; bets.tie = 0; setMessage("Breakout & Super Tie need a DEALER bet"); }
    }
    function place(which) {
      if (phase !== "betting") return;
      var other = which === "player" ? "dealer" : which === "dealer" ? "player" : null;
      if (other && bets[other] > 0) {
        // one main bet: tapping the other circle moves it across
        bets[which] = bets[other]; bets[other] = 0;
        var msg = "Moved to " + CAPS[which];
        if (which === "player" && (bets.breakout || bets.tie)) { bets.breakout = 0; bets.tie = 0; msg += " · Breakout & Super Tie back"; }
        setMessage(msg);
        renderAll(); return;
      }
      if ((which === "breakout" || which === "tie") && bets.player > 0) { setMessage(ONLY); return; }
      var lim = Shell.limits(), isSide = !other;
      var want = Math.min(selectedChip, bankroll - sum(bets));
      if (want <= 0) { setMessage("That's all your chips"); return; }
      var ok = allow(lim, want, bets[which], isSide);
      if (ok <= 0) { setMessage(refusal(lim, isSide)); return; }
      bets[which] += ok;
      setMessage(main() > 0 ? "Deal when you're ready" : "Now back the PLAYER or the DEALER");
      renderAll();
    }
    function clearBets() {
      if (phase !== "betting") return;
      bets = none();
      setMessage("Place your bet");
      renderAll();
    }

    // ---------- the hand ----------
    function deal() {
      tidy();
      if (phase !== "betting" || main() <= 0 || sum(bets) > bankroll) return;
      var fresh = shoe.length < RESHUFFLE_AT, first = !shoe.length;
      if (fresh) shoe = buildShoe(BOE.DECKS);
      bankroll -= sum(bets); syncWallet();
      last = { player: bets.player, dealer: bets.dealer, breakout: bets.breakout, tie: bets.tie, pair: bets.pair };
      side = bets.dealer > 0 ? "dealer" : "player";
      hands = [{ cards: [], stake: main(), done: false, doubled: false, aces: false }]; cur = 0;
      dealer = []; holeUp = false; res = null;
      phase = "dealing";
      setMessage(fresh && !first ? "Fresh shoe · dealing…" : "Dealing…");
      renderAll();
      var seq = [hands[0].cards, dealer, hands[0].cards, dealer];
      seq.forEach(function (to, k) { t(100 + k * 170, function () { to.push(draw()); renderCards(); }); });
      t(100 + 4 * 170 + 150, afterDeal);
    }
    function afterDeal() {
      var p = hands[0].cards, pb = BOE.bj(p), db = BOE.bj(dealer);
      first2 = p.slice(0, 2);
      if (pb || db) {
        holeUp = true;
        setMessage(db && pb ? "Two blackjacks!" : db ? "Dealer blackjack!" : "Blackjack!", side === "dealer" ? (db && !pb ? "win" : "") : (pb && !db ? "win" : ""));
        renderAll();
        return t(800, finish);
      }
      if (side === "dealer") { phase = "auto"; setMessage("The player's hand draws to 17…"); renderAll(); return t(500, autoPlayer); }
      phase = "player";
      setMessage(BOE.total(p) === 21 ? "21" : "Hit, stand, double or split");
      renderAll();
    }
    // DEALER bet: the player's hand plays itself, by the dealer's rule
    function autoPlayer() {
      var p = hands[0].cards;
      if (BOE.draws(p)) { p.push(draw()); renderAll(); return t(460, autoPlayer); }
      setMessage(BOE.bust(p) ? "Player busts — now the dealer" : "Player stands on " + BOE.total(p) + " — now the dealer");
      t(500, dealerPlay);
    }
    function dealerPlay() {
      phase = "dealer";
      holeUp = true;
      renderAll();
      (function step() {
        if (BOE.draws(dealer)) { t(480, function () { dealer.push(draw()); renderAll(); step(); }); return; }
        t(420, finish);
      })();
    }
    function nextHandOrDealer() {
      while (cur < hands.length && hands[cur].done) cur++;
      if (cur < hands.length) {
        if (hands[cur].cards.length === 1) { hands[cur].cards.push(draw()); if (hands[cur].aces || BOE.total(hands[cur].cards) === 21) hands[cur].done = true; renderAll(); return t(300, nextHandOrDealer); }
        setMessage(hands.length > 1 ? "Hand " + (cur + 1) + " of " + hands.length : "Hit, stand, double or split");
        renderAll(); return;
      }
      cur = hands.length - 1;
      phase = "dealer";
      if (hands.every(function (h) { return BOE.bust(h.cards); })) { holeUp = true; renderAll(); return t(500, finish); }
      setMessage("Dealer's turn…");
      t(250, dealerPlay);
    }
    function hit() {
      if (phase !== "player") return;
      var h = hands[cur];
      h.cards.push(draw());
      var tt = BOE.total(h.cards);
      if (tt >= 21) { h.done = true; setMessage(tt > 21 ? "Bust" : "21", tt > 21 ? "lose" : ""); renderAll(); return t(380, nextHandOrDealer); }
      renderAll();
    }
    function stand() { if (phase !== "player") return; hands[cur].done = true; nextHandOrDealer(); }
    function dbl() {
      if (!canDouble()) return;
      var h = hands[cur];
      bankroll -= h.stake; syncWallet();
      h.stake *= 2; h.doubled = true;
      h.cards.push(draw()); h.done = true;
      setMessage("Doubled · " + (BOE.bust(h.cards) ? "bust" : BOE.total(h.cards)));
      renderAll();
      t(450, nextHandOrDealer);
    }
    function split() {
      if (!canSplit()) return;
      var h = hands[cur], aces = h.cards[0].rank === "A";
      bankroll -= h.stake; syncWallet();
      var nh = { cards: [h.cards.pop()], stake: h.stake, done: false, doubled: false, aces: aces };
      h.aces = aces;
      hands.splice(cur + 1, 0, nh);
      h.cards.push(draw());
      if (aces || BOE.total(h.cards) === 21) h.done = true;
      renderAll();
      t(300, nextHandOrDealer);
    }
    function finish() {
      holeUp = true;
      var back = 0, tags = [];
      res = { main: 0, breakout: 0, tie: 0, pair: 0, hit: {}, tags: tags };
      if (side === "player") {
        hands.forEach(function (h) {
          var natural = hands.length === 1 && !h.doubled && h.cards.length === 2;
          var b = BOE.playerBet(h.cards, dealer, h.stake, natural), n = b - h.stake;
          back += b; res.main += n;
          tags.push({ text: n > 0 ? "WIN +$" + money(n) : n < 0 ? (BOE.bust(h.cards) ? "BUST" : "LOSE") : "PUSH", cls: n > 0 ? "w" : n < 0 ? "l" : "" });
        });
      } else {
        var b = BOE.dealerBet(hands[0].cards, dealer, bets.dealer);
        back += b; res.main = b - bets.dealer;
      }
      function sideBet(key, hitv) {
        if (!(bets[key] > 0)) return;
        res.hit[key] = hitv;
        if (hitv) { res[key] = bets[key] * hitv.pays; back += bets[key] * (hitv.pays + 1); } else res[key] = -bets[key];
      }
      if (side === "dealer") {
        sideBet("breakout", BOE.breakout(hands[0].cards, dealer));
        sideBet("tie", BOE.superTie(hands[0].cards, dealer));
      }
      sideBet("pair", BOE.pairUp(first2, dealer.slice(0, 2)));
      if (back > 0) { bankroll += back; syncWallet(); }
      var net = res.main + res.breakout + res.tie + res.pair, msg;
      if (side === "dealer") {
        var p = hands[0].cards, pt = BOE.total(p), dt = BOE.total(dealer);
        msg = res.main > 0 ? (BOE.bj(dealer) ? "Dealer blackjack pays 3:2 · +$" : "Dealer wins · +$") + money(res.main)
          : res.main === 0 ? (BOE.bust(p) && BOE.bust(dealer) ? "Both bust — push" : BOE.bj(p) ? "Two blackjacks — push" : "Player busts on a dealer 17 — push")
          : BOE.bj(p) ? "Player blackjack — dealer loses" : dt > 21 ? "Dealer busts" : pt === dt ? "Tie — dealer bet loses" : "Player beats the dealer";
      } else {
        msg = hands.length > 1 ? (res.main > 0 ? "Up " + signed(res.main) + " on the split" : res.main < 0 ? "Down " + signed(res.main).replace("−", "") + " on the split" : "Even on the split")
          : res.main > 0 ? (BOE.bj(hands[0].cards) && !hands[0].doubled ? "Blackjack pays 3:2 · +$" : "You win · +$") + money(res.main)
          : res.main === 0 ? "Push" : BOE.bust(hands[0].cards) ? "Bust" : BOE.bj(dealer) ? "Dealer blackjack" : "Dealer wins";
      }
      phase = "result";
      if (Shell.mode === "campaign" && bankroll >= Shell.getGoal()) msg = "🏆 GOAL REACHED!";
      setMessage(msg, net > 0 ? "win" : net < 0 ? "lose" : "");
      renderAll();
      if (Shell.mode === "campaign" && bankroll >= Shell.getGoal()) Shell.gate(refresh);
    }
    function nextHand() {
      if (phase !== "result") return;
      clearTimers();
      phase = "betting";
      hands = []; dealer = []; holeUp = false; res = null; cur = 0;
      // the same bets go back down, as far as the purse and the room allow
      var lim = Shell.limits(), left = bankroll;
      bets = none();
      KEYS.forEach(function (k) {
        var isMain = k === "player" || k === "dealer";
        if (!isMain && last.player + last.dealer <= 0) return;
        bets[k] = Math.max(0, Math.min(allow(lim, last[k], 0, !isMain), left)); left -= bets[k];
      });
      if (!main()) bets = none();
      setMessage(main() > 0 ? "Same bet — deal, or change it" : "Place your bet");
      renderAll();
      Shell.gate(refresh);
    }
    function refresh() {
      if (Shell.mode === "campaign") bankroll = Shell.getBankroll();
      if (phase === "betting" && sum(bets) > bankroll) bets = none();
      renderAll();
    }
    function enter() {
      clearTimers();
      phase = "betting";
      hands = []; dealer = []; holeUp = false; res = null; cur = 0;
      bets = none(); last = none();
      bankroll = Shell.mode === "campaign" ? Shell.getBankroll() : FREEPLAY.buyIn;
      setMessage("Back the PLAYER or the DEALER");
      renderAll();
      Shell.gate(refresh);
    }
    $("bo-buyin").addEventListener("click", function () {
      if (phase !== "betting" || bankroll >= 25) return;
      freshStack(function (v) { bankroll = v; setMessage("Place your bet"); renderAll(); });
    });
    (function () { var from = document.querySelector("#screen-miniuth .mu-bg"); if (from) $("bo-bg").src = from.src; })();
    $("bo-back").addEventListener("click", function () {
      if (phase !== "betting" && phase !== "result") { setMessage("Finish the hand first", "lose"); return; }
      Shell.show("bjmenu");
    });
    $("bjm-breakout").addEventListener("click", function () { Breakout.enter(); Shell.show("breakout"); });
    (function () {
      var show = Shell.show;
      Shell.show = function (name) {
        if (name === "bjmenu") {
          var hide = typeof TT !== "undefined" && TT.on && !(TT.hasGame && TT.hasGame("Breakout Blackjack"));
          $("bjm-breakout").style.display = hide ? "none" : "";
          $("bjm-sub").textContent = hide ? "THREE WAYS TO PLAY IT" : "FOUR WAYS TO PLAY IT";
        }
        return show.apply(this, arguments);
      };
    })();
    return {
      busy: function () { return phase !== "betting" && phase !== "result"; },
      credit: function (x) { bankroll += x; syncWallet(); if (phase === "betting" && sum(bets) > bankroll) bets = none(); renderAll(); },
      seatOk: function () { return phase === "betting" || phase === "result"; },
      cash: function () { return bankroll; },
      enter: enter
    };
  })();
