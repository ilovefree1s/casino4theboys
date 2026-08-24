package com.example.casinogames.campaign

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The purse every campaign table plays out of. */
const val CAMPAIGN_START = 5_000.0
const val CAMPAIGN_GOAL = 1_000_000.0

/** What the house hands over on a marker, and what it costs to clear it. */
const val MARKER_AMOUNT = 5_000.0
const val MARKER_INTEREST = 0.5

/**
 * A room on the floor. The buy-in is what it takes to get in; the limits are
 * what it lets you put down once you are.
 *
 * [minBet] is printed on the felt but never enforced — it is the room's
 * character, not a rule. [maxBet] and [sideMax] are hard: the house will not
 * take the chip.
 */
enum class Room(
    val roomName: String,
    val buyIn: Double,
    val minBet: Int,
    val maxBet: Int,
    val sideMax: Int,
    val badBeatMax: Int,
) {
    BASEMENT("The Basement", 0.0, 25, 500, 100, 25),
    MAIN_FLOOR("Main Floor", 10_000.0, 100, 2_500, 500, 50),
    HIGH_LIMIT("High Limit", 50_000.0, 500, 10_000, 2_000, 75),
    SALON("The Salon", 250_000.0, 2_500, 50_000, 10_000, 100),
    WHALE("Whale Room", 1_000_000.0, 10_000, 250_000, 50_000, 125);

    /** How the limits read on a table sign. */
    val sign: String get() = "$${fmt(minBet)} – $${fmt(maxBet)}"
    val sideSign: String get() = "SIDE $${fmt(sideMax)}"

    private fun fmt(v: Int) = if (v >= 1000) "${v / 1000}K" else "$v"
}

/**
 * One campaign, shared by every table. The old arrangement gave each game its
 * own copy of all of this, reading the purse back off disk whenever a table
 * was opened; they drifted apart, and a bust meant something different
 * depending on which felt you were sitting at. There is one of it now, and
 * every table reads the same live figures.
 */
object Campaign {
    private lateinit var prefs: SharedPreferences

    var bankroll by mutableDoubleStateOf(CAMPAIGN_START)
        private set
    var goal by mutableDoubleStateOf(CAMPAIGN_GOAL)
        private set

    /**
     * What is owed on markers taken, interest included. Nothing moves up a
     * room while this stands.
     */
    var debt by mutableDoubleStateOf(0.0)
        private set
    var markersTaken by mutableStateOf(0)
        private set

