package com.example.casinogames

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import com.example.casinogames.games.shootout.ShootoutCategory
import com.example.casinogames.games.shootout.ShootoutEval
import com.example.casinogames.games.shootout.ShootoutRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShootoutRulesTest {

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    @Test
    fun `five of a kind sits between quads and a straight flush`() {
        val quads = ShootoutEval.score(
            listOf(
                c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.DIAMONDS),
                c(Rank.KING, Suit.CLUBS), c(Rank.ACE, Suit.SPADES),
            )
        )
        val quints = ShootoutEval.score(
            listOf(
                c(Rank.THREE, Suit.SPADES), c(Rank.THREE, Suit.HEARTS), c(Rank.THREE, Suit.DIAMONDS),
                c(Rank.THREE, Suit.CLUBS), c(Rank.THREE, Suit.SPADES),
            )
        )
        val straightFlush = ShootoutEval.score(
            listOf(
                c(Rank.FIVE, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS),
                c(Rank.EIGHT, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS),
            )
        )
        assertEquals(ShootoutCategory.FIVE_KIND, quints.category)
        assertTrue(quints > quads)
        assertTrue(straightFlush > quints)
    }

    @Test
    fun `five of the very same card tops everything`() {
        val suited = ShootoutEval.score(List(5) { c(Rank.TWO, Suit.CLUBS) })
        val royal = ShootoutEval.score(
            listOf(
                c(Rank.ACE, Suit.SPADES), c(Rank.KING, Suit.SPADES), c(Rank.QUEEN, Suit.SPADES),
                c(Rank.JACK, Suit.SPADES), c(Rank.TEN, Suit.SPADES),
            )
        )
        assertEquals(ShootoutCategory.FIVE_KIND_SUITED, suited.category)
        assertTrue(suited > royal)
    }

    @Test
    fun `a duplicated card still makes a pair`() {
        val hand = ShootoutEval.score(
            listOf(
                c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.SPADES), c(Rank.TWO, Suit.HEARTS),
                c(Rank.FIVE, Suit.DIAMONDS), c(Rank.KING, Suit.CLUBS),
            )
        )
        assertEquals(ShootoutCategory.PAIR, hand.category)
    }

    @Test
    fun `the house keeps a high pair over ace-king`() {
        val kept = ShootoutRules.houseWay(
            listOf(
                c(Rank.ACE, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.NINE, Suit.DIAMONDS), c(Rank.NINE, Suit.CLUBS),
            )
        )
        assertEquals(setOf(c(Rank.NINE, Suit.DIAMONDS), c(Rank.NINE, Suit.CLUBS)), kept.toSet())
    }

    @Test
    fun `the house keeps ace-king over a low pair`() {
        val kept = ShootoutRules.houseWay(
            listOf(
                c(Rank.ACE, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.SIX, Suit.DIAMONDS), c(Rank.SIX, Suit.CLUBS),
            )
        )
        assertEquals(setOf(c(Rank.ACE, Suit.SPADES), c(Rank.KING, Suit.HEARTS)), kept.toSet())
    }

    @Test
    fun `the dealer takes a tie and the bonus still pays`() {
        val board = listOf(
            c(Rank.ACE, Suit.HEARTS), c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS),
            c(Rank.JACK, Suit.HEARTS), c(Rank.TEN, Suit.HEARTS),
        )
        val s = ShootoutRules.settle(
            playerHole = listOf(c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS)),
            dealerHole = listOf(c(Rank.FOUR, Suit.DIAMONDS), c(Rank.FIVE, Suit.DIAMONDS)),
            board = board,
            poker = 100.0,
            bonus = 10.0,
        )
        assertEquals(ShootoutRules.Outcome.LOSE, s.outcome)
        assertEquals(0.0, s.pokerReturn, 0.001)
        assertEquals(ShootoutRules.BonusPay.ROYAL_FLUSH, s.bonusWin)
        assertEquals(5010.0, s.bonusReturn, 0.001)
    }
}
