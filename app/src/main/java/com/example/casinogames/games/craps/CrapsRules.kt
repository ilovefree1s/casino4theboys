package com.example.casinogames.games.craps

/** One throw of the pair. */
data class Roll(val a: Int, val b: Int) {
    val total: Int get() = a + b
    /** A pair: the hard way of its own number. */
    val hard: Boolean get() = a == b
}

/** The one-roll centre bets. Each pays and stays up for the next throw. */
enum class Prop(val label: String, val pays: Int, val totals: Set<Int>) {
    ANY_SEVEN("Any 7", 4, setOf(7)),
    ANY_CRAPS("Any Craps", 7, setOf(2, 3, 12)),
    ACES("Aces", 30, setOf(2)),
    ACE_DEUCE("Ace-Deuce", 15, setOf(3)),
    YO("Yo — Eleven", 15, setOf(11)),
    BOXCARS("Boxcars", 30, setOf(12)),
}

/** A come bet that has travelled: its own point, and any odds behind it. */
data class ComeBet(val flat: Int, val odds: Int = 0)

/** Everything sitting on the felt. */
data class CrapsBets(
    val pass: Int = 0,
    val passOdds: Int = 0,
    /** Waiting on the COME, with no number of its own yet. */
    val comeFlat: Int = 0,
    val comePoints: Map<Int, ComeBet> = emptyMap(),
    val place: Map<Int, Int> = emptyMap(),
    val field: Int = 0,
    val hard: Map<Int, Int> = emptyMap(),
    val props: Map<Prop, Int> = emptyMap(),
) {
    // `field` is a soft keyword inside an accessor, so it needs the receiver.
    val total: Int
        get() = pass + passOdds + comeFlat + this.field + place.values.sum() +
            hard.values.sum() + props.values.sum() +
            comePoints.values.sumOf { it.flat + it.odds }

    val isEmpty: Boolean get() = total == 0
}

/** One line of what a throw decided, for the result pills. */
data class CrapsLine(val label: String, val net: Double)

/** What a throw did: the money back, the felt that survived it, and the story. */
data class RollResult(
    val roll: Roll,
    val point: Int?,
    val pointMade: Boolean,
    val sevenOut: Boolean,
    val returns: Double,
    val lines: List<CrapsLine>,
    val bets: CrapsBets,
    val headline: String,
)

/**
 * Crapless craps — "never ever craps", as dealt at the D and the Plaza.
 *
 * Seven wins the come out and nothing loses it: two, three, eleven and twelve
 * all become points instead of craps and a natural. There is no don't pass and
 * no don't come, which is where the name comes from.
 *
 * The line pays for that comfort. Giving up the eleven as an instant winner
 * costs more than dodging the craps is worth, so the pass line runs at 5.38%
 * against 1.41% in the ordinary game. The free odds behind it are the same
 * zero-edge bet they always are, and they are where this game is really played.
 */
object CrapsRules {

    /** Every number that can be a point here, which is every number but the seven. */
    val POINTS = listOf(2, 3, 4, 5, 6, 8, 9, 10, 11, 12)

    /** The four numbers that can be made the hard way. */
    val HARD_WAYS = listOf(4, 6, 8, 10)

    /** Ways to roll a total with two dice — the whole of the odds maths. */
    fun ways(total: Int): Int = 6 - Math.abs(7 - total)

    /**
     * True odds behind the line, as numerator to denominator. Free of edge:
     * the payout is exactly the chance of making the number before a seven.
     */
    fun oddsPay(point: Int): Pair<Int, Int> = when (point) {
        2, 12 -> 6 to 1
        3, 11 -> 3 to 1
        4, 10 -> 2 to 1
        5, 9 -> 3 to 2
        else -> 6 to 5
    }

    /** What the house pays to place a number, edge and all. */
    fun placePay(number: Int): Pair<Int, Int> = when (number) {
        2, 12 -> 11 to 2
        3, 11 -> 11 to 4
        4, 10 -> 9 to 5
        5, 9 -> 7 to 5
        else -> 7 to 6
    }

    /** Hard four and ten pay seven, hard six and eight pay nine. */
    fun hardPay(number: Int): Int = if (number == 4 || number == 10) 7 else 9

    /** The field takes every number but the five, six, seven and eight. */
    val FIELD_NUMBERS = setOf(2, 3, 4, 9, 10, 11, 12)

    /** Two pays double and twelve pays triple; the rest of the field pays even. */
    fun fieldPay(total: Int): Int = when (total) {
        2 -> 2
        12 -> 3
        else -> 1
    }

    private fun pay(stake: Int, odds: Pair<Int, Int>): Double =
        stake.toDouble() * odds.first / odds.second

