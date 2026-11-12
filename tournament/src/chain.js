  /* ============================================================
     CHAIN REACTION — the disc golf hold & spin slot, play testing only
     ============================================================ */
// Chain Reaction engine: shared by the page and the simulation.
  var CHX = (function () {
  var PAY = { ACE:[150,750,3000], GOLD:[100,380,1500], PINK:[80,260,1000], BLUE:[65,200,750], TREE:[50,150,500],
              A:[26,80,260], K:[26,80,260], Q:[21,65,210], J:[21,52,180], "10":[16,46,155], "9":[16,40,130] };
  var W = {
    ACE:[0,4,4,4,0], GOLD:[4,4,4,4,4], PINK:[5,5,5,5,5], BLUE:[6,6,6,6,6], TREE:[7,7,7,7,7],
    A:[11,11,11,11,11], K:[11,11,11,11,11], Q:[12,12,12,12,12], J:[12,12,12,12,12], "10":[13,13,13,13,13], "9":[13,13,13,13,13],
    BASKET:[15,15,15,15,15]
  };
  var LINES = [
    [1,1,1,1,1],[0,0,0,0,0],[2,2,2,2,2],[0,1,2,1,0],[2,1,0,1,2],
    [0,0,1,2,2],[2,2,1,0,0],[1,0,0,0,1],[1,2,2,2,1],[1,0,1,2,1],
    [1,2,1,0,1],[0,1,1,1,0],[2,1,1,1,2],[0,1,0,1,0],[2,1,2,1,2],
    [1,1,0,1,1],[1,1,2,1,1],[0,0,2,0,0],[2,2,0,2,2],[0,2,0,2,0],
    [2,0,2,0,2],[1,0,2,0,1],[1,2,0,2,1],[0,2,2,2,0],[2,0,0,0,2]
  ];
  // what a basket carries, in total bets (PAR/BIRDIE/EAGLE are the jackpot baskets)
  var VALUES = [[1,30],[2,24],[3,16],[4,10],[5,8],[8,5],[10,3],[15,2],["PAR",2.4],["BIRDIE",0.9],["EAGLE",0.12]];
  var JACK = { PAR: 10, BIRDIE: 30, EAGLE: 150, GRAND: 1000 };
  var TRIGGER = 6, RESPIN_HIT = 0.07;
  function wpick(t, rnd) { var tot = 0, i; for (i = 0; i < t.length; i++) tot += t[i][1]; var r = rnd() * tot;
    for (i = 0; i < t.length; i++) { r -= t[i][1]; if (r < 0) return t[i][0]; } return t[0][0]; }
  function pick(reel, rnd) {
    var tot = 0, k; for (k in W) tot += W[k][reel];
    var r = rnd() * tot; for (k in W) { r -= W[k][reel]; if (r < 0) return k; } return "9";
  }
  function basketValue(rnd) { return wpick(VALUES, rnd); }
  /** g[col][row] = {s, v} */
  function spin(rnd) {
    var g = [];
    for (var c = 0; c < 5; c++) { var col = []; for (var r = 0; r < 3; r++) { var s = pick(c, rnd); col.push({ s: s, v: s === "BASKET" ? basketValue(rnd) : null }); } g.push(col); }
    return g;
  }
  function evaluate(g) {
    var wins = [];
    for (var l = 0; l < LINES.length; l++) {
      var L = LINES[l], syms = L.map(function (row, col) { return g[col][row].s; });
      var base = null, n = 0;
      for (var i = 0; i < 5; i++) {
        var s = syms[i];
        if (s === "BASKET") break;
        if (s === "ACE") { n++; continue; }
        if (base === null) { base = s; n++; continue; }
        if (s === base) { n++; continue; }
        break;
      }
      var best = 0, sym = null, cnt = 0;
      if (n >= 3 && base) { best = PAY[base][n - 3]; sym = base; cnt = n; }
      var w = 0; while (w < 5 && syms[w] === "ACE") w++;
      if (w >= 3 && PAY.ACE[w - 3] > best) { best = PAY.ACE[w - 3]; sym = "ACE"; cnt = w; }
      if (best) wins.push({ line: l, sym: sym, count: cnt, pay: best });
    }
    var baskets = 0;
    for (var c = 0; c < 5; c++) for (var r = 0; r < 3; r++) if (g[c][r].s === "BASKET") baskets++;
    return { wins: wins, baskets: baskets };
  }
  function valueX(v) { return typeof v === "number" ? v : JACK[v]; }
  return { PAY: PAY, LINES: LINES, VALUES: VALUES, JACK: JACK, TRIGGER: TRIGGER, RESPIN_HIT: RESPIN_HIT,
           spin: spin, evaluate: evaluate, basketValue: basketValue, valueX: valueX };
})();
  var ChainSlot = (function () {
  var root = document.getElementById("screen-chain");
  var BETS = [25, 50, 100, 250, 500, 1000, 2500];
  var fmt = function (n) { return Math.round(n).toLocaleString("en-US"); };
  var store = {
    get: function (k, d) { try { var v = localStorage.getItem("cr2." + k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
    set: function (k, v) { try { localStorage.setItem("cr2." + k, JSON.stringify(v)); } catch (e) {} }
  };
  var bank = FREEPLAY.buyIn, bet = store.get("bet", 100), fast = store.get("fast", false);
  var stats = store.get("stats", { spins: 0, in: 0, out: 0, bonuses: 0, best: 0 });
  var soundOn = false, busy = false, grid = null, showTimer = null, countRaf = 0;

  // ---------- symbols ----------
  function disc(grad, stamp) {
    return '<svg viewBox="0 0 100 100" aria-hidden="true">' +
      '<ellipse cx="50" cy="66" rx="40" ry="13" fill="rgba(0,0,0,.45)"/>' +
      '<ellipse cx="50" cy="58" rx="44" ry="24" fill="url(#ch-' + grad + ')"/>' +
      '<ellipse cx="50" cy="52" rx="44" ry="24" fill="url(#ch-' + grad + ')"/>' +
      '<ellipse cx="50" cy="52" rx="44" ry="24" fill="none" stroke="rgba(255,255,255,.55)" stroke-width="1.5"/>' +
      '<ellipse cx="50" cy="52" rx="30" ry="15" fill="none" stroke="rgba(255,255,255,.35)" stroke-width="1.5"/>' +
      stamp +
      '<path d="M18 44 Q50 30 82 44" stroke="rgba(255,255,255,.6)" stroke-width="3" fill="none" stroke-linecap="round"/>' +
      "</svg>";
  }
  var STAMP_STAR = '<path d="M50 41 l3.2 6.6 7.2 1 -5.2 5 1.3 7.1 -6.5 -3.4 -6.5 3.4 1.3 -7.1 -5.2 -5 7.2 -1z" fill="rgba(255,255,255,.85)"/>';
  var STAMP_BOLT = '<path d="M53 40 l-10 13 h7 l-3 11 10 -14 h-7z" fill="rgba(255,255,255,.85)"/>';
  var STAMP_RING = '<ellipse cx="50" cy="52" rx="11" ry="5.5" fill="none" stroke="rgba(255,255,255,.85)" stroke-width="3"/>';
  var BASKET =
    '<svg viewBox="0 0 100 120" aria-hidden="true">' +
    '<ellipse cx="50" cy="62" rx="48" ry="48" fill="url(#ch-g-halo)"/>' +
    '<rect x="46.5" y="10" width="7" height="104" rx="2" fill="url(#ch-g-steel)"/>' +
    '<ellipse cx="50" cy="16" rx="30" ry="7" fill="url(#ch-g-band)" stroke="#8A5E08" stroke-width="1"/>' +
    '<g stroke="#D7DEE2" stroke-width="1.6" fill="none" opacity=".95">' +
    '<path d="M22 18 Q28 40 30 60"/><path d="M30 21 Q34 42 36 60"/><path d="M40 22 Q42 42 43 60"/><path d="M60 22 Q58 42 57 60"/>' +
    '<path d="M70 21 Q66 42 64 60"/><path d="M78 18 Q72 40 70 60"/><path d="M50 23 V60"/></g>' +
    '<path d="M16 60 h68 l-7 22 h-54 z" fill="url(#ch-g-band)" stroke="#8A5E08" stroke-width="1"/>' +
    '<ellipse cx="50" cy="60" rx="34" ry="6" fill="#B07A0C"/>' +
    '<path d="M24 68 h52" stroke="rgba(255,255,255,.45)" stroke-width="1.5"/>' +
    '<rect x="34" y="112" width="32" height="5" rx="2" fill="#4E585E"/>' +
    "</svg>";
  var TREE =
    '<svg viewBox="0 0 100 100" aria-hidden="true">' +
    '<ellipse cx="50" cy="92" rx="26" ry="5" fill="rgba(0,0,0,.4)"/>' +
    '<rect x="45" y="72" width="10" height="20" rx="2" fill="#7A5232"/>' +
    '<path d="M50 6 L80 44 H66 L88 72 H12 L34 44 H20 Z" fill="url(#ch-g-pine)" stroke="#0E4A24" stroke-width="1.5" stroke-linejoin="round"/>' +
    '<path d="M50 6 L80 44 H66 L88 72 H50 Z" fill="rgba(0,0,0,.18)"/>' +
    '<path d="M50 14 L38 36" stroke="rgba(255,255,255,.45)" stroke-width="3" stroke-linecap="round"/>' +
    "</svg>";
  function letter(t, grad) {
    var size = t === "10" ? 52 : 66;
    return '<svg viewBox="0 0 100 100" aria-hidden="true">' +
      '<text x="50" y="74" text-anchor="middle" font-family="Teko, Impact, sans-serif" font-weight="700" font-size="' + size + '" fill="rgba(0,0,0,.55)" transform="translate(0 3)">' + t + "</text>" +
      '<text x="50" y="74" text-anchor="middle" font-family="Teko, Impact, sans-serif" font-weight="700" font-size="' + size + '" fill="url(#ch-' + grad + ')" stroke="rgba(255,255,255,.55)" stroke-width="1.2">' + t + "</text></svg>";
  }
  var WILD =
    '<svg viewBox="0 0 100 100" aria-hidden="true">' +
    '<circle cx="50" cy="50" r="48" fill="url(#ch-g-halo)"/>' +
    '<circle cx="50" cy="46" r="36" fill="url(#ch-g-rim)"/>' +
    '<circle cx="50" cy="46" r="31" fill="url(#ch-g-gold)"/>' +
    '<circle cx="50" cy="46" r="24" fill="none" stroke="rgba(110,74,6,.55)" stroke-width="1.5" stroke-dasharray="2 3"/>' +
    '<text x="50" y="56" text-anchor="middle" font-family="Teko, Impact, sans-serif" font-weight="700" font-size="30" fill="#5A3A00">ACE</text>' +
    '<path d="M18 74 h64 l-5 9 5 9 h-64 l5 -9 z" fill="#C8102E" stroke="#6E0718" stroke-width="1"/>' +
    '<text x="50" y="88" text-anchor="middle" font-family="Teko, Impact, sans-serif" font-weight="700" font-size="17" letter-spacing="2" fill="#FFF3C4">WILD</text>' +
    "</svg>";
  var ART = {
    ACE: WILD, GOLD: disc("g-orange", STAMP_STAR), PINK: disc("g-pink", STAMP_BOLT), BLUE: disc("g-blue", STAMP_RING),
    TREE: TREE, BASKET: BASKET,
    A: letter("A", "g-L-red"), K: letter("K", "g-L-blue"), Q: letter("Q", "g-L-purple"), J: letter("J", "g-L-green"), "10": letter("10", "g-L-orange"), "9": letter("9", "g-L-green")
  };
  var NAMES = { ACE: "Ace", GOLD: "Driver", PINK: "Midrange", BLUE: "Putter", TREE: "Tree", A: "A", K: "K", Q: "Q", J: "J", "10": "10", "9": "9" };

  function amtHtml(v) {
    if (v === null || v === undefined) return "";
    if (typeof v === "number") return '<div class="ch-amt">' + fmt(v * bet) + "</div>";
    return '<div class="ch-amt ch-jp">' + v + "</div>";
  }

  // ---------- sound (only after the player turns it on) ----------
  var ac = null;
  function tone(f, t0, dur, type, vol) {
    if (!soundOn || !ac) return;
    var o = ac.createOscillator(), g = ac.createGain();
    o.type = type || "triangle"; o.frequency.value = f;
    g.gain.setValueAtTime(vol || 0.06, ac.currentTime + t0);
    g.gain.exponentialRampToValueAtTime(0.0001, ac.currentTime + t0 + dur);
    o.connect(g); g.connect(ac.destination); o.start(ac.currentTime + t0); o.stop(ac.currentTime + t0 + dur + 0.02);
  }
  function gong() { for (var i = 0; i < 16; i++) tone(2400 + Math.random() * 2600, i * 0.025 + Math.random() * 0.02, 0.09, "square", 0.022); }
  function stopClick() { tone(160, 0, 0.07, "square", 0.05); }
  function ding() { tone(1320, 0, 0.25, "triangle", 0.07); tone(1760, 0.06, 0.25, "triangle", 0.05); }
  function winJingle() { [523, 659, 784, 1046].forEach(function (f, i) { tone(f, i * 0.08, 0.2, "triangle", 0.06); }); }

  // ---------- build ----------
  var reels = [], strips = [];
  function build() {
    var host = $("ch-reels");
    for (var c = 0; c < 5; c++) {
      var r = document.createElement("div"); r.className = "ch-reel";
      var s = document.createElement("div"); s.className = "ch-strip";
      r.appendChild(s); host.appendChild(r); reels.push(r); strips.push(s);
    }
    var svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("class", "ch-lines"); svg.id = "ch-lines"; svg.setAttribute("aria-hidden", "true");
    host.appendChild(svg);
  }
  function cellHtml(x) { return '<div class="ch-cell" data-s="' + x.s + '">' + ART[x.s] + amtHtml(x.v) + "</div>"; }
  function setStrip(c, col) {
    strips[c].classList.remove("ch-blur");
    strips[c].innerHTML = col.map(cellHtml).join(""); strips[c].style.transition = "none"; strips[c].style.transform = "translateY(0)";
  }

  function paint() {
    $("ch-bank").textContent = fmt(bank);
    $("ch-betv").textContent = fmt(bet);
    $("ch-jpG").textContent = fmt(bet * CHX.JACK.GRAND); $("ch-jpM").textContent = fmt(bet * CHX.JACK.EAGLE);
    $("ch-jpN").textContent = fmt(bet * CHX.JACK.BIRDIE); $("ch-jpI").textContent = fmt(bet * CHX.JACK.PAR);
    var i = BETS.indexOf(bet);
    $("ch-betDown").disabled = busy || i <= 0;
    $("ch-betUp").disabled = busy || i >= BETS.length - 1 || BETS[i + 1] > bank;
    $("ch-throw").disabled = busy || bank < bet;
    $("ch-throw").classList.toggle("ch-going", busy);
    $("ch-fast").classList.toggle("ch-on", fast);
    $("ch-sound").classList.toggle("ch-on", soundOn);
    var rtp = stats.in ? (100 * stats.out / stats.in).toFixed(1) + "% back" : "no spins yet";
    $("ch-stats").textContent = fmt(stats.spins) + " spins · " + rtp + " · " + stats.bonuses + " feature" + (stats.bonuses === 1 ? "" : "s") +
      (stats.best ? " · best win " + fmt(stats.best) : "");
    if (bet > bank && bank >= BETS[0]) { for (var j = BETS.length - 1; j >= 0; j--) if (BETS[j] <= bank) { bet = BETS[j]; break; } paint(); }
  }
  function say(big, small, mode) {
    $("ch-msg").className = "ch-msg" + (mode ? " ch-" + mode : "");
    $("ch-msg").innerHTML = '<div class="ch-big">' + big + '</div><div class="ch-small">' + (small || "&nbsp;") + "</div>";
  }
  function countUp(el, to, ms) {
    cancelAnimationFrame(countRaf);
    var t0 = performance.now();
    var step = function (now) {
      var k = Math.min(1, (now - t0) / ms), v = Math.round(to * (1 - Math.pow(1 - k, 3)));
      el.textContent = fmt(v);
      if (k < 1) countRaf = requestAnimationFrame(step);
    };
    countRaf = requestAnimationFrame(step);
  }
  function save() { store.set("stats", stats); }

  // ---------- spin ----------
  function randCell(c) { return CHX.spin(Math.random)[c][0]; }
  function throwDisc() {
    if (busy || bank < bet) return;
    busy = true; clearInterval(showTimer); clearLines();
    bank -= bet; stats.spins++; stats.in += bet; save();
    $("ch-winv").textContent = "0";
    say("Good luck", "", "quiet");
    paint();
    grid = CHX.spin(Math.random);
    var h = reels[0].getBoundingClientRect().height / 3;
    var base = fast ? 520 : 1240, gap = fast ? 220 : 460;
    for (var c = 0; c < 5; c++) {
      var n = (fast ? 16 : 34) + c * (fast ? 5 : 9), col = [];
      for (var i = 0; i < n; i++) col.push(randCell(c));
      var stripCells = grid[c].concat(col).concat(currentCol(c));
      strips[c].innerHTML = stripCells.map(cellHtml).join("");
      strips[c].classList.add("ch-blur");
      strips[c].style.transition = "none";
      strips[c].style.transform = "translateY(" + (-(stripCells.length - 3) * h) + "px)";
      void strips[c].offsetHeight;
      var dur = base + c * gap;
      strips[c].style.transition = "transform " + dur + "ms cubic-bezier(.12,.55,.22,1.04)";
      strips[c].style.transform = "translateY(0)";
      (function (col, d) {
        setTimeout(function () { strips[col].classList.remove("ch-blur"); }, d - 160);
        setTimeout(function () { setStrip(col, grid[col]); stopClick(); if (col === 4) landed(); }, d);
      })(c, dur);
    }
  }
  var lastCols = [];
  function currentCol(c) { return lastCols[c] || [randCell(c), randCell(c), randCell(c)]; }
  function cellAt(c, r) { return strips[c].children[r]; }
  function clearLines() {
    $("ch-lines").innerHTML = "";
    root.querySelectorAll(".ch-cell").forEach(function (el) { el.classList.remove("ch-hit", "ch-dim"); });
  }
  function showWin(w) {
    clearLines();
    var host = $("ch-reels").getBoundingClientRect(), pts = [], L = CHX.LINES[w.line];
    $("ch-lines").setAttribute("viewBox", "0 0 " + host.width + " " + host.height);
    for (var c = 0; c < 5; c++) for (var r = 0; r < 3; r++) cellAt(c, r).classList.add("ch-dim");
    for (c = 0; c < 5; c++) {
      var el = cellAt(c, L[c]), b = el.getBoundingClientRect();
      pts.push((b.left - host.left + b.width / 2) + "," + (b.top - host.top + b.height / 2));
      if (c < w.count) { el.classList.remove("ch-dim"); el.classList.add("ch-hit"); }
    }
    $("ch-lines").innerHTML = '<polyline points="' + pts.join(" ") + '"/>';
  }
  function landed() {
    lastCols = grid;
    var e = CHX.evaluate(grid), lineBet = bet / 25, total = 0;
    e.wins.forEach(function (w) { total += w.pay * lineBet; });
    total = Math.round(total);
    if (total > 0) {
      bank += total; stats.out += total; stats.best = Math.max(stats.best, total); save();
      var top = e.wins.slice().sort(function (a, b) { return b.pay - a.pay; })[0];
      var x = total / bet, label = x >= 50 ? "MEGA WIN" : x >= 15 ? "BIG WIN" : x >= 5 ? "NICE WIN" : "WIN";
      say(label + " " + fmt(total), e.wins.length + " line" + (e.wins.length > 1 ? "s" : "") + " · " + top.count + " × " + NAMES[top.sym]);
      countUp($("ch-winv"), total, Math.min(1600, 400 + x * 60));
      winJingle();
      var i = 0; showWin(e.wins[0]);
      if (e.wins.length > 1 && e.baskets < CHX.TRIGGER) showTimer = setInterval(function () { i = (i + 1) % e.wins.length; showWin(e.wins[i]); }, 1200);
    } else if (e.baskets < CHX.TRIGGER) {
      say(e.baskets >= 4 ? e.baskets + " baskets — so close" : "No win", e.baskets >= 4 ? "6 start Hold & Spin" : "", "quiet");
    }
    if (e.baskets >= CHX.TRIGGER) {
      clearLines(); clearInterval(showTimer);
      for (var c = 0; c < 5; c++) for (var r = 0; r < 3; r++) if (grid[c][r].s === "BASKET") cellAt(c, r).classList.add("ch-hit");
      say(e.baskets + " BASKETS!", "Hold & Spin", "bonus");
      gong();
      setTimeout(function () { startHold(total); }, 1500);
      return;
    }
    busy = false; paint();
  }

  // ---------- Hold & Spin ----------
  var H = null;
  function hcellHtml(v, fresh) {
    return v === null ? "" : ART.BASKET + amtHtml(v);
  }
  function paintHold() {
    var host = $("ch-hold"); host.innerHTML = "";
    for (var k = 0; k < 15; k++) {
      var d = document.createElement("div");
      d.className = "ch-hcell" + (H.cells[k] !== null ? " ch-locked" : "") + (H.spinning && H.cells[k] === null ? " ch-spinning" : "") + (H.fresh[k] ? " ch-fresh" : "");
      d.innerHTML = hcellHtml(H.cells[k]);
      host.appendChild(d);
    }
    var dots = $("ch-spinsLeft").querySelectorAll("i");
    for (var i = 0; i < 3; i++) dots[i].classList.toggle("ch-on", i < H.left);
    var sum = 0; H.cells.forEach(function (v) { if (v !== null) sum += CHX.valueX(v) * bet; });
    $("ch-hsum").textContent = "$" + fmt(sum);
  }
  function flash(sub, title, num, ms, then) {
    $("ch-flashS").textContent = sub; $("ch-flashT").textContent = title; $("ch-flashN").textContent = num || "";
    $("ch-flash").hidden = false;
    setTimeout(function () { $("ch-flash").hidden = true; if (then) then(); }, ms);
  }
  function startHold(lineWin) {
    stats.bonuses++; save();
    // grid is [col][row]; the hold grid reads row by row
    var cells = [];
    for (var r = 0; r < 3; r++) for (var c = 0; c < 5; c++) cells.push(grid[c][r].s === "BASKET" ? grid[c][r].v : null);
    H = { cells: cells, left: 3, lineWin: lineWin, fresh: [], spinning: false };
    flash("HOLD & SPIN", "CHAINS!", "3 SPINS · NEW BASKETS RESET THEM", 1800, function () {
      $("ch-hold").hidden = false; $("ch-hud").hidden = false;
      say("Baskets lock in place", "Every new basket resets you to 3 spins", "bonus");
      paintHold();
      setTimeout(respin, 900);
    });
  }
  function respin() {
    if (!H) return;
    H.fresh = []; H.spinning = true; paintHold();
    var dur = fast ? 700 : 1300;
    setTimeout(function () {
      var hit = false;
      for (var k = 0; k < 15; k++) if (H.cells[k] === null && Math.random() < CHX.RESPIN_HIT) { H.cells[k] = CHX.basketValue(Math.random); H.fresh[k] = true; hit = true; }
      H.spinning = false;
      H.left = hit ? 3 : H.left - 1;
      paintHold();
      if (hit) ding(); else stopClick();
      var full = H.cells.every(function (v) { return v !== null; });
      if (full || H.left === 0) setTimeout(endHold, 1100);
      else setTimeout(respin, hit ? 1000 : 700);
    }, dur);
  }
  function endHold() {
    var sum = 0, full = H.cells.every(function (v) { return v !== null; }), jps = [];
    H.cells.forEach(function (v) { if (v !== null) { sum += CHX.valueX(v) * bet; if (typeof v !== "number") jps.push(v); } });
    if (full) { sum += CHX.JACK.GRAND * bet; jps.push("ACE"); }
    sum = Math.round(sum);
    bank += sum; stats.out += sum;
    var total = sum + H.lineWin;
    stats.best = Math.max(stats.best, total); save();
    gong();
    flash(full ? "ALL 15 FILLED" : "HOLD & SPIN PAYS", full ? "ACE!" : jps.length ? jps[jps.length - 1] + "!" : "COLLECT", "$" + fmt(sum), 2400, function () {
      $("ch-hold").hidden = true; $("ch-hud").hidden = true;
      countUp($("ch-winv"), total, 1400);
      var n = H.cells.filter(function (v) { return v !== null; }).length;
      say("BASKETS PAY " + fmt(sum), n + " of 15 baskets" + (jps.length ? " · " + jps.join(", ") : ""), "bonus");
      H = null; busy = false; paint();
    });
  }

  // ---------- pay table ----------
  function paintPays() {
    var lb = bet / 25, rows = [];
    ["ACE", "GOLD", "PINK", "BLUE", "TREE", "A", "K", "Q", "J", "10", "9"].forEach(function (s) {
      rows.push('<div class="ch-row"><div class="ch-ic">' + ART[s] + '</div><div class="ch-pays">' +
        CHX.PAY[s].map(function (v, i) { return "<span>" + (i + 3) + "×</span><span>" + fmt(v * lb) + "</span>"; }).reverse().join("") + "</div></div>");
    });
    rows.push('<div class="ch-row"><div class="ch-ic">' + ART.BASKET + '</div><div class="ch-pays"><span>6+</span><span>HOLD &amp; SPIN</span></div></div>');
    $("ch-pt").innerHTML = rows.join("");
  }

  // ---------- wiring ----------
  $("ch-throw").addEventListener("click", throwDisc);
  document.addEventListener("keydown", function (e) {
    if (e.code !== "Space" || !root.classList.contains("on")) return;
    if ($("ch-sheet").hidden) { e.preventDefault(); throwDisc(); }
  });
  $("ch-betDown").addEventListener("click", function () { var i = BETS.indexOf(bet); if (!busy && i > 0) { bet = BETS[i - 1]; store.set("bet", bet); paint(); } });
  $("ch-betUp").addEventListener("click", function () { var i = BETS.indexOf(bet); if (!busy && i < BETS.length - 1 && BETS[i + 1] <= bank) { bet = BETS[i + 1]; store.set("bet", bet); paint(); } });
  $("ch-fast").addEventListener("click", function () { fast = !fast; store.set("fast", fast); paint(); });
  $("ch-sound").addEventListener("click", function () {
    soundOn = !soundOn;
    if (soundOn && !ac) { try { ac = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { soundOn = false; } }
    if (ac && ac.state === "suspended") ac.resume();
    paint(); if (soundOn) ding();
  });
  $("ch-reset").addEventListener("click", function () {
    if (busy) return;
    freshStack(function (v) { bank = v; }); stats = { spins: 0, in: 0, out: 0, bonuses: 0, best: 0 }; save();
    $("ch-winv").textContent = "0"; clearLines(); clearInterval(showTimer);
    say("Fresh $" + fmt(bank), "Stats cleared", "quiet"); paint();
  });
  $("ch-paysBtn").addEventListener("click", function () { paintPays(); $("ch-sheet").hidden = false; });
  $("ch-sheetX").addEventListener("click", function () { $("ch-sheet").hidden = true; });
  $("ch-sheet").addEventListener("click", function (e) { if (e.target === $("ch-sheet")) $("ch-sheet").hidden = true; });

  $("ch-back").addEventListener("click", function () { if (!busy) Shell.show("othermenu"); });
  function enter() {
    Shell.mode = "testing";
    bank = FREEPLAY.buyIn;
    clearInterval(showTimer); clearLines();
    $("ch-winv").textContent = "0";
    say("Good luck", "6 baskets start Hold & Spin", "quiet");
    paint();
  }
  $("om-chain").addEventListener("click", function () { enter(); Shell.show("chain"); });
  // Other Games lists the slot only on the Play Testing side; the campaign and tournament never see it
  var show = Shell.show;
  Shell.show = function (name) {
    if (name === "othermenu") $("om-chain").style.display = Shell.mode === "testing" ? "" : "none";
    return show.apply(this, arguments);
  };
  build();
  var first = CHX.spin(Math.random);
  for (var c = 0; c < 5; c++) setStrip(c, first[c]);
  lastCols = first;
  if (BETS.indexOf(bet) < 0) bet = 100;
  paint();
  return { enter: enter, busy: function () { return busy; } };
})();
