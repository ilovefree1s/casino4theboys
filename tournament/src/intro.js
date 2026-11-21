  /* ---------- the tournament intro: the 4 The Boys piece gets sprayed onto a wall ----------
     About five seconds, with a spray can's rattle and hiss. Plays once, right after a player picks
     who they are and goes into the tournament. Tap SKIP (or anywhere) to cut it short.
     Open the game with #intro on the end of the address to watch it any time. */
  var Intro = (function () {
    var LEN = 5000, playing = false;

    function artSrc() {
      var im = document.querySelector("#screen-menu .mm-art");
      return im ? im.src : "";
    }
    function vol() { return typeof SOUND !== "undefined" ? SOUND.sfx : 0.4; }

    /* ---- sound: a ball rattling in the can, then the hiss while the paint is going on ---- */
    function Sound() {
      var v = vol(), ctx = null, hiss = null;
      if (!(v > 0)) return { hiss: function () {}, thump: function () {}, stop: function () {} };
      try { ctx = new (window.AudioContext || window.webkitAudioContext)(); if (ctx.state === "suspended") ctx.resume(); }
      catch (e) { return { hiss: function () {}, thump: function () {}, stop: function () {} }; }
      var len = ctx.sampleRate * 1, buf = ctx.createBuffer(1, len, ctx.sampleRate), d = buf.getChannelData(0);
      for (var i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
      function noise() { var s = ctx.createBufferSource(); s.buffer = buf; s.loop = true; return s; }
      // the rattle: a handful of dull clacks, a little uneven
      [0, 0.07, 0.15, 0.21, 0.30].forEach(function (at, n) {
        var t = ctx.currentTime + 0.02 + at;
        var s = noise(), f = ctx.createBiquadFilter(), g = ctx.createGain();
        f.type = "bandpass"; f.frequency.value = 2200 + (n % 2) * 700; f.Q.value = 5;
        g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.55 * v, t + 0.004);
        g.gain.exponentialRampToValueAtTime(0.0001, t + 0.045);
        s.connect(f); f.connect(g); g.connect(ctx.destination); s.start(t); s.stop(t + 0.06);
        var o = ctx.createOscillator(), og = ctx.createGain();
        o.type = "triangle"; o.frequency.setValueAtTime(760 + (n % 3) * 90, t);
        og.gain.setValueAtTime(0.0001, t); og.gain.exponentialRampToValueAtTime(0.22 * v, t + 0.003);
        og.gain.exponentialRampToValueAtTime(0.0001, t + 0.035);
        o.connect(og); og.connect(ctx.destination); o.start(t); o.stop(t + 0.05);
      });
      // the hiss: bright filtered noise, faded up and down as the can is pressed
      var src = noise(), hp = ctx.createBiquadFilter(), bp = ctx.createBiquadFilter(), hg = ctx.createGain();
      hp.type = "highpass"; hp.frequency.value = 3200;
      bp.type = "peaking"; bp.frequency.value = 7200; bp.Q.value = 0.7; bp.gain.value = 9;
      hg.gain.value = 0.0001;
      src.connect(hp); hp.connect(bp); bp.connect(hg); hg.connect(ctx.destination); src.start();
      hiss = hg;
      return {
        hiss: function (on) {
          var t = ctx.currentTime;
          hiss.gain.cancelScheduledValues(t);
          hiss.gain.setTargetAtTime(on ? 0.2 * v : 0.0001, t, on ? 0.03 : 0.06);
        },
        thump: function () {
          var t = ctx.currentTime, o = ctx.createOscillator(), g = ctx.createGain();
          o.type = "sine"; o.frequency.setValueAtTime(120, t); o.frequency.exponentialRampToValueAtTime(42, t + 0.35);
          g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.8 * v, t + 0.01);
          g.gain.exponentialRampToValueAtTime(0.0001, t + 0.5);
          o.connect(g); g.connect(ctx.destination); o.start(t); o.stop(t + 0.55);
        },
        stop: function () {
          try { hiss.gain.setTargetAtTime(0.0001, ctx.currentTime, 0.03); } catch (e) {}
          setTimeout(function () { try { ctx.close(); } catch (e) {} }, 400);
        }
      };
    }

    function play(opts, done) {
      if (typeof opts === "function") { done = opts; opts = {}; }
      opts = opts || {};
      if (playing) { if (done) done(); return; }
      var src = artSrc();
      if (!src) { if (done) done(); return; }
      playing = true;

      var W = innerWidth, H = innerHeight, dpr = Math.min(2, window.devicePixelRatio || 1);
      var wrap = document.createElement("div");
      wrap.id = "tt-intro";
      wrap.style.cssText = "position:fixed;inset:0;z-index:10050;background:#06060a;overflow:hidden;touch-action:none;" +
        "transition:opacity .45s ease;opacity:1";
      var cv = document.createElement("canvas");
      cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr);
      cv.style.cssText = "position:absolute;inset:0;width:100%;height:100%";
      var skip = document.createElement("button");
      skip.type = "button"; skip.textContent = "SKIP ›";
      skip.style.cssText = "position:absolute;right:14px;top:calc(env(safe-area-inset-top,0px) + 14px);padding:8px 14px;border-radius:999px;" +
        "border:1px solid rgba(255,255,255,.35);background:rgba(0,0,0,.45);color:#fff;font:700 11px system-ui,sans-serif;letter-spacing:.14em";
      wrap.appendChild(cv); wrap.appendChild(skip);
      document.body.appendChild(wrap);

      var g = cv.getContext("2d");
      function layer() { var c = document.createElement("canvas"); c.width = cv.width; c.height = cv.height; return c; }
      var wall = layer(), art = layer(), artMask = layer(), txt = layer(), txtMask = layer(), tmp = layer();
      var S = cv.width / 390;                       // everything is sized off a 390-wide phone

      /* the wall: dark painted concrete, a little mottled, faint block lines */
      (function () {
        var c = wall.getContext("2d"), w = wall.width, h = wall.height, i;
        var gr = c.createLinearGradient(0, 0, 0, h);
        gr.addColorStop(0, "#121218"); gr.addColorStop(0.55, "#0b0b10"); gr.addColorStop(1, "#060609");
        c.fillStyle = gr; c.fillRect(0, 0, w, h);
        for (i = 0; i < 2600; i++) {
          c.fillStyle = "rgba(" + (Math.random() < 0.5 ? "255,255,255," : "0,0,0,") + (Math.random() * 0.045).toFixed(3) + ")";
          var r = (0.6 + Math.random() * 2.2) * S;
          c.fillRect(Math.random() * w, Math.random() * h, r, r);
        }
        for (i = 0; i < 26; i++) {
          var x = Math.random() * w, y = Math.random() * h, rr = (30 + Math.random() * 90) * S;
          var rg = c.createRadialGradient(x, y, 0, x, y, rr);
          rg.addColorStop(0, "rgba(" + (Math.random() < 0.5 ? "255,255,255,.022" : "0,0,0,.2") + ")"); rg.addColorStop(1, "rgba(0,0,0,0)");
          c.fillStyle = rg; c.fillRect(x - rr, y - rr, rr * 2, rr * 2);
        }
        c.strokeStyle = "rgba(0,0,0,.33)"; c.lineWidth = 1.2 * S;
        var bh = 46 * S, bw = 104 * S, row = 0;
        for (var yy = 0; yy < h; yy += bh, row++) {
          c.beginPath(); c.moveTo(0, yy); c.lineTo(w, yy); c.stroke();
          for (var xx = (row % 2) * bw / 2; xx < w; xx += bw) { c.beginPath(); c.moveTo(xx, yy); c.lineTo(xx, yy + bh); c.stroke(); }
        }
      })();

      /* the piece: the menu's own 4 The Boys art, laid on the wall like the menu shows it */
      var img = new Image(), ready = false, box = null;
      img.onload = function () {
        var c = art.getContext("2d"), w = art.width, h = art.height;
        var sc = Math.max(w / img.width, h / img.height) * 1.0, iw = img.width * sc, ih = img.height * sc;
        c.globalCompositeOperation = "lighter";       // the art is on black: only its light goes onto the wall
        c.drawImage(img, (w - iw) / 2, (h - ih) / 2 + h * 0.07, iw, ih);
        // where the logo sits inside the art (measured from the picture)
        box = { x: w / 2 - iw * 0.47, y: (h - ih) / 2 + h * 0.07 + ih * 0.2, w: iw * 0.94, h: ih * 0.5 };
        ready = true;
      };
      img.src = src;

      /* the words that go on after the piece */
      var line1 = opts.title || "GRAFFITI WEEK", line2 = opts.sub || "EVERY GAME · $10,000 · MOST POINTS WINS";
      var tBox = null;
      (function () {
        var c = txt.getContext("2d"), w = txt.width, h = txt.height;
        var size = 62 * S;
        c.font = "900 italic " + size + "px Impact, Haettenschweiler, 'Arial Narrow Bold', 'Arial Black', sans-serif";
        while (c.measureText(line1).width > w * 0.86 && size > 20) { size -= 2; c.font = c.font.replace(/[\d.]+px/, size + "px"); }
        c.textAlign = "center"; c.textBaseline = "middle";
        var cx = w / 2, cy = h * 0.15;
        c.save(); c.translate(cx, cy); c.rotate(-0.055); c.transform(1, 0, -0.08, 1, 0, 0);
        // fat dark outline, then the fill, then a hot highlight: the way a throw-up is built
        c.lineJoin = "round"; c.lineWidth = 15 * S; c.strokeStyle = "#07060b"; c.strokeText(line1, 0, 0);
        c.lineWidth = 8 * S; c.strokeStyle = "#ff2e88"; c.strokeText(line1, 0, 0);
        var fg = c.createLinearGradient(0, -size / 2, 0, size / 2);
        fg.addColorStop(0, "#fff36b"); fg.addColorStop(0.5, "#ffb21f"); fg.addColorStop(1, "#ff6a00");
        c.fillStyle = fg; c.fillText(line1, 0, 0);
        c.globalCompositeOperation = "source-atop";
        c.fillStyle = "rgba(255,255,255,.55)";
        c.fillRect(-w, -size * 0.36, w * 2, size * 0.11);
        c.restore();
        var tw = c.measureText(line1).width;
        tBox = { x: cx - tw / 2 - 26 * S, y: cy - size * 0.75, w: tw + 52 * S, h: size * 1.5, cy: cy, size: size };
      })();

      /* spraying: soft blobs of "paint" go into a mask, and the art shows through wherever it's been hit */
      function blob(mask, x, y, r, a) {
        var c = mask.getContext("2d"), rg = c.createRadialGradient(x, y, 0, x, y, r);
        rg.addColorStop(0, "rgba(255,255,255," + a + ")"); rg.addColorStop(0.55, "rgba(255,255,255," + a * 0.55 + ")");
        rg.addColorStop(1, "rgba(255,255,255,0)");
        c.fillStyle = rg; c.fillRect(x - r, y - r, r * 2, r * 2);
        c.fillStyle = "rgba(255,255,255," + Math.min(1, a * 2.2) + ")";
        for (var i = 0; i < 9; i++) {                      // overspray: specks thrown wide of the fat line
          var an = Math.random() * 6.283, d = r * (0.7 + Math.random() * 1.1), s = (0.6 + Math.random() * 1.7) * S;
          c.fillRect(x + Math.cos(an) * d, y + Math.sin(an) * d, s, s);
        }
      }
      var mist = [];
      function puff(x, y, col) {
        for (var i = 0; i < 5; i++) {
          var an = Math.random() * 6.283, sp = (0.4 + Math.random() * 1.8) * S;
          mist.push({ x: x, y: y, vx: Math.cos(an) * sp, vy: Math.sin(an) * sp - 0.25 * S, life: 1, r: (1 + Math.random() * 2.4) * S, col: col });
        }
      }
      function show(mask, pic) {
        var c = tmp.getContext("2d");
        c.globalCompositeOperation = "source-over"; c.clearRect(0, 0, tmp.width, tmp.height);
        c.drawImage(mask, 0, 0);
        c.globalCompositeOperation = "source-in"; c.drawImage(pic, 0, 0);
        g.drawImage(tmp, 0, 0);
      }

      // the can's path over the piece: it draws the thing — round the ring, then the 4 stroke by stroke,
      // then a swipe each for THE and BOYS. Points are fractions of the logo's box; "up" legs are the hand moving.
      var ART = (function () {
        var segs = [], ring = [], i, a;
        for (i = 0; i <= 44; i++) { a = (-200 + i * (385 / 44)) * Math.PI / 180; ring.push([0.505 + Math.cos(a) * 0.415, 0.553 + Math.sin(a) * 0.361]); }
        segs.push({ pts: ring, down: true });
        [ [[0.59, 0.238], [0.208, 0.589]],                      // the 4: the slant
          [[0.208, 0.589], [0.683, 0.589]],                     //        the bar
          [[0.612, 0.238], [0.503, 0.814]],                     //        the stem
          [[0.631, 0.499], [0.917, 0.459]],                     // THE
          [[0.437, 0.712], [0.909, 0.632]],                     // BOYS, over
          [[0.895, 0.69], [0.46, 0.765]]                        // and back under
        ].forEach(function (st) {
          var prev = segs[segs.length - 1].pts; prev = prev[prev.length - 1];
          segs.push({ pts: [prev, st[0]], down: false });
          segs.push({ pts: st, down: true });
        });
        var legs = [], total = 0;
        segs.forEach(function (sg) {
          for (var k = 1; k < sg.pts.length; k++) {
            var dx = sg.pts[k][0] - sg.pts[k - 1][0], dy = (sg.pts[k][1] - sg.pts[k - 1][1]) * 1.15;
            var len = Math.sqrt(dx * dx + dy * dy) * (sg.down ? 1 : 0.4);      // the hand moves quicker than it paints
            legs.push({ a: sg.pts[k - 1], b: sg.pts[k], at: total, len: len, down: sg.down }); total += len;
          }
        });
        return { legs: legs, total: total };
      })();
      function artPos(p) {
        var d = Math.max(0, Math.min(1, p)) * ART.total, L = ART.legs[ART.legs.length - 1];
        for (var i = 0; i < ART.legs.length; i++) { if (d <= ART.legs[i].at + ART.legs[i].len) { L = ART.legs[i]; break; } }
        var u = L.len ? Math.min(1, (d - L.at) / L.len) : 1;
        return { x: box.x + box.w * (L.a[0] + (L.b[0] - L.a[0]) * u), y: box.y + box.h * (L.a[1] + (L.b[1] - L.a[1]) * u), down: L.down };
      }
      function txtPos(p) {
        return { x: tBox.x + tBox.w * p, y: tBox.cy + Math.sin(p * 22) * tBox.h * 0.22 };
      }
      var drips = [];
      function startDrips() {
        var cols = ["#ffb21f", "#ff2e88", "#ff6a00", "#fff36b", "#ff2e88"];
        for (var i = 0; i < 5; i++) {
          drips.push({ x: tBox.x + tBox.w * (0.12 + 0.76 * Math.random()), y: tBox.cy + tBox.size * (0.28 + Math.random() * 0.1),
                       len: 0, max: (10 + Math.random() * 26) * S, w: (2.2 + Math.random() * 2.2) * S, col: cols[i], sp: (0.5 + Math.random() * 0.9) * S });
        }
      }

      /* A spray can, drawn with its nozzle at (x, y) and leaning by `tilt`. */
      function drawCan(x, y, tilt, paint, pressed) {
        var bw = 46 * S, bh = 112 * S, c1 = paint === "blue" ? "#2f7bff" : "#ff8a1f", c2 = paint === "blue" ? "#0b2f8f" : "#b33a00";
        g.save();
        g.translate(x, y); g.rotate(tilt);
        g.shadowColor = "rgba(0,0,0,.6)"; g.shadowBlur = 14 * S; g.shadowOffsetY = 6 * S;
        // body
        var body = g.createLinearGradient(-bw / 2, 0, bw / 2, 0);
        body.addColorStop(0, "#3d4049"); body.addColorStop(0.18, "#c9ced8"); body.addColorStop(0.4, "#8b909b");
        body.addColorStop(0.75, "#4a4d57"); body.addColorStop(1, "#23252b");
        g.fillStyle = body;
        rr(-bw / 2, 30 * S, bw, bh, 7 * S); g.fill();
        g.shadowColor = "transparent";
        // the paint-colored label, with a gloss stripe
        var lab = g.createLinearGradient(-bw / 2, 0, bw / 2, 0);
        lab.addColorStop(0, c2); lab.addColorStop(0.22, c1); lab.addColorStop(0.5, c1); lab.addColorStop(1, c2);
        g.fillStyle = lab; g.fillRect(-bw / 2, 52 * S, bw, 66 * S);
        g.fillStyle = "rgba(255,255,255,.28)"; g.fillRect(-bw / 2 + 6 * S, 52 * S, 5 * S, 66 * S);
        g.fillStyle = "rgba(0,0,0,.35)"; g.fillRect(-bw / 2, 52 * S, bw, 2 * S); g.fillRect(-bw / 2, 116 * S, bw, 2 * S);
        g.fillStyle = "#fff"; g.font = "900 italic " + (17 * S) + "px Impact, 'Arial Black', sans-serif";
        g.textAlign = "center"; g.textBaseline = "middle";
        g.fillText("4TB", 0, 86 * S);
        // shoulder and collar
        var dome = g.createLinearGradient(-bw / 2, 0, bw / 2, 0);
        dome.addColorStop(0, "#4a4d57"); dome.addColorStop(0.25, "#e2e6ee"); dome.addColorStop(0.6, "#8b909b"); dome.addColorStop(1, "#2b2d34");
        g.fillStyle = dome;
        g.beginPath(); g.moveTo(-bw / 2, 34 * S); g.quadraticCurveTo(-bw / 2, 16 * S, -9 * S, 13 * S);
        g.lineTo(9 * S, 13 * S); g.quadraticCurveTo(bw / 2, 16 * S, bw / 2, 34 * S); g.closePath(); g.fill();
        g.fillStyle = "#b9bec9"; g.fillRect(-9 * S, 9 * S, 18 * S, 5 * S);
        // the cap you press, with the nozzle hole facing the wall
        var dn = pressed ? 2 * S : 0;
        g.fillStyle = "#f2f2f2"; rr(-8 * S, -6 * S + dn, 16 * S, 16 * S, 3 * S); g.fill();
        g.fillStyle = "#16161a"; g.beginPath(); g.arc(-6 * S, 1 * S + dn, 2 * S, 0, 6.283); g.fill();
        // bottom rim
        g.fillStyle = "#17181c"; rr(-bw / 2, 30 * S + bh - 6 * S, bw, 6 * S, 3 * S); g.fill();
        g.restore();
      }
      function rr(x, y, w, h, r) {
        g.beginPath(); g.moveTo(x + r, y); g.lineTo(x + w - r, y); g.quadraticCurveTo(x + w, y, x + w, y + r);
        g.lineTo(x + w, y + h - r); g.quadraticCurveTo(x + w, y + h, x + w - r, y + h); g.lineTo(x + r, y + h);
        g.quadraticCurveTo(x, y + h, x, y + h - r); g.lineTo(x, y + r); g.quadraticCurveTo(x, y, x + r, y); g.closePath();
      }
      var can = null;

      var snd = Sound(), t0 = 0, last = 0, raf = 0, over = false, hissing = false, thumped = false, dripped = false, prevA = 0, prevT = 0;
      function hiss(on) { if (on !== hissing) { hissing = on; snd.hiss(on); } }
      function finish() {
        if (over) return;
        over = true; cancelAnimationFrame(raf); snd.stop();
        wrap.style.opacity = "0";
        setTimeout(function () { wrap.remove(); playing = false; }, 460);
        if (done) done();
      }
      skip.onclick = function (e) { e.stopPropagation(); finish(); };
      wrap.onclick = finish;

      // timings, in ms
      var A0 = 420, A1 = 2450, T0 = 2750, T1 = 3500, SUB = 3650;
      function frame(now) {
        if (over) return;
        if (!ready) { raf = requestAnimationFrame(frame); return; }     // wait for the picture
        if (!t0) { t0 = now; last = now; }
        var t = now - t0;
        g.globalCompositeOperation = "source-over";
        g.drawImage(wall, 0, 0);

        // 1) the piece
        var nozzle = null;
        if (t >= A0) {
          var pa = Math.min(1, (t - A0) / (A1 - A0));
          for (var q = prevA; q <= pa; q += 0.003) {
            var pt = artPos(q);
            if (pt.down) blob(artMask, pt.x, pt.y, box.w * 0.125, 0.6);
          }
          prevA = pa;
          if (pa < 1) { nozzle = artPos(pa); if (nozzle.down) puff(nozzle.x, nozzle.y, "120,170,255"); }
          else {
            // last pass: the gaps fill in and the whole wall around it takes the glow
            var c2 = artMask.getContext("2d");
            c2.fillStyle = "rgba(255,255,255," + Math.min(1, (t - A1) / 380) * 0.22 + ")";
            c2.fillRect(0, 0, artMask.width, artMask.height);
          }
        }
        g.globalCompositeOperation = "lighter";
        show(artMask, art);
        g.globalCompositeOperation = "source-over";

        // 2) the words
        if (t >= T0) {
          var pw = Math.min(1, (t - T0) / (T1 - T0));
          for (var q2 = prevT; q2 <= pw; q2 += 0.006) {
            var p2 = txtPos(q2);
            blob(txtMask, p2.x, p2.y, tBox.h * 0.62, 0.75);
          }
          prevT = pw;
          if (pw < 1) { nozzle = txtPos(pw); puff(nozzle.x, nozzle.y, "255,190,60"); }
          else {
            var c3 = txtMask.getContext("2d");
            c3.fillStyle = "rgba(255,255,255,.3)"; c3.fillRect(tBox.x, tBox.y, tBox.w, tBox.h);
            if (!dripped) { dripped = true; startDrips(); }
            if (!thumped) { thumped = true; snd.thump(); }
          }
          // drips first, so they run out from under the letters
          drips.forEach(function (d) {
            d.len = Math.min(d.max, d.len + d.sp * (now - last) / 16);
            g.strokeStyle = d.col; g.lineWidth = d.w; g.lineCap = "round";
            g.beginPath(); g.moveTo(d.x, d.y); g.lineTo(d.x, d.y + d.len); g.stroke();
            g.fillStyle = d.col; g.beginPath(); g.arc(d.x, d.y + d.len, d.w * 0.75, 0, 6.283); g.fill();
          });
          show(txtMask, txt);
        }
        // 3) the small line under it, stencilled on
        if (t >= SUB) {
          var a = Math.min(1, (t - SUB) / 300);
          g.save();
          g.globalAlpha = a;
          g.font = "700 " + (11.5 * S) + "px ui-monospace, Menlo, Consolas, monospace";
          g.textAlign = "center"; g.textBaseline = "middle"; g.fillStyle = "#f4eedc";
          var y2 = tBox.cy + tBox.size * 1.55, sp = 2.2 * S, chars = line2.split(""), tw2 = 0;
          chars.forEach(function (ch) { tw2 += g.measureText(ch).width + sp; });
          var x2 = cv.width / 2 - tw2 / 2;
          chars.forEach(function (ch) { var wch = g.measureText(ch).width; g.fillText(ch, x2 + wch / 2, y2); x2 += wch + sp; });
          g.restore();
        }

        // the mist coming off the can, and the can's cone of spray
        hiss(!!nozzle);
        // the can itself: shaken first, then held off the wall and worked across it, then pulled away
        var want, paint = t < T0 - 150 ? "blue" : "orange";
        if (t < A0) want = { x: cv.width * 0.5, y: cv.height * 0.5 + Math.sin(t / 26) * 16 * S, tilt: -0.5 + Math.sin(t / 26) * 0.12 };
        else if (nozzle) want = { x: nozzle.x + 46 * S, y: nozzle.y + 40 * S, tilt: -0.62 };
        else if (t < T0) { var st = txtPos(0); want = { x: st.x + 46 * S, y: st.y + 40 * S, tilt: -0.62 }; }
        else want = { x: cv.width * 1.25, y: cv.height * 1.05, tilt: 0.2 };
        if (!can) can = { x: want.x, y: want.y, tilt: want.tilt };
        var k = nozzle ? 1 : Math.min(1, (now - last) / 110);
        can.x += (want.x - can.x) * k; can.y += (want.y - can.y) * k; can.tilt += (want.tilt - can.tilt) * Math.min(1, (now - last) / 80);
        if (nozzle) {
          // the fan of paint from the nozzle to the wall
          var ang = Math.atan2(nozzle.y - can.y, nozzle.x - can.x), dist = Math.hypot(nozzle.x - can.x, nozzle.y - can.y), sprd = 0.42;
          var fan = g.createRadialGradient(can.x, can.y, 2 * S, can.x, can.y, dist * 1.25);
          var rgb = paint === "blue" ? "150,200,255" : "255,205,90";
          fan.addColorStop(0, "rgba(" + rgb + ",.75)"); fan.addColorStop(0.7, "rgba(" + rgb + ",.28)"); fan.addColorStop(1, "rgba(" + rgb + ",0)");
          g.fillStyle = fan;
          g.beginPath(); g.moveTo(can.x, can.y); g.arc(can.x, can.y, dist * 1.25, ang - sprd, ang + sprd); g.closePath(); g.fill();
          var cone = g.createRadialGradient(nozzle.x, nozzle.y, 0, nozzle.x, nozzle.y, 24 * S);
          cone.addColorStop(0, "rgba(255,255,255,.45)"); cone.addColorStop(1, "rgba(255,255,255,0)");
          g.fillStyle = cone; g.fillRect(nozzle.x - 24 * S, nozzle.y - 24 * S, 48 * S, 48 * S);
        }
        drawCan(can.x, can.y, can.tilt, paint, !!nozzle);
        for (var i = mist.length - 1; i >= 0; i--) {
          var m = mist[i];
          m.x += m.vx; m.y += m.vy; m.life -= 0.035;
          if (m.life <= 0) { mist.splice(i, 1); continue; }
          g.fillStyle = "rgba(" + m.col + "," + (m.life * 0.5).toFixed(3) + ")";
          g.fillRect(m.x, m.y, m.r, m.r);
        }
        last = now;
        if (t >= LEN) { finish(); return; }
        raf = requestAnimationFrame(frame);
      }
      raf = requestAnimationFrame(frame);
      // never hang: if the picture won't load, or the tab is put away, get out of the way
      setTimeout(function () { if (!over && !t0) finish(); }, 2500);
      setTimeout(function () { finish(); }, LEN + 3000);
    }

    /* ---- lounge music for the how-it-works cards: walking bass, a soft piano comp, brushes ---- */
    function Music() {
      var v = vol(), none = { stop: function () {}, wake: function () {}, chips: function () {} }, ctx;
      if (!(v > 0)) return none;
      try { ctx = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { return none; }
      var out = ctx.createGain(); out.gain.value = 0.0001; out.connect(ctx.destination);
      out.gain.setTargetAtTime(0.5 * v, ctx.currentTime, 0.25);
      var nb = ctx.createBuffer(1, ctx.sampleRate * 0.3, ctx.sampleRate), nd = nb.getChannelData(0);
      for (var i = 0; i < nd.length; i++) nd[i] = Math.random() * 2 - 1;
      function hz(m) { return 440 * Math.pow(2, (m - 69) / 12); }
      function tone(type, m, t, len, vol_, cut) {
        var o = ctx.createOscillator(), g = ctx.createGain(), f = ctx.createBiquadFilter();
        o.type = type; o.frequency.value = hz(m); f.type = "lowpass"; f.frequency.value = cut;
        g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(vol_, t + 0.012);
        g.gain.exponentialRampToValueAtTime(0.0001, t + len);
        o.connect(f); f.connect(g); g.connect(out); o.start(t); o.stop(t + len + 0.03);
      }
      function brush(t, vol_, len) {
        var n = ctx.createBufferSource(), f = ctx.createBiquadFilter(), g = ctx.createGain();
        n.buffer = nb; f.type = "highpass"; f.frequency.value = 6500;
        g.gain.setValueAtTime(vol_, t); g.gain.exponentialRampToValueAtTime(0.0001, t + len);
        n.connect(f); f.connect(g); g.connect(out); n.start(t); n.stop(t + len + 0.02);
      }
      // Dm7  G7  Cmaj7  A7 — one bar each, bass walks four to the bar
      var BARS = [
        { bass: [38, 41, 45, 44], ch: [60, 65, 69, 72] },
        { bass: [43, 47, 50, 49], ch: [59, 65, 69, 71] },
        { bass: [36, 40, 43, 45], ch: [59, 64, 67, 71] },
        { bass: [45, 49, 52, 39], ch: [61, 64, 67, 70] }
      ];
      var LICK = [[0, 76], [1.67, 74], [2, 72], [3.67, 69], [4, 71], [6, 74], [7.67, 72], [8, 67], [10, 71], [11.67, 72], [12, 73], [14, 76]];
      var beat = 60 / 132, bar = 0, nextAt = ctx.currentTime + 0.12, stopped = false;
      function sched() {
        if (stopped) return;
        while (nextAt < ctx.currentTime + 0.6) {
          var b = BARS[bar % 4], t = nextAt, k;
          for (k = 0; k < 4; k++) {
            tone("triangle", b.bass[k], t + k * beat, beat * 0.95, 0.5, 420);
            brush(t + k * beat, k % 2 ? 0.14 : 0.07, 0.07);
            if (k % 2) brush(t + (k + 0.67) * beat, 0.06, 0.04);          // the swung skip
          }
          [0.67, 2.67].forEach(function (at) {                            // comp on the off-beats
            b.ch.forEach(function (m) { tone("triangle", m, t + at * beat, beat * 1.1, 0.085, 1800); });
          });
          LICK.forEach(function (n) {                                     // a little vibes line over the top
            if (Math.floor(n[0] / 4) === bar % 4) tone("sine", n[1] + 12, t + (n[0] % 4) * beat, beat * 1.4, 0.07, 5000);
          });
          bar++; nextAt += beat * 4;
        }
      }
      var iv = setInterval(sched, 120); sched();
      // ceramic chips riffled and dropped on a stack: dry clacks, all click and no ring.
      // Each one is a few milliseconds of noise through two tight filters (the chip's body and its edge), gone in ~25ms.
      function chips() {
        try {
          if (ctx.state === "suspended") ctx.resume();
          var t = ctx.currentTime + 0.01, at = 0, gaps = [0, 0.052, 0.046, 0.04, 0.036, 0.06];
          gaps.forEach(function (gap, i) {
            at += gap * (0.9 + Math.random() * 0.2);
            var when = t + at, last = i === gaps.length - 1, lvl = (last ? 1 : 0.5 + Math.random() * 0.25) * v;
            var body = 1700 + Math.random() * 500, edge = 3900 + Math.random() * 900;
            [[body, 2.2, 0.9, 0.03], [edge, 4.5, 0.75, 0.016], [edge * 1.9, 3, 0.25, 0.008]].forEach(function (b) {
              var n = ctx.createBufferSource(), f = ctx.createBiquadFilter(), g = ctx.createGain();
              n.buffer = nb; n.playbackRate.value = 0.9 + Math.random() * 0.3;
              f.type = "bandpass"; f.frequency.value = b[0]; f.Q.value = b[1];
              g.gain.setValueAtTime(0.0001, when); g.gain.linearRampToValueAtTime(lvl * b[2] * 1.6, when + 0.0012);
              g.gain.exponentialRampToValueAtTime(0.0001, when + b[3]);
              n.connect(f); f.connect(g); g.connect(ctx.destination); n.start(when, Math.random() * 0.2); n.stop(when + b[3] + 0.01);
            });
            if (last) {                       // the stack takes the weight: a soft low knock under the last clack
              var o = ctx.createOscillator(), og = ctx.createGain();
              o.type = "sine"; o.frequency.setValueAtTime(190, when); o.frequency.exponentialRampToValueAtTime(95, when + 0.05);
              og.gain.setValueAtTime(lvl * 0.35, when); og.gain.exponentialRampToValueAtTime(0.0001, when + 0.06);
              o.connect(og); og.connect(ctx.destination); o.start(when); o.stop(when + 0.08);
            }
          });
        } catch (e) {}
      }
      return {
        chips: chips,
        wake: function () { try { if (ctx.state === "suspended") { ctx.resume(); nextAt = Math.max(nextAt, ctx.currentTime + 0.1); } } catch (e) {} },
        stop: function () {
          if (stopped) return; stopped = true; clearInterval(iv);
          try { out.gain.cancelScheduledValues(ctx.currentTime); out.gain.setTargetAtTime(0.0001, ctx.currentTime, 0.12); } catch (e) {}
          setTimeout(function () { try { ctx.close(); } catch (e) {} }, 700);
        }
      };
    }

    /* ---- the how-it-works cards: neon signs on a dark wall, one per tap ---- */
    var FLAME = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2c1 4 5.5 6 5.5 11.5a5.5 5.5 0 0 1-11 0c0-2.2 1-3.8 2.2-5 0 2.2 1 3.2 2 3.2 0-3.2-1-6 1.3-9.7z"/></svg>';
    var CROWN = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 18V7l4.5 5L12 5l4.5 7L21 7v11z"/></svg>';
    var CUP = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 3h10v6a5 5 0 0 1-10 0zM7 5H3.5v1.5A3.5 3.5 0 0 0 7 10M17 5h3.5v1.5A3.5 3.5 0 0 1 17 10M12 14v4M8 21h8M9.5 18h5"/></svg>';
    var ARROW = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12h17M14 6l6 6-6 6"/></svg>';
    // frame = the tube round the sign, head = the lettering, each [bright core, glow]
    var CARDS = [
      { head: "HOW YOU<br>SCORE", frame: ["#7fc4ff", "#2f8cff"], lit: ["#ffe9f4", "#ff3d9a"],
        art: '<div class="gd-row"><span class="gd-cash">$10,000</span><span class="gd-arrow">' + ARROW + '</span><span class="gd-cash gd-up">$15,000+</span></div>' +
          '<div class="gd-crown">' + CROWN + '<span>TAKES THE POINTS</span></div>',
        body: "<b>Every game starts you at $10,000.</b> Whoever has the most money on a game at midnight takes that game's points. You have to be up at least $5,000 — that's <b>$15,000 or more</b>." },
      { head: "POINTS", frame: ["#ffb3da", "#ff3d9a"], lit: ["#fff3d6", "#ffb02e"],
        art: '<div class="gd-pts"><div><b>3</b><i>PTS</i><span>3 GAMES</span></div><div><b>2</b><i>PTS</i><span>5 GAMES</span></div><div><b>1</b><i>PT</i><span>THE REST</span></div></div>',
        body: "<b>Games are worth 3, 2 or 1 points.</b> They get shuffled every day. Each game's button shows what it's worth today and who's on top." },
      { head: "WHO'S ON TOP", frame: ["#b6ffd6", "#22d37a"], lit: ["#e3f2ff", "#2f8cff"], top: true,
        body: "<b>Every game's button shows who's on top.</b> It shows how much they have and what the game is worth today. Beat their number to take the points from them." },
      { head: "HOT GAMES", frame: ["#7fc4ff", "#2f8cff"], lit: ["#ffe9f4", "#ff3d9a"],
        art: '<div class="gd-hot">' + FLAME + '<b>1.5×</b></div>',
        body: "<b>Two games a day pay 1.5× on every win.</b> Different games each day. Look for the 🔥 on the menu." },
      { head: "MOST POINTS<br>WINS", frame: ["#ffe7a0", "#ffb02e"], lit: ["#ffe9f4", "#ff3d9a"],
        art: '<div class="gd-trophy">' + CUP + '</div>',
        body: "<b>Points add up all week.</b> Sunday through Saturday night. Go broke on a game? <b>REBUY is free</b> and puts you back at $10,000." }
    ];
    /* three made-up leaders on three game buttons, with real faces off the wall */
    function topArt() {
      var f = {}; try { f = (typeof Walkups !== "undefined" && Walkups.faces && Walkups.faces()) || {}; } catch (e) {}
      var names = Object.keys(f).sort(function () { return Math.random() - 0.5; });
      var rows = [["BLACKJACK", "$41,250", 3], ["CRAPS", "$27,800", 2], ["ROULETTE", "$18,400", 1]];
      return '<div class="gd-top">' + rows.map(function (r, i) {
        var n = names[i] || ["QTIP", "JUICE", "COUGAR"][i];
        var pic = f[n] ? '<img src="' + f[n] + '" alt="">' : '<span class="gd-noface">🙂</span>';
        return '<div class="gd-tile">' + pic + '<div class="gd-who"><b>' + r[0] + '</b><span>' + CROWN + String(n).toUpperCase() + '</span></div>' +
          '<div class="gd-amt"><b>' + r[1] + '</b><span>' + r[2] + ' PT' + (r[2] > 1 ? 'S' : '') + '</span></div></div>';
      }).join("") + '<div class="gd-eg">EXAMPLE</div></div>';
    }
    var demoing = false;
    function demo(done) {
      if (demoing) { if (done) done(); return; }
      demoing = true;
      if (!document.getElementById("gd-style")) {
        var NE = "'Tilt Neon',system-ui,sans-serif";
        function tube(core, glow) { return "color:" + core + ";text-shadow:0 0 6px " + glow + ",0 0 18px " + glow + ",0 0 44px " + glow; }
        var st = document.createElement("style"); st.id = "gd-style";
        st.textContent =
          "@font-face{font-family:'Tilt Neon';font-display:block;src:url(data:font/woff2;base64,/*NEONFONT*/) format('woff2')}" +
          "#tt-demo{--c:#7fc4ff;--g:#2f8cff;--hc:#ffe9f4;--hg:#ff3d9a;position:fixed;inset:0;z-index:10050;overflow:hidden;display:flex;flex-direction:column;align-items:center;" +
            "padding:calc(env(safe-area-inset-top,0px) + 56px) 24px calc(env(safe-area-inset-bottom,0px) + 20px);box-sizing:border-box;" +
            "background:#05050a;transition:opacity .35s ease;color:#fff;font-family:system-ui,sans-serif}" +
          "#tt-demo:before{content:'';position:absolute;inset:0;background-image:linear-gradient(rgba(255,255,255,.035) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.03) 1px,transparent 1px);background-size:78px 32px}" +
          "#tt-demo .gd-halo{position:absolute;left:50%;top:36%;width:470px;height:440px;margin:-220px 0 0 -235px;border-radius:50%;background:radial-gradient(closest-side,var(--g),transparent);opacity:.3;transition:background .3s}" +
          "#tt-demo .gd-skip{position:absolute;z-index:2;right:14px;top:calc(env(safe-area-inset-top,0px) + 14px);padding:8px 14px;border-radius:999px;border:1px solid rgba(255,255,255,.4);" +
            "background:rgba(0,0,0,.5);color:#fff;font:700 11px system-ui,sans-serif;letter-spacing:.14em}" +
          "#tt-demo .gd-card{position:relative;width:100%;max-width:400px;text-align:center;margin:auto 0;display:flex;flex-direction:column;align-items:center}" +
          "#tt-demo .gd-sign{width:100%;max-width:340px;box-sizing:border-box;padding:24px 12px 28px;border:3px solid var(--c);border-radius:28px;display:flex;flex-direction:column;align-items:center;gap:6px;" +
            "box-shadow:0 0 10px var(--g),0 0 34px var(--g),inset 0 0 10px var(--g),inset 0 0 30px var(--g);background:rgba(5,5,10,.55)}" +
          "#tt-demo .gd-card.in .gd-sign{animation:gdOn .5s ease-out both}" +
          "@keyframes gdOn{0%{opacity:.2}45%{opacity:1}60%{opacity:.75}100%{opacity:1}}" +
          "#tt-demo .gd-card.in .gd-body{animation:gdUp .4s .3s ease both}@keyframes gdUp{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}" +
          "#tt-demo .gd-tag{font:400 14px " + NE + ";letter-spacing:.3em;color:#e8f3ff;text-shadow:0 0 8px var(--g)}" +
          "#tt-demo h2{margin:0 0 8px;font:400 50px/1.02 " + NE + ";color:var(--hc);text-shadow:0 0 6px var(--hg),0 0 18px var(--hg),0 0 44px var(--hg)}" +
          "#tt-demo svg{fill:none;stroke:currentColor;stroke-width:1.4;stroke-linejoin:round;stroke-linecap:round}" +
          "#tt-demo .gd-art{min-height:112px;width:100%;display:flex;flex-direction:column;align-items:center;justify-content:center}" +
          // 1
          "#tt-demo .gd-row{display:flex;align-items:center;gap:8px}" +
          "#tt-demo .gd-cash{font:400 26px " + NE + ";padding:7px 10px 5px;border-radius:12px;border:2px solid #cfd6e6;color:#f1f4fa;text-shadow:0 0 8px rgba(255,255,255,.6);box-shadow:0 0 8px rgba(255,255,255,.35),inset 0 0 8px rgba(255,255,255,.2)}" +
          "#tt-demo .gd-cash.gd-up{border-color:#b6ffd6;" + tube("#e6fff0", "#22d37a") + ";box-shadow:0 0 10px #22d37a,0 0 24px #22d37a,inset 0 0 10px #22d37a}" +
          "#tt-demo .gd-arrow svg{width:26px;height:26px;display:block;color:#fff3d6;filter:drop-shadow(0 0 5px #ffb02e)}" +
          "#tt-demo .gd-crown{margin-top:16px;display:flex;align-items:center;gap:8px;font:400 18px " + NE + ";letter-spacing:.12em;" + tube("#fff3d6", "#ffb02e") + "}" +
          "#tt-demo .gd-crown svg{width:26px;height:26px;filter:drop-shadow(0 0 5px #ffb02e)}" +
          // 2
          "#tt-demo .gd-pts{display:flex;width:100%;justify-content:space-around;align-items:flex-end}" +
          "#tt-demo .gd-pts>div{display:flex;flex-direction:column;align-items:center}" +
          "#tt-demo .gd-pts b{font:400 92px/0.95 " + NE + "}#tt-demo .gd-pts i{font:400 15px " + NE + ";letter-spacing:.2em;font-style:normal}" +
          "#tt-demo .gd-pts>div:nth-child(1){" + tube("#fff3d6", "#ffb02e") + "}#tt-demo .gd-pts>div:nth-child(2){" + tube("#ffe9f4", "#ff3d9a") + "}#tt-demo .gd-pts>div:nth-child(3){" + tube("#e3f2ff", "#2f8cff") + "}" +
          "#tt-demo .gd-pts span{margin-top:8px;font:700 10px system-ui,sans-serif;letter-spacing:.14em;color:#dfe6f2;text-shadow:none}" +
          // 3
          "#tt-demo .gd-top{width:100%;display:flex;flex-direction:column;gap:9px;padding:0 4px;box-sizing:border-box}" +
          "#tt-demo .gd-tile{display:flex;align-items:center;gap:10px;padding:7px 12px 7px 7px;border-radius:999px;border:2px solid #b9c6dc;background:rgba(255,255,255,.04);box-shadow:0 0 8px rgba(160,190,255,.35),inset 0 0 8px rgba(160,190,255,.18);text-align:left}" +
          "#tt-demo .gd-tile:first-child{border-color:#ffe7a0;box-shadow:0 0 10px #ffb02e,0 0 22px rgba(255,176,46,.6),inset 0 0 10px rgba(255,176,46,.5)}" +
          "#tt-demo .gd-tile img,#tt-demo .gd-noface{width:46px;height:46px;border-radius:50%;object-fit:cover;border:2px solid #ffe7a0;box-shadow:0 0 10px #ffb02e;flex:none;display:flex;align-items:center;justify-content:center;font-size:24px;background:#222}" +
          "#tt-demo .gd-who{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}" +
          "#tt-demo .gd-who b{font:400 19px " + NE + ";letter-spacing:.06em;color:#fff}" +
          "#tt-demo .gd-who span{display:flex;align-items:center;gap:4px;font:700 10px system-ui,sans-serif;letter-spacing:.12em;color:#ffe7a0;white-space:nowrap;overflow:hidden}" +
          "#tt-demo .gd-who svg{width:12px;height:12px;flex:none}" +
          "#tt-demo .gd-amt{display:flex;flex-direction:column;align-items:flex-end;gap:1px}" +
          "#tt-demo .gd-amt b{font:400 19px " + NE + ";" + tube("#e6fff0", "#22d37a") + "}" +
          "#tt-demo .gd-amt span{font:400 12px " + NE + ";letter-spacing:.14em;" + tube("#ffe9f4", "#ff3d9a") + "}" +
          "#tt-demo .gd-eg{font:700 9px system-ui,sans-serif;letter-spacing:.24em;color:#aeb8cc;margin-top:2px}" +
          // 4, 5
          "#tt-demo .gd-hot{display:flex;align-items:center;gap:4px;" + tube("#fff3d6", "#ff8a2b") + "}" +
          "#tt-demo .gd-hot svg{width:66px;height:66px;filter:drop-shadow(0 0 5px #ff8a2b) drop-shadow(0 0 14px #ff8a2b)}" +
          "#tt-demo .gd-hot b{font:400 112px/1 " + NE + "}" +
          "#tt-demo .gd-trophy svg{width:128px;height:128px;stroke-width:1.1;color:#fff3d6;filter:drop-shadow(0 0 5px #ffb02e) drop-shadow(0 0 16px #ffb02e)}" +
          "#tt-demo .gd-body{margin:26px auto 0;max-width:336px;font-size:16px;line-height:1.45;color:#c9d3e6}" +
          "#tt-demo .gd-body b{display:block;margin-bottom:8px;font-size:20px;line-height:1.25;color:#fff}#tt-demo .gd-body b+b,#tt-demo .gd-body b:not(:first-child){display:inline;margin:0;font-size:inherit}" +
          "#tt-demo .gd-foot{position:relative;margin:0 0 22px;display:flex;flex-direction:column;align-items:center;gap:16px;width:100%}" +
          "#tt-demo .gd-dots{display:flex;gap:8px;align-items:center}#tt-demo .gd-dots i{width:8px;height:8px;border-radius:50%;background:#5f6b85}" +
          "#tt-demo .gd-dots i.on{background:var(--hc);box-shadow:0 0 10px var(--hg),0 0 20px var(--hg)}" +
          "#tt-demo .gd-next{width:100%;max-width:300px;height:58px;border:3px solid var(--hc);border-radius:999px;background:rgba(255,255,255,.05);font:400 25px " + NE + ";letter-spacing:.16em;" +
            "color:var(--hc);text-shadow:0 0 10px var(--hg);box-shadow:0 0 12px var(--hg),0 0 34px var(--hg),inset 0 0 14px var(--hg)}" +
          "#tt-demo .gd-next:active{transform:scale(.97)}";
        document.head.appendChild(st);
      }
      var wrap = document.createElement("div"); wrap.id = "tt-demo";
      var halo = document.createElement("div"); halo.className = "gd-halo";
      var skip = document.createElement("button"); skip.type = "button"; skip.className = "gd-skip"; skip.textContent = "SKIP ›";
      var card = document.createElement("div"); card.className = "gd-card";
      var foot = document.createElement("div"); foot.className = "gd-foot";
      var dots = document.createElement("div"); dots.className = "gd-dots";
      var next = document.createElement("button"); next.type = "button"; next.className = "gd-next";
      foot.appendChild(dots); foot.appendChild(next);
      wrap.appendChild(halo); wrap.appendChild(skip); wrap.appendChild(card); wrap.appendChild(foot);
      document.body.appendChild(wrap);
      var at = 0, closed = false, mus = Music();
      wrap.addEventListener("pointerdown", function () { mus.wake(); });
      function close() {
        if (closed) return; closed = true;
        mus.stop();
        wrap.style.opacity = "0";
        setTimeout(function () { wrap.remove(); demoing = false; }, 360);
        if (done) done();
      }
      function show() {
        var c = CARDS[at];
        wrap.style.setProperty("--c", c.frame[0]); wrap.style.setProperty("--g", c.frame[1]);
        wrap.style.setProperty("--hc", c.lit[0]); wrap.style.setProperty("--hg", c.lit[1]);
        card.className = "gd-card";
        card.innerHTML = '<div class="gd-sign"><div class="gd-tag">' + (at + 1) + ' OF ' + CARDS.length + '</div><h2>' + c.head + '</h2>' +
          '<div class="gd-art">' + (c.top ? topArt() : c.art) + '</div></div><div class="gd-body">' + c.body + '</div>';
        void card.offsetWidth; card.className = "gd-card in";
        dots.innerHTML = CARDS.map(function (_, i) { return '<i' + (i === at ? ' class="on"' : '') + '></i>'; }).join("");
        next.textContent = at === CARDS.length - 1 ? "LET'S PLAY" : "NEXT";
      }
      next.onclick = function () { mus.chips(); if (at < CARDS.length - 1) { at++; show(); } else close(); };
      skip.onclick = close;
      show();
    }

    return { play: play, demo: demo, isPlaying: function () { return playing || demoing; } };
  })();
  if (/(^|[#&])intro\b/.test(location.hash)) {
    addEventListener("load", function () {
      // sound needs a tap first on a phone, so the preview starts on the first tap
      var tip = document.createElement("div");
      tip.textContent = "TAP TO PLAY THE INTRO";
      tip.style.cssText = "position:fixed;inset:0;z-index:10049;display:flex;align-items:center;justify-content:center;" +
        "background:rgba(0,0,0,.85);color:#FFD24D;font:800 16px system-ui,sans-serif;letter-spacing:.14em";
      tip.onclick = function () { tip.remove(); Intro.play(function () { Intro.demo(); }); };
      document.body.appendChild(tip);
    });
  }

