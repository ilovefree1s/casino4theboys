package com.example.casinogames.games.roulette

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import com.example.casinogames.campaign.Campaign
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.campaign.limitsFor
import kotlinx.coroutines.launch


/** How long the reel runs, and how far it travels before it starts to die. */
const val SPIN_MILLIS = 3400
private const val TURNS_PER_SPIN = 5

enum class RoulettePhase { BETTING, SPINNING, RESULT }

class RouletteViewModel(app: Application) : AndroidViewModel(app) {
    /** Play testing has its own purse; the campaign shares one with every table. */
    private var freePurse by mutableDoubleStateOf(FreePlay.buyIn)
    val bankroll: Double get() = if (campaign) Campaign.bankroll else freePurse

    private fun spend(amount: Double) {
        if (campaign) Campaign.stake(amount) else freePurse -= amount
    }

    private fun collect(amount: Double) {
        if (campaign) Campaign.payOut(amount) else freePurse += amount
    }

    /** What this table will take on a spot, this hand. */
    val limits get() = limitsFor(campaign)
    var selectedChip by mutableIntStateOf(25)

    /** Chips on the felt, keyed by the spot they sit on. */
    val bets = mutableStateMapOf<String, Int>()
    private val defs = mutableMapOf<String, RouletteEngine.Bet>()
    private val chipHistory = mutableListOf<Pair<String, Int>>()
    private var lastBets: Map<String, Int> = emptyMap()
    private var lastDefs: Map<String, RouletteEngine.Bet> = emptyMap()

    /**
     * The reel is one long belt of wheel pockets; [reelStop] is the cell the
     * marker must come to rest on, and [spinId] tells the screen a new run has
     * started. The screen owns the motion, this owns where it ends.
     */
    var reelStop by mutableIntStateOf(0)
        private set
    var spinId by mutableIntStateOf(0)
        private set

    var phase by mutableStateOf(RoulettePhase.BETTING)
        private set
    var message by mutableStateOf("Place your bets")
        private set

    /**
     * The one thing worth interrupting a betting round to say. [message] is
     * hidden while bets are going down — it would flash the name of every
     * chip laid — so a refusal has to travel separately or the press looks
     * like it did nothing at all.
     */
    var notice by mutableStateOf<String?>(null)
        private set
    /** The pocket the ball is over — flickers during the spin, then settles. */
    var pocket by mutableStateOf<Int?>(null)
        private set
    var lastWin by mutableDoubleStateOf(0.0)
        private set
    /** The last few winning pockets, newest first, for the history rail. */
    var history by mutableStateOf<List<Int>>(emptyList())
        private set

    val totalStaked: Int get() = bets.values.sum()

    // ---- campaign ----

    var campaign by mutableStateOf(false)
        private set
    val goal: Double get() = Campaign.goal
    private var modeInitialized = false

    fun enterMode(campaignMode: Boolean) {
        // The campaign purse is shared and live, so there is nothing to read
        // back when another table has been at it — only free play needs a fill.
        if (modeInitialized && campaign == campaignMode) return
        campaign = campaignMode
        modeInitialized = true
        phase = RoulettePhase.BETTING
        bets.clear(); defs.clear(); chipHistory.clear()
        pocket = null; lastWin = 0.0; history = emptyList()
        if (!campaignMode) freePurse = FreePlay.buyIn
        notice = null
        message = "Place your bets"
    }

    fun raiseGoal() {
        Campaign.raiseGoal()
        notice = null
        message = "Place your bets"
    }

    fun restartCampaign() {
        Campaign.restart()
        notice = null
        message = "Place your bets"
    }

    /**
     * Broke in the campaign is a marker, not a free reset: the house hands
     * over five thousand and writes down seven and a half.
     */
    fun buyBackIn() {
        if (phase == RoulettePhase.SPINNING) return
        // The chips still showing after a result are spent, not staked; sweep
        // them or they read as a live bet and the refill never lands.
        if (phase == RoulettePhase.RESULT) nextSpin()
        if (totalStaked > 0 || bankroll >= 25) return
        if (campaign) Campaign.takeMarker() else freePurse = FreePlay.buyIn
        notice = null
        message = if (campaign) "Marker signed — dig out" else "Place your bets"
    }

    // ---- betting ----

