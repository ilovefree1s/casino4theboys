package com.example.casinogames.games.craps

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.casinogames.campaign.Campaign
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.campaign.limitsFor

/** The dice are either at rest, where the felt can be touched, or in the air. */
enum class CrapsPhase { BETTING, ROLLING }

/**
 * Crapless craps. The felt is live from the first throw: chips go down between
 * rolls and ride until they win, lose or are taken down. There is no hand to
 * deal and nothing to ante, so the only phases are dice at rest and dice in
 * the air.
 */
class CrapsViewModel(app: Application) : AndroidViewModel(app) {

    private var freePurse by mutableDoubleStateOf(FreePlay.buyIn)
    val bankroll: Double get() = if (campaign) Campaign.bankroll else freePurse

    private fun spend(amount: Double) {
        if (campaign) Campaign.stake(amount) else freePurse -= amount
    }

    private fun collect(amount: Double) {
        if (campaign) Campaign.payOut(amount) else freePurse += amount
    }

    val limits get() = limitsFor(campaign)
    var selectedChip by mutableIntStateOf(25)

    var bets by mutableStateOf(CrapsBets())
        private set
    /** Null on the come out; otherwise the number the shooter is trying to repeat. */
    var point by mutableStateOf<Int?>(null)
        private set
    var phase by mutableStateOf(CrapsPhase.BETTING)
        private set
    var lastRoll by mutableStateOf<Roll?>(null)
        private set
    var results by mutableStateOf<List<CrapsLine>>(emptyList())
        private set
    var message by mutableStateOf("Come out — seven wins, nothing loses")
        private set
    /** The last throw's headline, for the big call over the tray. */
    var headline by mutableStateOf("")
        private set
    /** What was standing when the last seven swept the felt, for SAME BET. */
    private var sweptBets: CrapsBets? = null

    val totalAtRisk: Int get() = bets.total
    val comeOut: Boolean get() = point == null

    // ---- campaign ----

    var campaign by mutableStateOf(false)
        private set
    val goal: Double get() = Campaign.goal
    private var modeInitialized = false

    fun enterMode(campaignMode: Boolean) {
        if (modeInitialized && campaign == campaignMode) return
        campaign = campaignMode
        modeInitialized = true
        bets = CrapsBets(); point = null; phase = CrapsPhase.BETTING
        lastRoll = null; results = emptyList(); headline = ""; sweptBets = null
        if (!campaignMode) freePurse = FreePlay.buyIn
        message = "Come out — seven wins, nothing loses"
    }

    fun raiseGoal() { Campaign.raiseGoal(); message = "Come out roll" }
    fun restartCampaign() { Campaign.restart(); message = "Come out roll" }

    fun buyBackIn() {
        if (phase != CrapsPhase.BETTING || totalAtRisk > 0 || bankroll >= 25) return
        if (campaign) Campaign.takeMarker() else freePurse = FreePlay.buyIn
        message = "Come out roll"
    }

    val canTakeMarker: Boolean
        get() = campaign && phase == CrapsPhase.BETTING && bankroll < 25 && Campaign.canTakeMarker

    // ---- putting chips down ----

    /** What the purse and the table will actually take, of the chip in hand. */
    private fun allowed(already: Int, side: Boolean): Int {
        val affordable = minOf(selectedChip, (bankroll - 0).toInt())
        if (affordable <= 0) {
            message = "No bankroll left"
            return 0
        }
        val amount = limits.allow(affordable, already, side)
        if (amount <= 0) message = limits.refusal(side)
        return amount
    }

    private fun put(amount: Int) = spend(amount.toDouble())

    fun addPass() {
        if (phase != CrapsPhase.BETTING) return
        // The line is a contract: it can only be bet before a point exists.
        if (!comeOut) { message = "The line is closed once a point is out"; return }
        val amount = allowed(bets.pass, side = false)
        if (amount <= 0) return
        put(amount)
        bets = bets.copy(pass = bets.pass + amount)
        message = "Pass line"
    }

    /** Odds behind the line, capped at the house's multiple of the flat bet. */
    fun addPassOdds() {
        if (phase != CrapsPhase.BETTING) return
        val p = point ?: run { message = "Odds go up once there is a point"; return }
        if (bets.pass <= 0) { message = "Odds ride behind a line bet"; return }
        val cap = bets.pass * CrapsRules.MAX_ODDS_MULTIPLE
        val room = cap - bets.passOdds
        if (room <= 0) { message = "Odds are capped at ${CrapsRules.MAX_ODDS_MULTIPLE}x the line"; return }
        val amount = minOf(allowed(bets.passOdds, side = false), room)
        if (amount <= 0) return
        put(amount)
        bets = bets.copy(passOdds = bets.passOdds + amount)
        val (n, d) = CrapsRules.oddsPay(p)
        message = "Odds on $p pay $n to $d"
    }

    fun addCome() {
        if (phase != CrapsPhase.BETTING) return
        if (comeOut) { message = "Come bets wait for a point"; return }
        val amount = allowed(bets.comeFlat, side = false)
        if (amount <= 0) return
        put(amount)
        bets = bets.copy(comeFlat = bets.comeFlat + amount)
        message = "Come"
    }

