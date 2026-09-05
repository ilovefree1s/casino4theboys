/*
 * dice-tray.js — the Chain Reaction dice, lifted out to stand on their own.
 *
 * One file, no dependencies, no build step. Drop it in a page, point it at an
 * element, and you have a tray of 1–6 dice you can pick up, rattle, slide
 * around and throw, with a physics settle that nothing rigs: the number is
 * whatever face the cube stops on.
 *
 *   <div id="tray" style="height:420px"></div>
 *   <script src="dice-tray.js"></script>
 *   <script>
 *     var tray = DiceTray.create(document.getElementById("tray"), {
 *       count: 2,
 *       sound: { url: "dice.mp3" },
 *       onSettle: function (values, total) { console.log(values, total); }
 *     });
 *   </script>
 *
 * API
 *   DiceTray.create(host, opts) -> tray
 *   tray.roll(strength)      throw them yourself; strength 0..1, default 0.4
 *   tray.setCount(n)         1..6
 *   tray.values()            [3, 5] — what they are showing right now
 *   tray.total()
 *   tray.set([3, 5])         put specific faces up without a throw
 *   tray.destroy()           unhooks the window listeners and empties the host
 *
 * opts
 *   count      how many dice to start with        (default 2)
 *   max        most the tray will hold            (default 6)
 *   size       die width in px                    (default 74)
 *   volume     0..1                               (default 0.7)
 *   sound      { url, shakeFrom, shakeLen, rollFrom, rollLen, rollDelay, shakeGain }
 *              A single clip holding a rattle and then a landing, played in two
 *              places. Defaults match Chain Reaction's dice.mp3: rattle 0..1.13,
 *              landing from 1.13 for 0.62, held back 0.4s so the dice have left
 *              the hand before they are heard to arrive. Omit for silence.
 *   onSettle   (values, total) once a throw has come to rest
 *   onChange   (values, total) every time the numbers change, rattle included
 *
 * The one thing worth knowing before you tune anything: gravity pulls toward the
 * TOP of the field, because that is where the table is. You throw away from
 * yourself and the dice settle against the far rail, in sight and clear of the
 * hand that threw them. Everything else is an ordinary 2D simulation.
 */