    fun addChip(id: String, def: RouletteEngine.Bet) {
        if (phase == RoulettePhase.SPINNING) return
        if (phase == RoulettePhase.RESULT) nextSpin()
        val affordable = minOf(selectedChip, (bankroll - totalStaked).toInt())
        if (affordable <= 0) {
            notice = "No bankroll left"
            return
        }
        // Every roulette spot is a bet in its own right, so the table max is
        // read against what is already sitting on this one.
        val amount = limits.allow(affordable, bets[id] ?: 0)
        if (amount <= 0) {
            notice = limits.refusal(side = false)
            return
        }
        bets[id] = (bets[id] ?: 0) + amount
        defs[id] = def
        chipHistory.add(id to amount)
        notice = null
        message = if (amount < selectedChip) "All in!" else def.name
    }

    fun undoChip() {
        if (phase != RoulettePhase.BETTING) return
        val (id, amount) = chipHistory.removeLastOrNull() ?: return
        val left = (bets[id] ?: 0) - amount
        if (left > 0) bets[id] = left else { bets.remove(id); defs.remove(id) }
    }

    fun clearBets() {
        if (phase != RoulettePhase.BETTING) return
        bets.clear(); defs.clear(); chipHistory.clear()
        notice = null
        message = "Place your bets"
    }

    fun rebet() {
        if (phase == RoulettePhase.SPINNING) return
        if (phase == RoulettePhase.RESULT) nextSpin()
        if (bets.isNotEmpty()) return
        if (lastBets.isEmpty()) return
        if (lastBets.values.sum() > bankroll) {
            notice = "Not enough for that bet"
            return
        }
        lastBets.forEach { (id, amount) ->
            bets[id] = amount
            defs[id] = lastDefs.getValue(id)
            chipHistory.add(id to amount)
        }
        notice = null
        message = "Bets repeated"
    }

    // ---- the spin ----

    fun spin() {
        if (phase == RoulettePhase.SPINNING) return
        // Pressing spin on a finished round clears the felt for the next one,
        // the same as reaching for a chip does — it used to do nothing at all.
        if (phase == RoulettePhase.RESULT) nextSpin()
        if (totalStaked <= 0) {
            notice = "Place a bet first"
            return
        }
        spend(totalStaked.toDouble())
        lastBets = bets.toMap()
        lastDefs = defs.toMap()
        phase = RoulettePhase.SPINNING
        notice = null
        message = "No more bets"
        val result = RouletteEngine.spin()

        // Wind the reel several whole turns on and stop it where the result
        // sits, so the belt always travels forward and lands under the marker.
        val slot = RouletteEngine.WHEEL.indexOf(result)
        var stop = reelStop + TURNS_PER_SPIN * RouletteEngine.WHEEL.size
        stop += Math.floorMod(slot - Math.floorMod(stop, RouletteEngine.WHEEL.size), RouletteEngine.WHEEL.size)
        reelStop = stop
        spinId++

        viewModelScope.launch {
            delay(SPIN_MILLIS.toLong() + 350)
            pocket = result
            settle(result)
        }
    }

    private fun settle(result: Int) {
        var totalReturn = 0.0
        bets.forEach { (id, stake) ->
            totalReturn += RouletteEngine.settle(defs.getValue(id), stake.toDouble(), result)
        }
        collect(totalReturn)
        lastWin = totalReturn
        history = (listOf(result) + history).take(12)
        val net = totalReturn - totalStaked
        message = when {
            campaign && bankroll >= goal -> "🏆 GOAL REACHED!"
            net > 0 -> "${RouletteEngine.label(result)} — you win ${"%,.0f".format(totalReturn)}"
            totalReturn > 0 -> "${RouletteEngine.label(result)} — back ${"%,.0f".format(totalReturn)}"
            else -> "${RouletteEngine.label(result)} — house takes it"
        }
        phase = RoulettePhase.RESULT
    }

    /** Clears the felt for fresh bets; addChip calls it on the first tap after a spin. */
    fun nextSpin() {
        if (phase != RoulettePhase.RESULT) return
        phase = RoulettePhase.BETTING
        bets.clear(); defs.clear(); chipHistory.clear()
        lastWin = 0.0
        notice = null
        message = "Place your bets"
    }
}
