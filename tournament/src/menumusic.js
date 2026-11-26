  /* ---------- menu music -------------------------------------------------
     One song, looping, on every screen that isn't a table. It starts on the
     first tap (phones won't play sound before one), follows the MUSIC dial,
     and stops for tables, the spray-can intro, the tips, and a hidden app. */
  var MenuMusic = (function () {
    var TABLES = /^screen-(baccarat|blackjack|craps|destroyer|djwild|doubledown|dragontiger|holdem|iluvsuits|keno|kirby|mines|miniuth|plinko|roulette|shootout|sicbo|wheel|paigow|chain|highlow|threeshot|breakout)$/;
    var ctx = null, gain = null, src = null, loading = false, unlocked = false, on = false, stopT = null;
    function onMenu() { var s = document.querySelector(".screen.on"); return !!s && !TABLES.test(s.id); }
    function busy() { try { return typeof Intro !== "undefined" && Intro.isPlaying(); } catch (e) { return false; } }
    function level() { return typeof SOUND !== "undefined" ? SOUND.music : 0.3; }
    function setVol(v, secs) {
      var t = ctx.currentTime;
      gain.gain.cancelScheduledValues(t);
      gain.gain.setValueAtTime(gain.gain.value, t);
      gain.gain.linearRampToValueAtTime(v, t + (secs || 0.05));
    }
    // The song is decoded and played through the same kind of volume knob as the tips beat,
    // so the MUSIC dial really is the volume on every phone.
    function make() {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      ctx = new AC(); gain = ctx.createGain(); gain.gain.value = 0; gain.connect(ctx.destination);
      loading = true;
      fetch("menu-music.mp3").then(function (r) { return r.arrayBuffer(); }).then(function (ab) {
        return new Promise(function (ok, no) { var p = ctx.decodeAudioData(ab, ok, no); if (p && p.catch) p.catch(function () {}); });
      }).then(function (buf) {
        src = ctx.createBufferSource(); src.buffer = buf; src.loop = true; src.connect(gain); src.start(0);
        loading = false; on = false; sync();
      }).catch(function () { loading = false; });
    }
    function sync() {
      var want = unlocked && !document.hidden && onMenu() && !busy() && level() > 0;
      if (want && !ctx) make();
      if (!ctx || !src) return;
      if (want) {
        clearTimeout(stopT);
        if (ctx.state !== "running") { try { ctx.resume(); } catch (e) {} }
        setVol(level(), on ? 0.1 : 0.8);
        on = true;
      } else if (on || ctx.state === "running") {
        var was = on; on = false;
        if (document.hidden || !was) { gain.gain.cancelScheduledValues(ctx.currentTime); gain.gain.value = 0; try { ctx.suspend(); } catch (e) {} }
        else { setVol(0, 0.4); clearTimeout(stopT); stopT = setTimeout(function () { if (!on) { try { ctx.suspend(); } catch (e) {} } }, 450); }
      }
    }
    function unlock() { if (!unlocked) { unlocked = true; sync(); } }
    document.addEventListener("pointerdown", unlock, true);
    document.addEventListener("touchend", unlock, true);
    document.addEventListener("visibilitychange", sync);
    window.addEventListener("pagehide", sync);
    setInterval(sync, 400);
    // fetch and decode the song while the page is still loading, so the first tap starts it at once
    setTimeout(function () { if (!ctx && level() > 0) { try { make(); } catch (e) {} } }, 300);
    return { sync: sync, playing: function () { return !!src && on && ctx.state === "running"; },
             dbg: function () { return { gain: gain ? gain.gain.value : null, ctx: ctx ? ctx.state : null, t: ctx ? ctx.currentTime : null, level: level(), loaded: !!src }; } };
  })();
  window.MenuMusic = MenuMusic;
