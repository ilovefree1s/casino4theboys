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

enum class BjPhase { BETTING, DEALING, PLAYER_TURN, DEALER_TURN, RESULT }

data class BjHand(
    val cards: List<Card>,
    val stake: Int,          // real money at risk on this hand
    val betUnit: Int,        // nominal bet that wins are paid against
    val doubled: Boolean = false,
    val isFree: Boolean = false,       // hand exists via a free split
    val freeDoubled: Boolean = false,  // doubled with the house's money
    val done: Boolean = false,
    val aceSplit: Boolean = false,     // took its one card from a split of aces
)

data class BjResult(val label: String, val net: Double)

private const val DECKS = 8
private const val RESHUFFLE_AT = 30
private const val STARTING_BANKROLL = 5000.0
private const val CAMPAIGN_START = 5000.0
private const val CAMPAIGN_GOAL = 1_000_000.0

class FreeBetViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("campaign", Context.MODE_PRIVATE)
    private val shoe = Shoe(decks = DECKS)
    private var lastBet = 0
    private var lastPotBet = 0
    private val chipHistory = mutableListOf<Pair<Boolean, Int>>() // isPot to amount

    var bankroll by mutableDoubleStateOf(STARTING_BANKROLL)
        private set
    var selectedChip by mutableIntStateOf(25)
    var bet by mutableIntStateOf(0)
        private set
    var potBet by mutableIntStateOf(0)
        private set
    var potStake by mutableIntStateOf(0)
        private set
    var freeCoins by mutableIntStateOf(0)
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

    val totalAtRisk: Int
        get() = if (phase == BjPhase.BETTING) bet + potBet
        else playerHands.sumOf { it.stake } + potStake

    // ---- campaign ----

    var campaign by mutableStateOf(false)
        private set
    var goal by mutableDoubleStateOf(CAMPAIGN_GOAL)
        private set
    private var modeInitialized = false

    /** Switches between campaign (persistent 5k→1M run) and free play testing. */
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
        playerHands.clear()
        dealerCards.clear()
        chipHistory.clear()
        bet = 0
        potBet = 0
        potStake = 0
        freeCoins = 0
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

    /** Bank the win and chase a goal 100× bigger. */
    fun raiseGoal() {
        goal *= 100
        prefs.edit().putFloat("goal", goal.toFloat()).apply()
        message = "Place your bet"
    }

    /** Cash out the campaign and restart from scratch. */
    fun restartCampaign() {
        bankroll = CAMPAIGN_START
        goal = CAMPAIGN_GOAL
        prefs.edit()
            .putFloat("bankroll", bankroll.toFloat())
            .putFloat("goal", goal.toFloat())
            .apply()
        message = "Place your bet"
    }

    // ---- betting ----

    /** Chips bigger than what's left just bet everything remaining. */
    private fun chipAmount(): Int {
        val available = (bankroll - bet - potBet).toInt()
        return minOf(selectedChip, available)
    }

    fun addChip() {
        if (phase != BjPhase.BETTING) return
        val amount = chipAmount()
        if (amount <= 0) {
            message = "No bankroll left"
            return
        }
        bet += amount
        chipHistory.add(false to amount)
        message = if (amount < selectedChip) "All in!" else "Place your bet"
    }

    fun addPotChip() {
        if (phase != BjPhase.BETTING) return
        val amount = chipAmount()
        if (amount <= 0) {
            message = "No bankroll left"
            return
        }
        potBet += amount
        chipHistory.add(true to amount)
        message = if (amount < selectedChip) "All in!" else "4 The Boys riding"
    }

    fun clearBet() {
        if (phase != BjPhase.BETTING) return
        bet = 0
        potBet = 0
        chipHistory.clear()
        message = "Place your bet"
    }

    /** Takes back the most recently placed chip only. */
    fun undoChip() {
        if (phase != BjPhase.BETTING) return
        val last = chipHistory.removeLastOrNull() ?: return
        if (last.first) potBet = (potBet - last.second).coerceAtLeast(0)
        else bet = (bet - last.second).coerceAtLeast(0)
    }

    fun buyBackIn() {
        if (phase == BjPhase.BETTING && bet == 0 && bankroll < 25) {
            bankroll = if (campaign) CAMPAIGN_START else STARTING_BANKROLL
            persist()
            message = if (campaign) "Fresh start — road to \$1,000,000" else "Place your bet"
        }
    }

    // ---- deal ----

    fun deal() {
        if (phase != BjPhase.BETTING) return
        if (bet <= 0) {
            message = if (potBet > 0) "4 The Boys rides with a main bet" else "Place a bet first"
            return
        }
        shoe.reshuffleIfBelow(RESHUFFLE_AT)
        bankroll -= bet + potBet
        persist()
        lastBet = bet
        lastPotBet = potBet
        potStake = potBet
        chipHistory.clear()
        freeCoins = 0
        playerHands.clear()
        dealerCards.clear()
        holeRevealed = false
        activeHand = 0
        results = emptyList()
        phase = BjPhase.DEALING
        message = "Dealing…"
        val wager = bet
        bet = 0
        potBet = 0

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

    private fun dealToPlayer(index: Int) {
        val h = playerHands[index]
        playerHands[index] = h.copy(cards = h.cards + shoe.draw())
        shoeCount = shoe.cardsRemaining
    }

    // ---- player actions ----

    private val current: BjHand? get() = playerHands.getOrNull(activeHand)

    // A split ace still takes only its one card; the one thing it may do is
    // split again when that card is another ace, so standing stays open.
    val canHit: Boolean
        get() = phase == BjPhase.PLAYER_TURN && current?.done == false &&
            current?.aceSplit != true
    val canStand: Boolean get() = phase == BjPhase.PLAYER_TURN && current?.done == false
    val canDouble: Boolean
        get() {
            val h = current ?: return false
            if (phase != BjPhase.PLAYER_TURN || h.done || h.cards.size != 2) return false
            if (h.aceSplit) return false
            // Free double on hard 9-11; paid double only when holding an ace.
            if (FreeBetRules.canFreeDouble(h.cards)) return true
            return FreeBetRules.canPaidDouble(h.cards) && !h.isFree && bankroll >= h.betUnit
        }
    val doubleIsFree: Boolean
        get() = current?.let { FreeBetRules.canFreeDouble(it.cards) } == true
    val canSplit: Boolean
        get() {
            val h = current ?: return false
            if (phase != BjPhase.PLAYER_TURN || h.done || playerHands.size >= 4) return false
            if (!FreeBetRules.canSplit(h.cards)) return false
            return FreeBetRules.isFreeSplit(h.cards) || bankroll >= h.betUnit
        }
    val splitIsFree: Boolean
        get() = current?.let { FreeBetRules.isFreeSplit(it.cards) } == true

    /** A split ace that draws another ace may be split again, up to four hands. */
    private fun canResplit(h: BjHand): Boolean =
        playerHands.size < 4 && FreeBetRules.canSplit(h.cards) &&
            (FreeBetRules.isFreeSplit(h.cards) || bankroll >= h.betUnit)

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
        val free = FreeBetRules.canFreeDouble(h.cards)
        var stake = h.stake
        if (free) {
            freeCoins++
        } else {
            bankroll -= h.betUnit
            stake += h.betUnit
            persist()
        }
        val cards = h.cards + shoe.draw()
        shoeCount = shoe.cardsRemaining
        playerHands[i] = h.copy(
            cards = cards, stake = stake, doubled = true,
            freeDoubled = free, done = true,
        )
        if (BlackjackCore.isBust(cards)) message = "Bust"
        advance()
    }

    fun split() {
        if (!canSplit) return
        val i = activeHand
        val h = playerHands[i]
        val free = FreeBetRules.isFreeSplit(h.cards)
        if (free) freeCoins++ else { bankroll -= h.betUnit; persist() }
        val aces = h.cards[0].rank == Rank.ACE && h.cards[1].rank == Rank.ACE
        phase = BjPhase.DEALING
        message = if (free) "Free split!" else "Split"
        viewModelScope.launch {
            playerHands[i] = h.copy(cards = listOf(h.cards[0]), aceSplit = aces)
            playerHands.add(
                i + 1,
                BjHand(
                    cards = listOf(h.cards[1]),
                    stake = if (free) 0 else h.betUnit,
                    betUnit = h.betUnit,
                    isFree = free,
                    aceSplit = aces,
                )
            )
            delay(450)
            dealToPlayer(i)
            delay(450)
            dealToPlayer(i + 1)
            delay(300)
            if (aces) {
                // Split aces receive one card each and stand — unless that card
                // is another ace, which the player may split again.
                listOf(i, i + 1).forEach { idx ->
                    if (!canResplit(playerHands[idx])) {
                        playerHands[idx] = playerHands[idx].copy(done = true)
                    }
                }
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

    // ---- dealer ----

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
        val push22 = FreeBetRules.dealerPushes(dealerCards)
        var totalReturn = 0.0
        val out = mutableListOf<BjResult>()

        playerHands.forEachIndexed { i, h ->
            val pTotal = BlackjackCore.total(h.cards)
            val playerBj =
                playerHands.size == 1 && !h.doubled && BlackjackCore.isBlackjack(h.cards)
            val winUnits = if (h.doubled) 2 else 1
            val returned: Double = when {
                playerBj && dealerBj -> h.stake.toDouble()
                playerBj -> h.stake + 1.5 * h.betUnit
                dealerBj -> 0.0
                BlackjackCore.isBust(h.cards) -> 0.0
                push22 -> h.stake.toDouble()
                dTotal > 21 -> h.stake + winUnits.toDouble() * h.betUnit
                pTotal > dTotal -> h.stake + winUnits.toDouble() * h.betUnit
                pTotal == dTotal -> h.stake.toDouble()
                else -> 0.0
            }
            totalReturn += returned
            val label = if (playerHands.size > 1) "Hand ${i + 1}" else
                if (playerBj) "Blackjack" else "Hand"
            out.add(BjResult(label, returned - h.stake))
        }

        if (potStake > 0) {
            val mult = FreeBetRules.potOfGoldMultiplier(freeCoins)
            val potReturn = if (mult > 0) potStake * (mult + 1.0) else 0.0
            totalReturn += potReturn
            val label = if (freeCoins == 1) "4 The Boys · 1 coin"
            else "4 The Boys · $freeCoins coins"
            out.add(BjResult(label, potReturn - potStake))
        }

        bankroll += totalReturn
        persist()
        val net = totalReturn - playerHands.sumOf { it.stake } - potStake
        message = when {
            campaign && bankroll >= goal -> "🏆 GOAL REACHED!"
            push22 -> "Dealer 22 — all hands push"
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
        potStake = 0
        freeCoins = 0
        chipHistory.clear()
        if (repeatBet && lastBet + lastPotBet <= bankroll) {
            bet = lastBet
            potBet = lastPotBet
        } else if (repeatBet && lastBet <= bankroll) {
            bet = lastBet
            potBet = 0
        } else {
            bet = 0
            potBet = 0
        }
        if (bet > 0) chipHistory.add(false to bet)
        if (potBet > 0) chipHistory.add(true to potBet)
        message = if (bet > 0) "Same bet placed" else "Place your bet"
    }
}
