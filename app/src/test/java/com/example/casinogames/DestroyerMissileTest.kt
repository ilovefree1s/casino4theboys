package com.example.casinogames

import com.example.casinogames.games.destroyer.DestroyerRules
import com.example.casinogames.games.destroyer.DestroyerRules.Ship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestroyerMissileTest {

    private val fleet = listOf(
        Ship(listOf(0, 1, 2, 3)),
        Ship(listOf(6, 7, 8)),
        Ship(listOf(12, 13, 14)),
        Ship(listOf(18, 19)),
    )

    @Test
    fun `extra shot value is exact with one cell left standing`() {
        // Everything sunk but one cell of the 2-ship: the board pays 311
        // (40 + 10 + 10 + 251); a full sweep pays 564 (plus 3, and the
        // fleet bonus steps 251 to 501). One fresh cell in 36, so
        // V = (564 + 35*311)/36 and the shot is worth 253/36 units.
        val hits = setOf(0, 1, 2, 3, 6, 7, 8, 12, 13, 14, 18)
        assertEquals(253.0 / 36.0, DestroyerRules.extraShotEv(fleet, hits), 1e-9)
    }

    @Test
    fun `missile is priced at twenty five twenty seconds of its worth`() {
        val hits = setOf(0, 1, 2, 3, 6, 7, 8, 12, 13, 14, 18)
        // 253/36 units x 25 stake x 25/22 = 199.65 — 200 at the window.
        assertEquals(200, DestroyerRules.missilePrice(fleet, hits, 25))
    }

    @Test
    fun `a fresh board still prices a shot above nothing`() {
        // 4096 states walked outright; the answer is exact, and positive.
        assertTrue(DestroyerRules.extraShotEv(fleet, emptySet()) > 0.0)
    }
}
