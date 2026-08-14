package com.example.casinogames.games.blackjack

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank

/**
 * Side bets for Blazing 777s. Both are decided by the player's first two cards
 * plus the dealer's up card, so they settle the moment the deal finishes.
 */
object Blazing777Rules {

    enum class BlazingWin(val label: String, val payout: Int) {
        SUITED_777("Suited 777", 1000),
        COLORED_777("Colored 777", 250),
        MIXED_777("Three 7s", 100),
        TWO_SEVENS("Two 7s", 25),
        ONE_SEVEN("One 7", 1),
    }

    enum class TriluxWin(val label: String, val payout: Int) {
        SUITED_TRIPS("Suited trips", 100),
        STRAIGHT_FLUSH("Straight flush", 40),
        THREE_KIND("Three of a kind", 30),
        STRAIGHT("Straight", 10),
        FLUSH("Flush", 5),
    }

    /** Blazing 7s counts sevens across the player's two cards and the dealer up card. */
    fun blazing(cards: List<Card>): BlazingWin? {
        val sevens = cards.filter { it.rank == Rank.SEVEN }
        return when (sevens.size) {
            3 -> when {
                sevens.all { it.suit == sevens[0].suit } -> BlazingWin.SUITED_777
                sevens.all { it.suit.isRed == sevens[0].suit.isRed } -> BlazingWin.COLORED_777
                else -> BlazingWin.MIXED_777
            }
            2 -> BlazingWin.TWO_SEVENS
            1 -> BlazingWin.ONE_SEVEN
            else -> null
        }
    }

    /** TriLux scores those same three cards as a three-card poker hand. */
    fun trilux(cards: List<Card>): TriluxWin? {
        if (cards.size != 3) return null
        val ranks = cards.map { it.rank }
        val flush = cards.all { it.suit == cards[0].suit }
        val trips = ranks.all { it == ranks[0] }
        val straight = isStraight(ranks)
        return when {
            trips && flush -> TriluxWin.SUITED_TRIPS
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