    /**
     * Settles one throw against the felt.
     *
     * Bets that win a decision — the line and the come — are paid and taken
     * down, stake and all. Bets that ride — place, field, hardways and the
     * props — are paid their winnings and left standing, the way the dealer
     * would. Place bets and hardways sleep through a come out; the come and
     * its odds work every throw.
     */
    fun resolve(bets: CrapsBets, point: Int?, roll: Roll): RollResult {
        val t = roll.total
        val comeOut = point == null
        var back = 0.0
        val lines = mutableListOf<CrapsLine>()

        var field = bets.field
        var props = bets.props.toMutableMap()
        var hard = bets.hard.toMutableMap()
        var place = bets.place.toMutableMap()
        var comePoints = bets.comePoints.toMutableMap()
        var comeFlat = bets.comeFlat
        var pass = bets.pass
        var passOdds = bets.passOdds

        // ---- the field, one throw and gone ----
        if (field > 0) {
            if (t in FIELD_NUMBERS) {
                val mult = fieldPay(t)
                back += field.toDouble() * mult
                lines.add(CrapsLine("Field · $t pays $mult", field.toDouble() * mult))
            } else {
                lines.add(CrapsLine("Field", -field.toDouble()))
                field = 0
            }
        }

        // ---- the centre props, one throw each ----
        for (p in Prop.entries) {
            val stake = props[p] ?: continue
            if (t in p.totals) {
                back += stake.toDouble() * p.pays
                lines.add(CrapsLine("${p.label} · ${p.pays} to 1", stake.toDouble() * p.pays))
            } else {
                lines.add(CrapsLine(p.label, -stake.toDouble()))
                props.remove(p)
            }
        }

        // ---- the hardways, asleep through a come out ----
        if (!comeOut && hard.isNotEmpty()) {
            if (t == 7) {
                val lost = hard.values.sum()
                if (lost > 0) lines.add(CrapsLine("Hardways", -lost.toDouble()))
                hard.clear()
            } else if (t in HARD_WAYS) {
                val stake = hard[t]
                if (stake != null) {
                    if (roll.hard) {
                        back += stake.toDouble() * hardPay(t)
                        lines.add(CrapsLine("Hard $t · ${hardPay(t)} to 1", stake.toDouble() * hardPay(t)))
                    } else {
                        lines.add(CrapsLine("Hard $t", -stake.toDouble()))
                        hard.remove(t)
                    }
                }
            }
        }

        // ---- come bets already on a number; these work every throw ----
        if (t == 7) {
            for ((number, bet) in comePoints) {
                lines.add(CrapsLine("Come $number", -(bet.flat + bet.odds).toDouble()))
            }
            comePoints.clear()
        } else {
            comePoints[t]?.let { bet ->
                val won = bet.flat.toDouble() + pay(bet.odds, oddsPay(t))
                back += bet.flat + bet.odds + won
                lines.add(CrapsLine("Come $t", won))
                comePoints.remove(t)
            }
        }

        // ---- a come bet still waiting for its number ----
        if (comeFlat > 0) {
            if (t == 7) {
                back += comeFlat.toDouble() * 2
                lines.add(CrapsLine("Come", comeFlat.toDouble()))
            } else {
                comePoints[t] = ComeBet(
                    flat = comeFlat + (comePoints[t]?.flat ?: 0),
                    odds = comePoints[t]?.odds ?: 0,
                )
                lines.add(CrapsLine("Come travels to $t", 0.0))
            }
            comeFlat = 0
        }

        // ---- place bets, asleep through a come out ----
        if (!comeOut) {
            if (t == 7) {
                val lost = place.values.sum()
                if (lost > 0) lines.add(CrapsLine("Place bets", -lost.toDouble()))
                place.clear()
            } else {
                place[t]?.let { stake ->
                    val won = pay(stake, placePay(t))
                    back += won
                    val (n, d) = placePay(t)
                    lines.add(CrapsLine("Place $t · $n to $d", won))
                }
            }
        }

        // ---- the line ----
        var newPoint = point
        var pointMade = false
        var sevenOut = false
        if (comeOut) {
            if (t == 7) {
                if (pass > 0) {
                    back += pass.toDouble() * 2
                    lines.add(CrapsLine("Pass Line", pass.toDouble()))
                    pass = 0
                }
            } else {
                newPoint = t
            }
        } else {
            if (t == point) {
                pointMade = true
                if (pass > 0) {
                    val won = pass.toDouble() + pay(passOdds, oddsPay(point))
                    back += pass + passOdds + won
                    lines.add(CrapsLine("Pass Line · point $point", won))
                }
                pass = 0; passOdds = 0
                newPoint = null
            } else if (t == 7) {
                sevenOut = true
                if (pass + passOdds > 0) {
                    lines.add(CrapsLine("Pass Line", -(pass + passOdds).toDouble()))
                }
                pass = 0; passOdds = 0
                newPoint = null
            }
        }

        val headline = when {
            sevenOut -> "Seven out"
            pointMade -> "Point $point made!"
            comeOut && t == 7 -> "Winner seven!"
            comeOut -> "Point is $t"
            else -> "$t"
        }

        return RollResult(
            roll = roll,
            point = newPoint,
            pointMade = pointMade,
            sevenOut = sevenOut,
            returns = back,
            lines = lines,
            bets = CrapsBets(
                pass = pass, passOdds = passOdds,
                comeFlat = comeFlat, comePoints = comePoints.toMap(),
                place = place.toMap(), field = field,
                hard = hard.toMap(), props = props.toMap(),
            ),
            headline = headline,
        )
    }

    /** The most odds the house will lay behind a flat bet: triple across here. */
    const val MAX_ODDS_MULTIPLE = 3
}
