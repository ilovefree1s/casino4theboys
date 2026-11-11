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
  var BETS = [25, 50, 100, 250, 500, 1000];
  var fmt = function (n) { return Math.round(n).toLocaleString("en-US"); };
  var store = {
    get: function (k, d) { try { var v = localStorage.getItem("cr." + k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
    set: function (k, v) { try { localStorage.setItem("cr." + k, JSON.stringify(v)); } catch (e) {} }
  };
  var bank = FREEPLAY.buyIn, bet = store.get("bet", 100), fast = store.get("fast", false);
  var stats = store.get("stats", { spins: 0, in: 0, out: 0, bonuses: 0, best: 0 });
  var soundOn = false, busy = false, grid = null, showTimer = null;

  // ---------- symbols ----------
  var DISC = function (c, label) {
    return '<svg viewBox="0 0 60 60" aria-hidden="true"><ellipse cx="30" cy="35" rx="27" ry="14" fill="' + c + '" opacity=".75"/>' +
      '<ellipse cx="30" cy="31" rx="27" ry="14" fill="' + c + '"/><ellipse cx="30" cy="31" rx="16" ry="7.5" fill="none" stroke="rgba(255,255,255,.55)" stroke-width="1.8"/>' +
      '<ellipse cx="24" cy="26" rx="8" ry="2.6" fill="rgba(255,255,255,.5)"/>' +
      (label ? '<text x="30" y="36" text-anchor="middle" font-family="Oswald, Impact, sans-serif" font-weight="700" font-size="13" fill="#3A2600">' + label + '</text>' : "") + "</svg>";
  };
  var BASKET = '<svg viewBox="0 0 50 62" aria-hidden="true"><rect x="23" y="4" width="4" height="56" fill="#8E979C"/><ellipse cx="25" cy="8" rx="16" ry="4" fill="#E8B72C"/>' +
    '<path d="M11 9 L17 30 M17 9 L21 30 M25 9 L25 30 M33 9 L29 30 M39 9 L33 30" stroke="#C3CBD0" stroke-width="1.6"/>' +
    '<path d="M9 30 h32 l-4 12 h-24 z" fill="#E8B72C"/><ellipse cx="25" cy="30" rx="16" ry="3" fill="#B98C14"/><rect x="18" y="58" width="14" height="3" rx="1" fill="#5E676C"/></svg>';
  var TREE = '<svg viewBox="0 0 44 60" aria-hidden="true"><path d="M22 2 L40 30 H30 L42 46 H2 L14 30 H4 Z" fill="#2E9E52"/><path d="M22 2 L40 30 H30 L42 46 H22 Z" fill="#23803F"/><rect x="19" y="46" width="6" height="12" fill="#6B4A2A"/></svg>';
  var LETTER = function (t, c) {
    return '<svg viewBox="0 0 60 60" aria-hidden="true"><text x="30" y="44" text-anchor="middle" font-family="Oswald, Impact, sans-serif" font-weight="700" font-size="' + (t === "10" ? 34 : 42) + '" fill="' + c + '" stroke="rgba(0,0,0,.25)" stroke-width="1">' + t + "</text></svg>";
  };
  var ART = {
    ACE: '<svg viewBox="0 0 60 60" aria-hidden="true"><circle cx="30" cy="30" r="27" fill="#1A1206"/><circle cx="30" cy="30" r="24" fill="#FFD24D"/><circle cx="30" cy="30" r="17" fill="none" stroke="#B88A10" stroke-width="2"/>' +
      '<text x="30" y="37" text-anchor="middle" font-family="Oswald, Impact, sans-serif" font-weight="700" font-size="18" fill="#3A2600">ACE</text></svg>',
    GOLD: DISC("#F0A818", ""), PINK: DISC("#E0457B", ""), BLUE: DISC("#2C8BE0", ""),
    TREE: TREE, BASKET: BASKET,
    A: LETTER("A", "#C8102E"), K: LETTER("K", "#2C6FD1"), Q: LETTER("Q", "#7A3FC8"), J: LETTER("J", "#1E8C4A"), "10": LETTER("10", "#D2541A")
  };
  var NAMES = { ACE: "Ace", GOLD: "Gold driver", PINK: "Pink midrange", BLUE: "Blue putter", TREE: "Tree", A: "A", K: "K", Q: "Q", J: "J", "10": "10" };

  // ---------- sound (starts only after the player turns it on) ----------
  var ac = null;
  function tone(f, t0, dur, type, vol) {
    if (!soundOn || !ac) return;
    var o = ac.createOscillator(), g = ac.createGain();
    o.type = type || "triangle"; o.frequency.value = f;
    g.gain.setValueAtTime(vol || 0.06, ac.currentTime + t0);
    g.gain.exponentialRampToValueAtTime(0.0001, ac.currentTime + t0 + dur);
    o.connect(g); g.connect(ac.destination); o.start(ac.currentTime + t0); o.stop(ac.currentTime + t0 + dur + 0.02);
  }
  function chains() {
    if (!soundOn || !ac) return;
    for (var i = 0; i < 14; i++) tone(2400 + Math.random() * 2600, i * 0.025 + Math.random() * 0.02, 0.09, "square", 0.025);
  }
  function stopClick() { tone(180, 0, 0.06, "square", 0.05); }
  function winJingle() { [523, 659, 784, 1046].forEach(function (f, i) { tone(f, i * 0.08, 0.18, "triangle", 0.06); }); }

  // ---------- build ----------
  var reels = [], strips = [];
  function build() {
    var host = $("ch-reels");
    for (var c = 0; c < 5; c++) {
      var r = document.createElement("div"); r.className = "reel";
      var s = document.createElement("div"); s.className = "strip";
      r.appendChild(s); host.appendChild(r); reels.push(r); strips.push(s);
    }
    var svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("class", "lines"); svg.id = "ch-lines"; svg.setAttribute("aria-hidden", "true");
    host.appendChild(svg);
    BETS.forEach(function (b) {
      var el = document.createElement("button"); el.type = "button"; el.className = "bet"; el.textContent = b >= 1000 ? (b / 1000) + "K" : b;
      el.dataset.v = b; el.setAttribute("aria-label", "Bet " + b);
      el.addEventListener("click", function () { if (busy) return; bet = b; store.set("bet", bet); paint(); });
      $("ch-bets").appendChild(el);
    });
  }
  function cellHtml(sym) { return '<div class="cell" data-s="' + sym + '">' + ART[sym] + "</div>"; }
  function setStrip(c, syms) { strips[c].innerHTML = syms.map(cellHtml).join(""); strips[c].style.transition = "none"; strips[c].style.transform = "translateY(0)"; }
  function cellH() { return reels[0].getBoundingClientRect().height / 3 + (5 / 3) - 5 / 3; }

  function paint() {
    $("ch-bank").textContent = fmt(bank);
    $("ch-betv").textContent = fmt(bet);
    $("ch-jp0").textContent = fmt(bet * CHX.PUTTS[0].pay);
    $("ch-jp1").textContent = fmt(bet * CHX.PUTTS[1].pay);
    $("ch-jp2").textContent = fmt(bet * CHX.PUTTS[2].pay);
    root.querySelectorAll(".bet").forEach(function (b) {
      var v = +b.dataset.v; b.classList.toggle("on", v === bet); b.disabled = busy || v > bank;
    });
    $("ch-throw").disabled = busy || bank < bet;
    $("ch-fast").textContent = "FAST SPIN · " + (fast ? "ON" : "OFF"); $("ch-fast").classList.toggle("on", fast);
    $("ch-sound").textContent = "SOUND · " + (soundOn ? "ON" : "OFF"); $("ch-sound").classList.toggle("on", soundOn);
    var rtp = stats.in ? (100 * stats.out / stats.in).toFixed(1) + "% back" : "—";
    $("ch-stats").textContent = fmt(stats.spins) + " spins · bet " + fmt(stats.in) + " · won " + fmt(stats.out) + " · " + rtp +
      " · " + stats.bonuses + " bonus" + (stats.bonuses === 1 ? "" : "es") + (stats.best ? " · best " + fmt(stats.best) : "");
    if (bet > bank && bank >= BETS[0]) { for (var i = BETS.length - 1; i >= 0; i--) if (BETS[i] <= bank) { bet = BETS[i]; break; } paint(); }
  }
  function say(big, small, lose) {
    $("ch-msg").className = "msg" + (lose ? " lose" : "");
    $("ch-msg").innerHTML = '<div class="big">' + big + '</div><div class="small">' + (small || "") + "</div>";
  }
  function save() { store.set("stats", stats); }

  // ---------- spin ----------
  function randSym(c) { return CHX.spin(Math.random)[c][0]; }
  function throwDisc() {
    if (busy || bank < bet) return;
    busy = true; clearInterval(showTimer); clearLines();
    bank -= bet; stats.spins++; stats.in += bet; save();
    $("ch-winv").textContent = "0";
    say("…", "", true);
    paint();
    grid = CHX.spin(Math.random);
    var h = reels[0].getBoundingClientRect().height, step = (h - 10) / 3 + 5;
    var base = fast ? 260 : 620, gap = fast ? 110 : 230;
    for (var c = 0; c < 5; c++) {
      var n = (fast ? 10 : 18) + c * (fast ? 3 : 5), syms = [];
      for (var i = 0; i < n; i++) syms.push(randSym(c));
      // the stop sits at the top of the strip; the strip starts scrolled down to its tail
      var stripSyms = grid[c].concat(syms).concat(strips[c].children.length ? currentCol(c) : [randSym(c), randSym(c), randSym(c)]);
      strips[c].innerHTML = stripSyms.map(cellHtml).join("");
      var start = -(stripSyms.length - 3) * step;
      strips[c].style.transition = "none";
      strips[c].style.transform = "translateY(" + start + "px)";
      void strips[c].offsetHeight;
      var dur = base + c * gap;
      strips[c].style.transition = "transform " + dur + "ms cubic-bezier(.2,.6,.25,1.06)";
      strips[c].style.transform = "translateY(0)";
      (function (col, d) { setTimeout(function () { setStrip(col, grid[col]); stopClick(); if (col === 4) landed(); }, d); })(c, dur);
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
    root.querySelectorAll(".cell").forEach(function (el) { el.classList.remove("hit", "dim"); });
  }
  function showWin(w) {
    clearLines();
    var host = $("ch-reels").getBoundingClientRect(), pts = [], L = CHX.LINES[w.line];
    $("ch-lines").setAttribute("viewBox", "0 0 " + host.width + " " + host.height);
    for (var c = 0; c < 5; c++) {
      for (var r = 0; r < 3; r++) cellAt(c, r).classList.add("dim");
    }
    for (c = 0; c < 5; c++) {
      var el = cellAt(c, L[c]), b = el.getBoundingClientRect();
      pts.push((b.left - host.left + b.width / 2) + "," + (b.top - host.top + b.height / 2));
      if (c < w.count) { el.classList.remove("dim"); el.classList.add("hit"); }
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
      $("ch-winv").textContent = fmt(total); $("ch-last").textContent = fmt(total);
      var top = e.wins.slice().sort(function (a, b) { return b.pay - a.pay; })[0];
      say("WIN " + fmt(total), e.wins.length + " line" + (e.wins.length > 1 ? "s" : "") + " · best: " + top.count + " × " + NAMES[top.sym]);
      winJingle();
      var i = 0; showWin(e.wins[0]);
      if (e.wins.length > 1) showTimer = setInterval(function () { i = (i + 1) % e.wins.length; showWin(e.wins[i]); }, 1100);
    } else if (baskets < 3) {
      say(baskets === 2 ? "So close — 2 baskets" : "No win", "", true);
    }
    if (baskets >= 3) {
      for (var c = 0; c < 5; c++) for (var r = 0; r < 3; r++) if (grid[c][r] === "BASKET") cellAt(c, r).classList.add("hit");
      chains();
      setTimeout(function () { startBonus(Math.min(5, baskets), total); }, 1100);
      return;
    }
    busy = false; paint();
  }

  // ---------- Chain Rattle ----------
  var B = null, raf = 0;
  function startBonus(n, lineWin) {
    stats.bonuses++; save();
    B = { won: CHX.BONUS_START[n] * bet, step: 0, results: [], lineWin: lineWin, moving: false };
    bank += B.won; stats.out += B.won; save();
    $("ch-basketBig").innerHTML = BASKET;
    $("ch-bonus").hidden = false;
    $("ch-bNote").textContent = n + " baskets: " + CHX.BONUS_START[n] + "× your bet to start. Stop the needle in the green.";
    paintBonus();
    setupPutt();
  }
  function paintBonus() {
    $("ch-bBank").textContent = "$" + fmt(B.won);
    $("ch-ladder").innerHTML = CHX.PUTTS.map(function (p, i) {
      var cls = B.results[i] === true ? "made" : B.results[i] === false ? "miss" : i === B.step ? "now" : "";
      return '<div class="rung ' + cls + '"><div class="ft">' + p.ft + ' FT</div><div class="p">+' + fmt(p.pay * bet) + "</div></div>";
    }).join("");
  }
  function setupPutt() {
    var p = CHX.PUTTS[B.step];
    var left = 0.15 + Math.random() * (0.7 - p.zone);
    B.zone = [left, left + p.zone];
    $("ch-zone").style.left = (left * 100) + "%"; $("ch-zone").style.width = (p.zone * 100) + "%";
    $("ch-putt").disabled = false; $("ch-putt").textContent = "PUTT " + p.ft + " FT";
    B.moving = true; B.t0 = performance.now(); B.speed = p.speed;
    cancelAnimationFrame(raf);
    var tick = function (now) {
      if (!B || !B.moving) return;
      var t = (now - B.t0) / 1000 * B.speed * 0.9;
      B.pos = 0.5 - 0.5 * Math.cos(t * Math.PI);          // eases at both ends, like a pendulum
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
      $("ch-basketBig").classList.remove("rattle"); void $("ch-basketBig").offsetWidth; $("ch-basketBig").classList.add("rattle");
      chains();
      $("ch-bNote").textContent = ["PAR!", "BIRDIE!", "ACE!!"][B.step] + " +" + fmt(add);
      B.step++;
      paintBonus();
      if (B.step < CHX.PUTTS.length) setTimeout(function () { $("ch-bNote").textContent = "Next: " + CHX.PUTTS[B.step].ft + " feet. The green shrinks."; setupPutt(); }, 1300);
      else setTimeout(endBonus, 1600);
    } else {
      $("ch-bNote").textContent = "Spit out. Bonus over.";
      tone(140, 0, 0.3, "sawtooth", 0.05);
      paintBonus();
      setTimeout(endBonus, 1500);
    }
  }
  function endBonus() {
    var total = B.won + B.lineWin;
    stats.best = Math.max(stats.best, total); save();
    $("ch-bonus").hidden = true;
    $("ch-winv").textContent = fmt(total); $("ch-last").textContent = fmt(total);
    say("CHAIN RATTLE " + fmt(B.won), "putts made: " + B.results.filter(Boolean).length + " of 3");
    B = null; busy = false; paint();
  }

  // ---------- pay table ----------
  function paintPays() {
    var lb = bet / 25, rows = ['<div></div><div class="h">3</div><div class="h">4</div><div class="h">5</div>'];
    ["ACE", "GOLD", "PINK", "BLUE", "TREE", "A", "K", "Q", "J", "10"].forEach(function (s) {
      rows.push('<div class="ic" title="' + NAMES[s] + '">' + ART[s] + "</div>" + CHX.PAY[s].map(function (v) { return '<div class="v">' + fmt(v * lb) + "</div>"; }).join(""));
    });
    rows.push('<div class="ic">' + BASKET + "</div>" + [5, 15, 50].map(function (m) { return '<div class="v" style="color: var(--gold)">' + fmt(m * bet) + "</div>"; }).join(""));
    $("ch-pt").innerHTML = rows.join("");
  }

  // ---------- wiring ----------
  $("ch-throw").addEventListener("click", throwDisc);
  document.addEventListener("keydown", function (e) {
    if (e.code !== "Space" || !root.classList.contains("on")) return;
    if (!$("ch-bonus").hidden) { e.preventDefault(); putt(); }
    else if ($("ch-sheet").hidden) { e.preventDefault(); throwDisc(); }
  });
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
    freshStack(function (v) { bank = v; });
    stats = { spins: 0, in: 0, out: 0, bonuses: 0, best: 0 }; save();
    $("ch-winv").textContent = "0"; $("ch-last").textContent = "0"; clearLines(); clearInterval(showTimer);
    say("Fresh $" + fmt(bank), "Stats cleared", true); paint();
  });
  $("ch-paysBtn").addEventListener("click", function () { paintPays(); $("ch-sheet").hidden = false; });
  $("ch-sheetX").addEventListener("click", function () { $("ch-sheet").hidden = true; });
  $("ch-sheet").addEventListener("click", function (e) { if (e.target === $("ch-sheet")) $("ch-sheet").hidden = true; });

  build();
  // open on a real-looking screen
  var first = CHX.spin(Math.random);
  for (var c = 0; c < 5; c++) setStrip(c, first[c]);
  paint();
  $("ch-back").addEventListener("click", function () { if (!busy) Shell.show("menu"); });
  function enter() {
    Shell.mode = "testing";
    bank = FREEPLAY.buyIn;
    clearInterval(showTimer); clearLines();
    $("ch-winv").textContent = "0";
    say("Pick a bet and throw", "3 baskets anywhere start the Chain Rattle bonus", true);
    paint();
  }
  $("mm-chain").addEventListener("click", function () { enter(); Shell.show("chain"); });
  return { enter: enter, busy: function () { return busy; } };
})();
