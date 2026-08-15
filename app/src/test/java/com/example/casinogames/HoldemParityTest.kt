package com.example.casinogames

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import com.example.casinogames.games.holdem.HoldemOutcome
import com.example.casinogames.games.holdem.UltimateHoldemRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs web/holdem-parity.txt against the Compose engine. The same file is run
 * against the JavaScript engine by web/parity.html, so a rule that changes in
 * one build and not the other fails here.
 */
class HoldemParityTest {

    private fun cards(spec: String): List<Card> = spec.trim().split(" ").map { token ->
        val rank = when (token[0]) {
            'A' -> Rank.ACE; 'K' -> Rank.KING; 'Q' -> Rank.QUEEN; 'J' -> Rank.JACK
            'T' -> Rank.TEN; '9' -> Rank.NINE; '8' -> Rank.EIGHT; '7' -> Rank.SEVEN
            '6' -> Rank.SIX; '5' -> Rank.FIVE; '4' -> Rank.FOUR; '3' -> Rank.THREE
            '2' -> Rank.TWO
            else -> error("bad rank in $token")
        }
        val suit = when (token[1]) {
            's' -> Suit.SPADES; 'h' -> Suit.HEARTS; 'd' -> Suit.DIAMONDS; 'c' -> Suit.CLUBS
            else -> error("bad suit in $token")
        }
        Card(rank, suit)
    }

    /** Gradle runs unit tests from the module directory; allow the repo root too. */
    private fun fixture(): List<String> {
        val paths = listOf("../web/holdem-parity.txt", "web/holdem-parity.txt")
        val file = paths.map(::File).firstOrNull { it.exists() }
            ?: error("holdem-parity.txt not found; looked in ${paths.joinToString()}")
        return file.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
    }

    @Test
    fun `every shared case settles the way the fixture says`() {
        val lines = fixture()
        assertTrue("the fixture should carry some cases", lines.size >= 10)

        lines.forEach { line ->
            val cols = line.split("|").map { it.trim() }
            require(cols.size == 7) { "expected 7 columns, got ${cols.size} in: $line" }
            val (player, dealer, board) = cols
            val stakes = cols[3].split(" ").filter { it.isNotEmpty() }
            val expected = cols[4].split(" ").filter { it.isNotEmpty() }

            val s = UltimateHoldemRules.settle(
                playerHole = cards(player),
                dealerHole = cards(dealer),
                board = cards(board),
                ante = stakes[0].toDouble(),
                blind = stakes[1].toDouble(),
                play = stakes[2].toDouble(),
                trips = stakes[3].toDouble(),
                folded = stakes[4] == "fold",
            )

            val outcome = when (expected[0]) {
                "win" -> HoldemOutcome.WIN
                "lose" -> HoldemOutcome.LOSE
                "push" -> HoldemOutcome.PUSH
                "fold" -> HoldemOutcome.FOLD
                else -> error("bad outcome ${expected[0]}")
            }
            assertEquals("outcome · $line", outcome, s.outcome)
            assertEquals("dealer opened · $line", expected[1] == "q", s.dealerQualified)
            assertEquals("ante · $line", expected[2].toDouble(), s.anteReturn, 0.001)
            assertEquals("blind · $line", expected[3].toDouble(), s.blindReturn, 0.001)
            assertEquals("play · $line", expected[4].toDouble(), s.playReturn, 0.001)
            assertEquals("trips · $line", expected[5].toDouble(), s.tripsReturn, 0.001)
            assertEquals("player hand · $line", cols[5], s.playerHand.category.label)
            assertEquals("dealer hand · $line", cols[6], s.dealerHand.category.label)
        }
    }
}
