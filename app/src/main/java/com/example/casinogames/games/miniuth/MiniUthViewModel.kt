package com.example.casinogames.games.miniuth

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

enum class MiniPhase { BETTING, DEALING, PRE_FLOP, FLOP, RIVER, SHOWDOWN, RESULT }

/** One line of the settlement breakdown. */
data class MiniResult(val label: String, val net: Double)

private enum class Spot { ANTE, FLUSH_PLUS, BONUS }

/**
 * Mini Ultimate Texas Hold'em. One hole card each, a two-card flop and a
 * river. Raise 3x before the flop, 2x after it, 1x on the river — once only —
 * or fold on the river and lose the ante and blind.
 */
class MiniUthViewModel(app: Application) : AndroidViewModel(app) {
    private val shoe = Shoe(decks = 1)
    private var lastAnte = 0
    private var lastFlushPlus = 0
    private var lastBonus = 0
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

    /** Pending bets while betting; the Blind always matches the Ante. */
    var ante by mutableIntStateOf(0)
        private set
    var flushPlus by mutableIntStateOf(0)
        private set
    var bonus by mutableIntStateOf(0)
        private set

    /** Locked-in stakes once the hand is dealt. */
    var anteStake by mutableIntStateOf(0)
        private set
    var blindStake by mutableIntStateOf(0)
        private set
    var playStake by mutableIntStateOf(0)
        private set
    var flushPlusStake by mutableIntStateOf(0)
        private set
    var bonusStake by mutableIntStateOf(0)
        private set

    val playerCards = mutableStateListOf<Card>()
    val dealerCards = mutableStateListOf<Card>()
    val community = mutableStateListOf<Card>()
    var dealerRevealed by mutableStateOf(false)
        private set
    var folded by mutableStateOf(false)
        private set

    var phase by mutableStateOf(MiniPhase.BETTING)
        private set
    var message by mutableStateOf("Place your ante")
        private set
    var settlement by mutableStateOf<MiniUthRules.Settlement?>(null)
        private set
    var results by mutableStateOf<List<MiniResult>>(emptyList())
        private set

    val totalAtRisk: Int
        get() = if (phase == MiniPhase.BETTING) ante * 2 + flushPlus + bonus
        else anteStake + blindStake + playStake + flushPlusStake + bonusStake

