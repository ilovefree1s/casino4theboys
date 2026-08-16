package com.example.casinogames.games.baccarat

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.casinogames.games.core.Shoe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Phase { BETTING, DEALING, RESULT }

/** One finished hand in the session ledger: who won and this player's net result. */
data class HandRecord(val outcome: Outcome, val net: Double)

private const val DECKS = 8
private const val RESHUFFLE_AT = 16
private const val STARTING_BANKROLL = 5000.0
private const val CAMPAIGN_START = 5000.0
private const val CAMPAIGN_GOAL = 1_000_000.0

class BaccaratViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("campaign", Context.MODE_PRIVATE)
    private val shoe = Shoe(decks = DECKS)
    private var lastBets: Map<BetType, Int>? = null
    private val chipHistory = mutableListOf<Pair<BetType, Int>>()

    var bankroll by mutableDoubleStateOf(STARTING_BANKROLL)
        private set
    var selectedChip by mutableIntStateOf(25)
    var phase by mutableStateOf(Phase.BETTING)
        private set
    var hand by mutableStateOf<BaccaratHand?>(null)
        private set
    var dealtPlayer by mutableIntStateOf(0)
        private set
    var dealtBanker by mutableIntStateOf(0)
        private set
    var revealedPlayer by mutableIntStateOf(0)
        private set
    var revealedBanker by mutableIntStateOf(0)
        private set
    var message by mutableStateOf("Place your bets")
        private set
    var lastReturn by mutableDoubleStateOf(0.0)
        private set

    /** Per-spot profit for the last hand: positive = won, zero = push. */
    var lastWinnings by mutableStateOf<List<Pair<BetType, Double>>>(emptyList())
        private set
    var shoeCount by mutableIntStateOf(shoe.cardsRemaining)
        private set

    val bets = mutableStateMapOf<BetType, Int>()
    val handLog = mutableStateListOf<HandRecord>()

    val totalStaked: Int get() = bets.values.sum()

    // ---- campaign ----

    var campaign by mutableStateOf(false)
        private set
    var goal by mutableDoubleStateOf(CAMPAIGN_GOAL)
        private set
    private var modeInitialized = false

    /** Switches between campaign (persistent shared wallet) and free play testing. */
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
        phase = Phase.BETTING
        hand = null
        dealtPlayer = 0
        dealtBanker = 0
        revealedPlayer = 0
        revealedBanker = 0
        bets.clear()
        chipHistory.clear()
        handLog.clear()
        lastReturn = 0.0
        lastWinnings = emptyList()
        bankroll = if (campaignMode) {
            prefs.getFloat("bankroll", CAMPAIGN_START.toFloat()).toDouble()
        } else STARTING_BANKROLL
        goal = prefs.getFloat("goal", CAMPAIGN_GOAL.toFloat()).toDouble()
        message = "Place your bets"
    }

    private fun persist() {
        if (campaign) prefs.edit().putFloat("bankroll", bankroll.toFloat()).apply()
    }

    /** Bank the win and chase a goal 100× bigger. */
    fun raiseGoal() {
        goal *= 100
        prefs.edit().putFloat("goal", goal.toFloat()).apply()
        message = "Place your bets"
    }

    /** Cash out the campaign and restart from scratch. */
    fun restartCampaign() {
        bankroll = CAMPAIGN_START
        goal = CAMPAIGN_GOAL
        prefs.edit()
            .putFloat("bankroll", bankroll.toFloat())
            .putFloat("goal", goal.toFloat())
            .apply()
        message = "Place your bets"
    }

    fun addBet(type: BetType) {
        if (phase != Phase.BETTING) return
        val amount = minOf(selectedChip, (bankroll - totalStaked).toInt())
        if (amount <= 0) {
            message = "No bankroll left"
            return
        }
        bets[type] = (bets[type] ?: 0) + amount
        chipHistory.add(type to amount)
        message = if (amount < selectedChip) "All in!" else "Place your bets"
    }

    fun clearBets() {
        if (phase != Phase.BETTING) return
        bets.clear()
        chipHistory.clear()
        message = "Place your bets"
    }

    /** Takes back the most recently placed chip only. */
    fun undoBet() {
        if (phase != Phase.BETTING) return
        val last = chipHistory.removeLastOrNull() ?: return
        val current = bets[last.first] ?: return
        val remaining = current - last.second
        if (remaining > 0) bets[last.first] = remaining else bets.remove(last.first)
    }

    fun deal() {
        if (phase != Phase.BETTING) return
        if (totalStaked == 0) {
            message = "Place a bet first"
            return
        }
        shoe.reshuffleIfBelow(RESHUFFLE_AT)
        val placed = bets.toMap()
        lastBets = placed
        chipHistory.clear()
        bankroll -= totalStaked
        persist()
        val dealt = BaccaratEngine.playHand(shoe::draw)
        shoeCount = shoe.cardsRemaining
        hand = dealt
        phase = Phase.DEALING
        // Both hands start with two face-down cards; a third only appears
        // later if the tableau calls for a draw.
        dealtPlayer = 2
        dealtBanker = 2
        revealedPlayer = 0
        revealedBanker = 0
        lastReturn = 0.0
        lastWinnings = emptyList()
        message = "Dealing…"

        viewModelScope.launch {
            delay(450)
            revealedPlayer = 1
            delay(520)
            revealedBanker = 1
            delay(520)
            revealedPlayer = 2
            delay(520)
            revealedBanker = 2
            if (dealt.player.size == 3) {
                delay(650)
                message = "Player draws…"
                dealtPlayer = 3
                delay(450)
                revealedPlayer = 3
            }
            if (dealt.banker.size == 3) {
                delay(650)
                message = "Banker draws…"
                dealtBanker = 3
                delay(450)
                revealedBanker = 3
            }
            delay(1000)

            val breakdown = BaccaratEngine.settleBreakdown(dealt, placed)
            val returned = breakdown.values.sum()
            bankroll += returned
            persist()
            lastReturn = returned
            lastWinnings = breakdown.entries
                .map { (type, ret) -> type to ret - (placed[type] ?: 0) }
                .sortedByDescending { it.second }
            handLog.add(HandRecord(dealt.outcome, returned - placed.values.sum()))
            val hi = maxOf(dealt.playerTotal, dealt.bankerTotal)
            val lo = minOf(dealt.playerTotal, dealt.bankerTotal)
            val who = when (dealt.outcome) {
                Outcome.PLAYER -> "Player wins $hi over $lo"
                Outcome.BANKER -> "Banker wins $hi over $lo"
                Outcome.TIE -> "Tie at $hi"
            }
            message = if (campaign && bankroll >= goal) {
                "🏆 GOAL REACHED!"
            } else {
                who + if (dealt.natural) " — natural" else ""
            }
            phase = Phase.RESULT
            bets.clear()
        }
    }

    fun nextHand(repeatBets: Boolean) {
        if (phase != Phase.RESULT) return
        phase = Phase.BETTING
        hand = null
        dealtPlayer = 0
        dealtBanker = 0
        revealedPlayer = 0
        revealedBanker = 0
        val previous = lastBets
        if (repeatBets && previous != null && previous.values.sum() <= bankroll) {
            bets.clear()
            bets.putAll(previous)
            chipHistory.clear()
            previous.forEach { (type, amount) -> chipHistory.add(type to amount) }
            message = "Same bets placed"
        } else {
            message = "Place your bets"
        }
    }

    fun buyBackIn() {
        if (phase == Phase.BETTING && totalStaked == 0 && bankroll < 25) {
            bankroll = if (campaign) CAMPAIGN_START else STARTING_BANKROLL
            persist()
            message = if (campaign) "Fresh start — road to \$1,000,000" else "Place your bets"
        }
    }
}
