package com.example.casinogames

import com.example.casinogames.games.craps.ComeBet
import com.example.casinogames.games.craps.CrapsBets
import com.example.casinogames.games.craps.CrapsRules
import com.example.casinogames.games.craps.Prop
import com.example.casinogames.games.craps.Roll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules that make crapless craps its own game, and the ladders under them. */
class CrapsRulesTest {

    private fun bets(
        pass: Int = 0, passOdds: Int = 0, comeFlat: Int = 0,
        comePoints: Map<Int, ComeBet> = emptyMap(),
        place: Map<Int, Int> = emptyMap(), field: Int = 0,
        hard: Map<Int, Int> = emptyMap(), props: Map<Prop, Int> = emptyMap(),
    ) = CrapsBets(pass, passOdds, comeFlat, comePoints, place, field, hard, props)

    @Test
    fun `seven wins the come out`() {
        val r = CrapsRules.resolve(bets(pass = 10), null, Roll(3, 4))
        assertEquals(20.0, r.returns, 0.001)
        assertNull(r.point)
        assertEquals(0, r.bets.pass)
    }

    @Test
    fun `craps are points here, not losers`() {
        for ((a, b) in listOf(1 to 1, 1 to 2, 6 to 6)) {
            val r = CrapsRules.resolve(bets(pass = 10), null, Roll(a, b))
            assertEquals("${a + b} should become the point", a + b, r.point)
            assertEquals("nothing comes back", 0.0, r.returns, 0.001)
            assertEquals("the line rides on", 10, r.bets.pass)
        }
    }

    @Test
    fun `eleven is a point too, not a natural`() {
        val r = CrapsRules.resolve(bets(pass = 10), null, Roll(5, 6))
        assertEquals(11, r.point)
        assertEquals(0.0, r.returns, 0.001)
    }

    @Test
    fun `the two pays six to one behind the line`() {
        val r = CrapsRules.resolve(bets(pass = 10, passOdds = 30), 2, Roll(1, 1))
        assertTrue(r.pointMade)
        // Flat pays ten, the thirty behind it pays a hundred and eighty.
        assertEquals(10 + 30 + 190.0, r.returns, 0.001)
        assertNull(r.point)
    }

    @Test
    fun `a seven sweeps the numbers and the come points`() {
        val r = CrapsRules.resolve(
            bets(
                pass = 10, passOdds = 20,
                place = mapOf(6 to 12, 8 to 12),
                comePoints = mapOf(5 to ComeBet(10, 20)),
                hard = mapOf(8 to 5),
            ),
            6, Roll(3, 4),
        )
        assertTrue(r.sevenOut)
        assertEquals(0.0, r.returns, 0.001)
        assertTrue(r.bets.isEmpty)
        assertNull(r.point)
    }

    @Test
    fun `a come bet still wins on the seven that sevens out`() {
        val r = CrapsRules.resolve(bets(pass = 10, comeFlat = 10), 6, Roll(1, 6))
        assertTrue(r.sevenOut)
        assertEquals("the waiting come bet is paid", 20.0, r.returns, 0.001)
    }

    @Test
    fun `place bets sleep through a come out`() {
        val r = CrapsRules.resolve(bets(pass = 10, place = mapOf(6 to 12)), null, Roll(3, 3))
        assertEquals(6, r.point)
        assertEquals("no place win on a come out", 0.0, r.returns, 0.001)
        assertEquals(12, r.bets.place[6])

        val out = CrapsRules.resolve(bets(pass = 10, place = mapOf(6 to 12)), null, Roll(3, 4))
        assertEquals("nor does the come out seven take them", 12, out.bets.place[6])
    }

    @Test
    fun `a place win pays and leaves the chips standing`() {
        val r = CrapsRules.resolve(bets(place = mapOf(6 to 12)), 8, Roll(4, 2))
        assertEquals("seven to six on twelve", 14.0, r.returns, 0.001)
        assertEquals(12, r.bets.place[6])
    }

    @Test
    fun `the field doubles the two and triples the twelve`() {
        assertEquals(20.0, CrapsRules.resolve(bets(field = 10), 6, Roll(1, 1)).returns, 0.001)
        assertEquals(30.0, CrapsRules.resolve(bets(field = 10), 6, Roll(6, 6)).returns, 0.001)
        assertEquals(10.0, CrapsRules.resolve(bets(field = 10), 6, Roll(1, 2)).returns, 0.001)
        val lost = CrapsRules.resolve(bets(field = 10), 6, Roll(2, 3))
        assertEquals(0.0, lost.returns, 0.001)
        assertEquals(0, lost.bets.field)
    }

    @Test
    fun `a hardway wants the pair, and dies on the easy way`() {
        val won = CrapsRules.resolve(bets(hard = mapOf(8 to 5)), 6, Roll(4, 4))
        assertEquals(45.0, won.returns, 0.001)
        assertEquals("it stays up", 5, won.bets.hard[8])

        val easy = CrapsRules.resolve(bets(hard = mapOf(8 to 5)), 6, Roll(5, 3))
        assertEquals(0.0, easy.returns, 0.001)
        assertNull(easy.bets.hard[8])
    }

    @Test
    fun `a come bet travels, then pays its own odds`() {
        val travelled = CrapsRules.resolve(bets(comeFlat = 10), 6, Roll(4, 5))
        assertEquals(ComeBet(10, 0), travelled.bets.comePoints[9])
        assertEquals(0.0, travelled.returns, 0.001)

        val paid = CrapsRules.resolve(bets(comePoints = mapOf(9 to ComeBet(10, 20))), 6, Roll(4, 5))
        // Flat pays ten, the twenty behind it pays thirty at three to two.
        assertEquals(10 + 20 + 40.0, paid.returns, 0.001)
        assertNull(paid.bets.comePoints[9])
    }

    @Test
    fun `one roll props pay and stay, or die trying`() {
        val yo = CrapsRules.resolve(bets(props = mapOf(Prop.YO to 5)), 6, Roll(5, 6))
        assertEquals(75.0, yo.returns, 0.001)
        assertEquals(5, yo.bets.props[Prop.YO])

        val missed = CrapsRules.resolve(bets(props = mapOf(Prop.YO to 5)), 6, Roll(5, 5))
        assertEquals(0.0, missed.returns, 0.001)
        assertNull(missed.bets.props[Prop.YO])
    }

    @Test
    fun `free odds carry no edge at all`() {
        // The payout is exactly the ways to make it against the ways to seven.
        for (point in CrapsRules.POINTS) {
            val (n, d) = CrapsRules.oddsPay(point)
            val fair = CrapsRules.ways(point).toDouble() * n / d
            assertEquals("odds on $point", CrapsRules.ways(7).toDouble(), fair, 0.001)
        }
    }
}
