package com.example.casinogames

import com.example.casinogames.games.craps.ComeBet
import com.example.casinogames.games.craps.CrapsBets
import com.example.casinogames.games.craps.CrapsRules
import com.example.casinogames.games.craps.Prop
import com.example.casinogames.games.craps.Roll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs web/craps-parity.txt against the Compose engine. The same file is run
 * against the JavaScript engine by web/cr-parity.html, so a rule that changes
 * in one build and not the other fails here.
 */
class CrapsParityTest {

    private val propKeys = mapOf(
        "any7" to Prop.ANY_SEVEN, "craps" to Prop.ANY_CRAPS, "aces" to Prop.ACES,
        "ace2" to Prop.ACE_DEUCE, "yo" to Prop.YO, "box" to Prop.BOXCARS,
    )

    private fun parseBets(spec: String): CrapsBets {
        if (spec == "-") return CrapsBets()
        var b = CrapsBets()
        for (token in spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val (key, value) = token.split("=", limit = 2)
            when {
                key == "pass" -> b = b.copy(pass = value.toInt())
                key == "odds" -> b = b.copy(passOdds = value.toInt())
                key == "come" -> b = b.copy(comeFlat = value.toInt())
                key == "field" -> b = b.copy(field = value.toInt())
                key.startsWith("c") && key.drop(1).toIntOrNull() != null -> {
                    val (flat, odds) = value.split(":")
                    b = b.copy(comePoints = b.comePoints + (key.drop(1).toInt() to ComeBet(flat.toInt(), odds.toInt())))
                }
                key.startsWith("p") && key.drop(1).toIntOrNull() != null ->
                    b = b.copy(place = b.place + (key.drop(1).toInt() to value.toInt()))
                key.startsWith("h") && key.drop(1).toIntOrNull() != null ->
                    b = b.copy(hard = b.hard + (key.drop(1).toInt() to value.toInt()))
                propKeys.containsKey(key) ->
                    b = b.copy(props = b.props + (propKeys.getValue(key) to value.toInt()))
                else -> error("unknown bet '$token'")
            }
        }
        return b
    }

    /** The felt written back out in one fixed order, so two builds can be compared. */
    private fun show(b: CrapsBets): String {
        val out = mutableListOf<String>()
        if (b.pass > 0) out.add("pass=${b.pass}")
        if (b.passOdds > 0) out.add("odds=${b.passOdds}")
        if (b.comeFlat > 0) out.add("come=${b.comeFlat}")
        b.comePoints.keys.sorted().forEach {
            val c = b.comePoints.getValue(it)
            out.add("c$it=${c.flat}:${c.odds}")
        }
        b.place.keys.sorted().forEach { out.add("p$it=${b.place.getValue(it)}") }
        if (b.field > 0) out.add("field=${b.field}")
        b.hard.keys.sorted().forEach { out.add("h$it=${b.hard.getValue(it)}") }
        propKeys.forEach { (key, prop) -> b.props[prop]?.let { out.add("$key=$it") } }
        return if (out.isEmpty()) "-" else out.joinToString(",")
    }

    private fun fixture(): List<String> {
        val paths = listOf("../web/craps-parity.txt", "web/craps-parity.txt")
        val file = paths.map(::File).firstOrNull { it.exists() }
            ?: error("craps-parity.txt not found; looked in ${paths.joinToString()}")
        return file.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
    }

    @Test
    fun `every shared case settles the way the fixture says`() {
        val lines = fixture()
        assertTrue("the fixture should carry some cases", lines.size >= 20)

        lines.forEach { line ->
            val cols = line.split("|").map { it.trim() }
            require(cols.size == 5) { "expected 5 columns, got ${cols.size} in: $line" }
            val point = cols[0].takeIf { it != "-" }?.toInt()
            val bets = parseBets(cols[1])
            val (a, b) = cols[2].split("+").map { it.trim().toInt() }
            val want = cols[3].split(" ").filter { it.isNotEmpty() }

            val r = CrapsRules.resolve(bets, point, Roll(a, b))
            assertEquals("point · $line", cols[3].split(" ")[0], r.point?.toString() ?: "-")
            assertEquals("returns · $line", want[1].toDouble(), r.returns, 0.001)
            assertEquals("surviving · $line", cols[4], show(r.bets))
        }
    }
}
