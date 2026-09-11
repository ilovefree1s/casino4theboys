package com.example.casinogames.games.shootout

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.casinogames.campaign.Campaign
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.campaign.limitsFor
import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Shoe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Four each, five on the board, and a split can want four more: keep a margin. */
private const val RESHUFFLE_AT = 20

enum class ShootoutPhase { BETTING, DEALING, CHOOSING, RUNOUT, SHOWDOWN, RESULT }

/** One line of the settlement breakdown. */
data class ShootoutResult(val label: String, val net: Double)

private enum class Spot { POKER, BONUS, BAD_BEAT }

/**
 * Texas Shootout. Post a poker bet and, if wanted, a bonus and a bad beat;
 * take four cards; keep two of them — or split them into two hands for a
 * second poker bet (and second side bets, if any were made). The dealer keeps
 * two by house way, five come out on the board, and the dealer takes every tie.
 */
class ShootoutViewModel(app: Application) : AndroidViewModel(app) {
    private val shoe = Shoe(decks = ShootoutRules.DECKS)
    private var lastPoker = 0
    private var lastBonus = 0
    private var lastBadBeat = 0
    private val chipHistory = mutableListOf<Pair<Spot, Int>>()

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

    /** Pending bets while betting. */
    var poker by mutableIntStateOf(0)
        private set
    var bonus by mutableIntStateOf(0)
        private set
    var badBeat by mutableIntStateOf(0)
        private set

    /** Locked-in stakes per hand once dealt; a split hand posts the same again. */
    var pokerStake by mutableIntStateOf(0)
        private set
    var bonusStake by mutableIntStateOf(0)
        private set
    var badBeatStake by mutableIntStateOf(0)
        private set
    var split by mutableStateOf(false)
        private set

    /** The four dealt, and which of them are picked to keep. */
    val playerFour = mutableStateListOf<Card>()
    val picked = mutableStateListOf<Int>()

    /** After the choice: one hand, or two when split. */
    var hands by mutableStateOf<List<List<Card>>>(emptyList())
        private set
    val dealerFour = mutableStateListOf<Card>()
    var dealerKept by mutableStateOf<List<Card>>(emptyList())
        private set
    var dealerRevealed by mutableStateOf(false)
        private set
    val board = mutableStateListOf<Card>()

    var phase by mutableStateOf(ShootoutPhase.BETTING)
        private set
    var message by mutableStateOf("Place your poker bet")
        private set
    var settlements by mutableStateOf<List<ShootoutRules.HandSettlement>>(emptyList())
        private set
    var results by mutableStateOf<List<ShootoutResult>>(emptyList())
        private set

    val handCount: Int get() = if (split) 2 else 1

    val totalAtRisk: Int
        get() = if (phase == ShootoutPhase.BETTING) poker + bonus + badBeat
        else (pokerStake + bonusStake + badBeatStake) * handCount

    // ---- campaign ----

    var campaign by mutableStateOf(false)
        private set
    val goal: Double get() = Campaign.goal
    private var modeInitialized = false

    fun enterMode(campaignMode: Boolean) {
        if (modeInitialized && campaign == campaignMode) return
        campaign = campaignMode
        modeInitialized = true
        clearTable()
        poker = 0; bonus = 0; badBeat = 0
        if (!campaignMode) freePurse = FreePlay.buyIn
        message = "Place your poker bet"
    }

    fun raiseGoal() {
        Campaign.raiseGoal()
        message = "Place your poker bet"
    }

    fun restartCampaign() {
        Campaign.restart()
        message = "Place your poker bet"
    }

    fun buyBackIn() {
        if (phase != ShootoutPhase.BETTING || totalAtRisk > 0 || bankroll >= 25) return
        if (campaign) Campaign.takeMarker() else freePurse = FreePlay.buyIn
        message = "Place your poker bet"
    }

    // ---- betting ----

    fun addPoker() = place(Spot.POKER)
    fun addBonus() = place(Spot.BONUS)
    fun addBadBeat() = place(Spot.BAD_BEAT)

