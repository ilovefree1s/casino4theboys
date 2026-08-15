package com.example.casinogames

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import com.example.casinogames.games.holdem.HandCategory
import com.example.casinogames.games.holdem.PokerEval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokerEvalTest {

    /** "As" -> ace of spades. Ranks 2-9, T, J, Q, K, A; suits s h d c. */
    private fun cards(spec: String): List<Card> = spec.trim().split(" ").map { token ->
        val rank = when (token[0]) {
            'A' -> Rank.ACE
            'K' -> Rank.KING
            'Q' -> Rank.QUEEN
            'J' -> Rank.JACK
            'T' -> Rank.TEN
            '9' -> Rank.NINE
            '8' -> Rank.EIGHT
            '7' -> Rank.SEVEN
            '6' -> Rank.SIX
            '5' -> Rank.FIVE
            '4' -> Rank.FOUR
            '3' -> Rank.THREE
            '2' -> Rank.TWO
            else -> error("bad rank ${token[0]}")
        }
        val suit = when (token[1]) {
            's' -> Suit.SPADES
            'h' -> Suit.HEARTS
            'd' -> Suit.DIAMONDS
            'c' -> Suit.CLUBS
            else -> error("bad suit ${token[1]}")
        }
        Card(rank, suit)
    }

    private fun category(spec: String) = PokerEval.best(cards(spec)).category

    // ---- categories ----

    @Test
    fun `ten through ace in one suit is the royal`() {
        assertEquals(HandCategory.ROYAL_FLUSH, category("Ts Js Qs Ks As 2h 7d"))
    }

    @Test
    fun `a lower suited run is a straight flush`() {
        assertEquals(HandCategory.STRAIGHT_FLUSH, category("5h 6h 7h 8h 9h Ks 2d"))
    }

    @Test
    fun `the wheel counts as a five-high straight flush`() {
        val v = PokerEval.best(cards("Ah 2h 3h 4h 5h Kd 9c"))
        assertEquals(HandCategory.STRAIGHT_FLUSH, v.category)
        assertEquals(listOf(5), v.tiebreak)
    }

    @Test
    fun `king ace two is not a straight`() {
        assertEquals(HandCategory.HIGH_CARD, category("Kd Ah 2c 7s 9h Jd 4c"))
    }

    @Test
    fun `quads beat a full house`() {
        assertTrue(PokerEval.best(cards("9s 9h 9d 9c 2s 3h 4d")) >
            PokerEval.best(cards("9s 9h 9d 2c 2s 3h 4d")))
    }

    @Test
    fun `full house beats a flush`() {
        assertTrue(PokerEval.best(cards("Ks Kh Kd 4c 4s 7h 9d")) >
            PokerEval.best(cards("2s 5s 8s Js Ks 3h 4d")))
    }

    @Test
    fun `flush beats a straight`() {
        assertTrue(PokerEval.best(cards("2s 5s 8s Js Ks 3h 4d")) >
            PokerEval.best(cards("5s 6h 7d 8c 9s Kh 2d")))
    }

    @Test
    fun `two pair beats one pair`() {
        assertTrue(PokerEval.best(cards("As Ah Kd Kc 4s 7h 9d")) >
            PokerEval.best(cards("As Ah Kd 5c 4s 7h 9d")))
    }

    @Test
    fun `seven cards pick the best five, not the first five`() {
        // The first five cards are junk; the flush needs the last two.
        assertEquals(HandCategory.FLUSH, category("2h 4h 7h 9d Ts Jh Kh"))
    }

    // ---- tiebreaks ----

    @Test
    fun `higher kicker wins with the same pair`() {
        assertTrue(PokerEval.best(cards("As Ah Kd 9c 4s 2h 3d")) >
            PokerEval.best(cards("Ac Ad Qh 9s 4c 2s 3h")))
    }

    @Test
    fun `the higher straight wins`() {
        assertTrue(PokerEval.best(cards("6s 7h 8d 9c Ts 2h 3d")) >
            PokerEval.best(cards("5s 6h 7d 8c 9s 2h 3d")))
    }

    @Test
    fun `a wheel loses to a six-high straight`() {
        assertTrue(PokerEval.best(cards("2s 3h 4d 5c 6s Kh Qd")) >
            PokerEval.best(cards("Ah 2d 3c 4s 5h Kd Qc")))
    }

    @Test
    fun `identical hands from different suits tie`() {
        assertEquals(0, PokerEval.best(cards("As Ks Qh Jd Tc 2h 3d"))
            .compareTo(PokerEval.best(cards("Ah Kh Qs Jc Td 2s 3c"))))
    }

    @Test
    fun `the board can play for both players`() {
        // Nobody improves on the board's straight, so it's a push.
        val board = "9s Ts Jh Qd Kc"
        assertEquals(0, PokerEval.best(cards("$board 2h 3d"))
            .compareTo(PokerEval.best(cards("$board 4s 5c"))))
    }

    @Test
    fun `higher two pair beats lower two pair`() {
        assertTrue(PokerEval.best(cards("As Ah 2d 2c 9s 5h 7d")) >
            PokerEval.best(cards("Ks Kh Qd Qc 9s 5h 7d")))
    }

    @Test
    fun `three of a kind beats two pair`() {
        assertTrue(PokerEval.best(cards("7s 7h 7d Kc 4s 2h 9d")) >
            PokerEval.best(cards("As Ah Kd Kc 4s 2h 9d")))
    }
}
