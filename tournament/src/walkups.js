  /* ---------- walk-ups: people come up to the table asking for money (Play Testing only) ----------
   * Between hands someone walks up. Strangers spin a sad story and mostly
   * vanish with the cash. Friends want a spot to go play something else and
   * come back a few hands later up or down.
   */
  var Walkups = (function () {
    var FACES = /*FACES*/{};
    var TABLES = { holdem: "Holdem", djwild: "DjWild", shootout: "Shootout", miniuth: "MiniUth",
      iluvsuits: "IluvSuits", destroyer: "Destroyer", sicbo: "SicBo", roulette: "Roulette",
      wheel: "Wheel", keno: "Keno", mines: "Mines", blackjack: "Blackjack", doubledown: "DoubleDown",
      baccarat: "Baccarat", dragontiger: "DragonTiger", plinko: "Plinko", kirby: "Kirby" };
    function games() {
      return { Holdem: Holdem, DjWild: DjWild, Shootout: Shootout, MiniUth: MiniUth,
        IluvSuits: IluvSuits, Destroyer: Destroyer, SicBo: SicBo, Roulette: Roulette, Wheel: Wheel,
        Keno: Keno, Mines: Mines, Blackjack: Blackjack, DoubleDown: DoubleDown, Baccarat: Baccarat,
        DragonTiger: DragonTiger, Plinko: Plinko, Kirby: Kirby };
    }
    function pick(a) { return a[Math.floor(Math.random() * a.length)]; }
    function rnd(a, b) { return a + Math.random() * (b - a); }
    /** A believable ask: 1 or 2 significant figures, never under $20. */
    function nice(x) {
      x = Math.max(20, x);
      var p = Math.pow(10, Math.floor(Math.log10(x)) - 1);
      var v = Math.round(x / p) * p;
      if (v >= 100) v = Math.round(v / (p * 5)) * p * 5;
      return Math.max(20, v);
    }
    function $$(s) { return "$" + fmt(Math.round(s)); }

    /* Strangers: a friend's face, a stranger's name. odds = chance they're legit, back = what legit pays back. */
    var STRANGERS = [
      { face: "Grace", name: "Earl", odds: 0.40, back: [2, 3], cut: [0.02, 0.05],
        ask: ["Sorry to bother you… I lost my wallet and my grandson's in the hospital in Reno. Could you spare {amt} for the bus? I'll pay you back, I swear on my late wife.",
              "Son, I've been sitting here since noon. My truck got towed with my insulin in it. {amt} gets it out."],
        good: "Earl found you. \"Grandson's doing fine. A man keeps his word.\" He hands you {back}.",
        bad: "You see Earl at the craps table blowing on dice with your money. He winks." },
      { face: "Cougar", name: "Vinnie", odds: 0.15, back: [4, 6], cut: [0.05, 0.10],
        ask: ["Hey, hey, you look like a smart guy. I got a guy with a horse running at Santa Anita, can't lose. Put {amt} in, I triple it by tonight. Trust me.",
              "Listen. Crypto. My cousin's coin. Gets listed Friday. {amt} now and you're on the ground floor, baby."],
        good: "Vinnie's back, sweating. \"TOLD you. Horse came in at 9 to 1.\" He counts out {back}.",
        bad: "Vinnie's number is disconnected. The horse was named 'Lol Sucker'." },
      { face: "Zephyrox", name: "Marco", odds: 0.08, back: [3, 4], cut: [0.03, 0.08],
        ask: ["Bro… my little girl needs surgery and the insurance won't cover it. I just need {amt} more. Please. Anything.",
              "I'm not proud of this. My kid's sick and I got nothing left. {amt} would save my life."],
        good: "Marco tracks you down, crying. \"She's okay. Here, I hit a jackpot, half's yours.\" {back}.",
        bad: "Marco's at the bar ordering bottle service. His 'kid' is a 25-year-old named Brandi." },
      { face: "Chris", name: "Rick", odds: 0.25, back: [2, 2.5], cut: [0.02, 0.04],
        ask: ["Hey man, my car ran out of gas in the garage and my phone's dead. {amt} gets me home to Ohio. I'll Venmo you, what's your @?",
              "Bro I'm locked out of my room and the front desk wants a deposit. {amt}? I'm good for it, I'm in sales."],
        good: "Rick actually Venmos you. \"Told you I'm in sales.\" {back}.",
        bad: "Rick's Venmo is 'Rick-Not-Paying-U'. Gone." },
      { face: "Lyndsi", name: "Destiny", odds: 0.20, back: [2, 3], cut: [0.03, 0.06],
        ask: ["Hi! My friends ditched me and my card got declined. Could you cover {amt} for an Uber? I'll pay you right back, I promise!",
              "Omg you seem nice. I'm raising money for my sister's medical bills, it's a GoFundMe. {amt}?"],
        good: "Destiny comes back with the cash plus some. \"See? Not everyone's a scammer.\" {back}.",
        bad: "Destiny's 'Uber' was a limo to the club. With 4 other guys' money." },
      { face: "Lucy", name: "Kai", odds: 0.30, back: [2.5, 4], cut: [0.03, 0.07],
        ask: ["Brah, I got a system for roulette. Never loses. Front me {amt} and we split the take, I'm serious.",
              "Yo, I know the dealer at table 6, she's gonna 'slip' tonight. {amt} gets you in."],
        good: "Kai runs up yelling. \"THE SYSTEM WORKS!\" Your cut: {back}.",
        bad: "Security walks Kai out by both arms. Your money stays with the casino." }
    ];
    /* Friends: they borrow to go play something, then come back. */
    var FRIENDS = ["Qtip", "Justin", "Matthew", "Darin", "SJ", "Juice", "Avery", "Zac", "James", "Tristen"];
    /* Lines only one friend says, mixed in with the usual ones. */
    var OWN = {
      Tristen: {
        ask: ["Bro. Bad habits, but good intentions. Spot me {amt} for {game}.",
              "Listen, I KNOW how this looks. {amt}. {game}. Good intentions, bro.",
              "Tristen, eyes bloodshot: \"I'm locked in, bro. {amt} on {game}, trust.\""],
        lost: ["Tristen: \"Bad habits, bro. Bad habits.\" Your money's gone.",
               "Tristen's back holding a beer and no chips. \"Good intentions though.\""],
        win: ["Tristen's screaming. \"GOOD INTENTIONS PAY, BRO!\" {back}."]
      }
    };
    function own(who, key, base) { return lines(who, key, base); }
    function lines(who, key, base) { var o = OWN[who] && OWN[who][key]; return o && Math.random() < 0.6 ? o : base; }
    var SPOTS = ["blackjack", "craps", "roulette", "the slots", "baccarat", "Pai Gow", "the Money Wheel", "Ultimate Texas Hold'em"];
    var FRIEND_ASK = [
      "Bro spot me {amt}, I'm feeling {game} right now. I'll bring it back, swear.",
      "Yo let me get {amt}. {game} is HOT, I can feel it.",
      "Hey bro, front me {amt} real quick? I got a read on {game}.",
      "Dude I'm down bad. {amt} and I'll run it up at {game}. You'll get it back double.",
      "You trust me? {amt}. {game}. Don't ask questions."
    ];
    var FRIEND_WIN = [
      "{who} is back, yelling. \"I CALLED IT!\" Hit at {game}. Here's {back}.",
      "{who} slides you {back}. \"{game} paid. Told you, bro.\"",
      "{who} comes back grinning: \"Never doubt me.\" {back}."
    ];
    var FRIEND_BIG = [
      "{who} is SPRINTING over. \"BRO WE HIT, WE HIT AT {GAME}!\" Your cut: {back}.",
      "{who} drops a rack on your table. \"{GAME} WENT CRAZY.\" {back}!"
    ];
    var FRIEND_EVEN = [
      "{who} broke even at {game}. \"Here's your money back, bro. Respect.\" {back}.",
      "{who} hands back {back}. \"Didn't hit, but I'm not that guy.\""
    ];
    var FRIEND_LOST = [
      "{who} walks back slow. \"Bro… {game} took it all. I'll get you next time.\"",
      "{who} won't make eye contact. Lost it all at {game}.",
      "{who}: \"So the good news is I had fun.\" Your money's gone.",
      "{who} got it up to triple at {game}, then let it ride. Gone."
    ];
    var FRIEND_SNUB = [
      "{who} went anyway with his own money and hit at {game}. \"Should've backed me, bro.\"",
      "{who} lost at {game} anyway. \"Good call not backing me.\""
    ];

    /* Settings: who you are, so you never walk up to yourself. */
    var ME_KEY = "c4tb.walkme";
    function me() { try { return store.getStr(ME_KEY, ""); } catch (e) { return ""; } }
    function notMe(face) { return face !== me(); }
    function paintMe() {
      var host = document.querySelector("#screen-settings .st-dials");
      if (!host) return;
      var box = $("wu-me");
      if (!box) {
        box = document.createElement("div"); box.id = "wu-me"; box.className = "st-dial"; box.style.setProperty("--c", "#FFD24D");
        host.appendChild(box);
      }
      var mine = me(), people = Object.keys(FACES);
      box.innerHTML = '<i class="edge"></i><div class="hd"><span class="nm">WHO ARE YOU?</span><span class="pc">' +
        (mine ? mine.toUpperCase() : "NOBODY") + '</span></div><span class="sub">YOU WON\'T WALK UP TO YOURSELF AT THE TABLES</span>' +
        '<div class="wu-grid">' + people.map(function (n) {
          return '<button type="button" data-me="' + n + '" class="' + (n === mine ? "on" : "") + '"><img alt="" src="' + FACES[n] + '"><b>' + n + "</b></button>";
        }).join("") + '<button type="button" data-me="" class="' + (mine ? "" : "on") + '"><span class="wu-none">?</span><b>Nobody</b></button></div>';
      box.querySelectorAll("button[data-me]").forEach(function (b) {
        b.onclick = function () {
          try { store.setStr(ME_KEY, b.getAttribute("data-me")); } catch (e) {}
          deck = [];
          paintMe();
        };
      });
    }
    var card = null, timer = 0, poll = 0, table = null, wasBusy = false;
    var hands = 0, gap = 3, pending = [], offer = null, deck = [];

    function style() {
      if ($("wu-style")) return;
      var s = document.createElement("style"); s.id = "wu-style";
      s.textContent =
        "#wu-card{position:fixed;left:50%;top:calc(env(safe-area-inset-top,0px) + 64px);transform:translate(-50%,-140%);z-index:9998;" +
        "width:min(94vw,460px);box-sizing:border-box;display:flex;gap:12px;align-items:flex-start;padding:12px;border-radius:16px;" +
        "background:rgba(12,10,22,.96);border:1.5px solid #FFD24D;box-shadow:0 10px 30px rgba(0,0,0,.6);" +
        "color:#F4EEDC;font:500 14px/1.35 system-ui,sans-serif;transition:transform .35s cubic-bezier(.2,.9,.3,1.2)}" +
        "#wu-card.on{transform:translate(-50%,0)}" +
        "#wu-card.friend{border-color:#3FA9FF}#wu-card.good{border-color:#57E06A}#wu-card.bad{border-color:#FF5E5E}" +
        "#wu-card img{width:58px;height:58px;border-radius:50%;object-fit:cover;flex:none;border:2px solid currentColor;color:#FFD24D}" +
        "#wu-card.friend img{color:#3FA9FF}#wu-card.good img{color:#57E06A}#wu-card.bad img{color:#FF5E5E}" +
        "#wu-card .wu-b{flex:1;min-width:0}" +
        "#wu-card .wu-n{font:800 13px/1.2 system-ui,sans-serif;letter-spacing:.1em;color:#FFD24D;margin-bottom:3px}" +
        "#wu-card.friend .wu-n{color:#7CC4FF}#wu-card.good .wu-n{color:#57E06A}#wu-card.bad .wu-n{color:#FF8080}" +
        "#wu-card .wu-row{display:flex;gap:8px;margin-top:10px}" +
        "#wu-card button{flex:1;height:38px;border-radius:10px;font:800 13px/1 system-ui,sans-serif;letter-spacing:.08em;cursor:pointer}" +
        "#wu-card .wu-yes{border:1.5px solid #57E06A;background:rgba(87,224,106,.15);color:#8BF09A}" +
        "#wu-card .wu-no{border:1.5px solid rgba(244,238,220,.35);background:rgba(0,0,0,.3);color:#F4EEDC}" +
        "#wu-card .wu-yes:disabled{opacity:.4}" +
        "#wu-me .wu-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin-top:10px}" +
        "#wu-me .wu-grid button{display:flex;flex-direction:column;align-items:center;gap:3px;padding:4px 0;border:0;background:none;color:#F4EEDC;cursor:pointer}" +
        "#wu-me .wu-grid img,#wu-me .wu-none{width:52px;height:52px;border-radius:50%;object-fit:cover;border:2px solid rgba(244,238,220,.25);box-sizing:border-box}" +
        "#wu-me .wu-none{display:flex;align-items:center;justify-content:center;font:800 22px system-ui;color:rgba(244,238,220,.6);background:rgba(0,0,0,.35)}" +
        "#wu-me .wu-grid b{font:700 10px/1.1 system-ui,sans-serif;letter-spacing:.04em;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}" +
        "#wu-me .wu-grid button.on img,#wu-me .wu-grid button.on .wu-none{border-color:#FFD24D;box-shadow:0 0 12px rgba(255,210,77,.6)}" +
        "#wu-me .wu-grid button.on b{color:#FFD24D}";
      document.head.appendChild(s);
    }
    var lastAuto = null;
    function hideCard() {
      clearTimeout(timer); lastAuto = null;
      if (!card) return;
      card.classList.remove("on");
      // gone for good once it has slid away, even if the phone froze the slide
      var c = card;
      setTimeout(function () { if (!c.classList.contains("on")) c.style.display = "none"; }, 450);
    }
    // the phone put the game away: stop the clock, and on the way back
    // finish whatever the card was doing instead of leaving it half way
    document.addEventListener("visibilitychange", function () {
      if (!card) return;
      if (document.hidden) { clearTimeout(timer); return; }
      if (!card.classList.contains("on")) { card.style.display = "none"; return; }
      card.style.transition = "none"; card.style.transform = "translate(-50%,0)";
      void card.offsetWidth; card.style.transition = ""; card.style.transform = "";
      if (lastAuto) { clearTimeout(timer); timer = setTimeout(lastAuto.fn, lastAuto.ms); }
    });
    /** kind: "" stranger ask, "friend" ask, "good" money back, "bad" money gone */
    function show(kind, face, name, text, buttons, ms) {
      style();
      if (!card) {
        card = document.createElement("div"); card.id = "wu-card";
        document.body.appendChild(card);
      }
      clearTimeout(timer);
      card.style.display = "flex";
      card.className = kind;
      card.innerHTML = '<img alt=""><div class="wu-b"><div class="wu-n"></div><div class="wu-t"></div>' +
        (buttons ? '<div class="wu-row"><button class="wu-yes"></button><button class="wu-no">NAH</button></div>' : "") + "</div>";
      card.querySelector("img").src = FACES[face] || "";
      card.querySelector(".wu-n").textContent = name.toUpperCase();
      card.querySelector(".wu-t").textContent = text;
      if (buttons) {
        var y = card.querySelector(".wu-yes");
        y.textContent = buttons.yes;
        y.disabled = !!buttons.cant;
        y.onclick = function (e) { e.stopPropagation(); hideCard(); buttons.onYes(); };
        card.querySelector(".wu-no").onclick = function (e) { e.stopPropagation(); hideCard(); buttons.onNo(); };
        card.onclick = null;
      } else card.onclick = hideCard;
      requestAnimationFrame(function () { requestAnimationFrame(function () { card.classList.add("on"); }); });
      var fn = function () { hideCard(); if (buttons) buttons.onNo(true); };
      lastAuto = { fn: fn, ms: ms || (buttons ? 25000 : 6000) };
      timer = setTimeout(fn, lastAuto.ms);
    }
    function cash() { try { return table && table.cash ? table.cash() : 0; } catch (e) { return 0; } }
    function money(x) {
      if (!table || !table.credit) return false;
      table.credit(x);
      return true;
    }
    function fill(t, o) {
      return up(t.replace(/\{(\w+)\}/g, function (_, k) {
        if (k === "GAME") return String(o.game || "").toUpperCase();
        return o[k] != null ? o[k] : "";
      }));
    }
    // a game name that starts a sentence (or a quote) gets a capital
    function up(t) { return t.replace(/(^|[.!?] |")([a-z])/g, function (_, a, c) { return a + c.toUpperCase(); }); }

    function strangerOffer(s) {
      s = s || pick(STRANGERS.filter(function (x) { return notMe(x.face); })); if (!s || !notMe(s.face)) return false; var br = cash();
      var amt = nice(br * rnd(s.cut[0], s.cut[1]));
      if (amt > br) return false;
      var o = { amt: $$(amt) };
      offer = s;
      show("", s.face, s.name, fill(pick(s.ask), o), {
        yes: "GIVE " + $$(amt), cant: amt > cash(),
        onYes: function () {
          offer = null;
          if (amt > cash() || !money(-amt)) return;
          var legit = Math.random() < s.odds;
          pending.push({ in: Math.round(rnd(3, 7)), who: s, amt: amt, legit: legit, stranger: true });
        },
        onNo: function () { offer = null; }
      });
      return true;
    }
    function friendOffer(forced) {
      var busyNames = pending.map(function (p) { return p.name; });
      var free = FRIENDS.filter(function (n) { return busyNames.indexOf(n) < 0 && notMe(n); });
      if (!free.length) return false;
      if (forced && free.indexOf(forced) < 0) return false;   // still out playing with your money
      var who = forced || pick(free), game = pick(SPOTS), br = cash();
      var amt = nice(br * rnd(0.04, 0.12));
      if (amt > br) return false;
      offer = who;
      show("friend", who, who, fill(pick(lines(who, "ask", FRIEND_ASK)), { amt: $$(amt), game: game }), {
        yes: "SPOT " + $$(amt), cant: amt > cash(),
        onYes: function () {
          offer = null;
          if (amt > cash() || !money(-amt)) return;
          pending.push({ in: Math.round(rnd(3, 6)), name: who, game: game, amt: amt });
        },
        onNo: function (timedOut) {
          offer = null;
          // now and then you hear how it went anyway
          if (!timedOut && Math.random() < 0.35)
            pending.push({ in: Math.round(rnd(3, 6)), name: who, game: game, amt: 0, snub: true });
        }
      });
      return true;
    }
    /** Friends: 40% win (1.5–2.5×), 8% big (4–6×), 15% pay it back, 37% lost it. */
    function settle(p) {
      if (p.stranger) {
        var s = p.who;
        if (p.legit) {
          var back = Math.round(p.amt * rnd(s.back[0], s.back[1]));
          money(back);
          show("good", s.face, s.name, fill(s.good, { back: $$(back) }));
        } else show("bad", s.face, s.name, s.bad);
        return;
      }
      var o = { who: p.name, game: p.game };
      if (p.snub) { show("friend", p.name, p.name, fill(pick(FRIEND_SNUB), o)); return; }
      var r = Math.random(), back = 0, lines;
      if (r < 0.08) { back = p.amt * rnd(4, 6); lines = FRIEND_BIG; }
      else if (r < 0.48) { back = p.amt * rnd(1.5, 2.5); lines = own(p.name, "win", FRIEND_WIN); }
      else if (r < 0.63) { back = p.amt; lines = FRIEND_EVEN; }
      else lines = own(p.name, "lost", FRIEND_LOST);
      back = Math.round(back);
      o.back = $$(back);
      if (back > 0) money(back);
      show(back > 0 ? "good" : "bad", p.name, p.name, fill(pick(lines), o));
    }

    /** A hand just finished at this table. */
    function handOver() {
      hands++;
      if (card && card.classList.contains("on")) return;
      pending.forEach(function (p) { p.in--; });
      var due = null;
      for (var i = 0; i < pending.length; i++) if (pending[i].in <= 0) { due = pending.splice(i, 1)[0]; break; }
      if (due) { settle(due); hands = 0; return; }
      if (cash() < 50) return;
      // testing: someone every hand, working through everybody before anyone repeats
      if (!deck.length) deck = FRIENDS.filter(notMe).map(function (n) { return { f: n }; })
        .concat(STRANGERS.filter(function (x) { return notMe(x.face); }).map(function (x) { return { s: x }; })).sort(function () { return Math.random() - 0.5; });
      var nx = deck.shift();
      var ok = nx.f ? friendOffer(nx.f) : strangerOffer(nx.s);
      if (!ok) deck.push(nx);
      if (ok) { hands = 0; gap = Math.round(rnd(3, 6)); }
    }
    function on() { return Shell.mode === "testing" && !(typeof TT !== "undefined" && TT.on); }
    function sit(screenName) {
      clearInterval(poll); table = null; wasBusy = false;
      if (!on()) return;
      var t = games()[TABLES[screenName]];
      if (!t || !t.busy || !t.credit || !t.cash) return;
      table = t;
      poll = setInterval(function () {
        if (!on()) return;
        var b = !!table.busy();
        if (wasBusy && !b) setTimeout(handOver, 900);
        wasBusy = b;
      }, 250);
    }
    var showScreen = Shell.show;
    Shell.show = function (name) {
      var r = showScreen.apply(this, arguments);
      if (name === "settings") { style(); paintMe(); }
      if (TABLES[name]) sit(name);
      else { clearInterval(poll); table = null; if (offer) { offer = null; } hideCard(); }
      return r;
    };
    return { _force: function (kind) { return kind === "friend" ? friendOffer() : strangerOffer(); },
             _pending: function () { return pending; }, _settleAll: function () { pending.forEach(function (p) { p.in = 0; }); } };
  })();
