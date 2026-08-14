package com.example.casinogames

import com.example.casinogames.games.baccarat.BaccaratEngine
import com.example.casinogames.games.baccarat.BaccaratHand
import com.example.casinogames.games.baccarat.BetType
import com.example.casinogames.games.baccarat.Outcome
import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaccaratEngineTest {

    private fun card(rank: Rank) = Card(rank, Suit.SPADES)

    private fun scriptedDraw(vararg ranks: Rank): () -> Card {
        // playHand draws both player cards, then both banker cards, then thirds.
        val queue = ArrayDeque(ranks.map { card(it) })
        return { queue.removeFirst() }
    }

    @Test
    fun `hand total is modulo ten`() {
        assertEquals(5, BaccaratEngine.handTotal(listOf(card(Rank.SEVEN), card(Rank.EIGHT))))
        assertEquals(0, BaccaratEngine.handTotal(listOf(card(Rank.KING), card(Rank.TEN))))
    }

    @Test
    fun `natural eight or nine stops the deal at four cards`() {
        // Player K+9 = natural 9; banker 2+3 = 5 would otherwise draw.
        val hand = BaccaratEngine.playHand(
            scriptedDraw(Rank.KING, Rank.NINE, Rank.TWO, Rank.THREE)
        )
        assertEquals(2, hand.player.size)
        assertEquals(2, hand.banker.size)
        assertEquals(true, hand.natural)
        assertEquals(Outcome.PLAYER, hand.outcome)
    }

    @Test
    fun `player draws on five or less and banker follows tableau`() {
        // Player 2+3=5 draws a 4 (total 9). Banker 4+2=6 stands against a 4.
        val hand = BaccaratEngine.playHand(
            scriptedDraw(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.TWO, Rank.FOUR)
        )
        assertEquals(3, hand.player.size)
        assertEquals(2, hand.banker.size)
        assertEquals(9, hand.playerTotal)
        assertEquals(Outcome.PLAYER, hand.outcome)
    }

    @Test
    fun `banker draws when player stands and banker is five or less`() {
        // Player K+6 = 6 stands. Banker K+5 = 5 must draw; gets a 3 -> 8.
        val hand = BaccaratEngine.playHand(
            scriptedDraw(Rank.KING, Rank.SIX, Rank.KING, Rank.FIVE, Rank.THREE)
        )
        assertEquals(2, hand.player.size)
        assertEquals(3, hand.banker.size)
        assertEquals(Outcome.BANKER, hand.outcome)
    }

    @Test
    fun `banker three stands only against a player third card eight`() {
        // Player 2+2=4 draws an 8 (total 2). Banker K+3=3 stands against an 8.
        val hand = BaccaratEngine.playHand(
            scriptedDraw(Rank.TWO, Rank.TWO, Rank.KING, Rank.THREE, Rank.EIGHT)
        )
        assertEquals(2, hand.banker.size)
        assertEquals(Outcome.BANKER, hand.outcome)
    }

    private fun hand(
        player: List<Rank>,
        banker: List<Rank>,
        outcome: Outcome,
        natural: Boolean = false,
    ) = BaccaratHand(player.map(::card), banker.map(::card), outcome, natural)

    @Test
    fun `player win pays even money`() {
        val h = hand(listOf(Rank.FOUR, Rank.FIVE), listOf(Rank.TWO, Rank.TWO), Outcome.PLAYER, natural = true)
        assertEquals(200.0, BaccaratEngine.settle(h, mapOf(BetType.PLAYER to 100)), 0.0)
    }

    @Test
    fun `banker win pays with five percent commission`() {
        val h = hand(listOf(Rank.TWO, Rank.TWO), listOf(Rank.FOUR, Rank.FIVE), Outcome.BANKER, natural = true)
        assertEquals(195.0, BaccaratEngine.settle(h, mapOf(BetType.BANKER to 100)), 0.0)
    }

    @Test
    fun `tie pays eight to one and pushes main bets`() {
        val h = hand(listOf(Rank.FOUR, Rank.TWO), listOf(Rank.KING, Rank.SIX), Outcome.TIE)
        val bets = mapOf(BetType.TIE to 10, BetType.PLAYER to 50, BetType.BANKER to 25)
        assertEquals(90.0 + 50.0 + 25.0, BaccaratEngine.settle(h, bets), 0.0)
    }

    @Test
    fun `pairs pay eleven to one on the first two cards`() {
        val h = hand(listOf(Rank.FOUR, Rank.FOUR), listOf(Rank.KING, Rank.SIX), Outcome.BANKER)
        assertEquals(120.0, BaccaratEngine.settle(h, mapOf(BetType.PLAYER_PAIR to 10)), 0.0)
        assertEquals(0.0, BaccaratEngine.settle(h, mapOf(BetType.BANKER_PAIR to 10)), 0.0)
    }

    @Test
    fun `fortune seven pays forty to one on a banker three card seven`() {
        val h = hand(
            listOf(Rank.KING, Rank.SIX),
            listOf(Rank.TWO, Rank.TWO, Rank.THREE),
            Outcome.BANKER,
        )
        assertEquals(410.0, BaccaratEngine.settle(h, mapOf(BetType.FORTUNE_7 to 10)), 0.0)
    }

    @Test
    fun `heavenly nine pays double rate when both hands have three card nines`() {
        val h = hand(
            listOf(Rank.TWO, Rank.THREE, Rank.FOUR),
            listOf(Rank.FOUR, Rank.FOUR, Rank.ACE),
            Outcome.TIE,
        )
        assertEquals(760.0, BaccaratEngine.settle(h, mapOf(BetType.HEAVENLY_9 to 10)), 0.0)
    }

    @Test
    fun `breakdown reports each winning spot separately and omits losers`() {
        // Banker three-card 7 vs player pair: Fortune 7, Blazing 7s, Banker and
        // Player Pair all pay; the Player and Tie bets lose and must be absent.
        val h = hand(
            listOf(Rank.SIX, Rank.SIX),
            listOf(Rank.TWO, Rank.TWO, Rank.THREE),
            Outcome.BANKER,
        )
        val bets = mapOf(
            BetType.PLAYER to 50, BetType.BANKER to 20, BetType.TIE to 5,
            BetType.PLAYER_PAIR to 10, BetType.FORTUNE_7 to 10, BetType.BLAZING_7S to 10,
        )
        val breakdown = BaccaratEngine.settleBreakdown(h, bets)
        assertEquals(
            mapOf(
                BetType.BANKER to 39.0,
                BetType.PLAYER_PAIR to 120.0,
                BetType.FORTUNE_7 to 410.0,
                BetType.BLAZING_7S to 260.0,
            ),
            breakdown,
        )
        assertEquals(breakdown.values.sum(), BaccaratEngine.settle(h, bets), 0.0)
    }

    @Test
    fun `cover all pays six to one when any fortune event hits`() {
        // Banker three-card 7: fortune event occurs.
        val hit = hand(
            listOf(Rank.KING, Rank.SIX),
            listOf(Rank.TWO, Rank.TWO, Rank.THREE),
            Outcome.BANKER,
        )
        assertEquals(70.0, BaccaratEngine.settle(hit, mapOf(BetType.COVER_ALL to 10)), 0.0)
        assertTrue(BetType.COVER_ALL in BaccaratEngine.winningSpots(hit))

        // Plain natural: no fortune event, cover all loses.
        val miss = hand(
            listOf(Rank.KING, Rank.SIX),
            listOf(Rank.KING, Rank.NINE),
            Outcome.BANKER,
            natural = true,
        )
        assertEquals(0.0, BaccaratEngine.settle(miss, mapOf(BetType.COVER_ALL to 10)), 0.0)
    }

    @Test
    fun `breakdown reports tie pushes as exactly the stake`() {
        val h = hand(listOf(Rank.FOUR, Rank.TWO), listOf(Rank.KING, Rank.SIX), Outcome.TIE)
        val breakdown = BaccaratEngine.settleBreakdown(
            h, mapOf(BetType.PLAYER to 50, BetType.BANKER to 25)
        )
        assertEquals(mapOf(BetType.PLAYER to 50.0, BetType.BANKER to 25.0), breakdown)
    }

    @Test
    fun `blazing sevens pays single rate when one hand has a three card seven`() {
        val h = hand(
            listOf(Rank.TWO, Rank.TWO, Rank.THREE),
            listOf(Rank.KING, Rank.EIGHT),
            Outcome.BANKER,
            natural = true,
        )
        assertEquals(260.0, BaccaratEngine.settle(h, mapOf(BetType.BLAZING_7S to 10)), 0.0)
    }
}
