  /* ================= CHIPS ON THE FRONT PAGE =================
   * (Built to fall; now they hold still - drawn once, redrawn only on a resize.)
   * The front page's backdrop: 230 solid chips tumbling down behind the glass.
   * One canvas, not thousands of 3D layers: each chip's face is drawn once per
   * denomination and shade, then every frame each chip is a few squashed copies
   * of it - the back ones darker, which reads as the striped clay edge. Real
   * proportions: 39 mm across, 3.3 mm thick. It runs only while the front page
   * is showing and the app is in front, at 30 frames a second; with reduced
   * motion asked for, it is one still frame.
   */
  (function () {
    var cv = $("cr-rain"); if (!cv || !cv.getContext) return;
    var g = cv.getContext("2d");
    var VALS = [25, 100, 500, 1000, 5000, 25000];
    var COUNT = 150, THICK = 3.3 / 39, PX = 192;
    var SPAN = 1.24;                          // all of them on the screen, a few hanging off the edges
    var SHADES = [0.24, 0.36, 0.5];           // far, middle, near: dark, as asked
    var EDGE = 0.62;                          // the side is darker than the face
    var still = true;                         // they lie where they landed: drawn once, never moving
    var sprites = {}, ready = 0, chips = [], W = 0, H = 0, dpr = 1;

    function shaded(img, k) {
      var c = document.createElement("canvas"); c.width = c.height = PX;
      var x = c.getContext("2d");
      x.drawImage(img, 0, 0, PX, PX);
      x.globalCompositeOperation = "source-atop";
      x.fillStyle = "rgba(0,0,0," + (1 - k).toFixed(3) + ")";
      x.fillRect(0, 0, PX, PX);
      return c;
    }
    VALS.forEach(function (v) {
      var svg = chipFace(v).replace("<svg ", '<svg xmlns="http://www.w3.org/2000/svg" width="' + PX + '" height="' + PX + '" ');
      var img = new Image();
      img.onload = function () {
        sprites[v] = SHADES.map(function (k) { return { face: shaded(img, k), edge: shaded(img, k * EDGE) }; });
        if (++ready === VALS.length) start();
      };
      img.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
    });

    var seed = 7;
    function rnd() { seed = (seed * 16807) % 2147483647; return (seed - 1) / 2147483646; }
    function size() {
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      W = cv.clientWidth; H = cv.clientHeight;
      cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr);
    }
    function start() {
      size();
      for (var i = 0; i < COUNT; i++) {
        var d = rnd();                                         // 0 far .. 1 near
        chips.push({
          v: VALS[Math.floor(rnd() * VALS.length)],
          shade: d < .33 ? 0 : d < .66 ? 1 : 2,
          s: 36 + d * 60,                                      // across, in px
          x: rnd(), y: rnd() * SPAN - (SPAN - 1.12),          // spread over more than a screen
          fall: (0.022 + d * 0.035) * (0.8 + rnd() * .4),      // screens a second
          axis: rnd() * Math.PI * 2,                           // the line it tips over
          tip: rnd() * Math.PI * 2,                            // how far over, now
          tumble: (rnd() < .5 ? -1 : 1) * (0.25 + rnd() * 0.6),// turns a second
          turn: rnd() * Math.PI * 2                            // the face's own spin
        });
      }
      chips.sort(function (a, b) { return a.s - b.s; });       // far ones first
      draw(0);
      if (!still) loop();
    }
    function chip(c) {
      var sp = sprites[c.v][c.shade];
      var cos = Math.cos(c.tip), sin = Math.sin(c.tip);
      var r = c.s / 2, t = c.s * THICK * Math.abs(sin);         // edge as seen
      var squash = Math.max(Math.abs(cos), 0.06);
      var n = Math.max(1, Math.min(6, Math.ceil(t / 1.4)));
      var dir = cos >= 0 ? 1 : -1;
      g.save();
      g.translate(c.x * W, c.y * H);
      g.rotate(c.axis);
      for (var i = 0; i <= n; i++) {
        var top = i === n;
        g.save();
        g.translate(0, dir * (t * (1 - i / n) - t / 2));
        g.scale(1, squash);
        g.rotate(c.turn);
        g.drawImage(top ? sp.face : sp.edge, -r, -r, c.s, c.s);
        g.restore();
      }
      g.restore();
    }
    function draw(dt) {
      g.setTransform(dpr, 0, 0, dpr, 0, 0);
      g.clearRect(0, 0, W, H);
      for (var i = 0; i < chips.length; i++) {
        var c = chips[i];
        if (dt) {
          c.y += c.fall * dt;
          c.tip += c.tumble * Math.PI * 2 * dt;
          if (c.y > 1.12) { c.y -= SPAN; c.x = rnd(); }
        }
        chip(c);
      }
    }
    var last = 0, raf = 0;
    function showing() {
      return !document.hidden && $("screen-menu").classList.contains("on");
    }
    function loop() {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(function step(now) {
        if (!showing()) { raf = 0; last = 0; return; }       // asleep off the front page
        if (now - last >= 33) {
          var dt = last ? Math.min((now - last) / 1000, 0.1) : 0;
          last = now;
          draw(dt);
        }
        raf = requestAnimationFrame(step);
      });
    }
    function wake() { if (ready === VALS.length && !still && !raf && showing()) loop(); }
    document.addEventListener("visibilitychange", wake);
    window.addEventListener("resize", function () { if (ready === VALS.length) { size(); draw(0); } });
    new MutationObserver(wake).observe($("screen-menu"), { attributes: true, attributeFilter: ["class"] });
  })();
