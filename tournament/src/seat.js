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
          // off the tables it goes back to the corner nothing else uses
          fs.style.left = ""; fs.style.top = ""; fs.style.right = ""; fs.style.bottom = ""; fs.style.marginLeft = "";
        }
      }
      if (!btn) return;
      if (!table || cur.name === "Craps") { btn.hidden = true; return; }      // craps keeps you at the rail until your bets come down
      var b = back.getBoundingClientRect();
      btn.hidden = false;
      btn.style.left = Math.round(b.right + 7) + "px"; btn.style.top = Math.max(4, Math.round(b.top + (b.height - 34) / 2)) + "px";
    }
    /* ---- tap your money at a table: take or pay a marker without walking back to the lobby ---- */
    var pop = null;
    function closePop() { if (pop) { pop.remove(); pop = null; } }
    function syncTable() { try { if (cur && cur.mod.credit && cur.mod.cash) cur.mod.credit(Shell.getBankroll() - cur.mod.cash()); } catch (e) {} }
    function openPop(anchor) {
      closePop();
      pop = document.createElement("div"); pop.id = "seat-marker";
      var r = anchor.getBoundingClientRect();
      pop.style.top = Math.round(r.bottom + 6) + "px";
      pop.style.right = Math.max(6, Math.round(innerWidth - r.right)) + "px";
      document.body.appendChild(pop);
      paintPop();
    }
    function paintPop() {
      if (!pop) return;
      var owed = Shell.getDebt(), bank = Shell.getBankroll(), risk = atRisk();
      var get = typeof MARKER_AMOUNT !== "undefined" ? MARKER_AMOUNT : 5000, back = Math.round(get * (1 + (typeof MARKER_INTEREST !== "undefined" ? MARKER_INTEREST : 0.5)));
      var still = !(cur && cur.mod.seatOk && !cur.mod.seatOk());
      var why = "", canTake = false, canPay = false;
      if (Shell.mode !== "campaign") why = "No markers in Play Testing.";
      else if (TT.on && TT.points) why = "No markers in a points week. Bust a game and REBUY is free.";
      else {
        canTake = still && Shell.canOfferMarker();
        canPay = still && owed > 0 && bank - owed >= risk;
      }
      var h = '<b class="t">MARKERS</b>';
      if (why) h += '<p>' + why + '</p>';
      else {
        h += '<p class="o">' + (owed > 0 ? 'YOU OWE <i>$' + fmt(owed) + '</i>' + (TT.on && TT.unpaid ? ' · ' + TT.unpaid() + '/' + TT.max() : '') : 'YOU OWE NOTHING') + '</p>';
        h += '<button type="button" class="take"' + (canTake ? '' : ' disabled') + '>TAKE +$' + fmt(get) + '<small>owe $' + fmt(back) + ' back</small></button>';
        h += '<button type="button" class="pay"' + (canPay ? '' : ' disabled') + '>' + (owed > 0 ? 'PAY $' + fmt(owed) : 'PAY MARKER') + '<small>' + (owed > 0 ? 'clears what you owe' : 'nothing to pay') + '</small></button>';
        var note = !still ? "Finish this hand first."
          : !canTake && Shell.canTakeMarker() ? "The house lends once you're low on chips, same as in the lobby."
          : !canTake && !owed ? "" : !canTake ? "You're at the limit on markers."
          : "";
        if (owed > 0 && still && !canPay) note = bank < owed ? "Not enough to pay it yet." : "Take some chips off the felt to pay it.";
        if (note) h += '<p class="n">' + note + '</p>';
      }
      pop.innerHTML = h;
      var tk = pop.querySelector(".take"), py = pop.querySelector(".pay");
      if (tk) tk.onclick = function () { Shell.takeMarker(); syncTable(); say("MARKER TAKEN · +$" + fmt(get)); closePop(); };
      if (py) py.onclick = function () { Shell.payMarker(); syncTable(); say("MARKER PAID"); closePop(); };
    }
    document.addEventListener("click", function (e) {
      if (pop && e.target.closest && e.target.closest("#seat-marker")) return;
      var had = !!pop; closePop();
      var m = e.target.closest && e.target.closest(".purse, .uth-money, #rl-bankroll, #rl-staked");
      if (!m || had || !cur) return;
      var scr = m.closest(".screen");
      if (!scr || !scr.classList.contains("on")) return;
      openPop(m.id === "rl-staked" ? (document.getElementById("rl-bankroll") || m) : m);
    }, true);

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