    /** What the player holds right now, once the flop is out. */
    val playerHandNow: MiniHandValue?
        get() = playerCards.firstOrNull()?.takeIf { community.size >= 2 }
            ?.let { MiniEval.best(it, community.toList()) }

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
        ante = 0; flushPlus = 0; bonus = 0
        if (!campaignMode) freePurse = FreePlay.buyIn
        message = "Place your ante"
    }

    fun raiseGoal() {
        Campaign.raiseGoal()
        message = "Place your ante"
    }

    fun restartCampaign() {
        Campaign.restart()
        message = "Place your ante"
    }

    fun buyBackIn() {
        if (phase != MiniPhase.BETTING || totalAtRisk > 0 || bankroll >= 25) return
        if (campaign) Campaign.takeMarker() else freePurse = FreePlay.buyIn
        message = "Place your ante"
    }

    // ---- betting ----

    fun addAnte() = place(Spot.ANTE)
    fun addFlushPlus() = place(Spot.FLUSH_PLUS)
    fun addBonus() = place(Spot.BONUS)

    private fun place(spot: Spot) {
        if (phase != MiniPhase.BETTING) return
        // The blind matches the ante, so an ante chip costs twice its face.
        val room = (bankroll - (ante * 2 + flushPlus + bonus)).toInt()
        val affordable = when (spot) {
            Spot.ANTE -> minOf(selectedChip, room / 2)
            else -> minOf(selectedChip, room)
        }
        if (affordable <= 0) {
            message = "Not enough for the ante and blind"
            return
        }
        val lim = limits
        val amount = when (spot) {
            Spot.ANTE -> lim.allow(affordable, ante)
            Spot.FLUSH_PLUS -> lim.allow(affordable, flushPlus, side = true)
            Spot.BONUS -> lim.allow(affordable, bonus, side = true)
        }
        if (amount <= 0) {
            message = lim.refusal(side = spot != Spot.ANTE)
            return
        }
        when (spot) {
            Spot.ANTE -> ante += amount
            Spot.FLUSH_PLUS -> flushPlus += amount
            Spot.BONUS -> bonus += amount
        }
        chipHistory.add(spot to amount)
        message = when {
            amount < selectedChip -> "All in!"
            spot == Spot.FLUSH_PLUS -> "Flush Plus riding"
            spot == Spot.BONUS -> "3 Card Bonus riding"
            else -> "Ante and blind posted"
        }
    }

    fun clearBets() {
        if (phase != MiniPhase.BETTING) return
        ante = 0; flushPlus = 0; bonus = 0
        chipHistory.clear()
        message = "Place your ante"
    }

    // ---- the deal ----

    fun deal() {
        if (phase != MiniPhase.BETTING) return
        if (ante <= 0) {
            message = if (flushPlus > 0 || bonus > 0) "Side bets ride with an ante"
            else "Place an ante first"
            return
        }
        // One deck, shuffled fresh every hand.
        shoe.reshuffleIfBelow(52)
        spend((ante * 2 + flushPlus + bonus).toDouble())
        lastAnte = ante; lastFlushPlus = flushPlus; lastBonus = bonus
        anteStake = ante; blindStake = ante
        flushPlusStake = flushPlus; bonusStake = bonus
        playStake = 0
        chipHistory.clear()
        clearCards()
        ante = 0; flushPlus = 0; bonus = 0
        phase = MiniPhase.DEALING
        message = "Dealing…"

        viewModelScope.launch {
            delay(220)
            playerCards.add(shoe.draw())
            delay(200)
            dealerCards.add(shoe.draw())
            delay(300)
            phase = MiniPhase.PRE_FLOP
            message = "Raise 3x, or check"
        }
    }

    // ---- the decisions ----

    val street: MiniStreet?
        get() = when (phase) {
            MiniPhase.PRE_FLOP -> MiniStreet.PRE_FLOP
            MiniPhase.FLOP -> MiniStreet.FLOP
            MiniPhase.RIVER -> MiniStreet.RIVER
            else -> null
        }

    val playCost: Int get() = street?.let { anteStake * MiniUthRules.playMultiple(it) } ?: 0
    val canRaise: Boolean get() = street != null && bankroll >= playCost

    /** Short of the last raise: the pit lends at the felt rather than force a fold. */
    val canTakeMarker: Boolean
        get() = campaign && phase == MiniPhase.RIVER && bankroll < playCost && Campaign.canTakeMarker

    fun takeMarker() {
        if (!canTakeMarker) return
        Campaign.takeMarker()
        message = "Marker taken — play on"
    }

    fun raise() {
        if (!canRaise) return
        spend(playCost.toDouble())
        playStake = playCost
        runOut()
    }

    fun check() {
        when (phase) {
            MiniPhase.PRE_FLOP -> dealFlop()
            MiniPhase.FLOP -> dealRiver()
            else -> Unit
        }
    }

    fun fold() {
        if (phase != MiniPhase.RIVER) return
        folded = true
        showdown()
    }

    private fun dealFlop() {
        phase = MiniPhase.DEALING
        message = "The flop"
        viewModelScope.launch {
            repeat(2) {
                delay(240)
                community.add(shoe.draw())
            }
            delay(260)
            phase = MiniPhase.FLOP
            message = "${playerHandNow?.category?.label} — raise 2x, or check"
        }
    }

    private fun dealRiver() {
        phase = MiniPhase.DEALING
        message = "The river"
        viewModelScope.launch {
            delay(260)
            community.add(shoe.draw())
            delay(260)
            phase = MiniPhase.RIVER
            message = "${playerHandNow?.category?.label} — play 1x, or fold"
        }
    }

    /** A raise ends the decisions: whatever is left of the board comes out. */
    private fun runOut() {
        phase = MiniPhase.DEALING
        message = "Raised — the board comes out"
        viewModelScope.launch {
            while (community.size < 3) {
                delay(240)
                community.add(shoe.draw())
            }
            showdown()
        }
    }

    private fun showdown() {
        phase = MiniPhase.SHOWDOWN
        message = if (folded) "Folded" else "Dealer turns over…"
        viewModelScope.launch {
            delay(420)
            dealerRevealed = true
            delay(650)
            settle()
        }
    }

    private fun settle() {
        val s = MiniUthRules.settle(
            playerHole = playerCards[0],
            dealerHole = dealerCards[0],
            community = community.toList(),
            ante = anteStake.toDouble(),
            blind = blindStake.toDouble(),
            play = playStake.toDouble(),
            flushPlus = flushPlusStake.toDouble(),
            bonus = bonusStake.toDouble(),
            folded = folded,
        )
        settlement = s
        collect(s.totalReturn)

        val out = mutableListOf<MiniResult>()
        if (!folded) {
            out.add(MiniResult("Ante", s.anteReturn - anteStake))
            out.add(
                MiniResult(
                    s.blindWin?.let { "Blind · ${it.label}" } ?: "Blind",
                    s.blindReturn - blindStake,
                ),
            )
            if (playStake > 0) out.add(MiniResult("Play", s.playReturn - playStake))
        } else {
            out.add(MiniResult("Folded", -(anteStake + blindStake).toDouble()))
        }
        if (flushPlusStake > 0) {
            out.add(
                MiniResult(
                    s.flushPlusWin?.let { "Flush Plus · ${it.label}" } ?: "Flush Plus",
                    s.flushPlusReturn - flushPlusStake,
                ),
            )
        }
        if (bonusStake > 0) {
            out.add(
                MiniResult(
                    s.bonusWin?.let { "3 Card · ${it.label}" } ?: "3 Card Bonus",
                    s.bonusReturn - bonusStake,
                ),
            )
        }
        results = out

        val staked = anteStake + blindStake + playStake + flushPlusStake + bonusStake
        val net = s.totalReturn - staked
        message = when {
            campaign && bankroll >= goal -> "🏆 GOAL REACHED!"
            s.playerHand.category == MiniCategory.MINI_ROYAL -> "MINI ROYAL!"
            folded -> "Folded — ante and blind down"
            s.outcome == MiniUthRules.Outcome.WIN -> "You win with ${s.playerHand.category.label}"
            s.outcome == MiniUthRules.Outcome.PUSH -> "Push"
            net > 0 -> "Up ${net.toInt()} on the side"
            else -> "Dealer wins with ${s.dealerHand.category.label}"
        }
        phase = MiniPhase.RESULT
    }

    fun nextHand(repeat: Boolean) {
        if (phase != MiniPhase.RESULT) return
        clearTable()
        if (repeat && lastAnte * 2 + lastFlushPlus + lastBonus <= bankroll) {
            ante = lastAnte; flushPlus = lastFlushPlus; bonus = lastBonus
        } else if (repeat && lastAnte * 2 <= bankroll) {
            ante = lastAnte; flushPlus = 0; bonus = 0
        } else {
            ante = 0; flushPlus = 0; bonus = 0
        }
        message = "Place your ante"
    }

    private fun clearCards() {
        playerCards.clear(); dealerCards.clear(); community.clear()
        dealerRevealed = false; folded = false
        settlement = null; results = emptyList()
    }

    private fun clearTable() {
        phase = MiniPhase.BETTING
        clearCards()
        chipHistory.clear()
        anteStake = 0; blindStake = 0; playStake = 0; flushPlusStake = 0; bonusStake = 0
    }
}
