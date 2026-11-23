"""Builds index.html: the original game plus the tournament.

python3 src/build.py <original.html> <out.html> [supabase_url] [anon_key]
"""
import sys, json, pathlib, re

src, out = sys.argv[1], sys.argv[2]
url = sys.argv[3] if len(sys.argv) > 3 else ""
key = sys.argv[4] if len(sys.argv) > 4 else ""
here = pathlib.Path(__file__).parent
# the version on the front-page sign: bump it with each update that goes out
VERSION = "2.02"   # bump with each update: 2.02, 2.03, ...
h = open(src, encoding="utf-8").read()
SEAT = __import__("os").environ.get("SEAT", "1") != "0"      # seat save and the moved fullscreen button: built in only when asked for, until it ships

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

# ---- the install tags belong in <head>: the page came from an artifact,
# whose wrapper left them in <body>, and Chrome will not install from there ----
import re as _re
head_tags = []
for pat in [r'<title>[^<]*</title>\n', r'<meta name="(?:viewport|apple-mobile-web-app-[a-z-]+|mobile-web-app-capable|theme-color)"[^>]*>\n',
            r'<link rel="(?:apple-touch-icon|manifest|icon)"[^>]*>\n']:
    for m in _re.findall(pat, h[:20000]):
        head_tags.append(m)
        h = h.replace(m, "", 1)
h = h.replace('<meta name=viewport content="width=device-width,initial-scale=1,viewport-fit=cover">', "", 1)
sub("</head><body>", "".join(head_tags) +
    "<script>if ('serviceWorker' in navigator && /^https:/.test(location.protocol)) "
    "navigator.serviceWorker.register('sw.js').catch(function () {});</script>\n</head><body>")

# ---- styles, before the menu markup ----
sub("</style>\n\n<!-- ================= MENU ================= -->",
    (here / "tourney.css").read_text() + "</style>\n\n<!-- ================= MENU ================= -->")

# ---- the lane on the front page, above the campaign ----
sub("      <!-- the run -->\n",
    """      <!-- the week-long contest -->
      <div class="mm-lane tourney" style="--c:#FF7A3D">
        <i class="edge"></i>
        <div class="mm-hdr"><span class="nm">TOURNAMENT</span><span class="tag" id="tt-tag">THIS WEEK</span><span class="tt-place" id="tt-place" hidden><b id="tt-place-n"></b><i>PLACE</i></span></div>
        <div class="tt-hots" id="tt-hots" hidden></div>
        <div class="tt-line" id="tt-line">CONNECTING…</div>
        <div class="tt-stats" id="tt-stats" hidden>
          <div class="l"><span class="dy"><i>DAY</i><b id="tt-st-day"></b></span><span class="td"><i id="tt-st-k"></i><b id="tt-st-v"></b></span></div>
          <div class="bx"><i id="tt-st-bk"></i><b id="tt-st-bv"></b></div>
        </div>
        <div class="tt-wins" id="tt-lane-days" hidden></div>
        <div class="tt-btns">
          <button type="button" id="tt-play">PLAY</button>
          <button type="button" id="tt-boardbtn">BOARD<i class="tt-dot" hidden></i></button>
        </div>
      </div>
      <!-- the run -->
""")

# ---- play testing folds down to its header; a tap opens it ----
sub('<div class="mm-lane testing" style="--c:#9BE3FB">\n        <i class="edge"></i>\n        <div class="mm-hdr">',
    '<div class="mm-lane testing" id="mm-testing" style="--c:#9BE3FB">\n        <i class="edge"></i>\n'
    '        <div class="mm-hdr" id="mm-testing-hdr" role="button" tabindex="0" aria-expanded="false" aria-controls="mm-games">')
sub('<span class="tag" id="mm-buyin">$20,000 IN CHIPS</span>',
    '<span class="tag" id="mm-buyin">$20,000 IN CHIPS</span><span class="chev" aria-hidden="true">▸</span>')
sub("  paintMenu();\n  /* ---------- sound",
    "  (function () {\n"
    "    var lane = $(\"mm-testing\"), hdr = $(\"mm-testing-hdr\");\n"
    "    function flip() {\n"
    "      var open = lane.classList.toggle(\"open\");\n"
    "      hdr.setAttribute(\"aria-expanded\", open ? \"true\" : \"false\");\n"
    "      if (open) setTimeout(function () { lane.scrollIntoView({ behavior: \"smooth\", block: \"nearest\" }); }, 30);\n"
    "    }\n"
    "    hdr.addEventListener(\"click\", flip);\n"
    "    hdr.addEventListener(\"keydown\", function (e) { if (e.key === \"Enter\" || e.key === \" \") { e.preventDefault(); flip(); } });\n"
    "  })();\n"
    "  paintMenu();\n  /* ---------- sound")

# ---- money wheel: the newest hit on the left, older ones pushed right ----
sub("      r.innerHTML = history.slice(-16).map(function (k) {\n        var S = SYM[k];\n        return '<b style=\"",
    "      r.innerHTML = history.slice(-16).reverse().map(function (k, i) {\n        var S = SYM[k];\n        return '<b' + (i === 0 ? ' class=\"new\"' : '') + ' style=\"")
sub("  .wh-strip b { width: 18px;",
    "  .wh-strip b.new { box-shadow: 0 0 0 2px rgba(245,241,232,.92), 0 0 0 3.5px #141018; }\n  .wh-strip b { width: 18px;")

# ---- plinko's back button shared its id with poker's, so it went nowhere ----
sub('<button class="wayout" id="pk-back" aria-label="Back to other games">',
    '<button class="wayout" id="plk-back" aria-label="Back to other games">')
sub('$("pk-back").addEventListener("click", function () { Plinko.leave(); Shell.show("othermenu"); });',
    '$("plk-back").addEventListener("click", function () { Plinko.leave(); Shell.show("othermenu"); });')

# ---- the front page: chip rain behind glass, the sign in its own gold box ----
sub('  <div class="mm-col">\n    <div class="mm-name">Casino Games</div>\n    <div class="mm-casino">4 THE BOYS</div>\n',
    '  <canvas id="cr-rain" aria-hidden="true"></canvas><div class="cr-vignette"></div>\n'
    '  <div class="mm-col">\n    <div class="mm-sign">\n      <div class="mm-name">Casino Games<span class="mm-ver" id="mm-ver">v' + VERSION + '</span></div>\n'
    '      <div class="mm-casino">4 THE BOYS</div>\n    </div>\n')
sub("  paintMenu();\n  /* ---------- sound",
    (here / "chiprain.js").read_text() + "  paintMenu();\n  /* ---------- sound")

# ---- tournament: the marker is on offer once you are down $4,000 ($1,000 or less) ----
sub("      return this.mode === \"campaign\" && this.getBankroll() < MARKER_OFFER_BELOW && this.canTakeMarker();",
    "      if (TT.on) return this.getBankroll() <= TT_MARKER_AT && this.canTakeMarker();\n"
    "      return this.mode === \"campaign\" && this.getBankroll() < MARKER_OFFER_BELOW && this.canTakeMarker();")
sub("  var MARKER_OFFER_BELOW = 2500;",
    "  var MARKER_OFFER_BELOW = 2500;\n  var TT_MARKER_AT = 1000;   // tournament: down $4,000 from the $5,000 start")

# ---- the phone's Back steps back a page instead of closing the game ----
sub("  paintMenu();\n  /* ---------- sound",
    (here / "backbutton.js").read_text() + "  paintMenu();\n  /* ---------- sound")

# ---- the tables' top line: the tournament says so, rather than "CAMPAIGN · GOAL $∞" ----
sub('sub.textContent = "CAMPAIGN · GOAL $" + fmt(Shell.getGoal());', 'sub.textContent = goalLine();', 2)
sub("? 'CAMPAIGN · GOAL $' + fmt(Shell.getGoal()) + ' · <span", "? goalLine() + ' · <span")
sub("  var Shell = {\n",
    "  function goalLine() {\n"
    "    if (TT.on) { var f = TT.frame ? TT.frame() : null; return \"TOURNAMENT · WEEK \" + ((f && f.week_no) || 1); }\n"
    "    return \"CAMPAIGN · GOAL $\" + fmt(Shell.getGoal());\n"
    "  }\n"
    "  var Shell = {\n")

# ---- Lightning Roulette: an unstruck straight-up pays LIGHTNING_STRAIGHT to 1, not 35 ----
sub("      return (bet.pockets.length === 1 && worn) ? stake * (worn + 1) : stake * (bet.pays + 1);",
    "      if (bet.pockets.length === 1) return stake * ((worn || LIGHTNING_STRAIGHT) + 1);\n"
    "      return stake * (bet.pays + 1);")
sub("    var STRIKE_MULTS = [[50, 38], [100, 30], [200, 15], [300, 9], [400, 5], [500, 3]];",
    "    var STRIKE_MULTS = [[50, 38], [100, 30], [200, 15], [300, 9], [400, 5], [500, 3]];\n"
    "    // what a straight-up pays when it isn't struck: the lightning is paid for out of this\n"
    "    var LIGHTNING_STRAIGHT = 30;")
