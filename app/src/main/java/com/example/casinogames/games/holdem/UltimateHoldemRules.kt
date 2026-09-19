package com.example.casinogames.games.holdem

import com.example.casinogames.games.core.Card

/** The three decision points, each with its own maximum Play bet. */
enum class Street { PRE_FLOP, FLOP, RIVER }

/** The Blind only pays on a straight or better, and only when the player wins. */
enum class BlindPay(val label: String, val multiplier: Double) {
    ROYAL_FLUSH("Royal Flush", 500.0),
    STRAIGHT_FLUSH("Straight Flush", 50.0),
    FOUR_KIND("Four of a Kind", 10.0),
    FULL_HOUSE("Full House", 5.0),
    FLUSH("Flush", 3.0),
    STRAIGHT("Straight", 1.0),
}

/**
 * Trips is settled on the player's own five cards, win or lose against the
 * dealer. The top five rungs come from the table art; straight and three of a
 * kind pay the usual amounts underneath them.
 */
enum class TripsPay(val label: String, val multiplier: Int) {
    ROYAL_FLUSH("Royal Flush", 200),
    STRAIGHT_FLUSH("Straight Flush", 100),
    FOUR_KIND("Four of a Kind", 50),
    FULL_HOUSE("Full House", 20),
    FLUSH("Flush", 10),
    STRAIGHT("Straight", 8),
    THREE_KIND("Three of a Kind", 4),
}

/**
 * The Bad Beat side bet, the ladder the user brought in: it pays off whichever
 * hand *lost* the showdown, when that hand was trips or better — the point
 * being that a big hand went down. A push pays nothing, since nothing was
 * beaten. A folded hand counts as the player's loss: the cards were dealt and
 * the dealer's are turned over, so a straight thrown away still lost.
 */
enum class BadBeatPay(val label: String, val multiplier: Int) {
    ROYAL_FLUSH("Royal Flush", 10_000),
    STRAIGHT_FLUSH("Straight Flush", 10_000),
    FOUR_KIND("Four of a Kind", 500),
    FULL_HOUSE("Full House", 50),
    FLUSH("Flush", 35),
    STRAIGHT("Straight", 25),
    THREE_KIND("Three of a Kind", 9),
}

/** What the Bad Beat found, and on whose hand. */
data class BadBeatHit(val rung: BadBeatPay, val onDealer: Boolean) {
    val label: String get() = if (onDealer) "Dealer ${rung.label}" else rung.label
}

enum class HoldemOutcome { WIN, LOSE, PUSH, FOLD }

/** Everything settle() decided, itemised the way the table reports it. */
data class HoldemSettlement(
    val playerHand: HandValue,
    val dealerHand: HandValue,
    val dealerQualified: Boolean,
    val outcome: HoldemOutcome,
    val anteReturn: Double,
    val blindReturn: Double,
    val playReturn: Double,
    val tripsReturn: Double,
    val blindWin: BlindPay?,
    val tripsWin: TripsPay?,
    val badBeatReturn: Double = 0.0,
    val badBeatWin: BadBeatHit? = null,
) {
    val totalReturn: Double get() = anteReturn + blindReturn + playReturn + tripsReturn + badBeatReturn
}

object UltimateHoldemRules {

    /** Play bet multiples of the ante, best first, for each decision point. */
    fun playOptions(street: Street): List<Int> = when (street) {
        Street.PRE_FLOP -> listOf(4, 3)
        Street.FLOP -> listOf(2)
        Street.RIVER -> listOf(1)
    }

    /** The dealer needs a pair or better to open. */
    fun dealerQualifies(hand: HandValue): Boolean =
        hand.category.ordinal >= HandCategory.PAIR.ordinal

    fun blindPay(hand: HandValue): BlindPay? = when (hand.category) {
        HandCategory.ROYAL_FLUSH -> BlindPay.ROYAL_FLUSH
        HandCategory.STRAIGHT_FLUSH -> BlindPay.STRAIGHT_FLUSH
        HandCategory.FOUR_KIND -> BlindPay.FOUR_KIND
        HandCategory.FULL_HOUSE -> BlindPay.FULL_HOUSE
        HandCategory.FLUSH -> BlindPay.FLUSH
        HandCategory.STRAIGHT -> BlindPay.STRAIGHT
        else -> null
    }

    fun tripsPay(hand: HandValue): TripsPay? = when (hand.category) {
        HandCategory.ROYAL_FLUSH -> TripsPay.ROYAL_FLUSH
        HandCategory.STRAIGHT_FLUSH -> TripsPay.STRAIGHT_FLUSH
        HandCategory.FOUR_KIND -> TripsPay.FOUR_KIND
        HandCategory.FULL_HOUSE -> TripsPay.FULL_HOUSE
        HandCategory.FLUSH -> TripsPay.FLUSH
        HandCategory.STRAIGHT -> TripsPay.STRAIGHT
        HandCategory.THREE_KIND -> TripsPay.THREE_KIND
        else -> null
    }