    var room by mutableStateOf(Room.BASEMENT)
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("campaign", Context.MODE_PRIVATE)
        bankroll = prefs.getFloat("bankroll", CAMPAIGN_START.toFloat()).toDouble()
        goal = prefs.getFloat("goal", CAMPAIGN_GOAL.toFloat()).toDouble()
        debt = prefs.getFloat("debt", 0f).toDouble()
        markersTaken = prefs.getInt("markers", 0)
        room = runCatching { Room.valueOf(prefs.getString("room", null) ?: "") }
            .getOrDefault(Room.BASEMENT)
        // A room the purse can no longer cover is not somewhere to be left
        // sitting: the pit walks the player down to one they can afford.
        demoteIfBroke()
    }

    private fun save() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putFloat("bankroll", bankroll.toFloat())
            .putFloat("goal", goal.toFloat())
            .putFloat("debt", debt.toFloat())
            .putInt("markers", markersTaken)
            .putString("room", room.name)
            .apply()
    }

    // ---- the purse ----

    /** Takes a stake off the purse. The table has already checked it is there. */
    fun stake(amount: Double) {
        bankroll -= amount
        save()
    }

    /**
     * Puts winnings back, stake included. A hand that ends leaving the purse
     * short of the room minimum walks the player down on the spot — better
     * than leaving them at a table they can no longer play.
     */
    fun payOut(amount: Double) {
        bankroll += amount
        demoteIfBroke()
        save()
    }

    /** Only for a table topping the purse up outside a hand. */
    fun resetPurseTo(amount: Double) {
        bankroll = amount
        save()
    }

    // ---- rooms ----

    /** Every room the purse can currently buy into, debt permitting. */
    fun roomsOpen(): List<Room> = Room.entries.filter { canEnter(it) }

    fun canEnter(target: Room): Boolean = when {
        target.ordinal <= room.ordinal -> true
        debt > 0 -> false
        else -> bankroll >= target.buyIn
    }

    /** Why a room is shut, for the lobby to say out loud. */
    fun blockedReason(target: Room): String? = when {
        canEnter(target) -> null
        debt > 0 -> "Square the marker first"
        else -> "Buy-in $${formatWhole(target.buyIn)}"
    }

    fun enter(target: Room) {
        if (!canEnter(target)) return
        room = target
        save()
    }

    /**
     * A purse that cannot cover the room's minimum has no business at its
     * tables. Drops to the highest room it can still play.
     */
    fun demoteIfBroke() {
        var target = room
        while (target.ordinal > 0 && bankroll < target.minBet) {
            target = Room.entries[target.ordinal - 1]
        }
        if (target != room) {
            room = target
            save()
        }
    }

    // ---- markers ----

    /** Broke is being unable to cover the cheapest bet in the house. */
    val isBroke: Boolean get() = bankroll < Room.BASEMENT.minBet

    fun takeMarker() {
        bankroll += MARKER_AMOUNT
        debt += MARKER_AMOUNT * (1 + MARKER_INTEREST)
        markersTaken++
        save()
    }

    val canPayMarker: Boolean get() = debt > 0 && bankroll >= debt

    fun payMarker() {
        if (!canPayMarker) return
        bankroll -= debt
        debt = 0.0
        save()
    }

    // ---- the goal ----

    val goalReached: Boolean get() = bankroll >= goal

    fun raiseGoal() {
        goal *= 100
        save()
    }

    fun restart() {
        bankroll = CAMPAIGN_START
        goal = CAMPAIGN_GOAL
        debt = 0.0
        markersTaken = 0
        room = Room.BASEMENT
        save()
    }
}

private fun formatWhole(v: Double) = String.format(java.util.Locale.US, "%,.0f", v)

/**
 * What a table will take this hand. Play testing has no house behind it, so
 * nothing is capped there — the limits are a campaign thing.
 */
data class TableLimits(
    val min: Int,
    val max: Int,
    val sideMax: Int,
    val enforced: Boolean,
    /**
     * DJ Wild's Bad Beat pays 10,000 to 1, so it is capped far below the
     * ordinary side bets — the way a real pit caps a bad beat — or one royal
     * on the cheapest table would be the whole campaign.
     */
    val badBeatMax: Int = sideMax,
) {
    /**
     * How much of [wanted] a spot will actually take, given what is on it
     * already. Zero means the chip does not go down.
     */
    fun allow(wanted: Int, alreadyOn: Int, side: Boolean = false): Int =
        allowUpTo(if (side) sideMax else max, wanted, alreadyOn)

    /** The same, against the Bad Beat's own much lower ceiling. */
    fun allowBadBeat(wanted: Int, alreadyOn: Int): Int =
        allowUpTo(badBeatMax, wanted, alreadyOn)

    private fun allowUpTo(cap: Int, wanted: Int, alreadyOn: Int): Int {
        if (!enforced) return wanted
        return wanted.coerceAtMost((cap - alreadyOn).coerceAtLeast(0))
    }

    /** What the table says when it will not take any more. */
    fun refusal(side: Boolean): String =
        if (side) "Side bet max $${money(sideMax)}" else "Table max $${money(max)}"

    fun badBeatRefusal(): String = "Bad Beat max $${money(badBeatMax)}"

    private fun money(v: Int) = String.format(java.util.Locale.US, "%,d", v)
}

/** No house, no limits. */
val FreePlayLimits = TableLimits(0, Int.MAX_VALUE, Int.MAX_VALUE, enforced = false)

fun limitsFor(campaign: Boolean): TableLimits =
    if (!campaign) FreePlayLimits
    else Campaign.room.let {
        TableLimits(it.minBet, it.maxBet, it.sideMax, enforced = true, badBeatMax = it.badBeatMax)
    }
