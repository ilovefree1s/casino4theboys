package com.example.casinogames

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import com.example.casinogames.games.miniuth.MiniUthRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs web/miniuth-parity.txt against the Compose engine. The same file is
 * run against the JavaScript engine by web/mu-parity.html, so a rule that
 * changes in one build and not the other fails here.
 */
class MiniUthParityTest {

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

    private fun fixture(): List<String> {
        val paths = listOf("../web/miniuth-parity.txt", "web/miniuth-parity.txt")
        val file = paths.map(::File).firstOrNull { it.exists() }
            ?: error("miniuth-parity.txt not found; looked in ${paths.joinToString()}")
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
            val stakes = cols[3].split(" ").filter { it.isNotEmpty() }
            val expected = cols[4].split(" ").filter { it.isNotEmpty() }

            val s = MiniUthRules.settle(
                playerHole = cards(cols[0]).single(),
                dealerHole = cards(cols[1]).single(),
                community = cards(cols[2]),
                ante = stakes[0].toDouble(),
                blind = stakes[1].toDouble(),
                play = stakes[2].toDouble(),
                flushPlus = stakes[3].toDouble(),
                bonus = stakes[4].toDouble(),
                folded = stakes[5] == "fold",
            )
            assertEquals("outcome · $line", expected[0], s.outcome.name.lowercase())
            assertEquals("ante · $line", expected[1].toDouble(), s.anteReturn, 0.001)
            assertEquals("blind · $line", expected[2].toDouble(), s.blindReturn, 0.001)
            assertEquals("play · $line", expected[3].toDouble(), s.playReturn, 0.001)
            assertEquals("flush plus · $line", expected[4].toDouble(), s.flushPlusReturn, 0.001)
            assertEquals("3 card bonus · $line", expected[5].toDouble(), s.bonusReturn, 0.001)
            assertEquals("player hand · $line", cols[5], s.playerHand.category.label)
            assertEquals("dealer hand · $line", cols[6], s.dealerHand.category.label)
        }
    }
}