    fun addPlace(number: Int) {
        if (phase != CrapsPhase.BETTING) return
        val amount = allowed(bets.place[number] ?: 0, side = false)
        if (amount <= 0) return
        put(amount)
        bets = bets.copy(place = bets.place + (number to (bets.place[number] ?: 0) + amount))
        val (n, d) = CrapsRules.placePay(number)
        message = "Place $number pays $n to $d"
    }

    /** Odds behind a come bet already sitting on its number. */
    fun addComeOdds(number: Int) {
        if (phase != CrapsPhase.BETTING) return
        val bet = bets.comePoints[number] ?: run { message = "No come bet on the $number"; return }
        val room = bet.flat * CrapsRules.MAX_ODDS_MULTIPLE - bet.odds
        if (room <= 0) { message = "Odds are capped at ${CrapsRules.MAX_ODDS_MULTIPLE}x"; return }
        val amount = minOf(allowed(bet.odds, side = false), room)
        if (amount <= 0) return
        put(amount)
        bets = bets.copy(comePoints = bets.comePoints + (number to bet.copy(odds = bet.odds + amount)))
        val (n, d) = CrapsRules.oddsPay(number)
        message = "Come odds on $number pay $n to $d"
    }

    fun addField() {
        if (phase != CrapsPhase.BETTING) return
        val amount = allowed(bets.field, side = true)
        if (amount <= 0) return
        put(amount)
        bets = bets.copy(field = bets.field + amount)
        message = "Field — two pays double, twelve triple"
    }

    fun addHard(number: Int) {
        if (phase != CrapsPhase.BETTING) return
        val amount = allowed(bets.hard[number] ?: 0, side = true)
        if (amount <= 0) return
        put(amount)
        bets = bets.copy(hard = bets.hard + (number to (bets.hard[number] ?: 0) + amount))
        message = "Hard $number pays ${CrapsRules.hardPay(number)} to 1"
    }

    fun addProp(prop: Prop) {
        if (phase != CrapsPhase.BETTING) return
        val amount = allowed(bets.props[prop] ?: 0, side = true)
        if (amount <= 0) return
        put(amount)
        bets = bets.copy(props = bets.props + (prop to (bets.props[prop] ?: 0) + amount))
        message = "${prop.label} pays ${prop.pays} to 1"
    }

    /**
     * Takes down everything the house would let a player pick up. The flat
     * line bet and a come bet already on its number are contracts and stay.
     */
    fun clearBets() {
        if (phase != CrapsPhase.BETTING) return
        var back = 0.0
        var kept = bets

        back += kept.passOdds + kept.comeFlat + kept.field +
            kept.place.values.sum() + kept.hard.values.sum() + kept.props.values.sum()
        kept = kept.copy(
            passOdds = 0, comeFlat = 0, field = 0,
            place = emptyMap(), hard = emptyMap(), props = emptyMap(),
        )
        // Come odds come down with the rest; the flat bets behind them cannot.
        kept.comePoints.forEach { (_, bet) -> back += bet.odds }
        kept = kept.copy(comePoints = kept.comePoints.mapValues { it.value.copy(odds = 0) })
        // On the come out even the line can be picked up.
        if (comeOut) { back += kept.pass; kept = kept.copy(pass = 0) }

        if (back <= 0.0) { message = "Nothing to take down"; return }
        collect(back)
        bets = kept
        message = "Taken down"
    }

    /** After a seven sweeps the felt, put the same chips back out. */
    fun rebet() {
        if (phase != CrapsPhase.BETTING) return
        val swept = sweptBets ?: return
        val cost = swept.total
        if (cost <= 0) return
        if (cost > bankroll) { message = "Not enough for that bet"; return }
        spend(cost.toDouble())
        // A swept felt is a come out, so the numbers go back but the travelled
        // come bets cannot: they died with the seven and have no number now.
        bets = bets.copy(
            pass = bets.pass + swept.pass,
            place = swept.place, field = swept.field,
            hard = swept.hard, props = swept.props,
        )
        sweptBets = null
        message = "Same bet"
    }

    val canRebet: Boolean get() = sweptBets != null && phase == CrapsPhase.BETTING

    // ---- the throw ----

    /** The line must be covered to come out; after that the dice are free. */
    val canRoll: Boolean
        get() = phase == CrapsPhase.BETTING && (!comeOut || bets.pass > 0)

    fun rolling() { if (canRoll) phase = CrapsPhase.ROLLING }

    /** The tray calls this once the dice have come to rest. */
    fun settleRoll(a: Int, b: Int) {
        if (phase != CrapsPhase.ROLLING) return
        val roll = Roll(a, b)
        val before = bets
        val result = CrapsRules.resolve(bets, point, roll)
        lastRoll = roll
        bets = result.bets
        point = result.point
        results = result.lines
        headline = result.headline
        collect(result.returns)

        if (result.sevenOut) sweptBets = before.copy(
            pass = before.pass, passOdds = 0, comeFlat = 0, comePoints = emptyMap(),
        )

        message = when {
            campaign && bankroll >= goal -> "🏆 GOAL REACHED!"
            result.sevenOut -> "Seven out — the felt is swept"
            result.pointMade -> "Winner! Come out again"
            comeOut -> "Come out — seven wins, nothing loses"
            else -> point?.let { p ->
                val (n, d) = CrapsRules.oddsPay(p)
                "Back the $p with odds — $n to $d, and no edge at all"
            } ?: ""
        }
        phase = CrapsPhase.BETTING
    }
}
