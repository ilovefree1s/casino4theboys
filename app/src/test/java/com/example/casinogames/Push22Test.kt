package com.example.casinogames

import com.example.casinogames.games.blackjack.DoubleDownRules
import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Push 22 side bet on the Double Down Madness felt. */
class Push22Test {

    private fun hand(vararg cards: Pair<Rank, Suit>) = cards.map { Card(it.first, it.second) }

    @Test
    fun `no 22 pays nothing`() {
        assertNull(DoubleDownRules.push22(hand(Rank.KING to Suit.SPADES, Rank.SEVEN to Suit.SPADES)))
        assertNull(DoubleDownRules.push22(emptyList()))
        // 23 busts the dealer outright — only 22 is the house's own number.
        assertNull(
            DoubleDownRules.push22(
                hand(Rank.KING to Suit.SPADES, Rank.EIGHT to Suit.SPADES, Rank.FIVE to Suit.SPADES)
            )
        )
    }

    @Test
    fun `one suit throughout pays 75`() {
        val win = DoubleDownRules.push22(
            hand(Rank.EIGHT to Suit.HEARTS, Rank.NINE to Suit.HEARTS, Rank.FIVE to Suit.HEARTS)
        )
        assertEquals(DoubleDownRules.Push22Win.SUITED, win)
        assertEquals(75, win?.payout)
    }

    @Test
    fun `one colour across two suits pays 50`() {
        val win = DoubleDownRules.push22(
            hand(Rank.EIGHT to Suit.HEARTS, Rank.NINE to Suit.DIAMONDS, Rank.FIVE to Suit.HEARTS)
        )
        assertEquals(DoubleDownRules.Push22Win.COLORED, win)
        assertEquals(50, win?.payout)
    }

    @Test
    fun `mixed colours pay 15`() {
        val win = DoubleDownRules.push22(
            hand(Rank.EIGHT to Suit.HEARTS, Rank.NINE to Suit.SPADES, Rank.FIVE to Suit.HEARTS)
        )
        assertEquals(DoubleDownRules.Push22Win.ANY, win)
        assertEquals(15, win?.payout)
    }

    @Test
    fun `an ace counted low still makes 22`() {
        // A-K-A-K is 22 hard; no ace may be counted as eleven without busting.
        val win = DoubleDownRules.push22(
            hand(
                Rank.ACE to Suit.CLUBS,
                Rank.KING to Suit.CLUBS,
                Rank.ACE to Suit.CLUBS,
                Rank.KING to Suit.CLUBS,
            )
        )
        assertEquals(DoubleDownRules.Push22Win.SUITED, win)
    }
}