    fun badBeatPay(hand: HandValue): BadBeatPay? = when (hand.category) {
        HandCategory.ROYAL_FLUSH -> BadBeatPay.ROYAL_FLUSH
        HandCategory.STRAIGHT_FLUSH -> BadBeatPay.STRAIGHT_FLUSH
        HandCategory.FOUR_KIND -> BadBeatPay.FOUR_KIND
        HandCategory.FULL_HOUSE -> BadBeatPay.FULL_HOUSE
        HandCategory.FLUSH -> BadBeatPay.FLUSH
        HandCategory.STRAIGHT -> BadBeatPay.STRAIGHT
        HandCategory.THREE_KIND -> BadBeatPay.THREE_KIND
        else -> null
    }

    /** The losing side's hand, if it was big enough to be a bad beat. */
    fun badBeat(playerHand: HandValue, dealerHand: HandValue, folded: Boolean): BadBeatHit? {
        val cmp = playerHand.compareTo(dealerHand)
        val onDealer = when {
            folded -> false
            cmp == 0 -> return null
            else -> cmp > 0
        }
        return badBeatPay(if (onDealer) dealerHand else playerHand)?.let { BadBeatHit(it, onDealer) }
    }

    /**
     * Settles one hand. [play] is 0 when the player folded. Returns are gross —
     * stake plus winnings — so a losing bet returns nothing and a push returns
     * exactly what was put up.
     */
    fun settle(
        playerHole: List<Card>,
        dealerHole: List<Card>,
        board: List<Card>,
        ante: Double,
        blind: Double,
        play: Double,
        trips: Double,
        folded: Boolean,
        badBeat: Double = 0.0,
    ): HoldemSettlement {
        val s = settleHand(playerHole, dealerHole, board, ante, blind, play, trips, folded)
        // The Bad Beat reads the hand that lost, whichever side that was.
        val hit = if (badBeat > 0) badBeat(s.playerHand, s.dealerHand, folded) else null
        return if (hit == null) s
        else s.copy(badBeatReturn = badBeat * (hit.rung.multiplier + 1), badBeatWin = hit)
    }

    private fun settleHand(
        playerHole: List<Card>,
        dealerHole: List<Card>,
        board: List<Card>,
        ante: Double,
        blind: Double,
        play: Double,
        trips: Double,
        folded: Boolean,
    ): HoldemSettlement {
        val playerHand = PokerEval.best(playerHole + board)
        val dealerHand = PokerEval.best(dealerHole + board)
        val qualified = dealerQualifies(dealerHand)

        // Trips rides on the player's own cards, so a fold doesn't touch it.
        val tripsWin = tripsPay(playerHand)
        val tripsReturn = if (trips > 0 && tripsWin != null) trips * (tripsWin.multiplier + 1) else 0.0

        if (folded) {
            return HoldemSettlement(
                playerHand, dealerHand, qualified, HoldemOutcome.FOLD,
                anteReturn = 0.0, blindReturn = 0.0, playReturn = 0.0,
                tripsReturn = tripsReturn, blindWin = null, tripsWin = tripsWin,
            )
        }

        val cmp = playerHand.compareTo(dealerHand)
        val outcome = when {
            cmp > 0 -> HoldemOutcome.WIN
            cmp < 0 -> HoldemOutcome.LOSE
            else -> HoldemOutcome.PUSH
        }

        val blindWin = if (outcome == HoldemOutcome.WIN) blindPay(playerHand) else null
        return when (outcome) {
            HoldemOutcome.WIN -> HoldemSettlement(
                playerHand, dealerHand, qualified, outcome,
                // A dealer who never opened can't take the ante, so it pushes.
                anteReturn = if (qualified) ante * 2 else ante,
                blindReturn = if (blindWin != null) blind * (blindWin.multiplier + 1) else blind,
                playReturn = play * 2,
                tripsReturn = tripsReturn,
                blindWin = blindWin, tripsWin = tripsWin,
            )
            HoldemOutcome.PUSH -> HoldemSettlement(
                playerHand, dealerHand, qualified, outcome,
                anteReturn = ante, blindReturn = blind, playReturn = play,
                tripsReturn = tripsReturn, blindWin = null, tripsWin = tripsWin,
            )
            else -> HoldemSettlement(
                playerHand, dealerHand, qualified, outcome,
                anteReturn = 0.0, blindReturn = 0.0, playReturn = 0.0,
                tripsReturn = tripsReturn, blindWin = null, tripsWin = tripsWin,
            )
        }
    }
}