sub('lightning: ["⚡ LIGHTNING", "STRUCK STRAIGHT UP PAYS 50X TO 500X", "#FFD24D"],',
    'lightning: ["⚡ LIGHTNING", "STRUCK STRAIGHT UP PAYS 50X TO 500X · OTHERS 30 TO 1", "#FFD24D"],')

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
    "      if (TT.on && (TT.closed || TT.isOut() || TT.points)) return false;\n")
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
        if (TT.on && TT.points) {
          // points week: a busted game is a free rebuy, back to a fresh $10,000
          $("ov-gameover-sub").textContent = "This game's $10,000 is gone. Rebuy and start this game over. The loss doesn't count.";
          $("ov-loan").textContent = "REBUY · FRESH $10,000";
          $("ov-loan-terms").textContent = "free · unlimited · only what you have at midnight counts";
          $("ov-gameover").classList.add("on");
          $("ov-loan").onclick = function () {
            $("ov-gameover").classList.remove("on");
            TT.rebuy();
            if (onDone) onDone();
          };
          return true;
        }
        if (TT.on) {
          var unpaid = TT.unpaid(), max = TT.max();
          if (!credit) TT.knockOut();
          $("ov-gameover-sub").textContent = credit
            ? "The purse is gone. " + (unpaid ? "You're holding " + unpaid + " of " + max + " unpaid markers." : "The house will cover you.")
            : "Broke with " + max + " unpaid markers. You're out for the week.";
          $("ov-loan").textContent = credit
            ? "SIGN MARKER · " + (unpaid + 1) + " OF " + max + " UNPAID"
            : "SEE THE BOARD";
          $("ov-loan-terms").textContent = credit
            ? "$" + fmt(MARKER_AMOUNT * (1 + MARKER_INTEREST)) + " to clear · go broke holding " + max + " unpaid and you're out"
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
    if (TT.on && TT.points) {
      var fp = TT.frame() || {};
      host.querySelector(".k").textContent = "WEEK " + (fp.week_no || 1) + " · " +
        (fp.day_no === 7 ? "FINAL DAY" : "DAY " + (fp.day_no || 1));
      host.querySelector(".v").textContent = "$10,000";
      host.querySelector(".g").textContent = "A GAME · POINTS";
      return;
    }
    if (TT.on) {
      var f = TT.frame() || {}, owed = Shell.getDebt();
      host.querySelector(".k").textContent = "WEEK " + (f.week_no || 1) + " · " +
        (f.day_no === 7 ? "FINAL DAY" : "DAY " + (f.day_no || 1));
      host.querySelector(".v").textContent = "$" + fmt(bank);
      host.querySelector(".g").textContent = (owed > 0 ? "OWES $" + fmt(owed) + " · " : "") +
        "UNPAID MARKERS " + TT.unpaid() + "/" + TT.max();
      return;
    }
    host.querySelector(".k").textContent = "CAMPAIGN";
""")

# ---- markers at the felt on every raise you are short for ----
# Mini UTH only lent on the river; preflop and flop just greyed the raise out.
sub("""        a.appendChild(actionBtn("CHECK", DIM, check));
        a.appendChild(actionBtn(MU.PLAY[phase] + "X  " + fmt(playCost()), GOLD, raise,
          bankroll < playCost()));""",
"""        a.appendChild(actionBtn("CHECK", DIM, check));
        if (Shell.mode === "campaign" && bankroll < playCost() && Shell.canTakeMarker()) {
          a.appendChild(actionBtn("MARKER +5,000", BRASS, function () {
            Shell.takeMarker();
            bankroll = Shell.getBankroll();
            setMessage("Marker taken — play on");
            renderAll();
          }));
        } else {
          a.appendChild(actionBtn(MU.PLAY[phase] + "X  " + fmt(playCost()), GOLD, raise,
            bankroll < playCost()));
        }""")
# Hold'em lent only when short of every raise; now when short of the big one.
sub('if (Shell.mode === "campaign" && !playChoices().length && Shell.canTakeMarker()) {',
    'if (Shell.mode === "campaign" && playChoices().length < PLAY_OPTIONS[street()].length && Shell.canTakeMarker()) {')

# ---- KIRBY: three-card guts with a dummy hand, the sixth poker table ----
sub("</style>\n\n<!-- ================= MENU ================= -->",
    (here / "kirby.css").read_text() + "</style>\n\n<!-- ================= MENU ================= -->")
sub('<!-- ================= SETTINGS ================= -->',
    (here / "kirby.html").read_text() + '<!-- ================= SETTINGS ================= -->')
sub("""        <span class="pk-txt"><span class="nm">I LUV SUITS</span><span class="l1">SEVEN CARDS · LONGEST FLUSH</span><span class="l2">1X · 2X · 3X</span></span>
        <span class="pk-play">PLAY</span>
      </button>""",
"""        <span class="pk-txt"><span class="nm">I LUV SUITS</span><span class="l1">SEVEN CARDS · LONGEST FLUSH</span><span class="l2">1X · 2X · 3X</span></span>
        <span class="pk-play">PLAY</span>
      </button>
      <button class="pk-game" id="pk-kirby" style="--c:#FF9A3D" aria-label="Kirby — play">
        <img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjM0IDIgMTE2IDExOCI+CjxkZWZzPjxsaW5lYXJHcmFkaWVudCBpZD0iZyIgeDE9IjAiIHkxPSIwIiB4Mj0iMCIgeTI9IjEiPjxzdG9wIG9mZnNldD0iMCIgc3RvcC1jb2xvcj0iI0ZGQjI1RSIvPjxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iI0UwNkExMiIvPjwvbGluZWFyR3JhZGllbnQ+PC9kZWZzPgo8ZyB0cmFuc2Zvcm09InRyYW5zbGF0ZSg5MiA2NikiPgo8ZyB0cmFuc2Zvcm09InJvdGF0ZSgtMjApIHRyYW5zbGF0ZSgtMjIgLTQwKSI+PHJlY3Qgd2lkdGg9IjQ0IiBoZWlnaHQ9IjY0IiByeD0iNiIgZmlsbD0iI0ZBRjZFQyIgc3Ryb2tlPSIjMDAwIiBzdHJva2Utb3BhY2l0eT0iLjM1Ii8+PHRleHQgeD0iNyIgeT0iMTciIGZvbnQtZmFtaWx5PSJHZW9yZ2lhLHNlcmlmIiBmb250LXNpemU9IjE0IiBmb250LXdlaWdodD0iNzAwIiBmaWxsPSIjMWExYTFhIj5LPC90ZXh0Pjx0ZXh0IHg9IjIyIiB5PSI0NCIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZm9udC1zaXplPSIyMiIgZmlsbD0iIzFhMWExYSI+JiM5ODI0OzwvdGV4dD48L2c+CjxnIHRyYW5zZm9ybT0icm90YXRlKDIwKSB0cmFuc2xhdGUoLTIyIC00MCkiPjxyZWN0IHdpZHRoPSI0NCIgaGVpZ2h0PSI2NCIgcng9IjYiIGZpbGw9IiNGQUY2RUMiIHN0cm9rZT0iIzAwMCIgc3Ryb2tlLW9wYWNpdHk9Ii4zNSIvPjx0ZXh0IHg9IjciIHk9IjE3IiBmb250LWZhbWlseT0iR2VvcmdpYSxzZXJpZiIgZm9udC1zaXplPSIxNCIgZm9udC13ZWlnaHQ9IjcwMCIgZmlsbD0iI0M4MTAyRSI+SzwvdGV4dD48dGV4dCB4PSIyMiIgeT0iNDQiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGZvbnQtc2l6ZT0iMjIiIGZpbGw9IiNDODEwMkUiPiYjOTgyOTs8L3RleHQ+PC9nPgo8ZyB0cmFuc2Zvcm09InRyYW5zbGF0ZSgtMjIgLTQ0KSI+PHJlY3Qgd2lkdGg9IjQ0IiBoZWlnaHQ9IjY0IiByeD0iNiIgZmlsbD0idXJsKCNnKSIgc3Ryb2tlPSIjMDAwIiBzdHJva2Utb3BhY2l0eT0iLjQiLz48cmVjdCB4PSI1IiB5PSI1IiB3aWR0aD0iMzQiIGhlaWdodD0iNTQiIHJ4PSI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNGRkYzRTAiIHN0cm9rZS1vcGFjaXR5PSIuNyIgc3Ryb2tlLXdpZHRoPSIxLjUiLz48dGV4dCB4PSIyMiIgeT0iNDIiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGZvbnQtZmFtaWx5PSJHZW9yZ2lhLHNlcmlmIiBmb250LXNpemU9IjI2IiBmb250LXdlaWdodD0iNzAwIiBmaWxsPSIjRkZGM0UwIj4/PC90ZXh0PjwvZz4KPC9nPjwvc3ZnPg==" alt="">
        <span class="pk-txt"><span class="nm">KIRBY</span><span class="l1">THREE CARDS · HOLD OR DROP</span><span class="l2">BEAT KIRBY · TAKE THE POT</span></span>
        <span class="pk-play">PLAY</span>
      </button>""")
sub('<div class="pk-head"><b>POKER</b><span id="pk-sign">FIVE TABLES</span></div>',
    '<div class="pk-head"><b>POKER</b><span id="pk-sign">SIX TABLES</span></div>')
sub("  paintMenu();\n  /* ---------- sound",
    (here / "kirby.js").read_text() + "  paintMenu();\n  /* ---------- sound")

sub('{ nm: "POKER",     sub: "5",', '{ nm: "POKER",     sub: "6",')

sub('{ nm: "POKER",     sub: "6",', '{ nm: "POKER",     sub: "7",')   # the Play Testing panel lists Pai Gow too
# ---- PAI GOW POKER: seven cards set into two hands. Campaign and Play Testing only; the tournament hides it ----
sub("</style>\n\n<!-- ================= MENU ================= -->",
    (here / "paigow.css").read_text() + "</style>\n\n<!-- ================= MENU ================= -->")
sub('<!-- ================= SETTINGS ================= -->',
    (here / "paigow.html").read_text() + '<!-- ================= SETTINGS ================= -->')
sub("""        <span class="pk-txt"><span class="nm">KIRBY</span><span class="l1">THREE CARDS · HOLD OR DROP</span><span class="l2">BEAT KIRBY · TAKE THE POT</span></span>
        <span class="pk-play">PLAY</span>
      </button>""",
"""        <span class="pk-txt"><span class="nm">KIRBY</span><span class="l1">THREE CARDS · HOLD OR DROP</span><span class="l2">BEAT KIRBY · TAKE THE POT</span></span>
        <span class="pk-play">PLAY</span>
      </button>
      <button class="pk-game" id="pk-paigow" style="--c:#E24B5B" aria-label="Pai Gow Poker — play">
        <img src="data:image/svg+xml;base64,""" + __import__("base64").b64encode((here / "paigow-icon.svg").read_bytes()).decode() + """" alt="">
        <span class="pk-txt"><span class="nm">PAI GOW</span><span class="l1">FACE UP · SET TWO HANDS</span><span class="l2">NO COMMISSION · JOKER IN THE DECK</span></span>
        <span class="pk-play">PLAY</span>
      </button>""")
sub("  paintMenu();\n  /* ---------- sound",
    (here / "paigow.js").read_text() + "  paintMenu();\n  /* ---------- sound")

# the slot's lettering
sub("</head><body>", '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Teko:wght@500;600;700&display=swap">\n</head><body>')

# ---- CHAIN REACTION: the disc golf slot, on the Play Testing panel only ----
sub("</style>\n\n<!-- ================= MENU ================= -->",
    (here / "chain.css").read_text() + "</style>\n\n<!-- ================= MENU ================= -->")
sub('<!-- ================= SETTINGS ================= -->',
    (here / "chain.html").read_text() + '<!-- ================= SETTINGS ================= -->')
sub("""          <span class="l2">CASH OUT WHEN YOU LIKE</span>
        </span>
        <span class="play">PLAY</span>
      </button>
    </div>""",
"""          <span class="l2">CASH OUT WHEN YOU LIKE</span>
        </span>
        <span class="play">PLAY</span>
      </button>
      <button class="cm-plate" id="om-chain" style="--c:#6BF08A; display:none" aria-label="Chain Reaction slot, play testing only">
        <span class="art"><svg viewBox="0 0 102 78" xmlns="http://www.w3.org/2000/svg"><defs><radialGradient id="omcbg" cx=".5" cy=".4" r=".8"><stop offset="0" stop-color="#16402A"/><stop offset="1" stop-color="#04100A"/></radialGradient><radialGradient id="omcg" cx=".4" cy=".3" r=".75"><stop offset="0" stop-color="#FFF7CF"/><stop offset=".45" stop-color="#FFD255"/><stop offset="1" stop-color="#A86F0C"/></radialGradient><linearGradient id="omcf" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFE7A1"/><stop offset=".5" stop-color="#8A6A1F"/><stop offset="1" stop-color="#F4D27C"/></linearGradient></defs><rect width="102" height="78" fill="url(#omcbg)"/><rect x="7" y="13" width="88" height="52" rx="8" fill="#06100B" stroke="url(#omcf)" stroke-width="2.5"/><rect x="12" y="18" width="24" height="42" rx="4" fill="#123222"/><rect x="39" y="18" width="24" height="42" rx="4" fill="#123222"/><rect x="66" y="18" width="24" height="42" rx="4" fill="#123222"/><ellipse cx="24" cy="40" rx="10" ry="5.5" fill="#E8457F"/><ellipse cx="24" cy="38" rx="10" ry="5.5" fill="#FF7FAF"/><circle cx="51" cy="39" r="10" fill="url(#omcg)" stroke="#6E4A06" stroke-width="1"/><text x="51" y="42.5" text-anchor="middle" font-family="Arial Black, Arial" font-weight="900" font-size="8" fill="#5A3A00">ACE</text><path d="M78 27 l7 10 h-4 l6 9 h-18 l6 -9 h-4z" fill="#2E9E57"/><rect x="76.5" y="46" width="3" height="5" fill="#7A5232"/><path d="M10 39 h82" stroke="#FFD866" stroke-width="1.5" opacity=".8"/></svg></span>
        <span class="txt">
          <span class="nm">CHAIN REACTION</span>
          <span class="l1">DISC GOLF · HOLD &amp; SPIN · 25 LINES</span>
          <span class="l2">PLAY TESTING ONLY · FILL 15 FOR THE ACE</span>
        </span>
        <span class="play">PLAY</span>
      </button>
    </div>""")
sub("  paintMenu();\n  /* ---------- sound",
    (here / "chain.js").read_text() + "  paintMenu();\n  /* ---------- sound")

# ---- HIGH OR LOW: nine piles, call each card higher or lower. Campaign and Play Testing; the tournament hides it ----
sub("</style>\n\n<!-- ================= MENU ================= -->",
    (here / "highlow.css").read_text() + "</style>\n\n<!-- ================= MENU ================= -->")
sub('<!-- ================= SETTINGS ================= -->',
    (here / "highlow.html").read_text() + '<!-- ================= SETTINGS ================= -->')
_HLART = ('<svg viewBox="0 0 102 78" xmlns="http://www.w3.org/2000/svg"><defs><radialGradient id="omhlbg" cx=".5" cy=".4" r=".8"><stop offset="0" stop-color="#0E2A40"/><stop offset="1" stop-color="#030A12"/></radialGradient></defs>'
          '<rect width="102" height="78" fill="url(#omhlbg)"/>'
          '<g transform="translate(14 14) rotate(-10)"><rect width="30" height="44" rx="4" fill="#FAF6EC" stroke="#000" stroke-opacity=".4"/><text x="5" y="13" font-family="Georgia,serif" font-weight="700" font-size="11" fill="#C8102E">7</text><text x="15" y="31" text-anchor="middle" font-size="15" fill="#C8102E">&#9829;</text></g>'
          '<g transform="translate(58 12) rotate(9)"><rect width="30" height="44" rx="4" fill="#0A0507" stroke="#35C8FF" stroke-width="1.5"/><text x="15" y="29" text-anchor="middle" font-family="Arial Black,Arial" font-weight="900" font-size="16" fill="#35C8FF">?</text></g>'
          '<path d="M51 26 l7 9 h-4.5 v9 h-5 v-9 H44Z" fill="#57E06A"/><path d="M51 70 l7 -9 h-4.5 v-9 h-5 v9 H44Z" fill="#FF6B6B"/></svg>')
sub("""          <span class="l2">CASH OUT WHEN YOU LIKE</span>
        </span>
        <span class="play">PLAY</span>
      </button>
      <button class="cm-plate" id="om-chain\"""", """          <span class="l2">CASH OUT WHEN YOU LIKE</span>
        </span>
        <span class="play">PLAY</span>
      </button>
      <button class="cm-plate" id="om-highlow" style="--c:#35C8FF" aria-label="High or Low — play">
        <span class="art">""" + _HLART + """</span>
        <span class="txt">
          <span class="nm">HIGH OR LOW</span>
          <span class="l1">NINE PILES · CALL EVERY CARD</span>
          <span class="l2">PAID FROM 12 RIGHT · TIE BET ON ANY FLIP</span>
        </span>
        <span class="play">PLAY</span>
      </button>
      <button class="cm-plate" id="om-chain\"""")
sub("  paintMenu();\n  /* ---------- sound",
    (here / "highlow.js").read_text() + "  paintMenu();\n  /* ---------- sound")

# ---- the OTHER GAMES bar in the lobby gets a picture like the rest: half money wheel, half mines ----
sub("""    { key: "other", c: "#F2EEE6", name: "OTHER GAMES", tables: 5,
      meta: "KENO · MONEY WHEEL · SIC BO · PLINKO · MINES", plain: true,
      art: "data:image/svg+xml;utf8," + encodeURIComponent(KENO_ART),""",
"""    { key: "other", c: "#F2EEE6", name: "OTHER GAMES", tables: 6,
      meta: "KENO · WHEEL · MINES · HIGH OR LOW & MORE",
      art: "data:image/webp;base64,""" + __import__("base64").b64encode((here / "art" / "other_games.webp").read_bytes()).decode() + """",""")
sub("  .mm-other { margin-top: 12px; height: 64px; }",
    "  .mm-other { margin-top: 12px; height: 64px; }\n  .st-tab[aria-label=\"OTHER GAMES\"] img { border-radius: 9px; object-fit: cover; height: 62px; width: 104px; margin-left: 4px;\n"
    "    -webkit-mask-image: radial-gradient(120% 120% at 50% 50%, #000 55%, transparent 100%); mask-image: radial-gradient(120% 120% at 50% 50%, #000 55%, transparent 100%); }")

sub('    $("st-hero").src = r.art;', '    $("st-hero").src = r.art;\n    $("st-hero").classList.toggle("oth", r.key === "other");   // the picture with square edges: faded in')
sub("  .mm-other { margin-top: 12px; height: 64px; }\n", "  .mm-other { margin-top: 12px; height: 64px; }\n  #st-hero.oth { border-radius: 14px; -webkit-mask-image: radial-gradient(100% 100% at 50% 50%, #000 50%, transparent 100%); mask-image: radial-gradient(100% 100% at 50% 50%, #000 50%, transparent 100%); }\n")

# ---- Super Buster: when the side bet wins, the main hand pushes instead of busting ----
sub("""        else if (dealerBj) { returned = 0; lost = true; }
        else if (isBust(h.cards)) { returned = 0; lost = true; }
        else if (push22) returned = h.stake;""",
"""        else if (dealerBj) { returned = 0; lost = true; }
        else if (superBusterWin && superBusterStake > 0) returned = h.stake;   // a winning Super Buster saves the hand
        else if (isBust(h.cards)) { returned = 0; lost = true; }
        else if (push22) returned = h.stake;""")

