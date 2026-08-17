package com.example.casinogames.games.blackjack

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank

/** Shared blackjack arithmetic, reused by every table variant. */
object BlackjackCore {

    fun cardValue(rank: Rank): Int = when (rank) {
        Rank.ACE -> 1
        Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING -> 10
        else -> rank.baccaratValue
    }

    /** Best total, counting one ace as 11 when that doesn't bust. */
    fun total(cards: List<Card>): Int {
        val hard = cards.sumOf { cardValue(it.rank) }
        return if (cards.any { it.rank == Rank.ACE } && hard + 10 <= 21) hard + 10 else hard
    }

    /** True when an ace is currently counted as 11. */
    fun isSoft(cards: List<Card>): Boolean {
        val hard = cards.sumOf { cardValue(it.rank) }
        return cards.any { it.rank == Rank.ACE } && hard + 10 <= 21
    }

    fun isBust(cards: List<Card>): Boolean = total(cards) > 21

    fun isBlackjack(cards: List<Card>): Boolean = cards.size == 2 && total(cards) == 21

    /** Dealer draws to 17, hitting soft 17. */
    fun dealerShouldHit(cards: List<Card>): Boolean {
        val t = total(cards)
        return t < 17 || (t == 17 && isSoft(cards))
    }
}

/** Rules specific to Double Down Madness. */
object DoubleDownRules {

    /** The Push 22 side bet, read off the dealer's finished hand. */
    enum class Push22Win(val label: String, val payout: Int) {
        SUITED("Same-suit 22", 75),
        COLORED("Same-color 22", 50),
        ANY("Any combo 22", 15),
    }

    /**
     * Pays when the dealer lands on 22 — the same 22 that pushes the main bet.
     * Suit and colour are read across the whole dealer hand, however many cards
     * it took to get there.
     */
    /**
     * What comes back on the main bet, stake included — so a push returns
     * exactly the stake and a loss returns nothing. [stake] is everything out
     * there after any doubling; [betUnit] is the bet it started as, which is
     * what a blackjack is paid 3:2 on.
     */
    fun settleHand(
        player: List<Card>,
        dealer: List<Card>,
        stake: Int,
        betUnit: Int,
        doubled: Boolean,
    ): Double {
        val dealerBj = BlackjackCore.isBlackjack(dealer)
        val dTotal = BlackjackCore.total(dealer)
        val pTotal = BlackjackCore.total(player)
        // A hand that took a card can no longer be a blackjack, however it adds up.
        val playerBj = !doubled && BlackjackCore.isBlackjack(player)
        return when {
            playerBj && dealerBj -> stake.toDouble()
            playerBj -> stake + 1.5 * betUnit
            dealerBj -> 0.0
            BlackjackCore.isBust(player) -> 0.0
            // The dealer's 22 pays for all that doubling: it pushes rather than loses.
            dTotal == 22 -> stake.toDouble()
            dTotal > 21 -> stake * 2.0
            pTotal > dTotal -> stake * 2.0
            pTotal == dTotal -> stake.toDouble()
            else -> 0.0
        }
    }

    /** What comes back on the Push 22 side bet, stake included. */
    fun settlePush22(dealer: List<Card>, stake: Int): Double {
        val win = push22(dealer) ?: return 0.0
        return stake * (win.payout + 1.0)
    }

    fun push22(dealerCards: List<Card>): Push22Win? {
        if (dealerCards.isEmpty()) return null
        if (BlackjackCore.total(dealerCards) != 22) return null
        val first = dealerCards.first().suit
        return when {
            dealerCards.all { it.suit == first } -> Push22Win.SUITED
            dealerCards.all { it.suit.isRed == first.isRed } -> Push22Win.COLORED
            else -> Push22Win.ANY
        }
    }
}

/** Rules specific to Free Bet Blackjack. */
object FreeBetRules {

    /** Free double: first two cards forming a hard 9, 10, or 11. */
    fun canFreeDouble(cards: List<Card>): Boolean {
        if (cards.size != 2) return false
        if (BlackjackCore.isSoft(cards)) return false
        return BlackjackCore.total(cards) in 9..11
    }

    /**
     * Doubling with your own money is limited to hands holding an ace — every
     * other paid double was dropped from this table.
     */
    fun canPaidDouble(cards: List<Card>): Boolean =
        cards.size == 2 && cards.any { it.rank == Rank.ACE }

    /** Any equal-value pair may split; only 10-value pairs cost real money. */
    fun canSplit(cards: List<Card>): Boolean =
        cards.size == 2 &&
            BlackjackCore.cardValue(cards[0].rank) == BlackjackCore.cardValue(cards[1].rank)

    fun isFreeSplit(cards: List<Card>): Boolean =
        canSplit(cards) && BlackjackCore.cardValue(cards[0].rank) != 10

    /** Dealer 22 pushes every live player hand instead of busting. */
    fun dealerPushes(dealerCards: List<Card>): Boolean =
        BlackjackCore.total(dealerCards) == 22

    /**
     * Pot of Gold side bet: pays by total Free Bet coins collected in the hand.
     * 1→3:1, 2→10:1, 3→30:1, 4→60:1, 5→100:1, 6→300:1, 7→1000:1.
     */
    fun potOfGoldMultiplier(coins: Int): Int = when (coins) {
        1 -> 3
        2 -> 10
        3 -> 30
        4 -> 60
        5 -> 100
        6 -> 300
        else -> if (coins >= 7) 1000 else 0
    }
}
