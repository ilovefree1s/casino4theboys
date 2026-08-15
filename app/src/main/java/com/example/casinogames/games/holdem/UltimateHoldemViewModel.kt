package com.example.casinogames.games.holdem

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
import com.example.casinogames.games.core.Shoe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DECKS = 1
private const val RESHUFFLE_AT = 20
private const val STARTING_BANKROLL = 5000.0
private const val CAMPAIGN_START = 5000.0
private const val CAMPAIGN_GOAL = 1_000_000.0

enum class UthPhase { BETTING, DEALING, PRE_FLOP, FLOP, RIVER, SHOWDOWN, RESULT }

/** One line of the settlement breakdown. */
data class UthResult(val label: String, val net: Double)

/** Which spot a chip landed on, so Undo can lift it off again. */
private enum class Spot { ANTE, TRIPS }

/**
 * Ultimate Texas Hold'em. The Ante and Blind are posted together, the Play bet
 * shrinks the longer you wait (4x or 3x blind, 2x after the flop, 1x after the
 * river), and Trips rides on the player's own five cards.
 */
class UltimateHoldemViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("campaign", Context.MODE_PRIVATE)
    private val shoe = Shoe(decks = DECKS)
    private var lastAnte = 0
    private var lastTrips = 0
    private val chipHistory = mutableListOf<Pair<Spot, Int>>()

    var bankroll by mutableDoubleStateOf(STARTING_BANKROLL)
        private set
    var selectedChip by mutableIntStateOf(25)

    /** Pending bets while betting; the Blind always matches the Ante. */
    var ante by mutableIntStateOf(0)
        private set
    var trips by mutableIntStateOf(0)
        private set

    /** Locked-in stakes once the hand is dealt. */
    var anteStake by mutableIntStateOf(0)
        private set
    var blindStake by mutableIntStateOf(0)
        private set
    var tripsStake by mutableIntStateOf(0)
        private set
    var playStake by mutableIntStateOf(0)
        private set

    var phase by mutableStateOf(UthPhase.BETTING)
        private set
    val playerCards = mutableStateListOf<Card>()
    val dealerCards = mutableStateListOf<Card>()
    val board = mutableStateListOf<Card>()

    /** How many community cards are face up: 0, then 3, then 5. */
    var boardRevealed by mutableIntStateOf(0)
        private set
    var dealerRevealed by mutableStateOf(false)
        private set
    var folded by mutableStateOf(false)
        private set

    var message by mutableStateOf("Place your ante")
        private set
    var results by mutableStateOf<List<UthResult>>(emptyList())
        private set
    var settlement by mutableStateOf<HoldemSettlement?>(null)
        private set

    /** The five cards that won the hand, so the table can pick them out. */
    var winningCards by mutableStateOf<Set<Card>>(emptySet())
        private set

    val totalAtRisk: Int
        get() = if (phase == UthPhase.BETTING) ante * 2 + trips
        else anteStake + blindStake + tripsStake + playStake

    /** The decision point the player is facing, if any. */
    val street: Street?
        get() = when (phase) {
            UthPhase.PRE_FLOP -> Street.PRE_FLOP
            UthPhase.FLOP -> Street.FLOP
            UthPhase.RIVER -> Street.RIVER
            else -> null
        }

    /** Play multiples the player can still afford at this point. */
    val playChoices: List<Int>
        get() {
            val s = street ?: return emptyList()
            return UltimateHoldemRules.playOptions(s)
                .filter { bankroll >= anteStake * it }
        }

    val canCheck: Boolean
        get() = phase == UthPhase.PRE_FLOP || phase == UthPhase.FLOP

    val canFold: Boolean get() = phase == UthPhase.RIVER

    // ---- campaign ----

    var campaign by mutableStateOf(false)
        private set
    var goal by mutableDoubleStateOf(CAMPAIGN_GOAL)
        private set
    private var modeInitialized = false

    fun enterMode(campaignMode: Boolean) {
        if (modeInitialized && campaign == campaignMode) {
            if (campaignMode && phase == UthPhase.BETTING) {
                bankroll = prefs.getFloat("bankroll", CAMPAIGN_START.toFloat()).toDouble()
                goal = prefs.getFloat("goal", CAMPAIGN_GOAL.toFloat()).toDouble()
            }
            return
        }
        campaign = campaignMode
        modeInitialized = true
        resetTable()
        bankroll = if (campaignMode) {
            prefs.getFloat("bankroll", CAMPAIGN_START.toFloat()).toDouble()
        } else STARTING_BANKROLL
        goal = prefs.getFloat("goal", CAMPAIGN_GOAL.toFloat()).toDouble()
        message = "Place your ante"
    }

    private fun resetTable() {
        phase = UthPhase.BETTING
        playerCards.clear()
        dealerCards.clear()
        board.clear()
        chipHistory.clear()
        ante = 0; trips = 0
        anteStake = 0; blindStake = 0; tripsStake = 0; playStake = 0
        boardRevealed = 0
        dealerRevealed = false
        folded = false
        results = emptyList()
        settlement = null
        winningCards = emptySet()
    }

    private fun persist() {
        if (campaign) prefs.edit().putFloat("bankroll", bankroll.toFloat()).apply()
    }

    fun raiseGoal() {
        goal *= 100
        prefs.edit().putFloat("goal", goal.toFloat()).apply()
        message = "Place your ante"
    }

    fun restartCampaign() {
        bankroll = CAMPAIGN_START
        goal = CAMPAIGN_GOAL
        prefs.edit()
            .putFloat("bankroll", bankroll.toFloat())
            .putFloat("goal", goal.toFloat())
            .apply()
        message = "Place your ante"
    }

    fun buyBackIn() {
        if (phase == UthPhase.BETTING && ante == 0 && bankroll < 25) {
            bankroll = if (campaign) CAMPAIGN_START else STARTING_BANKROLL
            persist()
            message = if (campaign) "Fresh start — road to \$1,000,000" else "Place your ante"
        }
    }

    // ---- betting ----

    /** The ante costs double, because the blind rides alongside it. */
    private fun place(spot: Spot) {
        if (phase != UthPhase.BETTING) return
        val committed = ante * 2 + trips
        val room = (bankroll - committed).toInt()
        val amount = when (spot) {
            // Half the remaining room, so ante and blind can both be covered.
            Spot.ANTE -> minOf(selectedChip, room / 2)
            Spot.TRIPS -> minOf(selectedChip, room)
        }
        if (amount <= 0) {
            message = "Not enough for the ante and blind"
            return
        }
        when (spot) {
            Spot.ANTE -> ante += amount
            Spot.TRIPS -> trips += amount
        }
        chipHistory.add(spot to amount)
        message = when {
            amount < selectedChip -> "All in!"
            spot == Spot.TRIPS -> "Trips riding"
            else -> "Ante and blind posted"
        }
    }

    fun addAnte() = place(Spot.ANTE)
    fun addTrips() = place(Spot.TRIPS)

    fun undoChip() {
        if (phase != UthPhase.BETTING) return
        val last = chipHistory.removeLastOrNull() ?: return
        when (last.first) {
            Spot.ANTE -> ante = (ante - last.second).coerceAtLeast(0)
            Spot.TRIPS -> trips = (trips - last.second).coerceAtLeast(0)
        }
    }

    fun clearBet() {
        if (phase != UthPhase.BETTING) return
        ante = 0; trips = 0
        chipHistory.clear()
        message = "Place your ante"
    }

    // ---- deal ----

    fun deal() {
        if (phase != UthPhase.BETTING) return
        if (ante <= 0) {
            message = if (trips > 0) "Trips rides with an ante" else "Place an ante first"
            return
        }
        shoe.reshuffleIfBelow(RESHUFFLE_AT)
        val posted = ante * 2 + trips
        bankroll -= posted
        persist()
        lastAnte = ante; lastTrips = trips
        anteStake = ante; blindStake = ante; tripsStake = trips
        playStake = 0
        chipHistory.clear()
        playerCards.clear()
        dealerCards.clear()
        board.clear()
        boardRevealed = 0
        dealerRevealed = false
        folded = false
        results = emptyList()
        settlement = null
        winningCards = emptySet()
        ante = 0; trips = 0
        phase = UthPhase.DEALING
        message = "Dealing…"

        viewModelScope.launch {
            repeat(2) {
                delay(280)
                playerCards.add(shoe.draw())
                delay(220)
                dealerCards.add(shoe.draw())
            }
            delay(250)
            repeat(5) { board.add(shoe.draw()) }
            delay(350)
            phase = UthPhase.PRE_FLOP
            message = "Check, or raise 3x or 4x"
        }
    }

    // ---- decisions ----

    fun play(multiple: Int) {
        val s = street ?: return
        if (multiple !in UltimateHoldemRules.playOptions(s)) return
        val cost = anteStake * multiple
        if (bankroll < cost) return
        bankroll -= cost
        persist()
        playStake = cost
        viewModelScope.launch {
            message = "Play ${multiple}x"
            // Once the player is in, the rest of the board runs out and we show down.
            if (boardRevealed < 3) {
                phase = UthPhase.DEALING
                delay(450)
                boardRevealed = 3
                delay(650)
            }
            if (boardRevealed < 5) {
                phase = UthPhase.DEALING
                delay(450)
                boardRevealed = 5
                delay(650)
            }
            showdown()
        }
    }

    fun check() {
        if (!canCheck) return
        viewModelScope.launch {
            val next = if (phase == UthPhase.PRE_FLOP) UthPhase.FLOP else UthPhase.RIVER
            phase = UthPhase.DEALING
            message = "Check"
            delay(400)
            boardRevealed = if (next == UthPhase.FLOP) 3 else 5
            delay(550)
            phase = next
            message = if (next == UthPhase.FLOP) "Check, or raise 2x" else "Play 1x, or fold"
        }
    }

    fun fold() {
        if (!canFold) return
        folded = true
        viewModelScope.launch {
            message = "Folded"
            delay(400)
            showdown()
        }
    }

    private suspend fun showdown() {
        phase = UthPhase.SHOWDOWN
        message = "Showdown"
        delay(400)
        dealerRevealed = true
        delay(700)
        settle()
    }

    private fun settle() {
        val s = UltimateHoldemRules.settle(
            playerHole = playerCards.toList(),
            dealerHole = dealerCards.toList(),
            board = board.toList(),
            ante = anteStake.toDouble(),
            blind = blindStake.toDouble(),
            play = playStake.toDouble(),
            trips = tripsStake.toDouble(),
            folded = folded,
        )
        settlement = s
        // Only a decided hand has a winner worth pointing at.
        winningCards = when (s.outcome) {
            HoldemOutcome.WIN -> PokerEval.bestCards(playerCards + board).toSet()
            HoldemOutcome.LOSE, HoldemOutcome.FOLD ->
                PokerEval.bestCards(dealerCards + board).toSet()
            // A tie can still use a card from each hand — A-2 against A-3 both
            // play their ace — so light what either side actually used.
            HoldemOutcome.PUSH ->
                PokerEval.bestCards(playerCards + board).toSet() +
                    PokerEval.bestCards(dealerCards + board).toSet()
        }

        val rows = mutableListOf<UthResult>()
        rows.add(UthResult("Ante", s.anteReturn - anteStake))
        rows.add(
            UthResult(
                s.blindWin?.let { "Blind · ${it.label}" } ?: "Blind",
                s.blindReturn - blindStake,
            )
        )
        if (playStake > 0) rows.add(UthResult("Play", s.playReturn - playStake))
        if (tripsStake > 0) {
            rows.add(
                UthResult(
                    s.tripsWin?.let { "Trips · ${it.label}" } ?: "Trips",
                    s.tripsReturn - tripsStake,
                )
            )
        }

        bankroll += s.totalReturn
        persist()
        val staked = anteStake + blindStake + playStake + tripsStake
        val net = s.totalReturn - staked
        message = when {
            campaign && bankroll >= goal -> "🏆 GOAL REACHED!"
            s.outcome == HoldemOutcome.FOLD -> "Folded — ${s.playerHand.category.label}"
            s.outcome == HoldemOutcome.PUSH -> "Push — ${s.playerHand.category.label}"
            s.outcome == HoldemOutcome.WIN ->
                if (!s.dealerQualified) "You win — dealer didn't qualify"
                else "You win — ${s.playerHand.category.label}"
            else -> "Dealer wins — ${s.dealerHand.category.label}"
        }
        if (net > 0 && s.outcome == HoldemOutcome.FOLD) message = "Folded — trips still pays"
        results = rows
        phase = UthPhase.RESULT
    }

    fun nextHand(repeatBet: Boolean) {
        if (phase != UthPhase.RESULT) return
        resetTable()
        if (repeatBet && lastAnte * 2 + lastTrips <= bankroll) {
            ante = lastAnte; trips = lastTrips
        } else if (repeatBet && lastAnte * 2 <= bankroll) {
            ante = lastAnte; trips = 0
        }
        message = "Place your ante"
    }
}
