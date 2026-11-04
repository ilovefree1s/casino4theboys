"""Builds index.html: the original game plus the tournament.

python3 src/build.py <original.html> <out.html> [supabase_url] [anon_key]
"""
import sys, json, pathlib

src, out = sys.argv[1], sys.argv[2]
url = sys.argv[3] if len(sys.argv) > 3 else ""
key = sys.argv[4] if len(sys.argv) > 4 else ""
here = pathlib.Path(__file__).parent
h = open(src, encoding="utf-8").read()

def sub(old, new, count=1):
    global h
    n = h.count(old)
    if n != count:
        raise SystemExit(f"expected {count} of {old[:70]!r}, found {n}")
    h = h.replace(old, new)

# ---- the app icon: browser tab, and the black bar colour on Android ----
sub('<link rel="manifest" href="manifest.webmanifest">',
    '<link rel="manifest" href="manifest.webmanifest">\n'
    '<link rel="icon" type="image/png" href="favicon.png">\n'
    '<meta name="theme-color" content="#000000">')

# ---- styles, before the menu markup ----
sub("</style>\n\n<!-- ================= MENU ================= -->",
    (here / "tourney.css").read_text() + "</style>\n\n<!-- ================= MENU ================= -->")

# ---- the lane on the front page, above the campaign ----
sub("      <!-- the run -->\n",
    """      <!-- the week-long contest -->
      <div class="mm-lane tourney" style="--c:#FF7A3D">
        <i class="edge"></i>
        <div class="mm-hdr"><span class="nm">TOURNAMENT</span><span class="tag" id="tt-tag">THIS WEEK</span></div>
        <div class="tt-line" id="tt-line">CONNECTING…</div>
        <div class="tt-btns">
          <button type="button" id="tt-play">PLAY</button>
          <button type="button" id="tt-boardbtn">BOARD</button>
        </div>
      </div>
      <!-- the run -->
""")

# ---- overlays ----
sub('<!-- ================= OVERLAYS ================= -->\n',
    '<!-- ================= OVERLAYS ================= -->\n' + (here / "tourney.html").read_text())

# ---- the shell: the tournament's own wallet ----
sub("  var Shell = {\n",
    "  /* Where the tournament keeps score. The anon key is meant to be public. */\n"
    f"  var TT_CONFIG = {{ url: {json.dumps(url)}, key: {json.dumps(key)} }};\n"
    "  var TT = { on: false, closed: false, week: \"\" };\n"
    "  function wkey(name) { return TT.on ? TT.key(name) : \"ftb.\" + name; }\n"
    "  var Shell = {\n")
sub('getBankroll: function () { return wholeDollars(store.get("ftb.bankroll", CAMPAIGN_START)); },',
    'getBankroll: function () { return wholeDollars(store.get(wkey("bankroll"), CAMPAIGN_START)); },')
sub('setBankroll: function (v) { store.set("ftb.bankroll", wholeDollars(v)); },',
    'setBankroll: function (v) {\n'
    '      if (TT.on && TT.closed) return;\n'
    '      if (TT.on) TT.track(wholeDollars(v) - this.getBankroll());\n'
    '      store.set(wkey("bankroll"), wholeDollars(v));\n'
    '      if (TT.on) TT.changed();\n'
    '    },')
sub('getGoal: function () { return store.get("ftb.goal", CAMPAIGN_GOAL); },',
    'getGoal: function () { return TT.on ? Infinity : store.get("ftb.goal", CAMPAIGN_GOAL); },')
sub('setGoal: function (v) { store.set("ftb.goal", v); },',
    'setGoal: function (v) { if (!TT.on) store.set("ftb.goal", v); },')
sub('var key = store.getStr("ftb.room", "BASEMENT");',
    'var key = store.getStr(wkey("room"), "BASEMENT");')
sub('getDebt: function () { return store.get("ftb.debt", 0); },',
    'getDebt: function () { return store.get(wkey("debt"), 0); },')
sub('setDebt: function (v) { store.set("ftb.debt", v); },',
    'setDebt: function (v) {\n'
    '      if (TT.on && TT.closed) return;\n'
    '      store.set(wkey("debt"), v);\n'
    '      if (TT.on) TT.changed();\n'
    '    },')
sub('store.setStr("ftb.room", room.key);', 'store.setStr(wkey("room"), room.key);')
sub('store.setStr("ftb.room", ROOMS[i].key);', 'store.setStr(wkey("room"), ROOMS[i].key);')
sub('store.setStr("ftb.room", ROOMS[0].key);', 'store.setStr(wkey("room"), ROOMS[0].key);')

# markers: three a week in the tournament, counted whether paid back or not
sub("    canTakeMarker: function () {\n",
    "    canTakeMarker: function () {\n"
    "      if (TT.on) return !TT.closed && !TT.isOut() && TT.markers() < TT.max();\n")
