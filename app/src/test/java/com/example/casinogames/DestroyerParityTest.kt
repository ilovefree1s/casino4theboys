package com.example.casinogames

import com.example.casinogames.games.destroyer.DestroyerRules
import com.example.casinogames.games.destroyer.DestroyerRules.Ship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs web/destroyer-parity.txt against the Compose engine. The same file is
 * run against the JavaScript engine by web/dz-parity.html, so a rule that
 * changes in one build and not the other fails here.
 */
class DestroyerParityTest {

    /** Gradle runs unit tests from the module directory; allow the repo root too. */
    private fun fixture(): List<String> {
        val paths = listOf("../web/destroyer-parity.txt", "web/destroyer-parity.txt")
        val file = paths.map(::File).firstOrNull { it.exists() }
            ?: error("destroyer-parity.txt not found; looked in ${paths.joinToString()}")
        return file.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
    }

    /** The table's roll loop, replayed over a fixed sequence. */
    private fun replay(fleet: List<Ship>, rolls: List<Int>): Pair<Int, Int> {
        val hits = mutableSetOf<Int>()
        var lives = DestroyerRules.LIVES
        for (cell in rolls) {
            if (lives <= 0) break
            if (DestroyerRules.sunkCount(fleet, hits) == fleet.size) break
            val onShip = fleet.any { cell in it.cells }
            if (onShip && cell !in hits) hits.add(cell) else lives--
        }
        return DestroyerRules.settle(fleet, hits) to lives
    }

    @Test
    fun `every shared case settles the way the fixture says`() {
        val lines = fixture()
        assertTrue("the fixture should carry some cases", lines.size >= 10)

        lines.forEach { line ->
            val cols = line.split("|").map { it.trim() }
            require(cols.size == 3) { "expected 3 columns, got ${cols.size} in: $line" }
            val fleet = cols[0].split(";").map { shipSpec ->
                Ship(shipSpec.trim().split(" ").map { it.toInt() })
            }
            val rolls = cols[1].split(" ").filter { it.isNotEmpty() }.map { it.toInt() }
            val expected = cols[2].split(" ").filter { it.isNotEmpty() }.map { it.toInt() }

            val (units, lives) = replay(fleet, rolls)
            assertEquals("units · $line", expected[0], units)
            assertEquals("lives · $line", expected[1], lives)
        }
    }
}
