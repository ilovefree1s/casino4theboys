package com.example.casinogames

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import com.example.casinogames.games.djwild.DjWildEval
import com.example.casinogames.games.djwild.DjWildRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs web/djwild-parity.txt against the Compose engine. The same file is run
 * against the JavaScript engine by web/dj-parity.html, so a rule that changes
 * in one build and not the other fails here.
 */
class DjWildParityTest {

    private fun cards(spec: String): List<Card> = spec.trim().split(" ").map { token ->
        val rank = when (token[0]) {
            'A' -> Rank.ACE; 'K' -> Rank.KING; 'Q' -> Rank.QUEEN; 'J' -> Rank.JACK
            'T' -> Rank.TEN; '9' -> Rank.NINE; '8' -> Rank.EIGHT; '7' -> Rank.SEVEN
            '6' -> Rank.SIX; '5' -> Rank.FIVE; '4' -> Rank.FOUR; '3' -> Rank.THREE
            '2' -> Rank.TWO; 'W' -> Rank.JOKER
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
        val paths = listOf("../web/djwild-parity.txt", "web/djwild-parity.txt")
        val file = paths.map(::File).firstOrNull { it.exists() }
            ?: error("djwild-parity.txt not found; looked in ${paths.joinToString()}")
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
            require(cols.size == 6) { "expected 6 columns, got ${cols.size} in: $line" }
            val stakes = cols[2].split(" ").filter { it.isNotEmpty() }
            val expected = cols[3].split(" ").filter { it.isNotEmpty() }

            val player = cards(cols[0])
            val dealer = cards(cols[1])
            val s = DjWildRules.settle(
                player = player,
                dealer = dealer,
                ante = stakes[0].toDouble(),
                blind = stakes[1].toDouble(),
                play = stakes[2].toDouble(),
                trips = stakes[3].toDouble(),
                badBeat = stakes[4].toDouble(),
                folded = stakes[5] == "fold",
            )

            assertEquals("outcome · $line", expected[0], s.outcome.name.lowercase())
            assertEquals("ante · $line", expected[1].toDouble(), s.anteReturn, 0.001)
            assertEquals("blind · $line", expected[2].toDouble(), s.blindReturn, 0.001)
            assertEquals("play · $line", expected[3].toDouble(), s.playReturn, 0.001)
            assertEquals("trips · $line", expected[4].toDouble(), s.tripsReturn, 0.001)
            assertEquals("bad beat · $line", expected[5].toDouble(), s.badBeatReturn, 0.001)
            assertEquals("player hand · $line", cols[4], DjWildEval.score(player).category.label)
            assertEquals("dealer hand · $line", cols[5], DjWildEval.score(dealer).category.label)
        }
    }
}
