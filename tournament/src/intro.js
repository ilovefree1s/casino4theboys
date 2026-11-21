  /* ---------- the tournament intro: the 4 The Boys piece gets sprayed onto a wall ----------
     About five seconds, with a spray can's rattle and hiss. Plays once, right after a player picks
     who they are and goes into the tournament. Tap SKIP (or anywhere) to cut it short.
     Open the game with #intro on the end of the address to watch it any time. */
  var Intro = (function () {
    var LEN = 5220, playing = false;
    // the title's paint font, when one is built in (a spray-can face, cut down to capitals and numbers)
    var SPRAY_B64 = "/*SPRAYFONT*/", SPRAY_SCALE = +"/*SPRAYSCALE*/" || 1, SPRAY_OK = false, SPRAY_FACE = null;
    if (SPRAY_B64.length > 100 && typeof FontFace !== "undefined") {
      try {
        var ff = new FontFace("C4Spray", "url(data:font/woff2;base64," + SPRAY_B64 + ") format('woff2')");
        SPRAY_FACE = ff.load().then(function (f) { document.fonts.add(f); SPRAY_OK = true; });
        SPRAY_FACE.catch(function () {});
      } catch (e) { SPRAY_FACE = null; }
    }

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
      // the rattle: Justin's own can, recorded. If the phone can't decode it, the made-up one below stands in.
      function rattle() {
        // the rattle: a handful of dull clacks, a little uneven, each with the can ringing after it
        // (a few high, out-of-tune partials that die away over a tenth of a second: the metal in it)
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
          var base = 1850 + (n % 3) * 140, hit = (n % 2 ? 0.8 : 1) * v;
          [[1, 0.2, 0.13], [1.59, 0.16, 0.11], [2.31, 0.13, 0.09], [3.42, 0.09, 0.07], [4.87, 0.06, 0.05]].forEach(function (pt) {
            var r = ctx.createOscillator(), rg = ctx.createGain();
            r.type = "sine"; r.frequency.value = base * pt[0] * (1 + (Math.random() - 0.5) * 0.02);
            rg.gain.setValueAtTime(0.0001, t); rg.gain.exponentialRampToValueAtTime(pt[1] * hit, t + 0.002);
            rg.gain.exponentialRampToValueAtTime(0.0001, t + pt[2]);
            r.connect(rg); rg.connect(ctx.destination); r.start(t); r.stop(t + pt[2] + 0.02);
          });
        });
      }
      try {
        var bin = atob("/*CANSHAKE*/"), bytes = new Uint8Array(bin.length);
        for (var bi = 0; bi < bin.length; bi++) bytes[bi] = bin.charCodeAt(bi);
        var played = false, playIt = function (buf) {
          if (played) return; played = true;
          var cs = ctx.createBufferSource(), cg = ctx.createGain();
          cs.buffer = buf; cg.gain.value = Math.min(1.8, v * 2.6);
          cs.connect(cg); cg.connect(ctx.destination); cs.start();
        }, miss = function () { if (!played) { played = true; rattle(); } };
        var pr = ctx.decodeAudioData(bytes.buffer, playIt, miss);
        if (pr && pr.catch) pr.catch(miss);
      } catch (e) { rattle(); }
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
      // once the paint is on, SKIP goes and this comes up: the way out
      var go = document.createElement("button");
      go.type = "button"; go.textContent = "GAMBLE!";
      go.style.cssText = "position:absolute;left:50%;bottom:calc(env(safe-area-inset-bottom,0px) + 34px);transform:translateX(-50%) rotate(-2deg);" +
        "min-width:230px;padding:15px 30px;border-radius:16px;border:3px solid #07060b;background:linear-gradient(180deg,#fff36b,#ffb21f 55%,#ff6a00);" +
        "color:#07060b;font:900 italic 30px Impact,Haettenschweiler,'Arial Narrow Bold','Arial Black',sans-serif;letter-spacing:.06em;" +
        "box-shadow:0 0 0 3px #ff2e88,0 10px 26px rgba(0,0,0,.6);opacity:0;pointer-events:none;transition:opacity .35s ease";
      wrap.appendChild(cv); wrap.appendChild(skip); wrap.appendChild(go);
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
      /* a day's piece: GRAFFITI WEEK itself, sprayed big in the middle in the can's blue, with the day over it */
      function paintWords() {
        var c = art.getContext("2d"), w = art.width, h = art.height;
        var fam = SPRAY_OK ? "C4Spray" : "Impact, Haettenschweiler, 'Arial Black', sans-serif";
        var words = ["GRAFFITI", "WEEK"], size = 112 * S * (SPRAY_OK ? SPRAY_SCALE : 1);
        c.clearRect(0, 0, w, h);
        c.font = size + "px " + fam;
        while (c.measureText(words[0]).width > w * 0.86 && size > 20) { size -= 2; c.font = size + "px " + fam; }
        var cx = w / 2, cy = h * 0.5, lh = size * 0.98, tw = c.measureText(words[0]).width, i, x, y, r;
        c.save(); c.translate(cx, cy); c.rotate(-0.07);
        c.textAlign = "center"; c.textBaseline = "middle";
        words.forEach(function (wd, k) {
          var yy = (k - 0.5) * lh;
          c.shadowColor = "rgba(70,120,255,.95)"; c.shadowBlur = 30 * S; c.fillStyle = "rgba(70,120,255,.4)"; c.fillText(wd, 0, yy);
          c.shadowColor = "rgba(150,90,255,.95)"; c.shadowBlur = 12 * S; c.fillStyle = "rgba(150,90,255,.55)"; c.fillText(wd, 0, yy);
          var fg = c.createLinearGradient(0, yy - size / 2, 0, yy + size / 2);
          fg.addColorStop(0, "#eef8ff"); fg.addColorStop(0.45, "#7cc8ff"); fg.addColorStop(1, "#7a52ff");
          c.shadowColor = "rgba(160,210,255,.9)"; c.shadowBlur = 3 * S; c.fillStyle = fg; c.fillText(wd, 0, yy);
        });
        c.shadowBlur = 0; c.shadowColor = "transparent";
        c.globalCompositeOperation = "destination-out";            // thin patches where the can moved fast
        for (i = 0; i < 1300; i++) {
          x = (Math.random() - 0.5) * (tw + 40 * S); y = (Math.random() - 0.5) * lh * 2.2; r = (0.4 + Math.random() * 1.4) * S;
          c.fillStyle = "rgba(0,0,0," + (0.25 + Math.random() * 0.5).toFixed(2) + ")"; c.fillRect(x, y, r, r);
        }
        c.globalCompositeOperation = "source-over";
        for (i = 0; i < 700; i++) {                                // specks thrown wide of the letters
          x = (Math.random() - 0.5) * (tw + 90 * S); y = (Math.random() - 0.5) * lh * 2.8; r = (0.5 + Math.random() * 1.6) * S;
          c.fillStyle = (Math.random() < 0.6 ? "rgba(120,190,255," : "rgba(160,100,255,") + (0.25 + Math.random() * 0.6).toFixed(2) + ")";
          c.beginPath(); c.arc(x, y, r, 0, 6.283); c.fill();
        }
        c.restore();
        box = { x: cx - tw / 2 - 18 * S, y: cy - lh - 6 * S, w: tw + 36 * S, h: lh * 2 + 12 * S };
        ready = true;
      }
      if (opts.quick) {
        var wordsDone = false, wordsGo = function () { if (!wordsDone) { wordsDone = true; paintWords(); } };
        if (SPRAY_FACE && !SPRAY_OK) { SPRAY_FACE.then(wordsGo, wordsGo); setTimeout(wordsGo, 1500); } else wordsGo();
      } else img.src = src;

      /* the words that go on after the piece */
      var line1 = opts.title || "GRAFFITI WEEK", line2 = opts.sub || "EVERY GAME · $10,000 · MOST POINTS WINS";
      var tBox = null;
      function paintTitle() {
        var c = txt.getContext("2d"), w = txt.width, h = txt.height;
        c.setTransform(1, 0, 0, 1, 0, 0); c.globalCompositeOperation = "source-over"; c.clearRect(0, 0, w, h);
        if (SPRAY_OK) return paintSpray(c, w, h);
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
      }
      /* The title as paint, not type: no outline, soft edges, a halo of overspray, thin patches and specks. */
      function paintSpray(c, w, h) {
        var size = 74 * S * SPRAY_SCALE;
        c.font = size + "px C4Spray";
        while (c.measureText(line1).width > w * 0.9 && size > 20) { size -= 2; c.font = size + "px C4Spray"; }
        c.textAlign = "center"; c.textBaseline = "middle";
        var cx = w / 2, cy = h * 0.15, tw = c.measureText(line1).width;
        c.save(); c.translate(cx, cy); c.rotate(-0.055);
        // the cloud that lands round the letters before the line is tight
        c.shadowColor = "rgba(255,46,136,.9)"; c.shadowBlur = 26 * S; c.fillStyle = "rgba(255,46,136,.35)"; c.fillText(line1, 0, 0);
        c.shadowColor = "rgba(255,150,30,.95)"; c.shadowBlur = 11 * S; c.fillStyle = "rgba(255,150,30,.6)"; c.fillText(line1, 0, 0);
        // the paint itself, a touch soft at the edge
        var fg = c.createLinearGradient(0, -size / 2, 0, size / 2);
        fg.addColorStop(0, "#fff9b0"); fg.addColorStop(0.45, "#ffc93a"); fg.addColorStop(1, "#ff7a12");
        c.shadowColor = "rgba(255,200,60,.9)"; c.shadowBlur = 3 * S; c.fillStyle = fg; c.fillText(line1, 0, 0);
        c.shadowBlur = 0; c.shadowColor = "transparent";
        // thin patches where the can moved fast: bits of wall showing through
        c.globalCompositeOperation = "destination-out";
        var i, x, y, r;
        for (i = 0; i < 900; i++) {
          x = (Math.random() - 0.5) * (tw + 40 * S); y = (Math.random() - 0.5) * size * 1.2; r = (0.4 + Math.random() * 1.3) * S;
          c.fillStyle = "rgba(0,0,0," + (0.25 + Math.random() * 0.5).toFixed(2) + ")"; c.fillRect(x, y, r, r);
        }
        // and specks thrown wide of the letters
        c.globalCompositeOperation = "source-over";
        for (i = 0; i < 520; i++) {
          x = (Math.random() - 0.5) * (tw + 90 * S); y = (Math.random() - 0.5) * size * 1.9; r = (0.5 + Math.random() * 1.6) * S;
          c.fillStyle = (Math.random() < 0.6 ? "rgba(255,190,50," : "rgba(255,60,140,") + (0.25 + Math.random() * 0.6).toFixed(2) + ")";
          c.beginPath(); c.arc(x, y, r, 0, 6.283); c.fill();
        }
        c.restore();
        tBox = { x: cx - tw / 2 - 30 * S, y: cy - size * 0.85, w: tw + 60 * S, h: size * 1.7, cy: cy, size: size * 0.8 };
      }
      paintTitle();
      if (SPRAY_FACE && !SPRAY_OK) SPRAY_FACE.then(function () { SPRAY_OK = true; paintTitle(); }, function () {});

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
      // for a day's piece the can works across GRAFFITI, comes back, and works across WEEK
      var WORDS = (function () {
        var segs = [], row = function (y, x0, x1) { var pts = []; for (var i = 0; i <= 30; i++) { var u = i / 30; pts.push([x0 + (x1 - x0) * u, y + Math.sin(u * 19) * 0.1]); } return pts; };
        var a = row(0.27, 0.03, 0.97), b = row(0.76, 0.22, 0.8);
        segs.push({ pts: a, down: true });
        segs.push({ pts: [a[a.length - 1], b[0]], down: false });
        segs.push({ pts: b, down: true });
        var legs = [], total = 0;
        segs.forEach(function (sg) {
          for (var k = 1; k < sg.pts.length; k++) {
            var dx = sg.pts[k][0] - sg.pts[k - 1][0], dy = (sg.pts[k][1] - sg.pts[k - 1][1]) * 0.5;
            var len = Math.sqrt(dx * dx + dy * dy) * (sg.down ? 1 : 0.4);
            legs.push({ a: sg.pts[k - 1], b: sg.pts[k], at: total, len: len, down: sg.down }); total += len;
          }
        });
        return { legs: legs, total: total };
      })();
      var PATH = opts.quick ? WORDS : ART;
      function artPos(p) {
        var d = Math.max(0, Math.min(1, p)) * PATH.total, L = PATH.legs[PATH.legs.length - 1];
        for (var i = 0; i < PATH.legs.length; i++) { if (d <= PATH.legs[i].at + PATH.legs[i].len) { L = PATH.legs[i]; break; } }
        var u = L.len ? Math.min(1, (d - L.at) / L.len) : 1;
        return { x: box.x + box.w * (L.a[0] + (L.b[0] - L.a[0]) * u), y: box.y + box.h * (L.a[1] + (L.b[1] - L.a[1]) * u), down: L.down };
      }
      function txtPos(p) {
        return { x: tBox.x + tBox.w * p, y: tBox.cy + Math.sin(p * 22) * tBox.h * 0.22 };
      }
      /* Drips: they run from the real bottom edge of a painted letter, in that letter's own colour.
         Columns are tried across the piece; each takes the lowest solid run of paint it finds (a few pixels
         of solid colour, so a stray speck is never mistaken for a letter). */
      var drips = [];
      function dripsFrom(cnv, bx, n) {
        var c = cnv.getContext("2d");
        var x0 = Math.max(0, Math.floor(bx.x)), y0 = Math.max(0, Math.floor(bx.y));
        var w = Math.min(cnv.width - x0, Math.ceil(bx.w)), h = Math.min(cnv.height - y0, Math.ceil(bx.h));
        if (w < 4 || h < 4) return;
        var d = c.getImageData(x0, y0, w, h).data, run = Math.max(3, Math.round(3 * S)), used = [], got = 0, tries = 0;
        function solid(x, y) { return y >= 0 && d[(y * w + x) * 4 + 3] > 225; }
        while (got < n && tries++ < 120) {
          var cx = Math.floor(w * (0.05 + 0.9 * Math.random()));
          if (used.some(function (u) { return Math.abs(u - cx) < w * 0.06; })) continue;
          for (var yy = h - 1; yy >= run; yy--) {
            if (!solid(cx, yy)) continue;
            var ok = true; for (var k = 1; k <= run; k++) if (!solid(cx, yy - k)) { ok = false; break; }
            if (!ok) continue;
            var i = ((yy - run) * w + cx) * 4;
            drips.push({ x: x0 + cx, y: y0 + yy - 2 * S, len: 0, max: (14 + Math.random() * 34) * S, w: (2.2 + Math.random() * 2.2) * S,
                         col: [d[i], d[i + 1], d[i + 2]], sp: (0.4 + Math.random() * 0.8) * S });
            used.push(cx); got++;
            break;
          }
        }
      }
      function drawDrips(now) {
        drips.forEach(function (dp) {
          dp.len = Math.min(dp.max, dp.len + dp.sp * (now - last) / 16 * (1 - dp.len / dp.max * 0.7));
          var c = dp.col, top = "rgb(" + c + ")", end = "rgb(" + c.map(function (v) { return Math.round(v * 0.78); }) + ")";
          var gr = g.createLinearGradient(0, dp.y, 0, dp.y + dp.len);
          gr.addColorStop(0, top); gr.addColorStop(1, end);
          // a little wider where it leaves the letter, thinning as it runs, and a bead at the end
          g.fillStyle = gr;
          g.beginPath();
          g.moveTo(dp.x - dp.w * 0.75, dp.y);
          g.quadraticCurveTo(dp.x - dp.w * 0.45, dp.y + dp.len * 0.5, dp.x - dp.w * 0.38, dp.y + dp.len);
          g.lineTo(dp.x + dp.w * 0.38, dp.y + dp.len);
          g.quadraticCurveTo(dp.x + dp.w * 0.45, dp.y + dp.len * 0.5, dp.x + dp.w * 0.75, dp.y);
          g.closePath(); g.fill();
          g.fillStyle = end; g.beginPath(); g.arc(dp.x, dp.y + dp.len, dp.w * 0.62, 0, 6.283); g.fill();
          g.fillStyle = "rgba(255,255,255,.35)"; g.beginPath(); g.arc(dp.x - dp.w * 0.2, dp.y + dp.len - dp.w * 0.2, dp.w * 0.18, 0, 6.283); g.fill();
        });
      }

      /* Overspray over the whole wall, not only round the letters: it lands a bit at a time while the can
         works, blue while the blue goes on and warm while the warm goes on, and stays. */
      var spat = layer(), specks = { blue: [], warm: [] };
      (function () {
        var w = spat.width, h = spat.height, i;
        function add(list, n, cols) {
          for (i = 0; i < n; i++) {
            var big = Math.random() < 0.02;
            list.push({ x: Math.random() * w, y: Math.random() * h, r: (big ? 1.8 + Math.random() * 2 : 0.4 + Math.random() * 1.2) * S,
                        a: big ? 0.55 + Math.random() * 0.35 : 0.22 + Math.random() * 0.55, col: cols[Math.floor(Math.random() * cols.length)],
                        at: Math.random(), big: big });
          }
          list.sort(function (p, q) { return p.at - q.at; });
          list.i = 0;
        }
        add(specks.blue, 1100, ["110,170,255", "150,110,255", "90,200,255"]);
        add(specks.warm, 900, ["255,190,50", "255,120,30", "255,60,140", "255,240,120"]);
      })();
      function spray(list, upTo) {
        var c = spat.getContext("2d");
        while (list.i < list.length && list[list.i].at <= upTo) {
          var p = list[list.i++];
          c.fillStyle = "rgba(" + p.col + "," + p.a.toFixed(2) + ")";
          c.beginPath(); c.arc(p.x, p.y, p.r, 0, 6.283); c.fill();
          if (p.big) for (var k = 0; k < 6; k++) {            // a splat throws a few satellites
            var an = Math.random() * 6.283, dd = p.r * (1.6 + Math.random() * 2.4);
            c.beginPath(); c.arc(p.x + Math.cos(an) * dd, p.y + Math.sin(an) * dd, (0.4 + Math.random() * 0.9) * S, 0, 6.283); c.fill();
          }
        }
      }

      /* ---------- yesterday's results, sprayed on after a coat of black ----------
         opts.results: [{ place, name, pts, face }], already the top three (ties share a place). */
      var RES = opts.quick && opts.results && opts.results.length ? opts.results.slice() : null;
      var res = null, prevC = 0, covered = false, goShown = false;
      if (RES) {
        RES.sort(function (a, b) { return b.place - a.place || a.pts - b.pts; });     // 3rd at the top, 1st last
        RES.forEach(function (r) { if (r.face) { r.img = new Image(); r.img.src = r.face; } });
      }
      function famNow() { return SPRAY_OK ? "C4Spray" : "Impact, Haettenschweiler, 'Arial Black', sans-serif"; }
      function sprayText(c, text, x, y, size, cols, glow, align) {
        c.font = size + "px " + famNow(); c.textAlign = align || "center"; c.textBaseline = "middle";
        c.shadowColor = glow; c.shadowBlur = 18 * S; c.fillStyle = glow; c.globalAlpha = 0.45; c.fillText(text, x, y); c.globalAlpha = 1;
        var fg = c.createLinearGradient(0, y - size / 2, 0, y + size / 2);
        fg.addColorStop(0, cols[0]); fg.addColorStop(0.5, cols[1]); fg.addColorStop(1, cols[2]);
        c.shadowColor = glow; c.shadowBlur = 3 * S; c.fillStyle = fg; c.fillText(text, x, y);
        c.shadowBlur = 0; c.shadowColor = "transparent";
        return c.measureText(text).width;
      }
      function roughen(c, bx, n) {                     // thin patches and stray specks, like the rest of the wall
        var i, x, y, r;
        c.save(); c.globalCompositeOperation = "destination-out";
        for (i = 0; i < n; i++) { x = bx.x + Math.random() * bx.w; y = bx.y + Math.random() * bx.h; r = (0.4 + Math.random() * 1.3) * S;
          c.fillStyle = "rgba(0,0,0," + (0.2 + Math.random() * 0.5).toFixed(2) + ")"; c.fillRect(x, y, r, r); }
        c.restore();
      }
      var MEDAL = { 1: { cols: ["#fff8c4", "#ffd24d", "#ff9a1f"], glow: "rgba(255,190,40,.95)", ring: "#ffd24d", word: "1ST" },
                    2: { cols: ["#ffffff", "#d4dcea", "#8a9bb5"], glow: "rgba(170,200,255,.9)", ring: "#d4dcea", word: "2ND" },
                    3: { cols: ["#ffe0bd", "#e8914f", "#a3531f"], glow: "rgba(240,130,60,.9)", ring: "#e8914f", word: "3RD" } };
      function buildRes() {
        var w = cv.width, h = cv.height, n = RES.length;
        res = { black: layer(), cover: layer(), items: [] };
        var bc = res.black.getContext("2d");
        bc.fillStyle = "#060608"; bc.fillRect(0, 0, w, h);
        for (var i = 0; i < 1400; i++) { bc.fillStyle = "rgba(255,255,255," + (Math.random() * 0.035).toFixed(3) + ")"; bc.fillRect(Math.random() * w, Math.random() * h, 1.4 * S, 1.4 * S); }
        // the heading
        var tl = layer(), tc = tl.getContext("2d"), tsz = 46 * S * SPRAY_SCALE, ty = h * 0.15;
        tc.font = tsz + "px " + famNow();
        while (tc.measureText(opts.resultsTitle || "RESULTS").width > w * 0.88 && tsz > 18) { tsz -= 2; tc.font = tsz + "px " + famNow(); }
        tc.save(); tc.translate(w / 2, ty); tc.rotate(-0.045);
        var tw = sprayText(tc, opts.resultsTitle || "RESULTS", 0, 0, tsz, ["#fff9b0", "#ffc93a", "#ff7a12"], "rgba(255,46,136,.9)");
        tc.restore();
        var tb = { x: w / 2 - tw / 2 - 16 * S, y: ty - tsz * 0.75, w: tw + 32 * S, h: tsz * 1.5, cy: ty };
        roughen(tc, tb, 500);
        res.items.push({ cnv: tl, box: tb, mask: layer(), prev: 0, r: tsz * 0.7 });
        // a line for each of the top three
        var top = h * 0.32, bottom = h * 0.68, step = (bottom - top) / Math.max(1, n - 1 || 1);
        if (n === 1) top = h * 0.48;
        RES.forEach(function (r, k) {
          var cy = n === 1 ? top : top + step * k, big = r.place === 1, md = MEDAL[r.place] || MEDAL[3];
          var cnv = layer(), c = cnv.getContext("2d"), x0 = w * 0.06, x1 = w * 0.94;
          var rot = (k % 2 ? 0.025 : -0.03);
          c.save(); c.translate(w / 2, cy); c.rotate(rot); c.translate(-w / 2, -cy);
          var psz = (big ? 38 : 33) * S * SPRAY_SCALE;
          var pw = sprayText(c, md.word, x0, cy, psz, md.cols, md.glow, "left");
          var fr = (big ? 30 : 25) * S, fx = x0 + Math.max(pw, 60 * S) + 12 * S + fr;
          // the face, sprayed round like a stencil
          c.save(); c.beginPath(); c.arc(fx, cy, fr, 0, 6.283); c.closePath();
          c.fillStyle = "#1a1a22"; c.fill(); c.clip();
          if (r.img && r.img.complete && r.img.naturalWidth) c.drawImage(r.img, fx - fr, cy - fr, fr * 2, fr * 2);
          c.restore();
          c.lineWidth = 3.5 * S; c.strokeStyle = md.ring; c.shadowColor = md.glow; c.shadowBlur = 12 * S;
          c.beginPath(); c.arc(fx, cy, fr, 0, 6.283); c.stroke(); c.shadowBlur = 0;
          // the points, right-hand end
          var ptxt = r.pts + (r.pts === 1 ? " PT" : " PTS"), qsz = 28 * S * SPRAY_SCALE;
          c.font = qsz + "px " + famNow();
          var qw = c.measureText(ptxt).width;
          sprayText(c, ptxt, x1, cy, qsz, ["#fff9b0", "#ffd24d", "#ff9a1f"], "rgba(255,170,30,.9)", "right");
          // the name, in the room between
          var nx = fx + fr + 12 * S, room = x1 - qw - 12 * S - nx, nsz = (big ? 34 : 28) * S * SPRAY_SCALE;
          c.font = nsz + "px " + famNow();
          while (c.measureText(r.name).width > room && nsz > 12) { nsz -= 1; c.font = nsz + "px " + famNow(); }
          sprayText(c, r.name, nx, cy, nsz, ["#ffffff", "#bfe6ff", "#6fb8ff"], "rgba(90,160,255,.9)", "left");
          c.restore();
          var hh = (big ? 82 : 68) * S, bx = { x: x0 - 8 * S, y: cy - hh / 2, w: x1 - x0 + 16 * S, h: hh, cy: cy };
          roughen(c, bx, 650);
          res.items.push({ cnv: cnv, box: bx, mask: layer(), prev: 0, r: hh * 0.62 });
        });
      }
      // the black coat: the can goes back and forth down the whole wall
      function coverPos(p) {
        var rows = 5, f = Math.max(0, Math.min(1, p)) * rows, i = Math.min(rows - 1, Math.floor(f)), u = f - i;
        var y = cv.height * (0.06 + 0.88 * (i + u * 0.15) / (rows - 1 + 0.15));
        var x = i % 2 ? cv.width * (1.08 - 1.16 * u) : cv.width * (-0.08 + 1.16 * u);
        return { x: x, y: y };
      }
      function sweepPos(it, p) { return { x: it.box.x + it.box.w * p, y: it.box.cy + Math.sin(p * 17) * it.box.h * 0.14 }; }
      function showGo() {
        if (goShown || over) return;
        goShown = true; held = true; snd.stop();
        skip.style.display = "none";
        go.style.opacity = "1"; go.style.pointerEvents = "auto";
      }

      /* A spray can, drawn with its nozzle at (x, y) and leaning by `tilt`. */
      function drawCan(x, y, tilt, paint, pressed) {
        var bw = 46 * S, bh = 112 * S, c1 = paint === "blue" ? "#2f7bff" : paint === "black" ? "#3a3a42" : "#ff8a1f", c2 = paint === "blue" ? "#0b2f8f" : paint === "black" ? "#0c0c10" : "#b33a00";
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

      var snd = Sound(), t0 = 0, last = 0, raf = 0, over = false, hissing = false, thumped = false, dripped = false, wdripped = false, prevA = 0, prevT = 0;
      function hiss(on) { if (on !== hissing) { hissing = on; snd.hiss(on); } }
      function finish() {
        if (over) return;
        over = true; cancelAnimationFrame(raf); snd.stop();
        wrap.style.opacity = "0";
        setTimeout(function () { wrap.remove(); playing = false; }, 460);
        if (done) done();
      }
      var held = false;
      function hold() {
        if (held || over) return;
        held = true;
        if (RES) return;            // yesterday's results come next; GAMBLE! shows when they're on
        showGo();
      }
      skip.onclick = function (e) { e.stopPropagation(); finish(); };
      go.onclick = function (e) { e.stopPropagation(); finish(); };
      wrap.onclick = function () { if (!held) finish(); };

      // timings, in ms
      // the can is shaken first for as long as the knocks in the recording of it last, then the paint goes on
      var A0 = 620, A1 = 2650, T0 = 2950, T1 = 3700, SUB = 3850, len = LEN;
      function frame(now) {
        if (over) return;
        if (!ready) { raf = requestAnimationFrame(frame); return; }     // wait for the picture
        if (!t0) { t0 = now; last = now; }
        var t = now - t0;
        g.globalCompositeOperation = "source-over";
        g.drawImage(wall, 0, 0);
        if (t >= A0) spray(specks.blue, Math.min(1, (t - A0) / (A1 - A0)) * 1.04);
        if (t >= T0) spray(specks.warm, Math.min(1, (t - T0) / (T1 - T0)) * 1.04);
        g.drawImage(spat, 0, 0);

        // 1) the piece
        var nozzle = null;
        if (t >= A0) {
          var pa = Math.min(1, (t - A0) / (A1 - A0));
          for (var q = prevA; q <= pa; q += 0.003) {
            var pt = artPos(q);
            if (pt.down) blob(artMask, pt.x, pt.y, opts.quick ? box.h * 0.34 : box.w * 0.125, 0.6);
          }
          prevA = pa;
          if (pa < 1) { nozzle = artPos(pa); if (nozzle.down) puff(nozzle.x, nozzle.y, "120,170,255"); }
          else {
            // last pass: the gaps fill in and the whole wall around it takes the glow
            if (opts.quick && !wdripped) { wdripped = true; dripsFrom(art, box, 7); }
            var c2 = artMask.getContext("2d");
            c2.fillStyle = "rgba(255,255,255," + Math.min(1, (t - A1) / 380) * 0.22 + ")";
            c2.fillRect(0, 0, artMask.width, artMask.height);
          }
        }
        g.globalCompositeOperation = "lighter";
        show(artMask, art);
        g.globalCompositeOperation = "source-over";
        if (t < T0) drawDrips(now);

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
            if (!dripped) { dripped = true; dripsFrom(txt, tBox, 6); }
            if (!thumped) { thumped = true; snd.thump(); }
          }
          // drips first, so they run out from under the letters
          drawDrips(now);
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

        // 4) yesterday's results: a coat of black over everything, then the heading and the top three, sprayed on one by one
        var paintRes = null, RS = len + 300;
        if (RES && t >= RS) {
          if (!res) buildRes();
          var rt = t - RS, cp = Math.min(1, rt / 1150);
          for (var qc = prevC; qc <= cp; qc += 0.0025) { var pc = coverPos(qc); blob(res.cover, pc.x, pc.y, cv.height * 0.12, 0.85); }
          prevC = cp;
          if (cp < 1) { nozzle = coverPos(cp); paintRes = "black"; puff(nozzle.x, nozzle.y, "40,40,46"); }
          else if (!covered) { covered = true; var cc = res.cover.getContext("2d"); cc.fillStyle = "#fff"; cc.fillRect(0, 0, cv.width, cv.height); }
          show(res.cover, res.black);
          var at = 1300;
          res.items.forEach(function (it, k) {
            var s0 = at + k * 780, s1 = s0 + (k ? 520 : 460);
            if (rt < s0) return;
            var p = Math.min(1, (rt - s0) / (s1 - s0));
            for (var qq = it.prev; qq <= p; qq += 0.01) { var sp = sweepPos(it, qq); blob(it.mask, sp.x, sp.y, it.r, 0.8); }
            it.prev = p;
            if (p < 1) { nozzle = sweepPos(it, p); paintRes = "orange"; puff(nozzle.x, nozzle.y, "255,190,60"); }
            else if (!it.done) {
              it.done = true; snd.thump();
              var mc = it.mask.getContext("2d"); mc.fillStyle = "#fff"; mc.fillRect(it.box.x - it.r, it.box.y - it.r, it.box.w + it.r * 2, it.box.h + it.r * 2);
            }
            show(it.mask, it.cnv);
          });
          if (rt >= at + res.items.length * 780 + 250) showGo();
        }

        // the mist coming off the can, and the can's cone of spray
        hiss(!!nozzle);
        // the can itself: shaken first, then held off the wall and worked across it, then pulled away
        var want, paint = paintRes || (t < T0 - 150 ? "blue" : "orange");
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
          var rgb = paint === "blue" ? "150,200,255" : paint === "black" ? "70,70,80" : "255,205,90";
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
        if (t >= len) hold();
        raf = requestAnimationFrame(frame);
      }
      raf = requestAnimationFrame(frame);
      // never hang: if the picture won't load, or the tab is put away, get out of the way
      setTimeout(function () { if (!over && !t0) finish(); }, 2500);
      setTimeout(hold, len + 3000);
      if (RES) setTimeout(showGo, len + 9000);        // never stuck behind the results
    }

    /* ---- the beat under the how-it-works cards: Justin's pick, one loop of it, round and round ---- */
    function Music() {
      var v = vol(), mv = typeof SOUND !== "undefined" ? (SOUND.music > 0 ? Math.max(SOUND.music, 0.3) : 0) : 0.3;        // chips follow the sound dial, the beat the music dial
      var none = { stop: function () {}, wake: function () {}, chips: function () {} }, ctx;
      if (!(v > 0) && !(mv > 0)) return none;
      try { ctx = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { return none; }
      var stopped = false, out = ctx.createGain(); out.gain.value = 0.0001; out.connect(ctx.destination);
      var nb = ctx.createBuffer(1, ctx.sampleRate * 0.4, ctx.sampleRate), nd = nb.getChannelData(0);
      for (var i = 0; i < nd.length; i++) nd[i] = Math.random() * 2 - 1;
      function b64(str) { var bin = atob(str), u = new Uint8Array(bin.length); for (var k = 0; k < bin.length; k++) u[k] = bin.charCodeAt(k); return u.buffer; }
      // The file is the loop with a quarter second of itself on each end. The beat repeats exactly every LOOP seconds,
      // so any LOOP-long window of it joins up clean, however much silence the decoder leaves at the front.
      var LOOP = 604800 / 44100, loopSrc = null;
      if (mv > 0) {
        try {
          var started = function (buf) {
            if (stopped || loopSrc) return;
            loopSrc = ctx.createBufferSource(); loopSrc.buffer = buf; loopSrc.loop = true;
            loopSrc.loopStart = 0.12; loopSrc.loopEnd = 0.12 + LOOP;
            loopSrc.connect(out); loopSrc.start(0, 0.12);
            out.gain.setTargetAtTime(Math.min(1, mv * 2.2), ctx.currentTime, 0.35);
          }, pm = ctx.decodeAudioData(b64("/*TIPSLOOP*/"), started, function () {});
          if (pm && pm.catch) pm.catch(function () {});
        } catch (e) {}
      }
      // off the screen means off: it stops the moment the app is minimised, and picks up when it's back
      function vis() {
        try { if (document.hidden) ctx.suspend(); else if (!stopped) ctx.resume(); } catch (e) {}
      }
      document.addEventListener("visibilitychange", vis); window.addEventListener("pagehide", vis);
      // Justin's own chips, recorded: decoded once, played on every tap. The made-up clack below is only the fallback.
      var clip = null;
      try {
        var got = function (buf) { clip = buf; }, pr = ctx.decodeAudioData(b64("/*CHIPCLACK*/"), got, function () {});
        if (pr && pr.catch) pr.catch(function () {});
      } catch (e) {}
      function chips() {
        try {
          if (!(v > 0) || document.hidden) return;
          if (ctx.state === "suspended") ctx.resume();
          if (clip) {
            var src = ctx.createBufferSource(), cg = ctx.createGain();
            src.buffer = clip; src.playbackRate.value = 0.97 + Math.random() * 0.06;      // never quite the same twice
            cg.gain.value = Math.min(1.6, v * 2.4);
            src.connect(cg); cg.connect(ctx.destination); src.start();
            return;
          }
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
        wake: function () { try { if (ctx.state === "suspended" && !document.hidden) ctx.resume(); } catch (e) {} },
        stop: function () {
          if (stopped) return; stopped = true;
          document.removeEventListener("visibilitychange", vis); window.removeEventListener("pagehide", vis);
          try { out.gain.cancelScheduledValues(ctx.currentTime); out.gain.setTargetAtTime(0.0001, ctx.currentTime, 0.12); } catch (e) {}
          setTimeout(function () { try { if (loopSrc) loopSrc.stop(); ctx.close(); } catch (e) {} }, 700);
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
      { head: '<span style="font-size:38px;white-space:nowrap">WHO\'S ON TOP</span>', frame: ["#b6ffd6", "#22d37a"], lit: ["#e3f2ff", "#2f8cff"], top: true,
        body: "<b>Every game's button shows who's on top.</b> Their face is in the PLAY circle with their money under it. The gold tag is the game's points. Beat their number to take them." },
      { head: "MOST POINTS<br>WINS", frame: ["#ffe7a0", "#ffb02e"], lit: ["#ffe9f4", "#ff3d9a"],
        art: '<div class="gd-trophy">' + CUP + '</div>',
        body: "<b>Points add up all week.</b> Sunday through Saturday night. Go broke on a game? <b>REBUY is free</b> and puts you back at $10,000." }
    ];
    /* three made-up leaders on three game buttons, drawn the way the real buttons show them:
       the points on the top edge, the leader's face in the PLAY circle, their money under it */
    function topArt() {
      var f = {}; try { f = (typeof Walkups !== "undefined" && Walkups.faces && Walkups.faces()) || {}; } catch (e) {}
      var names = Object.keys(f).sort(function () { return Math.random() - 0.5; });
      var rows = [["BLACKJACK", "$41,250", 3, "#B98CFF"], ["CRAPS", "$27,800", 2, "#57E06A"], ["ROULETTE", "$18,400", 1, "#7fc4ff"]];
      return '<div class="gd-top">' + rows.map(function (r, i) {
        var n = names[i], pic = n && f[n] ? '<img src="' + f[n] + '" alt="">' : '<span class="gd-noface">🙂</span>';
        return '<div class="gd-tile" style="--tc:' + r[3] + '"><span class="gd-pt">' + r[2] + ' PT' + (r[2] > 1 ? 'S' : '') + '</span>' +
          '<b class="gd-game">' + r[0] + '</b><span class="gd-lead">' + pic + '<i>' + r[1] + '</i></span></div>';
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
          "#tt-demo .gd-top{width:100%;display:flex;flex-direction:column;gap:13px;padding:8px 6px 0;box-sizing:border-box}" +
          "#tt-demo .gd-tile{position:relative;display:flex;align-items:center;justify-content:space-between;gap:10px;padding:6px 14px 5px 16px;border-radius:16px;" +
            "border:2px solid var(--tc);background:rgba(255,255,255,.04);box-shadow:0 0 8px var(--tc),inset 0 0 8px rgba(255,255,255,.06);text-align:left}" +
          "#tt-demo .gd-pt{position:absolute;top:-10px;left:50%;transform:translateX(-50%);padding:3px 9px;border-radius:999px;background:#FFD24D;color:#14101c;" +
            "font:900 10px/1 system-ui,sans-serif;letter-spacing:.08em;white-space:nowrap;box-shadow:0 0 10px rgba(255,210,77,.7)}" +
          "#tt-demo .gd-game{font:400 22px/1 " + NE + ";letter-spacing:.05em;color:#fff;text-shadow:0 0 8px var(--tc),0 0 18px var(--tc)}" +
          "#tt-demo .gd-lead{display:flex;flex-direction:column;align-items:center;gap:3px;flex:none}" +
          "#tt-demo .gd-lead img,#tt-demo .gd-noface{width:36px;height:36px;border-radius:50%;object-fit:cover;border:2.5px solid var(--tc);box-shadow:0 0 10px var(--tc);" +
            "display:flex;align-items:center;justify-content:center;font-size:22px;background:#222;box-sizing:border-box}" +
          "#tt-demo .gd-lead i{font:900 12px/1 system-ui,sans-serif;font-style:normal;color:#57E06A;text-shadow:0 0 8px rgba(87,224,106,.6);white-space:nowrap}" +
          "#tt-demo .gd-eg{font:700 9px system-ui,sans-serif;letter-spacing:.24em;color:#aeb8cc;margin-top:-4px}" +
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
      // a swipe turns the page too: left for the next tip, right to go back one
      var sx = null, sy = 0;
      wrap.addEventListener("touchstart", function (e) { var t = e.touches[0]; sx = t.clientX; sy = t.clientY; }, { passive: true });
      wrap.addEventListener("touchend", function (e) {
        if (sx === null) return;
        var t = e.changedTouches[0], dx = t.clientX - sx, dy = t.clientY - sy; sx = null;
        if (Math.abs(dx) < 45 || Math.abs(dx) < Math.abs(dy) * 1.5) return;
        // a swipe sounds like a card coming off the deck (the chip clack is the NEXT button's)
        var turned = false;
        if (dx < 0 && at < CARDS.length - 1) { at++; turned = true; }
        else if (dx > 0 && at > 0) { at--; turned = true; }
        if (turned) { try { playDraw(1); } catch (err) {} show(); }
      }, { passive: true });
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
      var day = /(^|[#&])intro-day\b/.test(location.hash);      // #intro-day previews the short new-day one
      if (day) tip.textContent = "TAP TO PLAY THE NEW DAY INTRO";
      tip.onclick = function () {
        tip.remove();
        if (day) {
          var fc = typeof Walkups !== "undefined" && Walkups.faces ? Walkups.faces() : {};
          Intro.play({ quick: true, title: "DAY 2", sub: "PLINKO & MINES PAY 1.5×", resultsTitle: "DAY 1 RESULTS",
                       results: [{ place: 1, name: "JUSTIN", pts: 12, face: fc.Justin }, { place: 2, name: "MATT", pts: 8, face: fc.Matthew },
                                 { place: 3, name: "JACOB", pts: 4, face: fc.Zac }] });
        }
        else Intro.play(function () { Intro.demo(); });
      };
      document.body.appendChild(tip);
    });
  }

