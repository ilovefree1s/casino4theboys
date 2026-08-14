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

/** Which spot a chip went on, so Undo can take it back off the right one. */
private enum class Spot { MAIN, BLAZING, TRILUX }

/**
 * Blazing 777s: classic blackjack (3:2, dealer hits soft 17, doubles and splits
 * cost real money) plus the Blazing 7s and TriLux side bets.
 */
class Blazing777ViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("campaign", Context.MODE_PRIVATE)
    private val shoe = Shoe(decks = DECKS)
    private var lastBet = 0
    private var lastBlazing = 0
    private var lastTrilux = 0
    private val chipHistory = mutableListOf<Pair<Spot, Int>>()

    var bankroll by mutableDoubleStateOf(STARTING_BANKROLL)
        private set
    var selectedChip by mutableIntStateOf(25)
    var bet by mutableIntStateOf(0)
        private set
    var blazingBet by mutableIntStateOf(0)
        private set
    var triluxBet by mutableIntStateOf(0)
        private set
    var blazingStake by mutableIntStateOf(0)
        private set
    var triluxStake by mutableIntStateOf(0)
        private set
    var phase by mutableStateOf(BjPhase.BETTING)
        private set
    val playerHands = mutableStateListOf<BjHand>()
    val dealerCards = mutableStateListOf<Card>()
    var holeRevealed by mutableStateOf(false)
        private set
    var activeHand by mutableIntStateOf(0)
        private set
    var message by mutableStateOf("Place your bet")
        private set
    var results by mutableStateOf<List<BjResult>>(emptyList())
        private set
    var shoeCount by mutableIntStateOf(shoe.cardsRemaining)
        private set

    /** Side-bet outcomes, kept for the badges shown on the spots. */
    var blazingWin by mutableStateOf<Blazing777Rules.BlazingWin?>(null)
        private set
    var triluxWin by mutableStateOf<Blazing777Rules.TriluxWin?>(null)
        private set

    val totalAtRisk: Int
        get() = if (phase == BjPhase.BETTING) bet + blazingBet + triluxBet
        else playerHands.sumOf { it.stake } + blazingStake + triluxStake

    // ---- campaign ----

    var campaign by mutableStateOf(false)
        private set
    var goal by mutableDoubleStateOf(CAMPAIGN_GOAL)
        private set
    private var modeInitialized = false

    fun enterMode(campaignMode: Boolean) {
        if (modeInitialized && campaign == campaignMode) {
            if (campaignMode && phase == BjPhase.BETTING) {
                bankroll = prefs.getFloat("bankroll", CAMPAIGN_START.toFloat()).toDouble()
                goal = prefs.getFloat("goal", CAMPAIGN_GOAL.toFloat()).toDouble()
            }
            return
        }
        campaign = campaignMode
        modeInitialized = true
        phase = BjPhase.BETTING
        playerHands.clear()
        dealerCards.clear()
        chipHistory.clear()
        bet = 0; blazingBet = 0; triluxBet = 0
        blazingStake = 0; triluxStake = 0
        blazingWin = null; triluxWin = null
        results = emptyList()
        holeRevealed = false
        activeHand = 0
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

    private fun chipAmount(): Int =
        minOf(selectedChip, (bankroll - bet - blazingBet - triluxBet).toInt())

    private fun place(spot: Spot) {
        if (phase != BjPhase.BETTING) return
        val amount = chipAmount()
        if (amount <= 0) {
            message = "No bankroll left"
            return
        }
        when (spot) {
            Spot.MAIN -> bet += amount
            Spot.BLAZING -> blazingBet += amount
            Spot.TRILUX -> triluxBet += amount
        }
        chipHistory.add(spot to amount)
        message = when {
            amount < selectedChip -> "All in!"
            spot == Spot.BLAZING -> "Blazing 7s riding"
            spot == Spot.TRILUX -> "TriLux riding"
            else -> "Place your bet"
        }
    }

    fun addChip() = place(Spot.MAIN)
    fun addBlazingChip() = place(Spot.BLAZING)
    fun addTriluxChip() = place(Spot.TRILUX)

    fun undoChip() {
        if (phase != BjPhase.BETTING) return
        val last = chipHistory.removeLastOrNull() ?: return
        when (last.first) {
            Spot.MAIN -> bet = (bet - last.second).coerceAtLeast(0)
            Spot.BLAZING -> blazingBet = (blazingBet - last.second).coerceAtLeast(0)
            Spot.TRILUX -> triluxBet = (triluxBet - last.second).coerceAtLeast(0)
        }
    }

    fun clearBet() {
        if (phase != BjPhase.BETTING) return
        bet = 0; blazingBet = 0; triluxBet = 0
        chipHistory.clear()
        message = "Place your bet"
    }

    // ---- deal ----

    fun deal() {
        if (phase != BjPhase.BETTING) return
        if (bet <= 0) {
            message = if (blazingBet + triluxBet > 0) "Side bets ride with a main bet"
            else "Place a bet first"
            return
        }
        shoe.reshuffleIfBelow(RESHUFFLE_AT)
        bankroll -= bet + blazingBet + triluxBet
        persist()
        lastBet = bet; lastBlazing = blazingBet; lastTrilux = triluxBet
        blazingStake = blazingBet
        triluxStake = triluxBet
        chipHistory.clear()
        blazingWin = null; triluxWin = null
        playerHands.clear()
        dealerCards.clear()
        holeRevealed = false
        activeHand = 0
        results = emptyList()
        phase = BjPhase.DEALING
        message = "Dealing…"
        val wager = bet
        bet = 0; blazingBet = 0; triluxBet = 0

        viewModelScope.launch {
            playerHands.add(BjHand(emptyList(), stake = wager, betUnit = wager))
            delay(350)
            dealToPlayer(0)
            delay(400)
            dealerCards.add(shoe.draw())
            delay(400)
            dealToPlayer(0)
            delay(400)
            dealerCards.add(shoe.draw()) // hole, face down
            shoeCount = shoe.cardsRemaining

            // Side bets are decided by the three face-up cards.
            val threeCards = playerHands[0].cards + dealerCards[0]
            blazingWin = Blazing777Rules.blazing(threeCards)
            triluxWin = Blazing777Rules.trilux(threeCards)
            if (blazingStake > 0 && blazingWin != null || triluxStake > 0 && triluxWin != null) {
                delay(500)
                message = sideBetShout()
            }

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

            if (BlackjackCore.isBlackjack(playerHands[0].cards)) {
                message = "Blackjack!"
                playerHands[0] = playerHands[0].copy(done = true)
                delay(700)
                dealerTurn()
            } else {
                phase = BjPhase.PLAYER_TURN
                message = "Your move"
            }
        }
    }

    private fun sideBetShout(): String {
        val hits = buildList {
            if (blazingStake > 0) blazingWin?.let { add(it.label) }
            if (triluxStake > 0) triluxWin?.let { add(it.label) }
        }
        return if (hits.isEmpty()) message else hits.joinToString(" · ") + "!"
    }

    private fun dealToPlayer(index: Int) {
        val h = playerHands[index]
        playerHands[index] = h.copy(cards = h.cards + shoe.draw())
        shoeCount = shoe.cardsRemaining
    }

    // ---- player actions ----

    private val current: BjHand? get() = playerHands.getOrNull(activeHand)

    val canHit: Boolean get() = phase == BjPhase.PLAYER_TURN && current?.done == false
    val canStand: Boolean get() = canHit
    val canDouble: Boolean
        get() {
            val h = current ?: return false
            if (phase != BjPhase.PLAYER_TURN || h.done || h.cards.size != 2) return false
            return bankroll >= h.betUnit
        }
    val canSplit: Boolean
        get() {
            val h = current ?: return false
            if (phase != BjPhase.PLAYER_TURN || h.done || playerHands.size >= 4) return false
            if (!FreeBetRules.canSplit(h.cards)) return false
            return bankroll >= h.betUnit
        }

    fun hit() {
        if (!canHit) return
        val i = activeHand
        val h = playerHands[i]
        val cards = h.cards + shoe.draw()
        shoeCount = shoe.cardsRemaining
        val bust = BlackjackCore.isBust(cards)
        val stop = bust || BlackjackCore.total(cards) == 21
        playerHands[i] = h.copy(cards = cards, done = stop)
        if (bust) message = "Bust"
        if (stop) advance()
    }

    fun stand() {
        if (!canStand) return
        playerHands[activeHand] = playerHands[activeHand].copy(done = true)
        advance()
    }

    fun doubleDown() {
        if (!canDouble) return
        val i = activeHand
        val h = playerHands[i]
        bankroll -= h.betUnit
        persist()
        val cards = h.cards + shoe.draw()
        shoeCount = shoe.cardsRemaining
        playerHands[i] = h.copy(
            cards = cards, stake = h.stake + h.betUnit, doubled = true, done = true,
        )
        if (BlackjackCore.isBust(cards)) message = "Bust"
        advance()
    }

    fun split() {
        if (!canSplit) return
        val i = activeHand
        val h = playerHands[i]
        bankroll -= h.betUnit
        persist()
        val aces = h.cards[0].rank == Rank.ACE && h.cards[1].rank == Rank.ACE
        phase = BjPhase.DEALING
        message = "Split"
        viewModelScope.launch {
            playerHands[i] = h.copy(cards = listOf(h.cards[0]))
            playerHands.add(
                i + 1,
                BjHand(
                    cards = listOf(h.cards[1]),
                    stake = h.betUnit,
                    betUnit = h.betUnit,
                )
            )
            delay(450)
            dealToPlayer(i)
            delay(450)
            dealToPlayer(i + 1)
            delay(300)
            if (aces) {
                playerHands[i] = playerHands[i].copy(done = true)
                playerHands[i + 1] = playerHands[i + 1].copy(done = true)
                advance()
            } else {
                if (BlackjackCore.total(playerHands[i].cards) == 21) {
                    playerHands[i] = playerHands[i].copy(done = true)
                }
                if (playerHands[i].done) {
                    advance()
                } else {
                    activeHand = i
                    phase = BjPhase.PLAYER_TURN
                    message = "Your move — hand ${i + 1}"
                }
            }
        }
    }

    private fun advance() {
        if (phase != BjPhase.PLAYER_TURN && phase != BjPhase.DEALING) return
        val next = playerHands.indexOfFirst { !it.done }
        if (next >= 0) {
            activeHand = next
            phase = BjPhase.PLAYER_TURN
            if (playerHands.size > 1) message = "Your move — hand ${next + 1}"
            if (BlackjackCore.total(playerHands[next].cards) == 21) {
                playerHands[next] = playerHands[next].copy(done = true)
                advance()
            }
            return
        }
        dealerTurn()
    }

    private fun dealerTurn() {
        viewModelScope.launch {
            phase = BjPhase.DEALER_TURN
            message = "Dealer plays…"
            delay(450)
            holeRevealed = true
            delay(650)
            val soleBlackjack =
                playerHands.size == 1 && BlackjackCore.isBlackjack(playerHands[0].cards)
            val anyLive = playerHands.any { !BlackjackCore.isBust(it.cards) }
            if (anyLive && !soleBlackjack) {
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
        val dealerBj = BlackjackCore.isBlackjack(dealerCards)
        val dTotal = BlackjackCore.total(dealerCards)
        var totalReturn = 0.0
        val out = mutableListOf<BjResult>()

        playerHands.forEachIndexed { i, h ->
            val pTotal = BlackjackCore.total(h.cards)
            val playerBj =
                playerHands.size == 1 && !h.doubled && BlackjackCore.isBlackjack(h.cards)
            val returned: Double = when {
                playerBj && dealerBj -> h.stake.toDouble()
                playerBj -> h.stake + 1.5 * h.betUnit
                dealerBj -> 0.0
                BlackjackCore.isBust(h.cards) -> 0.0
                dTotal > 21 -> h.stake * 2.0
                pTotal > dTotal -> h.stake * 2.0
                pTotal == dTotal -> h.stake.toDouble()
                else -> 0.0
            }
            totalReturn += returned
            val label = if (playerHands.size > 1) "Hand ${i + 1}" else
                if (playerBj) "Blackjack" else "Hand"
            out.add(BjResult(label, returned - h.stake))
        }

        if (blazingStake > 0) {
            val win = blazingWin
            val ret = if (win != null) blazingStake * (win.payout + 1.0) else 0.0
            totalReturn += ret
            out.add(BjResult(win?.let { "Blazing · ${it.label}" } ?: "Blazing 7s", ret - blazingStake))
        }
        if (triluxStake > 0) {
            val win = triluxWin
            val ret = if (win != null) triluxStake * (win.payout + 1.0) else 0.0
            totalReturn += ret
            out.add(BjResult(win?.let { "TriLux · ${it.label}" } ?: "TriLux", ret - triluxStake))
        }

        bankroll += totalReturn
        persist()
        val staked = playerHands.sumOf { it.stake } + blazingStake + triluxStake
        val net = totalReturn - staked
        message = when {
            campaign && bankroll >= goal -> "🏆 GOAL REACHED!"
            dealerBj -> if (net >= 0) "Dealer blackjack — push" else "Dealer blackjack"
            net > 0 -> "You win"
            net < 0 -> "Dealer wins"
            else -> "Push"
        }
        results = out
        phase = BjPhase.RESULT
    }

    fun nextHand(repeatBet: Boolean) {
        if (phase != BjPhase.RESULT) return
        phase = BjPhase.BETTING
        playerHands.clear()
        dealerCards.clear()
        holeRevealed = false
        activeHand = 0
        results = emptyList()
        blazingStake = 0; triluxStake = 0
        blazingWin = null; triluxWin = null
        chipHistory.clear()
        val repeatCost = lastBet + lastBlazing + lastTrilux
        if (repeatBet && repeatCost <= bankroll) {
            bet = lastBet; blazingBet = lastBlazing; triluxBet = lastTrilux
        } else if (repeatBet && lastBet <= bankroll) {
            bet = lastBet; blazingBet = 0; triluxBet = 0
        } else {
            bet = 0; blazingBet = 0; triluxBet = 0
        }
        message = "Place your bet"
    }
}
