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
          <button type="button" id="tt-boardbtn">BOARD</button>
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
    '  <div class="mm-col">\n    <div class="mm-sign">\n      <div class="mm-name">Casino Games</div>\n'
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
    "      if (TT.on && (TT.closed || TT.isOut())) return false;\n")
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
          <span class="l1">DISC GOLF SLOT · 25 LINES</span>
          <span class="l2">PLAY TESTING ONLY · PUTT FOR THE ACE</span>
        </span>
        <span class="play">PLAY</span>
      </button>
    </div>""")
sub("  paintMenu();\n  /* ---------- sound",
    (here / "chain.js").read_text() + "  paintMenu();\n  /* ---------- sound")

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
    body = body[:r] + "\n    return { busy: function () { return %s; }," % (test or BUSY_DEFAULT) + body[r + len("\n    return {"):]
    h = h[:a] + body + h[b:]

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

open(out, "w", encoding="utf-8").write(h)
print("built", out, len(h))
