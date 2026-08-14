package com.example.casinogames

import com.example.casinogames.games.blackjack.BlackjackCore
import com.example.casinogames.games.blackjack.FreeBetRules
import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The table offers a free double on hard 9-11 and a paid double only with an ace. */
class FreeBetDoubleRuleTest {

    private fun hand(vararg ranks: Rank) =
        ranks.mapIndexed { i, r -> Card(r, if (i % 2 == 0) Suit.SPADES else Suit.HEARTS) }

    private fun offered(vararg ranks: Rank): Boolean {
        val cards = hand(*ranks)
        return FreeBetRules.canFreeDouble(cards) || FreeBetRules.canPaidDouble(cards)
    }

    @Test
    fun `every ace hand short of blackjack offers a double`() {
        val partners = listOf(
            Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX,
            Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.ACE,
        )
        for (partner in partners) {
            assertTrue(
                "ace + ${partner.label} should offer a double",
                offered(Rank.ACE, partner),
            )
        }
    }

    @Test
    fun `ace with a ten-value card is blackjack, which never doubles`() {
        for (ten in listOf(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING)) {
            val cards = hand(Rank.ACE, ten)
            assertTrue("ace + ${ten.label} is blackjack", BlackjackCore.isBlackjack(cards))
        }
    }

    @Test
    fun `hard nine ten and eleven double free`() {
        assertTrue(FreeBetRules.canFreeDouble(hand(Rank.FOUR, Rank.FIVE)))
        assertTrue(FreeBetRules.canFreeDouble(hand(Rank.SIX, Rank.FOUR)))
        assertTrue(FreeBetRules.canFreeDouble(hand(Rank.SEVEN, Rank.FOUR)))
    }

    @Test
    fun `aceless hands outside nine to eleven never double`() {
        assertFalse(offered(Rank.KING, Rank.QUEEN))   // 20
        assertFalse(offered(Rank.NINE, Rank.NINE))    // 18
        assertFalse(offered(Rank.FIVE, Rank.THREE))   // 8
        assertFalse(offered(Rank.KING, Rank.TWO))     // 12
    }
}
