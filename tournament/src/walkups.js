  /* ---------- walk-ups: people come up to the table asking for money (everywhere, about 1 hand in 10) ----------
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

    /* Hustles: anyone can run any of these. odds = chance it's legit, back = what legit pays back. */
    var SCAMS = [
      { odds: 0.40, back: [2, 3], cut: [0.02, 0.05],
        ask: "{who} looks rough. \"I lost my wallet and my grandma's in the hospital in Reno. {amt} for the bus? I swear I'll pay you back.\"",
        good: "{who} found you. \"Grandma's doing fine. I keep my word.\" {back}.",
        bad: "You spot {who} at the craps table blowing on dice with your money. A wink." },
      { odds: 0.15, back: [4, 6], cut: [0.05, 0.10],
        ask: "{who} leans in. \"I got a guy with a horse running at Santa Anita. Can't lose. {amt} in, I triple it by tonight. Trust me.\"",
        good: "{who}'s back, sweating. \"TOLD you. Came in at 9 to 1.\" {back}.",
        bad: "{who}'s number is disconnected. The horse was named 'Lol Sucker'." },
      { odds: 0.10, back: [3, 5], cut: [0.05, 0.10],
        ask: "\"Listen. Crypto. My cousin's coin, gets listed Friday. {amt} now and you're on the ground floor, baby.\" {who} is dead serious.",
        good: "{who} runs over. \"IT MOONED.\" {back}.",
        bad: "{who}'s coin is now worth $0.0000003. \"We're early, bro.\"" },
      { odds: 0.08, back: [3, 4], cut: [0.03, 0.08],
        ask: "{who}, teary-eyed: \"My kid needs surgery and insurance won't cover it. I just need {amt} more. Please.\"",
        good: "{who} tracks you down, crying. \"Kid's okay. Hit a jackpot, half's yours.\" {back}.",
        bad: "{who}'s at the bar ordering bottle service. There is no kid." },
      { odds: 0.25, back: [2, 2.5], cut: [0.02, 0.04],
        ask: "\"Car's out of gas in the garage and my phone's dead. {amt} gets me home. I'll Venmo you, what's your @?\" asks {who}.",
        good: "{who} actually Venmos you. {back}.",
        bad: "{who}'s Venmo is '{who}-Not-Paying-U'. Gone." },
      { odds: 0.30, back: [2, 3], cut: [0.02, 0.05],
        ask: "{who}: \"Locked out of my room and the front desk wants a deposit. {amt}? I'm good for it.\"",
        good: "{who} comes back with room service and your money. {back}.",
        bad: "{who} never had a room. Slept in the sportsbook with your money." },
      { odds: 0.20, back: [2, 3], cut: [0.03, 0.06],
        ask: "\"My card got declined and my ride left me. {amt} for an Uber? I'll pay you right back, promise!\" says {who}.",
        good: "{who} comes back with the cash plus some. \"See? Not everyone's a scammer.\" {back}.",
        bad: "{who}'s 'Uber' was a limo to the club." },
      { odds: 0.15, back: [3, 4], cut: [0.03, 0.06],
        ask: "{who} holds up a phone. \"It's a GoFundMe for my sister's medical bills. {amt}? Every bit helps.\"",
        good: "{who}: \"We hit the goal! Here's yours back, with interest.\" {back}.",
        bad: "The GoFundMe was for {who}'s jet ski." },
      { odds: 0.30, back: [2.5, 4], cut: [0.03, 0.07],
        ask: "{who}: \"I got a system for roulette. Never loses. Front me {amt} and we split the take.\"",
        good: "{who} runs up yelling. \"THE SYSTEM WORKS!\" Your cut: {back}.",
        bad: "Security walks {who} out by both arms. Your money stays with the casino." },
      { odds: 0.25, back: [3, 4], cut: [0.04, 0.08],
        ask: "{who} whispers: \"I know the dealer on table 6. They're gonna 'slip' tonight. {amt} gets you in.\"",
        good: "{who} slides you a fat envelope. {back}. \"You didn't get this from me.\"",
        bad: "The dealer on table 6 got fired. So did your money." },
      { odds: 0.05, back: [5, 8], cut: [0.03, 0.06],
        ask: "{who} has a Rolex in a ziplock bag. \"Real. Swear on my mother. {amt}, it's yours.\"",
        good: "Holy. It's real. You flip it at the gift shop for {back}.",
        bad: "The Rolex says 'Rollex'. {who} is gone." },
      { odds: 0.20, back: [2, 3], cut: [0.02, 0.05],
        ask: "{who}: \"Bro I got a parking ticket and they're towing my car RIGHT NOW. {amt}, quick!\"",
        good: "{who} pays you back. \"You saved my life.\" {back}.",
        bad: "{who} doesn't own a car." },
      { odds: 0.35, back: [2, 3], cut: [0.03, 0.06],
        ask: "{who}: \"I got tickets to the fight tonight, front row. Front me {amt} and I'll sell them for double, we split it.\"",
        good: "{who} sold them to a whale. Your half: {back}.",
        bad: "The tickets were for a fight last year." },
      { odds: 0.12, back: [3, 5], cut: [0.04, 0.08],
        ask: "{who}: \"I'm starting a food truck. Disc golf themed. {amt} and you're a partner.\"",
        good: "The food truck is a hit. Your dividend from {who}: {back}.",
        bad: "{who} bought a truck. No food. No permit. It got towed." },
      { odds: 0.40, back: [1.5, 2.5], cut: [0.01, 0.03],
        ask: "{who}: \"Hey, I left my wallet in the room and I'm STARVING. {amt} for the buffet? I'll get you back.\"",
        good: "{who} comes back full and pays you. {back}.",
        bad: "{who} spent it on a dozen shrimp cocktails and forgot you exist." }
    ];
    /* Everyone: each walk-up is anybody, asking for a spot or running a hustle. */
    var FRIENDS = ["Qtip", "Justin", "Matthew", "Darin", "SJ", "Juice", "Chris", "Avery", "Zac", "James",
                   "Lyndsi", "Cougar", "Grace", "Zephyrox", "Lucy", "Tristen"];
    /* Tristen only ever wants two things: blackjack money, or this. */
    var TRISTEN_SCAM = { odds: 0.35, back: [2, 3], cut: [0.03, 0.07],
      ask: "Tristen, dead serious: \"Bro. The gift shop has a GIANT dildo. Like, huge. I need it. {amt} and I pay you back tonight, swear.\"",
      good: "Tristen walks up with the giant dildo under his arm AND your money. \"Worth every penny, bro.\" {back}.",
      bad: "Tristen bought the giant dildo. You're not getting paid back. He's never been happier." };
    /* Lines only one friend says, mixed in with the usual ones. */
    var OWN = {
      Tristen: {
        ask: ["Bro. Bad habits, but good intentions. Spot me {amt} for blackjack.",
              "Listen, I KNOW how this looks. {amt}. Blackjack. Good intentions, bro.",
              "Tristen, eyes bloodshot: \"I'm locked in, bro. {amt} on blackjack, trust.\""],
        lost: ["Tristen: \"Bad habits, bro. Bad habits.\" Your money's gone.",
               "Tristen's back holding a beer and no chips. \"Good intentions though.\""],
        win: ["Tristen's screaming. \"GOOD INTENTIONS PAY, BRO!\" Hit for {won}, your half: {back}."]
      }
    };
    function own(who, key, base) { return lines(who, key, base); }
    function lines(who, key, base) { var o = OWN[who] && OWN[who][key]; return o && (key === "ask" || Math.random() < 0.6) ? o : base; }
    var SPOTS = ["blackjack", "craps", "roulette", "the slots", "baccarat", "Pai Gow", "the Money Wheel", "Ultimate Texas Hold'em"];
    var FRIEND_ASK = [
      "Bro spot me {amt}, I'm feeling {game} right now. I'll bring it back, swear.",
      "Yo let me get {amt}. {game} is HOT, I can feel it.",
      "Hey bro, front me {amt} real quick? I got a read on {game}.",
      "Dude I'm down bad. {amt} and I'll run it up at {game}. You'll get it back double.",
      "You trust me? {amt}. {game}. Don't ask questions."
    ];
    var FRIEND_WIN = [
      "{who} is back, yelling. \"I CALLED IT!\" Ran it up to {won} at {game}. Split 50/50: {back}.",
      "{who} slides you {back}. \"{game} paid {won}. Half's yours, bro.\"",
      "{who} comes back grinning: \"Never doubt me.\" Turned it into {won}, you get half: {back}."
    ];
    var FRIEND_BIG = [
      "{who} is SPRINTING over. \"BRO WE HIT, WE HIT AT {GAME}!\" {won}! Your half: {back}.",
      "{who} drops a rack on your table. \"{GAME} WENT CRAZY.\" {won}, split down the middle: {back}!"
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
    /* Until you've picked who you are, the menus ask (once per visit, never at a table). */
    var nagged = false;
    function nag(screenName) {
      if (nagged || me() || TABLES[screenName] || screenName === "settings") return;
      nagged = true;
      setTimeout(function () {
        var on = document.querySelector(".screen.on");
        if (me() || !on || TABLES[on.id.replace("screen-", "")] || on.id === "screen-settings") { nagged = false; return; }
        var faces = Object.keys(FACES).slice(0, 6).map(function (n) { return '<img alt="" src="' + FACES[n] + '">'; }).join("");
        show("friend", "", "NEW · WHO ARE YOU?", "", {
          yes: "PICK ME", onYes: function () { Shell.show("settings"); }, onNo: function () {}
        }, 60000);
        card.querySelector("img").remove();
        card.querySelector(".wu-t").innerHTML = 'The boys now walk up at the tables asking for money. ' +
          'Lock in who you are in <b>Settings</b> so you never get hustled by yourself.' +
          '<div class="wu-strip">' + faces + "</div>";
        card.querySelector(".wu-no").textContent = "LATER";
      }, 1200);
    }
    var card = null, timer = 0, poll = 0, table = null, wasBusy = false;
    var hands = 0, gap = Math.round(rnd(7, 13)), pending = [], offer = null, deck = [];
    /* Money out with somebody is kept on the phone, one list per wallet
       (campaign, play testing, each tournament day), so a reload doesn't lose it. */
    var pkey = "";
    function walletKey() {
      if (typeof TT !== "undefined" && TT.on) return "tt." + TT.week + "." + (TT.day || "");
      return Shell.mode;
    }
    function PUSH(p) { pending.push(p); savePending(); }
    function savePending() {
      if (!pkey) return;
      try {
        store.setStr("c4tb.wu." + pkey, JSON.stringify(pending.map(function (p) {
          var o = {}; for (var k in p) o[k] = p[k];
          if (p.sc) { o.sc = p.sc === TRISTEN_SCAM ? -1 : SCAMS.indexOf(p.sc); }
          return o;
        })));
      } catch (e) {}
    }
    function loadPending() {
      var k = walletKey();
      if (k === pkey) return;
      pkey = k;
      try {
        pending = JSON.parse(store.getStr("c4tb.wu." + k, "[]") || "[]").map(function (p) {
          if (p.sc != null) p.sc = p.sc === -1 ? TRISTEN_SCAM : SCAMS[p.sc];
          return p;
        }).filter(function (p) { return !p.stranger || p.sc; });
      } catch (e) { pending = []; }
    }

    function style() {
      if ($("wu-style")) return;
      var s = document.createElement("style"); s.id = "wu-style";
      s.textContent =
        "#wu-card{position:fixed;left:50%;top:calc(env(safe-area-inset-top,0px) + 64px);transform:translate(-50%,-140%);z-index:9998;" +
        "width:min(94vw,460px);box-sizing:border-box;display:flex;gap:12px;align-items:flex-start;padding:12px;border-radius:16px;" +
        "background:rgba(12,10,22,.96);border:1.5px solid #FFD24D;box-shadow:0 10px 30px rgba(0,0,0,.6);" +
        "color:#F4EEDC;font:500 14px/1.35 system-ui,sans-serif;transition:transform .35s cubic-bezier(.2,.9,.3,1.2)}" +
        "#wu-card.on{transform:translate(-50%,0)}" +
        "#wu-card.guard{border-color:#FFD24D;background:rgba(16,22,36,.97)}#wu-card.guard .wu-n{color:#FFD24D}#wu-card.guard img{color:#FFD24D}" +
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
        "#wu-card .wu-strip{display:flex;margin-top:8px}#wu-card .wu-strip img{width:30px;height:30px;margin-right:-6px;border-width:1.5px;color:#0C0A16}" +
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
    /* Sounds: a happy little jingle when money comes back with profit, womp womp when it doesn't. */
    var actx = null;
    function audio() {
      try {
        if (!actx) actx = new (window.AudioContext || window.webkitAudioContext)();
        if (actx.state === "suspended") actx.resume();
      } catch (e) { actx = null; }
      return actx;
    }
    function vol() { return typeof SOUND !== "undefined" ? SOUND.sfx : 0.4; }
    function tone(type, freqs, start, len, gain, slide) {
      var a = audio(); if (!a || !vol()) return;
      var t = a.currentTime + start, o = a.createOscillator(), g = a.createGain(), f = a.createBiquadFilter();
      o.type = type; f.type = "lowpass"; f.frequency.value = type === "sawtooth" ? 900 : 6000;
      o.frequency.setValueAtTime(freqs, t);
      if (slide) o.frequency.linearRampToValueAtTime(slide, t + len);
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(gain * vol(), t + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, t + len);
      o.connect(f); f.connect(g); g.connect(a.destination);
      o.start(t); o.stop(t + len + 0.05);
      return o;
    }
    function jingle() {
      // C E G C, then a sparkle on top
      [523.25, 659.25, 783.99, 1046.5].forEach(function (f, i) { tone("triangle", f, i * 0.09, 0.22, 0.5); tone("square", f * 2, i * 0.09, 0.08, 0.06); });
      tone("triangle", 1318.5, 0.38, 0.4, 0.35);
      tone("triangle", 1567.98, 0.46, 0.5, 0.3);
    }
    function womp() {
      // the sad trombone: three steps down, then the long droop with a wobble
      [[293.66, 0], [277.18, 0.36], [261.63, 0.72]].forEach(function (n) { tone("sawtooth", n[0], n[1], 0.32, 0.4, n[0] * 0.98); });
      var o = tone("sawtooth", 246.94, 1.08, 1.1, 0.45, 220);
      var a = actx;
      if (o && a) {
        var lfo = a.createOscillator(), d = a.createGain();
        lfo.frequency.value = 6; d.gain.value = 7;
        lfo.connect(d); d.connect(o.frequency);
        lfo.start(a.currentTime + 1.08); lfo.stop(a.currentTime + 2.25);
      }
    }
    // phones only let sound start from a tap: wake it on the first one
    document.addEventListener("pointerdown", function () { audio(); }, { once: true, capture: true });
    function cash() { try { return table && table.cash ? table.cash() : 0; } catch (e) { return 0; } }
    function money(x) {
      if (!table || !table.credit) return false;
      var tt = typeof TT !== "undefined" && TT.on;
      // in the tournament it's the day's money but not a hand: keep it out of the hot-game count
      if (tt) TT.quiet = true;
      try { table.credit(x); } finally { if (tt) TT.quiet = false; }
      if (tt && TT.changed) TT.changed(true);
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

    function strangerOffer(who) {
      var busyNames = pending.map(function (p) { return p.name; });
      if (!who || !notMe(who) || busyNames.indexOf(who) >= 0) return false;
      var sc = who === "Tristen" ? TRISTEN_SCAM : pick(SCAMS), br = cash();
      var amt = nice(br * rnd(sc.cut[0], sc.cut[1]));
      if (amt > br) return false;
      offer = who;
      show("", who, who, fill(sc.ask, { amt: $$(amt), who: who }), {
        yes: "GIVE " + $$(amt), cant: amt > cash(),
        onYes: function () {
          offer = null;
          if (amt > cash() || !money(-amt)) return;
          var legit = Math.random() < sc.odds;
          PUSH({ in: Math.round(rnd(legit ? 3 : 4, 7)), name: who, sc: sc, amt: amt, legit: legit, stranger: true });
          // the cameras saw it: two hands later security has a word
          if (!legit) PUSH({ in: 2, name: "", guard: true, who: who, amt: amt });
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
      var who = forced || pick(free), game = who === "Tristen" ? "blackjack" : pick(SPOTS), br = cash();
      var amt = nice(br * rnd(0.04, 0.12));
      if (amt > br) return false;
      offer = who;
      show("friend", who, who, fill(pick(lines(who, "ask", FRIEND_ASK)), { amt: $$(amt), game: game }), {
        yes: "SPOT " + $$(amt), cant: amt > cash(),
        onYes: function () {
          offer = null;
          if (amt > cash() || !money(-amt)) return;
          PUSH({ in: Math.round(rnd(3, 6)), name: who, game: game, amt: amt });
        },
        onNo: function (timedOut) {
          offer = null;
          // now and then you hear how it went anyway
          if (!timedOut && Math.random() < 0.35)
            PUSH({ in: Math.round(rnd(3, 6)), name: who, game: game, amt: 0, snub: true });
        }
      });
      return true;
    }
    /** Friends: 40% win (1.5–2.5×), 8% big (4–6×), 15% pay it back, 37% lost it. */
    function settle(p) {
      if (p.guard) {
        show("guard", "", "CASINO SECURITY", "\"Sir, we noticed on the cameras that you loaned " + $$(p.amt) + " to " + p.who +
          ". That was a scam. Please don't give money to strangers, it's usually a scam.\"", null, 9000);
        card.querySelector("img").src = FACES.Justin;   // security is always Justin
        return;
      }
      if (p.stranger) {
        var sc = p.sc;
        if (p.legit) {
          var bk = Math.round(p.amt * rnd(sc.back[0], sc.back[1]));
          money(bk);
          show("good", p.name, p.name, fill(sc.good, { back: $$(bk), who: p.name }));
          jingle();
        } else { show("bad", p.name, p.name, fill(sc.bad, { who: p.name })); womp(); }
        return;
      }
      var o = { who: p.name, game: p.game };
      if (p.snub) { show("friend", p.name, p.name, fill(pick(FRIEND_SNUB), o)); return; }
      var r = Math.random(), back = 0, lines;
      // a friend who wins splits it with you 50/50: half of what your money turned into
      var won = 0;
      if (r < 0.07) { won = nice(p.amt * rnd(10, 16)); lines = FRIEND_BIG; }
      else if (r < 0.42) { won = nice(p.amt * rnd(4, 8)); lines = own(p.name, "win", FRIEND_WIN); }
      else if (r < 0.57) { back = p.amt; lines = FRIEND_EVEN; }
      else lines = own(p.name, "lost", FRIEND_LOST);
      if (won) back = won / 2;
      back = Math.round(back);
      o.won = $$(won);
      o.back = $$(back);
      if (back > 0) money(back);
      show(back > 0 ? "good" : "bad", p.name, p.name, fill(pick(lines), o));
      if (back > 0) jingle(); else womp();
    }

    /** A hand just finished at this table. */
    function handOver() {
      hands++;
      if (card && card.classList.contains("on")) return;
      pending.forEach(function (p) { p.in--; });
      var due = null;
      // security always gets there before the scam plays out
      var at = -1;
      for (var i = 0; i < pending.length; i++) if (pending[i].in <= 0 && (at < 0 || (pending[i].guard && !pending[at].guard))) at = i;
      if (at >= 0) due = pending.splice(at, 1)[0];
      savePending();
      if (due) { settle(due); return; }
      if (cash() < 50 || hands < gap) return;
      // about one hand in ten, working through everybody before anyone repeats
      if (!deck.length) deck = FRIENDS.filter(notMe).sort(function () { return Math.random() - 0.5; });
      var nx = deck.shift();
      var ok = Math.random() < 0.5 ? friendOffer(nx) : strangerOffer(nx);
      if (!ok) deck.push(nx);
      if (ok) { hands = 0; gap = Math.round(rnd(7, 13)); }
    }
    function on() { return !(typeof TT !== "undefined" && TT.on && TT.closed); }
    function sit(screenName) {
      clearInterval(poll); table = null; wasBusy = false;
      if (!on()) return;
      var t = games()[TABLES[screenName]];
      if (!t || !t.busy || !t.credit || !t.cash) return;
      table = t;
      loadPending();
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
      nag(name);
      if (TABLES[name]) sit(name);
      else { clearInterval(poll); table = null; if (offer) { offer = null; } hideCard(); }
      return r;
    };
    setTimeout(function () { var on = document.querySelector(".screen.on"); if (on) nag(on.id.replace("screen-", "")); }, 800);
    return { _force: function (kind) { return kind === "friend" ? friendOffer() : strangerOffer(); },
             _pending: function () { return pending; }, _settleAll: function () { pending.forEach(function (p) { p.in = 0; }); } };
  })();