sub("""        Anything but an ace or a ten busts the hand and loses the bet.
      </div>""", """        Anything but an ace or a ten busts the hand and loses the bet.
        Win it and your main bet pushes.
      </div>""")

# ---- Mines: after a mine, the same bet stays on the spot for another go, as it does after a cash-out ----
sub("""        msgText = "BOOM — the house takes " + fmt(stake);
        stake = 0; phase = "result";""",
"""        msgText = "BOOM — the house takes " + fmt(stake);
        if (stake <= bankroll) bet = stake;
        stake = 0; phase = "result";""")

# ---- Craps: the roll button always answers; when the table won't take the throw, a pop-up says why ----
sub("""      a.appendChild(actionBtn(comeOut() ? "COME OUT" : "ROLL", RED, throwDice,
        comeOut() && bets.pass <= 0));""",
"""      var rb = actionBtn(comeOut() ? "COME OUT" : "ROLL", RED, throwDice);
      if (comeOut() && bets.pass <= 0) rb.style.opacity = ".5";   // looks off, but a tap still says why
      a.appendChild(rb);""")
sub("""    function setMessage(text) { message = text; $("cr-message").textContent = text; }""",
"""    function setMessage(text) { message = text; $("cr-message").textContent = text; }
    var popTimer = 0;
    /** A pop-up over the felt, for a throw the table won't take. */
    function pop(title, text) {
      var p = $("cr-pop");
      if (!p) {
        p = document.createElement("div"); p.id = "cr-pop";
        p.style.cssText = "position:fixed;left:50%;top:38%;transform:translate(-50%,-50%);z-index:9999;width:min(320px,84vw);" +
          "padding:16px 18px;border-radius:14px;background:rgba(10,8,18,.96);border:1.5px solid #FF3B3B;" +
          "box-shadow:0 12px 30px rgba(0,0,0,.6);text-align:center;pointer-events:none;transition:opacity .25s;opacity:0;" +
          "font-family:system-ui,sans-serif";
        document.body.appendChild(p);
      }
      p.innerHTML = '<div style="font-weight:800;letter-spacing:.1em;color:#FF5A5A;font-size:14px"></div>' +
        '<div style="margin-top:6px;color:#F5F1E8;font-size:14px;line-height:1.4"></div>';
      p.children[0].textContent = title; p.children[1].textContent = text;
      p.style.opacity = "1";
      clearTimeout(popTimer);
      popTimer = setTimeout(function () { p.style.opacity = "0"; }, 2600);
    }""")
sub("""        setMessage("The line must be covered to come out"); renderHead();""",
"""        setMessage("The line must be covered to come out"); renderHead();
        pop("NO PASS LINE BET", "Put chips on the PASS LINE to come out. Then you can roll.");""")

# ---- Double Ball: outside bets ride half on each ball, so one ball in counts ----
sub("      if (isOutside(bet)) return hits === 2 ? stake * (bothBalls(bet) + 1) : 0;",
    "      // half the stake rides each ball at the outside bet's own price: red with one\n"
    "      // ball in is a push, both in pays even money; a dozen with one in wins half\n"
    "      if (isOutside(bet)) return Math.floor(hits * (stake / 2) * (bet.pays + 1));")

# ---- Roulette: five chip places on the felt; a room that takes the 25K chip racks its top five ----
sub("""        b.addEventListener("click", function () {
          if (chipsFor(bankroll, Shell.limits()).indexOf(v) < 0) return;
          selectedChip = v; renderRing();
        });""",
"""        b.addEventListener("click", function () {
          var cv = +b.getAttribute("data-chip");
          if (chipsFor(bankroll, Shell.limits()).indexOf(cv) < 0) return;
          selectedChip = cv; renderRing();
        });""")
sub("""      renderRing = function () {
        var rackv = chipsFor(bankroll, Shell.limits());
        [].forEach.call(rack.children, function (b) {
          var v = +b.getAttribute("data-chip"), out = rackv.indexOf(v) < 0;""",
"""      renderRing = function () {
        var rackv = chipsFor(bankroll, Shell.limits());
        // six chips won't fit five places: the room's five biggest take them
        var show = rackv.length >= 5 ? rackv.slice(-5) : vals;
        [].forEach.call(rack.children, function (b, i) {
          if (+b.getAttribute("data-chip") !== show[i]) { b.setAttribute("data-chip", show[i]); b.innerHTML = chipFace(show[i]); }
        });
        if (show.indexOf(selectedChip) < 0 && rackv.indexOf(selectedChip) < 0) selectedChip = rackv[rackv.length - 1];
        [].forEach.call(rack.children, function (b) {
          var v = +b.getAttribute("data-chip"), out = rackv.indexOf(v) < 0;""")

# ---- Whale Room: no table max, no side-bet max, no Bad Beat max ----
sub('    { key: "WHALE",      name: "Whale Room",   buyIn: 1000000, min: 10000, max: 250000, sideMax: 50000, badBeatMax: 125 }',
    '    { key: "WHALE",      name: "Whale Room",   buyIn: 1000000, min: 10000, max: Infinity, sideMax: Infinity, badBeatMax: Infinity }')
sub('  function roomSign(r) { return "$" + roomShort(r.min) + " – $" + roomShort(r.max); }',
    '  function roomSign(r) { return "$" + roomShort(r.min) + (r.max === Infinity ? " – NO LIMIT" : " – $" + roomShort(r.max)); }')
sub('  function roomSideSign(r) { return "SIDE $" + roomShort(r.sideMax); }',
    '  function roomSideSign(r) { return r.sideMax === Infinity ? "SIDES NO LIMIT" : "SIDE $" + roomShort(r.sideMax); }')
sub('      ["MAX", L.enforced ? "$" + fmt(L.max) : "ANY"]',
    '      ["MAX", L.enforced && L.max !== Infinity ? "$" + fmt(L.max) : "ANY"]')

# ---- Whale Room chips: 1K up to 1M, no 25s, 100s or 500s ----
sub("""        "v": 25000,
        "label": "25K",
        "body": "#d9dd03",
        "a": "#000000",
        "b": "#001df5",
        "face": "#3e2e0c",
        "ink": "#ffffff",
        "spotLight": 0.48
      }
    ]""", """        "v": 25000,
        "label": "25K",
        "body": "#bbbe19",
        "a": "#000000",
        "b": "#001df5",
        "face": "#3e2e0c",
        "ink": "#ffffff",
        "spotLight": 0.28
      },
      {
        "v": 100000,
        "label": "100K",
        "body": "#ff006f",
        "a": "#00d679",
        "b": "#0296f2",
        "face": "#2a0008",
        "ink": "#ffffff",
        "spotLight": 0
      },
      {
        "v": 1000000,
        "label": "1M",
        "body": "#2e8e88",
        "a": "#f58f00",
        "b": "#bd0000",
        "face": "#0e2e2c",
        "ink": "#ffd24d",
        "spotLight": 0.34
      },
      {
        "v": 100000000,
        "label": "100M",
        "body": "#0a3d91",
        "a": "#ffc400",
        "b": "#ffffff",
        "face": "#0a3d91",
        "ink": "#ffd24d",
        "spotLight": 0.3
      },
      {
        "v": 1000000000,
        "label": "1B",
        "body": "#c9c30d",
        "a": "#525252",
        "b": "#d11515",
        "face": "#000000",
        "faceOwn": true,
        "ink": "#ffd24d",
        "spotLight": 0.3
      }
    ]""")
# a chip may have its own center color
sub("    var face = S.faceOwn ? c.face : body;", "    var face = (c.faceOwn != null ? c.faceOwn : S.faceOwn) ? c.face : body;")
sub("""    var all = [25, 100, 500, 1000, 5000, 25000];
    var ceiling = limits && limits.enforced ? limits.max : (bankroll >= 100000 ? 25000 : 5000);""",
"""    // the Whale Room has no ceiling: it racks the five big chips, 500 to 100K
    if (limits && limits.enforced && limits.max === Infinity)
      // a billion or more on the stack brings out the 100M chip (the 1K steps off to make room)
      // ten billion and up: just the big three
      return bankroll >= 10000000000 ? [1000000, 100000000, 1000000000]
        : bankroll >= 1000000000 ? [5000, 25000, 100000, 1000000, 100000000] : [1000, 5000, 25000, 100000, 1000000];
    var all = [25, 100, 500, 1000, 5000, 25000];
    var ceiling = limits && limits.enforced ? limits.max : (bankroll >= 100000 ? 25000 : 5000);""")
sub("""    var values = [25, 100, 500, 1000, 5000, 25000];
    var chip = null;""", """    var values = [25, 100, 500, 1000, 5000, 25000, 100000, 1000000, 100000000, 1000000000];
    var chip = null;""")

# ---- Money Wheel after a spin: NEW BET clears the felt, SPIN AGAIN spins the same bet straight away ----
sub("""      else if (phase === "result") { feltBtn("Same bet", "#8A93A6", function () { nextSpin(true); }); feltBtn("Spin again", GOLD, function () {}, true); }""",
"""      else if (phase === "result") {
        var again = 0; Object.keys(lastBets).forEach(function (k) { again += lastBets[k]; });
        feltBtn("New bet", "#8A93A6", function () { nextSpin(false); });
        feltBtn("Spin again", GOLD, function () {
          nextSpin(true);
          if (phase === "betting" && totalStaked() > 0) spin();
        }, !again || again > bankroll);
      }""")

# ---- walk-ups: friends and strangers asking for money between hands (Play Testing) ----
sub("  paintMenu();\n  /* ---------- sound",
    (here / "walkups.js").read_text().replace("/*FACES*/{}", (here / "faces.json").read_text()) + "  paintMenu();\n  /* ---------- sound")

# ---- walk-ups: the tables say what they dealt, for a friend's tip ----
WU = 'if (typeof Walkups !== "undefined") try { Walkups.result(%s); } catch (e) {} '
sub("      result = outcome(d, t);", "      result = outcome(d, t); " + WU % '"dragontiger", result')
sub("    function settleBreakdown(h, placed) {", "    function settleBreakdown(h, placed) {\n      " + WU % '"baccarat", winningSpots(h)')
sub("    function settle(text, pay) {", "    function settle(text, pay) {\n      " + WU % '"roulette", pocket2 != null && text.indexOf("&") >= 0 ? [pocket, pocket2] : [pocket]')
sub("    function settle(staked) {", "    function settle(staked) {\n      " + WU % '"wheel", stop')
sub("    function settleRoll(vals) {\n      if (phase !== \"rolling\") return;", "    function settleRoll(vals) {\n      if (phase !== \"rolling\") return;\n"
    "      // a friend's tip that missed gets one more roll; the dice turn over to it\n"
    "      if (WUR(\"sicbo\", vals.slice(0, 3))) { vals = [1, 2, 3].map(function () { return 1 + Math.floor(Math.random() * 6); }); try { tray.set(vals); } catch (e) {} }\n      "
    + WU % '"sicbo", vals.slice(0, 3)')
sub("      var dealerBj = isBJ(dealer);", "      var dealerBj = isBJ(dealer); " + WU % '"blackjack", { dealer: total(dealer), trilux: !!triluxWin }')

# ---- no betting more than you can finish: keep enough back for the play bet ----
def keepBack(old, cost, need, word):
    sub(old, ("      if (bankroll < %s + %s) {\n"
              "        setMessage(\"Keep $\" + fmt(%s) + \" back for the %s bet. Bet less.\");\n"
              "        return;\n      }\n") % (cost, need, need, word) + old)
keepBack("      if (shoe.length < RESHUFFLE_AT) shoe = buildDjShoe();\n      bankroll -= ante * 2 + trips + badBeat;",
         "ante * 2 + trips + badBeat", "ante * 2", "play")
keepBack("      if (shoe.length < RESHUFFLE_AT) shoe = buildShoe(DECKS);\n      bankroll -= ante * 2 + trips + badBeat;",
         "ante * 2 + trips + badBeat", "ante", "play")
keepBack("      // One deck, shuffled fresh every hand.\n      shoe = buildShoe(1);\n      bankroll -= ante * 2 + flushPlus + bonus;",
         "ante * 2 + flushPlus + bonus", "ante", "play")
keepBack("      // Fourteen cards off a single deck, shuffled fresh every hand.\n      shoe = buildShoe(1);\n      bankroll -= ante + flushRush + superRush;",
         "ante + flushRush + superRush", "ante", "raise")

# ---- walk-ups: the poker tables and keno say what they dealt too ----
sub("      settlement = s;\n      // A tie can still use a card",
    "      settlement = s; " + WU % '"holdem", { trips: s.tripsPay > 0, badbeat: !!s.badBeatWin }' +
    "\n      // A tie can still use a card")
sub("                        tripsStake, badBeatStake, folded);\n      settlement = s;",
    "                        tripsStake, badBeatStake, folded);\n      settlement = s; " +
    WU % '"djwild", { trips: !!s.tripsWin, badbeat: !!s.badBeatWin }')
sub("flushPlusStake, bonusStake, folded);\n      settlement = s;",
    "flushPlusStake, bonusStake, folded);\n      settlement = s; " + WU % '"miniuth", { flushplus: s.flushPlusWin >= 0, bonus: s.bonusWin >= 0 }')

# ---- walk-ups: keno says what it drew, for a friend's number ----
sub("    function settle() {\n      var h = hits(), p = pays(picks.length, h)",
    "    function settle() {\n      " + WU % '"keno", drawn.slice()' + "\n      var h = hits(), p = pays(picks.length, h)")

# ---- walk-ups: a friend's tip gets a boost - if the outcome misses it, it is dealt once more ----
sub("  // ---------- wallet & shell ----------", """  /** True when a backed tip at [game] misses on [what], so the table should deal/spin again (once). */
  function WUR(game, what) {
    try { return typeof Walkups !== "undefined" && Walkups.reroll(game, what); } catch (e) { return false; }
  }
  /** Shuffle what's left of a shoe in place: a fresh, fair run of cards. */
  function WUshuffle(a) {
    for (var i = a.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)), x = a[i]; a[i] = a[j]; a[j] = x; }
  }
  /** The next [n] cards off a shoe, in the order they'd be drawn (pop takes from the end). */
  function WUpeek(shoe, n) { var out = []; for (var i = 0; i < n; i++) out.push(shoe[shoe.length - 1 - i]); return out; }
  // ---------- wallet & shell ----------""")
