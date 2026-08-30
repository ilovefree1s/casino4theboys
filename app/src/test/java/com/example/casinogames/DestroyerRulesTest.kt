package com.example.casinogames

import com.example.casinogames.games.destroyer.DestroyerRules
import com.example.casinogames.games.destroyer.DestroyerRules.Ship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Destroyer: the fleet, the milestone pays, and the maths of the whole game. */
class DestroyerRulesTest {

    // ---- the fleet ----

    @Test
    fun `every fleet is legal`() {
        val random = Random(7)
        repeat(2_000) {
            val fleet = DestroyerRules.placeFleet(random)
            assertEquals(listOf(4, 3, 3, 2), fleet.map { it.size })
            val all = fleet.flatMap { it.cells }
            assertEquals("ships must not overlap", all.size, all.toSet().size)
            assertTrue(all.all { it in 0 until DestroyerRules.CELLS })
            // Every ship is a straight line: one row or one column, contiguous.
            fleet.forEach { ship ->
                val rows = ship.cells.map { it / DestroyerRules.GRID }.distinct()
                val cols = ship.cells.map { it % DestroyerRules.GRID }.distinct()
                assertTrue(rows.size == 1 || cols.size == 1)
                val line = if (rows.size == 1) ship.cells.map { it % DestroyerRules.GRID }
                else ship.cells.map { it / DestroyerRules.GRID }
                assertEquals((line.min()..line.max()).toList(), line.sorted())
            }
        }
    }

    // ---- the ladder ----

    @Test
    fun `each ship pays its highest rung only`() {
        assertEquals(0, DestroyerRules.shipPay(2, 0))
        assertEquals(0, DestroyerRules.shipPay(2, 1))
        assertEquals(3, DestroyerRules.shipPay(2, 2))
        assertEquals(3, DestroyerRules.shipPay(3, 2))
        assertEquals(10, DestroyerRules.shipPay(3, 3))
        assertEquals(3, DestroyerRules.shipPay(4, 2))
        assertEquals(4, DestroyerRules.shipPay(4, 3))
        assertEquals(40, DestroyerRules.shipPay(4, 4))
    }

    @Test
    fun `sinking the whole fleet pays the tower`() {
        val fleet = listOf(
            Ship(listOf(0, 1, 2, 3)),
            Ship(listOf(6, 7, 8)),
            Ship(listOf(12, 13, 14)),
            Ship(listOf(18, 19)),
        )
        val everything = fleet.flatMap { it.cells }.toSet()
        // 40 + 10 + 10 + 3 for the ships, 501 for the fleet.
        assertEquals(564, DestroyerRules.settle(fleet, everything))
        assertEquals(4, DestroyerRules.sunkCount(fleet, everything))
    }

    @Test
    fun `partial hits pay their rungs with no bonus`() {
        val fleet = listOf(
            Ship(listOf(0, 1, 2, 3)),
            Ship(listOf(6, 7, 8)),
            Ship(listOf(12, 13, 14)),
            Ship(listOf(18, 19)),
        )
        // Three into the big ship, two into a three, one stray on the 2-ship.
        val hits = setOf(0, 1, 2, 6, 7, 18)
        assertEquals(4 + 3, DestroyerRules.settle(fleet, hits))
        // One ship sunk earns its rung but no fleet bonus.
        val oneSunk = setOf(18, 19)
        assertEquals(3, DestroyerRules.settle(fleet, oneSunk))
        assertEquals(0, DestroyerRules.settle(fleet, emptySet()))
    }

    // ---- the whole game ----

    /**
     * Plays the game by its table rules: four lives, a fresh hit is a free
     * roll, twice-hit water is a miss. Seeded, so the answer never moves.
     */
    private fun playHand(random: Random): Int {
        val fleet = DestroyerRules.placeFleet(random)
        val hits = mutableSetOf<Int>()
        var lives = DestroyerRules.LIVES
        while (lives > 0 && DestroyerRules.sunkCount(fleet, hits) < fleet.size) {
            val cell = random.nextInt(DestroyerRules.CELLS)
            val onShip = fleet.any { cell in it.cells }
            if (onShip && cell !in hits) hits.add(cell) else lives--
        }
        return DestroyerRules.settle(fleet, hits)
    }

    @Test
    fun `the game pays about 94 percent, like the floor machine`() {
        val random = Random(20260830)
        val hands = 400_000
        var returned = 0L
        repeat(hands) { returned += playHand(random) }
        val rtp = returned.toDouble() / hands
        // The floor game's published edge is 5.6%; the seed pins this run.
        assertTrue("RTP was $rtp", rtp in 0.93..0.96)
    }
}
