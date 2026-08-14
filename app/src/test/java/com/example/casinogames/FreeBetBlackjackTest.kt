package com.example.casinogames

import com.example.casinogames.games.blackjack.BlackjackCore
import com.example.casinogames.games.blackjack.FreeBetRules
import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeBetBlackjackTest {

    private fun cards(vararg ranks: Rank) = ranks.map { Card(it, Suit.SPADES) }

    @Test
    fun `ace counts as eleven only when it fits`() {
        assertEquals(21, BlackjackCore.total(cards(Rank.ACE, Rank.KING)))
        assertEquals(12, BlackjackCore.total(cards(Rank.ACE, Rank.SIX, Rank.FIVE)))
        assertEquals(12, BlackjackCore.total(cards(Rank.ACE, Rank.ACE)))
        assertTrue(BlackjackCore.isSoft(cards(Rank.ACE, Rank.SIX)))
        assertFalse(BlackjackCore.isSoft(cards(Rank.ACE, Rank.SIX, Rank.KING)))
    }

    @Test
    fun `blackjack is exactly two cards totalling twenty one`() {
        assertTrue(BlackjackCore.isBlackjack(cards(Rank.ACE, Rank.QUEEN)))
        assertFalse(BlackjackCore.isBlackjack(cards(Rank.SEVEN, Rank.SEVEN, Rank.SEVEN)))
    }

    @Test
    fun `dealer hits soft seventeen and stands on hard seventeen`() {
        assertTrue(BlackjackCore.dealerShouldHit(cards(Rank.ACE, Rank.SIX)))
        assertFalse(BlackjackCore.dealerShouldHit(cards(Rank.KING, Rank.SEVEN)))
        assertTrue(BlackjackCore.dealerShouldHit(cards(Rank.KING, Rank.SIX)))
        assertFalse(BlackjackCore.dealerShouldHit(cards(Rank.KING, Rank.EIGHT)))
    }

    @Test
    fun `free double only on hard nine ten eleven`() {
        assertTrue(FreeBetRules.canFreeDouble(cards(Rank.FOUR, Rank.FIVE)))
        assertTrue(FreeBetRules.canFreeDouble(cards(Rank.SIX, Rank.FOUR)))
        assertTrue(FreeBetRules.canFreeDouble(cards(Rank.SIX, Rank.FIVE)))
        // soft 20 is not a free double even though 9 could be made of it
        assertFalse(FreeBetRules.canFreeDouble(cards(Rank.ACE, Rank.NINE)))
        assertFalse(FreeBetRules.canFreeDouble(cards(Rank.FIVE, Rank.SEVEN)))
        assertFalse(FreeBetRules.canFreeDouble(cards(Rank.FOUR, Rank.FOUR)))
    }

    @Test
    fun `free split on pairs except ten value pairs`() {
        assertTrue(FreeBetRules.isFreeSplit(cards(Rank.EIGHT, Rank.EIGHT)))
        assertTrue(FreeBetRules.isFreeSplit(cards(Rank.ACE, Rank.ACE)))
        assertTrue(FreeBetRules.canSplit(cards(Rank.KING, Rank.TEN)))
        assertFalse(FreeBetRules.isFreeSplit(cards(Rank.KING, Rank.TEN)))
        assertFalse(FreeBetRules.canSplit(cards(Rank.KING, Rank.NINE)))
    }

    @Test
    fun `pot of gold pays by coins collected`() {
        assertEquals(0, FreeBetRules.potOfGoldMultiplier(0))
        assertEquals(3, FreeBetRules.potOfGoldMultiplier(1))
        assertEquals(10, FreeBetRules.potOfGoldMultiplier(2))
        assertEquals(30, FreeBetRules.potOfGoldMultiplier(3))
        assertEquals(60, FreeBetRules.potOfGoldMultiplier(4))
        assertEquals(100, FreeBetRules.potOfGoldMultiplier(5))
        assertEquals(300, FreeBetRules.potOfGoldMultiplier(6))
        assertEquals(1000, FreeBetRules.potOfGoldMultiplier(7))
    }

    @Test
    fun `dealer twenty two pushes`() {
        assertTrue(FreeBetRules.dealerPushes(cards(Rank.KING, Rank.SIX, Rank.SIX)))
        assertFalse(FreeBetRules.dealerPushes(cards(Rank.KING, Rank.SIX, Rank.SEVEN)))
        assertFalse(FreeBetRules.dealerPushes(cards(Rank.KING, Rank.QUEEN)))
    }
}
