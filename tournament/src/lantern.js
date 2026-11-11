  /* ============================================================
     CHILI LANTERNS — the hold & spin slot, play testing only
     ============================================================ */
// Chili Lanterns engine: shared by the page and the simulation.
  var LNX = (function () {
  var PAY = { WILD:[150,750,3000], WOK:[100,380,1500], DUMP:[80,260,1000], CRACK:[65,200,750], COIN:[50,150,500],
              A:[26,80,260], K:[26,80,260], Q:[21,65,210], J:[21,52,180], "10":[16,46,155], "9":[16,40,130] };
  var W = {
    WILD:[0,4,4,4,0], WOK:[4,4,4,4,4], DUMP:[5,5,5,5,5], CRACK:[6,6,6,6,6], COIN:[7,7,7,7,7],
    A:[11,11,11,11,11], K:[11,11,11,11,11], Q:[12,12,12,12,12], J:[12,12,12,12,12], "10":[13,13,13,13,13], "9":[13,13,13,13,13],
    LANTERN:[15,15,15,15,15]
  };
  var LINES = [
    [1,1,1,1,1],[0,0,0,0,0],[2,2,2,2,2],[0,1,2,1,0],[2,1,0,1,2],
    [0,0,1,2,2],[2,2,1,0,0],[1,0,0,0,1],[1,2,2,2,1],[1,0,1,2,1],
    [1,2,1,0,1],[0,1,1,1,0],[2,1,1,1,2],[0,1,0,1,0],[2,1,2,1,2],
    [1,1,0,1,1],[1,1,2,1,1],[0,0,2,0,0],[2,2,0,2,2],[0,2,0,2,0],
    [2,0,2,0,2],[1,0,2,0,1],[1,2,0,2,1],[0,2,2,2,0],[2,0,0,0,2]
  ];
  // what a lantern carries, in total bets (MINI/MINOR/MAJOR are the jackpot lanterns)
  var VALUES = [[1,30],[2,24],[3,16],[4,10],[5,8],[8,5],[10,3],[15,2],["MINI",2.4],["MINOR",0.9],["MAJOR",0.12]];
  var JACK = { MINI: 10, MINOR: 30, MAJOR: 150, GRAND: 1000 };
  var TRIGGER = 6, RESPIN_HIT = 0.07;
  function wpick(t, rnd) { var tot = 0, i; for (i = 0; i < t.length; i++) tot += t[i][1]; var r = rnd() * tot;
    for (i = 0; i < t.length; i++) { r -= t[i][1]; if (r < 0) return t[i][0]; } return t[0][0]; }
  function pick(reel, rnd) {
    var tot = 0, k; for (k in W) tot += W[k][reel];
    var r = rnd() * tot; for (k in W) { r -= W[k][reel]; if (r < 0) return k; } return "9";
  }
  function lanternValue(rnd) { return wpick(VALUES, rnd); }
  /** g[col][row] = {s, v} */
  function spin(rnd) {
    var g = [];
    for (var c = 0; c < 5; c++) { var col = []; for (var r = 0; r < 3; r++) { var s = pick(c, rnd); col.push({ s: s, v: s === "LANTERN" ? lanternValue(rnd) : null }); } g.push(col); }
    return g;
  }
  function evaluate(g) {
    var wins = [];
    for (var l = 0; l < LINES.length; l++) {
      var L = LINES[l], syms = L.map(function (row, col) { return g[col][row].s; });
      var base = null, n = 0;
      for (var i = 0; i < 5; i++) {
        var s = syms[i];
        if (s === "LANTERN") break;
        if (s === "WILD") { n++; continue; }
        if (base === null) { base = s; n++; continue; }
        if (s === base) { n++; continue; }
        break;
      }
      var best = 0, sym = null, cnt = 0;
      if (n >= 3 && base) { best = PAY[base][n - 3]; sym = base; cnt = n; }
      var w = 0; while (w < 5 && syms[w] === "WILD") w++;
      if (w >= 3 && PAY.WILD[w - 3] > best) { best = PAY.WILD[w - 3]; sym = "WILD"; cnt = w; }
      if (best) wins.push({ line: l, sym: sym, count: cnt, pay: best });
    }
    var lanterns = 0;
    for (var c = 0; c < 5; c++) for (var r = 0; r < 3; r++) if (g[c][r].s === "LANTERN") lanterns++;
    return { wins: wins, lanterns: lanterns };
  }
  function valueX(v) { return typeof v === "number" ? v : JACK[v]; }
  return { PAY: PAY, LINES: LINES, VALUES: VALUES, JACK: JACK, TRIGGER: TRIGGER, RESPIN_HIT: RESPIN_HIT,
           spin: spin, evaluate: evaluate, lanternValue: lanternValue, valueX: valueX };
})();
  var LanternSlot = (function () {
  var root = document.getElementById("screen-lantern");
  var BETS = [25, 50, 100, 250, 500, 1000, 2500];
  var fmt = function (n) { return Math.round(n).toLocaleString("en-US"); };
  var store = {
    get: function (k, d) { try { var v = localStorage.getItem("cl." + k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
    set: function (k, v) { try { localStorage.setItem("cl." + k, JSON.stringify(v)); } catch (e) {} }
  };
  var bank = FREEPLAY.buyIn, bet = store.get("bet", 100), fast = store.get("fast", false);
  var stats = store.get("stats", { spins: 0, in: 0, out: 0, bonuses: 0, best: 0 });
  var soundOn = false, busy = false, grid = null, showTimer = null, countRaf = 0;

  // ---------- symbols ----------
  var LANTERN =
    '<svg viewBox="0 0 100 100" aria-hidden="true">' +
    '<circle cx="50" cy="52" r="46" fill="url(#cl-g-glow)"/>' +
    '<path d="M50 2 v8" stroke="#E8B850" stroke-width="2.5"/>' +
    '<rect x="32" y="10" width="36" height="8" rx="2" fill="url(#cl-g-cap)"/>' +
    '<ellipse cx="50" cy="48" rx="36" ry="31" fill="url(#cl-g-lan)"/>' +
    '<path d="M50 17 Q30 48 50 79 M50 17 Q70 48 50 79 M50 17 Q16 48 50 79 M50 17 Q84 48 50 79" stroke="rgba(110,10,6,.55)" stroke-width="1.6" fill="none"/>' +
    '<ellipse cx="40" cy="34" rx="10" ry="6" fill="rgba(255,255,255,.28)"/>' +
    '<rect x="32" y="77" width="36" height="8" rx="2" fill="url(#cl-g-cap)"/>' +
    '<path d="M44 85 v12 M50 85 v14 M56 85 v12" stroke="#E8B850" stroke-width="2"/>' +
    "</svg>";
  var WILD =
    '<svg viewBox="0 0 100 100" aria-hidden="true">' +
    '<circle cx="50" cy="48" r="46" fill="url(#cl-g-glow)"/>' +
    '<path d="M62 14 q8 -10 18 -6" stroke="#2E8A3A" stroke-width="6" fill="none" stroke-linecap="round"/>' +
    '<path d="M52 18 q10 -6 18 2 q4 6 -2 10 l-6 2 z" fill="#3FB050"/>' +
    '<path d="M60 22 C80 32 74 66 46 82 C30 90 14 86 18 78 C34 72 46 58 50 36 C52 26 54 20 60 22 Z" fill="url(#cl-g-chili)" stroke="#5A0604" stroke-width="1.5"/>' +
    '<path d="M58 30 C66 40 60 58 46 70" stroke="rgba(255,255,255,.5)" stroke-width="3.5" fill="none" stroke-linecap="round"/>' +
    '<path d="M16 72 h68 l-5 9 5 9 h-68 l5 -9 z" fill="#FFD255" stroke="#8A5A06" stroke-width="1"/>' +
    '<text x="50" y="86" text-anchor="middle" font-family="Teko, Impact, sans-serif" font-weight="700" font-size="17" letter-spacing="2" fill="#7A0A06">WILD</text>' +
    "</svg>";
  var WOK =
    '<svg viewBox="0 0 100 100" aria-hidden="true">' +
    '<path d="M30 52 q-4 -16 8 -26 q-2 12 8 14 q-4 -16 10 -28 q0 16 12 20 q4 -10 0 -16 q14 10 6 36 z" fill="url(#cl-g-fire)"/>' +
    '<path d="M8 50 h84 q-6 30 -42 30 q-36 0 -42 -30 z" fill="url(#cl-g-wok)" stroke="#0A0C0D" stroke-width="1.5"/>' +
    '<path d="M14 54 q36 10 72 0" stroke="rgba(255,255,255,.35)" stroke-width="2" fill="none"/>' +
    '<rect x="88" y="46" width="12" height="7" rx="3" fill="#6B4A2A"/><rect x="0" y="46" width="12" height="7" rx="3" fill="#6B4A2A"/>' +
    "</svg>";
  var DUMP =
    '<svg viewBox="0 0 100 100" aria-hidden="true">' +
    '<path d="M28 30 q4 -10 10 -2 q6 -12 12 0 q6 -12 12 0 q6 -8 10 2" stroke="rgba(255,255,255,.55)" stroke-width="3" fill="none" stroke-linecap="round"/>' +
    '<ellipse cx="50" cy="40" rx="38" ry="10" fill="#F6E3B4"/>' +
    '<path d="M24 40 q8 -14 16 0 M42 40 q8 -16 16 0 M60 40 q8 -14 16 0" fill="#FFF4D8" stroke="#D8B878" stroke-width="1.2"/>' +
    '<path d="M12 42 h76 v30 q0 8 -8 8 h-60 q-8 0 -8 -8 z" fill="url(#cl-g-bam)" stroke="#6A4410" stroke-width="1.5"/>' +
    '<path d="M12 52 h76 M12 64 h76" stroke="#7A5418" stroke-width="2"/>' +
    "</svg>";
  var CRACK =
    '<svg viewBox="0 0 100 100" aria-hidden="true">' +
    '<path d="M50 18 q6 -10 16 -10" stroke="#C8A060" stroke-width="2.5" fill="none"/>' +
    '<circle cx="68" cy="8" r="5" fill="#FFE98A"/><circle cx="68" cy="8" r="9" fill="rgba(255,200,80,.35)"/>' +
    '<g stroke="#5A0604" stroke-width="1.2"><rect x="22" y="26" width="16" height="56" rx="4" fill="url(#cl-g-crack)"/><rect x="42" y="20" width="16" height="62" rx="4" fill="url(#cl-g-crack)"/><rect x="62" y="26" width="16" height="56" rx="4" fill="url(#cl-g-crack)"/></g>' +
    '<path d="M18 46 h64" stroke="#FFD255" stroke-width="5"/><path d="M18 64 h64" stroke="#FFD255" stroke-width="3"/>' +
    "</svg>";
  var COIN =
    '<svg viewBox="0 0 100 100" aria-hidden="true">' +
    '<circle cx="50" cy="52" r="40" fill="#7A4A06"/><circle cx="50" cy="48" r="40" fill="url(#cl-g-coin)" stroke="#8A5A06" stroke-width="1.5"/>' +
    '<circle cx="50" cy="48" r="30" fill="none" stroke="rgba(120,80,6,.6)" stroke-width="2"/>' +
    '<rect x="40" y="38" width="20" height="20" rx="2" fill="#3A0A04" stroke="#8A5A06" stroke-width="1.5"/>' +
    '<path d="M24 36 q10 -12 22 -14" stroke="rgba(255,255,255,.7)" stroke-width="3" fill="none" stroke-linecap="round"/>' +
    "</svg>";
  function letter(t, grad) {
    var size = t === "10" ? 52 : 66;
    return '<svg viewBox="0 0 100 100" aria-hidden="true">' +
      '<text x="50" y="74" text-anchor="middle" font-family="Teko, Impact, sans-serif" font-weight="700" font-size="' + size + '" fill="rgba(0,0,0,.55)" transform="translate(0 3)">' + t + "</text>" +
      '<text x="50" y="74" text-anchor="middle" font-family="Teko, Impact, sans-serif" font-weight="700" font-size="' + size + '" fill="url(#cl-' + grad + ')" stroke="rgba(255,255,255,.5)" stroke-width="1.2">' + t + "</text></svg>";
  }
  var ART = {
    WILD: WILD, WOK: WOK, DUMP: DUMP, CRACK: CRACK, COIN: COIN, LANTERN: LANTERN,
    A: letter("A", "g-L-gold"), K: letter("K", "g-L-jade"), Q: letter("Q", "g-L-blue"), J: letter("J", "g-L-pink"),
    "10": letter("10", "g-L-gold"), "9": letter("9", "g-L-jade")
  };
  var NAMES = { WILD: "Chili", WOK: "Wok", DUMP: "Dumplings", CRACK: "Firecrackers", COIN: "Lucky coin", A: "A", K: "K", Q: "Q", J: "J", "10": "10", "9": "9" };
  function amtHtml(v) {
    if (v === null || v === undefined) return "";
    if (typeof v === "number") return '<div class="cl-amt">' + fmt(v * bet) + "</div>";
    return '<div class="cl-amt cl-jp">' + v + "</div>";
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
  function gong() { tone(110, 0, 1.2, "sine", 0.12); tone(220, 0, 0.9, "sine", 0.05); tone(331, 0.02, 0.7, "sine", 0.03); }
  function stopClick() { tone(160, 0, 0.07, "square", 0.05); }
  function ding() { tone(1320, 0, 0.25, "triangle", 0.07); tone(1760, 0.06, 0.25, "triangle", 0.05); }
  function winJingle() { [523, 659, 784, 1046].forEach(function (f, i) { tone(f, i * 0.08, 0.2, "triangle", 0.06); }); }

  // ---------- build ----------
  var reels = [], strips = [];
  function build() {
    var host = $("cl-reels");
    for (var c = 0; c < 5; c++) {
      var r = document.createElement("div"); r.className = "cl-reel";
      var s = document.createElement("div"); s.className = "cl-strip";
      r.appendChild(s); host.appendChild(r); reels.push(r); strips.push(s);
    }
    var svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("class", "cl-lines"); svg.id = "cl-lines"; svg.setAttribute("aria-hidden", "true");
    host.appendChild(svg);
  }
  function cellHtml(x) { return '<div class="cl-cell" data-s="' + x.s + '">' + ART[x.s] + amtHtml(x.v) + "</div>"; }
  function setStrip(c, col) {
    strips[c].classList.remove("cl-blur");
    strips[c].innerHTML = col.map(cellHtml).join(""); strips[c].style.transition = "none"; strips[c].style.transform = "translateY(0)";
  }

  function paint() {
    $("cl-bank").textContent = fmt(bank);
    $("cl-betv").textContent = fmt(bet);
    $("cl-jpG").textContent = fmt(bet * LNX.JACK.GRAND); $("cl-jpM").textContent = fmt(bet * LNX.JACK.MAJOR);
    $("cl-jpN").textContent = fmt(bet * LNX.JACK.MINOR); $("cl-jpI").textContent = fmt(bet * LNX.JACK.MINI);
    var i = BETS.indexOf(bet);
    $("cl-betDown").disabled = busy || i <= 0;
    $("cl-betUp").disabled = busy || i >= BETS.length - 1 || BETS[i + 1] > bank;
    $("cl-throw").disabled = busy || bank < bet;
    $("cl-throw").classList.toggle("cl-going", busy);
    $("cl-fast").classList.toggle("cl-on", fast);
    $("cl-sound").classList.toggle("cl-on", soundOn);
    var rtp = stats.in ? (100 * stats.out / stats.in).toFixed(1) + "% back" : "no spins yet";
    $("cl-stats").textContent = fmt(stats.spins) + " spins · " + rtp + " · " + stats.bonuses + " feature" + (stats.bonuses === 1 ? "" : "s") +
      (stats.best ? " · best win " + fmt(stats.best) : "");
    if (bet > bank && bank >= BETS[0]) { for (var j = BETS.length - 1; j >= 0; j--) if (BETS[j] <= bank) { bet = BETS[j]; break; } paint(); }
  }
  function say(big, small, mode) {
    $("cl-msg").className = "cl-msg" + (mode ? " cl-" + mode : "");
    $("cl-msg").innerHTML = '<div class="cl-big">' + big + '</div><div class="cl-small">' + (small || "&nbsp;") + "</div>";
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
  function randCell(c) { return LNX.spin(Math.random)[c][0]; }
  function throwDisc() {
    if (busy || bank < bet) return;
    busy = true; clearInterval(showTimer); clearLines();
    bank -= bet; stats.spins++; stats.in += bet; save();
    $("cl-winv").textContent = "0";
    say("Good luck", "", "quiet");
    paint();
    grid = LNX.spin(Math.random);
    var h = reels[0].getBoundingClientRect().height / 3;
    var base = fast ? 520 : 1240, gap = fast ? 220 : 460;
    for (var c = 0; c < 5; c++) {
      var n = (fast ? 16 : 34) + c * (fast ? 5 : 9), col = [];
      for (var i = 0; i < n; i++) col.push(randCell(c));
      var stripCells = grid[c].concat(col).concat(currentCol(c));
      strips[c].innerHTML = stripCells.map(cellHtml).join("");
      strips[c].classList.add("cl-blur");
      strips[c].style.transition = "none";
      strips[c].style.transform = "translateY(" + (-(stripCells.length - 3) * h) + "px)";
      void strips[c].offsetHeight;
      var dur = base + c * gap;
      strips[c].style.transition = "transform " + dur + "ms cubic-bezier(.12,.55,.22,1.04)";
      strips[c].style.transform = "translateY(0)";
      (function (col, d) {
        setTimeout(function () { strips[col].classList.remove("cl-blur"); }, d - 160);
        setTimeout(function () { setStrip(col, grid[col]); stopClick(); if (col === 4) landed(); }, d);
      })(c, dur);
    }
  }
  var lastCols = [];
  function currentCol(c) { return lastCols[c] || [randCell(c), randCell(c), randCell(c)]; }
  function cellAt(c, r) { return strips[c].children[r]; }
  function clearLines() {
    $("cl-lines").innerHTML = "";
    root.querySelectorAll(".cl-cell").forEach(function (el) { el.classList.remove("cl-hit", "cl-dim"); });
  }
  function showWin(w) {
    clearLines();
    var host = $("cl-reels").getBoundingClientRect(), pts = [], L = LNX.LINES[w.line];
    $("cl-lines").setAttribute("viewBox", "0 0 " + host.width + " " + host.height);
    for (var c = 0; c < 5; c++) for (var r = 0; r < 3; r++) cellAt(c, r).classList.add("cl-dim");
    for (c = 0; c < 5; c++) {
      var el = cellAt(c, L[c]), b = el.getBoundingClientRect();
      pts.push((b.left - host.left + b.width / 2) + "," + (b.top - host.top + b.height / 2));
      if (c < w.count) { el.classList.remove("cl-dim"); el.classList.add("cl-hit"); }
    }
    $("cl-lines").innerHTML = '<polyline points="' + pts.join(" ") + '"/>';
  }
  function landed() {
    lastCols = grid;
    var e = LNX.evaluate(grid), lineBet = bet / 25, total = 0;
    e.wins.forEach(function (w) { total += w.pay * lineBet; });
    total = Math.round(total);
    if (total > 0) {
      bank += total; stats.out += total; stats.best = Math.max(stats.best, total); save();
      var top = e.wins.slice().sort(function (a, b) { return b.pay - a.pay; })[0];
      var x = total / bet, label = x >= 50 ? "MEGA WIN" : x >= 15 ? "BIG WIN" : x >= 5 ? "NICE WIN" : "WIN";
      say(label + " " + fmt(total), e.wins.length + " line" + (e.wins.length > 1 ? "s" : "") + " · " + top.count + " × " + NAMES[top.sym]);
      countUp($("cl-winv"), total, Math.min(1600, 400 + x * 60));
      winJingle();
      var i = 0; showWin(e.wins[0]);
      if (e.wins.length > 1 && e.lanterns < LNX.TRIGGER) showTimer = setInterval(function () { i = (i + 1) % e.wins.length; showWin(e.wins[i]); }, 1200);
    } else if (e.lanterns < LNX.TRIGGER) {
      say(e.lanterns >= 4 ? e.lanterns + " lanterns — so close" : "No win", e.lanterns >= 4 ? "6 start Hold & Spin" : "", "quiet");
    }
    if (e.lanterns >= LNX.TRIGGER) {
      clearLines(); clearInterval(showTimer);
      for (var c = 0; c < 5; c++) for (var r = 0; r < 3; r++) if (grid[c][r].s === "LANTERN") cellAt(c, r).classList.add("cl-hit");
      say(e.lanterns + " LANTERNS!", "Hold & Spin", "bonus");
      gong();
      setTimeout(function () { startHold(total); }, 1500);
      return;
    }
    busy = false; paint();
  }

  // ---------- Hold & Spin ----------
  var H = null;
  function hcellHtml(v, fresh) {
    return v === null ? "" : ART.LANTERN + amtHtml(v);
  }
  function paintHold() {
    var host = $("cl-hold"); host.innerHTML = "";
    for (var k = 0; k < 15; k++) {
      var d = document.createElement("div");
      d.className = "cl-hcell" + (H.cells[k] !== null ? " cl-locked" : "") + (H.spinning && H.cells[k] === null ? " cl-spinning" : "") + (H.fresh[k] ? " cl-fresh" : "");
      d.innerHTML = hcellHtml(H.cells[k]);
      host.appendChild(d);
    }
    var dots = $("cl-spinsLeft").querySelectorAll("i");
    for (var i = 0; i < 3; i++) dots[i].classList.toggle("cl-on", i < H.left);
    var sum = 0; H.cells.forEach(function (v) { if (v !== null) sum += LNX.valueX(v) * bet; });
    $("cl-hsum").textContent = "$" + fmt(sum);
  }
  function flash(sub, title, num, ms, then) {
    $("cl-flashS").textContent = sub; $("cl-flashT").textContent = title; $("cl-flashN").textContent = num || "";
    $("cl-flash").hidden = false;
    setTimeout(function () { $("cl-flash").hidden = true; if (then) then(); }, ms);
  }
  function startHold(lineWin) {
    stats.bonuses++; save();
    // grid is [col][row]; the hold grid reads row by row
    var cells = [];
    for (var r = 0; r < 3; r++) for (var c = 0; c < 5; c++) cells.push(grid[c][r].s === "LANTERN" ? grid[c][r].v : null);
    H = { cells: cells, left: 3, lineWin: lineWin, fresh: [], spinning: false };
    flash("HOLD & SPIN", "LANTERNS!", "3 SPINS · NEW LANTERNS RESET THEM", 1800, function () {
      $("cl-hold").hidden = false; $("cl-hud").hidden = false;
      say("Lanterns lock in place", "Every new lantern resets you to 3 spins", "bonus");
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
      for (var k = 0; k < 15; k++) if (H.cells[k] === null && Math.random() < LNX.RESPIN_HIT) { H.cells[k] = LNX.lanternValue(Math.random); H.fresh[k] = true; hit = true; }
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
    H.cells.forEach(function (v) { if (v !== null) { sum += LNX.valueX(v) * bet; if (typeof v !== "number") jps.push(v); } });
    if (full) { sum += LNX.JACK.GRAND * bet; jps.push("GRAND"); }
    sum = Math.round(sum);
    bank += sum; stats.out += sum;
    var total = sum + H.lineWin;
    stats.best = Math.max(stats.best, total); save();
    gong();
    flash(full ? "ALL 15 FILLED" : "HOLD & SPIN PAYS", full ? "GRAND!" : jps.length ? jps[jps.length - 1] + "!" : "COLLECT", "$" + fmt(sum), 2400, function () {
      $("cl-hold").hidden = true; $("cl-hud").hidden = true;
      countUp($("cl-winv"), total, 1400);
      var n = H.cells.filter(function (v) { return v !== null; }).length;
      say("LANTERNS PAY " + fmt(sum), n + " of 15 lanterns" + (jps.length ? " · " + jps.join(", ") : ""), "bonus");
      H = null; busy = false; paint();
    });
  }

  // ---------- pay table ----------
  function paintPays() {
    var lb = bet / 25, rows = [];
    ["WILD", "WOK", "DUMP", "CRACK", "COIN", "A", "K", "Q", "J", "10", "9"].forEach(function (s) {
      rows.push('<div class="cl-row"><div class="cl-ic">' + ART[s] + '</div><div class="cl-pays">' +
        LNX.PAY[s].map(function (v, i) { return "<span>" + (i + 3) + "×</span><span>" + fmt(v * lb) + "</span>"; }).reverse().join("") + "</div></div>");
    });
    rows.push('<div class="cl-row"><div class="cl-ic">' + ART.LANTERN + '</div><div class="cl-pays"><span>6+</span><span>HOLD &amp; SPIN</span></div></div>');
    $("cl-pt").innerHTML = rows.join("");
  }

  // ---------- wiring ----------
  $("cl-throw").addEventListener("click", throwDisc);
  document.addEventListener("keydown", function (e) {
    if (e.code !== "Space" || !root.classList.contains("on")) return;
    if ($("cl-sheet").hidden) { e.preventDefault(); throwDisc(); }
  });
  $("cl-betDown").addEventListener("click", function () { var i = BETS.indexOf(bet); if (!busy && i > 0) { bet = BETS[i - 1]; store.set("bet", bet); paint(); } });
  $("cl-betUp").addEventListener("click", function () { var i = BETS.indexOf(bet); if (!busy && i < BETS.length - 1 && BETS[i + 1] <= bank) { bet = BETS[i + 1]; store.set("bet", bet); paint(); } });
  $("cl-fast").addEventListener("click", function () { fast = !fast; store.set("fast", fast); paint(); });
  $("cl-sound").addEventListener("click", function () {
    soundOn = !soundOn;
    if (soundOn && !ac) { try { ac = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { soundOn = false; } }
    if (ac && ac.state === "suspended") ac.resume();
    paint(); if (soundOn) ding();
  });
  $("cl-reset").addEventListener("click", function () {
    if (busy) return;
    freshStack(function (v) { bank = v; }); stats = { spins: 0, in: 0, out: 0, bonuses: 0, best: 0 }; save();
    $("cl-winv").textContent = "0"; clearLines(); clearInterval(showTimer);
    say("Fresh $" + fmt(bank), "Stats cleared", "quiet"); paint();
  });
  $("cl-paysBtn").addEventListener("click", function () { paintPays(); $("cl-sheet").hidden = false; });
  $("cl-sheetX").addEventListener("click", function () { $("cl-sheet").hidden = true; });
  $("cl-sheet").addEventListener("click", function (e) { if (e.target === $("cl-sheet")) $("cl-sheet").hidden = true; });

  $("cl-back").addEventListener("click", function () { if (!busy) Shell.show("othermenu"); });
  function enter() {
    Shell.mode = "testing";
    bank = FREEPLAY.buyIn;
    clearInterval(showTimer); clearLines();
    $("cl-winv").textContent = "0";
    say("Good luck", "6 lanterns start Hold & Spin", "quiet");
    paint();
  }
  $("om-lantern").addEventListener("click", function () { enter(); Shell.show("lantern"); });
  // Other Games lists the slot only on the Play Testing side; the campaign and tournament never see it
  var show = Shell.show;
  Shell.show = function (name) {
    if (name === "othermenu") $("om-lantern").style.display = Shell.mode === "testing" ? "" : "none";
    return show.apply(this, arguments);
  };
  build();
  var first = LNX.spin(Math.random);
  for (var c = 0; c < 5; c++) setStrip(c, first[c]);
  lastCols = first;
  if (BETS.indexOf(bet) < 0) bet = 100;
  paint();
  return { enter: enter, busy: function () { return busy; } };
})();
