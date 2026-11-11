  /* ============================================================
     CHAIN REACTION — the disc golf slot, play testing only
     ============================================================ */
// Chain Reaction engine: shared by the page and the simulation.
  var CHX = (function () {
  var SYMS = ["ACE","GOLD","PINK","BLUE","TREE","A","K","Q","J","10","BASKET"];
  var PAY = { ACE:[110,550,2500], GOLD:[65,240,1000], PINK:[45,165,650], BLUE:[35,120,450], TREE:[28,85,330],
              A:[17,55,165], K:[16,50,155], Q:[11,38,120], J:[11,33,100], "10":[11,28,85] };
  // weights per reel; ACE (wild) only on reels 2-4
  var W = {
    ACE:[0,6,6,6,0], GOLD:[4,4,4,4,4], PINK:[6,6,6,6,6], BLUE:[8,8,8,8,8], TREE:[10,10,10,10,10],
    A:[13,13,13,13,13], K:[13,13,13,13,13], Q:[15,15,15,15,15], J:[15,15,15,15,15], "10":[15,15,15,15,15],
    BASKET:[3.1,3.1,3.1,3.1,3.1]
  };
  var LINES = [
    [1,1,1,1,1],[0,0,0,0,0],[2,2,2,2,2],[0,1,2,1,0],[2,1,0,1,2],
    [0,0,1,2,2],[2,2,1,0,0],[1,0,0,0,1],[1,2,2,2,1],[1,0,1,2,1],
    [1,2,1,0,1],[0,1,1,1,0],[2,1,1,1,2],[0,1,0,1,0],[2,1,2,1,2],
    [1,1,0,1,1],[1,1,2,1,1],[0,0,2,0,0],[2,2,0,2,2],[0,2,0,2,0],
    [2,0,2,0,2],[1,0,2,0,1],[1,2,0,2,1],[0,2,2,2,0],[2,0,0,0,2]
  ];
  var BONUS_START = { 3: 5, 4: 15, 5: 50 };      // × total bet, on landing
  var PUTTS = [ { ft: 10, pay: 10, zone: 0.24, speed: 1.0 },
                { ft: 20, pay: 30, zone: 0.14, speed: 1.35 },
                { ft: 30, pay: 150, zone: 0.07, speed: 1.75 } ];   // × total bet
  function pick(reel, rnd) {
    var tot = 0, k;
    for (k in W) tot += W[k][reel];
    var r = rnd() * tot;
    for (k in W) { r -= W[k][reel]; if (r < 0) return k; }
    return "10";
  }
  function spin(rnd) {
    var g = [];
    for (var c = 0; c < 5; c++) { g.push([pick(c, rnd), pick(c, rnd), pick(c, rnd)]); }
    return g;   // g[col][row]
  }
  /** Line wins (in line-bet units) and the scatter count. */
  function evaluate(g) {
    var wins = [];
    for (var l = 0; l < LINES.length; l++) {
      var L = LINES[l], syms = L.map(function (row, col) { return g[col][row]; });
      var base = null, n = 0;
      for (var i = 0; i < 5; i++) {
        var s = syms[i];
        if (s === "BASKET") break;
        if (s === "ACE") { n++; continue; }
        if (base === null) { base = s; n++; continue; }
        if (s === base) { n++; continue; }
        break;
      }
      // all wilds, or wilds then a symbol: pay the better of the two readings
      var best = 0, sym = null, cnt = 0;
      if (n >= 3 && base) { best = PAY[base][n - 3]; sym = base; cnt = n; }
      var w = 0; while (w < 5 && syms[w] === "ACE") w++;
      if (w >= 3 && PAY.ACE[w - 3] > best) { best = PAY.ACE[w - 3]; sym = "ACE"; cnt = w; }
      if (best) wins.push({ line: l, sym: sym, count: cnt, pay: best });
    }
    var scat = 0;
    for (var c = 0; c < 5; c++) for (var r = 0; r < 3; r++) if (g[c][r] === "BASKET") scat++;
    return { wins: wins, scatters: scat };
  }
  return { SYMS: SYMS, PAY: PAY, LINES: LINES, BONUS_START: BONUS_START, PUTTS: PUTTS, spin: spin, evaluate: evaluate };
})();
  var ChainSlot = (function () {
  var root = document.getElementById("screen-chain");
  var BETS = [25, 50, 100, 250, 500, 1000, 2500];
  var fmt = function (n) { return Math.round(n).toLocaleString("en-US"); };
  var store = {
    get: function (k, d) { try { var v = localStorage.getItem("cr." + k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
    set: function (k, v) { try { localStorage.setItem("cr." + k, JSON.stringify(v)); } catch (e) {} }
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
    A: letter("A", "g-L-red"), K: letter("K", "g-L-blue"), Q: letter("Q", "g-L-purple"), J: letter("J", "g-L-green"), "10": letter("10", "g-L-orange")
  };
  var NAMES = { ACE: "Ace", GOLD: "Driver", PINK: "Midrange", BLUE: "Putter", TREE: "Tree", A: "A", K: "K", Q: "Q", J: "J", "10": "10" };

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
  function chains() { for (var i = 0; i < 16; i++) tone(2400 + Math.random() * 2600, i * 0.025 + Math.random() * 0.02, 0.09, "square", 0.022); }
  function stopClick() { tone(160, 0, 0.07, "square", 0.05); }
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
  function cellHtml(sym) { return '<div class="ch-cell" data-s="' + sym + '">' + ART[sym] + "</div>"; }
  function setStrip(c, syms) {
    strips[c].classList.remove("ch-blur");
    strips[c].innerHTML = syms.map(cellHtml).join(""); strips[c].style.transition = "none"; strips[c].style.transform = "translateY(0)";
  }

  function paint() {
    $("ch-bank").textContent = fmt(bank);
    $("ch-betv").textContent = fmt(bet);
    $("ch-jp0").textContent = fmt(bet * CHX.PUTTS[0].pay);
    $("ch-jp1").textContent = fmt(bet * CHX.PUTTS[1].pay);
    $("ch-jp2").textContent = fmt(bet * CHX.PUTTS[2].pay);
    var i = BETS.indexOf(bet);
    $("ch-betDown").disabled = busy || i <= 0;
    $("ch-betUp").disabled = busy || i >= BETS.length - 1 || BETS[i + 1] > bank;
    $("ch-throw").disabled = busy || bank < bet;
    $("ch-throw").classList.toggle("ch-going", busy);
    $("ch-fast").classList.toggle("ch-on", fast);
    $("ch-sound").classList.toggle("ch-on", soundOn);
    var rtp = stats.in ? (100 * stats.out / stats.in).toFixed(1) + "% back" : "no spins yet";
    $("ch-stats").textContent = fmt(stats.spins) + " spins · " + rtp + " · " + stats.bonuses + " bonus" + (stats.bonuses === 1 ? "" : "es") +
      (stats.best ? " · best win " + fmt(stats.best) : "");
    if (bet > bank && bank >= BETS[0]) { for (var j = BETS.length - 1; j >= 0; j--) if (BETS[j] <= bank) { bet = BETS[j]; break; } paint(); }
  }
  function say(big, small, mode) {
    $("ch-msg").className = "ch-msg" + (mode ? " ch-" + mode : "");
    $("ch-msg").innerHTML = '<div class="ch-big">' + big + '</div><div class="ch-small">' + (small || "&nbsp;") + "</div>";
  }
  function countUp(el, to, ms, prefix) {
    cancelAnimationFrame(countRaf);
    var t0 = performance.now();
    var step = function (now) {
      var k = Math.min(1, (now - t0) / ms), v = Math.round(to * (1 - Math.pow(1 - k, 3)));
      el.textContent = (prefix || "") + fmt(v);
      if (k < 1) countRaf = requestAnimationFrame(step);
    };
    countRaf = requestAnimationFrame(step);
  }
  function save() { store.set("stats", stats); }

  // ---------- spin ----------
  function randSym(c) { return CHX.spin(Math.random)[c][0]; }
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
      var n = (fast ? 16 : 34) + c * (fast ? 5 : 9), syms = [];
      for (var i = 0; i < n; i++) syms.push(randSym(c));
      var stripSyms = grid[c].concat(syms).concat(currentCol(c));
      strips[c].innerHTML = stripSyms.map(cellHtml).join("");
      strips[c].classList.add("ch-blur");
      strips[c].style.transition = "none";
      strips[c].style.transform = "translateY(" + (-(stripSyms.length - 3) * h) + "px)";
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
  function currentCol(c) {
    var out = [], kids = strips[c].children;
    for (var i = 0; i < 3 && i < kids.length; i++) out.push(kids[i].dataset.s);
    return out.length === 3 ? out : [randSym(c), randSym(c), randSym(c)];
  }
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
    var e = CHX.evaluate(grid), lineBet = bet / 25, total = 0;
    e.wins.forEach(function (w) { total += w.pay * lineBet; });
    total = Math.round(total);
    var baskets = e.scatters;
    if (total > 0) {
      bank += total; stats.out += total; stats.best = Math.max(stats.best, total); save();
      var top = e.wins.slice().sort(function (a, b) { return b.pay - a.pay; })[0];
      var x = total / bet, label = x >= 50 ? "MEGA WIN" : x >= 15 ? "BIG WIN" : x >= 5 ? "NICE WIN" : "WIN";
      say(label + " " + fmt(total), e.wins.length + " line" + (e.wins.length > 1 ? "s" : "") + " · " + top.count + " × " + NAMES[top.sym]);
      countUp($("ch-winv"), total, Math.min(1600, 400 + x * 60));
      winJingle();
      var i = 0; showWin(e.wins[0]);
      if (e.wins.length > 1) showTimer = setInterval(function () { i = (i + 1) % e.wins.length; showWin(e.wins[i]); }, 1200);
    } else if (baskets < 3) {
      say(baskets === 2 ? "2 baskets — so close" : "No win", "", "quiet");
    }
    if (baskets >= 3) {
      for (var c = 0; c < 5; c++) for (var r = 0; r < 3; r++) if (grid[c][r] === "BASKET") cellAt(c, r).classList.add("ch-hit");
      say("CHAIN RATTLE!", baskets + " baskets", "bonus");
      chains();
      setTimeout(function () { startBonus(Math.min(5, baskets), total); }, 1500);
      return;
    }
    busy = false; paint();
  }

  // ---------- Chain Rattle ----------
  var B = null, raf = 0, RUNG = ["PAR", "BIRDIE", "ACE"];
  function startBonus(n, lineWin) {
    stats.bonuses++; save();
    B = { won: CHX.BONUS_START[n] * bet, step: 0, results: [], lineWin: lineWin, moving: false };
    bank += B.won; stats.out += B.won; save();
    $("ch-basketBig").innerHTML = BASKET;
    $("ch-bonus").hidden = false;
    $("ch-bNote").textContent = n + " baskets pay " + CHX.BONUS_START[n] + "× to start. Stop the needle in the green.";
    paintBonus();
    setupPutt();
  }
  function paintBonus() {
    $("ch-bBank").textContent = "$" + fmt(B.won);
    $("ch-ladder").innerHTML = CHX.PUTTS.map(function (p, i) {
      var cls = B.results[i] === true ? "ch-made" : B.results[i] === false ? "ch-miss" : i === B.step ? "ch-now" : "";
      return '<div class="ch-rung ' + cls + '"><div class="ch-nm">' + RUNG[i] + '</div><div class="ch-ft">' + p.ft + ' FT</div><div class="ch-p">+' + fmt(p.pay * bet) + "</div></div>";
    }).join("");
  }
  function setupPutt() {
    var p = CHX.PUTTS[B.step];
    var left = 0.12 + Math.random() * (0.76 - p.zone);
    B.zone = [left, left + p.zone];
    $("ch-zone").style.left = (left * 100) + "%"; $("ch-zone").style.width = (p.zone * 100) + "%";
    $("ch-putt").disabled = false; $("ch-putt").textContent = "PUTT " + p.ft + " FT";
    B.moving = true; B.t0 = performance.now(); B.speed = p.speed;
    cancelAnimationFrame(raf);
    var tick = function (now) {
      if (!B || !B.moving) return;
      var t = (now - B.t0) / 1000 * B.speed * 0.9;
      B.pos = 0.5 - 0.5 * Math.cos(t * Math.PI);
      $("ch-needle").style.left = (B.pos * 100) + "%";
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
  }
  function putt() {
    if (!B || !B.moving) return;
    B.moving = false; cancelAnimationFrame(raf);
    $("ch-putt").disabled = true;
    var made = B.pos >= B.zone[0] && B.pos <= B.zone[1], p = CHX.PUTTS[B.step];
    B.results[B.step] = made;
    if (made) {
      var add = p.pay * bet; B.won += add; bank += add; stats.out += add; save();
      $("ch-basketBig").classList.remove("ch-rattle"); void $("ch-basketBig").offsetWidth; $("ch-basketBig").classList.add("ch-rattle");
      chains();
      $("ch-bNote").textContent = RUNG[B.step] + "! +" + fmt(add);
      B.step++;
      paintBonus();
      if (B.step < CHX.PUTTS.length) setTimeout(function () { $("ch-bNote").textContent = "Next up: " + CHX.PUTTS[B.step].ft + " feet. The green shrinks."; setupPutt(); }, 1400);
      else setTimeout(endBonus, 1800);
    } else {
      $("ch-bNote").textContent = "Spit out. The bonus is over.";
      tone(130, 0, 0.35, "sawtooth", 0.05);
      paintBonus();
      setTimeout(endBonus, 1600);
    }
  }
  function endBonus() {
    var total = B.won + B.lineWin;
    stats.best = Math.max(stats.best, total); save();
    $("ch-bonus").hidden = true;
    countUp($("ch-winv"), total, 1400);
    say("CHAIN RATTLE " + fmt(B.won), B.results.filter(Boolean).length + " of 3 putts made", "bonus");
    B = null; busy = false; paint();
  }

  // ---------- pay table ----------
  function paintPays() {
    var lb = bet / 25, rows = [];
    ["ACE", "GOLD", "PINK", "BLUE", "TREE", "A", "K", "Q", "J", "10"].forEach(function (s) {
      rows.push('<div class="ch-row"><div class="ch-ic">' + ART[s] + '</div><div class="ch-pays">' +
        CHX.PAY[s].map(function (v, i) { return "<span>" + (5 - i === 5 ? "" : "") + (i + 3) + "×</span><span>" + fmt(v * lb) + "</span>"; }).reverse().join("") + "</div></div>");
    });
    rows.push('<div class="ch-row"><div class="ch-ic">' + BASKET + '</div><div class="ch-pays">' +
      [[5, 50], [4, 15], [3, 5]].map(function (m) { return "<span>" + m[0] + "×</span><span>" + fmt(m[1] * bet) + "</span>"; }).join("") + "</div></div>");
    $("ch-pt").innerHTML = rows.join("");
  }

  // ---------- wiring ----------
  $("ch-throw").addEventListener("click", throwDisc);
  document.addEventListener("keydown", function (e) {
    if (e.code !== "Space" || !root.classList.contains("on")) return;
    if (!$("ch-bonus").hidden) { e.preventDefault(); putt(); }
    else if ($("ch-sheet").hidden) { e.preventDefault(); throwDisc(); }
  });
  $("ch-betDown").addEventListener("click", function () { var i = BETS.indexOf(bet); if (!busy && i > 0) { bet = BETS[i - 1]; store.set("bet", bet); paint(); } });
  $("ch-betUp").addEventListener("click", function () { var i = BETS.indexOf(bet); if (!busy && i < BETS.length - 1 && BETS[i + 1] <= bank) { bet = BETS[i + 1]; store.set("bet", bet); paint(); } });
  $("ch-putt").addEventListener("click", putt);
  $("ch-fast").addEventListener("click", function () { fast = !fast; store.set("fast", fast); paint(); });
  $("ch-sound").addEventListener("click", function () {
    soundOn = !soundOn;
    if (soundOn && !ac) { try { ac = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { soundOn = false; } }
    if (ac && ac.state === "suspended") ac.resume();
    paint(); if (soundOn) winJingle();
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

  $("ch-back").addEventListener("click", function () { if (!busy) Shell.show("menu"); });
  function enter() {
    Shell.mode = "testing";
    bank = FREEPLAY.buyIn;
    clearInterval(showTimer); clearLines();
    $("ch-winv").textContent = "0";
    say("Good luck", "3 baskets anywhere start the Chain Rattle bonus", "quiet");
    paint();
  }
  $("mm-chain").addEventListener("click", function () { enter(); Shell.show("chain"); });
  build();
  var first = CHX.spin(Math.random);
  for (var c = 0; c < 5; c++) setStrip(c, first[c]);
  if (BETS.indexOf(bet) < 0) bet = 100;
  paint();
  return { enter: enter, busy: function () { return busy; } };
})();
