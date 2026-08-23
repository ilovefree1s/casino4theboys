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
    /** The Push 22 side bet, before and after the cards come out. */
    var push22Bet by mutableIntStateOf(0)
        private set
    var push22Stake by mutableIntStateOf(0)
        private set
    var push22Win by mutableStateOf<DoubleDownRules.Push22Win?>(null)
        private set
    /**
     * The Pair Square side bet. It is read off the first two cards, and on
     * this table the second card comes mid-hand, so it is answered the moment
     * that card lands rather than at the settle.
     */
    var pairSquareBet by mutableIntStateOf(0)
        private set
    var pairSquareStake by mutableIntStateOf(0)
        private set
    var pairSquareWin by mutableStateOf<DoubleDownRules.PairSquareWin?>(null)
        private set
    /** Set once the first two cards are in and the side bet has its answer. */
    var pairSquareSettled by mutableStateOf(false)
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
    /** Pair Square files its own verdict: it is settled before the hand is. */
    var pairSquareResult by mutableStateOf<BjResult?>(null)
        private set

    private var lastBet = 0
    private var lastPush22 = 0
    private var lastPairSquare = 0
    private val chipHistory = mutableListOf<Pair<Spot, Int>>()

    enum class Spot { BET, PUSH_22, PAIR_SQUARE }

    val totalStaked: Int
        get() = if (phase == BjPhase.BETTING) bet + push22Bet + pairSquareBet
        else (hand?.stake ?: 0) + push22Stake + pairSquareStake

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
        push22Bet = 0
        push22Stake = 0
        push22Win = null
        pairSquareBet = 0
        pairSquareStake = 0
        pairSquareWin = null
        pairSquareResult = null
        pairSquareSettled = false
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

    /** Nothing on the felt, either spot — the table is only busted then. */
    val nothingAtStake: Boolean get() = bet == 0 && push22Bet == 0 && pairSquareBet == 0

    fun buyBackIn() {
        if (phase == BjPhase.BETTING && nothingAtStake && bankroll < 25) {
            bankroll = if (campaign) CAMPAIGN_START else STARTING_BANKROLL
            persist()
            message = if (campaign) "Fresh start — road to \$1,000,000" else "Place your bet"
        }
    }

    // ---- betting ----

    fun addChip() = addChip(Spot.BET)

    fun addPush22Chip() = addChip(Spot.PUSH_22)

    fun addPairSquareChip() = addChip(Spot.PAIR_SQUARE)

    private fun addChip(spot: Spot) {
        if (phase != BjPhase.BETTING) return
        val amount = minOf(selectedChip, (bankroll - bet - push22Bet - pairSquareBet).toInt())
        if (amount <= 0) {
            message = "No bankroll left"
            return
        }
        when (spot) {
            Spot.BET -> bet += amount
            Spot.PUSH_22 -> push22Bet += amount
            Spot.PAIR_SQUARE -> pairSquareBet += amount
        }
        chipHistory.add(spot to amount)
        message = when {
            amount < selectedChip -> "All in!"
            spot == Spot.PUSH_22 -> "Push 22 riding"
            spot == Spot.PAIR_SQUARE -> "Pair Square riding"
            else -> "Place your bet"
        }
    }

    fun undoChip() {
        if (phase != BjPhase.BETTING) return
        val last = chipHistory.removeLastOrNull() ?: return
        when (last.first) {
            Spot.BET -> bet = (bet - last.second).coerceAtLeast(0)
            Spot.PUSH_22 -> push22Bet = (push22Bet - last.second).coerceAtLeast(0)
            Spot.PAIR_SQUARE -> pairSquareBet = (pairSquareBet - last.second).coerceAtLeast(0)
        }
    }

    fun clearBet() {
        if (phase != BjPhase.BETTING) return
        bet = 0
        push22Bet = 0
        pairSquareBet = 0
        chipHistory.clear()
        message = "Place your bet"
    }

    // ---- deal ----

    fun deal() {
        if (phase != BjPhase.BETTING) return
        if (bet <= 0) {
            message = if (push22Bet > 0 || pairSquareBet > 0) "Side bets ride with a main bet"
            else "Place a bet first"
            return
        }
        shoe.reshuffleIfBelow(RESHUFFLE_AT)
        bankroll -= bet + push22Bet + pairSquareBet
        persist()
        lastBet = bet
        lastPush22 = push22Bet
        lastPairSquare = pairSquareBet
        push22Stake = push22Bet
        push22Win = null
        pairSquareStake = pairSquareBet
        pairSquareWin = null
        pairSquareResult = null
        pairSquareSettled = false
        chipHistory.clear()
        dealerCards.clear()
        holeRevealed = false
        pushed22 = false
        results = emptyList()
        phase = BjPhase.DEALING
        message = "Dealing…"
        val wager = bet
        bet = 0
        push22Bet = 0
        pairSquareBet = 0

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
        if (hand?.cards?.size == 2) settlePairSquare()
    }

    /**
     * Pair Square is decided by the second card, whenever it comes: it pays
     * out there and then rather than waiting on the dealer, and the hand
     * carries on with the main bet.
     */
    private fun settlePairSquare() {
        if (pairSquareSettled || pairSquareStake <= 0) return
        pairSquareSettled = true
        val cards = hand?.cards ?: return
        pairSquareWin = DoubleDownRules.pairSquare(cards)
        val ret = DoubleDownRules.settlePairSquare(cards, pairSquareStake)
        bankroll += ret
        persist()
        pairSquareResult = BjResult(pairSquareWin?.label ?: "Pair Square", ret - pairSquareStake)
        pairSquareWin?.let { message = "${it.label} — ${it.payout}:1" }
    }

    // ---- player actions ----

    private val live: BjHand? get() = hand?.takeIf { !it.done && phase == BjPhase.PLAYER_TURN }

    val canHit: Boolean get() = live != null
    /**
     * The madness: any hand, at any size, so long as the bankroll can match what
     * is already out there — a double doubles the whole bet, not the first one.
     */
    val canDouble: Boolean get() = live?.let { bankroll >= it.stake } == true
    /** Standing on a single card is never a play, so it waits for the second. */
    val canStand: Boolean get() = live?.let { it.cards.size >= 2 } == true

    fun hit() {
        if (live == null) return
        draw()
        val cards = hand?.cards ?: return
        val bust = BlackjackCore.isBust(cards)
        val twentyOne = BlackjackCore.total(cards) == 21
        if (bust || twentyOne) hand = hand?.copy(done = true)
        val pair = pairSquareWin?.takeIf { cards.size == 2 && pairSquareStake > 0 }
        message = when {
            bust -> "Bust"
            twentyOne -> "Twenty-one"
            pair != null -> "${pair.label} — ${pair.payout}:1"
            else -> "Hit or double"
        }
        if (bust || twentyOne) dealerTurn()
    }

    /**
     * A double buys one card and doubles the whole bet — 100 becomes 200, and
     * doubling that puts 400 out, not 300. It does not close the hand: keep
     * doubling as long as the bankroll and the cards allow.
     */
    fun doubleDown() {
        val h = live ?: return
        // Matching what is already staked is what doubles it.
        val cost = h.stake
        if (bankroll < cost) return
        bankroll -= cost
        persist()
        hand = h.copy(stake = h.stake + cost, doubled = true)
        message = "Double!"
        viewModelScope.launch {
            phase = BjPhase.DEALING
            delay(400)
            draw()
            val cards = hand?.cards ?: emptyList()
            val bust = BlackjackCore.isBust(cards)
            val twentyOne = BlackjackCore.total(cards) == 21
            if (bust || twentyOne) {
                hand = hand?.copy(done = true)
                message = if (bust) "Bust" else "Twenty-one"
                delay(500)
                dealerTurn()
            } else {
                phase = BjPhase.PLAYER_TURN
                val pair = pairSquareWin?.takeIf { cards.size == 2 && pairSquareStake > 0 }
                message = pair?.let { "${it.label} — ${it.payout}:1" } ?: "Hit or double again"
            }
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
            // A live Push 22 keeps the dealer drawing even when the main bet is
            // already decided: the side bet is owed its answer.
            val sideBetLive = push22Stake > 0
            if (sideBetLive || (!BlackjackCore.isBust(cards) && !BlackjackCore.isBlackjack(cards))) {
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
            val playerBj = !h.doubled && BlackjackCore.isBlackjack(h.cards)
            val returned = DoubleDownRules.settleHand(
                player = h.cards,
                dealer = dealerCards,
                stake = h.stake,
                betUnit = h.betUnit,
                doubled = h.doubled,
            )
            pushed22 = push22 && !BlackjackCore.isBust(h.cards) && !dealerBj
            totalReturn += returned
            out.add(BjResult(if (playerBj) "Blackjack" else "Hand", returned - h.stake))
        }

        // A dealer blackjack ends the hand on one card. The bet was never
        // decided, so it goes back — there was no second card to read.
        if (pairSquareStake > 0 && !pairSquareSettled) {
            pairSquareSettled = true
            totalReturn += pairSquareStake
            pairSquareResult = BjResult("Pair Square", 0.0)
        }

        if (push22Stake > 0) {
            push22Win = DoubleDownRules.push22(dealerCards)
            val ret = DoubleDownRules.settlePush22(dealerCards, push22Stake)
            totalReturn += ret
            out.add(BjResult(push22Win?.label ?: "Push 22", ret - push22Stake))
        }

        bankroll += totalReturn
        persist()
        // The verdict speaks for the hand: a won hand beside a lost side bet
        // still won, whatever the two of them net out to.
        val net = out.firstOrNull()?.net ?: 0.0
        val sideWin = push22Win
        message = when {
            campaign && bankroll >= goal -> "🏆 GOAL REACHED!"
            sideWin != null -> "${sideWin.label} — ${sideWin.payout}:1"
            pushed22 -> "Dealer 22 — push"
            dealerBj -> if (net >= 0) "Dealer blackjack — push" else "Dealer blackjack"
            net > 0 -> "You win"
            net < 0 -> "Dealer wins"
            else -> "Push"
        }
        results = out
        phase = BjPhase.RESULT
    }

    /** settle() files the hand first and the side bet after it, if it rode. */
    val handResult: BjResult? get() = results.firstOrNull()
    val push22Result: BjResult? get() = results.getOrNull(1)

    /** +1 won, −1 lost, 0 neither — what the message says, so its colour agrees. */
    val verdict: Int
        get() = when {
            push22Win != null -> 1
            pushed22 -> 0
            else -> (handResult?.net ?: 0.0).let { if (it > 0) 1 else if (it < 0) -1 else 0 }
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
        push22Stake = 0
        push22Win = null
        pairSquareStake = 0
        pairSquareWin = null
        pairSquareResult = null
        pairSquareSettled = false
        if (repeat && lastBet + lastPush22 + lastPairSquare <= bankroll) {
            bet = lastBet
            push22Bet = lastPush22
            pairSquareBet = lastPairSquare
        } else {
            bet = 0
            push22Bet = 0
            pairSquareBet = 0
        }
        message = "Place your bet"
    }
}
