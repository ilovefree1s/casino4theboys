package com.example.casinogames.games.roulette

import kotlin.random.Random

/**
 * American double-zero roulette: 38 pockets, every standard bet on the felt.
 *
 * A pocket is 0-36, with 37 standing in for 00 — it needs to live in the same
 * ints as the rest, and 37 is the one number the wheel does not use.
 */
object RouletteEngine {

    const val DOUBLE_ZERO = 37

    val RED_NUMBERS = setOf(
        1, 3, 5, 7, 9, 12, 14, 16, 18,
        19, 21, 23, 25, 27, 30, 32, 34, 36,
    )

    /**
     * The pockets in the order they sit on an American wheel, so a reel built
     * from this reads like the rim unrolled rather than a shuffled list.
     */
    val WHEEL = listOf(
        0, 28, 9, 26, 30, 11, 7, 20, 32, 17,
        5, 22, 34, 15, 3, 24, 36, 13, 1, DOUBLE_ZERO,
        27, 10, 25, 29, 12, 8, 19, 31, 18, 6,
        21, 33, 16, 4, 23, 35, 14, 2,
    )

    fun isRed(pocket: Int): Boolean = pocket in RED_NUMBERS
    fun isBlack(pocket: Int): Boolean = pocket in 1..36 && pocket !in RED_NUMBERS

    fun label(pocket: Int): String = if (pocket == DOUBLE_ZERO) "00" else pocket.toString()

    fun spin(random: Random = Random.Default): Int = random.nextInt(38).let {
        if (it == 37) DOUBLE_ZERO else it
    }

    /**
     * One wager: the pockets it covers and what a hit multiplies the stake by.
     * Winnings only — settle() adds the stake back on top.
     */
    data class Bet(val name: String, val pockets: Set<Int>, val paysToOne: Int)

    /** 35:1 on any single pocket, zeroes included. */
    fun straight(pocket: Int) = Bet(label(pocket), setOf(pocket), 35)

    /** 17:1 on two adjacent pockets — includes the 0/00 split. */
    fun split(a: Int, b: Int) = Bet("${label(a)}-${label(b)}", setOf(a, b), 17)

    /** 11:1 on a row of three; also 0-1-2, 0-00-2 and 00-2-3. */
    fun street(pockets: Set<Int>) =
        Bet(pockets.sorted().joinToString("-") { label(it) }, pockets, 11)

    /** 8:1 on four pockets meeting at a corner. */
    fun corner(pockets: Set<Int>) =
        Bet(pockets.sorted().joinToString("-") { label(it) }, pockets, 8)

    /** 6:1 on 0-00-1-2-3, the American top line — the felt's worst odds. */
    val TOP_LINE = Bet("Top Line", setOf(0, DOUBLE_ZERO, 1, 2, 3), 6)

    /** 5:1 on two adjoining rows. */
    fun sixLine(pockets: Set<Int>) =
        Bet(pockets.sorted().joinToString("-") { label(it) }, pockets, 5)

    fun dozen(index: Int): Bet {
        val start = index * 12 + 1
        return Bet(
            listOf("1st 12", "2nd 12", "3rd 12")[index],
            (start until start + 12).toSet(),
            2,
        )
    }

    fun column(index: Int): Bet = Bet(
        listOf("Col 1", "Col 2", "Col 3")[index],
        (0 until 12).map { it * 3 + index + 1 }.toSet(),
        2,
    )

    val RED = Bet("Red", RED_NUMBERS, 1)
    val BLACK = Bet("Black", (1..36).toSet() - RED_NUMBERS, 1)
    val ODD = Bet("Odd", (1..36).filter { it % 2 == 1 }.toSet(), 1)
    val EVEN = Bet("Even", (1..36).filter { it % 2 == 0 }.toSet(), 1)
    val LOW = Bet("1 to 18", (1..18).toSet(), 1)
    val HIGH = Bet("19 to 36", (19..36).toSet(), 1)

    /**
     * Gross return for one wager: stake plus winnings on a hit, the stake alone
     * never — roulette has no pushes.
     */
    fun settle(bet: Bet, stake: Double, pocket: Int): Double =
        if (pocket in bet.pockets) stake * (bet.paysToOne + 1.0) else 0.0
}
