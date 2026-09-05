package com.example.casinogames.games.destroyer

import kotlin.random.Random

/**
 * Destroyer — the battleship bubble-craps game, played the way the floor
 * machine plays it. A 6x6 grid holds four ships (2, 3, 3 and 4 cells). Two
 * dice pick a cell: one die the row, one the column. Four lives: a miss —
 * open water or a cell already hit — burns one, a fresh hit is a free roll.
 *
 * When the hand ends, each ship pays the highest rung it reached, sinking
 * two or more ships pays a bonus on top, and the stake itself is spent, not
 * returned — this is a machine game, not a table bet. That reading of the
 * felt is the one that reproduces the floor game's published 5.6% edge; paying
 * every rung as it lands comes out over 100%.
 */
object DestroyerRules {

    const val GRID = 6
    const val CELLS = GRID * GRID
    const val LIVES = 4
    /** The house sells at most this many bonus missiles a hand. */
    const val MISSILE_LIMIT = 3

    /** Ship sizes, largest first so placement fails less often. */
    val SHIP_SIZES = listOf(4, 3, 3, 2)

    /** What a ship pays for reaching [hits], keyed by ship size. */
    val MILESTONES: Map<Int, Map<Int, Int>> = mapOf(
        2 to mapOf(2 to 3),
        3 to mapOf(2 to 3, 3 to 10),
        4 to mapOf(2 to 3, 3 to 4, 4 to 40),
    )

    /** The fleet bonus, by ships sunk. */
    val FLEET_BONUS = listOf(0, 0, 51, 251, 501)

    /** One ship: its cells on the grid, cell = row * GRID + col. */
    data class Ship(val cells: List<Int>) {
        val size: Int get() = cells.size
    }

    /**
     * Deals a fleet: every ship straight, in bounds, no overlap. Ships may
     * touch — the floor game allows it and the odds don't care.
     */
    fun placeFleet(random: Random): List<Ship> {
        while (true) {
            val taken = mutableSetOf<Int>()
            val ships = mutableListOf<Ship>()
            var ok = true
            for (size in SHIP_SIZES) {
                var placed = false
                repeat(100) {
                    if (placed) return@repeat
                    val horizontal = random.nextBoolean()
                    val row = random.nextInt(GRID)
                    val col = random.nextInt(GRID - size + 1)
                    val cells = (0 until size).map { i ->
                        if (horizontal) row * GRID + col + i else (col + i) * GRID + row
                    }
                    if (cells.none { it in taken }) {
                        taken.addAll(cells)
                        ships.add(Ship(cells))
                        placed = true
                    }
                }
                if (!placed) { ok = false; break }
            }
            if (ok) return ships
        }
    }

    /** A straight hull anchored at [row],[col], or null when it runs off the map. */
    fun shipAt(row: Int, col: Int, size: Int, horizontal: Boolean): Ship? {
        val cells = (0 until size).map { i ->
            val r = if (horizontal) row else row + i
            val c = if (horizontal) col + i else col
            if (r !in 0 until GRID || c !in 0 until GRID) return null
            r * GRID + c
        }
        return Ship(cells)
    }

    /** Whether [ship] lies in open water, clear of every hull in [others]. */
    fun clearOf(ship: Ship, others: List<Ship>): Boolean =
        others.none { other -> other.cells.any { it in ship.cells } }

    /** What one ship pays for the hits it took — the highest rung reached. */
    fun shipPay(size: Int, hits: Int): Int {
        val rungs = MILESTONES.getValue(size)
        return rungs.filterKeys { it <= hits }.values.maxOrNull() ?: 0
    }

    /**
     * Settles a finished hand: units won per unit staked. The stake is spent,
     * so the table credits exactly stake × this — nothing comes back on a
     * hand that never reached a rung.
     */
    fun settle(fleet: List<Ship>, hitCells: Set<Int>): Int {
        var units = 0
        var sunk = 0
        for (ship in fleet) {
            val hits = ship.cells.count { it in hitCells }
            units += shipPay(ship.size, hits)
            if (hits == ship.size) sunk++
        }
        return units + FLEET_BONUS[sunk]
    }

    /** How many ships in [fleet] are fully under [hitCells]. */
    fun sunkCount(fleet: List<Ship>, hitCells: Set<Int>): Int =
        fleet.count { ship -> ship.cells.all { it in hitCells } }

    /**
     * What one more shot is worth, in expected units above what the board
     * already pays — the free-roll chain included, exactly: a fresh hit rolls
     * again, and only a miss (or the fleet going down) ends it. The state is
     * small enough to walk outright, so this is the true figure, not a guess.
     */
    fun extraShotEv(fleet: List<Ship>, hitCells: Set<Int>): Double {
        val shipCells = fleet.flatMap { it.cells }
        val index = shipCells.withIndex().associate { (i, c) -> c to i }
        val full = (1 shl shipCells.size) - 1
        val memo = HashMap<Int, Double>()
        fun payout(mask: Int): Int {
            val hits = HashSet<Int>()
            for ((i, c) in shipCells.withIndex()) if (mask and (1 shl i) != 0) hits.add(c)
            return settle(fleet, hits)
        }
        fun v(mask: Int): Double {
            memo[mask]?.let { return it }
            val out: Double
            if (mask == full) {
                out = payout(mask).toDouble()
            } else {
                var sum = 0.0
                var fresh = 0
                for (i in shipCells.indices) {
                    if (mask and (1 shl i) == 0) { fresh++; sum += v(mask or (1 shl i)) }
                }
                out = (sum + (CELLS - fresh) * payout(mask)) / CELLS
            }
            memo[mask] = out
            return out
        }
        var mask = 0
        for (c in hitCells) index[c]?.let { mask = mask or (1 shl it) }
        return v(mask) - payout(mask)
    }

    /**
     * What the house sells that shot for: 25/22 of its expected value — the
     * floor machine's own pricing, a flat 12% edge on the purchase.
     */
    fun missilePrice(fleet: List<Ship>, hitCells: Set<Int>, stake: Int): Int =
        Math.round(extraShotEv(fleet, hitCells) * stake * 25.0 / 22.0).toInt().coerceAtLeast(1)
}
