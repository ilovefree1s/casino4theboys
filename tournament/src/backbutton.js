  /* ================= THE PHONE'S BACK BUTTON =================
   * Back steps the game back one page instead of closing it. While you are
   * anywhere but the front page, one spare history entry is kept; the phone's
   * Back uses it up, and this presses the screen's own back button - the same
   * one you would tap, with whatever tidying up it does. On the front page,
   * with nothing open, Back leaves as it always did.
   */
  (function () {
    if (!window.history || !history.pushState) return;
    var armed = false, ignore = 0;
    // pop-ups Back can close, and the button that closes each
    var CLOSE = { "tt-rules": "tt-rules-ok", "tt-crown": "tt-crown-ok", "tt-join": "tt-join-x", "tt-board": "tt-board-x" };
    function current() {
      var s = document.querySelector(".screen.on");
      return s ? s.id.replace("screen-", "") : "menu";
    }
    function popup() { return document.querySelector(".ov.on"); }
    // the Play Testing panel opened up on the front page counts as a page of its own
    function testing() { var t = $("mm-testing"); return current() === "menu" && t && t.classList.contains("open"); }
    function settle() {
      if (current() !== "menu" || popup() || testing()) {
        if (!armed) { history.pushState({ c4tbBack: 1 }, ""); armed = true; }
      } else if (armed) {
        armed = false; ignore++; history.back();   // back on the front page: give the spare entry back
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
      var o = popup();
      if (o) {
        // a pop-up that is only news closes; one asking you to decide (going broke, a result) stays
        if (CLOSE[o.id]) $(CLOSE[o.id]).click();
      } else if (testing()) {
        $("mm-testing-hdr").click();               // fold Play Testing back up
      } else if (current() !== "menu") {
        var b = document.querySelector(".screen.on .wayout");
        if (b) b.click();
      }
      settle();
    });
    settle();
  })();
