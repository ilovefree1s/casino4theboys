package com.example.casinogames.games.miniuth

import com.example.casinogames.games.core.Card

/** The three decision points, each with its own Play bet. */
enum class MiniStreet { PRE_FLOP, FLOP, RIVER }

/**
 * Mini Ultimate Texas Hold'em, Light & Wonder's quick cut of the big game,
 * read off the felt at its September 2026 debut. One hole card each and three
 * community cards — two turn as the flop, then the river. Every hand must use
 * its single hole card, so a hand is that card plus the best two of the three.
 *
 * The Ante and Blind go up together. The Play bet is 3x the ante before the
 * flop, 2x after it, 1x on the river — or fold there and lose both.
 *
 * Two things the felt shots did not settle, held here as assumptions: the
 * dealer always plays (no qualifying hand), and the 3 Card Bonus reads the
 * player's final hand the way Flush Plus says it does.
 */
object MiniUthRules {

    fun playMultiple(street: MiniStreet): Int = when (street) {
        MiniStreet.PRE_FLOP -> 3
        MiniStreet.FLOP -> 2
        MiniStreet.RIVER -> 1
    }

    /**
     * The Blind, off the felt. It pays only when the player's hand beats the
     * dealer's; a winning hand under a straight pushes it, and so does a tie.
     */
    enum class BlindPay(val label: String, val payout: Int) {
        MINI_ROYAL("Mini Royal", 40),
        STRAIGHT_FLUSH("Straight Flush", 8),
        THREE_KIND("Three of a Kind", 5),
        STRAIGHT("Straight", 1),
    }

    /** Flush Plus, off the felt: pays on the player's final three-card hand. */
    enum class FlushPlusPay(val label: String, val payout: Int) {
        MINI_ROYAL("Mini Royal", 200),
        STRAIGHT_FLUSH("Straight Flush", 40),
        THREE_KIND("Three of a Kind", 10),
        STRAIGHT("Straight", 2),
        FLUSH("Flush", 1),
    }

    /** The 3 Card Bonus, as read off the felt. */
    enum class BonusPay(val label: String, val payout: Int) {
        MINI_ROYAL("Mini Royal", 50),
        STRAIGHT_FLUSH("Straight Flush", 40),
        THREE_KIND("Three of a Kind", 30),
        STRAIGHT("Straight", 6),
        FLUSH("Flush", 3),
        PAIR("Pair", 1),
    }

    fun blindRung(hand: MiniHandValue): BlindPay? = when (hand.category) {
        MiniCategory.MINI_ROYAL -> BlindPay.MINI_ROYAL
        MiniCategory.STRAIGHT_FLUSH -> BlindPay.STRAIGHT_FLUSH
        MiniCategory.THREE_KIND -> BlindPay.THREE_KIND
        MiniCategory.STRAIGHT -> BlindPay.STRAIGHT
        else -> null
    }

    fun flushPlusRung(hand: MiniHandValue): FlushPlusPay? = when (hand.category) {
        MiniCategory.MINI_ROYAL -> FlushPlusPay.MINI_ROYAL
        MiniCategory.STRAIGHT_FLUSH -> FlushPlusPay.STRAIGHT_FLUSH
        MiniCategory.THREE_KIND -> FlushPlusPay.THREE_KIND
        MiniCategory.STRAIGHT -> FlushPlusPay.STRAIGHT
        MiniCategory.FLUSH -> FlushPlusPay.FLUSH
        else -> null
    }

    fun bonusRung(hand: MiniHandValue): BonusPay? = when (hand.category) {
        MiniCategory.MINI_ROYAL -> BonusPay.MINI_ROYAL
        MiniCategory.STRAIGHT_FLUSH -> BonusPay.STRAIGHT_FLUSH
        MiniCategory.THREE_KIND -> BonusPay.THREE_KIND
        MiniCategory.STRAIGHT -> BonusPay.STRAIGHT
        MiniCategory.FLUSH -> BonusPay.FLUSH
        MiniCategory.PAIR -> BonusPay.PAIR
        else -> null
    }

    enum class Outcome { WIN, LOSE, PUSH, FOLD }

    /** Everything a settled hand decided. Returns are gross — stake plus winnings. */
    data class Settlement(
        val playerHand: MiniHandValue,
        val dealerHand: MiniHandValue,
        val outcome: Outcome,
        val anteReturn: Double,
        val blindReturn: Double,
        val playReturn: Double,
        val flushPlusReturn: Double,
        val bonusReturn: Double,
        val blindWin: BlindPay?,
        val flushPlusWin: FlushPlusPay?,
        val bonusWin: BonusPay?,
    ) {
        val totalReturn: Double
            get() = anteReturn + blindReturn + playReturn + flushPlusReturn + bonusReturn
    }

    /**
     * Settles one hand. [play] is 0 when the player folded. The side bets ride
     * the player's own final hand, so a fold never touches them.
     */
    fun settle(
        playerHole: Card,
        dealerHole: Card,
        community: List<Card>,
        ante: Double,
        blind: Double,
        play: Double,
        flushPlus: Double,
        bonus: Double,
        folded: Boolean,
    ): Settlement {
        require(community.size == 3) { "the board is three cards" }
        val playerHand = MiniEval.best(playerHole, community)
        val dealerHand = MiniEval.best(dealerHole, community)

        val flushPlusWin = if (flushPlus > 0) flushPlusRung(playerHand) else null
        val flushPlusReturn = if (flushPlusWin != null) flushPlus * (flushPlusWin.payout + 1) else 0.0
        val bonusWin = if (bonus > 0) bonusRung(playerHand) else null
        val bonusReturn = if (bonusWin != null) bonus * (bonusWin.payout + 1) else 0.0

        val cmp = playerHand.compareTo(dealerHand)
        val outcome = when {
            folded -> Outcome.FOLD
            cmp > 0 -> Outcome.WIN
            cmp < 0 -> Outcome.LOSE
            else -> Outcome.PUSH
        }
        val blindWin = if (outcome == Outcome.WIN) blindRung(playerHand) else null
        return Settlement(
            playerHand, dealerHand, outcome,
            anteReturn = when (outcome) {
                Outcome.WIN -> ante * 2
                Outcome.PUSH -> ante
                else -> 0.0
            },
            blindReturn = when (outcome) {
                Outcome.WIN -> if (blindWin != null) blind * (blindWin.payout + 1) else blind
                Outcome.PUSH -> blind
                else -> 0.0
            },
            playReturn = when (outcome) {
                Outcome.WIN -> play * 2
                Outcome.PUSH -> play
                else -> 0.0
            },
            flushPlusReturn = flushPlusReturn,
            bonusReturn = bonusReturn,
            blindWin = blindWin, flushPlusWin = flushPlusWin, bonusWin = bonusWin,
        )
    }
}
