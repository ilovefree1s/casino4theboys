package com.example.casinogames

import com.example.casinogames.games.roulette.RouletteEngine
import com.example.casinogames.games.roulette.RouletteEngine.DOUBLE_ZERO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RouletteEngineTest {

    @Test
    fun `the wheel has 38 pockets and lands on every one`() {
        val seen = mutableSetOf<Int>()
        val rng = Random(7)
        repeat(20_000) { seen.add(RouletteEngine.spin(rng)) }
        assertEquals(38, seen.size)
        assertTrue(DOUBLE_ZERO in seen)
        assertTrue(0 in seen)
    }

    @Test
    fun `straight pays 35 to 1 and only on its own pocket`() {
        val bet = RouletteEngine.straight(17)
        assertEquals(360.0, RouletteEngine.settle(bet, 10.0, 17), 0.0)
        assertEquals(0.0, RouletteEngine.settle(bet, 10.0, 18), 0.0)
        // The zeroes are pockets like any other.
        assertEquals(360.0, RouletteEngine.settle(RouletteEngine.straight(0), 10.0, 0), 0.0)
        assertEquals(
            360.0,
            RouletteEngine.settle(RouletteEngine.straight(DOUBLE_ZERO), 10.0, DOUBLE_ZERO),
            0.0,
        )
    }

    @Test
    fun `the combination ladder pays 17, 11, 8, 6 and 5`() {
        assertEquals(180.0, RouletteEngine.settle(RouletteEngine.split(8, 11), 10.0, 11), 0.0)
        assertEquals(
            120.0,
            RouletteEngine.settle(RouletteEngine.street(setOf(4, 5, 6)), 10.0, 5),
            0.0,
        )
        assertEquals(
            90.0,
            RouletteEngine.settle(RouletteEngine.corner(setOf(17, 18, 20, 21)), 10.0, 20),
            0.0,
        )
        assertEquals(70.0, RouletteEngine.settle(RouletteEngine.TOP_LINE, 10.0, DOUBLE_ZERO), 0.0)
        assertEquals(
            60.0,
            RouletteEngine.settle(RouletteEngine.sixLine(setOf(13, 14, 15, 16, 17, 18)), 10.0, 16),
            0.0,
        )
    }

    @Test
    fun `outside bets cover their halves and thirds`() {
        assertEquals(18, RouletteEngine.RED.pockets.size)
        assertEquals(18, RouletteEngine.BLACK.pockets.size)
        assertEquals(18, RouletteEngine.ODD.pockets.size)
        assertEquals(18, RouletteEngine.EVEN.pockets.size)
        assertEquals(18, RouletteEngine.LOW.pockets.size)
        assertEquals(18, RouletteEngine.HIGH.pockets.size)
        (0..2).forEach { assertEquals(12, RouletteEngine.dozen(it).pockets.size) }
        (0..2).forEach { assertEquals(12, RouletteEngine.column(it).pockets.size) }
        // Column 2 is 2, 5, 8 … 35.
        assertEquals((0 until 12).map { it * 3 + 2 }.toSet(), RouletteEngine.column(1).pockets)
        assertEquals(20.0, RouletteEngine.settle(RouletteEngine.RED, 10.0, 32), 0.0)
        assertEquals(30.0, RouletteEngine.settle(RouletteEngine.dozen(2), 10.0, 25), 0.0)
    }

    @Test
    fun `the zeroes beat every outside bet`() {
        for (z in listOf(0, DOUBLE_ZERO)) {
            for (bet in listOf(
                RouletteEngine.RED, RouletteEngine.BLACK, RouletteEngine.ODD,
                RouletteEngine.EVEN, RouletteEngine.LOW, RouletteEngine.HIGH,
                RouletteEngine.dozen(0), RouletteEngine.column(2),
            )) {
                assertEquals("$z vs ${bet.name}", 0.0, RouletteEngine.settle(bet, 10.0, z), 0.0)
            }
        }
    }

    @Test
    fun `red and black split the numbers the way the wheel paints them`() {
        assertTrue(RouletteEngine.isRed(1))
        assertTrue(RouletteEngine.isBlack(2))
        assertTrue(RouletteEngine.isRed(36))
        assertTrue(RouletteEngine.isBlack(35))
        // The zeroes are neither.
        assertTrue(!RouletteEngine.isRed(0) && !RouletteEngine.isBlack(0))
        assertTrue(!RouletteEngine.isRed(DOUBLE_ZERO) && !RouletteEngine.isBlack(DOUBLE_ZERO))
    }
}
