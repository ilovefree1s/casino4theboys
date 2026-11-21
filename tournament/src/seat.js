  /* ---------- seat save: leave a table with your bets still down, and come back to them ---------- */
  var Seat = (function () {
    var saved = {}, cur = null, lastTile = null, btn = null, note = null, noteT = 0;
    function mods() {
      try {
        return { Baccarat: Baccarat, DragonTiger: DragonTiger, Blackjack: Blackjack, DoubleDown: DoubleDown, Holdem: Holdem, DjWild: DjWild,
                 Shootout: Shootout, MiniUth: MiniUth, IluvSuits: IluvSuits, Kirby: Kirby, Roulette: Roulette, Keno: Keno, Wheel: Wheel,
                 SicBo: SicBo, Plinko: Plinko, Mines: Mines, Craps: Craps, Destroyer: Destroyer };
      } catch (e) { return {}; }
    }
    function say(text) {
      if (!note) { note = document.createElement("div"); note.id = "seat-note"; document.body.appendChild(note); }
      note.textContent = text; note.classList.add("on");
      clearTimeout(noteT); noteT = setTimeout(function () { note.classList.remove("on"); }, 2600);
    }
    /** What's on the felt right now, read off the table's own AT RISK / STAKED figure. */
    function atRisk() {
      var scr = document.querySelector(".screen.on"), e = scr && scr.querySelector(".val.risk, .val.staked, .rl-money.staked");
      if (!e) return 0;
      if (e.dataset && e.dataset.raw != null && !/\d,\d|^\s*\$?\d+\s*$/.test(e.textContent)) return +e.dataset.raw;
      var d = e.textContent.replace(/[^0-9]/g, ""); return d ? +d : 0;
    }
    function tags() {
      document.querySelectorAll(".seat-tag").forEach(function (t) { t.remove(); });
      Object.keys(saved).forEach(function (n) {
        var tile = saved[n].tile && document.getElementById(saved[n].tile);
        if (!tile) return;
        if (getComputedStyle(tile).position === "static") tile.style.position = "relative";
        var t = document.createElement("span"); t.className = "seat-tag"; t.textContent = "SEAT SAVED"; tile.appendChild(t);
      });
    }
    function wrap() {
      var m = mods();
      Object.keys(m).forEach(function (name) {
        var M = m[name];
        if (!M || !M.enter || M._seat) return;
        M._seat = 1;
        var orig = M.enter;
        M.enter = function (v) {
          var key = name + ":" + (v == null ? "" : v), keep = saved[name], self = this, args = arguments;
          cur = { name: name, key: key, mod: M, tile: lastTile };
          delete saved[name];
          if (keep && keep.key === key && M.credit && M.cash) {
            // your chips are where you left them: only the money is brought up to date
            try { if (Shell.mode === "campaign") M.credit(Shell.getBankroll() - M.cash()); else M.credit(0); } catch (e) {}
            tags();
            setTimeout(function () {
              if (cur && cur.mod === M && atRisk() > M.cash() + 0.5) {       // spent it somewhere else: can't cover the felt any more
                orig.apply(self, args); say("NOT ENOUGH LEFT FOR THE BETS YOU SAVED · TABLE CLEARED");
              }
            }, 80);
            return;
          }
          tags();
          return orig.apply(this, arguments);
        };
      });
    }
    function save() {
      var scr = document.querySelector(".screen.on"), back = scr && scr.querySelector(".wayout");
      if (!cur || !back) return;
      var M = cur.mod, ok = M.seatOk ? M.seatOk() : !(M.busy && M.busy());
      if (!ok) return say("FINISH THIS HAND FIRST");
      var name = cur.name;
      saved[name] = { key: cur.key, tile: cur.tile };
      back.click();
      if (document.querySelector(".screen.on") === scr) { delete saved[name]; return; }      // the table wouldn't let go (it says why itself)
      tags();
      say("SEAT SAVED · YOUR BETS ARE WAITING");
    }
    /* the two corner buttons ride with whatever table is up: SAVE SEAT just right of the way out, fullscreen just left of your chips */
    function place() {
      var scr = document.querySelector(".screen.on"), fs = document.getElementById("fs-btn");
      // where the money is shown: most tables have a purse; craps, Destroyer and roulette write it out plain
      var purse = null, back = scr && scr.querySelector(".wayout");
      if (scr) [".purse", ".uth-money", "#rl-bankroll"].some(function (q) { var e = scr.querySelector(q); if (e && e.offsetParent) { purse = e; return true; } return false; });
      var table = !!(purse && back && cur);
      if (fs) {
        if (table) {
          var p = purse.getBoundingClientRect();
          fs.style.left = Math.max(0, Math.round(p.left - 8 - 34)) + "px"; fs.style.top = Math.max(4, Math.round(p.top + (p.height - 34) / 2)) + "px";
          fs.style.right = "auto"; fs.style.bottom = "auto"; fs.style.marginLeft = "0";
        } else {
          fs.style.left = "auto"; fs.style.bottom = "auto"; fs.style.marginLeft = "0";
          fs.style.right = "12px"; fs.style.top = "calc(env(safe-area-inset-top, 0px) + 12px)";
        }
      }
      if (!btn) return;
      if (!table || cur.name === "Craps") { btn.hidden = true; return; }      // craps keeps you at the rail until your bets come down
      var b = back.getBoundingClientRect();
      btn.hidden = false;
      btn.style.left = Math.round(b.right + 7) + "px"; btn.style.top = Math.max(4, Math.round(b.top + (b.height - 34) / 2)) + "px";
    }
    function init() {
      wrap();
      btn = document.createElement("button"); btn.type = "button"; btn.id = "seat-btn"; btn.hidden = true;
      btn.setAttribute("aria-label", "Save your seat: leave with your bets still down");
      btn.innerHTML = "SAVE<br>SEAT";
      btn.addEventListener("click", save);
      document.body.appendChild(btn);
      // which game button was pressed last: that's the one that gets the SEAT SAVED tag
      document.addEventListener("click", function (e) {
        var b = e.target.closest && e.target.closest("button[id]");
        if (b && /^(bm|bjm|pk|rlm|cm|om)-(?!back|sign|spot|buyin)/.test(b.id)) lastTile = b.id;
      }, true);
      var mo = new MutationObserver(function () { requestAnimationFrame(place); });
      document.querySelectorAll(".screen").forEach(function (s) { mo.observe(s, { attributes: true, attributeFilter: ["class"] }); });
      ["resize", "orientationchange", "fullscreenchange", "webkitfullscreenchange"].forEach(function (ev) { addEventListener(ev, function () { setTimeout(place, 60); }); });
      setInterval(place, 500);
      place();
    }
    setTimeout(init, 0);
    return { save: save, saved: function () { return saved; }, place: place };
  })();
  try { window.Seat = Seat; } catch (e) {}
