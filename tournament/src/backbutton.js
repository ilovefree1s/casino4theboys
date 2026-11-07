  /* ================= THE PHONE'S BACK BUTTON =================
   * Back steps the game back one page instead of closing it. While you are
   * anywhere but the front page (or a pop-up or the Play Testing panel is
   * open), one spare history step sits ahead of the page. The phone's Back
   * steps onto the page underneath; this presses the screen's own back button,
   * the same one you would tap, then steps forward onto the spare again.
   *
   * Chrome on phones skips history steps a page adds without a tap, so a spare
   * added while handling Back would be jumped over by the next Back, closing
   * the game. So the spare is only ever added on a tap, and Back re-uses it by
   * stepping forward, which adds nothing.
   */
  (function () {
    if (!window.history || !history.pushState) return;
    var armed = false, ignore = 0, handling = false;
    // pop-ups Back can close, and the button that closes each
    var CLOSE = { "tt-rules": "tt-rules-ok", "tt-crown": "tt-crown-ok", "tt-join": "tt-join-x", "tt-board": "tt-board-x" };
    function current() {
      var s = document.querySelector(".screen.on");
      return s ? s.id.replace("screen-", "") : "menu";
    }
    function popup() { return document.querySelector(".ov.on"); }
    // the Play Testing panel opened up on the front page counts as a page of its own
    function testing() { var t = $("mm-testing"); return current() === "menu" && t && t.classList.contains("open"); }
    function deep() { return current() !== "menu" || !!popup() || testing(); }
    function settle() {
      if (handling) return;
      if (deep()) {
        if (!armed) { history.pushState({ c4tbBack: 1 }, ""); armed = true; }
      } else if (armed) {
        armed = false; ignore++; history.back();   // back on the front page by a tap: give the spare back
      }
    }
    var show = Shell.show;
    Shell.show = function () { var r = show.apply(this, arguments); settle(); return r; };
    document.querySelectorAll(".ov, #mm-testing").forEach(function (o) {
      new MutationObserver(settle).observe(o, { attributes: true, attributeFilter: ["class"] });
    });
    window.addEventListener("popstate", function () {
      if (ignore) { ignore--; return; }
      armed = false;
      handling = true;
      try {
        var o = popup();
        if (o) {
          // a pop-up that is only news closes; one asking you to decide (going broke, a result) stays
          if (CLOSE[o.id]) $(CLOSE[o.id]).click();
        } else if (testing()) {
          $("mm-testing-hdr").click();             // fold Play Testing back up
        } else if (current() !== "menu") {
          var b = document.querySelector(".screen.on .wayout");
          if (b) b.click();
        }
      } finally { handling = false; }
      // still somewhere Back should work: step forward onto the spare again
      if (deep()) { ignore++; armed = true; history.forward(); }
    });
    settle();
  })();
