"""Builds index.html: the original game plus the tournament.

python3 src/build.py <original.html> <out.html> [supabase_url] [anon_key]
"""
import sys, json, pathlib, re

src, out = sys.argv[1], sys.argv[2]
url = sys.argv[3] if len(sys.argv) > 3 else ""
key = sys.argv[4] if len(sys.argv) > 4 else ""
here = pathlib.Path(__file__).parent
# the version on the front-page sign: bump it with each update that goes out
VERSION = "1.21"
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
        <div class="mm-hdr"><span class="nm">TOURNAMENT</span><span class="tag" id="tt-tag">THIS WEEK</span></div>
        <div class="tt-line" id="tt-line">CONNECTING…</div>
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
    '  <div class="mm-col">\n    <div class="mm-sign">\n      <div class="mm-name">Casino Games<span class="mm-ver">v' + VERSION + '</span></div>\n'
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
        (fp.day_no === 7 ? "FINAL DAY" : "DAY " + (fp.day_no || 1)) + " · POINTS";
      host.querySelector(".v").textContent = "$10,000 A GAME";
      host.querySelector(".g").textContent = "MOST MONEY ON A GAME AT MIDNIGHT TAKES ITS POINTS";
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
    var u = [[1e12, "T"], [1e9, "B"], [1e6, "M"], [1e3, "K"]];
    for (var i = 0; i < u.length; i++) if (v >= u[i][0]) {
      var x = v / u[i][0], d = x >= 100 ? 0 : x >= 10 ? 1 : 2;
      var t = (Math.floor(x * Math.pow(10, d) + 1e-6) / Math.pow(10, d)).toFixed(d);
      if (d) t = t.replace(/0+$/, "").replace(/\\.$/, "");
      return t + u[i][1];
    }
    return fmt(v);
  }
  function chipHtml(amount, size) {""")

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
    "  paintMenu();\n" + (here / "tourney.js").read_text() + "  /* ---------- sound")

# ---- big bankrolls: the money bar shrinks to fit the screen instead of pushing the page wider ----
# (a page wider than the phone makes the browser zoom out, and it comes back from texting the wrong size)
h = h.replace("</head>", """<style>html, body { overflow-x: hidden; max-width: 100%; }</style>
<script>
(function () {
  var busy = false;
  function fit() {
    busy = false;
    var pad = 8;
    document.querySelectorAll(".purse").forEach(function (el) {
      if (!el.offsetParent) return;
      // a billion and up reads short up top: 148.5B, 40.47B, 1.15B, 3.2T
      var w = document.createTreeWalker(el, NodeFilter.SHOW_TEXT), n;
      while ((n = w.nextNode())) {
        var m = /^(\\s*)(-?)\\$?([\\d,]+)(\\s*)$/.exec(n.nodeValue);
        if (!m) continue;
        var v = +m[3].replace(/,/g, "");
        if (v < 1e9) continue;
        var u = v >= 1e12 ? [1e12, "T"] : [1e9, "B"], x = v / u[0], d = x >= 100 ? 1 : 2;
        var t = (Math.floor(x * Math.pow(10, d) + 1e-6) / Math.pow(10, d)).toFixed(d).replace(/0+$/, "").replace(/\\.$/, "");
        n.nodeValue = m[1] + m[2] + t + u[1] + m[4];
      }
      el.style.zoom = "";
      var r = el.getBoundingClientRect();
      if (r.right <= innerWidth - pad || r.width <= 0) return;
      var room = innerWidth - pad - r.left;
      el.style.zoom = String(Math.max(0.55, Math.floor(room / r.width * 100) / 100));
    });
  }
  function soon() { if (!busy) { busy = true; requestAnimationFrame(fit); } }
  addEventListener("resize", soon);
  document.addEventListener("visibilitychange", soon);
  addEventListener("DOMContentLoaded", function () {
    new MutationObserver(soon).observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ["class"] });
    soon();
  });
})();
</script>
</head>""", 1)

# ---- win/loss pills read short once they're in the millions: +1.2B, +14T ----
_n = h.count('"+" + fmt(') + h.count('"−" + fmt(')
h = h.replace('"+" + fmt(', '"+" + pillAmt(').replace('"−" + fmt(', '"−" + pillAmt(')
h = h.replace('" — +" + fmt(net)', '" — +" + pillAmt(net)').replace('" — −" + fmt(-net)', '" — −" + pillAmt(-net)')
h = h.replace("  function shortAmt(v) {", "  function pillAmt(v) { return v >= 1e6 ? shortAmt(v) : fmt(v); }\n  function shortAmt(v) {", 1)
print("pills shortened:", _n)
open(out, "w", encoding="utf-8").write(h)
print("built", out, len(h))
