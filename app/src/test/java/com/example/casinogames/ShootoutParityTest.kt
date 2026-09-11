package com.example.casinogames

import com.example.casinogames.games.core.Card
import com.example.casinogames.games.core.Rank
import com.example.casinogames.games.core.Suit
import com.example.casinogames.games.shootout.ShootoutRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs web/shootout-parity.txt against the Compose engine. The same file is
 * run against the JavaScript engine by web/so-parity.html, so a rule that
 * changes in one build and not the other fails here.
 */
class ShootoutParityTest {

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

    private fun token(card: Card): String {
        val r = when (card.rank) {
            Rank.ACE -> "A"; Rank.KING -> "K"; Rank.QUEEN -> "Q"; Rank.JACK -> "J"; Rank.TEN -> "T"
            else -> card.rank.label
        }
        val s = when (card.suit) {
            Suit.SPADES -> "s"; Suit.HEARTS -> "h"; Suit.DIAMONDS -> "d"; Suit.CLUBS -> "c"
        }
        return r + s
    }

    private fun fixture(): List<String> {
        val paths = listOf("../web/shootout-parity.txt", "web/shootout-parity.txt")
        val file = paths.map(::File).firstOrNull { it.exists() }
            ?: error("shootout-parity.txt not found; looked in ${paths.joinToString()}")
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
            require(cols.size == 8) { "expected 8 columns, got ${cols.size} in: $line" }
            val stakes = cols[3].split(" ").filter { it.isNotEmpty() }
            val expected = cols[4].split(" ").filter { it.isNotEmpty() }

            val player = cards(cols[0])
            val dealerFour = cards(cols[1])
            val board = cards(cols[2])
            val kept = ShootoutRules.houseWay(dealerFour)
            assertEquals("dealer keeps · $line", cols[7], kept.joinToString(" ") { token(it) })

            val s = ShootoutRules.settle(
                playerHole = player,
                dealerHole = kept,
                board = board,
                poker = stakes[0].toDouble(),
                bonus = stakes[1].toDouble(),
                badBeat = stakes[2].toDouble(),
            )
            assertEquals("outcome · $line", expected[0], s.outcome.name.lowercase())
            assertEquals("poker · $line", expected[1].toDouble(), s.pokerReturn, 0.001)
            assertEquals("bonus · $line", expected[2].toDouble(), s.bonusReturn, 0.001)
            assertEquals("bad beat · $line", expected[3].toDouble(), s.badBeatReturn, 0.001)
            assertEquals("player hand · $line", cols[5], s.playerHand.category.label)
            assertEquals("dealer hand · $line", cols[6], s.dealerHand.category.label)
        }
    }
}