    private fun place(spot: Spot) {
        if (phase != ShootoutPhase.BETTING) return
        val room = (bankroll - (poker + bonus + badBeat)).toInt()
        val affordable = minOf(selectedChip, room)
        if (affordable <= 0) {
            message = "Not enough for that chip"
            return
        }
        val lim = limits
        val amount = when (spot) {
            Spot.POKER -> lim.allow(affordable, poker)
            Spot.BONUS -> lim.allow(affordable, bonus, side = true)
            Spot.BAD_BEAT -> lim.allowBadBeat(affordable, badBeat)
        }
        if (amount <= 0) {
            message = when (spot) {
                Spot.BAD_BEAT -> lim.badBeatRefusal()
                else -> lim.refusal(side = spot == Spot.BONUS)
            }
            return
        }
        when (spot) {
            Spot.POKER -> poker += amount
            Spot.BONUS -> bonus += amount
            Spot.BAD_BEAT -> badBeat += amount
        }
        chipHistory.add(spot to amount)
        message = when {
            amount < selectedChip -> "All in!"
            spot == Spot.BONUS -> "Bonus riding"
            spot == Spot.BAD_BEAT -> "Bad Beat riding"
            else -> "Poker bet posted"
        }
    }

    fun undoChip() {
        if (phase != ShootoutPhase.BETTING) return
        val last = chipHistory.removeLastOrNull() ?: return
        when (last.first) {
            Spot.POKER -> poker = (poker - last.second).coerceAtLeast(0)
            Spot.BONUS -> bonus = (bonus - last.second).coerceAtLeast(0)
            Spot.BAD_BEAT -> badBeat = (badBeat - last.second).coerceAtLeast(0)
        }
    }

    fun clearBets() {
        if (phase != ShootoutPhase.BETTING) return
        poker = 0; bonus = 0; badBeat = 0
        chipHistory.clear()
        message = "Place your poker bet"
    }

    // ---- the deal ----

    fun deal() {
        if (phase != ShootoutPhase.BETTING) return
        if (poker <= 0) {
            message = if (bonus > 0 || badBeat > 0) "The side bets ride with a poker bet"
            else "Place a poker bet first"
            return
        }
        shoe.reshuffleIfBelow(RESHUFFLE_AT)
        spend((poker + bonus + badBeat).toDouble())
        lastPoker = poker; lastBonus = bonus; lastBadBeat = badBeat
        pokerStake = poker; bonusStake = bonus; badBeatStake = badBeat
        chipHistory.clear()
        clearCards()
        poker = 0; bonus = 0; badBeat = 0
        phase = ShootoutPhase.DEALING
        message = "Dealing…"

        viewModelScope.launch {
            repeat(4) {
                delay(190)
                playerFour.add(shoe.draw())
                delay(140)
                dealerFour.add(shoe.draw())
            }
            delay(260)
            phase = ShootoutPhase.CHOOSING
            message = "Pick two cards to keep"
        }
    }

    // ---- the one decision ----

    fun togglePick(index: Int) {
        if (phase != ShootoutPhase.CHOOSING) return
        if (picked.remove(index)) return
        if (picked.size >= 2) picked.removeAt(0)
        picked.add(index)
        message = when (picked.size) {
            2 -> "Keep these two, or split and play both"
            else -> "Pick one more"
        }
    }

    val canKeep: Boolean get() = phase == ShootoutPhase.CHOOSING && picked.size == 2

    /** A split posts the poker bet again, and the side bets again if there were any. */
    val splitCost: Int get() = pokerStake + bonusStake + badBeatStake
    val canSplit: Boolean get() = canKeep && bankroll >= splitCost

    val canTakeMarker: Boolean
        get() = campaign && canKeep && bankroll < splitCost && Campaign.canTakeMarker

    fun takeMarker() {
        if (!canTakeMarker) return
        Campaign.takeMarker()
        message = "Marker taken — split if you like"
    }

    fun keepTwo() {
        if (!canKeep) return
        split = false
        hands = listOf(picked.sorted().map { playerFour[it] })
        runOut()
    }

    fun splitHands() {
        if (!canSplit) return
        spend(splitCost.toDouble())
        split = true
        val kept = picked.sorted()
        val rest = playerFour.indices.filter { it !in kept }
        hands = listOf(kept.map { playerFour[it] }, rest.map { playerFour[it] })
        runOut()
    }

