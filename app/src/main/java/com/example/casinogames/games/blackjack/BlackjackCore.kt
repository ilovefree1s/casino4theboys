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

/** Rules specific to Free Bet Blackjack. */
object FreeBetRules {

    /** Free double: first two cards forming a hard 9, 10, or 11. */
    fun canFreeDouble(cards: List<Card>): Boolean {
        if (cards.size != 2) return false
        if (BlackjackCore.isSoft(cards)) return false
        return BlackjackCore.total(cards) in 9..11
    }

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