# roulette: classic and lightning, then double ball
sub("var result = spinWheel();", "var result = spinWheel(); if (WUR(\"roulette\", [result])) result = spinWheel();", 2)
sub("        var first = spinWheel(), second = spinWheel();",
    "        var first = spinWheel(), second = spinWheel();\n"
    "        if (WUR(\"roulette\", [first, second])) { first = spinWheel(); second = spinWheel(); }")
# keno, money wheel
sub("      var balls = shuffled().slice(0, DRAWN);",
    "      var balls = shuffled().slice(0, DRAWN);\n      if (WUR(\"keno\", balls)) balls = shuffled().slice(0, DRAWN);")
sub("      var i = Math.floor(Math.random() * N), target = aim(turn, i, STEP, TURNS, within);",
    "      var i = Math.floor(Math.random() * N);\n      if (WUR(\"wheel\", STOPS[i])) i = Math.floor(Math.random() * N);\n"
    "      var target = aim(turn, i, STEP, TURNS, within);")
# baccarat, dragon tiger: deal the hand again
sub("      hand = playHand(shoe);", "      hand = playHand(shoe);\n      if (WUR(\"baccarat\", winningSpots(hand))) hand = playHand(shoe);")
sub("      var d = shoe.pop(), t = shoe.pop();",
    "      var d = shoe.pop(), t = shoe.pop();\n      if (WUR(\"dragontiger\", outcome(d, t))) { d = shoe.pop(); t = shoe.pop(); }")
# the poker tables: read the hand off the top of the shoe; a miss shuffles the shoe once
sub("      // Two rounds of hole cards, then the board goes down face down.",
    "      (function () {\n"
    "        var c = WUpeek(shoe, 9), x = UTH.settle([c[0], c[2]], [c[1], c[3]], c.slice(4, 9), 1, 1, 0, 1, false, 1);\n"
    "        if (WUR(\"holdem\", { trips: x.tripsPay > 0, badbeat: !!x.badBeatWin })) WUshuffle(shoe);\n"
    "      })();\n"
    "      // Two rounds of hole cards, then the board goes down face down.")
sub("      // Five each, alternating, the way they come off the deck.",
    "      (function () {\n"
    "        var c = WUpeek(shoe, 10), p = [c[0], c[2], c[4], c[6], c[8]], q = [c[1], c[3], c[5], c[7], c[9]];\n"
    "        var x = DJ.settle(p, q, 1, 1, 0, 1, 1, false);\n"
    "        if (WUR(\"djwild\", { trips: !!x.tripsWin, badbeat: !!x.badBeatWin })) WUshuffle(shoe);\n"
    "      })();\n"
    "      // Five each, alternating, the way they come off the deck.")
sub("      t(220, function () { player = draw(); renderCards(); });",
    "      (function () {\n"
    "        var c = WUpeek(shoe, 5), x = MU.settle(c[0], c[1], c.slice(2, 5), 1, 1, 0, 1, 1, false);\n"
    "        if (WUR(\"miniuth\", { flushplus: x.flushPlusWin >= 0, bonus: x.bonusWin >= 0 })) WUshuffle(shoe);\n"
    "      })();\n"
    "      t(220, function () { player = draw(); renderCards(); });")
# blackjack: Trilux reads the deal; Push 22 reads the dealer's draws
sub("      t(350, function () { hands[0].cards.push(draw()); renderHands(); });",
    "      if (variant === \"blazing\") (function () {\n"
    "        var c = WUpeek(shoe, 3);\n"
    "        if (WUR(\"blackjack\", { trilux: !!triluxOf([c[0], c[2], c[1]]), dealer: 22 })) WUshuffle(shoe);\n"
    "      })();\n"
    "      t(350, function () { hands[0].cards.push(draw()); renderHands(); });")
sub("        var soleBJ = hands.length === 1 && isBJ(hands[0].cards);",
    "        var soleBJ = hands.length === 1 && isBJ(hands[0].cards);\n"
    "        if (hands.some(function (h) { return !isBust(h.cards); }) && !soleBJ) (function () {\n"
    "          var d = dealer.slice(), k = 0;\n"
    "          while (dealerHits(d) && k < 12) { d.push(shoe[shoe.length - 1 - k]); k++; }\n"
    "          if (WUR(\"blackjack\", { trilux: true, dealer: total(d) })) WUshuffle(shoe);\n"
    "        })();")

# ---- blackjack: a friend leans over and says "fuck the book" - listen and the cards lean your way ----
sub("""            setMessage("Your move");
            renderAll();""", """            setMessage("Your move");
            renderAll();
            WUadvise(hands[0].cards, dealer[0]);""")
sub("""      if (!h || h.done || h.aceSplit) return;
      h.cards.push(draw());""", """      if (!h || h.done || h.aceSplit) return;
      // took the friend's "hit it": a card that would bust you is swapped for the next one, once
      var wuc = draw();
      if (WUlisten("hit") && isBust(h.cards.concat([wuc]))) wuc = draw();
      h.cards.push(wuc);""")
sub("""    function stand() {
      if (phase !== "player") return;
      hands[active].done = true;""", """    function stand() {
      if (phase !== "player") return;
      WUlisten("stand");
      hands[active].done = true;""")
sub("""    function triluxOf(cards) {""", """    /** What the book says on a close call (hard 12-17, soft 17-19): "hit" or "stand"; "" when it isn't close. */
    function WUbook(cards, up) {
      if (!cards || cards.length !== 2 || !up) return "";
      var t = total(cards), soft = isSoft(cards), d = up.rank === "A" ? 11 : bjValue(up.rank);
      if (soft) {
        if (t < 17 || t > 19) return "";
        return t >= 19 || (t === 18 && d >= 2 && d <= 8) ? "stand" : "hit";
      }
      if (t < 12 || t > 17) return "";
      if (t === 17) return "stand";
      if (t >= 13) return d >= 2 && d <= 6 ? "stand" : "hit";
      return d >= 4 && d <= 6 ? "stand" : "hit";
    }
    /** Now and then a friend says the opposite of the book. */
    function WUadvise(cards, up) {
      var b = WUbook(cards, up);
      try { if (b && typeof Walkups !== "undefined") Walkups.advise(b === "hit" ? "stand" : "hit", function () { return results[0] ? results[0].net : 0; }); } catch (e) {}
    }
    function WUlisten(act) {
      try { return typeof Walkups !== "undefined" && Walkups.listen(act); } catch (e) { return false; }
    }
    function triluxOf(cards) {""")
# stood on his say-so: if the dealer's run would beat you, the shoe is shuffled once
sub("""          if (WUR("blackjack", { trilux: true, dealer: total(d) })) WUshuffle(shoe);""",
    """          if (WUR("blackjack", { trilux: true, dealer: total(d) })) WUshuffle(shoe);
          else {
            var pt = total(hands[0].cards), dt = total(d);
            var beat = dt <= 21 && dt > pt && !(variant === "freebet" && dt === 22);
            try { if (beat && typeof Walkups !== "undefined" && Walkups.standBoost()) WUshuffle(shoe); } catch (e) {}
          }""")

# ---- a bet bigger than one chip reads short on the felt: 85M, 6B, 17T ----
sub("""      var label = fmt(amount);
      var base = label.length <= 3 ? 17 : label.length <= 5 ? 13 : 10;""",
"""      var label = amount >= 10000 ? shortAmt(amount) : fmt(amount);
      var base = label.length <= 3 ? 17 : label.length <= 5 ? 13 : 10;""")
sub("""  function chipHtml(amount, size) {""",
"""  /** 85,000,000 -> 85M, 3,111,470,150 -> 3.11B, 17,000,000,000,000 -> 17T. */
  function shortAmt(v) {
    var u = [[1e15, "Q"], [1e12, "T"], [1e9, "B"], [1e6, "M"], [1e3, "K"]];
    for (var i = 0; i < u.length; i++) if (v >= u[i][0]) {
      var x = v / u[i][0], d = x >= 100 ? 0 : x >= 10 ? 1 : 2;
      var t = (Math.floor(x * Math.pow(10, d) + 1e-6) / Math.pow(10, d)).toFixed(d);
      if (d) t = t.replace(/0+$/, "").replace(/\\.$/, "");
      return t + u[i][1];
    }
    return fmt(v);
  }
  function chipHtml(amount, size) {""")

# ---- Mini UTH: the side bets' result pills sit right above their own pay charts ----
sub("""      var host = $("mu-results");
      host.innerHTML = "";
      if (phase !== "result") return;
      results.forEach(function (r) {
        var cls = r.net > 0 ? "w" : r.net < 0 ? "l" : "p";
        var txt = r.net > 0 ? "+" + fmt(r.net)
          : r.net < 0 ? "−" + fmt(-r.net) : "push";
        host.insertAdjacentHTML("beforeend",
          '<div class="dj-pill ' + cls + '"><span class="nm">' + r.label +
          '</span><span class="amt">' + txt + "</span></div>");
      });
    }
    function paysHtml(title, rows) {""",
"""      var host = $("mu-results");
      host.innerHTML = "";
      var purse = document.querySelector("#screen-miniuth .purse");
      if (phase !== "result") { if (window.purseWin) purseWin.clear(purse); return; }
      var net = 0; results.forEach(function (r) { net += r.net; });
      if (window.purseWin) purseWin.net(purse, net);
      results.forEach(function (r) {
        if (muSide(r)) return;   // drawn over its own pay chart instead
        host.insertAdjacentHTML("beforeend", muPill(r));
      });
    }
    function muSide(r) {
      return /^Flush Plus/.test(r.label) ? "fp" : /^3 Card/.test(r.label) ? "bonus"
        : /^Blind/.test(r.label) ? "blind" : "play";
    }
    function muPill(r) {
      var cls = r.net > 0 ? "w" : r.net < 0 ? "l" : "p";
      var txt = r.net > 0 ? "+" + fmt(r.net)
        : r.net < 0 ? "−" + fmt(-r.net) : "push";
      var nm = r.label.replace(/^(Flush Plus|3 Card Bonus|3 Card|Blind)( · )?/, "");
      return '<div class="dj-pill ' + cls + '">' + (nm ? '<span class="nm">' + nm + "</span>" : "") +
        '<span class="amt">' + txt + "</span></div>";
    }
    function muSidePills(which) {
      if (phase !== "result") return "";
      var h = results.filter(function (r) { return muSide(r) === which; }).map(muPill).join("");
      return h ? '<div class="mu-sidepill">' + h + "</div>" : "";
    }
    function paysHtml(title, rows) {""")
sub("""          '<div style="padding:0 6px 4px 0">' + paysHtml("FLUSH PLUS", odds(MU.FLUSH_PLUS)) + "</div>" +""",
    """          '<div style="padding:0 6px 4px 0">' + muSidePills("fp") + paysHtml("FLUSH PLUS", odds(MU.FLUSH_PLUS)) + "</div>" +""")
sub("""          '<div style="padding-right:6px">' + paysHtml("PLAY",""",
    """          '<div style="padding-right:6px">' + muSidePills("play") + paysHtml("PLAY",""")
sub("""            paysHtml("BLIND", odds(MU.BLIND).concat([["Other hands", "push"]])) + "</div>" +""",
    """            muSidePills("blind") + paysHtml("BLIND", odds(MU.BLIND).concat([["Other hands", "push"]])) + "</div>" +""")
sub("""          '<div style="padding:0 0 4px 6px">' + paysHtml("3 CARD BONUS", odds(MU.BONUS)) + "</div>" +""",
    """          '<div style="padding:0 0 4px 6px">' + muSidePills("bonus") + paysHtml("3 CARD BONUS", odds(MU.BONUS)) + "</div>" +""")

# ---- DJ Wild and Ultimate Texas Hold'em: every result pill sits over its own pay chart (like Mini UTH) ----
sub("""  function chipHtml(amount, size) {""",
"""  /** Which pay chart a result belongs over. */
  function pillSide(r) {
    if (r.side) return r.side;
    var l = r.label;
    return /^Blind/.test(l) ? "blind" : /^Trips/.test(l) ? "trips" : /^Bad Beat/.test(l) ? "badbeat" : "main";
  }
  /** The result pills for one chart, to print right above it. */
  function pillsOver(phase, results, which) {
    if (phase !== "result" || !results) return "";
    var h = results.filter(function (r) { return pillSide(r) === which; }).map(function (r) {
      var cls = r.net > 0 ? "w" : r.net < 0 ? "l" : "p";
      var txt = r.net > 0 ? "+" + fmt(r.net) : r.net < 0 ? "−" + fmt(-r.net) : "push";
      var nm = r.label.replace(/^(Blind|Trips|Bad Beat)( · )?/, "");
      return '<div class="dj-pill ' + cls + '">' + (nm ? '<span class="nm">' + nm + "</span>" : "") +
        '<span class="amt">' + txt + "</span></div>";
    }).join("");
    return h ? '<div class="mu-sidepill">' + h + "</div>" : "";
  }
  /** The money window: +what the hand won, cleared when the next one starts. */
  function pillsPurse(scr, phase, results) {
    if (!window.purseWin) return;
    var purse = document.querySelector("#screen-" + scr + " .purse");
    if (phase !== "result") { purseWin.clear(purse); return; }
    var net = 0; (results || []).forEach(function (r) { net += r.net; });
    purseWin.net(purse, net);
  }
  function chipHtml(amount, size) {""")
# DJ Wild
sub("""          label: s.tripsWin ? DJ.tripsLabel(s.tripsWin) : "Trips",""",
    """          side: "trips", label: s.tripsWin ? DJ.tripsLabel(s.tripsWin) : "Trips",""")
sub("""          label: s.badBeatWin ? DJ.badBeatLabel(s.badBeatWin) : "Bad Beat",""",
    """          side: "badbeat", label: s.badBeatWin ? DJ.badBeatLabel(s.badBeatWin) : "Bad Beat",""")
sub("""      var betting = phase === "betting";
      $("dj-spots").innerHTML =""",
    """      var betting = phase === "betting";
      pillsPurse("djwild", phase, results);
      $("dj-spots").innerHTML =""")
