package com.example.casinogames

import com.example.casinogames.games.blackjack.Blazing777Rules
import com.example.casinogames.games.blackjack.Blazing777Rules.BlazingWin
import com.example.casinogames.games.blackjack.Blazing777Rules.TriluxWin
import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Blazing777RulesTest {

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    // ---- Blazing 7s ----

    @Test
    fun `three sevens all one suit is the top tier`() {
        val hand = listOf(
            c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS),
        )
        assertEquals(BlazingWin.SUITED_777, Blazing777Rules.blazing(hand))
    }

    @Test
    fun `three sevens sharing a colour but not a suit pay the coloured tier`() {
        val hand = listOf(
            c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.DIAMONDS), c(Rank.SEVEN, Suit.HEARTS),
        )
        assertEquals(BlazingWin.COLORED_777, Blazing777Rules.blazing(hand))
    }

    @Test
    fun `three sevens of mixed colours pay the plain tier`() {
        val hand = listOf(
            c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.SPADES), c(Rank.SEVEN, Suit.DIAMONDS),
        )
        assertEquals(BlazingWin.MIXED_777, Blazing777Rules.blazing(hand))
    }

    @Test
    fun `two and one seven pay their own tiers`() {
        val two = listOf(
            c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.CLUBS), c(Rank.KING, Suit.SPADES),
        )
        assertEquals(BlazingWin.TWO_SEVENS, Blazing777Rules.blazing(two))
        val one = listOf(
            c(Rank.SEVEN, Suit.HEARTS), c(Rank.TWO, Suit.CLUBS), c(Rank.KING, Suit.SPADES),
        )
        assertEquals(BlazingWin.ONE_SEVEN, Blazing777Rules.blazing(one))
    }

    @Test
    fun `no sevens pays nothing`() {
        val hand = listOf(
            c(Rank.ACE, Suit.HEARTS), c(Rank.TWO, Suit.CLUBS), c(Rank.KING, Suit.SPADES),
        )
        assertNull(Blazing777Rules.blazing(hand))
    }

    // ---- TriLux ----

    @Test
    fun `queen king ace in one suit is the royal`() {
        val hand = listOf(
            c(Rank.QUEEN, Suit.SPADES), c(Rank.KING, Suit.SPADES), c(Rank.ACE, Suit.SPADES),
        )
        assertEquals(TriluxWin.ROYAL_FLUSH, Blazing777Rules.trilux(hand))
    }

    @Test
    fun `off-suit queen king ace is only a straight`() {
        val hand = listOf(
            c(Rank.QUEEN, Suit.SPADES), c(Rank.KING, Suit.HEARTS), c(Rank.ACE, Suit.SPADES),
        )
        assertEquals(TriluxWin.STRAIGHT, Blazing777Rules.trilux(hand))
    }

    @Test
    fun `trips outrank a flush even when suited`() {
        val hand = listOf(
            c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.SPADES),
        )
        assertEquals(TriluxWin.THREE_KIND, Blazing777Rules.trilux(hand))
    }

    @Test
    fun `straight flush beats three of a kind`() {
        val hand = listOf(
            c(Rank.NINE, Suit.SPADES), c(Rank.TEN, Suit.SPADES), c(Rank.JACK, Suit.SPADES),
        )
        assertEquals(TriluxWin.STRAIGHT_FLUSH, Blazing777Rules.trilux(hand))
    }

    @Test
    fun `mixed-suit trips also pay three of a kind`() {
        val hand = listOf(
            c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS), c(Rank.NINE, Suit.CLUBS),
        )
        assertEquals(TriluxWin.THREE_KIND, Blazing777Rules.trilux(hand))
    }

    @Test
    fun `off-suit run is a straight`() {
        val hand = listOf(
            c(Rank.NINE, Suit.CLUBS), c(Rank.TEN, Suit.HEARTS), c(Rank.JACK, Suit.SPADES),
        )
        assertEquals(TriluxWin.STRAIGHT, Blazing777Rules.trilux(hand))
    }

    @Test
    fun `ace plays high and low in straights`() {
        val wheel = listOf(
            c(Rank.ACE, Suit.CLUBS), c(Rank.TWO, Suit.HEARTS), c(Rank.THREE, Suit.SPADES),
        )
        assertEquals(TriluxWin.STRAIGHT, Blazing777Rules.trilux(wheel))
        val broadway = listOf(
            c(Rank.QUEEN, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), c(Rank.ACE, Suit.SPADES),
        )
        assertEquals(TriluxWin.STRAIGHT, Blazing777Rules.trilux(broadway))
    }

    @Test
    fun `king ace two is not a straight`() {
        val hand = listOf(
            c(Rank.KING, Suit.CLUBS), c(Rank.ACE, Suit.HEARTS), c(Rank.TWO, Suit.SPADES),
        )
        assertNull(Blazing777Rules.trilux(hand))
    }

    @Test
    fun `same suit with no run pays the flush`() {
        val hand = listOf(
            c(Rank.TWO, Suit.SPADES), c(Rank.SEVEN, Suit.SPADES), c(Rank.KING, Suit.SPADES),
        )
        assertEquals(TriluxWin.FLUSH, Blazing777Rules.trilux(hand))
    }

    @Test
    fun `a pair is not a paying trilux hand`() {
        val hand = listOf(
            c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS), c(Rank.KING, Suit.CLUBS),
        )
        assertNull(Blazing777Rules.trilux(hand))
    }
}
