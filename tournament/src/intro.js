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

      // the can's path over the piece: side to side, working down, with a wobble
      function artPos(p) {
        var rows = 7, f = p * rows, row = Math.min(rows - 1, Math.floor(f)), u = f - row;
        var e = u < 0.5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2;
        var x = box.x + box.w * (row % 2 ? 1 - e : e);
        var y = box.y + box.h * ((row + 0.5) / rows) + Math.sin(p * 40) * box.h * 0.035;
        return { x: x, y: y };
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
          for (var q = prevA; q <= pa; q += 0.004) {
            var pt = artPos(q);
            blob(artMask, pt.x, pt.y, box.w * 0.19, 0.5);
          }
          prevA = pa;
          if (pa < 1) { nozzle = artPos(pa); puff(nozzle.x, nozzle.y, "120,170,255"); }
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
      var v = vol(), none = { stop: function () {}, wake: function () {} }, ctx;
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
      return {
        wake: function () { try { if (ctx.state === "suspended") { ctx.resume(); nextAt = Math.max(nextAt, ctx.currentTime + 0.1); } } catch (e) {} },
        stop: function () {
          if (stopped) return; stopped = true; clearInterval(iv);
          try { out.gain.cancelScheduledValues(ctx.currentTime); out.gain.setTargetAtTime(0.0001, ctx.currentTime, 0.12); } catch (e) {}
          setTimeout(function () { try { ctx.close(); } catch (e) {} }, 700);
        }
      };
    }

    /* ---- the how-it-works cards: a brick wall, a fresh piece on it for every tap ---- */
    var CARDS = [
      { head: ["HOW YOU", "SCORE"], color: "#2F8CFF", hi: "#9AD4FF", second: "#FF4FA3",
        art: '<div class="gd-row"><span class="gd-cash">$10,000</span><span class="gd-arrow">➜</span><span class="gd-cash gd-up">$15,000+</span></div><div class="gd-crown">👑 TAKES THE POINTS</div>',
        body: "<b>Every game starts you at $10,000.</b> Whoever has the most money on a game at midnight takes that game's points. You have to be up at least $5,000 — that's <b>$15,000 or more</b>." },
      { head: ["3 · 2 · 1"], color: "#FF3D9A", hi: "#FFB3DA", second: "#FFD24D",
        art: '<div class="gd-pts"><div><b>3</b><i>PTS</i><span>3 GAMES</span></div><div><b>2</b><i>PTS</i><span>5 GAMES</span></div><div><b>1</b><i>PT</i><span>THE REST</span></div></div>',
        body: "<b>Games are worth 3, 2 or 1 points.</b> They get shuffled every day. Each game's button shows what it's worth today and who's on top." },
      { head: ["WHO'S", "ON TOP"], color: "#35E08A", hi: "#C9FFE0", second: "#2F8CFF", top: true,
        body: "<b>Every game's button shows who's leading it,</b> how much they have, and what the game is worth today. Beat their number to take the points from them." },
      { head: ["HOT", "GAMES"], color: "#FF6A2B", hi: "#FFD24D", second: "#FF2B4F",
        art: '<div class="gd-hot"><span>🔥</span><b>1.5×</b><span>🔥</span></div>',
        body: "<b>Two games a day pay 1.5× on every win.</b> Different games each day. Look for the 🔥 on the menu." },
      { head: ["MOST POINTS", "WINS"], color: "#FFC62B", hi: "#FFF3B0", second: "#35E08A",
        art: '<div class="gd-trophy">🏆</div>',
        body: "<b>Points add up all week,</b> Sunday through Saturday night. Go broke on a game? <b>REBUY is free</b> and puts you back at $10,000." }
    ];
    /* three made-up leaders on three game buttons, with real faces off the wall */
    function topArt() {
      var f = {}; try { f = (typeof Walkups !== "undefined" && Walkups.faces && Walkups.faces()) || {}; } catch (e) {}
      var names = Object.keys(f).sort(function () { return Math.random() - 0.5; });
      var rows = [["BLACKJACK", "$41,250", 3], ["CRAPS", "$27,800", 2], ["ROULETTE", "$18,400", 1]];
      return '<div class="gd-top">' + rows.map(function (r, i) {
        var n = names[i] || ["QTIP", "JUICE", "COUGAR"][i];
        var pic = f[n] ? '<img src="' + f[n] + '" alt="">' : '<span class="gd-noface">🙂</span>';
        return '<div class="gd-tile">' + pic + '<div class="gd-who"><b>' + r[0] + '</b><span>👑 ' + String(n).toUpperCase() + '</span></div>' +
          '<div class="gd-amt"><b>' + r[1] + '</b><span>' + r[2] + ' PT' + (r[2] > 1 ? 'S' : '') + '</span></div></div>';
      }).join("") + '<div class="gd-eg">EXAMPLE</div></div>';
    }
    function rgba(hex, a) {
      var n = parseInt(hex.slice(1), 16);
      return "rgba(" + (n >> 16) + "," + ((n >> 8) & 255) + "," + (n & 255) + "," + a + ")";
    }
    var R = Math.random;
    /* a dark brick wall, drawn once */
    function brickWall(w, h, S) {
      var cv = document.createElement("canvas"); cv.width = w; cv.height = h;
      var c = cv.getContext("2d"), bh = 30 * S, bw = 74 * S, m = 2.5 * S, row = 0, x, y;
      c.fillStyle = "#050507"; c.fillRect(0, 0, w, h);
      for (y = 0; y < h; y += bh, row++) {
        for (x = (row % 2 ? -bw / 2 : 0); x < w; x += bw) {
          var l = 15 + R() * 9, warm = R() * 7;
          c.fillStyle = "rgb(" + Math.round(l + warm) + "," + Math.round(l) + "," + Math.round(l + 3) + ")";
          c.fillRect(x + m / 2, y + m / 2, bw - m, bh - m);
          c.fillStyle = "rgba(255,255,255,.035)"; c.fillRect(x + m / 2, y + m / 2, bw - m, 1.5 * S);
        }
      }
      for (var i = 0; i < 3500; i++) {
        c.fillStyle = "rgba(" + (R() < 0.5 ? "255,255,255," : "0,0,0,") + (R() * 0.06).toFixed(3) + ")";
        var r = (0.5 + R() * 1.8) * S; c.fillRect(R() * w, R() * h, r, r);
      }
      var g = c.createRadialGradient(w / 2, h * 0.42, h * 0.15, w / 2, h * 0.42, h * 0.8);
      g.addColorStop(0, "rgba(0,0,0,0)"); g.addColorStop(1, "rgba(0,0,0,.72)");
      c.fillStyle = g; c.fillRect(0, 0, w, h);
      return cv;
    }
    /* one burst from a can: soft middle, overspray, flung spatter, runs */
    function splat(c, x, y, rad, color, S, strong) {
      var i, a, d, r;
      for (i = 0; i < 46; i++) {
        a = R() * 6.283; d = R() * rad * 0.55; r = rad * (0.18 + R() * 0.34);
        var px = x + Math.cos(a) * d * 1.25, py = y + Math.sin(a) * d * 0.8;
        var g = c.createRadialGradient(px, py, 0, px, py, r);
        g.addColorStop(0, rgba(color, 0.11 * strong)); g.addColorStop(1, rgba(color, 0));
        c.fillStyle = g; c.beginPath(); c.arc(px, py, r, 0, 6.283); c.fill();
      }
      c.fillStyle = rgba(color, 0.55 * strong);
      for (i = 0; i < 520; i++) {
        a = R() * 6.283; d = rad * (0.25 + Math.pow(R(), 0.7) * 1.15);
        r = (0.4 + R() * R() * 2.6) * S;
        c.beginPath(); c.arc(x + Math.cos(a) * d * 1.2, y + Math.sin(a) * d * 0.85, r, 0, 6.283); c.fill();
      }
      c.fillStyle = rgba(color, 0.8 * strong);
      for (i = 0; i < 13; i++) {
        a = R() * 6.283; d = rad * (0.6 + R() * 0.25); r = (3 + R() * 6) * S;
        for (var k = 0; k < 7; k++) {
          c.beginPath(); c.arc(x + Math.cos(a) * d * 1.2, y + Math.sin(a) * d * 0.85, r, 0, 6.283); c.fill();
          d += r * 2.4 + R() * 8 * S; r *= 0.68;
        }
      }
      for (i = 0; i < 6; i++) {
        var dx = x + (R() - 0.5) * rad * 1.6, dy = y + rad * (0.15 + R() * 0.3), len = (40 + R() * 150) * S, wd = (2.5 + R() * 4) * S;
        c.fillStyle = rgba(color, 0.6 * strong);
        c.fillRect(dx - wd / 2, dy, wd, len);
        c.beginPath(); c.arc(dx, dy + len, wd * 0.95, 0, 6.283); c.fill();
      }
    }
    /* scribbles a tagger leaves around a piece */
    function doodle(c, kind, x, y, sz, color, rot) {
      c.save(); c.translate(x, y); c.rotate(rot); c.strokeStyle = color; c.fillStyle = color;
      c.lineWidth = sz * 0.11; c.lineCap = "round"; c.lineJoin = "round"; c.shadowColor = color; c.shadowBlur = sz * 0.22;
      c.beginPath();
      if (kind === "crown") {
        c.moveTo(-sz / 2, sz * 0.3); c.lineTo(-sz / 2, -sz * 0.25); c.lineTo(-sz * 0.22, sz * 0.05); c.lineTo(0, -sz * 0.4);
        c.lineTo(sz * 0.22, sz * 0.05); c.lineTo(sz / 2, -sz * 0.25); c.lineTo(sz / 2, sz * 0.3); c.closePath();
      } else if (kind === "arrow") {
        c.moveTo(-sz / 2, sz * 0.1); c.quadraticCurveTo(0, -sz * 0.35, sz / 2, 0);
        c.moveTo(sz * 0.22, -sz * 0.26); c.lineTo(sz / 2, 0); c.lineTo(sz * 0.2, sz * 0.24);
      } else if (kind === "star") {
        for (var i = 0; i < 6; i++) { var a = -1.571 + i * 2.513; c[i ? "lineTo" : "moveTo"](Math.cos(a) * sz / 2, Math.sin(a) * sz / 2); }
      } else if (kind === "x") {
        c.moveTo(-sz / 3, -sz / 3); c.lineTo(sz / 3, sz / 3); c.moveTo(sz / 3, -sz / 3); c.lineTo(-sz / 3, sz / 3);
      } else if (kind === "swirl") {
        for (var t = 0; t < 14; t += 0.3) c[t ? "lineTo" : "moveTo"](Math.cos(t) * t * sz * 0.035, Math.sin(t) * t * sz * 0.035);
      } else {
        c.font = "900 italic " + sz * 0.7 + "px Impact, 'Arial Black', sans-serif"; c.textAlign = "center"; c.textBaseline = "middle";
        c.strokeText(kind, 0, 0);
      }
      c.stroke(); c.restore();
    }
    /* the heading as a piece: fat tilted letters, outline, drop, shine and runs */
    function piece(cv, lines, color, hi, cssW) {
      var d = Math.min(2, window.devicePixelRatio || 1), size = 74, pad = 26, FONT = "px Impact, Haettenschweiler, 'Arial Narrow Bold', 'Arial Black', sans-serif";
      var c = cv.getContext("2d");
      c.font = "900 italic " + size + FONT;
      var widest = Math.max.apply(null, lines.map(function (l) { return c.measureText(l).width * 0.93; }));
      if (widest > cssW - pad * 2) size = Math.max(34, Math.floor(size * (cssW - pad * 2) / widest));
      var lh = size * 0.98, cssH = Math.round(lines.length * lh + size * 0.2 + 64);
      cv.width = Math.round(cssW * d); cv.height = Math.round(cssH * d);
      cv.style.width = cssW + "px"; cv.style.height = cssH + "px";
      c.setTransform(d, 0, 0, d, 0, 0);
      c.font = "900 italic " + size + FONT; c.textBaseline = "alphabetic"; c.lineJoin = "round";
      var glyphs = [];
      lines.forEach(function (line, li) {
        var chars = line.split(""), ws = chars.map(function (ch) { return c.measureText(ch).width * 0.93; });
        var tot = ws.reduce(function (a, b) { return a + b; }, 0), x = (cssW - tot) / 2 + (li % 2 ? 10 : -6);
        chars.forEach(function (ch, i) {
          var p = chars.length > 1 ? i / (chars.length - 1) : 0.5;
          glyphs.push({ ch: ch, x: x + ws[i] / 2, y: size * 1.02 + li * lh - Math.sin(p * 3.14) * size * 0.09 + (R() - 0.5) * size * 0.1 - p * size * 0.1,
            rot: (R() - 0.5) * 0.2 - 0.05, sc: 0.94 + R() * 0.16, w: ws[i] });
          x += ws[i];
        });
      });
      function each(fn) {
        glyphs.forEach(function (g) {
          if (g.ch === " ") return;
          c.save(); c.translate(g.x, g.y); c.rotate(g.rot); c.scale(g.sc, g.sc); c.textAlign = "center"; fn(g); c.restore();
        });
      }
      // glow thrown on the wall, then the drop, then outlines out to in, then the fill
      c.shadowColor = color; c.shadowBlur = 26; c.fillStyle = rgba(color, 0.55); each(function (g) { c.fillText(g.ch, 0, 0); }); c.shadowBlur = 0;
      for (var k = 9; k > 0; k--) { c.strokeStyle = c.fillStyle = "#050507"; c.lineWidth = size * 0.2; each(function (g) { c.strokeText(g.ch, k, k); }); }
      c.strokeStyle = "#fff"; c.lineWidth = size * 0.22; each(function (g) { c.strokeText(g.ch, 0, 0); });
      c.strokeStyle = "#08080c"; c.lineWidth = size * 0.13; each(function (g) { c.strokeText(g.ch, 0, 0); });
      each(function (g) {
        var gr = c.createLinearGradient(0, -size * 0.74, 0, 0);
        gr.addColorStop(0, "#fff"); gr.addColorStop(0.22, hi); gr.addColorStop(0.62, color); gr.addColorStop(1, color);
        c.fillStyle = gr; c.fillText(g.ch, 0, 0);
      });
      // shine ticks and a few runs off the bottom of the letters
      c.fillStyle = "#fff";
      glyphs.forEach(function (g, i) {
        if (g.ch === " " || i % 2) return;
        c.save(); c.translate(g.x - g.w * 0.16, g.y - size * 0.56); c.rotate(-0.5);
        c.beginPath(); c.ellipse(0, 0, size * 0.035, size * 0.1, 0, 0, 6.283); c.fill(); c.restore();
      });
      var last = (lines.length - 1) * lh + size * 1.02;
      glyphs.forEach(function (g) {
        if (g.ch === " " || g.y < last - size * 0.4 || R() < 0.45) return;
        var dx = g.x + (R() - 0.5) * g.w * 0.4, len = 14 + R() * 38, wd = 3 + R() * 3;
        c.fillStyle = "#08080c"; c.fillRect(dx - wd / 2 - 1.5, g.y - 4, wd + 3, len + 4); c.beginPath(); c.arc(dx, g.y + len, wd * 0.9 + 1.5, 0, 6.283); c.fill();
        c.fillStyle = color; c.fillRect(dx - wd / 2, g.y - 6, wd, len + 4); c.beginPath(); c.arc(dx, g.y + len, wd * 0.9, 0, 6.283); c.fill();
      });
      c.fillStyle = color;
      for (var i = 0; i < 60; i++) { c.globalAlpha = 0.5 + R() * 0.5; c.beginPath(); c.arc(R() * cssW, R() * cssH, 0.6 + R() * R() * 3, 0, 6.283); c.fill(); }
      c.globalAlpha = 1;
    }
    var demoing = false;
    function demo(done) {
      if (demoing) { if (done) done(); return; }
      demoing = true;
      if (!document.getElementById("gd-style")) {
        var IMP = "Impact,Haettenschweiler,'Arial Narrow Bold','Arial Black',sans-serif";
        var st = document.createElement("style"); st.id = "gd-style";
        st.textContent =
          "#tt-demo{position:fixed;inset:0;z-index:10050;overflow:hidden;display:flex;flex-direction:column;align-items:center;" +
            "padding:calc(env(safe-area-inset-top,0px) + 50px) 20px calc(env(safe-area-inset-bottom,0px) + 20px);box-sizing:border-box;" +
            "background:#07070a;transition:opacity .35s ease;color:#fff;font-family:system-ui,sans-serif}" +
          "#tt-demo .gd-wall{position:absolute;inset:0;width:100%;height:100%}" +
          "#tt-demo .gd-wall.in{animation:gdWall .5s ease both}@keyframes gdWall{from{opacity:.25}to{opacity:1}}" +
          "#tt-demo .gd-skip{position:absolute;z-index:2;right:14px;top:calc(env(safe-area-inset-top,0px) + 14px);padding:8px 14px;border-radius:999px;border:1px solid rgba(255,255,255,.35);" +
            "background:rgba(0,0,0,.6);color:#fff;font:700 11px system-ui,sans-serif;letter-spacing:.14em}" +
          "#tt-demo .gd-card{position:relative;width:100%;max-width:400px;text-align:center;margin:auto 0;display:flex;flex-direction:column;align-items:center}" +
          "#tt-demo .gd-piece{display:block;margin:0 0 -8px;transform:rotate(-5deg)}" +
          "#tt-demo .gd-card.in .gd-piece{animation:gdSpray .5s cubic-bezier(.3,.7,.3,1) both}" +
          "@keyframes gdSpray{from{clip-path:inset(0 100% 0 0);opacity:.4}to{clip-path:inset(0 0 0 0);opacity:1}}" +
          "#tt-demo .gd-card.in .gd-art{animation:gdPop .45s .22s cubic-bezier(.2,1.5,.4,1) both}" +
          "#tt-demo .gd-card.in .gd-note{animation:gdSlap .4s .4s cubic-bezier(.2,1.4,.4,1) both}" +
          "@keyframes gdPop{from{opacity:0;transform:scale(.7) rotate(4deg)}to{opacity:1;transform:none}}" +
          "@keyframes gdSlap{from{opacity:0;transform:rotate(-7deg) scale(1.25)}to{opacity:1;transform:rotate(-1.4deg)}}" +
          "#tt-demo .gd-art{position:relative;min-height:120px;width:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;margin-bottom:20px}" +
          // 1: stencilled cash
          "#tt-demo .gd-row{display:flex;align-items:center;gap:8px}" +
          "#tt-demo .gd-cash{font:900 italic 27px " + IMP + ";padding:7px 12px 5px;background:#f2efe6;color:#0a0a0f;border:3px solid #0a0a0f;box-shadow:4px 5px 0 rgba(0,0,0,.7);transform:rotate(-4deg)}" +
          "#tt-demo .gd-cash.gd-up{background:#35E08A;transform:rotate(3deg) scale(1.08)}" +
          "#tt-demo .gd-arrow{font-size:28px;color:#FFD24D;text-shadow:2px 3px 0 #000}" +
          "#tt-demo .gd-crown{margin-top:16px;font:900 italic 19px " + IMP + ";letter-spacing:.08em;color:#FFD24D;-webkit-text-stroke:5px #0a0a0f;paint-order:stroke fill;transform:rotate(-2deg)}" +
          // 2: three throw-ups
          "#tt-demo .gd-pts{display:flex;gap:6px;width:100%;justify-content:center;align-items:flex-end}" +
          "#tt-demo .gd-pts>div{flex:1;max-width:116px;display:flex;flex-direction:column;align-items:center}" +
          "#tt-demo .gd-pts>div:nth-child(1){color:#FFD24D;transform:rotate(-7deg)}#tt-demo .gd-pts>div:nth-child(2){color:#FF4FA3;transform:rotate(4deg) translateY(8px)}#tt-demo .gd-pts>div:nth-child(3){color:#3D9BFF;transform:rotate(-3deg)}" +
          "#tt-demo .gd-pts b{font:900 italic 96px/0.9 " + IMP + ";-webkit-text-stroke:12px #08080c;paint-order:stroke fill;filter:drop-shadow(5px 6px 0 #08080c) drop-shadow(0 0 14px currentColor)}" +
          "#tt-demo .gd-pts>div:nth-child(2) b{font-size:84px}#tt-demo .gd-pts>div:nth-child(3) b{font-size:72px}" +
          "#tt-demo .gd-pts i{font:900 italic 17px " + IMP + ";letter-spacing:.14em;margin-top:8px;position:relative;z-index:1;-webkit-text-stroke:5px #08080c;paint-order:stroke fill}" +
          "#tt-demo .gd-pts span{margin-top:7px;padding:3px 8px 2px;background:#f2efe6;color:#0a0a0f;font:800 10px ui-monospace,Menlo,Consolas,monospace;letter-spacing:.1em;box-shadow:2px 3px 0 rgba(0,0,0,.7)}" +
          // 3: leaders, slapped up like stickers
          "#tt-demo .gd-top{width:100%;max-width:340px;display:flex;flex-direction:column;gap:9px}" +
          "#tt-demo .gd-tile{display:flex;align-items:center;gap:11px;padding:7px 12px 7px 7px;border-radius:6px;background:#f2efe6;color:#0a0a0f;border:3px solid #0a0a0f;box-shadow:5px 6px 0 rgba(0,0,0,.7);text-align:left}" +
          "#tt-demo .gd-tile:nth-child(1){transform:rotate(-2.2deg)}#tt-demo .gd-tile:nth-child(2){transform:rotate(1.6deg) translateX(6px)}#tt-demo .gd-tile:nth-child(3){transform:rotate(-1deg) translateX(-4px)}" +
          "#tt-demo .gd-tile img,#tt-demo .gd-noface{width:50px;height:50px;border-radius:50%;object-fit:cover;border:3px solid #0a0a0f;flex:none;display:flex;align-items:center;justify-content:center;font-size:26px;background:#222}" +
          "#tt-demo .gd-who{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}" +
          "#tt-demo .gd-who b{font:900 italic 20px " + IMP + ";letter-spacing:.04em}" +
          "#tt-demo .gd-who span{font:800 10.5px ui-monospace,Menlo,Consolas,monospace;letter-spacing:.08em;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}" +
          "#tt-demo .gd-amt{display:flex;flex-direction:column;align-items:flex-end;gap:3px}" +
          "#tt-demo .gd-amt b{font:900 italic 20px " + IMP + ";color:#0c8a45}" +
          "#tt-demo .gd-amt span{font:800 10px system-ui,sans-serif;letter-spacing:.14em;padding:2px 7px;background:#FF4FA3;color:#0a0a0f;border:2px solid #0a0a0f;transform:rotate(-4deg)}" +
          "#tt-demo .gd-eg{font:700 9px ui-monospace,Menlo,Consolas,monospace;letter-spacing:.24em;opacity:.6;margin-top:3px}" +
          // 4 and 5
          "#tt-demo .gd-hot{display:flex;align-items:center;gap:4px;transform:rotate(-4deg)}#tt-demo .gd-hot span{font-size:46px;line-height:1}#tt-demo .gd-hot span:last-child{transform:scaleX(-1)}" +
          "#tt-demo .gd-hot b{font:900 italic 104px/1 " + IMP + ";color:#FFD24D;-webkit-text-stroke:13px #08080c;paint-order:stroke fill;filter:drop-shadow(6px 7px 0 #08080c) drop-shadow(0 0 22px #FF6A2B)}" +
          "#tt-demo .gd-trophy{font-size:104px;line-height:1;transform:rotate(6deg);filter:drop-shadow(6px 7px 0 #08080c) drop-shadow(0 0 24px rgba(255,198,43,.7))}" +
          // the words, on a pasted-up note
          "#tt-demo .gd-note{position:relative;max-width:336px;padding:15px 16px 14px;background:rgba(9,9,13,.9);border:3px solid;box-shadow:6px 7px 0 rgba(0,0,0,.65);transform:rotate(-1.4deg)}" +
          "#tt-demo .gd-note:before,#tt-demo .gd-note:after{content:'';position:absolute;top:-11px;width:54px;height:19px;background:rgba(242,239,230,.82);box-shadow:0 2px 3px rgba(0,0,0,.4)}" +
          "#tt-demo .gd-note:before{left:-16px;transform:rotate(-32deg)}#tt-demo .gd-note:after{right:-16px;transform:rotate(30deg)}" +
          "#tt-demo .gd-note p{margin:0;font-size:15.5px;line-height:1.45;color:rgba(255,255,255,.86)}#tt-demo .gd-note b{color:#fff}" +
          "#tt-demo .gd-foot{position:relative;margin:0 0 22px;display:flex;flex-direction:column;align-items:center;gap:14px;width:100%}" +
          "#tt-demo .gd-dots{display:flex;gap:8px;align-items:center}#tt-demo .gd-dots i{width:8px;height:8px;border-radius:50%;background:rgba(255,255,255,.3);box-shadow:0 0 0 2px #08080c}#tt-demo .gd-dots i.on{width:12px;height:12px}" +
          "#tt-demo .gd-next{width:100%;max-width:290px;padding:14px 0 12px;border:3px solid #08080c;border-radius:4px;font:900 italic 24px " + IMP + ";letter-spacing:.08em;color:#08080c;" +
            "transform:skewX(-9deg) rotate(-1.5deg);box-shadow:6px 7px 0 #f2efe6}" +
          "#tt-demo .gd-next:active{transform:skewX(-9deg) rotate(-1.5deg) translate(4px,5px);box-shadow:2px 2px 0 #f2efe6}";
        document.head.appendChild(st);
      }
      var W = innerWidth, H = innerHeight, dpr = Math.min(2, window.devicePixelRatio || 1);
      var wrap = document.createElement("div"); wrap.id = "tt-demo";
      var wall = document.createElement("canvas"); wall.className = "gd-wall";
      wall.width = Math.round(W * dpr); wall.height = Math.round(H * dpr);
      var bricks = brickWall(wall.width, wall.height, dpr);
      var skip = document.createElement("button"); skip.type = "button"; skip.className = "gd-skip"; skip.textContent = "SKIP ›";
      var card = document.createElement("div"); card.className = "gd-card";
      var foot = document.createElement("div"); foot.className = "gd-foot";
      var dots = document.createElement("div"); dots.className = "gd-dots";
      var next = document.createElement("button"); next.type = "button"; next.className = "gd-next";
      foot.appendChild(dots); foot.appendChild(next);
      wrap.appendChild(wall); wrap.appendChild(skip); wrap.appendChild(card); wrap.appendChild(foot);
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
      var KINDS = ["crown", "arrow", "star", "x", "swirl", "4TB", "$"];
      function paintWall(c) {
        var g = wall.getContext("2d"), w = wall.width, h = wall.height, S = dpr;
        g.clearRect(0, 0, w, h); g.drawImage(bricks, 0, 0);
        splat(g, w * (0.12 + R() * 0.12), h * (0.74 + R() * 0.1), w * 0.3, c.second, S, 0.75);
        splat(g, w * (0.82 + R() * 0.1), h * (0.1 + R() * 0.08), w * 0.26, c.second, S, 0.6);
        splat(g, w * 0.5, h * 0.3, w * 0.5, c.color, S, 1);
        var spots = [[0.13, 0.11], [0.88, 0.34], [0.1, 0.44], [0.9, 0.7], [0.14, 0.9], [0.84, 0.92]];
        g.globalAlpha = 0.55;
        spots.forEach(function (p, i) {
          doodle(g, KINDS[Math.floor(R() * KINDS.length)], w * p[0] + (R() - 0.5) * 16 * S, h * p[1] + (R() - 0.5) * 22 * S,
            (38 + R() * 22) * S, i % 2 ? c.second : "#f2efe6", (R() - 0.5) * 0.9);
        });
        g.globalAlpha = 1;
      }
      function show() {
        var c = CARDS[at];
        paintWall(c);
        wall.className = "gd-wall"; card.className = "gd-card";
        card.innerHTML = '<canvas class="gd-piece"></canvas><div class="gd-art">' + (c.top ? topArt() : c.art) + '</div>' +
          '<div class="gd-note" style="border-color:' + c.color + '"><p>' + c.body + '</p></div>';
        piece(card.firstChild, c.head, c.color, c.hi, Math.min(400, W - 40));
        void card.offsetWidth; card.className = "gd-card in"; wall.className = "gd-wall in";
        dots.innerHTML = CARDS.map(function (_, i) { return '<i' + (i === at ? ' class="on" style="background:' + c.color + '"' : '') + '></i>'; }).join("");
        next.textContent = at === CARDS.length - 1 ? "LET'S PLAY" : "NEXT ›";
        next.style.background = c.color;
      }
      next.onclick = function () { if (at < CARDS.length - 1) { at++; show(); } else close(); };
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

