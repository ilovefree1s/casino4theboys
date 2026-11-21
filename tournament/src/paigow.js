  /* ============================================================
     FACE UP PAI GOW POKER — seven cards each, from 52 and a joker.
     The dealer's seven are dealt face up and set first. You split yours into a
     two-card hand and a five-card hand; the five has to outrank the two.
     Win both and the bet pays even money, no commission; lose both and it's
     gone; one each is a push. A hand that ties exactly (a "copy") goes to the
     dealer. When the best the dealer can make is ace-high, every bet pushes.
     The joker is an ace, unless it finishes a straight or a flush.
     Two side bets: BONUS pays on the best hand in your seven cards, win or
     lose; ACE-HIGH pays when the dealer is ace-high.
     In Campaign, Play Testing and (once the server lists it) the tournament.
     ============================================================ */
  var PGE = (function () {
    var HIGH = 0, PAIR = 1, TWOPAIR = 2, TRIPS = 3, STRAIGHT = 4, FLUSH = 5, FULL = 6, QUADS = 7, SFLUSH = 8, FIVEACES = 9;
    var B = 15, B5 = B * B * B * B * B;
    var NAMES = { 14: "Ace", 13: "King", 12: "Queen", 11: "Jack", 10: "10" };
    var SHORT = { 14: "A", 13: "K", 12: "Q", 11: "J", 10: "10" };
    var SUITIX = { "♠": 0, "♥": 1, "♦": 2, "♣": 3 };
    function nm(v) { return NAMES[v] || String(v); }
    function sh(v) { return SHORT[v] || String(v); }
    function plural(v) { return v === 6 ? "Sixes" : nm(v) + "s"; }
    function val(r) { return r === "A" ? 14 : r === "K" ? 13 : r === "Q" ? 12 : r === "J" ? 11 : parseInt(r, 10); }
    /** A dealt card as {v, s}; the joker comes back as null. */
    function plain(c) { return c.joker ? null : { v: val(c.rank), s: SUITIX[c.suit] }; }
    function pack(cat, t) { return ((((cat * B + (t[0] || 0)) * B + (t[1] || 0)) * B + (t[2] || 0)) * B + (t[3] || 0)) * B + (t[4] || 0); }
    function catOf(sc) { return Math.floor(sc / B5); }
    function tie(sc, i) { return Math.floor(sc / Math.pow(B, 4 - i)) % B; }

    /** Five plain cards. A suit of -1 is the joker standing in as an ace: it never helps a flush. */
    function score5(cs) {
      var v = cs.map(function (c) { return c.v; }).sort(function (x, y) { return y - x; });
      var flush = cs[0].s >= 0 && cs.every(function (c) { return c.s === cs[0].s; });
      var cnt = {}, i;
      for (i = 0; i < 5; i++) cnt[v[i]] = (cnt[v[i]] || 0) + 1;
      var groups = Object.keys(cnt).map(Number).sort(function (a, b) { return cnt[b] - cnt[a] || b - a; });
      var top = 0;
      if (groups.length === 5) {
        if (v[0] - v[4] === 4) top = v[0];
        else if (v[0] === 14 && v[1] === 5 && v[4] === 2) top = 5;       // the wheel, A-2-3-4-5: the lowest straight
      }
      if (cnt[groups[0]] === 5) return pack(FIVEACES, [groups[0]]);
      if (top && flush) return pack(SFLUSH, [top]);
      if (cnt[groups[0]] === 4) return pack(QUADS, groups);
      if (cnt[groups[0]] === 3 && cnt[groups[1]] === 2) return pack(FULL, groups);
      if (flush) return pack(FLUSH, v);
      if (top) return pack(STRAIGHT, [top]);
      if (cnt[groups[0]] === 3) return pack(TRIPS, groups);
      if (cnt[groups[0]] === 2 && cnt[groups[1]] === 2) return pack(TWOPAIR, groups);
      if (cnt[groups[0]] === 2) return pack(PAIR, groups);
      return pack(HIGH, v);
    }
    var ALL = [];
    for (var s0 = 0; s0 < 4; s0++) for (var r0 = 2; r0 <= 14; r0++) ALL.push({ v: r0, s: s0 });
    /** Five dealt cards, joker and all. */
    function five(cards) {
      var fixed = [], joker = false;
      cards.forEach(function (c) { var p = plain(c); if (p) fixed.push(p); else joker = true; });
      if (!joker) return score5(fixed);
      var best = score5(fixed.concat([{ v: 14, s: -1 }]));                   // an ace, and nothing more
      for (var i = 0; i < ALL.length; i++) {
        var a = ALL[i], held = false;
        for (var k = 0; k < 4; k++) if (fixed[k].v === a.v && fixed[k].s === a.s) { held = true; break; }
        if (held) continue;
        var sc = score5(fixed.concat([a])), cat = catOf(sc);
        if ((cat === STRAIGHT || cat === FLUSH || cat === SFLUSH) && sc > best) best = sc;
      }
      return best;
    }
    /** The two-card hand: a pair or two high cards. The joker is an ace. */
    function two(cards) {
      var a = cards[0].joker ? 14 : val(cards[0].rank), b = cards[1].joker ? 14 : val(cards[1].rank);
      if (a === b) return 1000 + a;
      return Math.max(a, b) * B + Math.min(a, b);
    }
    /** The five has to outrank the two, or the hand is fouled. */
    function fouled(lo, hi) {
      var hc = catOf(hi);
      if (lo >= 1000) return hc === HIGH || (hc === PAIR && tie(hi, 0) < lo - 1000);
      if (hc !== HIGH) return false;
      return tie(hi, 0) * B + tie(hi, 1) < lo;
    }
    function name5(sc) {
      var c = catOf(sc), a = tie(sc, 0), b = tie(sc, 1);
      if (c === FIVEACES) return "Five Aces";
      if (c === SFLUSH) return a === 14 ? "Royal Flush" : "Straight Flush";
      if (c === QUADS) return "Four " + plural(a);
      if (c === FULL) return "Full House";
      if (c === FLUSH) return nm(a) + "-high Flush";
      if (c === STRAIGHT) return nm(a) + "-high Straight";
      if (c === TRIPS) return "Three " + plural(a);
      if (c === TWOPAIR) return sh(a) + "s & " + sh(b) + "s";
      if (c === PAIR) return "Pair of " + plural(a);
      return nm(a) + " High";
    }
    function name2(sc) {
      if (sc >= 1000) return "Pair of " + plural(sc - 1000);
      return sh(Math.floor(sc / B)) + "-" + sh(sc % B);
    }
    /** Every legal way to split seven cards: [{lo:[i,j], loS, hiS}]. */
    function splits(cards) {
      var out = [];
      for (var i = 0; i < 6; i++) for (var j = i + 1; j < 7; j++) {
        var lo = [cards[i], cards[j]], hi = cards.filter(function (c, k) { return k !== i && k !== j; });
        var loS = two(lo), hiS = five(hi);
        if (!fouled(loS, hiS)) out.push({ lo: [i, j], loS: loS, hiS: hiS });
      }
      return out;
    }
    function deck() {
      var d = buildShoe(1);
      d.push({ rank: "★", suit: "★", red: false, joker: true });
      for (var i = d.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)), t = d[i]; d[i] = d[j]; d[j] = t; }
      return d;
    }
    // ---- the house way ----
    // The dealer sets a hand the way that wins most: for every legal split, how often would each
    // half beat the half an opponent sets? Those odds come from a few thousand hands dealt and
    // set when the table is first opened, the first pass by a plain rule, the second by the odds
    // from the first.
    var DIST = null;
    function simple(cards) {
      var all = splits(cards), topCat = 0, best = null;
      all.forEach(function (s) { topCat = Math.max(topCat, catOf(s.hiS)); });
      all.forEach(function (s) {
        if (catOf(s.hiS) !== topCat) return;
        if (!best || s.loS > best.loS || (s.loS === best.loS && s.hiS > best.hiS)) best = s;
      });
      return best;
    }
    function below(arr, x) {      // the share of [arr] strictly under x
      var lo = 0, hi = arr.length;
      while (lo < hi) { var m = (lo + hi) >> 1; if (arr[m] < x) lo = m + 1; else hi = m; }
      return lo / arr.length;
    }
    /** A five-card hand without its kickers: a pair is its pair, two pair its two pairs, a high-card hand its top card.
        The house never gives up a better two-card hand to keep a kicker, so the odds are worked on this. */
    function coarse(sc) {
      var c = catOf(sc);
      return pack(c, [tie(sc, 0), c === TWOPAIR || c === FULL ? tie(sc, 1) : 0]);
    }
    function byOdds(cards, dist) {
      var all = splits(cards), best = null, bestEv = -9;
      all.forEach(function (s) {
        var pl = below(dist.lo, s.loS), ph = below(dist.hi, coarse(s.hiS));
        var ev = pl * ph - (1 - pl) * (1 - ph);
        // level odds: the stronger two-card hand first, then the stronger five
        var level = Math.abs(ev - bestEv) <= 1e-9 && best && (s.loS > best.loS || (s.loS === best.loS && s.hiS > best.hiS));
        if (ev > bestEv + 1e-9 || level) { bestEv = ev; best = s; }
      });
      return best;
    }
    function sample(setter, n) {
      var lo = [], hi = [];
      for (var i = 0; i < n; i++) { var s = setter(deck().slice(0, 7)); lo.push(s.loS); hi.push(coarse(s.hiS)); }
      var num = function (a, b) { return a - b; };
      return { lo: lo.sort(num), hi: hi.sort(num) };
    }
    function warm() {
      if (DIST) return;
      var first = sample(simple, 2500);
      DIST = sample(function (c) { return byOdds(c, first); }, 2500);
    }
    function houseWay(cards) { warm(); return byOdds(cards, DIST); }

    // ---- the bonus bet: the best hand in all seven ----
    var BONUS = [
      { key: "sf7", name: "7-card Straight Flush", pays: 8000 },
      { key: "sf7j", name: "7-card Straight Flush with Joker", pays: 1000 },
      { key: "aces", name: "Five Aces", pays: 400 },
      { key: "royal", name: "Royal Flush", pays: 150 },
      { key: "sf", name: "Straight Flush", pays: 50 },
      { key: "quads", name: "Four of a Kind", pays: 25 },
      { key: "full", name: "Full House", pays: 5 },
      { key: "flush", name: "Flush", pays: 4 },
      { key: "trips", name: "Three of a Kind", pays: 3 },
      { key: "straight", name: "Straight", pays: 2 }
    ];
    function sevenFlush(cards) {
      var ps = [], joker = false;
      cards.forEach(function (c) { var p = plain(c); if (p) ps.push(p); else joker = true; });
      if (!ps.every(function (p) { return p.s === ps[0].s; })) return null;
      var tryRun = function (vs) {
        vs = vs.slice().sort(function (a, b) { return a - b; });
        for (var i = 1; i < vs.length; i++) if (vs[i] === vs[i - 1]) return false;
        var span = vs[vs.length - 1] - vs[0];
        return joker ? span <= 6 : span === 6;
      };
      var vs = ps.map(function (p) { return p.v; });
      if (tryRun(vs) || tryRun(vs.map(function (v) { return v === 14 ? 1 : v; }))) return joker ? "sf7j" : "sf7";
      return null;
    }
    function best5(cards) {
      var best = 0;
      for (var i = 0; i < 6; i++) for (var j = i + 1; j < 7; j++)
        best = Math.max(best, five(cards.filter(function (c, k) { return k !== i && k !== j; })));
      return best;
    }
    /** What the bonus bet makes of seven cards: {key, name, pays} or null. */
    function bonus(cards) {
      var key = sevenFlush(cards);
      if (!key) {
        var sc = best5(cards), c = catOf(sc);
        key = c === FIVEACES ? "aces" : c === SFLUSH ? (tie(sc, 0) === 14 ? "royal" : "sf") : c === QUADS ? "quads" : c === FULL ? "full"
            : c === FLUSH ? "flush" : c === TRIPS ? "trips" : c === STRAIGHT ? "straight" : null;
      }
      if (!key) return null;
      for (var k = 0; k < BONUS.length; k++) if (BONUS[k].key === key) return BONUS[k];
      return null;
    }
    /** Your two hands against the dealer's: +1 both, -1 neither, 0 one each. Copies go to the dealer. */
    function settle(you, dealer) {
      var lo = you.loS > dealer.loS, hi = you.hiS > dealer.hiS;
      return { lo: lo, hi: hi, result: lo && hi ? 1 : !lo && !hi ? -1 : 0 };
    }
    /** Ace-high: the best five in the seven is no pair, no straight, no flush, with an ace (or the joker as one) on top. */
    function aceHigh(cards) {
      var sc = best5(cards);
      return { is: catOf(sc) === HIGH && tie(sc, 0) === 14, joker: cards.some(function (c) { return !!c.joker; }) };
    }
    var ACEPAYS = [{ key: "ah5", name: "DEALER ONLY", pays: 5 }, { key: "ah15", name: "WITH THE JOKER", pays: 15 }, { key: "ah40", name: "YOU BOTH", pays: 40 }];
    /** What the ace-high bet makes of the two hands: one of ACEPAYS, or null. */
    function aceBet(yours, dealers) {
      var d = aceHigh(dealers);
      if (!d.is) return null;
      return aceHigh(yours).is ? ACEPAYS[2] : d.joker ? ACEPAYS[1] : ACEPAYS[0];
    }
    return { five: five, two: two, fouled: fouled, name5: name5, name2: name2, splits: splits, deck: deck, houseWay: houseWay,
             warm: warm, bonus: bonus, BONUS: BONUS, settle: settle, catOf: catOf, best5: best5,
             aceHigh: aceHigh, aceBet: aceBet, ACEPAYS: ACEPAYS };
  })();
  window.PGE = PGE;   // exposed so the rules can be exercised directly

  var PaiGow = (function () {
    var RED = "#E24B5B", GOLD = "#FFD24D", LOSS = "#FF4D4D", DIM = "rgba(245,241,232,.7)";
    var bankroll = FREEPLAY.buyIn, selectedChip = 25;
    // betting | dealing | set | reveal | result
    var phase = "betting";
    var bets = { bet: 0, bonus: 0, ace: 0 }, last = { bet: 0, bonus: 0, ace: 0 };
    var mine = [], theirs = [], pick = [], mySet = null, dealerSet = null, dealerAce = false, outcome = null;
    var sideHit = null, aceHit = null, paid = { main: 0, bonus: 0, ace: 0, bonusBet: 0, aceBet: 0 };
    var timers = [];
    function t(ms, fn) { timers.push(setTimeout(fn, ms)); }
    function clearTimers() { timers.forEach(clearTimeout); timers = []; }
    function total() { return bets.bet + bets.bonus + bets.ace; }
    function syncWallet() {
      if (Shell.mode !== "campaign") return;
      Shell.setBankroll(bankroll);
      Shell.demoteIfBroke();
    }
    function order(c) { return c.joker ? 15 : (c.rank === "A" ? 14 : c.rank === "K" ? 13 : c.rank === "Q" ? 12 : c.rank === "J" ? 11 : parseInt(c.rank, 10)); }
    function sortHand(cs) { return cs.slice().sort(function (a, b) { return order(b) - order(a); }); }

    // ---------- render ----------
    function setMessage(text, tone) {
      var m = $("pg-message");
      m.textContent = text;
      m.className = "uth-message" + (tone ? " " + tone : "");
    }
    function slot(host) { var s = document.createElement("div"); s.className = "slot"; host.appendChild(s); }
    function gap(host) { var g = document.createElement("div"); g.className = "gapper"; host.appendChild(g); }
    function label(id, text, cls) {
      var l = $(id);
      l.className = "uth-label" + (cls ? " " + cls : (id === "pg-you-label" ? " gold" : ""));
      l.innerHTML = '<span class="nm">' + text + "</span>";
    }
    function cap(host, cls, head, text, tone) {
      var d = document.createElement("div"); d.className = cls + (tone ? " " + tone : "");
      d.innerHTML = "<b>" + head + "</b>" + text;
      host.appendChild(d);
    }
    function renderTop() {
      $("pg-bankroll").textContent = fmt(bankroll);
      $("pg-risk").textContent = fmt(phase === "betting" || phase === "result" ? 0 : total());
      // under the money: what the hand made or cost, cleared when the next one starts
      if (window.purseWin) {
        var purse = document.querySelector("#screen-paigow .purse");
        if (phase === "result" && outcome) purseWin.net(purse, paid.main + paid.bonus + paid.ace, true);
        else purseWin.clear(purse);
      }
    }
    /** A set hand in a row: the two, a gap, the five. */
    function rowSet(host, cards, set, marks) {
      var lo = set.lo, hi = [0, 1, 2, 3, 4, 5, 6].filter(function (k) { return lo.indexOf(k) < 0; });
      lo.forEach(function (k) { var el = cardEl(cards[k], true); if (marks) el.classList.add(marks.lo ? "won" : "lost"); host.appendChild(el); });
      gap(host);
      hi.forEach(function (k) { var el = cardEl(cards[k], true); if (marks) el.classList.add(marks.hi ? "won" : "lost"); host.appendChild(el); });
    }
    function renderCards() {
      var d = $("pg-dealer"), y = $("pg-you"), dc = $("pg-dealer-caps"), yc = $("pg-you-caps"), i;
      d.innerHTML = ""; y.innerHTML = ""; dc.innerHTML = ""; yc.innerHTML = "";
      y.classList.toggle("pick", phase === "set");
      var judged = outcome && !outcome.aceHigh;      // an ace-high dealer pushes everything: nothing won or lost to mark
      // the dealer: face up from the first card, and set before you set yours
      if (!theirs.length) { for (i = 0; i < 7; i++) { if (i === 2) gap(d); slot(d); } }
      else if (dealerSet) {
        rowSet(d, theirs, dealerSet, judged ? { lo: !outcome.lo, hi: !outcome.hi } : null);
        if (dealerAce) { var ah = document.createElement("div"); ah.className = "hint"; ah.textContent = "ACE-HIGH · EVERY BET PUSHES"; dc.appendChild(ah); }
        else {
          cap(dc, "lo", "2-CARD", PGE.name2(dealerSet.loS), judged ? (outcome.lo ? "lose" : "win") : "");
          cap(dc, "hi", "5-CARD", PGE.name5(dealerSet.hiS), judged ? (outcome.hi ? "lose" : "win") : "");
        }
      } else { for (i = 0; i < theirs.length; i++) d.appendChild(cardEl(theirs[i], true)); for (; i < 7; i++) slot(d); }
      // you
      if (!mine.length) { for (i = 0; i < 7; i++) { if (i === 2) gap(y); slot(y); } }
      else if (mySet) {
        rowSet(y, mine, mySet, judged ? { lo: outcome.lo, hi: outcome.hi } : null);
        cap(yc, "lo", "2-CARD", PGE.name2(mySet.loS), judged ? (outcome.lo ? "win" : "lose") : "");
        cap(yc, "hi", "5-CARD", PGE.name5(mySet.hiS), judged ? (outcome.hi ? "win" : "lose") : "");
      } else {
        for (i = 0; i < mine.length; i++) (function (k) {
          var el = cardEl(mine[k], true);
          if (pick.indexOf(k) >= 0) el.classList.add("low");
          if (phase === "set") el.addEventListener("click", function () { toggle(k); });
          y.appendChild(el);
        })(i);
        for (; i < 7; i++) slot(y);
        if (phase === "set") {
          var h = document.createElement("div"); h.className = "hint";
          var chk = check();
          h.textContent = pick.length < 2 ? "TAP THE TWO CARDS FOR YOUR 2-CARD HAND"
            : chk.foul ? "NOT ALLOWED · THE 5-CARD HAND MUST BE THE HIGHER ONE"
            : PGE.name2(chk.loS).toUpperCase() + "  ·  " + PGE.name5(chk.hiS).toUpperCase();
          yc.appendChild(h);
        }
      }
      var res = judged ? outcome.result : 0;
      label("pg-dealer-label", "DEALER", res < 0 ? "lose" : "");
      label("pg-you-label", "YOUR HAND", res > 0 ? "win" : res < 0 ? "lose" : "");
    }
    function check() {
      if (pick.length < 2) return { foul: true };
      var lo = [mine[pick[0]], mine[pick[1]]], hi = mine.filter(function (c, k) { return pick.indexOf(k) < 0; });
      var loS = PGE.two(lo), hiS = PGE.five(hi);
      return { loS: loS, hiS: hiS, foul: PGE.fouled(loS, hiS) };
    }
    var SPOTS = [["bet", "BET"], ["bonus", "BONUS"], ["ace", "ACE-HIGH"]];
    /** After a hand: what that bet made or lost, in a pill on top of its circle. */
    function pill(which) {
      if (phase !== "result" || !outcome) return "";
      var staked = which === "bet" ? last.bet : which === "bonus" ? paid.bonusBet : paid.aceBet;
      if (!(staked > 0)) return "";
      var net = which === "bet" ? paid.main : which === "bonus" ? paid.bonus : paid.ace;
      var big = Math.abs(net) >= 100000 && typeof shortAmt === "function";
      var amt = big ? shortAmt(Math.abs(net)) : fmt(Math.abs(net));
      return '<span class="dj-pill pg-pill ' + (net > 0 ? "w" : net < 0 ? "l" : "p") + '"><span class="amt">' +
        (net > 0 ? "+" + amt : net < 0 ? "−" + amt : "PUSH") + "</span></span>";
    }
    function renderSpots() {
      var open = phase === "betting";
      SPOTS.forEach(function (o) {
        var host = $("pg-spot-" + o[0]); host.innerHTML = "";
        var v = bets[o[0]], b = document.createElement("button");
        b.type = "button"; b.className = "pg-spot" + (o[0] === "bet" ? "" : " bonus"); b.disabled = !open;
        b.setAttribute("data-spot", o[0]);
        b.innerHTML = '<span class="ring">' + (v > 0 ? '<span class="placed">' + chipHtml(v, 46) + "</span>" : '<span class="empty">TAP</span>') +
          '</span><span class="cap">' + o[1] + "</span>" + pill(o[0]);
        b.addEventListener("click", function () { place(o[0]); });
        host.appendChild(b);
      });
    }
    function renderRack() {
      var host = $("pg-rack"); host.innerHTML = "";
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
      var a = $("pg-actions"); a.innerHTML = "";
      if (phase === "betting") {
        a.appendChild(actionBtn("CLEAR", DIM, clearBets, total() <= 0));
        a.appendChild(actionBtn(bets.bet > 0 ? "DEAL · $" + fmt(total()) : "DEAL", GOLD, deal, bets.bet <= 0 || total() > bankroll));
      } else if (phase === "set") {
        a.appendChild(actionBtn("HOUSE WAY", DIM, auto));
        a.appendChild(actionBtn("SET HAND", GOLD, confirm, check().foul));
      } else if (phase === "result") {
        a.appendChild(actionBtn("NEW HAND", GOLD, nextHand));
      }
    }
    /** The side bets, in one line under the result. */
    function renderBonusLine() {
      var el = $("pg-bonusline");
      el.className = "pg-bonusline";
      if (phase !== "result" || !(paid.bonusBet || paid.aceBet)) { el.textContent = ""; return; }
      var bits = [];
      if (sideHit && paid.bonusBet) bits.push("BONUS +$" + fmt(paid.bonus));
      if (aceHit && paid.aceBet) bits.push("ACE-HIGH +$" + fmt(paid.ace));
      if (bits.length) el.textContent = bits.join("  ·  ");
      else { el.textContent = "SIDE BETS · NOTHING THIS TIME"; el.classList.add("miss"); }
    }
    // what the side bets pay, on the felt the whole time; the line that hit lights up
    var SHORTPAY = { sf7: "7-CARD STR FLUSH", sf7j: "7-CARD SF, JOKER", aces: "FIVE ACES", royal: "ROYAL FLUSH", sf: "STRAIGHT FLUSH",
                     quads: "FOUR OF A KIND", full: "FULL HOUSE", flush: "FLUSH", trips: "THREE OF A KIND", straight: "STRAIGHT" };
    (function () {
      var rows = $("pg-pays-rows"), ace = $("pg-ace-rows");
      PGE.BONUS.forEach(function (b) {
        var d = document.createElement("div");
        d.setAttribute("data-k", b.key);
        d.innerHTML = "<span>" + SHORTPAY[b.key] + "</span><b>" + fmt(b.pays) + "</b>";
        rows.appendChild(d);
      });
      PGE.ACEPAYS.forEach(function (b) {
        var d = document.createElement("div");
        d.setAttribute("data-k", b.key);
        d.innerHTML = "<span>" + b.name + "</span><b>" + b.pays + "</b>";
        ace.appendChild(d);
      });
    })();
    function renderPays() {
      var done = phase === "result";
      var hit = done && paid.bonusBet > 0 && sideHit ? sideHit.key : null, ah = done && paid.aceBet > 0 && aceHit ? aceHit.key : null;
      [].forEach.call($("pg-pays-rows").children, function (d) { d.classList.toggle("hit", d.getAttribute("data-k") === hit); });
      [].forEach.call($("pg-ace-rows").children, function (d) { d.classList.toggle("hit", d.getAttribute("data-k") === ah); });
      var live = phase === "betting" ? bets : { bonus: paid.bonusBet, ace: paid.aceBet };
      $("pg-pays").classList.toggle("live", live.bonus > 0);
      $("pg-acepays").classList.toggle("live", live.ace > 0);
    }
    function renderRebuy() {
      var broke = Shell.mode === "testing" && bankroll < 25 && phase === "betting";
      $("pg-rebuy").classList.toggle("on", broke);
    }
    function renderAll() { renderTop(); renderCards(); renderSpots(); renderRack(); renderActions(); renderBonusLine(); renderPays(); renderRebuy(); }

    // ---------- betting ----------
    function place(which) {
      if (phase !== "betting") return;
      var lim = Shell.limits(), isSide = which !== "bet";
      var want = Math.min(selectedChip, bankroll - total());
      if (want <= 0) { setMessage("That's all your chips"); return; }
      var ok = allow(lim, want, bets[which], isSide);
      if (ok <= 0) { setMessage(refusal(lim, isSide)); return; }
      bets[which] += ok;
      setMessage(bets.bet > 0 ? "Deal when you're ready" : "Put a bet down first — the side bets ride with it");
      renderAll();
    }
    function clearBets() {
      if (phase !== "betting") return;
      bets = { bet: 0, bonus: 0, ace: 0 };
      setMessage("Place your bet");
      renderAll();
    }

    // ---------- the hand ----------
    function deal() {
      if (phase !== "betting" || bets.bet <= 0 || total() > bankroll) return;
      bankroll -= total();
      syncWallet();
      last = { bet: bets.bet, bonus: bets.bonus, ace: bets.ace };
      var d = PGE.deck();
      var my = sortHand(d.slice(0, 7)), dl = sortHand(d.slice(7, 14));
      mine = []; theirs = []; pick = []; mySet = null; dealerSet = null; dealerAce = false; outcome = null; sideHit = null; aceHit = null;
      paid = { main: 0, bonus: 0, ace: 0, bonusBet: bets.bonus, aceBet: bets.ace };
      phase = "dealing";
      setMessage("Dealing…");
      renderAll();
      for (var i = 0; i < 7; i++) (function (k) {
        t(80 + k * 110, function () { theirs.push(dl[k]); mine.push(my[k]); renderCards(); });
      })(i);
      t(80 + 7 * 110 + 160, function () {
        // the dealer sets first, face up, the house way
        dealerSet = PGE.houseWay(theirs);
        dealerAce = PGE.aceHigh(theirs).is;
        if (dealerAce) {
          // nothing to play for: every bet pushes, so your hand is just laid out
          mySet = PGE.houseWay(mine);
          phase = "reveal";
          setMessage("Dealer is ace-high…");
          renderAll();
          t(900, settle);
          return;
        }
        phase = "set";
        setMessage("Set your two hands to beat the dealer's");
        renderAll();
      });
    }
    function toggle(k) {
      if (phase !== "set") return;
      var at = pick.indexOf(k);
      if (at >= 0) pick.splice(at, 1);
      else { if (pick.length >= 2) pick.shift(); pick.push(k); }
      renderCards(); renderActions();
    }
    function auto() {
      if (phase !== "set") return;
      pick = PGE.houseWay(mine).lo.slice();
      setMessage("Set the house way — tap SET HAND, or change it");
      renderCards(); renderActions();
    }
    function confirm() {
      if (phase !== "set") return;
      var chk = check();
      if (chk.foul) { setMessage(pick.length < 2 ? "Pick two cards for the 2-card hand" : "The 5-card hand has to be the higher one"); return; }
      mySet = { lo: pick.slice().sort(function (a, b) { return a - b; }), loS: chk.loS, hiS: chk.hiS };
      phase = "reveal";
      setMessage("…");
      renderAll();
      t(500, settle);
    }
    function settle() {
      outcome = PGE.settle(mySet, dealerSet);
      if (dealerAce) outcome = { lo: false, hi: false, result: 0, aceHigh: true };
      var back = 0, won = 0;
      if (outcome.result > 0) { won = bets.bet; back = bets.bet * 2; }
      else if (outcome.result === 0) back = bets.bet;
      paid.main = back - bets.bet;
      if (paid.bonusBet > 0) {
        sideHit = PGE.bonus(mine);
        if (sideHit) { paid.bonus = paid.bonusBet * sideHit.pays; back += paid.bonusBet + paid.bonus; }
        else paid.bonus = -paid.bonusBet;
      }
      if (paid.aceBet > 0) {
        aceHit = PGE.aceBet(mine, theirs);
        if (aceHit) { paid.ace = paid.aceBet * aceHit.pays; back += paid.aceBet + paid.ace; }
        else paid.ace = -paid.aceBet;
      }
      if (back > 0) { bankroll += back; syncWallet(); }
      var net = paid.main + paid.bonus + paid.ace;
      var msg = outcome.aceHigh ? "Dealer is ace-high — your bet pushes"
        : outcome.result > 0 ? "You win both — +$" + fmt(won)
        : outcome.result === 0 ? "One each — push, your bet comes back"
        : "Dealer takes both";
      phase = "result";
      if (Shell.mode === "campaign" && bankroll >= Shell.getGoal()) msg = "🏆 GOAL REACHED!";
      setMessage(msg, outcome.result > 0 || (outcome.result === 0 && net > 0) ? "win" : outcome.result < 0 && net <= 0 ? "lose" : "");
      renderAll();
      if (Shell.mode === "campaign" && bankroll >= Shell.getGoal()) Shell.gate(refresh);
    }
    function nextHand() {
      if (phase !== "result") return;
      clearTimers();
      phase = "betting";
      mine = []; theirs = []; pick = []; mySet = null; dealerSet = null; dealerAce = false; outcome = null; sideHit = null; aceHit = null;
      // the same bets go back down, as far as the purse and the room allow
      var lim = Shell.limits(), left = bankroll;
      bets.bet = Math.min(allow(lim, last.bet, 0, false), left); left -= bets.bet;
      bets.bonus = bets.bet > 0 ? Math.min(allow(lim, last.bonus, 0, true), left) : 0; left -= bets.bonus;
      bets.ace = bets.bet > 0 ? Math.min(allow(lim, last.ace, 0, true), left) : 0;
      setMessage(bets.bet > 0 ? "Same bet — deal, or change it" : "Place your bet");
      renderAll();
      Shell.gate(refresh);
    }
    function refresh() {
      if (Shell.mode === "campaign") bankroll = Shell.getBankroll();
      if (phase === "betting" && total() > bankroll) bets = { bet: 0, bonus: 0, ace: 0 };
      renderAll();
    }
    function enter() {
      clearTimers();
      phase = "betting";
      mine = []; theirs = []; pick = []; mySet = null; dealerSet = null; dealerAce = false; outcome = null; sideHit = null; aceHit = null;
      bets = { bet: 0, bonus: 0, ace: 0 }; last = { bet: 0, bonus: 0, ace: 0 };
      bankroll = Shell.mode === "campaign" ? Shell.getBankroll() : FREEPLAY.buyIn;
      setMessage("Place your bet");
      renderAll();
      Shell.gate(refresh);
      setTimeout(PGE.warm, 60);      // the dealer's odds, worked out while you put your chips down
    }
    $("pg-buyin").addEventListener("click", function () {
      if (phase !== "betting" || bankroll >= 25) return;
      freshStack(function (v) { bankroll = v; setMessage("Place your bet"); renderAll(); });
    });
    // the 4 The Boys piece behind the felt, the same picture the other poker tables use, turned red
    (function () { var from = document.querySelector("#screen-miniuth .mu-bg"); if (from) $("pg-bg").src = from.src; })();
    $("pg-back").addEventListener("click", function () { Shell.show("poker"); });
    $("pk-paigow").addEventListener("click", function () { PaiGow.enter(); Shell.show("paigow"); });
    // Campaign and Play Testing always list this table; the tournament does once its server knows the game.
    (function () {
      var show = Shell.show;
      Shell.show = function (name) {
        // in the tournament it shows once the server counts it as one of the games
        if (name === "poker") $("pk-paigow").style.display = typeof TT !== "undefined" && TT.on && !(TT.hasGame && TT.hasGame("Pai Gow")) ? "none" : "";
        return show.apply(this, arguments);
      };
    })();
    return {
      busy: function () { return phase !== "betting" && phase !== "result"; },
      credit: function (x) {
        bankroll += x; syncWallet();
        if (phase === "betting" && total() > bankroll) bets = { bet: 0, bonus: 0, ace: 0 };
        renderAll();
      },
      seatOk: function () { return phase === "betting" || phase === "result"; },
      cash: function () { return bankroll; },
      enter: enter
    };
  })();