sub("""paysHtml("BAD BEAT", odds(DJ.BAD_BEAT)) + "</div>" +""", """pillsOver(phase, results, "badbeat") + paysHtml("BAD BEAT", odds(DJ.BAD_BEAT)) + "</div>" +""")
sub("""paysHtml("TRIPS", tripsOdds()) + "</div>" +""", """pillsOver(phase, results, "trips") + paysHtml("TRIPS", tripsOdds()) + "</div>" +""")
sub("""paysHtml("BLIND", odds(DJ.BLIND)) + "</div>" +""", """pillsOver(phase, results, "blind") + paysHtml("BLIND", odds(DJ.BLIND)) + "</div>" +""")
sub("""          "<div></div>" +
          '<div class="mid" style="flex-direction:column">' +
            spotHtml("dj-play", "ring", "PLAY", playStake, false, NEON) +""",
    """          '<div class="mu-mainpill">' + pillsOver(phase, results, "main") + "</div>" +
          '<div class="mid" style="flex-direction:column">' +
            spotHtml("dj-play", "ring", "PLAY", playStake, false, NEON) +""")
# Ultimate Texas Hold'em
sub("""      var host = $("uth-spots");
      host.innerHTML =""",
    """      var host = $("uth-spots");
      pillsPurse("holdem", phase, results);
      host.innerHTML =""")
sub("""paysHtml("BAD BEAT", UTH.BAD_BEAT.slice(1).map(function (r) {""", """pillsOver(phase, results, "badbeat") + paysHtml("BAD BEAT", UTH.BAD_BEAT.slice(1).map(function (r) {""")
sub("""paysHtml("TRIPS", odds(UTH.TRIPS)) + "</div>" +""", """pillsOver(phase, results, "trips") + paysHtml("TRIPS", odds(UTH.TRIPS)) + "</div>" +""")
sub("""paysHtml("PLAY BET", [""", """pillsOver(phase, results, "main") + paysHtml("PLAY BET", [""")
sub("""paysHtml("BLIND", odds(UTH.BLIND)) + "</div>" +""", """pillsOver(phase, results, "blind") + paysHtml("BLIND", odds(UTH.BLIND)) + "</div>" +""")

# ---- every chip stack carries its exact amount, so the hold-to-pour bubble can show what's on THAT spot ----
sub("""    return html;
  }

  // ---------- the floor ----------""",
"""    return html + '<i class="amt-raw" data-amt="' + amount + '" hidden></i>';
  }

  // ---------- the floor ----------""")

# ---- the floor plate on every game menu: change rooms without going back to the lobby ----
import re as _re
_PLATE = '<button class="lb-plate room gm-floor" type="button" aria-label="Change floor" hidden><span class="rungs"></span><span class="nm"></span><span class="lim"></span><span class="arrow">▸</span></button>'
h, _np = _re.subn(r'(<div class="(?:cm|bjm)-head">.*?</div>)', lambda m: m.group(1) + "\n    " + _PLATE, h, flags=_re.S)
assert _np == 5, _np
sub('<div class="pk-head"><b>POKER</b><span id="pk-sign">SIX TABLES</span></div>',
    '<div class="pk-head"><b>POKER</b><span id="pk-sign">SIX TABLES</span></div>\n    ' + _PLATE)
sub("""            self.enter(room);
            $("ov-floor").classList.remove("on");
            self.showLobby();""",
    """            self.enter(room);
            $("ov-floor").classList.remove("on");
            // picked from a game menu: stay on it, with the new room's limits
            if (self._floorFrom) { var back = self._floorFrom; self._floorFrom = null; self.renderRoomRow(); paintRun("lb-campaign-line"); self.show(back); }
            else self.showLobby();""")
sub("""      window.scrollTo(0, 0);
    },
    showMenu: function () { TT.on = false;""",
    """      paintFloors();
      window.scrollTo(0, 0);
    },
    showMenu: function () { TT.on = false;""")
sub("""  $("lb-roomplate").addEventListener("click", function () { Shell.showFloor(); });""",
    """  $("lb-roomplate").addEventListener("click", function () { Shell._floorFrom = null; Shell.showFloor(); });
  /** The same room plate the lobby wears, on each game menu: what room you're in and its limits; tap to change. */
  function paintFloors() {
    var on = Shell.mode === "campaign", plates = document.querySelectorAll(".gm-floor");
    if (!plates.length) return;
    var r = on ? Shell.getRoom() : null, at = on ? Shell.roomIndex() : 0, rungs = "";
    for (var k = 0; on && k < FLOOR_TINTS.length; k++) {
      rungs += '<b class="' + (k <= at ? "" : "off") + '" style="height:' + (k <= at ? 4 + k * 2.2 : 3) + 'px"></b>';
    }
    plates.forEach(function (p) {
      p.hidden = !on;
      if (!on) return;
      p.style.setProperty("--rc", FLOOR_TINTS[at]);
      p.querySelector(".rungs").innerHTML = rungs;
      p.querySelector(".nm").textContent = r.name.toUpperCase();
      p.querySelector(".lim").textContent = roomSign(r) + " · " + roomSideSign(r);
    });
  }
  document.querySelectorAll(".gm-floor").forEach(function (p) {
    p.addEventListener("click", function () {
      var scr = document.querySelector(".screen.on");
      Shell._floorFrom = scr ? scr.id.replace("screen-", "") : null;
      Shell.showFloor();
    });
  });""")

# ---- campaign on the front page folds up like Play Testing ----
sub('<div class="mm-lane campaign" style="--c:#FFD24D">',
    '<div class="mm-lane campaign" id="mm-campaign" style="--c:#FFD24D">')
sub('<div class="mm-hdr"><span class="nm">CAMPAIGN</span></div>',
    '<div class="mm-hdr" id="mm-campaign-hdr" role="button" tabindex="0" aria-expanded="false"><span class="nm">CAMPAIGN</span>'
    '<span class="tag" id="mm-camp-tag"></span><span class="chev" aria-hidden="true">▸</span></div>')
sub("""    var lane = $("mm-testing"), hdr = $("mm-testing-hdr");""",
    """    (function () {
      var cl = $("mm-campaign"), ch = $("mm-campaign-hdr"), big = $("mm-big"), tag = $("mm-camp-tag");
      function sync() { tag.textContent = big.textContent; }
      function flipC() {
        var open = cl.classList.toggle("open");
        ch.setAttribute("aria-expanded", open ? "true" : "false");
        if (open) setTimeout(function () { cl.scrollIntoView({ behavior: "smooth", block: "nearest" }); }, 30);
      }
      ch.addEventListener("click", flipC);
      ch.addEventListener("keydown", function (e) { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); flipC(); } });
      try { new MutationObserver(sync).observe(big, { childList: true, characterData: true, subtree: true }); } catch (e) {}
      sync();
    })();
    var lane = $("mm-testing"), hdr = $("mm-testing-hdr");""")

# ---- lightning roulette: a chip on a struck number slides left so the multiplier pill beside it can be read ----
sub("""        chip.style.left = PX(cen.x); chip.style.top = PY(cen.y);
        chip.innerHTML = chipHtml(bets[id], 62);
        armDrag(chip, id);""",
    """        var struck = id.indexOf("s-") === 0 && strikes && strikes[id.slice(2)] != null;
        chip.style.left = PX(cen.x - (struck ? 52 : 0)); chip.style.top = PY(cen.y);
        chip.innerHTML = chipHtml(bets[id], 62);
        armDrag(chip, id);""")

# ---- menu music starts everyone at 2% (once), and the dial moves a point at a time ----
sub('music: Math.min(1, Math.max(0, store.get("ftb.music", 0.3)))',
    'music: (function () { if (!store.get("ftb.music2", 0)) { store.set("ftb.music2", 1); store.set("ftb.music", 0.02); } '
    'return Math.min(1, Math.max(0, store.get("ftb.music", 0.02))); })()')
sub('id="sf-music" min="0" max="100" step="5" value="30"', 'id="sf-music" min="0" max="100" step="1" value="2"')

# ---- points week: going broke doesn't drop you a floor; the free rebuy puts you right back at $10,000 ----
sub("""    demoteIfBroke: function () {
      var i = this.roomIndex(), b = this.getBankroll();""", """    demoteIfBroke: function () {
      var i = this.roomIndex(), b = this.getBankroll();
      // a tournament points week: a busted game is about to be rebought to $10,000, and the rebuy picks its floor
      if (typeof TT !== "undefined" && TT.on && TT.points && b < 25) return;""")
# ---- points week: each game has its own floor, and sitting down puts you on the highest one that game's wallet can buy into ----
sub("""    demoteIfBroke: function () {""", """    autoFloor: function () {
      if (typeof TT === "undefined" || !TT.on || !TT.points) return;
      var b = this.getBankroll(), i = ROOMS.length - 1;
      while (i > 0 && b < ROOMS[i].buyIn) i--;
      store.setStr(wkey("room"), ROOMS[i].key);
    },
    demoteIfBroke: function () {""")

# ---- blackjack (Free Bet and Blazing 777): drag a stack of chips from one spot to another, or off to the rack ----
sub("""    function on(root, sel, fn) {
      var el = root.querySelector(sel);
      if (el) el.addEventListener("click", fn);
    }""", """    var bjNoClick = false;   // a drag just ended: the click it leaves behind isn't a bet
    function on(root, sel, fn) {
      var el = root.querySelector(sel);
      if (el) el.addEventListener("click", function (e) { if (bjNoClick) { bjNoClick = false; return; } fn(e); });
    }
    var BJ_SPOT = { "bj-betspot": "main", "bj-boyspot": "pot", "bj-push22spot": "push22", "bj-luxspot": "trilux", "bj-busterspot": "superbuster" };
    function bjGet(k) { return k === "pot" ? potBet : k === "trilux" ? triluxBet : k === "superbuster" ? superBusterBet : k === "push22" ? push22SideBet : bet; }
    function bjSet(k, v) {
      if (k === "pot") potBet = v; else if (k === "trilux") triluxBet = v; else if (k === "superbuster") superBusterBet = v;
      else if (k === "push22") push22SideBet = v; else bet = v;
    }
    /** A stack on a spot: a tap still adds a chip; a drag lifts the chosen chip's worth (or all of it) and drops it on
        another spot, or back on the rack to take it down. Anywhere else, it goes back where it was. */
    function bjArmDrag(root) {
      Object.keys(BJ_SPOT).forEach(function (id) {
        var el = root.querySelector("#" + id); if (!el) return;
        var k = BJ_SPOT[id];
        el.addEventListener("pointerdown", function (e) {
          if (phase !== "betting" || !(bjGet(k) > 0) || !e.isPrimary) return;
          var x0 = e.clientX, y0 = e.clientY, moved = false, ghost = null, lift = Math.min(selectedChip, bjGet(k));
          var pl = el.querySelector(".placed"), r = (pl && pl.offsetWidth ? pl : el).getBoundingClientRect();
          function mv(ev) {
            var dx = ev.clientX - x0, dy = ev.clientY - y0;
            if (!moved && Math.abs(dx) + Math.abs(dy) > 9) {
              moved = true;
              ghost = document.createElement("div"); ghost.className = "dt-ghost";
              var sz = Math.min(r.width, r.height) || 46;
              ghost.style.left = (r.left + r.width / 2 - sz / 2) + "px"; ghost.style.top = (r.top + r.height / 2 - sz / 2) + "px";
              ghost.style.width = sz + "px"; ghost.style.height = sz + "px";
              ghost.innerHTML = chipHtml(lift, sz);
              document.body.appendChild(ghost);
            }
            if (ghost) { ev.preventDefault(); ghost.style.transform = "translate(" + dx + "px," + dy + "px)"; }
          }
          function up(ev) {
            window.removeEventListener("pointermove", mv); window.removeEventListener("pointerup", up); window.removeEventListener("pointercancel", up);
            if (!moved) return;
            bjNoClick = true; setTimeout(function () { bjNoClick = false; }, 400);
            if (ghost) ghost.remove();
            var at = document.elementFromPoint(ev.clientX, ev.clientY);
            var to = at && at.closest ? at.closest("#" + Object.keys(BJ_SPOT).join(", #")) : null;
            var dst = to ? BJ_SPOT[to.id] : null, lim = Shell.limits();
            if (dst && dst !== k) {
              var ok = allow(lim, lift, bjGet(dst), dst !== "main");
              if (ok > 0) { bjSet(k, bjGet(k) - ok); bjSet(dst, bjGet(dst) + ok); chipHist = []; setMessage(SPOT_SHOUT[dst]); }
              else setMessage(refusal(lim, dst !== "main"));
            } else if (at && at.closest && at.closest("#bj-bottom .rack")) {
              bjSet(k, bjGet(k) - lift); chipHist = [];
              setMessage(bjGet(k) > 0 ? fmt(lift) + " taken down" : "Chip taken down");
            }
            renderTop(); renderBottom();
          }
          window.addEventListener("pointermove", mv, { passive: false });
          window.addEventListener("pointerup", up); window.addEventListener("pointercancel", up);
        });
      });
    }""")
sub("""      fillPlaced(spots, "#bj-push22spot", phase === "betting" ? push22SideBet : push22SideStake);
      return spots;""", """      fillPlaced(spots, "#bj-push22spot", phase === "betting" ? push22SideBet : push22SideStake);
      bjArmDrag(spots);
      return spots;""")
sub("""      fillPlaced(row, "#bj-luxspot", phase === "betting" ? triluxBet : triluxStake, 38);
      return row;""", """      fillPlaced(row, "#bj-luxspot", phase === "betting" ? triluxBet : triluxStake, 38);
      bjArmDrag(row);
      return row;""")

