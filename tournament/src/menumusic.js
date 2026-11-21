  /* ---------- menu music -------------------------------------------------
     One song, looping, on every screen that isn't a table. It starts on the
     first tap (phones won't play sound before one), follows the MUSIC dial,
     and stops for tables, the spray-can intro, the tips, and a hidden app. */
  var MenuMusic = (function () {
    var TABLES = /^screen-(baccarat|blackjack|craps|destroyer|djwild|doubledown|dragontiger|holdem|iluvsuits|keno|kirby|mines|miniuth|plinko|roulette|shootout|sicbo|wheel)$/;
    var a = null, ctx = null, gain = null, unlocked = false, on = false, stopT = null;
    function onMenu() { var s = document.querySelector(".screen.on"); return !!s && !TABLES.test(s.id); }
    function busy() { try { return typeof Intro !== "undefined" && Intro.isPlaying(); } catch (e) { return false; } }
    function level() { return typeof SOUND !== "undefined" ? SOUND.music : 0.3; }
    function setVol(v, secs) {
      if (gain) {
        var t = ctx.currentTime;
        gain.gain.cancelScheduledValues(t);
        gain.gain.setValueAtTime(gain.gain.value, t);
        gain.gain.linearRampToValueAtTime(v, t + (secs || 0.05));
      } else if (a) { try { a.volume = v; } catch (e) {} }
    }
    function make() {
      a = new Audio("menu-music.mp3"); a.loop = true; a.preload = "auto";
      try {
        var AC = window.AudioContext || window.webkitAudioContext;
        ctx = new AC(); gain = ctx.createGain(); gain.gain.value = 0;
        ctx.createMediaElementSource(a).connect(gain); gain.connect(ctx.destination);
      } catch (e) { ctx = null; gain = null; a.volume = 0; }
    }
    function sync() {
      var want = unlocked && !document.hidden && onMenu() && !busy() && level() > 0;
      if (want) {
        if (!a) make();
        clearTimeout(stopT);
        if (ctx && ctx.state !== "running") { try { ctx.resume(); } catch (e) {} }
        if (a.paused) { var p = a.play(); if (p && p.catch) p.catch(function () {}); }
        setVol(level(), on ? 0.1 : 0.8);
        on = true;
      } else if (on) {
        on = false;
        if (document.hidden) { setVol(0, 0.01); a.pause(); }
        else { setVol(0, 0.4); clearTimeout(stopT); stopT = setTimeout(function () { if (!on && a) a.pause(); }, 450); }
      }
    }
    function unlock() { if (!unlocked) { unlocked = true; sync(); } }
    document.addEventListener("pointerdown", unlock, true);
    document.addEventListener("touchend", unlock, true);
    document.addEventListener("visibilitychange", sync);
    window.addEventListener("pagehide", sync);
    setInterval(sync, 400);
    return { sync: sync, playing: function () { return !!a && !a.paused && on; } };
  })();
  window.MenuMusic = MenuMusic;
