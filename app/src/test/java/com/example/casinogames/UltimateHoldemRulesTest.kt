package com.example.casinogames

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import com.example.casinogames.games.holdem.BlindPay
import com.example.casinogames.games.holdem.HoldemOutcome
import com.example.casinogames.games.holdem.Street
import com.example.casinogames.games.holdem.TripsPay
import com.example.casinogames.games.holdem.UltimateHoldemRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UltimateHoldemRulesTest {

    private fun cards(spec: String): List<Card> = spec.trim().split(" ").map { token ->
        val rank = when (token[0]) {
            'A' -> Rank.ACE; 'K' -> Rank.KING; 'Q' -> Rank.QUEEN; 'J' -> Rank.JACK
            'T' -> Rank.TEN; '9' -> Rank.NINE; '8' -> Rank.EIGHT; '7' -> Rank.SEVEN
            '6' -> Rank.SIX; '5' -> Rank.FIVE; '4' -> Rank.FOUR; '3' -> Rank.THREE
            '2' -> Rank.TWO
            else -> error("bad rank")
        }
        val suit = when (token[1]) {
            's' -> Suit.SPADES; 'h' -> Suit.HEARTS; 'd' -> Suit.DIAMONDS; 'c' -> Suit.CLUBS
            else -> error("bad suit")
        }
        Card(rank, suit)
    }

    private fun settle(
        player: String, dealer: String, board: String,
        ante: Double = 10.0, blind: Double = 10.0, play: Double = 20.0,
        trips: Double = 0.0, folded: Boolean = false,
    ) = UltimateHoldemRules.settle(
        cards(player), cards(dealer), cards(board), ante, blind, play, trips, folded,
    )

    // ---- bet structure ----

    @Test
    fun `the play bet shrinks as the hand goes on`() {
        assertEquals(listOf(4, 3), UltimateHoldemRules.playOptions(Street.PRE_FLOP))
        assertEquals(listOf(2), UltimateHoldemRules.playOptions(Street.FLOP))
        assertEquals(listOf(1), UltimateHoldemRules.playOptions(Street.RIVER))
    }

    // ---- dealer qualification ----

    @Test
    fun `dealer needs a pair to open`() {
        // Board pairs the dealer's deuce.
        val paired = settle("As Kd", "2h 7c", "2d 9s Jh 4c 6d")
        assertTrue(paired.dealerQualified)
        // Dealer plays the board's ace-high with nothing of their own.
        val nothing = settle("Ks Qd", "2h 7c", "As 9d Jh 4c 6s")
        assertFalse(nothing.dealerQualified)
    }

    @Test
    fun `ante pushes when the dealer does not open`() {
        // Player pairs their king; dealer is stuck with board high card.
        val s = settle("Ks Qd", "3h 8c", "Kh 9d Jc 4s 2h", ante = 10.0, blind = 10.0, play = 40.0)
        assertEquals(HoldemOutcome.WIN, s.outcome)
        assertFalse(s.dealerQualified)
        assertEquals(10.0, s.anteReturn, 0.001)  // pushed, not paid
        assertEquals(80.0, s.playReturn, 0.001)  // play still pays even money
    }

    @Test
    fun `ante pays even money when the dealer opens and loses`() {
        // Dealer opens with a pair of kings; the player's trip aces beat it.
        val s = settle("As Ad", "Kh Kc", "Ah 9d 2c 4s 7h", ante = 10.0, blind = 10.0, play = 40.0)
        assertEquals(HoldemOutcome.WIN, s.outcome)
        assertTrue(s.dealerQualified)
        assertEquals(20.0, s.anteReturn, 0.001)
    }

    // ---- blind ----

    @Test
    fun `blind pushes on a win with less than a straight`() {
        val s = settle("As Ad", "Kh Qc", "Ah 9d 2c 4s 7h", blind = 10.0)
        assertEquals(HoldemOutcome.WIN, s.outcome)
        assertNull(s.blindWin)
        assertEquals(10.0, s.blindReturn, 0.001)
    }

    @Test
    fun `blind pays the paytable on a straight or better`() {
        val s = settle("5h 6h", "Kd Qc", "7h 8h 9h 2c 3d", blind = 10.0)
        assertEquals(HoldemOutcome.WIN, s.outcome)
        assertEquals(BlindPay.STRAIGHT_FLUSH, s.blindWin)
        assertEquals(510.0, s.blindReturn, 0.001)  // 50:1 plus the stake back
    }

    @Test
    fun `the flush blind pays three to one`() {
        val s = settle("2s 5s", "Kd Qc", "8s Js Ks 3h 4d", blind = 10.0)
        assertEquals(BlindPay.FLUSH, s.blindWin)
        assertEquals(40.0, s.blindReturn, 0.001)
    }

    @Test
    fun `a losing straight pays the blind nothing`() {
        // Player has a straight; dealer has a flush, so everything but trips dies.
        val s = settle("5h 6d", "2s 9s", "7c 8s 9h Ks 4s", blind = 10.0)
        assertEquals(HoldemOutcome.LOSE, s.outcome)
        assertNull(s.blindWin)
        assertEquals(0.0, s.blindReturn, 0.001)
    }

    // ---- push ----

    @Test
    fun `a tie pushes ante blind and play`() {
        val s = settle("2h 3d", "4s 5c", "9s Ts Jh Qd Kc", ante = 10.0, blind = 10.0, play = 20.0)
        assertEquals(HoldemOutcome.PUSH, s.outcome)
        assertEquals(10.0, s.anteReturn, 0.001)
        assertEquals(10.0, s.blindReturn, 0.001)
        assertEquals(20.0, s.playReturn, 0.001)
    }

    // ---- folding ----

    @Test
    fun `folding loses ante and blind`() {
        val s = settle("2h 7d", "As Ah", "Kd Qc 9s 4h 3c", play = 0.0, folded = true)
        assertEquals(HoldemOutcome.FOLD, s.outcome)
        assertEquals(0.0, s.anteReturn, 0.001)
        assertEquals(0.0, s.blindReturn, 0.001)
        assertEquals(0.0, s.playReturn, 0.001)
    }

    @Test
    fun `trips still pays after a fold`() {
        // Player folds but the board completes their flush.
        val s = settle("2h 7h", "As Ad", "Kh Qh 9h 4c 3s", play = 0.0, trips = 5.0, folded = true)
        assertEquals(HoldemOutcome.FOLD, s.outcome)
        assertEquals(TripsPay.FLUSH, s.tripsWin)
        assertEquals(55.0, s.tripsReturn, 0.001)  // 10:1 plus the stake
    }

    // ---- trips ----

    @Test
    fun `trips pays on the player's own hand even in a loss`() {
        // Trip nines for the player, but the board's king gives the dealer trip kings.
        val s = settle("9s 9h", "Ks Kh", "9d Kc 4h 2s 7c", trips = 10.0)
        assertEquals(HoldemOutcome.LOSE, s.outcome)
        assertEquals(TripsPay.THREE_KIND, s.tripsWin)
        assertEquals(50.0, s.tripsReturn, 0.001)  // 4:1 plus the stake
    }

    @Test
    fun `trips pays nothing below three of a kind`() {
        val s = settle("As Kd", "2h 3c", "9s 7d 4h Jc 6s", trips = 10.0)
        assertNull(s.tripsWin)
        assertEquals(0.0, s.tripsReturn, 0.001)
    }

    @Test
    fun `trips is skipped when it was not staked`() {
        val s = settle("9s 9h", "As Ad", "9d Kc 4h 2s 7c", trips = 0.0)
        assertEquals(0.0, s.tripsReturn, 0.001)
    }

    // ---- totals ----

    @Test
    fun `a maximum raise on a monster returns every bet`() {
        val s = settle(
            "As Ks", "9d 9c", "Qs Js Ts 2h 3d",
            ante = 10.0, blind = 10.0, play = 40.0, trips = 10.0,
        )
        assertEquals(HoldemOutcome.WIN, s.outcome)
        assertEquals(BlindPay.ROYAL_FLUSH, s.blindWin)
        assertEquals(TripsPay.ROYAL_FLUSH, s.tripsWin)
        // ante 20 + blind 5010 + play 80 + trips 2010
        assertEquals(7120.0, s.totalReturn, 0.001)
    }
}
