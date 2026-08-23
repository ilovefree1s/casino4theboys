package com.example.casinogames

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import com.example.casinogames.games.djwild.DjWildDeck
import com.example.casinogames.games.djwild.DjWildEval
import com.example.casinogames.games.djwild.WildCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DJ Wild's deck and its wild-card hand ranking. */
class DjWildHandTest {

    private fun c(spec: String): Card {
        val rank = when (spec[0]) {
            'A' -> Rank.ACE; 'K' -> Rank.KING; 'Q' -> Rank.QUEEN; 'J' -> Rank.JACK
            'T' -> Rank.TEN; '9' -> Rank.NINE; '8' -> Rank.EIGHT; '7' -> Rank.SEVEN
            '6' -> Rank.SIX; '5' -> Rank.FIVE; '4' -> Rank.FOUR; '3' -> Rank.THREE
            '2' -> Rank.TWO; 'W' -> Rank.JOKER
            else -> error("bad rank in $spec")
        }
        val suit = when (spec[1]) {
            's' -> Suit.SPADES; 'h' -> Suit.HEARTS; 'd' -> Suit.DIAMONDS; 'c' -> Suit.CLUBS
            else -> error("bad suit in $spec")
        }
        return Card(rank, suit)
    }

    private fun hand(vararg specs: String) = specs.map(::c)

    private fun cat(vararg specs: String) = DjWildEval.score(hand(*specs)).category

    // ---- the deck ----

    @Test
    fun `every deuce and the joker play wild`() {
        assertTrue(DjWildDeck.isWild(c("2s")))
        assertTrue(DjWildDeck.isWild(c("2h")))
        assertTrue(DjWildDeck.isWild(c("2d")))
        assertTrue(DjWildDeck.isWild(c("2c")))
        assertTrue(DjWildDeck.isWild(c("Ws")))
        assertFalse(DjWildDeck.isWild(c("3s")))
        assertFalse(DjWildDeck.isWild(c("As")))
    }

    @Test
    fun `the shoe deals fifty three cards and only one joker`() {
        val shoe = com.example.casinogames.games.core.Shoe(
            decks = DjWildDeck.DECKS,
            jokers = DjWildDeck.JOKERS,
        )
        assertEquals(53, shoe.cardsRemaining)
        val all = (1..53).map { shoe.draw() }
        assertEquals(1, all.count { it.isJoker })
        assertEquals(4, all.count { it.rank == Rank.TWO })
        // Five wilds in the deck: the four deuces and the joker.
        assertEquals(5, all.count(DjWildDeck::isWild))
        assertEquals(53, all.distinct().size)
    }

    @Test
    fun `an ordinary shoe still has no jokers`() {
        val shoe = com.example.casinogames.games.core.Shoe(decks = 1)
        assertEquals(52, shoe.cardsRemaining)
        assertEquals(0, (1..52).map { shoe.draw() }.count { it.isJoker })
    }

    // ---- natural hands rank as they always did ----

    @Test
    fun `hands without a wild score straight`() {
        assertEquals(WildCategory.HIGH_CARD, cat("As", "Kh", "9d", "7c", "5s"))
        assertEquals(WildCategory.PAIR, cat("As", "Ah", "9d", "7c", "5s"))
        assertEquals(WildCategory.TWO_PAIR, cat("As", "Ah", "9d", "9c", "5s"))
        assertEquals(WildCategory.THREE_KIND, cat("As", "Ah", "Ad", "9c", "5s"))
        assertEquals(WildCategory.STRAIGHT, cat("9s", "8h", "7d", "6c", "5s"))
        assertEquals(WildCategory.FLUSH, cat("As", "Js", "9s", "7s", "5s"))
        assertEquals(WildCategory.FULL_HOUSE, cat("As", "Ah", "Ad", "9c", "9s"))
        assertEquals(WildCategory.FOUR_KIND, cat("As", "Ah", "Ad", "Ac", "9s"))
        assertEquals(WildCategory.STRAIGHT_FLUSH, cat("9s", "8s", "7s", "6s", "5s"))
        assertEquals(WildCategory.ROYAL_FLUSH, cat("As", "Ks", "Qs", "Js", "Ts"))
    }

    // ---- a wild becomes whatever helps most ----

    @Test
    fun `one wild fills a pair into trips`() {
        assertEquals(WildCategory.THREE_KIND, cat("As", "Ah", "9d", "7c", "2s"))
    }

    @Test
    fun `one wild completes a straight`() {
        // 9-8-6-5 plus a wild is the seven.
        assertEquals(WildCategory.STRAIGHT, cat("9s", "8h", "6d", "5c", "2s"))
    }

    @Test
    fun `one wild completes a flush over a straight`() {
        // Four spades and a wild: a flush beats the straight it could also make.
        assertEquals(WildCategory.FLUSH, cat("As", "Js", "9s", "7s", "2h"))
    }

    @Test
    fun `one wild completes a royal flush`() {
        assertEquals(WildCategory.ROYAL_FLUSH, cat("As", "Ks", "Qs", "Js", "2h"))
    }

    @Test
    fun `a wild never plays as itself when something beats it`() {
        // The wild is a deuce, but reading it as one would leave only a pair.
        assertEquals(WildCategory.THREE_KIND, cat("2s", "As", "Ah", "9d", "7c"))
    }

    @Test
    fun `two wilds make four of a kind out of a pair`() {
        assertEquals(WildCategory.FOUR_KIND, cat("As", "Ah", "2d", "2c", "9s"))
    }

    @Test
    fun `three wilds and a pair make five of a kind`() {
        assertEquals(WildCategory.FIVE_KIND, cat("As", "Ah", "2d", "2c", "Ws"))
    }

    @Test
    fun `four wilds beat everything below the top`() {
        // Four wilds plus any card is five of a kind at worst.
        val v = DjWildEval.score(hand("2s", "2h", "2d", "Ws", "Ac"))
        assertTrue(
            "four wilds should reach five of a kind or better, got ${v.category}",
            v.category.ordinal >= WildCategory.FIVE_KIND.ordinal,
        )
    }

    @Test
    fun `all four deuces and the joker are five wilds`() {
        assertEquals(WildCategory.FIVE_WILDS, cat("2s", "2h", "2d", "2c", "Ws"))
    }

    // ---- comparing hands ----

    @Test
    fun `a wild trips loses to natural quads`() {
        val wildTrips = DjWildEval.score(hand("As", "Ah", "9d", "7c", "2s"))
        val quads = DjWildEval.score(hand("9s", "9h", "9d", "9c", "5s"))
        assertTrue(quads > wildTrips)
    }

    @Test
    fun `same category splits on the higher rank`() {
        val aces = DjWildEval.score(hand("As", "Ah", "Ad", "9c", "5s"))
        val kings = DjWildEval.score(hand("Ks", "Kh", "Kd", "9c", "5s"))
        assertTrue(aces > kings)
        assertEquals(0, aces.compareTo(DjWildEval.score(hand("Ac", "Ad", "Ah", "9s", "5h"))))
    }

    @Test
    fun `five wilds outrank a royal flush`() {
        val fiveWilds = DjWildEval.score(hand("2s", "2h", "2d", "2c", "Ws"))
        val royal = DjWildEval.score(hand("As", "Ks", "Qs", "Js", "Ts"))
        assertTrue(fiveWilds > royal)
    }
}