sub("      if (!this.canTakeMarker()) return;\n      this.setBankroll(this.getBankroll() + MARKER_AMOUNT);\n",
    "      if (!this.canTakeMarker()) return;\n      TT.quiet = true;\n      this.setBankroll(this.getBankroll() + MARKER_AMOUNT);\n      TT.quiet = false;\n")
sub("      this.setDebt(this.getDebt() + MARKER_AMOUNT * (1 + MARKER_INTEREST));\n    },",
    "      this.setDebt(this.getDebt() + MARKER_AMOUNT * (1 + MARKER_INTEREST));\n"
    "      if (TT.on) TT.signMarker();\n    },")
sub("      if (!this.canPayMarker()) return;\n      this.setBankroll(this.getBankroll() - this.getDebt());\n",
    "      if (!this.canPayMarker()) return;\n      TT.quiet = true;\n      this.setBankroll(this.getBankroll() - this.getDebt());\n      TT.quiet = false;\n")
sub("    restartCampaign: function () {\n",
    "    restartCampaign: function () {\n      if (TT.on) return;\n")

# the lobby and the menu
sub("      if (mode) this.mode = mode;\n      var scr = $(\"screen-lobby\");\n",
    "      if (mode) { this.mode = mode; TT.on = false; }\n      var scr = $(\"screen-lobby\");\n"
    "      scr.classList.toggle(\"tourney\", TT.on);\n")
sub("    showMenu: function () { paintMenu(); this.show(\"menu\"); },",
    "    showMenu: function () { TT.on = false; paintMenu(); this.show(\"menu\"); },")
sub('b.addEventListener("click", function () { Shell.mode = "testing"; g.go(); });',
    'b.addEventListener("click", function () { Shell.mode = "testing"; TT.on = false; g.go(); });')
sub('$("mm-other").addEventListener("click", function () { Shell.mode = "testing"; Shell.show("othermenu"); });',
    '$("mm-other").addEventListener("click", function () { Shell.mode = "testing"; TT.on = false; Shell.show("othermenu"); });')
sub("    Shell.mode = \"campaign\";\n    Shell.showFloor();",
    "    Shell.mode = \"campaign\";\n    TT.on = false;\n    Shell.showFloor();")

# busting: a marker while there are any left this week, then out
sub("""        var owed = self.getDebt(), credit = self.canTakeMarker();
""", """        var owed = self.getDebt(), credit = self.canTakeMarker();
        if (TT.on) {
          var signed = TT.markers(), max = TT.max();
          if (!credit) TT.knockOut();
          $("ov-gameover-sub").textContent = credit
            ? "The purse is gone. " + (signed ? "You've signed " + signed + " of " + max + " markers." : "The house will cover you.")
            : "That was marker " + signed + " of " + max + ". You're out for the week.";
          $("ov-loan").textContent = credit
            ? "SIGN MARKER " + (signed + 1) + " OF " + max
            : "SEE THE BOARD";
          $("ov-loan-terms").textContent = credit
            ? "$" + fmt(MARKER_AMOUNT * (1 + MARKER_INTEREST)) + " to clear · bust after marker " + max + " and you're out"
            : "final score " + TT.money(b - owed);
          $("ov-gameover").classList.add("on");
          $("ov-loan").onclick = function () {
            if (credit) self.takeMarker();
            $("ov-gameover").classList.remove("on");
            self.showMenu();
            if (!credit) TT.showBoard();
            if (onDone) onDone();
          };
          return true;
        }
""")

# the lobby's money line, in the tournament
sub("""  function paintRun(id) {
    var bank = Shell.getBankroll(), goal = Shell.getGoal();
    var host = $(id);
""", """  function paintRun(id) {
    var bank = Shell.getBankroll(), goal = Shell.getGoal();
    var host = $(id);
    if (TT.on) {
      var f = TT.frame() || {}, owed = Shell.getDebt();
      host.querySelector(".k").textContent = "WEEK " + (f.week_no || 1) + " · " +
        (f.day_no === 7 ? "FINAL DAY" : "DAY " + (f.day_no || 1));
      host.querySelector(".v").textContent = "$" + fmt(bank);
      host.querySelector(".g").textContent = (owed > 0 ? "OWES $" + fmt(owed) + " · " : "") +
        "MARKERS " + TT.markers() + "/" + TT.max();
      return;
    }
    host.querySelector(".k").textContent = "CAMPAIGN";
""")

# the tournament itself, once the menu is painted
sub("  paintMenu();\n  /* ---------- sound",
    "  paintMenu();\n" + (here / "tourney.js").read_text() + "  /* ---------- sound")

open(out, "w", encoding="utf-8").write(h)
print("built", out, len(h))
