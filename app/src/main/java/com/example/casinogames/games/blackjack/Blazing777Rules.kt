package com.example.casinogames.games.blackjack

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit

/**
 * Side bets for Blazing 777s. Both are decided by the player's first two cards
 * plus the dealer's up card, so they settle the moment the deal finishes.
 */
object Blazing777Rules {

    /**
     * Blazing 7s rides free, so only the jackpot hand pays: three sevens of
     * diamonds. The award is flat money, not a multiple of a stake.
     */
    enum class BlazingWin(val label: String, val award: Int) {
        DIAMOND_777("Three 7♦", 500_000),
    }

    enum class TriluxWin(val label: String, val payout: Int) {
        ROYAL_FLUSH("Royal Flush", 50),
        STRAIGHT_FLUSH("Straight Flush", 35),
        THREE_KIND("Three of a Kind", 25),
        STRAIGHT("Straight", 15),
        FLUSH("Flush", 10),
    }

    /**
     * The jackpot: all three face-up cards — the player's two plus the dealer's
     * up card — are the seven of diamonds. Anything less pays nothing and is
     * not announced.
     */
    fun blazing(cards: List<Card>): BlazingWin? {
        val diamondSevens = cards.count {
            it.rank == Rank.SEVEN && it.suit == Suit.DIAMONDS
        }
        return if (diamondSevens == 3) BlazingWin.DIAMOND_777 else null
    }

    /** TriLux scores those same three cards as a three-card poker hand. */
    fun trilux(cards: List<Card>): TriluxWin? {
        if (cards.size != 3) return null
        val ranks = cards.map { it.rank }
        val flush = cards.all { it.suit == cards[0].suit }
        val trips = ranks.all { it == ranks[0] }
        val straight = isStraight(ranks)
        // Q-K-A is the top run, so suited it becomes the royal.
        val royal = straight &&
            ranks.toSet() == setOf(Rank.QUEEN, Rank.KING, Rank.ACE)
        return when {
            royal && flush -> TriluxWin.ROYAL_FLUSH
            straight && flush -> TriluxWin.STRAIGHT_FLUSH
            trips -> TriluxWin.THREE_KIND
            straight -> TriluxWin.STRAIGHT
            flush -> TriluxWin.FLUSH
            else -> null
        }
    }

    /** Ace plays high or low: A-2-3 and Q-K-A both count. */
    private fun isStraight(ranks: List<Rank>): Boolean {
        val order = listOf(
            Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN,
            Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE,
        )
        val idx = ranks.map { order.indexOf(it) }.sorted()
        if (idx.distinct().size != 3) return false
        if (idx[1] == idx[0] + 1 && idx[2] == idx[1] + 1) return true
        // wheel: A-2-3
        return idx == listOf(0, 1, order.lastIndex)
    }
}