    private fun runOut() {
        phase = ShootoutPhase.RUNOUT
        dealerKept = ShootoutRules.houseWay(dealerFour.toList())
        message = if (split) "Two hands in — the board comes out" else "The board comes out"
        viewModelScope.launch {
            delay(350)
            repeat(5) {
                board.add(shoe.draw())
                delay(230)
            }
            phase = ShootoutPhase.SHOWDOWN
            message = "Dealer sets a hand…"
            delay(500)
            dealerRevealed = true
            delay(700)
            settle()
        }
    }

    private fun settle() {
        val settled = hands.map {
            ShootoutRules.settle(
                playerHole = it,
                dealerHole = dealerKept,
                board = board.toList(),
                poker = pokerStake.toDouble(),
                bonus = bonusStake.toDouble(),
                badBeat = badBeatStake.toDouble(),
            )
        }
        settlements = settled
        val totalReturn = settled.sumOf { it.totalReturn }
        collect(totalReturn)

        val out = mutableListOf<ShootoutResult>()
        settled.forEachIndexed { i, s ->
            val tag = if (split) "Hand ${i + 1} · " else ""
            out.add(ShootoutResult("${tag}Poker", s.pokerReturn - pokerStake))
            if (bonusStake > 0) {
                out.add(
                    ShootoutResult(
                        tag + (s.bonusWin?.let { "Bonus · ${it.label}" } ?: "Bonus"),
                        s.bonusReturn - bonusStake,
                    ),
                )
            }
            if (badBeatStake > 0) {
                out.add(
                    ShootoutResult(
                        tag + (s.badBeatWin?.let { "Bad Beat · ${it.label}" } ?: "Bad Beat"),
                        s.badBeatReturn - badBeatStake,
                    ),
                )
            }
        }
        results = out

        val staked = (pokerStake + bonusStake + badBeatStake) * handCount
        val net = totalReturn - staked
        val wins = settled.count { it.outcome == ShootoutRules.Outcome.WIN }
        val bigBonus = settled.mapNotNull { it.bonusWin }.minByOrNull { it.ordinal }
        val badBeatHit = settled.mapNotNull { it.badBeatWin }.minByOrNull { it.rung.ordinal }
        message = when {
            campaign && bankroll >= goal -> "🏆 GOAL REACHED!"
            badBeatHit != null -> "${badBeatHit.label} — bad beat pays ${formatWhole(badBeatHit.rung.payout)} to 1"
            bigBonus != null && bigBonus.payout >= 40 -> "${bigBonus.label} — bonus pays ${bigBonus.payout} to 1"
            split && wins == 2 -> "Both hands win!"
            split && wins == 1 -> "One hand each"
            wins == 1 -> "You win with ${settled[0].playerHand.category.label}"
            net > 0 -> "Up ${net.toInt()} on the bonus"
            else -> "Dealer wins with ${settled[0].dealerHand.category.label}"
        }
        phase = ShootoutPhase.RESULT
    }

    fun nextHand(repeat: Boolean) {
        if (phase != ShootoutPhase.RESULT) return
        clearTable()
        if (repeat && lastPoker + lastBonus + lastBadBeat <= bankroll) {
            poker = lastPoker; bonus = lastBonus; badBeat = lastBadBeat
        } else if (repeat && lastPoker <= bankroll) {
            poker = lastPoker; bonus = 0; badBeat = 0
        } else {
            poker = 0; bonus = 0; badBeat = 0
        }
        message = "Place your poker bet"
    }

    private fun formatWhole(v: Int): String = String.format(java.util.Locale.US, "%,d", v)

    private fun clearCards() {
        playerFour.clear(); picked.clear(); hands = emptyList()
        dealerFour.clear(); dealerKept = emptyList(); dealerRevealed = false
        board.clear()
        settlements = emptyList(); results = emptyList()
    }

    private fun clearTable() {
        phase = ShootoutPhase.BETTING
        clearCards()
        chipHistory.clear()
        pokerStake = 0; bonusStake = 0; badBeatStake = 0; split = false
    }
}