(function (root) {
  "use strict";

  /* ================= sound =================
   * Decoded into a buffer once, so any stretch of a clip can be played from
   * anywhere — the same on every server. An <audio> element cannot do this:
   * seeking into one needs the server to answer byte-range requests, so the
   * same code works on one host and silently starts at zero on another.
   *
   * A buffer can also be played LOUDER than the recording, which an element's
   * volume cannot — it caps at the file's own level. The rattle half of a dice
   * clip is usually much quieter than the landing, and needs the lift.
   */
  var actx = null, bufs = {};
  function ready(url) {
    if (!url) return null;
    if (!actx) {
      var Ctx = root.AudioContext || root.webkitAudioContext;
      if (!Ctx) return null;
      actx = new Ctx();
    }
    // Phones suspend it until something is touched; every use follows a tap.
    if (actx.state === "suspended") actx.resume();
    if (bufs[url] === undefined) {
      bufs[url] = null; // in flight — do not ask twice
      fetch(url).then(function (r) { return r.arrayBuffer(); })
        .then(function (b) { return actx.decodeAudioData(b); })
        .then(function (b) { bufs[url] = b; })
        .catch(function () { bufs[url] = null; });
    }
    return bufs[url];
  }
  /* [delay] holds the sound back without holding the picture back: scheduled on
     the audio clock, so it is a real pause and not a timer that fires late. */
  function playSlice(url, from, seconds, gain, loop, delay) {
    var buf = ready(url);
    if (!buf || !(gain > 0)) return null;
    try {
      var src = actx.createBufferSource();
      src.buffer = buf;
      var g = actx.createGain();
      g.gain.value = gain;
      src.connect(g); g.connect(actx.destination);
      if (loop) { src.loop = true; src.loopStart = from; src.loopEnd = from + seconds; }
      src.start(actx.currentTime + (delay || 0), from, loop ? undefined : seconds);
      return src;
    } catch (e) { return null; }
  }
  function stopSlice(src) { if (src) { try { src.stop(); } catch (e) { /* done */ } } }

  /* ================= the die =================
   * Every face is always there, carrying its own number. Nothing rewrites pips:
   * the cube is simply turned until the side you are meant to read faces you.
   * That is why a die never lands showing one number and then changes to
   * another — there is nothing to change.
   */

  /* Which cells of a face's 3x3 grid carry a pip. */
  var PIPS = {
    1: [4], 2: [0, 8], 3: [0, 4, 8], 4: [0, 2, 6, 8],
    5: [0, 2, 4, 6, 8], 6: [0, 2, 3, 5, 6, 8]
  };
  /* Where each number sits in the cube's own space: value, then the direction
     that face points before anything is turned. */
  var NORMALS = [
    [1, 0, 0, 1], [6, 0, 0, -1], [3, 1, 0, 0], [4, -1, 0, 0], [5, 0, -1, 0], [2, 0, 1, 0]
  ];
  /*
   * Which number a cube is showing, given how far it has been turned about each
   * axis. Turn every face by the same rotation and read off whichever ends up
   * pointing at the screen. Same order as the CSS transform: Z, then X, then Y.
   */
  function faceToward(z, x, y) {
    var rz = z * Math.PI / 180, rx = x * Math.PI / 180, ry = y * Math.PI / 180;
    var cz = Math.cos(rz), sz = Math.sin(rz);
    var cx = Math.cos(rx), sx = Math.sin(rx);
    var cy = Math.cos(ry), sy = Math.sin(ry);
    var best = 1, bestZ = -2;
    for (var i = 0; i < NORMALS.length; i++) {
      var n = NORMALS[i], vx = n[1], vy = n[2], vz = n[3];
      // rotateY, then rotateX, then rotateZ — innermost first.
      var ax = cy * vx + sy * vz, ay = vy, az = -sy * vx + cy * vz;
      var bz = sx * ay + cx * az; // rotateZ leaves z alone, so this is the answer
      if (bz > bestZ) { bestZ = bz; best = n[0]; }
    }
    return best;
  }
  /* Which way up a die has to be for a given number to face you. Opposite sides
     of a real die sum to seven, so naming three of them fixes the other three. */
  var FACING = { 1: [0, 0], 6: [0, 180], 3: [0, -90], 4: [0, 90], 5: [-90, 0], 2: [90, 0] };

  function facesHtml() {
    var out = "";
    for (var v = 1; v <= 6; v++) {
      var pips = "";
      for (var p = 0; p < 9; p++) {
        pips += '<span class="dt-pip' + (PIPS[v].indexOf(p) !== -1 ? " on" : "") + '"></span>';
      }
      out += '<div class="dt-face dt-f' + v + '">' + pips + "</div>";
    }
    return out;
  }

  /* ================= tuning =================
   * Pixels and seconds, so gravity reads as gravity: 2600px/s² is about right
   * for a die a centimetre across on a phone.
   */
  var GRAVITY = 2600, BOUNCE = 0.42, FLOOR_DRAG = 0.82, AIR = 0.995;
  /* Upward speed in pixels per millisecond. FLICK is what it takes out in the
     open felt — well clear of a brisk drag, or setting a die down at the top of
     the tray would re-roll the table. LOB is what it takes down in the throwing
     band, where a die is in hand to be thrown rather than arranged: barely more
     than a lift. FAST is the ceiling, past which a throw cannot get harder. */
  var FLICK = 0.55, LOB = 0.12, FAST = 2.2, THROW_PX = 42;

  var CSS = [
    /* Perspective, or rotateX is a flat scale and the die reads as a picture
       being pinched rather than a cube going over. */
    ".dt-field{position:relative;width:100%;height:100%;min-height:260px;",
    "  touch-action:none;perspective:900px;overflow:hidden}",
    ".dt-field .dt-die{position:absolute;transform:translate(-50%,-50%);",
    "  width:var(--dt-size,74px);height:var(--dt-size,74px);perspective:700px;",
    "  cursor:grab;filter:drop-shadow(0 5px 9px rgba(0,0,0,.45))}",
    ".dt-field .dt-die.held{cursor:grabbing}",
    /* Mid-throw the simulation writes every transform each frame, so nothing
       eases — an easing would be the browser arguing with the physics about
       where the die is. Settled, they walk themselves home on left/top only;
       the transform is the die's own centring and must not be eased. */
    ".dt-field.rolling .dt-die,.dt-field.rolling .dt-cube{transition:none}",
    ".dt-field:not(.rolling) .dt-die{border-radius:13px;overflow:hidden;",
    "  transition:left 420ms cubic-bezier(.3,.9,.3,1),top 420ms cubic-bezier(.3,.9,.3,1)}",
    /* Picked up: brighter and out from under the others. The lift and the
       carrying are done on the element's transform by the drag itself, so this
       rule stays off transform entirely — it would fight the finger for it. */
    ".dt-field .dt-die.held{z-index:2;will-change:transform;transition:none;",
    "  box-shadow:0 8px 20px rgba(0,0,0,.6),0 0 18px var(--dt-hold,rgba(77,195,255,.5))}",
    ".dt-cube{position:absolute;inset:0;transform-style:preserve-3d;",
    "  transition:transform 260ms ease-out}",
    ".dt-face{position:absolute;inset:0;background:var(--dt-face,#F4F6FA);",
    "  border-radius:12px;display:grid;grid-template-columns:repeat(3,1fr);",
    "  grid-template-rows:repeat(3,1fr);padding:15%;box-sizing:border-box;gap:4%}",
    /* Half the die out along each axis, which is what makes it a cube and not
       six sheets of paper in the same place. */
    ".dt-f1{transform:translateZ(calc(var(--dt-size,74px)/2))}",
    ".dt-f6{transform:rotateY(180deg) translateZ(calc(var(--dt-size,74px)/2))}",
    ".dt-f3{transform:rotateY(90deg) translateZ(calc(var(--dt-size,74px)/2))}",
    ".dt-f4{transform:rotateY(-90deg) translateZ(calc(var(--dt-size,74px)/2))}",
    ".dt-f5{transform:rotateX(90deg) translateZ(calc(var(--dt-size,74px)/2))}",
    ".dt-f2{transform:rotateX(-90deg) translateZ(calc(var(--dt-size,74px)/2))}",
    ".dt-pip{border-radius:50%}",
    ".dt-pip.on{background:var(--dt-pip,#10182A)}",
    /* The throwing area's near edge. Quiet enough to ignore, there enough to find. */
    ".dt-line{position:absolute;left:7%;right:7%;bottom:50%;height:0;z-index:1;",
    "  border-top:2px dashed var(--dt-line,rgba(77,195,255,.3));pointer-events:none}",
    ".dt-line span{position:absolute;left:50%;top:8px;transform:translateX(-50%);",
    "  white-space:nowrap;font:900 11px/1 system-ui,sans-serif;letter-spacing:2px;",
    "  text-transform:uppercase;color:var(--dt-line,rgba(77,195,255,.45))}",
    ".dt-field.rolling .dt-line{display:none}"
  ].join("");

  var styled = false;
  function injectCss() {
    if (styled) return;
    styled = true;
    var s = document.createElement("style");
    s.textContent = CSS;
    document.head.appendChild(s);
  }

  /* ================= the tray ================= */
  function Tray(host, opts) {
    opts = opts || {};
    var t = this;
    injectCss();

    t.host = host;
    t.max = Math.min(6, opts.max || 6);
    t.size = opts.size || 74;
    t.volume = typeof opts.volume === "number" ? opts.volume : 0.7;
    t.snd = opts.sound
      ? {
          url: opts.sound.url,
          shakeFrom: num(opts.sound.shakeFrom, 0),
          shakeLen: num(opts.sound.shakeLen, 1.13),
          rollFrom: num(opts.sound.rollFrom, 1.13),
          rollLen: num(opts.sound.rollLen, 0.62),
          rollDelay: num(opts.sound.rollDelay, 0.4),
          shakeGain: num(opts.sound.shakeGain, 4)
        }
      : null;
    t.onSettle = opts.onSettle || null;
    t.onChange = opts.onChange || null;

    t.count = Math.max(1, Math.min(t.max, opts.count || 2));
    t.vals = [];
    for (var i = 0; i < t.count; i++) t.vals.push(1 + Math.floor(Math.random() * 6));
    t.pos = null;
    t.rolling = false;
    t.drag = null;
    t.sim = null;

    t.field = document.createElement("div");
    t.field.className = "dt-field";
    t.field.style.setProperty("--dt-size", t.size + "px");
    host.innerHTML = "";
    host.appendChild(t.field);
    t.build();

    // Pointer events, not touch: the same handful of listeners then serve a
    // thumb on a phone and a mouse on a laptop.
    t._down = function (e) { t.onDown(e); };
    t._move = function (e) { t.onMove(e); };
    t._up = function () { t.endDrag(); };
    t._cancel = function () { if (t.drag) { t.drag.moved = true; t.endDrag(); } };
    t.field.addEventListener("pointerdown", t._down);
    // On window, so a drag that leaves the tray — or the screen — still tracks.
    root.addEventListener("pointermove", t._move);
    root.addEventListener("pointerup", t._up);
    // Android cancels the stream when it claims the drag; whatever was held settles.
    root.addEventListener("pointercancel", t._cancel);
  }
  function num(v, d) { return typeof v === "number" ? v : d; }

  Tray.prototype.build = function () {
    var t = this;
    var html = '<div class="dt-line"><span>Easy throw below here</span></div>';
    for (var i = 0; i < t.count; i++) {
      html += '<div class="dt-die" data-die="' + i + '"><div class="dt-cube">' +
        facesHtml() + "</div></div>";
    }
    t.field.innerHTML = html;
    t.pos = t.home();
    t.place();
  };

  /* Where the dice wait before a throw: a row along the bottom, in percentages
     of the field so they keep their places on any screen. Three to a row —
     six dice will not fit across a phone in one line, and would sit on top of
     each other before anything had been thrown. */
  Tray.prototype.home = function () {
    var out = [], cols = Math.min(3, this.count);
    for (var i = 0; i < this.count; i++) {
      out.push({
        x: 50 + ((i % cols) - (cols - 1) / 2) * 26,
        y: 86 - Math.floor(i / cols) * 17
      });
    }
    return out;
  };
  Tray.prototype.places = function () {
    if (!this.pos || this.pos.length !== this.count) this.pos = this.home();
    return this.pos;
  };
  Tray.prototype.el = function (i) {
    return this.field.querySelector('[data-die="' + i + '"]');
  };
  /* Moves the dice already on the felt to where the state says they are, and
     turns them to the numbers the state says, without rebuilding anything. */
  Tray.prototype.place = function () {
    var at = this.places();
    for (var i = 0; i < this.count; i++) {
      var el = this.el(i);
      if (!el) continue;
      el.style.left = at[i].x.toFixed(2) + "%";
      el.style.top = at[i].y.toFixed(2) + "%";
      var f = FACING[this.vals[i]] || FACING[1];
      var cube = el.firstChild;
      if (cube) cube.style.transform = "rotateX(" + f[0] + "deg) rotateY(" + f[1] + "deg)";
    }
  };
  Tray.prototype.values = function () { return this.vals.slice(); };
  Tray.prototype.total = function () {
    return this.vals.reduce(function (a, b) { return a + b; }, 0);
  };
  Tray.prototype.changed = function () {
    if (this.onChange) this.onChange(this.values(), this.total());
  };
  Tray.prototype.set = function (vals) {
    for (var i = 0; i < this.count && i < vals.length; i++) {
      this.vals[i] = Math.max(1, Math.min(6, vals[i] | 0));
    }
    this.place();
    this.changed();
  };
  Tray.prototype.setCount = function (n) {
    n = Math.max(1, Math.min(this.max, n | 0));
    if (n === this.count || this.rolling) return;
    while (this.vals.length < n) this.vals.push(1 + Math.floor(Math.random() * 6));
    this.vals.length = n;
    this.count = n;
    this.pos = null;
    this.build();
    this.changed();
  };

  /* ---------- picking them up ---------- */

  /* Everything close enough to the die at [i] to be part of the same pile, each
     remembered by where it starts in the field's own pixels. A die under the
     finger comes up with whatever is stacked on it, the way a handful would. */
  Tray.prototype.pile = function (at, i, fb) {
    var out = [];
    for (var k = 0; k < at.length; k++) {
      var sx = at[k].x / 100 * fb.width, sy = at[k].y / 100 * fb.height;
      var dx = sx - at[i].x / 100 * fb.width, dy = sy - at[i].y / 100 * fb.height;
      if (Math.sqrt(dx * dx + dy * dy) <= this.size * 0.62) out.push({ i: k, sx: sx, sy: sy });
    }
    return out;
  };
  /* A band rather than a hard edge, with the dashed line through the middle of
     it: a flick aimed at the line lands somewhere either side of it, and having
     to start below an invisible boundary is exactly what makes this fiddly. */
  Tray.prototype.inZone = function (y) {
    var r = this.field.getBoundingClientRect();
    return y >= r.top + r.height * 0.42;
  };
  /* The dashed marker itself. Carrying a die up over this is the throw — waiting
     for the finger to lift as well means a flick that runs off the top of the
     tray does nothing at all, which is most of what makes dice hard to roll. */
  Tray.prototype.pastLine = function (y) {
    var r = this.field.getBoundingClientRect();
    return y < r.top + r.height * 0.5;
  };

  Tray.prototype.onDown = function (e) {
    var t = this;
    if (!e.isPrimary || t.rolling || !e.target.closest) return;
    var die = e.target.closest(".dt-die");
    // Every die is picked up wherever it lies — how you let go is what decides
    // whether it was a throw.
    if (!die) return;
    var fb = t.field.getBoundingClientRect();
    var i = +die.getAttribute("data-die");
    var w = die.getBoundingClientRect().width || t.size;
    t.drag = {
      i: i, field: fb,
      // Half the die AS HELD: it is scaled up while carried, and clamping to its
      // resting size lets a corner of it hang over the edge of the tray.
      reach: w * 0.55,
      pile: t.pile(t.places(), i, fb),
      x0: e.clientX, y0: e.clientY, moved: false,
      lastX: e.clientX, lastY: e.clientY, lastT: Date.now(), speed: 0,
      // Which side of the line the finger was on last, so a throw can be spotted
      // as the crossing itself. NOT where the grab began: the dice settle at the
      // top of the tray, so after the first roll every grab starts above the line
      // and a rule about the starting side would never let you throw again.
      wasBelow: !t.pastLine(e.clientY),
      shakeX: e.clientX, shakeY: e.clientY, shakeDir: 0, turns: [], shaking: false
    };
    t.drag.pile.forEach(function (g) {
      g.el = t.el(g.i);
      if (g.el) g.el.classList.add("held");
    });
  };

  Tray.prototype.onMove = function (e) {
    var t = this, g = t.drag;
    if (!g) return;
    var now = Date.now(), f = g.field;
    var dt = now - g.lastT;
    if (dt > 0) g.speed = (g.lastY - e.clientY) / dt; // upward is positive
    g.lastX = e.clientX; g.lastY = e.clientY; g.lastT = now;
    if (Math.abs(e.clientX - g.x0) > 6 || Math.abs(e.clientY - g.y0) > 6) g.moved = true;
    t.shakeStep(g, e.clientX, e.clientY, now);
    // Carried on transform rather than left/top: the finger gets a compositor
    // move per frame instead of a layout of the whole tray. Held inside the walls
    // as it goes — a die should not be able to leave the screen in your hand any
    // more than it can be thrown off it.
    var dx = e.clientX - g.x0, dy = e.clientY - g.y0, edge = g.reach;
    g.pile.forEach(function (m) {
      if (!m.el) return;
      m.nx = Math.max(edge, Math.min(f.width - edge, m.sx + dx));
      m.ny = Math.max(edge, Math.min(f.height - edge, m.sy + dy));
      m.el.style.transform = "translate(-50%,-50%) translate(" +
        (m.nx - m.sx).toFixed(1) + "px," + (m.ny - m.sy).toFixed(1) + "px) scale(1.1)";
    });
    // Carrying a die up over the line is the throw, finger or no finger. The
    // crossing is what counts, so a die resting up top can be nudged around all
    // day without one — it only goes when it comes up from under the line.
    // Shaking is the one thing this is not: a rattle that wanders past the line
    // is still a rattle.
    var below = !t.pastLine(e.clientY);
    if (g.wasBelow && !below && !g.shaking && g.speed > 0) {
      g.launch = Math.max(0.15, Math.min(1, (g.speed - LOB) / (FAST - LOB)));
      t.endDrag();
      return;
    }
    g.wasBelow = below;
  };

  /* ---------- rattling them ----------
   * Back and forth counts as a shake — three changes of direction inside a
   * second — and while it is going the dice in hand keep turning up new numbers,
   * the way they would in a fist. It stops when you stop; whatever they are
   * showing when you put them down is what they are.
   */
  Tray.prototype.shakeOn = function (g) {
    var t = this;
    if (!g.snd && t.snd) {
      // Looped, and lifted past the recording's own level: the rattle half of a
      // dice clip is usually a fraction of the landing's peak, which on a phone
      // at arm's length is nothing at all.
      g.snd = playSlice(t.snd.url, t.snd.shakeFrom, t.snd.shakeLen,
        t.volume * t.snd.shakeGain, true);
    }
    // A gap rather than a stop on every quiet frame: a shake is a series of
    // moves with pauses in it, and cutting on each one would be a stutter. A
    // finger resting on a die is still holding it, so without this the dice sit
    // there rattling in a hand that has gone still.
    if (g.idle) clearTimeout(g.idle);
    g.idle = setTimeout(function () { t.shakeOff(g); }, 160);
  };
  Tray.prototype.shakeOff = function (g) {
    if (g.idle) { clearTimeout(g.idle); g.idle = null; }
    stopSlice(g.snd);
    g.snd = null;
  };
  Tray.prototype.shakeStep = function (g, x, y, now) {
    var t = this;
    // A rattle is side to side and stays on one level. Gathering dice into a
    // pile is side to side too, but it travels — and a throw travels furthest of
    // all. Without telling those apart, the nudges that stack the dice latch the
    // drag as a shake for the rest of its life, and every flick after them goes
    // nowhere until you let go and take hold again.
    if (g.shaking && g.shakeY != null && g.shakeY - y > 44) {
      g.shaking = false; g.turns = []; g.shakeDir = 0;
      t.shakeOff(g);
    }
    if (g.shaking) t.shakeOn(g);
    if (Math.abs(x - (g.shakeX == null ? x : g.shakeX)) < 9) return;
    var dir = x > g.shakeX ? 1 : -1;
    g.shakeX = x;
    if (g.shakeDir && dir !== g.shakeDir) {
      // A change of direction that also changed height is aim, not a rattle.
      if (g.shakeY != null && Math.abs(y - g.shakeY) > 30) g.turns = [];
      g.turns = (g.turns || []).filter(function (v) { return now - v < 900; });
      g.turns.push(now);
      g.shakeY = y;
      if (g.turns.length >= 3) g.shaking = true;
    }
    g.shakeDir = dir;
    if (!g.shaking) return;
    t.shakeOn(g);
    if (now - (g.rattled || 0) < 80) return;
    g.rattled = now;
    g.pile.forEach(function (m) {
      var was = t.vals[m.i];
      do { t.vals[m.i] = 1 + Math.floor(Math.random() * 6); } while (t.vals[m.i] === was);
      var f = FACING[t.vals[m.i]] || FACING[1];
      var cube = m.el && m.el.firstChild;
      if (cube) {
        // A knock of a few degrees off square, so a shaken die looks rattled
        // rather than neatly presented.
        cube.style.transition = "none";
        cube.style.transform = "rotateX(" + (f[0] + (Math.random() - 0.5) * 26) +
          "deg) rotateY(" + (f[1] + (Math.random() - 0.5) * 26) + "deg)";
      }
    });
    t.changed();
  };

  /* ---------- letting go ---------- */
  Tray.prototype.endDrag = function () {
    var t = this, g = t.drag;
    if (!g) return;
    t.shakeOff(g);
    g.pile.forEach(function (m) {
      if (m.el) { m.el.classList.remove("held"); m.el.style.transform = ""; }
    });
    t.drag = null;
    if (t.rolling) return;
    // A die that was shaken keeps whatever it was shaken onto and is not thrown:
    // rattling something in your fist is not letting go of it.
    if (g.shaking) { t.place(); return; }
    // Put down without having gone anywhere. Nothing happens.
    if (!g.moved) { t.place(); return; }
    // Where the drag left them, which it has already kept inside the walls.
    var at = t.places(), f = g.field;
    g.pile.forEach(function (m) {
      if (!at[m.i] || m.nx == null) return;
      at[m.i].x = m.nx / f.width * 100;
      at[m.i].y = m.ny / f.height * 100;
      if (m.el) {
        m.el.style.left = at[m.i].x.toFixed(2) + "%";
        m.el.style.top = at[m.i].y.toFixed(2) + "%";
      }
    });
    // Already thrown, by crossing the line rather than by letting go.
    if (g.launch != null) { t.roll(g.launch); return; }
    // A flick: upward, and still moving as it was let go. Out in the open tray it
    // has to be a real one, because carrying a die across the table and setting
    // it down must not throw the lot. Down in the throwing band the dice are
    // there to be thrown, so a gentle lift is enough.
    var speed = Date.now() - g.lastT > 90 ? 0 : g.speed;
    var dy = g.lastY - g.y0, dx = Math.abs(g.lastX - g.x0);
    var near = t.inZone(g.y0) || t.inZone(g.lastY);
    var needs = near ? LOB : FLICK;
    if (dy <= (near ? -16 : -THROW_PX) && speed >= needs && dx < Math.abs(dy) * 1.6) {
      t.roll(Math.max(0, (speed - needs) / (FAST - needs)));
    }
  };

  /* ---------- the throw ----------
   * Handed to a physics loop and left alone: the dice carry the speed your flick
   * gave them, fall under gravity, bounce off the walls and off each other, and
   * stop when they have nothing left. Nothing decides where they land, and
   * nothing decides what they say.
   */
  Tray.prototype.roll = function (strength) {
    var t = this;
    if (t.rolling) return;
    if (typeof strength !== "number") strength = 0.4;
    strength = Math.max(0, Math.min(1, strength));
    t.rolling = true;
    // A beat of nothing first: the dice are still leaving the hand when the
    // throw starts, and a landing heard before they have gone anywhere is a
    // noise rather than an event.
    if (t.snd) {
      playSlice(t.snd.url, t.snd.rollFrom, t.snd.rollLen, t.volume, false, t.snd.rollDelay);
    }
    var fb = t.field.getBoundingClientRect();
    var first = t.el(0);
    var size = first ? first.getBoundingClientRect().width : t.size;
    // A tumbling die is a square, and a square on its corner is wider than its
    // side: half its diagonal is what has to clear the wall, or it visibly cuts
    // through the edge of the screen on the way past.
    var reach = size * 0.708;
    var at = t.places();

    t.field.classList.add("rolling");
    for (var k = 0; k < t.count; k++) {
      var e0 = t.el(k);
      // The resting left/top has to GO, not be overridden: it is inline, and no
      // stylesheet rule outranks that — the transform lands on top of it, so
      // every throw comes out offset by wherever the die had been sitting.
      if (e0) { e0.style.left = "0"; e0.style.top = "0"; }
    }

    // Away from you and slightly outward, harder the harder it was flicked, and
    // never all exactly alike — dice thrown from one hand do not leave it in
    // formation. Gravity is already pulling them up the tray, so this only has
    // to start them off.
    var lift = 260 + 900 * strength, bodies = [];
    for (var i = 0; i < t.count; i++) {
      bodies.push({
        x: at[i].x / 100 * fb.width, y: at[i].y / 100 * fb.height,
        vx: (Math.random() - 0.5) * (260 + 420 * strength),
        vy: -lift * (0.8 + Math.random() * 0.4),
        rot: 0, spin: (Math.random() - 0.5) * (700 + 900 * strength),
        // The turn through its own faces, kept apart from the flat spin: one is
        // the die rotating on the felt, the other is it going over and over.
        tumble: Math.random() * 360, roll: Math.random() * 360
      });
    }
    t.sim = { bodies: bodies, w: fb.width, h: fb.height, r: size / 2,
              reach: reach, last: 0, still: 0, began: 0 };
    requestAnimationFrame(function (now) { t.step(now); });
  };

  Tray.prototype.step = function (now) {
    var t = this, sim = t.sim;
    if (!sim) return;
    // The first frame has no previous one to measure against, and a tab left in
    // the background hands back a gap of seconds — either would teleport them.
    var dt = sim.last ? Math.min(0.032, (now - sim.last) / 1000) : 0.016;
    sim.last = now;
    // The walls are re-measured every frame, before anything is written.
    // Measuring once at the throw leaves the simulation playing in a world wider
    // than the tray actually is — the layout settles a moment after the throw
    // starts — and dice bounce off walls that are not where the screen is.
    var fr = t.field.getBoundingClientRect();
    if (fr.width > 0) { sim.w = fr.width; sim.h = fr.height; }
    var b, i, j, bodies = sim.bodies, r = sim.r, w = sim.reach, moving = 0;

    for (i = 0; i < bodies.length; i++) {
      b = bodies[i];
      b.vy -= GRAVITY * dt; // toward the top, where the table is
      b.vx *= AIR; b.vy *= AIR;
      b.x += b.vx * dt; b.y += b.vy * dt;
      b.rot += b.spin * dt; b.spin *= 0.988;
      // Walls. A die hitting one loses most of what it had and is turned around.
      if (b.x < w) { b.x = w; b.vx = Math.abs(b.vx) * BOUNCE; b.spin = -b.spin * 0.7; }
      if (b.x > sim.w - w) { b.x = sim.w - w; b.vx = -Math.abs(b.vx) * BOUNCE; b.spin = -b.spin * 0.7; }
      if (b.y < w) {
        b.y = w;
        b.vy = Math.abs(b.vy) * BOUNCE;
        // The table drags: sliding and spinning both die out on it, which is
        // what eventually brings the whole throw to a stop.
        b.vx *= FLOOR_DRAG; b.spin *= 0.8;
        if (Math.abs(b.vy) < 60) b.vy = 0;
      }
      // The near edge is only a rail — a die that comes back this far bounces
      // off it rather than dropping out of the game.
      if (b.y > sim.h - w) { b.y = sim.h - w; b.vy = -Math.abs(b.vy) * BOUNCE; }
    }

    // Dice against dice, treated as discs: push them apart, then swap what they
    // were carrying along the line between them.
    for (i = 0; i < bodies.length; i++) {
      for (j = i + 1; j < bodies.length; j++) {
        var a = bodies[i], c = bodies[j];
        var dx = c.x - a.x, dy = c.y - a.y;
        var dist = Math.sqrt(dx * dx + dy * dy), min = r * 2;
        if (dist >= min) continue;
        if (dist < 0.01) { dx = 1; dy = 0; dist = 1; }
        var nx = dx / dist, ny = dy / dist, push = (min - dist) / 2;
        a.x -= nx * push; a.y -= ny * push;
        c.x += nx * push; c.y += ny * push;
        var rel = (c.vx - a.vx) * nx + (c.vy - a.vy) * ny;
        if (rel > 0) continue; // already parting
        var hit = -rel * 0.6;
        a.vx -= nx * hit; a.vy -= ny * hit;
        c.vx += nx * hit; c.vy += ny * hit;
        a.spin -= hit * 2; c.spin += hit * 2;
      }
    }

    // Pushing two dice apart can push one of them through a wall, and the wall
    // pass has already been and gone this frame. Without this the die is drawn
    // outside the tray for a frame and shoved back the next, which at six dice
    // in a corner is a die visibly out past the edge of the screen.
    for (i = 0; i < bodies.length; i++) {
      b = bodies[i];
      if (b.x < w) { b.x = w; if (b.vx < 0) b.vx = -b.vx * BOUNCE; }
      if (b.x > sim.w - w) { b.x = sim.w - w; if (b.vx > 0) b.vx = -b.vx * BOUNCE; }
      if (b.y < w) { b.y = w; if (b.vy < 0) b.vy = -b.vy * BOUNCE; }
      if (b.y > sim.h - w) { b.y = sim.h - w; if (b.vy > 0) b.vy = -b.vy * BOUNCE; }
    }

    for (i = 0; i < bodies.length; i++) {
      b = bodies[i];
      var speed = Math.abs(b.vx) + Math.abs(b.vy);
      if (speed > 26) moving++;
      var el = t.el(i);
      if (!el) continue;
      // It goes over at a rate set by how fast it is travelling, about two axes
      // at once, so no throw looks like the last one.
      b.tumble += speed * 0.8 * dt;
      b.roll += speed * 0.55 * dt;
      el.style.transform = "translate(" + b.x.toFixed(1) + "px," + b.y.toFixed(1) +
        "px) translate(-50%,-50%)";
      var cube = el.firstChild;
      if (cube) {
        cube.style.transform = "rotateZ(" + b.rot.toFixed(1) + "deg) rotateX(" +
          b.tumble.toFixed(1) + "deg) rotateY(" + b.roll.toFixed(1) + "deg)";
      }
    }

    // Still for a few frames running: they have settled, wherever that turned
    // out to be. The cap is only there so a pathological bounce cannot run on.
    sim.still = moving ? 0 : sim.still + 1;
    if (!sim.began) sim.began = now;
    if (sim.still < 8 && now - sim.began < 6000) {
      requestAnimationFrame(function (n) { t.step(n); });
      return;
    }
    t.settle(sim);
  };

  Tray.prototype.settle = function (sim) {
    var t = this, bodies = sim.bodies, i;
    // At rest a die is square to the felt, so half its width is what has to stay
    // inside — worked out from the die and the tray rather than assumed.
    var padX = sim.r / sim.w * 100, padY = sim.r / sim.h * 100;
    t.pos = bodies.map(function (b) {
      return {
        x: Math.max(padX, Math.min(100 - padX, b.x / sim.w * 100)),
        y: Math.max(padY, Math.min(100 - padY, b.y / sim.h * 100))
      };
    });
    // Two dice clamped to the same corner land on the same spot and read as a
    // stack of one. Shove them apart in the tray's own proportions — a percent
    // across a narrow felt is a much longer way than a percent up it.
    spaceOut(t.pos, padX * 2.1, sim.h / sim.w, padX, padY);

    t.field.classList.remove("rolling");
    for (i = 0; i < t.count; i++) {
      var b = bodies[i];
      // The die rocks onto the nearest flat side, and that side is read off it.
      // Nothing is drawn for the number afterwards, so it never lands showing
      // one face and then changes to another.
      var sx = Math.round(b.rot / 90) * 90;
      var sy = Math.round(b.tumble / 90) * 90;
      var sz = Math.round(b.roll / 90) * 90;
      t.vals[i] = faceToward(sx, sy, sz);
      var el = t.el(i), cube = el && el.firstChild;
      if (el) {
        el.style.left = t.pos[i].x.toFixed(2) + "%";
        el.style.top = t.pos[i].y.toFixed(2) + "%";
        el.style.transform = "";
      }
      if (cube) {
        cube.style.transform = "rotateZ(" + sx + "deg) rotateX(" + sy + "deg) rotateY(" + sz + "deg)";
      }
    }
    t.sim = null;
    t.rolling = false;
    t.changed();
    if (t.onSettle) t.onSettle(t.values(), t.total());

    // Read where they landed, then they walk back down to the band they are
    // thrown from. Dice that settle at the top have to be dragged the length of
    // the tray before every throw; bringing them home is the difference between
    // rolling again and setting up to roll again. The numbers do not change —
    // this is the same dice, moved.
    setTimeout(function () {
      if (t.rolling || t.drag || !t.field.isConnected) return;
      t.pos = t.home();
      t.place();
    }, 1000);
  };

  /* Shoves overlapping dice apart, a little at a time, and keeps them on the
     felt. [ratio] is the tray's height over its width, so a percent means the
     same distance in both directions. */
  function spaceOut(pos, gap, ratio, padX, padY) {
    for (var pass = 0; pass < 8; pass++) {
      for (var i = 0; i < pos.length; i++) {
        for (var j = i + 1; j < pos.length; j++) {
          var dx = pos[j].x - pos[i].x;
          var dy = (pos[j].y - pos[i].y) * ratio;
          var dist = Math.sqrt(dx * dx + dy * dy);
          // Dead centre on each other: nudge them off the same spot first.
          if (dist < 0.01) { pos[j].x += 0.5; dx = 0.5; dist = 0.5; }
          if (dist >= gap) continue;
          var push = (gap - dist) / 2, ux = dx / dist, uy = dy / dist;
          pos[i].x -= ux * push; pos[i].y -= uy * push / ratio;
          pos[j].x += ux * push; pos[j].y += uy * push / ratio;
        }
      }
      for (var k = 0; k < pos.length; k++) {
        pos[k].x = Math.max(padX, Math.min(100 - padX, pos[k].x));
        pos[k].y = Math.max(padY, Math.min(100 - padY, pos[k].y));
      }
    }
  }

  Tray.prototype.destroy = function () {
    this.field.removeEventListener("pointerdown", this._down);
    root.removeEventListener("pointermove", this._move);
    root.removeEventListener("pointerup", this._up);
    root.removeEventListener("pointercancel", this._cancel);
    if (this.drag) this.shakeOff(this.drag);
    this.sim = null;
    this.drag = null;
    this.host.innerHTML = "";
  };

  root.DiceTray = {
    create: function (host, opts) { return new Tray(host, opts); }
  };
})(window);
