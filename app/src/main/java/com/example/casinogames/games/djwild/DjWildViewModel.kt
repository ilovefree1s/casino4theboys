package com.example.casinogames.games.djwild

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

private const val RESHUFFLE_AT = 15

enum class DjPhase { BETTING, DEALING, DECISION, SHOWDOWN, RESULT }

/** One line of the settlement breakdown. */
data class DjResult(val label: String, val net: Double)

/** Which spot a chip landed on, so Undo can lift it off again. */
private enum class Spot { ANTE, TRIPS, BAD_BEAT }

/**
 * DJ Wild. Five cards each from a deck of fifty-three, every deuce and the
 * joker playing wild. The Ante and Blind are posted together and there is one
 * decision after that: play at twice the ante, or fold and lose both. The
 * dealer always qualifies.
 */
class DjWildViewModel(app: Application) : AndroidViewModel(app) {
    private val shoe = Shoe(decks = DjWildDeck.DECKS, jokers = DjWildDeck.JOKERS)
    private var lastAnte = 0
    private var lastTrips = 0
    private var lastBadBeat = 0
    private val chipHistory = mutableListOf<Pair<Spot, Int>>()

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

    /** Pending bets while betting; the Blind always matches the Ante. */
    var ante by mutableIntStateOf(0)
        private set
    var trips by mutableIntStateOf(0)
        private set
    var badBeat by mutableIntStateOf(0)
        private set

    /** Locked-in stakes once the hand is dealt. */
    var anteStake by mutableIntStateOf(0)
        private set
    var blindStake by mutableIntStateOf(0)
        private set
    var playStake by mutableIntStateOf(0)
        private set
    var tripsStake by mutableIntStateOf(0)
        private set
    var badBeatStake by mutableIntStateOf(0)
        private set

    val playerCards = mutableStateListOf<Card>()
    val dealerCards = mutableStateListOf<Card>()
    var dealerRevealed by mutableStateOf(false)
        private set
    var folded by mutableStateOf(false)
        private set

    var phase by mutableStateOf(DjPhase.BETTING)
        private set
    var message by mutableStateOf("Place your ante")
        private set
    var settlement by mutableStateOf<DjWildRules.Settlement?>(null)
        private set
    var results by mutableStateOf<List<DjResult>>(emptyList())
        private set
    var shoeCount by mutableIntStateOf(shoe.cardsRemaining)
        private set

    /** Everything out on the felt, so the top bar can say what is at risk. */
    val totalAtRisk: Int
        get() = if (phase == DjPhase.BETTING) ante * 2 + trips + badBeat
        else anteStake + blindStake + playStake + tripsStake + badBeatStake

    // ---- campaign ----

    var campaign by mutableStateOf(false)
        private set
    val goal: Double get() = Campaign.goal
    private var modeInitialized = false

    fun enterMode(campaignMode: Boolean) {
        if (modeInitialized && campaign == campaignMode) return
        campaign = campaignMode
        modeInitialized = true
        phase = DjPhase.BETTING
        playerCards.clear(); dealerCards.clear(); chipHistory.clear()
        ante = 0; trips = 0; badBeat = 0
        anteStake = 0; blindStake = 0; playStake = 0; tripsStake = 0; badBeatStake = 0
        dealerRevealed = false; folded = false
        settlement = null; results = emptyList()
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

    /**
     * Broke in the campaign is a marker, not a free reset: the house hands
     * over five thousand and writes down seven and a half.
     */
    fun buyBackIn() {
        if (phase != DjPhase.BETTING || totalAtRisk > 0 || bankroll >= 25) return
        if (campaign) Campaign.takeMarker() else freePurse = FreePlay.buyIn
        message = "Place your ante"
    }

    // ---- betting ----

    fun addAnte() = place(Spot.ANTE)
    fun addTrips() = place(Spot.TRIPS)
    fun addBadBeat() = place(Spot.BAD_BEAT)

    private fun place(spot: Spot) {
        if (phase != DjPhase.BETTING) return
        // The blind matches the ante, so an ante chip costs twice its face.
        val committed = ante * 2 + trips + badBeat
        val room = (bankroll - committed).toInt()
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
            Spot.TRIPS -> lim.allow(affordable, trips, side = true)
            Spot.BAD_BEAT -> lim.allowBadBeat(affordable, badBeat)
        }
        if (amount <= 0) {
            message = when (spot) {
                Spot.BAD_BEAT -> lim.badBeatRefusal()
                Spot.ANTE -> lim.refusal(side = false)
                else -> lim.refusal(side = true)
            }
            return
        }
        when (spot) {
            Spot.ANTE -> ante += amount
            Spot.TRIPS -> trips += amount
            Spot.BAD_BEAT -> badBeat += amount
        }
        chipHistory.add(spot to amount)
        message = when {
            amount < selectedChip -> "All in!"
            spot == Spot.TRIPS -> "Trips riding"
            spot == Spot.BAD_BEAT -> "Bad Beat riding"
            else -> "Ante and blind posted"
        }
    }

    fun undoChip() {
        if (phase != DjPhase.BETTING) return
        val last = chipHistory.removeLastOrNull() ?: return
        when (last.first) {
            Spot.ANTE -> ante = (ante - last.second).coerceAtLeast(0)
            Spot.TRIPS -> trips = (trips - last.second).coerceAtLeast(0)
            Spot.BAD_BEAT -> badBeat = (badBeat - last.second).coerceAtLeast(0)
        }
    }

    fun clearBets() {
        if (phase != DjPhase.BETTING) return
        ante = 0; trips = 0; badBeat = 0
        chipHistory.clear()
        message = "Place your ante"
    }

