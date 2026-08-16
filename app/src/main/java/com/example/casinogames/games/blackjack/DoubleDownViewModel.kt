package com.example.casinogames.games.blackjack

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Shoe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DECKS = 8
private const val RESHUFFLE_AT = 30
private const val STARTING_BANKROLL = 5000.0
private const val CAMPAIGN_START = 5000.0
private const val CAMPAIGN_GOAL = 1_000_000.0

/**
 * Double Down Madness. The player is dealt a single card and then chooses,
 * at every step, whether to take another or to double — the double may come
 * on the first card or the fifth. Doubling buys exactly one more card and ends
 * the hand, as it does anywhere else.
 *
 * The house takes its side of the bargain back with the dealer's 22: a dealer
 * who lands on it pushes a live hand instead of losing to it.
 */
class DoubleDownViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("campaign", Context.MODE_PRIVATE)
    private val shoe = Shoe(decks = DECKS)

    var bankroll by mutableDoubleStateOf(STARTING_BANKROLL)
        private set
    var selectedChip by mutableIntStateOf(25)
    var bet by mutableIntStateOf(0)
        private set
    var shoeCount by mutableIntStateOf(shoe.cardsRemaining)
        private set

    /** One hand only — there is nothing to split when you start with one card. */
    var hand by mutableStateOf<BjHand?>(null)
        private set
    val dealerCards = mutableStateListOf<Card>()
    var holeRevealed by mutableStateOf(false)
        private set

    var phase by mutableStateOf(BjPhase.BETTING)
        private set
    var message by mutableStateOf("Place your bet")
        private set
    var results by mutableStateOf<List<BjResult>>(emptyList())
        private set
    /** True when the dealer's 22 saved the hand, so the table can say so. */
    var pushed22 by mutableStateOf(false)
        private set

    private var lastBet = 0
    private val chipHistory = mutableListOf<Int>()

    val totalStaked: Int get() = if (phase == BjPhase.BETTING) bet else hand?.stake ?: 0

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
        phase = BjPhase.BETTING
        hand = null
        dealerCards.clear()
        chipHistory.clear()
        bet = 0
        results = emptyList()
        holeRevealed = false
        pushed22 = false
        bankroll = if (campaignMode) {
            prefs.getFloat("bankroll", CAMPAIGN_START.toFloat()).toDouble()
        } else STARTING_BANKROLL
        goal = prefs.getFloat("goal", CAMPAIGN_GOAL.toFloat()).toDouble()
        message = "Place your bet"
    }

    private fun persist() {
        if (campaign) prefs.edit().putFloat("bankroll", bankroll.toFloat()).apply()
    }

    fun raiseGoal() {
        goal *= 100
        prefs.edit().putFloat("goal", goal.toFloat()).apply()
        message = "Place your bet"
    }

    fun restartCampaign() {
        bankroll = CAMPAIGN_START
        goal = CAMPAIGN_GOAL
        prefs.edit()
            .putFloat("bankroll", bankroll.toFloat())
            .putFloat("goal", goal.toFloat())
            .apply()
        message = "Place your bet"
    }

    fun buyBackIn() {
        if (phase == BjPhase.BETTING && bet == 0 && bankroll < 25) {
            bankroll = if (campaign) CAMPAIGN_START else STARTING_BANKROLL
            persist()
            message = if (campaign) "Fresh start — road to \$1,000,000" else "Place your bet"
        }
    }

    // ---- betting ----

    fun addChip() {
        if (phase != BjPhase.BETTING) return
        val amount = minOf(selectedChip, (bankroll - bet).toInt())
        if (amount <= 0) {
            message = "No bankroll left"
            return
        }
        bet += amount
        chipHistory.add(amount)
        message = if (amount < selectedChip) "All in!" else "Place your bet"
    }

    fun undoChip() {
        if (phase != BjPhase.BETTING) return
        val last = chipHistory.removeLastOrNull() ?: return
        bet = (bet - last).coerceAtLeast(0)
    }

    fun clearBet() {
        if (phase != BjPhase.BETTING) return
        bet = 0
        chipHistory.clear()
        message = "Place your bet"
    }

    // ---- deal ----

    fun deal() {
        if (phase != BjPhase.BETTING) return
        if (bet <= 0) {
            message = "Place a bet first"
            return
        }
        shoe.reshuffleIfBelow(RESHUFFLE_AT)
        bankroll -= bet
        persist()
        lastBet = bet
        chipHistory.clear()
        dealerCards.clear()
        holeRevealed = false
        pushed22 = false
        results = emptyList()
        phase = BjPhase.DEALING
        message = "Dealing…"
        val wager = bet
        bet = 0

        viewModelScope.launch {
            // One card to the player, two to the dealer.
            hand = BjHand(emptyList(), stake = wager, betUnit = wager)
            delay(350)
            draw()
            delay(400)
            dealerCards.add(shoe.draw())
            delay(400)
            dealerCards.add(shoe.draw()) // hole, face down
            shoeCount = shoe.cardsRemaining

            val up = dealerCards[0]
            if (up.rank == Rank.ACE || BlackjackCore.cardValue(up.rank) == 10) {
                delay(450)
                message = "Dealer checks for blackjack…"
                delay(900)
                if (BlackjackCore.isBlackjack(dealerCards)) {
                    holeRevealed = true
                    delay(700)
                    settle()
                    return@launch
                }
                message = "No blackjack"
                delay(450)
            }
            phase = BjPhase.PLAYER_TURN
            message = "One card down — hit or double"
        }
    }

    private fun draw() {
        val h = hand ?: return
        hand = h.copy(cards = h.cards + shoe.draw())
        shoeCount = shoe.cardsRemaining
    }

    // ---- player actions ----

    private val live: BjHand? get() = hand?.takeIf { !it.done && phase == BjPhase.PLAYER_TURN }

    val canHit: Boolean get() = live != null
    /** The madness: any hand, at any size, so long as the bankroll covers it. */
    val canDouble: Boolean get() = live?.let { bankroll >= it.betUnit } == true
    /** Standing on a single card is never a play, so it waits for the second. */
    val canStand: Boolean get() = live?.let { it.cards.size >= 2 } == true

    fun hit() {
        if (live == null) return
        draw()
        val cards = hand?.cards ?: return
        val bust = BlackjackCore.isBust(cards)
        val twentyOne = BlackjackCore.total(cards) == 21
        if (bust || twentyOne) hand = hand?.copy(done = true)
        message = when {
            bust -> "Bust"
            twentyOne -> "Twenty-one"
            else -> "Hit or double"
        }
        if (bust || twentyOne) dealerTurn()
    }

    fun doubleDown() {
        val h = live ?: return
        if (bankroll < h.betUnit) return
        bankroll -= h.betUnit
        persist()
        hand = h.copy(stake = h.stake + h.betUnit, doubled = true)
        message = "Double!"
        viewModelScope.launch {
            phase = BjPhase.DEALING
            delay(400)
            draw()
            hand = hand?.copy(done = true)
            if (BlackjackCore.isBust(hand?.cards ?: emptyList())) message = "Bust"
            delay(500)
            dealerTurn()
        }
    }

    fun stand() {
        if (!canStand) return
        hand = hand?.copy(done = true)
        dealerTurn()
    }

    private fun dealerTurn() {
        viewModelScope.launch {
            phase = BjPhase.DEALER_TURN
            message = "Dealer plays…"
            delay(450)
            holeRevealed = true
            delay(650)
            val cards = hand?.cards ?: emptyList()
            if (!BlackjackCore.isBust(cards) && !BlackjackCore.isBlackjack(cards)) {
                while (BlackjackCore.dealerShouldHit(dealerCards)) {
                    delay(650)
                    dealerCards.add(shoe.draw())
                    shoeCount = shoe.cardsRemaining
                }
            }
            delay(550)
            settle()
        }
    }

    private fun settle() {
        val h = hand
        val dealerBj = BlackjackCore.isBlackjack(dealerCards)
        val dTotal = BlackjackCore.total(dealerCards)
        // The dealer's 22 pays for all that doubling: it pushes rather than loses.
        val push22 = dTotal == 22
        var totalReturn = 0.0
        val out = mutableListOf<BjResult>()

        if (h != null) {
            val pTotal = BlackjackCore.total(h.cards)
            val playerBj = !h.doubled && BlackjackCore.isBlackjack(h.cards)
            val returned: Double = when {
                playerBj && dealerBj -> h.stake.toDouble()
                playerBj -> h.stake + 1.5 * h.betUnit
                dealerBj -> 0.0
                BlackjackCore.isBust(h.cards) -> 0.0
                push22 -> h.stake.toDouble()
                dTotal > 21 -> h.stake * 2.0
                pTotal > dTotal -> h.stake * 2.0
                pTotal == dTotal -> h.stake.toDouble()
                else -> 0.0
            }
            pushed22 = push22 && !BlackjackCore.isBust(h.cards) && !dealerBj
            totalReturn += returned
            out.add(BjResult(if (playerBj) "Blackjack" else "Hand", returned - h.stake))
        }

        bankroll += totalReturn
        persist()
        val staked = h?.stake ?: 0
        val net = totalReturn - staked
        message = when {
            campaign && bankroll >= goal -> "🏆 GOAL REACHED!"
            pushed22 -> "Dealer 22 — push"
            dealerBj -> if (net >= 0) "Dealer blackjack — push" else "Dealer blackjack"
            net > 0 -> "You win"
            net < 0 -> "Dealer wins"
            else -> "Push"
        }
        results = out
        phase = BjPhase.RESULT
    }

    fun nextHand(repeat: Boolean) {
        if (phase != BjPhase.RESULT) return
        phase = BjPhase.BETTING
        hand = null
        dealerCards.clear()
        holeRevealed = false
        pushed22 = false
        results = emptyList()
        chipHistory.clear()
        bet = if (repeat && lastBet <= bankroll) lastBet else 0
        message = "Place your bet"
    }
}
