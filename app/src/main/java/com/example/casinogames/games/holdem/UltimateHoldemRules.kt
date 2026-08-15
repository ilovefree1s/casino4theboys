package com.example.casinogames.games.holdem

import com.example.casinogames.games.core.Card

/** The three decision points, each with its own maximum Play bet. */
enum class Street { PRE_FLOP, FLOP, RIVER }

/** The Blind only pays on a straight or better, and only when the player wins. */
enum class BlindPay(val label: String, val multiplier: Double) {
    ROYAL_FLUSH("Royal Flush", 500.0),
    STRAIGHT_FLUSH("Straight Flush", 50.0),
    FOUR_KIND("Four of a Kind", 10.0),
    FULL_HOUSE("Full House", 3.0),
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
    STRAIGHT("Straight", 4),
    THREE_KIND("Three of a Kind", 2),
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
) {
    val totalReturn: Double get() = anteReturn + blindReturn + playReturn + tripsReturn
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
