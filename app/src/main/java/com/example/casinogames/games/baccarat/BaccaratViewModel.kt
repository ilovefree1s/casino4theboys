package com.example.casinogames.games.baccarat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.casinogames.campaign.Campaign
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.campaign.limitsFor
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

class BaccaratViewModel(app: Application) : AndroidViewModel(app) {
    private val shoe = Shoe(decks = DECKS)
    private var lastBets: Map<BetType, Int>? = null
    private val chipHistory = mutableListOf<Pair<BetType, Int>>()

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
    val goal: Double get() = Campaign.goal
    private var modeInitialized = false

    /** Switches between campaign (persistent shared wallet) and free play testing. */
    fun enterMode(campaignMode: Boolean) {
        // The campaign purse is shared and live, so there is nothing to read
        // back when another table has been at it — only free play needs a fill.
        if (modeInitialized && campaign == campaignMode) return
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
        if (!campaignMode) freePurse = FreePlay.buyIn
        message = "Place your bets"
    }

    /** Bank the win and chase a goal 100× bigger. */
    fun raiseGoal() {
        Campaign.raiseGoal()
        message = "Place your bets"
    }

    /** Cash out the campaign and restart from scratch. */
    fun restartCampaign() {
        Campaign.restart()
        message = "Place your bets"
    }

    fun addBet(type: BetType) {
        if (phase != Phase.BETTING) return
        val affordable = minOf(selectedChip, (bankroll - totalStaked).toInt())
        if (affordable <= 0) {
            message = "No bankroll left"
            return
        }
        val amount = limits.allow(affordable, bets[type] ?: 0, type.isSide)
        if (amount <= 0) {
            message = limits.refusal(type.isSide)
            return
        }
        bets[type] = (bets[type] ?: 0) + amount
        chipHistory.add(type to amount)
        message = if (amount < selectedChip) "All in!" else "Place your bets"
    }

    /** Slides a whole stack from one spot to another, limits permitting. */
    fun moveBet(from: BetType, to: BetType) {
        if (phase != Phase.BETTING || from == to) return
        val amount = bets[from] ?: return
        val allowed = limits.allow(amount, bets[to] ?: 0, to.isSide)
        if (allowed < amount) {
            // Dropped on a spot already at its cap: the stack comes off the
            // felt and the money never left the purse — staking happens at
            // the deal.
            bets.remove(from)
            chipHistory.removeAll { it.first == from }
            message = "${to.displayName} is full — bet taken down"
            return
        }
        bets.remove(from)
        bets[to] = (bets[to] ?: 0) + amount
        // The history follows the chips, so undo keeps meaning something.
        for (i in chipHistory.indices) {
            if (chipHistory[i].first == from) chipHistory[i] = to to chipHistory[i].second
        }
        message = "${to.displayName} — bet moved"
    }

    /** Drag a stack off the felt onto the rack: the bet comes down. */
    fun removeBet(type: BetType) {
        if (phase != Phase.BETTING) return
        if (bets.remove(type) != null) {
            chipHistory.removeAll { it.first == type }
            message = "Bet taken down"
        }
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
        spend(totalStaked.toDouble())
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
            collect(returned.toDouble())
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
            // Broke in the campaign is a marker, not a free reset: five
            // thousand over the table, seven and a half written down.
            if (campaign) Campaign.takeMarker() else freePurse = FreePlay.buyIn
            message = if (campaign) "Marker signed — dig out" else "Place your bets"
        }
    }
}