# ---- every poker table: drag a chip stack between spots, or back to the rack to take it down ----
sub("""  function chipHtml(amount, size) {""", """  /** One drag for every table's spots. o.map: selector -> bet key; o.get/o.set read and write a bet;
      o.add(key, amount) places it through the table's own rules (limits, ante + blind room);
      o.rack: where a chip goes to come down; o.done(): redraw. A tap is still the spot's own click. */
  var chipDragEat = 0;
  document.addEventListener("click", function (e) {
    if (chipDragEat && Date.now() < chipDragEat) { chipDragEat = 0; e.stopPropagation(); e.preventDefault(); }
  }, true);
  function chipDrag(root, o) {
    if (!root) return;
    var sels = Object.keys(o.map);
    sels.forEach(function (sel) {
      var el = root.querySelector(sel), k = o.map[sel];
      if (!el || el.disabled || !(o.get(k) > 0)) return;
      el.onpointerdown = function (e) {
        if (!o.live() || !(o.get(k) > 0) || !e.isPrimary) return;
        var x0 = e.clientX, y0 = e.clientY, moved = false, ghost = null, lift = Math.min(o.chip(), o.get(k));
        var pl = el.querySelector(".placed"), r = (pl && pl.offsetWidth ? pl : el).getBoundingClientRect();
        function mv(ev) {
          var dx = ev.clientX - x0, dy = ev.clientY - y0;
          if (!moved && Math.abs(dx) + Math.abs(dy) > 9) {
            moved = true;
            ghost = document.createElement("div"); ghost.className = "dt-ghost";
            var sz = Math.min(r.width, r.height) || 40;
            ghost.style.left = (r.left + r.width / 2 - sz / 2) + "px"; ghost.style.top = (r.top + r.height / 2 - sz / 2) + "px";
            ghost.style.width = sz + "px"; ghost.style.height = sz + "px";
            ghost.innerHTML = chipHtml(lift, sz);
            document.body.appendChild(ghost);
            if (pl) pl.style.opacity = o.get(k) > lift ? "1" : ".25";
          }
          if (ghost) { ev.preventDefault(); ghost.style.transform = "translate(" + dx + "px," + dy + "px)"; }
        }
        function up(ev) {
          window.removeEventListener("pointermove", mv); window.removeEventListener("pointerup", up); window.removeEventListener("pointercancel", up);
          if (!moved) return;
          if (ev.type === "pointercancel") { if (ghost) ghost.remove(); o.done(); return; }   // the phone took the touch: put it all back
          chipDragEat = Date.now() + 150;
          if (ghost) ghost.remove();
          var at = document.elementFromPoint(ev.clientX, ev.clientY), dst = null;
          if (at && at.closest) sels.forEach(function (s) { if (!dst && at.closest(s)) dst = o.map[s]; });
          if (dst && dst !== k) {
            o.set(k, o.get(k) - lift);
            var before = o.get(dst);
            o.add(dst, lift);
            var got = o.get(dst) - before;
            if (got < lift) o.set(k, o.get(k) + lift - got);
          } else if (!dst && at && at.closest && at.closest(o.rack)) {
            o.set(k, o.get(k) - lift);
            o.say(o.get(k) > 0 ? fmt(lift) + " taken down" : "Chip taken down");
          }
          o.done();
        }
        window.addEventListener("pointermove", mv, { passive: false });
        window.addEventListener("pointerup", up); window.addEventListener("pointercancel", up);
      };
    });
  }
  function chipHtml(amount, size) {""")
for pre, spots, keys in [
    ("uth", "uth-spots", [("ante","ante"),("blind","ante"),("trips","trips"),("badbeat","badBeat")]),
    ("dj", "dj-spots", [("ante","ante"),("blind","ante"),("trips","trips"),("badbeat","badBeat")]),
    ("mu", "mu-spots", [("ante","ante"),("blind","ante"),("flushplus","flushPlus"),("bonus","bonus")]),
    ("is", "is-spots", [("ante","ante"),("flushrush","flushRush"),("superrush","superRush")]),
]:
    names = sorted(set(k for k,_ in keys if k != "blind"))
    m = ", ".join('"#%s-%s": "%s"' % (pre, k, "ante" if k == "blind" else k) for k,_ in keys)
    var = dict((k, v) for k, v in keys if k != "blind")
    get = " : ".join('k === "%s" ? %s' % (k, v) for k, v in var.items()) + " : 0"
    st = " else ".join('if (k === "%s") %s = v;' % (k, v) for k, v in var.items())
    sub('      $("%s-ante").addEventListener("click", function () { place("ante"); });\n      }' % pre,
        '      $("%s-ante").addEventListener("click", function () { place("ante"); });\n      }\n'
        '      if (betting) chipDrag($("%s"), { map: { %s },\n'
        '        live: function () { return phase === "betting"; }, chip: function () { return selectedChip; },\n'
        '        get: function (k) { return %s; },\n'
        '        set: function (k, v) { %s },\n'
        '        add: function (k, v) { var s = selectedChip; selectedChip = v; try { place(k); } finally { selectedChip = s; } },\n'
        '        rack: "#%s-rack, #%s-actions", say: setMessage, done: function () { %srenderTop(); renderSpots(); } });'
        % (pre, spots, m, get, st, pre, pre, "chipHist = []; " if pre in ("uth", "dj") else ""))
sub("  .dt-ghost { position: fixed;", "  .uth-spot, .pg-spot, #dd-spots .spot, #dz-bet, #bj-betspot, #bj-boyspot, #bj-push22spot, #bj-luxspot, #bj-busterspot { touch-action: none; }\n  .uth-spot .placed, .pg-spot .placed, #dd-spots .spot .placed { touch-action: none; cursor: grab; }\n  .dt-ghost { position: fixed;")
# Double Down Madness
sub('      fill(s, "#dd-pairspot", betting ? pairBet : pairStake, 32);',
    '''      fill(s, "#dd-pairspot", betting ? pairBet : pairStake, 32);
      if (betting) chipDrag(s, { map: { "#dd-betspot": "main", "#dd-pushspot": "push22", "#dd-pairspot": "pair" },
        live: function () { return phase === "betting"; }, chip: function () { return selectedChip; },
        get: function (k) { return k === "push22" ? push22Bet : k === "pair" ? pairBet : bet; },
        set: function (k, v) { if (k === "push22") push22Bet = v; else if (k === "pair") pairBet = v; else bet = v; },
        add: function (k, v) { var q = selectedChip; selectedChip = v; try { addChip(k); } finally { selectedChip = q; } },
        rack: "#dd-rack, #dd-actions", say: setMessage, done: function () { chipHist = []; renderTop(); renderSpots(); } });''')
# Destroyer: one spot, so the drag only takes it down
sub('''          : '<span class="cap">BET</span>');
      $("dz-shuffle").style.visibility''', '''          : '<span class="cap">BET</span>');
      spot.onpointerdown = null;
      if (phase === "betting") chipDrag(spot.parentNode, { map: { "#dz-bet": "bet" },
        live: function () { return phase === "betting"; }, chip: function () { return selectedChip; },
        get: function () { return bet; }, set: function (k, v) { bet = v; }, add: function () {},
        rack: "#dz-rack, #dz-actions", say: setMessage, done: function () { renderTop(); renderBet(); } });
      $("dz-shuffle").style.visibility''')

# ---- card sounds: a hand can opt out (data-cardsnd="off"), or count as one pile however it's split (data-cardsnd="pool") ----
sub("""      for (var i = 0; i < cards.length; i++) {
        var k = spotOf(cards[i].parentNode, screen);""", """      for (var i = 0; i < cards.length; i++) {
        var q = cards[i].closest("[data-cardsnd]");
        if (q && q.getAttribute("data-cardsnd") === "off") continue;
        var k = q ? "pool:" + q.id : spotOf(cards[i].parentNode, screen);""")
sub("""        if ((recs[i].oldValue || "").split(/s+/).indexOf("up") >= 0) continue;""",
    """        if ((recs[i].oldValue || "").split(/\\s+/).indexOf("up") >= 0) continue;   // was /s+/: a face-up card changing any other class turned over again
        if (el.closest('[data-cardsnd="off"]')) continue;""")
# I Luv Suits: only your own cards make a sound; sorting them into the flush and the rest is not a deal
sub('<div class="cards is-hand" id="is-dealer" style="margin-top:4px"></div>', '<div class="cards is-hand" id="is-dealer" data-cardsnd="off" style="margin-top:4px"></div>')
sub('<div class="cards is-hand" id="is-hole" style="margin-top:4px"></div>', '<div class="cards is-hand" id="is-hole" data-cardsnd="pool" style="margin-top:4px"></div>')

# ---- card backs: pick one in Settings (kept on this phone) ----
import base64 as _b64
_classic = re.search(r'\.card \.back \{\s*transform: rotateY\(180deg\);\s*background: #0A0507 url\("(data:image/webp;base64,[^"]+)"\)', h).group(1)
_boys = "data:image/webp;base64," + _b64.b64encode((here / "cardbacks" / "back_boys.webp").read_bytes()).decode()
# the 4 The Boys back is the default; the old back stays as ORIGINAL
_graf = "data:image/webp;base64," + _b64.b64encode((here / "cardbacks" / "back_graffiti.webp").read_bytes()).decode()
# the graffiti back is everyone's default now
CARDBACKS = [("graffiti", "GRAFFITI", _graf), ("classic", "4 THE BOYS", _boys), ("original", "ORIGINAL", _classic)] + [
    (k, n, "data:image/webp;base64," + _b64.b64encode((here / "cardbacks" / f"back_{k}.webp").read_bytes()).decode())
    for k, n in [("dragon", "DRAGON"), ("neon", "NEON"), ("crown", "CROWN TAG")]]
_cbcss = f'  html:not([data-cb]) .card .back {{ background-image: url("{_graf}"); }}\n'
_cbcss += "".join(f'  html[data-cb="{k}"] .card .back {{ background-image: url("{u}"); }}\n' for k, n, u in CARDBACKS)
_cbcss += "".join(f'  .cb-opt[data-cb="{k}"] i {{ background-image: url("{u}"); }}\n' for k, n, u in CARDBACKS)
_cbcss += """  .cb-box { width: 100%; max-width: 380px; margin-top: 14px; }
  .cb-box .hd { font-family: var(--display); font-size: 11px; letter-spacing: .16em; color: rgba(245,241,232,.8); text-align: center; margin-bottom: 8px; }
  .cb-row { display: grid; grid-template-columns: repeat(4, 64px); justify-content: center; gap: 12px 10px; }
  .cb-opt { display: flex; flex-direction: column; align-items: center; gap: 5px; padding: 0; background: none; border: 0; color: rgba(245,241,232,.55);
            font-family: var(--mono); font-size: 7px; letter-spacing: .1em; white-space: nowrap; cursor: pointer; }
  .cb-opt i { display: block; width: 52px; height: 78px; border-radius: 6px; background: #0A0507 center/cover no-repeat;
              box-shadow: 0 4px 10px rgba(0,0,0,.5); outline: 2px solid transparent; outline-offset: 2px; transition: outline-color .15s, transform .15s; }
  .cb-opt.on { color: #FFD24D; }
  .cb-opt.on i { outline-color: #FFD24D; transform: translateY(-3px); }
"""
sub("  .st-foot {", _cbcss + "  .st-foot {")
sub('      <div class="st-foot">BOTH ARE KEPT ON THIS PHONE', '''      <div class="cb-box"><div class="hd">CARD BACK</div><div class="cb-row" id="cb-row">'''
    + "".join(f'<button class="cb-opt" data-cb="{k}" type="button"><i></i>{n}</button>' for k, n, u in CARDBACKS)
    + '''</div></div>
      <div class="st-foot">BOTH ARE KEPT ON THIS PHONE''')
sub("</body>", """<script>
(function () {
  var KEY = "ftb.cardback", root = document.documentElement;
  // once: everyone starts on the graffiti back (they can still pick another after)
  try { if (!localStorage.getItem("ftb.cb.graffiti")) { localStorage.setItem("ftb.cb.graffiti", "1"); localStorage.setItem(KEY, "graffiti"); } } catch (e) {}
  function get() {
    var k = "graffiti";
    try { k = localStorage.getItem(KEY) || "graffiti"; } catch (e) {}
    return document.querySelector('.cb-opt[data-cb="' + k + '"]') ? k : "graffiti";   // a back that's been taken out goes back to the default
  }
  function apply(k) {
    root.setAttribute("data-cb", k);
    var row = document.getElementById("cb-row");
    if (row) [].forEach.call(row.children, function (b) { b.classList.toggle("on", b.getAttribute("data-cb") === k); });
  }
  apply(get());
  var row = document.getElementById("cb-row");
  if (row) row.addEventListener("click", function (e) {
    var b = e.target.closest(".cb-opt"); if (!b) return;
    var k = b.getAttribute("data-cb");
    try { localStorage.setItem(KEY, k); } catch (e2) {}
    apply(k);
  });
})();
</script>
</body>""")

# ---- no walking away from a hand ----
# Leaving a table mid-hand and coming back started a fresh hand, and the chips
# already on the felt went with the old one. Each table now says when a hand
# is live, and the way out waits until it is over.
BUSY_DEFAULT = 'phase !== "betting" && phase !== "result"'
BUSY = {"Holdem": None, "DjWild": None, "Shootout": None, "MiniUth": None, "IluvSuits": None,
        "Destroyer": None, "SicBo": None, "Roulette": None, "Wheel": None, "Keno": None,
        "Mines": None, "Blackjack": None, "DoubleDown": None, "Baccarat": None,
        "DragonTiger": None, "Plinko": "inFlight()", "Craps": 'phase === "rolling" || atRisk() > 0'}
for name, test in BUSY.items():
    a = h.index("\n  var %s = (function () {" % name)
    b = h.index("\n  })();", a)
    body = h[a:b]
    r = body.rindex("\n    return {")
    # credit: the hot game's second helping, put straight onto the table's own stack
    draw = "updateMoney" if name == "Baccarat" else "renderAll"
    credit = " credit: function (x) { bankroll += x; syncWallet(); try { %s(); } catch (e) {} }, cash: function () { return bankroll; }," % draw
    # seatOk: nothing is in motion, so the table can be left as it lies and picked up again
    still = {"Craps": 'phase === "rolling"', "Plinko": "inFlight()"}.get(name, test or BUSY_DEFAULT)
    credit += " seatOk: function () { return !(%s); }," % still
    body = body[:r] + "\n    return { busy: function () { return %s; },%s" % (test or BUSY_DEFAULT, credit) + body[r + len("\n    return {"):]
    h = h[:a] + body + h[b:]

# ---- walk-ups: a friend's tip goes down as a real bet on the felt ----
# tip(spot, v): puts v on that spot through the table's own placing (limits and
# bankroll apply) and says how much went down. phase(): where the hand is.
def tipfn(get, put, draw="renderAll"):
    return (" tip: function (spot, v) { if (phase !== \"betting\") return 0; var sc = selectedChip, before = %s; selectedChip = v;"
            " try { %s; } catch (e) {} finally { selectedChip = sc; } try { %s(); } catch (e) {} return %s - before; },"
            " phase: function () { return phase; }, variant: function () { return typeof variant === \"undefined\" ? \"\" : variant; },"
            " tipOn: function (spot) { try { return %s; } catch (e) { return 0; } },"
            " redraw: function () { try { %s(); } catch (e) {} },") % (get, put, draw, get, get, draw)
