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
                   "Lyndsi", "Cougar", "Grace", "Zephyrox", "Lucy", "Tristen", "Daneal", "Max", "Ethan", "Tye", "Zach"];
    /* Tristen only ever wants two things: blackjack money, or this. */
    var TRISTEN_SCAM = { odds: 0.35, back: [2, 3], cut: [0.03, 0.07],
      ask: "Tristen, dead serious: \"Bro. The gift shop has a GIANT dildo. Like, huge. I need it. {amt} and I pay you back tonight, swear.\"",
      good: "Tristen walks up with the giant dildo under his arm AND your money. \"Worth every penny, bro.\" {back}.",
      bad: "Tristen bought the giant dildo. You're not getting paid back. He's never been happier." };
    var CONTEST = "the dildo taking contest";
    var CONTEST_ASK = [
      "Bro. There's a dildo taking contest in the back. Entry's {amt}. I can WIN this.",
      "Tristen, stretching: \"Front me {amt} for the dildo taking contest. First place, bro. Easy money.\"",
      "{amt} gets me in the dildo taking contest. I've been training. Don't ask.",
      "Bad habits, good intentions. {amt} for the dildo taking contest. You'll get it back, swear."
    ];
    var CONTEST_WIN = [
      "Tristen took FIRST PLACE in the dildo taking contest. {won} prize. He hands you back {back}. Exactly {back}.",
      "Tristen's back with a trophy. \"Told you I trained.\" First place, {won}. You get your {back} back. The rest? \"That's mine, bro.\"",
      "Tristen won the dildo taking contest. Nobody's asking how. Here's your {back}. He keeps the {kept}."
    ];
    var CONTEST_LOSE = [
      "Tristen didn't place in the dildo taking contest. Your money's gone. He's walking funny.",
      "Tristen lost the dildo taking contest. \"Bad habits, bro.\" Nothing back.",
      "Tristen got smoked in the dildo taking contest. Entry fee's gone."
    ];
    var CONTEST_SNUB = [
      "Tristen paid his own way into the dildo taking contest and took first. \"Should've backed me.\"",
      "Tristen entered the dildo taking contest anyway and lost. \"Good call not backing me.\""
    ];
    var TRISTEN_KEEPS = [
      "Tristen hit for {won} at blackjack. He hands you back {back}. Exactly {back}. \"Good intentions, bro.\"",
      "Tristen's back with a rack of chips. You get your {back} back. The other {kept}? \"That's mine, bro.\"",
      "Tristen turned it into {won}. Pays you {back} and pockets the rest. \"Bad habits.\""
    ];
    /* Lines only one friend says, mixed in with the usual ones. */
    var OWN = {
      Darin: {
        ask: ["Bro spot me {amt}. Trilux on the 777s. I can FEEL it.",
              "{amt}. 777s. Trilux. You know what it is.",
              "Darin, already walking to the 777s table: \"Front me {amt}, Trilux is about to hit.\"",
              "You know where I'm going. {amt} for Trilux on the 777s, bro."]
      },
      Tristen: {
        ask: ["Bro. Bad habits, but good intentions. Spot me {amt} for blackjack.",
              "Listen, I KNOW how this looks. {amt}. Blackjack. Good intentions, bro.",
              "Tristen, eyes bloodshot: \"I'm locked in, bro. {amt} on blackjack, trust.\""],
        lost: ["Tristen: \"Bad habits, bro. Bad habits.\" Your money's gone.",
               "Tristen's back holding a beer and no chips. \"Good intentions though.\""],
        win: []
      }
    };
    function own(who, key, base) { return lines(who, key, base); }
    function lines(who, key, base) { var o = OWN[who] && OWN[who][key]; return o && o.length && (key === "ask" || Math.random() < 0.6) ? o : base; }
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
    var HUSTLES = false;
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
        "#wu-card.shout{border-color:#FFB238;background:linear-gradient(90deg,rgba(60,24,0,.97),rgba(24,12,4,.97))}#wu-card.shout .wu-n{color:#FFB238}#wu-card.shout img{color:#FFB238}" +
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
        (buttons ? '<div class="wu-row"><button class="wu-yes"></button><button class="wu-no"></button></div>' : "") + "</div>";
      card.querySelector("img").src = FACES[face] || "";
      if (!FACES[face]) card.querySelector("img").style.display = "none";
      card.querySelector(".wu-n").textContent = name.toUpperCase();
      card.querySelector(".wu-t").textContent = text;
      if (buttons) {
        var y = card.querySelector(".wu-yes");
        y.textContent = buttons.yes;
        card.querySelector(".wu-no").textContent = buttons.no || "NAH";
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
    /* ---- shout-outs: another player's big hit, announced wherever you are ---- */
    var shoutQ = [];
    function faceOf(name) {
      var n = String(name || "").toLowerCase();
      var k = Object.keys(FACES).filter(function (f) { return f.toLowerCase() === n; })[0];
      return k || "";
    }
    function announce(name, game, amount, mult) {
      shoutQ.push({ name: name, game: game, amount: amount, mult: mult });
      drainShout();
    }
    function drainShout() {
      if (!shoutQ.length) return;
      if (card && card.classList.contains("on")) { setTimeout(drainShout, 2500); return; }
      var x = shoutQ.shift(), face = faceOf(x.name);
      show("shout", face, "🔥 BIG HIT", String(x.name).toUpperCase() + " just hit " + $$(x.amount) + " on " +
        String(x.game).toUpperCase() + " — " + fmt(x.mult) + "× the bet!", null, 7000);
      var img = card.querySelector("img");
      if (!face) img.style.display = "none";
      jingle();
      if (shoutQ.length) setTimeout(drainShout, 7600);
    }
    /* ---- duels: notices and questions from the tournament, in the same card ---- */
    var noteQ = [];
    function queue(fn) { noteQ.push(fn); drainNotes(); }
    function drainNotes() {
      if (!noteQ.length) return;
      if (card && card.classList.contains("on")) { setTimeout(drainNotes, 2500); return; }
      noteQ.shift()();
      if (noteQ.length) setTimeout(drainNotes, 1500);
    }
    /** A notice: kind good/bad/shout/friend, with a sound when asked. */
    function notice(kind, name, title, text, sound) {
      queue(function () {
        show(kind, faceOf(name), title, text, null, 8000);
        if (sound === "win") jingle(); else if (sound === "lose") womp();
      });
    }
    /** A question with two answers; a timeout counts as no answer at all. */
    function ask(name, title, text, yes, no, onYes, onNo, onLater) {
      queue(function () {
        show("friend", faceOf(name), title, text, { yes: yes, no: no, onYes: onYes,
          onNo: function (timedOut) { if (timedOut) { if (onLater) onLater(); } else onNo(); } }, 45000);
      });
    }
    /** Money from the tournament onto the table you're at; false when you aren't at one. */
    function give(x) { if (!table || !table.credit) return false; return money(x); }
    /** Board stats (tournament): walk-ups shown, backed, real, scams. */
    function count(f) { try { if (typeof TT !== "undefined" && TT.walkup) TT.walkup(f); } catch (e) {} }
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
      var legit = Math.random() < sc.odds;
      count("shown"); count(legit ? "real" : "scams");
      offer = who;
      show("", who, who, fill(sc.ask, { amt: $$(amt), who: who }), {
        yes: "GIVE " + $$(amt), cant: amt > cash(),
        onYes: function () {
          offer = null;
          if (amt > cash() || !money(-amt)) return;
          count("backed");
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
      var who = forced || pick(free), br = cash();
      var game = who === "Tristen" ? (Math.random() < 0.5 ? CONTEST : "blackjack") : who === "Darin" ? "Trilux on the 777s" : pick(SPOTS);
      var amt = nice(br * rnd(0.04, 0.12));
      if (amt > br) return false;
      offer = who;
      count("shown"); count("real");
      show("friend", who, who, fill(pick(game === CONTEST ? CONTEST_ASK : lines(who, "ask", FRIEND_ASK)), { amt: $$(amt), game: game }), {
        yes: "SPOT " + $$(amt), cant: amt > cash(),
        onYes: function () {
          offer = null;
          if (amt > cash() || !money(-amt)) return;
          count("backed");
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
        // security is Justin, unless Justin ran the scam: then it's somebody else on shift
        var guard = "Justin";
        if (p.who === "Justin") guard = pick(FRIENDS.filter(function (n) { return n !== "Justin" && notMe(n); }));
        card.querySelector("img").src = FACES[guard];
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
      if (p.game === CONTEST) {
        // he places or he doesn't: first place, and you get your loan back (he keeps the prize)
        if (p.snub) { show("friend", p.name, p.name, fill(pick(CONTEST_SNUB), o)); return; }
        if (Math.random() < 0.45) {
          var prize = nice(p.amt * rnd(4, 8));
          o.won = $$(prize); o.back = $$(p.amt); o.kept = $$(Math.max(0, prize - p.amt));
          money(p.amt);
          show("good", p.name, p.name, fill(pick(CONTEST_WIN), o)); jingle();
        } else { show("bad", p.name, p.name, fill(pick(CONTEST_LOSE), o)); womp(); }
        return;
      }
      if (p.snub) { show("friend", p.name, p.name, fill(pick(FRIEND_SNUB), o)); return; }
      var r = Math.random(), back = 0, lines;
      // a friend who wins splits it with you 50/50: half of what your money turned into
      var won = 0;
      if (r < 0.07) { won = nice(p.amt * rnd(10, 16)); lines = FRIEND_BIG; }
      else if (r < 0.42) { won = nice(p.amt * rnd(4, 8)); lines = own(p.name, "win", FRIEND_WIN); }
      else if (r < 0.57) { back = p.amt; lines = FRIEND_EVEN; }
      else lines = own(p.name, "lost", FRIEND_LOST);
      if (won) back = won / 2;
      // Tristen wins, Tristen keeps it: you get exactly what you lent him
      if (won && p.name === "Tristen") { back = p.amt; lines = TRISTEN_KEEPS; }
      back = Math.round(back);
      o.won = $$(won); o.kept = $$(Math.max(0, won - p.amt));
      o.back = $$(back);
      if (back > 0) money(back);
      show(back > 0 ? "good" : "bad", p.name, p.name, fill(pick(lines), o));
      if (back > 0) jingle(); else womp();
    }

    /* ---- Tips: a friend tells you what to bet, and BACK IT puts real chips on
       that spot for the next hand, paid by the table's own paytable. */
    var TIPS = {
      baccarat: [
        { say: "BANKER", spot: "banker" }, { say: "PLAYER", spot: "player" },
        { say: "the TIE", spot: "tie" }, { say: "PLAYER PAIR", spot: "playerPair" }, { say: "BANKER PAIR", spot: "bankerPair" }],
      dragontiger: [{ say: "DRAGON", spot: "dragon" }, { say: "TIGER", spot: "tiger" }, { say: "the TIE", spot: "tie" }],
      roulette: [{ lucky: true }],   // always a number, straight up
      keno: [{ keno: true }],        // a number marked on your ticket
      sicbo: [{ say: "BIG", spot: "big" }, { say: "SMALL", spot: "small" }],
      wheel: [{ say: "RED", spot: "r" }, { say: "GREEN", spot: "g" }, { say: "BLUE", spot: "b" }, { say: "GREY", spot: "s" }],
      blackjack: [{ say: "PUSH 22", spot: "push22", variant: "freebet" },
                  { say: "TRILUX", spot: "trilux", variant: "blazing", only: "Darin" }],   // Darin lives on Trilux
      holdem: [{ say: "the TRIPS", spot: "trips", poker: true }, { say: "the BAD BEAT", spot: "badbeat", poker: true }],
      djwild: [{ say: "the TRIPS", spot: "trips", poker: true }, { say: "the BAD BEAT", spot: "badbeat", poker: true }],
      miniuth: [{ say: "the FLUSH PLUS", spot: "flushplus", poker: true }, { say: "the 3 CARD BONUS", spot: "bonus", poker: true }]
    };
    /* Which tipped spots are side bets under the room's limits (the rest go against the table max). */
    var TIP_SIDE = { trips: 1, flushplus: 1, bonus: 1, playerPair: 1, bankerPair: 1, push22: 1, trilux: 1 };
    /** The most the table will take on that spot right now: room limits, less what's already on it. */
    function tipCap(game, spot) {
      var lim = Shell.limits();
      if (!lim || !lim.enforced) return Infinity;
      var cap;
      if (spot === "badbeat") {
        // UTH and DJ Wild run their own, doubled Bad Beat caps
        cap = { 25: 50, 50: 250, 75: 500, 100: 1000, 125: 5000 }[lim.badBeatMax] || lim.badBeatMax;
      } else cap = TIP_SIDE[spot] ? lim.sideMax : lim.max;
      var on = 0;
      try { on = table && table.tipOn ? table.tipOn(spot) || 0 : 0; } catch (e) {}
      return Math.max(0, cap - on);
    }
    /** Did the tipped spot win, from what the table reports at the settle. */
    function tipHit(game, spot, what) {
      if (game === "dragontiger") return what === spot;
      if (game === "wheel") return what === spot;
      if (game === "blackjack") return spot === "trilux" ? !!what.trilux : what.dealer === 22;
      if (game === "sicbo") {
        var t = what[0] + what[1] + what[2], triple = what[0] === what[1] && what[1] === what[2];
        return !triple && (spot === "big" ? t >= 11 && t <= 17 : t >= 4 && t <= 10);
      }
      if (game === "keno") return what.indexOf(+spot.slice(2)) >= 0;
      if (game === "roulette") return what.some(function (p) { return tip.pockets.indexOf(p) >= 0; });
      return !!(what && what[spot]);   // baccarat's winning spots, the poker tables' side results
    }
    var RED = { 1:1,3:1,5:1,7:1,9:1,12:1,14:1,16:1,18:1,19:1,21:1,23:1,25:1,27:1,30:1,32:1,34:1,36:1 };
    function pocketsFor(spot) {
      var a = [], n;
      if (spot.indexOf("n:") === 0) return [+spot.slice(2)];
      if (spot.indexOf("dz-") === 0) { var s0 = +spot.slice(3) * 12 + 1; for (n = s0; n < s0 + 12; n++) a.push(n); return a; }
      for (n = 1; n <= 36; n++) if ((spot === "red" && RED[n]) || (spot === "black" && !RED[n]) ||
        (spot === "odd" && n % 2) || (spot === "even" && !(n % 2))) a.push(n);
      return a;
    }
    var TIP_ASK = [
      "Bro. Throw {amt} on {pick} next hand. Trust me.",
      "I'm telling you, {amt} on {pick}. Right now.",
      "Yo I got a feeling. {amt} on {pick}. Don't overthink it.",
      "I've been watching this table all night. {amt} on {pick}.",
      "Put {amt} on {pick} with me. We eat together."
    ];
    // poker tables: the bet's always the same spot, so it's about the hand coming
    var TIP_POKER = [
      "Bro trust me, slam {pick} this next hand! {amt}.",
      "I can FEEL it. Next hand is the one. Slam {amt} on {pick}.",
      "Bro. {pick}. {amt}. Next hand. Don't think, just slam it.",
      "Trust me bro, {pick} is coming next hand. Throw {amt} on it.",
      "I just watched the dealer shuffle. Slam {pick} next hand, {amt}. Trust."
    ];
    var KENO_ASK = [
      "Bro. Mark {n}. Trust me, {n} is coming out next draw.",
      "{n}. I dreamed about {n} last night. Mark it.",
      "Yo put {n} on your ticket. It's due, I've been watching.",
      "Mark {n} bro. Don't ask why. Just mark it."
    ];
    var FLOOR_OK = [
      "Already maxed? I talked to the floor, they're letting you go over this hand.",
      "Don't worry about the max, the floor said it's cool this one time.",
      "Floor guy owes me one. You can go over the limit this hand."
    ];
    var TIP_WIN = ["{who} was RIGHT. {pick} hit!", "{who}: \"WHAT DID I TELL YOU?\" {pick} paid!", "{who} is screaming. {pick}!"];
    var TIP_LOSE = ["{who}: \"My bad bro… I really felt it.\"", "{who} is already walking away. {pick} missed.", "{who}: \"Rigged. That table's rigged.\""];
    var tip = null;   // { who, game, spot, pick, amt, placed, pockets }
    function tipGame() { var on = document.querySelector(".screen.on"); return on ? on.id.replace("screen-", "") : ""; }
    function tipOffer(who) {
      var game = tipGame(), list = TIPS[game];
      if (!list || tip || !table || !table.tip) return false;
      var variant = table.variant ? table.variant() : "";
      list = list.filter(function (t) { return (!t.variant || t.variant === variant) && (!t.only || t.only === who); });
      // Darin at the 777s only ever has one tip
      if (who === "Darin" && list.some(function (t) { return t.only === "Darin"; })) list = list.filter(function (t) { return t.only === "Darin"; });
      if (!list.length) return false;
      var busyNames = pending.map(function (p) { return p.name; });
      if (!notMe(who) || busyNames.indexOf(who) >= 0) return false;
      var t = pick(list), br = cash();
      if (t.lucky) { var n = 1 + Math.floor(Math.random() * 36); t = { say: "number " + n, spot: "n:" + n }; }
      if (t.keno) {
        var k = 1 + Math.floor(Math.random() * 80);
        t = { say: "" + k, spot: "n:" + k, keno: true };
        count("shown"); count("real");
        show("friend", who, who, pick(KENO_ASK).replace(/\{n\}/g, k), {
          yes: "MARK " + k, onNo: function () {},
          onYes: function () { count("backed"); tip = { who: who, game: game, spot: t.spot, pick: "number " + k, amt: 0, placed: 0 }; placeTip(); }
        });
        return true;
      }
      var amt = nice(br * rnd(0.02, 0.05));
      if (amt <= 0 || amt > br) return false;
      // over what the table takes on that spot: he's squared it with the floor for this one hand
      var floor = amt > tipCap(game, t.spot);
      count("shown"); count("real");
      show("friend", who, who, fill(pick(t.poker ? TIP_POKER : TIP_ASK), { amt: $$(amt), pick: t.say }) +
        (floor ? " " + pick(FLOOR_OK) : ""), {
        yes: "BACK IT " + $$(amt), cant: amt > cash(),
        onYes: function () {
          count("backed");
          tip = { who: who, game: game, spot: t.spot, pick: t.say, amt: amt, placed: 0, floor: floor,
                  pockets: game === "roulette" ? pocketsFor(t.spot) : null };
          placeTip();
        },
        onNo: function () {}
      });
      return true;
    }
    /** The chips go down as soon as the table takes bets again. */
    function placeTip() {
      if (!tip || tip.placed || !table || !table.tip || tipGame() !== tip.game) return;
      if (table.phase && table.phase() !== "betting") return;
      // the floor okayed it: this one bet goes down past the room's limit
      var got = 0, lim = Shell.limits;
      // always: a rebet can max the spot out after he asked, and the floor's okay covers that too
      Shell.limits = function () { return { min: 0, max: Infinity, sideMax: Infinity, badBeatMax: Infinity, enforced: false }; };
      try { got = table.tip(tip.spot, tip.amt); } finally { Shell.limits = lim; }
      if (got > 0) tip.placed = got;
      else { var t = tip; tip = null; show("bad", t.who, t.who, t.who + (t.game === "keno" ? ": \"Your ticket's full, bro. Never mind.\""
        : ": \"You don't have the chips left for it, bro. Never mind.\"")); }
    }
    /** The table reports what it dealt; the friend reacts to the tipped spot. */
    function result(game, what) {
      if (!tip || !tip.placed || tip.game !== game) return;
      var won = false;
      try { won = tipHit(game, tip.spot, what); } catch (e) {}
      var t = tip; tip = null;
      // one hand only: take the friend's chips back off the rebet
      try { if (table && table.untip) table.untip(t.spot, t.placed); } catch (e) {}
      setTimeout(function () {
        var o = { who: t.who, pick: t.pick };
        if (won) { show("good", t.who, t.who, fill(pick(TIP_WIN), o)); jingle(); }
        else { show("bad", t.who, t.who, fill(pick(TIP_LOSE), o)); womp(); }
      }, 1400);
    }
    /** A backed tip that [what] misses: the table deals or spins once more (2x the chance on a long shot). */
    function reroll(game, what) {
      if (!tip || !tip.placed || tip.game !== game) return false;
      try { return !tipHit(game, tip.spot, what); } catch (e) { return false; }
    }
    /** Left the table: the tip goes with it (chips not yet dealt come back with the table). */
    function refundTip() { tip = null; }

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
      var roll = Math.random(), ok = false;
      // hustles (sick kid, horse tip...) are off for now: just tips and friends borrowing to play
      if (roll < 0.5 && nx !== "Tristen") ok = tipOffer(nx);
      if (!ok) ok = HUSTLES && roll >= 0.75 ? strangerOffer(nx) : friendOffer(nx);
      if (!ok) deck.push(nx);
      if (ok) { hands = 0; gap = Math.round(rnd(7, 13)); }
    }
    function on() { return !(typeof TT !== "undefined" && TT.on && TT.closed); }
    function sit(screenName) {
      refundTip(); clearInterval(poll); table = null; wasBusy = false;
      if (!on()) return;
      var t = games()[TABLES[screenName]];
      if (!t || !t.busy || !t.credit || !t.cash) return;
      table = t;
      loadPending();
      poll = setInterval(function () {
        if (!on()) return;
        var b = !!table.busy();
        if (tip && !tip.placed) placeTip();
        if (wasBusy && !b) {
          try { if (typeof TT !== "undefined" && TT.handDone) TT.handDone(); } catch (e) {}
          setTimeout(handOver, 900);
        }
        wasBusy = b;
      }, 250);
    }
    var showScreen = Shell.show;
    Shell.show = function (name) {
      var r = showScreen.apply(this, arguments);
      if (name === "settings") { style(); paintMe(); }
      nag(name);
      if (TABLES[name]) sit(name);
      else { refundTip(); clearInterval(poll); table = null; if (offer) { offer = null; } hideCard(); }
      return r;
    };
    setTimeout(function () { var on = document.querySelector(".screen.on"); if (on) nag(on.id.replace("screen-", "")); }, 800);
    return { result: result, reroll: reroll, announce: announce, notice: notice, ask: ask, give: give, _cur: function () { return tip; }, _tip: function (w) { return tipOffer(w || "Zac"); }, _force: function (kind) { return kind === "friend" ? friendOffer() : kind === "tristen" ? friendOffer("Tristen") : strangerOffer(pick(FRIENDS.filter(notMe))); },
             _pending: function () { return pending; }, _settleAll: function () { pending.forEach(function (p) { p.in = 0; }); } };
  })();
