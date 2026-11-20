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
        c.drawImage(img, (w - iw) / 2, (h - ih) / 2 - h * 0.05, iw, ih);
        // where the logo sits inside the art (measured from the picture)
        box = { x: w / 2 - iw * 0.47, y: (h - ih) / 2 - h * 0.05 + ih * 0.2, w: iw * 0.94, h: ih * 0.5 };
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
        var cx = w / 2, cy = h * 0.765;
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

    /* ---- the how-it-works cards: four taps, same wall, same paint ---- */
    var CARDS = [
      { tag: "1 OF 4", head: "HOW YOU SCORE", color: "#3D8BFF",
        art: '<div class="gd-row"><span class="gd-cash">$10,000</span><span class="gd-arrow">➜</span><span class="gd-cash gd-up">$15,000+</span></div><div class="gd-crown">👑 TAKES THE POINTS</div>',
        body: "<b>Every game starts you at $10,000.</b> Whoever has the most money on a game at midnight takes that game's points. You have to be up at least $5,000 — that's <b>$15,000 or more</b>." },
      { tag: "2 OF 4", head: "3 · 2 · 1", color: "#FF4FA3",
        art: '<div class="gd-pts"><div><b>3</b><i>PTS</i><span>3 GAMES</span></div><div><b>2</b><i>PTS</i><span>5 GAMES</span></div><div><b>1</b><i>PT</i><span>THE REST</span></div></div>',
        body: "<b>Games are worth 3, 2 or 1 points.</b> They get shuffled every day. Each game's button shows what it's worth today and who's on top." },
      { tag: "3 OF 4", head: "HOT GAMES", color: "#FF7A3D",
        art: '<div class="gd-hot">🔥 <b>1.5×</b></div>',
        body: "<b>Two games a day pay 1.5× on every win.</b> Different games each day. Look for the 🔥 on the menu." },
      { tag: "4 OF 4", head: "MOST POINTS WINS", color: "#FFD24D",
        art: '<div class="gd-trophy">🏆</div>',
        body: "<b>Points add up all week,</b> Sunday through Saturday night. Go broke on a game? <b>REBUY is free</b> and puts you back at $10,000." }
    ];
    var demoing = false;
    function demo(done) {
      if (demoing) { if (done) done(); return; }
      demoing = true;
      if (!document.getElementById("gd-style")) {
        var st = document.createElement("style"); st.id = "gd-style";
        st.textContent =
          "#tt-demo{position:fixed;inset:0;z-index:10050;overflow:hidden;display:flex;flex-direction:column;align-items:center;justify-content:center;" +
            "padding:calc(env(safe-area-inset-top,0px) + 54px) 22px calc(env(safe-area-inset-bottom,0px) + 22px);box-sizing:border-box;" +
            "background:radial-gradient(120% 70% at 50% 30%,#16161f 0%,#0b0b10 55%,#060609 100%);transition:opacity .35s ease;color:#fff;font-family:system-ui,sans-serif}" +
          "#tt-demo:before{content:'';position:absolute;inset:0;opacity:.5;background:" +
            "repeating-linear-gradient(0deg,transparent 0 63px,rgba(255,255,255,.035) 63px 64px),repeating-linear-gradient(90deg,transparent 0 127px,rgba(255,255,255,.03) 127px 128px)}" +
          "#tt-demo .gd-skip{position:absolute;right:14px;top:calc(env(safe-area-inset-top,0px) + 14px);padding:8px 14px;border-radius:999px;border:1px solid rgba(255,255,255,.35);" +
            "background:rgba(0,0,0,.45);color:#fff;font:700 11px system-ui,sans-serif;letter-spacing:.14em}" +
          "#tt-demo .gd-card{position:relative;width:100%;max-width:400px;text-align:center;margin:auto 0}" +
          "#tt-demo .gd-card.in{animation:gdIn .42s cubic-bezier(.2,1.3,.4,1) both}" +
          "@keyframes gdIn{from{opacity:0;transform:translateY(18px) scale(.94)}to{opacity:1;transform:none}}" +
          "#tt-demo .gd-splat{position:absolute;left:50%;top:38%;width:340px;height:340px;margin:-170px 0 0 -170px;border-radius:50%;filter:blur(38px);opacity:.34;pointer-events:none}" +
          "#tt-demo .gd-tag{position:relative;font:700 10px ui-monospace,Menlo,Consolas,monospace;letter-spacing:.22em;opacity:.6;margin-bottom:10px}" +
          "#tt-demo h2{position:relative;margin:0 0 20px;font:900 italic 46px/1 Impact,Haettenschweiler,'Arial Narrow Bold','Arial Black',sans-serif;letter-spacing:.01em;" +
            "transform:rotate(-4deg);-webkit-text-stroke:2px #0a0a0f;paint-order:stroke fill;text-shadow:3px 4px 0 #0a0a0f,0 0 26px currentColor}" +
          "#tt-demo .gd-art{position:relative;min-height:128px;display:flex;flex-direction:column;align-items:center;justify-content:center;margin-bottom:18px}" +
          "#tt-demo .gd-row{display:flex;align-items:center;gap:10px}" +
          "#tt-demo .gd-cash{font:900 italic 27px Impact,'Arial Black',sans-serif;padding:8px 12px;border-radius:10px;background:rgba(255,255,255,.08);border:2px dashed rgba(255,255,255,.3)}" +
          "#tt-demo .gd-cash.gd-up{color:#5CFF9D;border-color:#5CFF9D;background:rgba(92,255,157,.1);text-shadow:0 0 16px rgba(92,255,157,.7)}" +
          "#tt-demo .gd-arrow{font-size:24px;color:#FFD24D}" +
          "#tt-demo .gd-crown{margin-top:14px;font:800 14px system-ui,sans-serif;letter-spacing:.14em;color:#FFD24D}" +
          "#tt-demo .gd-pts{display:flex;gap:10px;width:100%;justify-content:center}" +
          "#tt-demo .gd-pts>div{flex:1;max-width:108px;padding:12px 4px 10px;border-radius:14px;background:rgba(255,255,255,.07);border:2px solid currentColor;display:flex;flex-direction:column;align-items:center}" +
          "#tt-demo .gd-pts>div:nth-child(1){color:#FFD24D;transform:rotate(-3deg)}#tt-demo .gd-pts>div:nth-child(2){color:#FF4FA3;transform:rotate(2deg)}#tt-demo .gd-pts>div:nth-child(3){color:#3D8BFF;transform:rotate(-2deg)}" +
          "#tt-demo .gd-pts b{font:900 italic 54px/1 Impact,'Arial Black',sans-serif;text-shadow:0 0 18px currentColor}" +
          "#tt-demo .gd-pts i{font:800 11px system-ui,sans-serif;letter-spacing:.18em;font-style:normal}" +
          "#tt-demo .gd-pts span{margin-top:8px;font:700 10px ui-monospace,Menlo,Consolas,monospace;letter-spacing:.1em;color:#fff;opacity:.85}" +
          "#tt-demo .gd-hot{font-size:60px;line-height:1}#tt-demo .gd-hot b{font:900 italic 76px/1 Impact,'Arial Black',sans-serif;color:#FF7A3D;-webkit-text-stroke:2px #0a0a0f;paint-order:stroke fill;text-shadow:3px 4px 0 #0a0a0f,0 0 30px #FF7A3D}" +
          "#tt-demo .gd-trophy{font-size:92px;line-height:1;filter:drop-shadow(0 0 22px rgba(255,210,77,.6))}" +
          "#tt-demo p{position:relative;margin:0 auto;max-width:330px;font-size:16px;line-height:1.45;color:rgba(255,255,255,.82)}#tt-demo p b{color:#fff}" +
          "#tt-demo .gd-foot{position:relative;margin:0 0 26px;display:flex;flex-direction:column;align-items:center;gap:14px;width:100%}" +
          "#tt-demo .gd-dots{display:flex;gap:7px}#tt-demo .gd-dots i{width:7px;height:7px;border-radius:50%;background:rgba(255,255,255,.25)}#tt-demo .gd-dots i.on{background:#fff}" +
          "#tt-demo .gd-next{width:100%;max-width:300px;padding:15px 0;border:0;border-radius:999px;font:900 italic 20px Impact,'Arial Black',sans-serif;letter-spacing:.08em;color:#0a0a0f;box-shadow:0 6px 0 rgba(0,0,0,.5)}";
        document.head.appendChild(st);
      }
      var wrap = document.createElement("div"); wrap.id = "tt-demo";
      var skip = document.createElement("button"); skip.type = "button"; skip.className = "gd-skip"; skip.textContent = "SKIP ›";
      var card = document.createElement("div"); card.className = "gd-card";
      var foot = document.createElement("div"); foot.className = "gd-foot";
      var dots = document.createElement("div"); dots.className = "gd-dots";
      var next = document.createElement("button"); next.type = "button"; next.className = "gd-next";
      foot.appendChild(dots); foot.appendChild(next);
      wrap.appendChild(skip); wrap.appendChild(card); wrap.appendChild(foot);
      document.body.appendChild(wrap);
      var snd = Sound(), at = 0, closed = false;
      function close() {
        if (closed) return; closed = true;
        try { snd.stop(); } catch (e) {}
        wrap.style.opacity = "0";
        setTimeout(function () { wrap.remove(); demoing = false; }, 360);
        if (done) done();
      }
      function show() {
        var c = CARDS[at];
        card.className = "gd-card";
        card.innerHTML = '<div class="gd-splat" style="background:' + c.color + '"></div><div class="gd-tag">' + c.tag + '</div>' +
          '<h2 style="color:' + c.color + '">' + c.head + '</h2><div class="gd-art">' + c.art + '</div><p>' + c.body + '</p>';
        void card.offsetWidth; card.className = "gd-card in";
        dots.innerHTML = CARDS.map(function (_, i) { return '<i' + (i === at ? ' class="on"' : '') + '></i>'; }).join("");
        next.textContent = at === CARDS.length - 1 ? "LET'S PLAY" : "NEXT ›";
        next.style.background = c.color;
        try { snd.hiss(true); setTimeout(function () { try { snd.hiss(false); } catch (e) {} }, 260); } catch (e) {}
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