# untip(spot, v): the friend's chips came off the "last bet", so REBET doesn't keep them
def lastBet(expr): return ' untip: function (spot, v) { try { %s } catch (e) {} },' % expr
def fromLast(obj, key="spot"):
    return 'var k = %s; if (%s[k]) { %s[k] = Math.max(0, %s[k] - v); if (!%s[k]) delete %s[k]; }' % (key, obj, obj, obj, obj, obj)
TIPFN = {
  "Holdem": tipfn('(spot === "trips" ? trips : badBeat)', 'place(spot)') +
    lastBet('if (spot === "trips") lastTrips = Math.max(0, lastTrips - v); else lastBadBeat = Math.max(0, lastBadBeat - v);'),
  "DjWild": tipfn('(spot === "trips" ? trips : badBeat)', 'place(spot)') +
    lastBet('if (spot === "trips") lastTrips = Math.max(0, lastTrips - v); else lastBadBeat = Math.max(0, lastBadBeat - v);'),
  "MiniUth": tipfn('(spot === "flushplus" ? flushPlus : bonus)', 'place(spot)') +
    lastBet('if (spot === "flushplus") lastFlushPlus = Math.max(0, lastFlushPlus - v); else lastBonus = Math.max(0, lastBonus - v);'),
  "Baccarat": tipfn('(bets[spot] || 0)', 'addBet(spot)', "updateMoney") + lastBet(fromLast("lastBets")),
  "DragonTiger": tipfn('(bets[spot] || 0)', 'addChip(spot)') + lastBet(fromLast("lastBets")),
  "SicBo": tipfn('(bets[spot] || 0)', 'addChip(spot)') + lastBet(fromLast("lastBets")),
  "Wheel": tipfn('(bets[spot] || 0)', 'addChip(spot)') + lastBet(fromLast("lastBets")),
  "Blackjack": tipfn('(spot === "trilux" ? triluxBet : push22SideBet)', 'addChip(spot)') +
    lastBet('if (spot === "trilux") lastTrilux = Math.max(0, lastTrilux - v); else lastPush22Side = Math.max(0, lastPush22Side - v);'),
  "Roulette": tipfn('(bets[tipRoute(spot).id] || 0)', 'addChip(tipRoute(spot))') +
    lastBet('var k = tipRoute(spot).id; if (lastBets[k]) { lastBets[k] = Math.max(0, lastBets[k] - v); if (!lastBets[k]) { delete lastBets[k]; delete lastDefs[k]; delete lastCens[k]; } }'),
  # keno: the friend's number gets marked on your ticket (2 = it was already yours, so it stays)
  "Keno": (" tip: function (spot) { if (phase !== \"betting\") return 0; var n = +spot.slice(2);"
           " if (picks.indexOf(n) >= 0) return 2; if (picks.length >= MAX) return 0;"
           " picks.push(n); noteText = \"\"; msgText = picks.length + \" marked\"; renderAll(); return 1; },"
           " phase: function () { return phase; },"
           " untip: function (spot, v) { if (v !== 1) return; var n = +spot.slice(2), i = lastPicks.indexOf(n); if (i >= 0) lastPicks.splice(i, 1); },"),
}
for name, fn in TIPFN.items():
    a = h.index("\n  var %s = (function () {" % name)
    b = h.index("\n  })();", a)
    body = h[a:b]
    r = body.rindex("\n    return { busy:")
    body = body[:r] + "\n    return {" + fn + body[r + len("\n    return {"):]
    h = h[:a] + body + h[b:]
# roulette: where a tip's chips go
sub("    function routeArt(artX, artY) {", """    function tipRoute(spot) {
      var i, p;
      if (spot.indexOf("n:") === 0) {
        var n = +spot.slice(2), r = Math.floor((n - 1) / 3), c = (n - 1) % 3;
        p = anchorPx(c + 0.5, r + 1.5);
        return { id: "s-" + n, bet: straight(n), cx: p.x, cy: p.y };
      }
      if (spot.indexOf("dz-") === 0) {
        i = +spot.slice(3);
        var dt = A.GRID_TOP + i * 4 * A.ROW_PITCH, db = A.GRID_TOP + (i + 1) * 4 * A.ROW_PITCH;
        return { id: spot, bet: dozen(i), cx: (A.DOZENS_LEFT + A.DOZENS_RIGHT) / 2, cy: (dt + db) / 2 };
      }
      for (i = 0; i < EVENS.length; i++) if (EVENS[i][0] === spot)
        return { id: spot, bet: EVENS[i][1], cx: (A.EVEN_EDGES[i] + A.EVEN_EDGES[i + 1]) / 2, cy: (A.EVEN_TOP + A.EVEN_BOTTOM) / 2 };
      return null;
    }
    function routeArt(artX, artY) {""")

# ---- free bet blackjack: a pair of 5s splits free, it never doubles ----
sub("          var freeDbl = live && !h.aceSplit && canFreeDouble(h.cards);",
    "          var freeDbl = live && !h.aceSplit && canFreeDouble(h.cards) &&\n"
    "            !(h.cards.length === 2 && h.cards[0].rank === \"5\" && h.cards[1].rank === \"5\");")

# ---- free bet blackjack: the free-double coins are the gold logo ----
sub("""coinsHtml += '<img class="coin" src="' + BOYS_IMG + '" alt="coin">';""",
    """coinsHtml += '<img class="coin" src="' + COIN_IMG + '" alt="coin">';""")

# ---- Ultimate Hold'em and DJ Wild: their own, bigger Bad Beat caps ----
sub('  function badBeatRefusal(limits) { return "Bad Beat max $" + fmt(limits.badBeatMax); }',
    '  function badBeatRefusal(limits) { return "Bad Beat max $" + fmt(limits.badBeatMax); }\n'
    '  function doubleBadBeat(limits) {\n'
    '    var l = {}; for (var k in limits) l[k] = limits[k];\n'
    '    // by room: Basement 50, Main Floor 250, High Limit 500, Salon 1,000, Whale Room 5,000\n'
    '    l.badBeatMax = { 25: 50, 50: 250, 75: 500, 100: 1000, 125: 5000 }[limits.badBeatMax] || limits.badBeatMax;\n'
    '    return l;\n'
    '  }')
sub('var amount = spot === "badbeat" ? allowBadBeat(lim, affordable, badBeat)',
    'var amount = spot === "badbeat" ? allowBadBeat(doubleBadBeat(lim), affordable, badBeat)')
sub('if (amount <= 0) { setMessage(spot === "badbeat" ? badBeatRefusal(lim) : refusal(lim, side)); return; }',
    'if (amount <= 0) { setMessage(spot === "badbeat" ? badBeatRefusal(doubleBadBeat(lim)) : refusal(lim, side)); return; }')
sub("""        amount = allowBadBeat(lim, affordable, badBeat);
        refused = badBeatRefusal(lim);""",
"""        amount = allowBadBeat(doubleBadBeat(lim), affordable, badBeat);
        refused = badBeatRefusal(doubleBadBeat(lim));""")

# ---- Double Down Madness: an ace and a ten is a blackjack even after doubling ----
# It used to count as a plain 21 once doubled, so a dealer who drew out to 21
# (he keeps drawing while Push 22 is up) pushed it. Doubled, it now beats
# everything but a dealer blackjack and wins even money on the whole stake.
sub("""      // A hand that took a card can no longer be a blackjack, however it adds up.
      var playerBj = !doubled && isBJ(player);
      if (playerBj && dealerBj) return stake;
      if (playerBj) return wholeDollars(stake + 1.5 * betUnit);
      if (dealerBj) return 0;""",
"""      // Ace and ten on the first two cards is a blackjack. Not doubled it pays
      // 3:2; doubled, it beats every dealer hand but a blackjack at even money.
      var playerBj = !doubled && isBJ(player);
      if (playerBj && dealerBj) return stake;
      if (playerBj) return wholeDollars(stake + 1.5 * betUnit);
      if (dealerBj) return 0;
      if (isBJ(player)) return stake * 2;""")
sub('results.push({ label: playerBj ? "Blackjack" : "Hand", net: returned - hand.stake });',
    'results.push({ label: isBJ(hand.cards) ? "Blackjack" : "Hand", net: returned - hand.stake });')

# the tournament itself, once the menu is painted
sub("  paintMenu();\n  /* ---------- sound",
    "  paintMenu();\n" + (here / "menumusic.js").read_text() + ((here / "seat.js").read_text() if SEAT else "") + (here / "intro.js").read_text().replace("/*SPRAYFONT*/", __import__("base64").b64encode((here / "spray-title.woff2").read_bytes()).decode()).replace("/*SPRAYSCALE*/", __import__("os").environ.get("SPRAYSCALE", "1")).replace("/*NEONFONT*/", __import__("base64").b64encode((here / "tilt-neon.woff2").read_bytes()).decode()).replace("/*CHIPCLACK*/", __import__("base64").b64encode((here / "chip-clack.mp3").read_bytes()).decode()).replace("/*CANSHAKE*/", __import__("base64").b64encode((here / "can-shake.mp3").read_bytes()).decode()).replace("/*TIPSLOOP*/", __import__("base64").b64encode((here / "tips-loop.mp3").read_bytes()).decode()) + (here / "tourney.js").read_text() + "  /* ---------- sound")