    fun rebet() {
        if (phase != DjPhase.BETTING || totalAtRisk > 0) return
        if (lastAnte == 0 && lastTrips == 0 && lastBadBeat == 0) return
        if (lastAnte * 2 + lastTrips + lastBadBeat > bankroll) {
            message = "Not enough for that bet"
            return
        }
        ante = lastAnte; trips = lastTrips; badBeat = lastBadBeat
        chipHistory.clear()
        message = "Bets repeated"
    }

    // ---- the deal ----

    fun deal() {
        if (phase != DjPhase.BETTING) return
        if (ante <= 0) {
            message = if (trips > 0 || badBeat > 0) "Side bets ride with an ante"
            else "Place an ante first"
            return
        }
        shoe.reshuffleIfBelow(RESHUFFLE_AT)
        spend((ante * 2 + trips + badBeat).toDouble())
        lastAnte = ante; lastTrips = trips; lastBadBeat = badBeat
        anteStake = ante; blindStake = ante; tripsStake = trips; badBeatStake = badBeat
        playStake = 0
        chipHistory.clear()
        playerCards.clear(); dealerCards.clear()
        dealerRevealed = false; folded = false
        settlement = null; results = emptyList()
        ante = 0; trips = 0; badBeat = 0
        phase = DjPhase.DEALING
        message = "Dealing…"

        viewModelScope.launch {
            repeat(5) {
                delay(190)
                playerCards.add(shoe.draw())
                delay(140)
                dealerCards.add(shoe.draw())
            }
            shoeCount = shoe.cardsRemaining
            delay(280)
            phase = DjPhase.DECISION
            val wilds = DjWildDeck.wildCount(playerCards)
            message = when (wilds) {
                0 -> "Play 2x, or fold"
                1 -> "One wild — play 2x, or fold"
                else -> "$wilds wilds — play 2x, or fold"
            }
        }
    }

    // ---- the one decision ----

    val playCost: Int get() = anteStake * DjWildRules.PLAY_MULTIPLE
    val canPlay: Boolean get() = phase == DjPhase.DECISION && bankroll >= playCost

    /**
     * The pit lends at the felt when the purse cannot cover the play bet, so
     * a good hand is not folded for want of chips. Free play has no house to
     * borrow from.
     */
    val canTakeMarker: Boolean
        get() = campaign && phase == DjPhase.DECISION && bankroll < playCost &&
            Campaign.canTakeMarker

    fun takeMarker() {
        if (!canTakeMarker) return
        Campaign.takeMarker()
        message = "Marker taken — play on"
    }

    fun play() {
        if (!canPlay) return
        spend(playCost.toDouble())
        playStake = playCost
        showdown()
    }

    fun fold() {
        if (phase != DjPhase.DECISION) return
        folded = true
        showdown()
    }

    private fun showdown() {
        phase = DjPhase.SHOWDOWN
        message = if (folded) "Folded" else "Dealer turns over…"
        viewModelScope.launch {
            delay(420)
            dealerRevealed = true
            delay(650)
            settle()
        }
    }

    private fun settle() {
        val s = DjWildRules.settle(
            player = playerCards.toList(),
            dealer = dealerCards.toList(),
            ante = anteStake.toDouble(),
            blind = blindStake.toDouble(),
            play = playStake.toDouble(),
            trips = tripsStake.toDouble(),
            badBeat = badBeatStake.toDouble(),
            folded = folded,
        )
        settlement = s
        collect(s.totalReturn)

        val out = mutableListOf<DjResult>()
        if (!folded) {
            out.add(DjResult("Ante", s.anteReturn - anteStake))
            out.add(
                DjResult(
                    s.blindWin?.let { "Blind · ${it.label}" } ?: "Blind",
                    s.blindReturn - blindStake,
                ),
            )
            if (playStake > 0) out.add(DjResult("Play", s.playReturn - playStake))
        } else {
            out.add(DjResult("Folded", -(anteStake + blindStake).toDouble()))
        }
        if (tripsStake > 0) {
            out.add(DjResult(s.tripsWin?.label ?: "Trips", s.tripsReturn - tripsStake))
        }
        if (badBeatStake > 0) {
            out.add(DjResult(s.badBeatWin?.label ?: "Bad Beat", s.badBeatReturn - badBeatStake))
        }
        results = out

        val staked = anteStake + blindStake + playStake + tripsStake + badBeatStake
        val net = s.totalReturn - staked
        message = when {
            campaign && bankroll >= goal -> "🏆 GOAL REACHED!"
            s.badBeatWin != null -> "${s.badBeatWin!!.label} — bad beat pays"
            s.tripsWin?.rung == DjWildRules.TripsPay.FIVE_WILDS -> "FIVE WILDS!"
            folded -> "Folded — ante and blind down"
            s.outcome == DjWildRules.DjOutcome.WIN -> "You win with ${s.playerHand.category.label}"
            s.outcome == DjWildRules.DjOutcome.PUSH -> "Push"
            net > 0 -> "Up ${net.toInt()} on the side"
            else -> "Dealer wins with ${s.dealerHand.category.label}"
        }
        phase = DjPhase.RESULT
    }

    fun nextHand(repeat: Boolean) {
        if (phase != DjPhase.RESULT) return
        phase = DjPhase.BETTING
        playerCards.clear(); dealerCards.clear()
        dealerRevealed = false; folded = false
        settlement = null; results = emptyList()
        chipHistory.clear()
        anteStake = 0; blindStake = 0; playStake = 0; tripsStake = 0; badBeatStake = 0
        if (repeat && lastAnte * 2 + lastTrips + lastBadBeat <= bankroll) {
            ante = lastAnte; trips = lastTrips; badBeat = lastBadBeat
        } else {
            ante = 0; trips = 0; badBeat = 0
        }
        message = "Place your ante"
    }
}
