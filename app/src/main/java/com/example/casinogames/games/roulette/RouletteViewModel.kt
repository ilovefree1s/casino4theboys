package com.example.casinogames.games.roulette

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STARTING_BANKROLL = 5000.0
private const val CAMPAIGN_START = 5000.0
private const val CAMPAIGN_GOAL = 1_000_000.0

enum class RoulettePhase { BETTING, SPINNING, RESULT }

class RouletteViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("campaign", Context.MODE_PRIVATE)

    var bankroll by mutableDoubleStateOf(STARTING_BANKROLL)
        private set
    var selectedChip by mutableIntStateOf(25)

    /** Chips on the felt, keyed by the spot they sit on. */
    val bets = mutableStateMapOf<String, Int>()
    private val defs = mutableMapOf<String, RouletteEngine.Bet>()
    private val chipHistory = mutableListOf<Pair<String, Int>>()
    private var lastBets: Map<String, Int> = emptyMap()
    private var lastDefs: Map<String, RouletteEngine.Bet> = emptyMap()

    var phase by mutableStateOf(RoulettePhase.BETTING)
        private set
    var message by mutableStateOf("Place your bets")
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
    var goal by mutableDoubleStateOf(CAMPAIGN_GOAL)
        private set
    private var modeInitialized = false

    fun enterMode(campaignMode: Boolean) {
        if (modeInitialized && campaign == campaignMode) {
            // One purse across the whole campaign: another table may have moved
            // it while we were away, whatever this one was left in the middle of.
            if (campaignMode) {
                bankroll = prefs.getFloat("bankroll", CAMPAIGN_START.toFloat()).toDouble()
                goal = prefs.getFloat("goal", CAMPAIGN_GOAL.toFloat()).toDouble()
            }
            return
        }
        campaign = campaignMode
        modeInitialized = true
        phase = RoulettePhase.BETTING
        bets.clear(); defs.clear(); chipHistory.clear()
        pocket = null; lastWin = 0.0; history = emptyList()
        bankroll = if (campaignMode) {
            prefs.getFloat("bankroll", CAMPAIGN_START.toFloat()).toDouble()
        } else STARTING_BANKROLL
        goal = prefs.getFloat("goal", CAMPAIGN_GOAL.toFloat()).toDouble()
        message = "Place your bets"
    }

    private fun persist() {
        if (campaign) prefs.edit().putFloat("bankroll", bankroll.toFloat()).apply()
    }

    fun raiseGoal() {
        goal *= 100
        prefs.edit().putFloat("goal", goal.toFloat()).apply()
        message = "Place your bets"
    }

    fun restartCampaign() {
        bankroll = CAMPAIGN_START
        goal = CAMPAIGN_GOAL
        prefs.edit()
            .putFloat("bankroll", bankroll.toFloat())
            .putFloat("goal", goal.toFloat())
            .apply()
        message = "Place your bets"
    }

    fun buyBackIn() {
        if (phase != RoulettePhase.SPINNING && totalStaked == 0 && bankroll < 25) {
            bankroll = if (campaign) CAMPAIGN_START else STARTING_BANKROLL
            persist()
            message = if (campaign) "Fresh start — road to \$1,000,000" else "Place your bets"
        }
    }

    // ---- betting ----

    fun addChip(id: String, def: RouletteEngine.Bet) {
        if (phase == RoulettePhase.SPINNING) return
        if (phase == RoulettePhase.RESULT) nextSpin()
        val amount = minOf(selectedChip, (bankroll - totalStaked).toInt())
        if (amount <= 0) {
            message = "No bankroll left"
            return
        }
        bets[id] = (bets[id] ?: 0) + amount
        defs[id] = def
        chipHistory.add(id to amount)
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
        message = "Place your bets"
    }

    fun rebet() {
        if (phase != RoulettePhase.BETTING || bets.isNotEmpty()) return
        if (lastBets.isEmpty() || lastBets.values.sum() > bankroll) return
        lastBets.forEach { (id, amount) ->
            bets[id] = amount
            defs[id] = lastDefs.getValue(id)
            chipHistory.add(id to amount)
        }
        message = "Bets repeated"
    }

    // ---- the spin ----

    fun spin() {
        if (phase != RoulettePhase.BETTING) return
        if (totalStaked <= 0) {
            message = "Place a bet first"
            return
        }
        bankroll -= totalStaked
        persist()
        lastBets = bets.toMap()
        lastDefs = defs.toMap()
        phase = RoulettePhase.SPINNING
        message = "No more bets"
        val result = RouletteEngine.spin()

        viewModelScope.launch {
            // The ball rattles through pockets, slowing as it dies out.
            var wait = 60L
            while (wait < 420L) {
                pocket = RouletteEngine.spin()
                delay(wait)
                wait = (wait * 1.28).toLong()
            }
            pocket = result
            delay(650)
            settle(result)
        }
    }

    private fun settle(result: Int) {
        var totalReturn = 0.0
        bets.forEach { (id, stake) ->
            totalReturn += RouletteEngine.settle(defs.getValue(id), stake.toDouble(), result)
        }
        bankroll += totalReturn
        persist()
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
        message = "Place your bets"
    }
}