# ---- big bankrolls: the money bar shrinks to fit the screen instead of pushing the page wider ----
# (a page wider than the phone makes the browser zoom out, and it comes back from texting the wrong size)
_OLDFS = """/* at a table the fullscreen button sits up top in the middle, between the back button and the money */
body:has(.screen.on .purse) .fs-btn { top: 11px; bottom: auto; right: auto; left: 50%; margin-left: -17px; }"""
h = h.replace("</head>", """<style>html, body { overflow-x: hidden; max-width: 100%; }
/* tapping fast on a bet spot was picking out its text and bringing up the phone's Copy / Select all bar:
   nothing in the game is text to pick, except the boxes you type in and Justin's posts */
body { -webkit-user-select: none; user-select: none; -webkit-touch-callout: none; -webkit-tap-highlight-color: transparent; }
input, textarea, [contenteditable], .tt-mbody, .tt-msg p { -webkit-user-select: text; user-select: text; -webkit-touch-callout: default; }
/* baccarat: the result pills float in the gap above the chips, so the table doesn't shift when they show */
#screen-baccarat #pills { height: 0; margin: 0; overflow: visible; align-items: flex-end; align-content: flex-end;
  position: relative; z-index: 3; transform: translateY(-3px); }
#pour-bub { position: fixed; z-index: 9997; display: none; align-items: center; gap: 6px; padding: 6px 12px 6px 7px; border-radius: 999px;
  background: rgba(14,11,4,.96); border: 1.5px solid #FFD24D; box-shadow: 0 6px 18px rgba(0,0,0,.6); pointer-events: none; white-space: nowrap; }
#pour-bub i { width: 18px; height: 18px; border-radius: 50%; background: radial-gradient(circle at 35% 30%, #fff3b0, #FFD24D 55%, #b8860b);
  border: 2px dashed rgba(0,0,0,.45); box-sizing: border-box; }
#pour-bub b { font: 800 16px/1 system-ui, sans-serif; color: #FFD24D; letter-spacing: .02em; }
/*SEATCSS*/
#seat-btn { position: fixed; z-index: 9998; width: 44px; height: 34px; padding: 0; border-radius: 10px; background: rgba(8,6,14,.78);
  border: 1px solid rgba(255,210,77,.55); color: #FFD24D; font: 800 8.5px/1.15 system-ui, sans-serif; letter-spacing: .08em; text-align: center; }
#seat-btn[hidden] { display: none; }
#seat-btn:active { transform: scale(.94); }
#seat-note { position: fixed; left: 50%; top: calc(env(safe-area-inset-top, 0px) + 56px); transform: translateX(-50%); z-index: 10020; max-width: 88vw;
  padding: 9px 14px; border-radius: 999px; background: rgba(14,11,4,.96); border: 1.5px solid #FFD24D; color: #FFD24D;
  font: 800 11px/1.2 system-ui, sans-serif; letter-spacing: .08em; text-align: center; opacity: 0; pointer-events: none; transition: opacity .2s ease; }
#seat-note.on { opacity: 1; }
.seat-tag { position: absolute; left: 12px; bottom: -8px; z-index: 2; padding: 3px 8px; border-radius: 999px; background: #14101c; border: 1.5px solid #FFD24D;
  color: #FFD24D; font: 900 9px/1 system-ui, sans-serif; letter-spacing: .08em; white-space: nowrap; pointer-events: none; }
.mu-sidepill { display: flex; justify-content: center; flex-wrap: wrap; gap: 3px; margin-bottom: 4px; }
#screen-miniuth #mu-results { display: none; }
#screen-djwild #dj-results, #screen-holdem #uth-res-labels, #screen-holdem #uth-res-amounts { display: none; }
.mu-mainpill { display: flex; align-items: flex-end; justify-content: center; }
.purse { position: relative; overflow: visible !important; }
.purse > .pw { display: none !important; }
.purse[data-won] > .pw { display: flex !important; flex-direction: column; align-items: flex-end; line-height: 1.1;
  position: absolute; right: 0; left: 0; top: 21px; padding: 0 14px 3px; box-sizing: border-box; z-index: 6; pointer-events: none; }
.purse[data-hot] > .pw { background: rgba(8,6,12,.97); border: 1px solid rgba(255,255,255,.2); border-top: 0; border-radius: 0 0 11px 11px; padding-bottom: 5px; }
.purse[data-won] .pr { visibility: hidden; }
.purse .pw b { font: 11px/1.15 var(--display); color: rgba(245,241,232,.75); font-weight: 400; }
.purse .pw b.up { color: #57E06A; font-size: 12px; }
.purse .pw b.dn { color: #FF6B6B; font-size: 12px; }
#screen-blackjack .spot .placed { touch-action: none; cursor: grab; }
#screen-blackjack .spot img { pointer-events: none; -webkit-user-drag: none; }
.purse .pw i { font: italic 10px/1.1 var(--display); color: #FFB238; }
/* Mini UTH: the dealer's hand name sits beside the dealer's card, clear of the money window */
#mu-dealer-label { position: relative; }
#mu-dealer-label .badge { position: absolute; left: calc(50% + 38px); top: calc(100% + 32px); margin: 0; white-space: nowrap; }
.mu-sidepill .dj-pill { font-size: 10px; padding: 2px 9px; }
.purse.stack { flex-direction: column !important; align-items: flex-end !important; justify-content: center !important; gap: 0 !important; height: auto !important; min-height: 34px; padding: 3px 14px !important; line-height: 1.1; }
.purse.stack .lbl, .purse.stack .gap { display: none !important; }
.purse.stack > span:not(.val):not(.lbl):not(.gap):not(.pr):not(.pw) { display: contents !important; }
.purse.stack .pr { display: flex !important; align-items: baseline; justify-content: flex-end; gap: 5px; }
.purse.stack .pr .lbl { display: inline !important; font-size: 8px !important; letter-spacing: .12em; color: rgba(245,241,232,.5) !important; }
.purse.stack .pr.empty .lbl { visibility: hidden; }
.purse.stack .val.risk, .purse.stack .val.staked { font-size: 11px !important; color: rgba(245,241,232,.62) !important; font-family: var(--display); }
/* result pills pair up, two to a line, so the table fits the phone */
.dj-pills { flex-direction: row !important; flex-wrap: wrap !important; justify-content: center !important; max-width: 380px; margin-left: auto !important; margin-right: auto !important; }
</style>
<script>
(function () {
  var busy = false;
  function fit() {
    busy = false;
    var pad = 8;
    document.querySelectorAll(".purse").forEach(function (el) {
      if (!el.offsetParent) return;
      // money on top in full; right under it, what's at risk, short: −500M
      el.classList.add("stack");
      el.querySelectorAll(".val.risk, .val.staked").forEach(function (r) {
        var m = /^\\s*\\$?([\\d,]+)\\s*$/.exec(r.textContent);
        if (!m) return;
        var v = +m[1].replace(/,/g, "");
        r.dataset.raw = String(v);      // the exact figure, for anything that needs it after it's shortened
        r.textContent = v > 0 ? "−" + short(v) : "\\u00a0";
      });
      // AT RISK sits on the left of its amount
      el.querySelectorAll(".val.risk, .val.staked").forEach(function (r) {
        var row = r.parentElement;
        if (row === el) {
          var lb = r.previousElementSibling;
          row = document.createElement("span");
          el.insertBefore(row, lb && lb.classList.contains("lbl") ? lb : r);
          if (lb && lb.classList.contains("lbl")) row.appendChild(lb);
          row.appendChild(r);
        }
        row.classList.add("pr");
        row.classList.toggle("empty", !/\\d/.test(r.textContent));
      });
      el.style.zoom = "";
      var r = el.getBoundingClientRect();
      if (r.right <= innerWidth - pad || r.width <= 0) return;
      var room = innerWidth - pad - r.left;
      el.style.zoom = String(Math.max(0.55, Math.floor(room / r.width * 100) / 100));
    });
  }
  function short(v) {
    if (v < 1e6) return v.toLocaleString("en-US");
    var u = v >= 1e12 ? [1e12, "T"] : v >= 1e9 ? [1e9, "B"] : [1e6, "M"], x = v / u[0], d = x >= 100 ? 0 : x >= 10 ? 1 : 2;
    var t = (Math.floor(x * Math.pow(10, d) + 1e-6) / Math.pow(10, d)).toFixed(d);
    if (d) t = t.replace(/0+$/, "").replace(/\\.$/, "");
    return t + u[1];
  }
  function soon() { if (!busy) { busy = true; requestAnimationFrame(fit); } }
  // the money window's second line after a hand: +what you won, or on a 2x game  400B / ×2 / 800B
  function pw(el) {
    if (!el) return null;
    var b = el.querySelector(".pw");
    if (!b) { b = document.createElement("span"); b.className = "pw"; el.appendChild(b); }
    return b;
  }
  /* It is laid OVER the at-risk line and hangs below the window: it never changes the window's
     size, so nothing on the table moves when it shows. Marked with data-won (a table that rewrites
     the window's classes can't knock it off), and it takes itself down after a few seconds. */
  var pwTimer = 0;
  window.purseWin = {
    clear: function (el) {
      if (!el) return;
      delete el.dataset.won; delete el.dataset.hot;
      var b = el.querySelector(".pw"); if (b) b.innerHTML = "";
    },
    net: function (el, v, losses) {
      if (!el || el.dataset.hot) return;
      if (losses && v < 0) { el.dataset.won = "1"; pw(el).innerHTML = '<b class="dn">−' + short(-v) + "</b>"; return; }
      if (!(v > 0)) { this.clear(el); return; }
      el.dataset.won = "1"; pw(el).innerHTML = '<b class="up">+' + short(v) + "</b>";
    },
    hot: function (el, base, x, total) {
      if (!el) return;
      el.dataset.hot = "1"; el.dataset.won = "1";
      pw(el).innerHTML = '<b>+' + short(base) + '</b><i>' + x + '</i><b class="up">+' + short(total) + "</b>";
      var self = this, mine = ++pwTimer;
      setTimeout(function () { if (mine === pwTimer && el.dataset.hot) self.clear(el); }, 8000);
    }
  };
  /* Fullscreen only. With the phone's bars gone, the page still starts below the camera strip but is
     sized as if it had the whole screen, so it is one strip too tall and scrolls for no reason.
     In fullscreen the tables are made one strip shorter. Nothing is scaled or locked, and outside
     fullscreen nothing changes. */
  var probe = null;
  function strip() {
    if (!document.body) return 0;
    if (!probe) {
      probe = document.createElement("div");
      probe.style.cssText = "position:fixed;top:0;left:0;width:0;visibility:hidden;pointer-events:none;" +
        "height:calc(env(safe-area-inset-top, 0px) + var(--fakeInset, 0px))";
    }
    if (!probe.parentNode) document.body.appendChild(probe);
    return Math.ceil(probe.getBoundingClientRect().height) || 0;
  }
  function fsCut() {
    var full = !!(document.fullscreenElement || document.webkitFullscreenElement);
    document.documentElement.style.setProperty("--fsCut", (full ? strip() : 0) + "px");
  }
  function fsSoon() { fsCut(); setTimeout(fsCut, 300); setTimeout(fsCut, 1000); }
  document.addEventListener("fullscreenchange", fsSoon);
  document.addEventListener("webkitfullscreenchange", fsSoon);
  addEventListener("resize", fsCut);
  addEventListener("resize", soon);
  document.addEventListener("visibilitychange", soon);

  /* Press and hold a bet spot to pour chips on it. After a short hold the spot is tapped for you,
     faster and faster, until you let go, slide off, or the chips stop going down (table max, or
     you're out of money). The deal/action buttons, the chip rack and the back button never repeat. */
  (function () {
    var NO = '[id$="actions"], .actions, .uth-actions, .bj-actions, .chip-btn, .wayout, .fs-btn, #wu-card, #tt-x2, #tt-rebuy, a, input, textarea, select';
    var hold = null;
    var MONEY = '.screen.on [id$="bankroll"], .screen.on [id$="staked"], .screen.on [id$="risk"]';
    function purseText() {
      var t = "";
      document.querySelectorAll(MONEY).forEach(function (e) { t += e.textContent + "|"; });
      return t;
    }
    // your finger is on the spot, so a little bubble beside it shows what you've poured so far
    var bub = null;
    function bank() {      // what's on the table right now
      var e = document.querySelector('.screen.on [id$="risk"], .screen.on [id$="staked"]');
      if (!e) return NaN;
      var t = e.textContent;
      if (/^[\\s$\\d,]*\\d[\\s$\\d,]*$/.test(t)) return +t.replace(/[^0-9]/g, "");   // freshly written, in full
      if (e.dataset.raw != null) return +e.dataset.raw;
      var d = t.replace(/[^0-9]/g, "");
      return d ? +d : 0;
    }
    function bubble() {
      var h = hold;
      if (!h || !h.fired) return;
      if (!bub) { bub = document.createElement("div"); bub.id = "pour-bub"; document.body.appendChild(bub); }
      // what is on the spot under your finger (just that spot, not a bet that rides along with it)
      var on = NaN, n = document.elementFromPoint(h.x, h.y);
      for (var up = 0; n && up < 4 && isNaN(on); up++, n = n.parentElement) {
        var raw = n.querySelector ? n.querySelector(".amt-raw") : null;
        if (raw) on = +raw.getAttribute("data-amt");
      }
      var now = bank(), put = isNaN(now) || isNaN(h.bank) ? 0 : now - h.bank;
      bub.innerHTML = "<i></i><b>" + (on > 0 ? short(on) : put > 0 ? "+" + short(put) : "×" + h.fired) + "</b>";
      var left = h.x > innerWidth / 2;          // on whichever side has the room
      bub.style.left = left ? "" : Math.min(innerWidth - 8, h.x + 46) + "px";
      bub.style.right = left ? Math.min(innerWidth - 8, innerWidth - h.x + 46) + "px" : "";
      bub.style.top = Math.max(8, h.y - 22) + "px";
      bub.style.display = "flex";
    }
    function stop() {
      if (bub) bub.style.display = "none";
      if (!hold) return;
      clearTimeout(hold.t);
      var fired = hold.fired;
      hold = null;
      // the finger coming up would be one more tap: swallow it if we were already pouring
      if (fired) { swallow = Date.now() + 400; }
    }
    var swallow = 0;
    /** The thing to tap for a spot: the spot itself (it usually has an id and stays put) rather than
        the chip drawn inside it, which is thrown away and redrawn on every chip. */
    function steady(el) {
      for (var n = el, up = 0; n && up < 4; n = n.parentElement, up++) {
        if (n.id && !n.classList.contains("screen")) {
          var r = n.getBoundingClientRect();
          if (r.width <= 240 && r.height <= 240) return n;
          break;
        }
      }
      return el;
    }
    /** One chip onto the spot under the finger. False when the pour should end. */
    function drop(h) {
      if (!document.body.contains(h.el)) {
        // redrawn out from under us: the same spot by its id if it has one (quick), else whatever is under the finger now
        var now = h.sid ? document.getElementById(h.sid) : null;
        if (!now) { now = document.elementFromPoint(h.x, h.y); if (now) now = steady(now); }
        if (!now || now.closest(NO) || !now.closest(".screen.on")) return false;
        h.el = now; h.sid = now.id || "";
      }
      h.pouring = true;
      try { h.el.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true, view: window, clientX: h.x, clientY: h.y })); }
      catch (err) { h.pouring = false; return false; }
      h.pouring = false;
      h.fired++;
      return true;
    }
    /* The pour speeds up the whole time you hold: a few chips a second at first, about 33 a second
       after one second, and it keeps climbing to about 330 a second (ten times that) at ten seconds. */
    function tick() {
      if (!hold) return;
      var h = hold, t = Date.now();
      if (!h.began) {
        h.began = t; h.last = t; h.owed = 1;
        if (document.body.contains(h.el)) { h.el = steady(h.el); h.sid = h.el.id || ""; }
      }
      if (!document.querySelector(MONEY)) { stop(); return; }       // left the table
      var held = (t - h.began) / 1000;
      var rate = held < 0.5 ? 11 : held < 1 ? 18 : 33 * (1 + 9 * Math.pow(Math.min(1, (held - 1) / 9), 2));
      h.owed += Math.min(rate * (t - h.last) / 1000, 40);
      h.last = t;
      var n = Math.floor(h.owed);
      h.owed -= n;
      var before = purseText(), spent = Date.now();
      for (var c = 0; c < n; c++) {
        if (hold !== h) return;
        if (!drop(h)) { stop(); return; }
        if (Date.now() - spent > 45) { h.owed = 0; break; }     // a slow phone: don't freeze, just pour what it can
      }
      // the chips have stopped going down (table max, or out of money): that's the end of the pour
      if (n > 0) { if (purseText() === before) { if (++h.dry >= 2) { stop(); return; } } else h.dry = 0; }
      bubble();
      h.t = setTimeout(tick, 25);
    }
    document.addEventListener("pointerdown", function (e) {
      stop();
      if (e.button) return;
      var el = e.target;
      if (!el || !el.closest || !el.closest(".screen.on") || !document.querySelector(MONEY)) return;
      if (el.closest(NO)) return;
      hold = { el: el, x: e.clientX, y: e.clientY, fired: 0, dry: 0, id: e.pointerId, bank: bank(), t: setTimeout(tick, 380) };
    }, true);
    document.addEventListener("pointermove", function (e) {
      if (hold && e.pointerId === hold.id && Math.abs(e.clientX - hold.x) + Math.abs(e.clientY - hold.y) > 24) stop();
    }, true);
    ["pointerup", "pointercancel"].forEach(function (n) {
      document.addEventListener(n, function (e) { if (hold && e.pointerId === hold.id) stop(); }, true);
    });
    addEventListener("blur", stop);
    document.addEventListener("visibilitychange", stop);
    document.addEventListener("click", function (e) {
      if (hold && hold.pouring) return;            // our own taps go through
      if (Date.now() < swallow && e.isTrusted) { swallow = 0; e.stopPropagation(); e.preventDefault(); }
    }, true);
    // a long press must not pop the phone's copy/save menu while pouring
    document.addEventListener("contextmenu", function (e) { if (hold || Date.now() < swallow) e.preventDefault(); }, true);
  })();
  addEventListener("DOMContentLoaded", function () {
    new MutationObserver(soon).observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ["class"] });
    soon();
  });
})();
</script>
</head>""", 1)

# in fullscreen, "full screen tall" is one camera strip shorter (see fsCut above)
h = h.replace("min-height: 100dvh", "min-height: calc(100dvh - var(--fsCut, 0px))")
# ---- win/loss pills read short once they're in the millions: +1.2B, +14T ----
_n = h.count('"+" + fmt(') + h.count('"−" + fmt(')
h = h.replace('"+" + fmt(', '"+" + pillAmt(').replace('"−" + fmt(', '"−" + pillAmt(')
h = h.replace('" — +" + fmt(net)', '" — +" + pillAmt(net)').replace('" — −" + fmt(-net)', '" — −" + pillAmt(-net)')
h = h.replace("  function shortAmt(v) {", "  function pillAmt(v) { return v >= 1e6 ? shortAmt(v) : fmt(v); }\n  function shortAmt(v) {", 1)
print("pills shortened:", _n)
assert h.count("/*SEATCSS*/") == 1
h = h.replace("/*SEATCSS*/", (here / "seat-fs.css").read_text() if SEAT else _OLDFS)
open(out, "w", encoding="utf-8").write(h)
print("built", out, len(h))
