package com.example.casinogames

import com.example.casinogames.games.blackjack.DoubleDownRules
import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs web/doubledown-parity.txt against the Compose engine. The same file is
 * run against the JavaScript engine by web/dd-parity.html, so a rule that
 * changes in one build and not the other fails here.
 */
class DoubleDownParityTest {

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
        val paths = listOf("../web/doubledown-parity.txt", "web/doubledown-parity.txt")
        val file = paths.map(::File).firstOrNull { it.exists() }
            ?: error("doubledown-parity.txt not found; looked in ${paths.joinToString()}")
        return file.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
    }

    @Test
    fun `every shared case settles the way the fixture says`() {
        val lines = fixture()
        assertTrue("the fixture should carry some cases", lines.size >= 15)

        lines.forEach { line ->
            val cols = line.split("|").map { it.trim() }
            require(cols.size == 5) { "expected 5 columns, got ${cols.size} in: $line" }
            val stakes = cols[2].split(" ").filter { it.isNotEmpty() }
            val expected = cols[3].split(" ").filter { it.isNotEmpty() }
            val dealer = cards(cols[1])
            val push22Stake = stakes[3].toInt()

            val handReturn = DoubleDownRules.settleHand(
                player = cards(cols[0]),
                dealer = dealer,
                stake = stakes[0].toInt(),
                betUnit = stakes[1].toInt(),
                doubled = stakes[2] == "dbl",
            )
            assertEquals("hand · $line", expected[0].toDouble(), handReturn, 0.001)
            assertEquals(
                "push 22 · $line",
                expected[1].toDouble(),
                DoubleDownRules.settlePush22(dealer, push22Stake),
                0.001,
            )
            assertEquals(
                "side bet · $line",
                cols[4].takeIf { it != "-" },
                DoubleDownRules.push22(dealer)?.label,
            )
        }
    }
}
