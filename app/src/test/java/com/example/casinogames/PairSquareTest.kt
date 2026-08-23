package com.example.casinogames

import com.example.casinogames.games.blackjack.DoubleDownRules
import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Pair Square side bet on the Double Down Madness felt. */
class PairSquareTest {

    private fun hand(vararg cards: Pair<Rank, Suit>) = cards.map { Card(it.first, it.second) }

    @Test
    fun `one card is not yet an answer`() {
        assertNull(DoubleDownRules.pairSquare(hand(Rank.EIGHT to Suit.HEARTS)))
        assertNull(DoubleDownRules.pairSquare(emptyList()))
    }

    @Test
    fun `different ranks pay nothing`() {
        assertNull(DoubleDownRules.pairSquare(hand(Rank.EIGHT to Suit.HEARTS, Rank.NINE to Suit.HEARTS)))
        // Same value is not the same rank: a king and a ten are not a pair.
        assertNull(DoubleDownRules.pairSquare(hand(Rank.KING to Suit.HEARTS, Rank.TEN to Suit.HEARTS)))
        assertEquals(0.0, DoubleDownRules.settlePairSquare(hand(Rank.EIGHT to Suit.HEARTS, Rank.NINE to Suit.HEARTS), 25), 0.0)
    }

    @Test
    fun `same rank and suit is a perfect pair at 25`() {
        val win = DoubleDownRules.pairSquare(hand(Rank.EIGHT to Suit.HEARTS, Rank.EIGHT to Suit.HEARTS))
        assertEquals(DoubleDownRules.PairSquareWin.PERFECT, win)
        assertEquals(25, win?.payout)
        // Stake comes back with the win.
        assertEquals(650.0, DoubleDownRules.settlePairSquare(hand(Rank.EIGHT to Suit.HEARTS, Rank.EIGHT to Suit.HEARTS), 25), 0.0)
    }

    @Test
    fun `same rank any other suit is a pair at 10`() {
        // Colour does not matter: a red-red pair and a red-black pair pay the same.
        for (other in listOf(Suit.DIAMONDS, Suit.SPADES)) {
            val win = DoubleDownRules.pairSquare(hand(Rank.EIGHT to Suit.HEARTS, Rank.EIGHT to other))
            assertEquals(DoubleDownRules.PairSquareWin.PAIR, win)
            assertEquals(10, win?.payout)
        }
        assertEquals(275.0, DoubleDownRules.settlePairSquare(hand(Rank.EIGHT to Suit.HEARTS, Rank.EIGHT to Suit.CLUBS), 25), 0.0)
    }

    @Test
    fun `only the first two cards count`() {
        // A pair made later in the hand is not the bet.
        assertNull(
            DoubleDownRules.pairSquare(
                hand(Rank.EIGHT to Suit.HEARTS, Rank.FIVE to Suit.CLUBS, Rank.EIGHT to Suit.HEARTS)
            )
        )
        // And a pair up front stays a pair however the hand goes on.
        assertEquals(
            DoubleDownRules.PairSquareWin.PERFECT,
            DoubleDownRules.pairSquare(
                hand(Rank.EIGHT to Suit.HEARTS, Rank.EIGHT to Suit.HEARTS, Rank.FIVE to Suit.CLUBS)
            ),
        )
    }
}
