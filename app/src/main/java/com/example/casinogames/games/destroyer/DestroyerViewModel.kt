package com.example.casinogames.games.destroyer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.casinogames.campaign.Campaign
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.campaign.limitsFor
import com.example.casinogames.games.destroyer.DestroyerRules.Ship
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class DzPhase { BETTING, TARGETING, OFFER, RESULT }

data class DzResult(val label: String, val units: Int)


class DestroyerViewModel : ViewModel() {

    private val random = Random(System.nanoTime())

    /** Play testing has its own purse; the campaign shares one with every table. */
    private var freePurse by mutableDoubleStateOf(FreePlay.buyIn)
    val bankroll: Double get() = if (campaign) Campaign.bankroll else freePurse

    private fun spend(amount: Double) {
        if (campaign) Campaign.stake(amount) else freePurse -= amount
    }

    private fun collect(amount: Double) {
        if (amount <= 0.0) return
        if (campaign) Campaign.payOut(amount) else freePurse += amount
    }

    val limits get() = limitsFor(campaign)
    val goal: Double get() = Campaign.goal

    // ---- campaign ----

    var campaign by mutableStateOf(false)
        private set
    private var modeInitialized = false

    fun enterMode(campaignMode: Boolean) {
        if (modeInitialized && campaign == campaignMode) return
        campaign = campaignMode
        modeInitialized = true
        if (!campaignMode) freePurse = FreePlay.buyIn
        resetHand()
    }

    // ---- the hand ----

    var phase by mutableStateOf(DzPhase.BETTING)
        private set
    var selectedChip by mutableIntStateOf(25)

    var bet by mutableIntStateOf(0)
        private set
    var stake by mutableIntStateOf(0)
        private set
    private var lastBet = 0

    var fleet by mutableStateOf<List<Ship>>(DestroyerRules.placeFleet(Random(0)))
        private set
    val hitCells = mutableStateListOf<Int>()
    val missCells = mutableStateListOf<Int>()
    var lives by mutableIntStateOf(DestroyerRules.LIVES)
        private set
    /** The last roll as row to column, or null before the first. */
    var lastRoll by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    var message by mutableStateOf("Place your bet")
        private set
    var results by mutableStateOf<List<DzResult>>(emptyList())
        private set
    var lastWin by mutableIntStateOf(0)
        private set
    /** What the house wants for one more missile, priced off the live board. */
    var missilePrice by mutableIntStateOf(0)
        private set
    private var missilesBought = 0
    /** True while the shot in the rack was a bought one: a hit off it earns
     *  the right to buy one more, so a hot missile keeps the shop open. */
    private var boughtShotLive = false

    val totalAtRisk: Int
        get() = if (phase == DzPhase.BETTING) bet else stake

    init {
        fleet = DestroyerRules.placeFleet(random)
    }

    fun addChip() {
        if (phase != DzPhase.BETTING) return
        val room = (bankroll - bet).toInt()
        val affordable = minOf(selectedChip, room)
        if (affordable <= 0) { message = "Not enough in the purse"; return }
        val amount = limits.allow(affordable, bet, side = false)
        if (amount <= 0) { message = limits.refusal(side = false); return }
        bet += amount
        message = if (amount < selectedChip) limits.refusal(side = false) else "Fleet bet down"
    }

    fun clearBet() {
        if (phase != DzPhase.BETTING) return
        bet = 0
        message = "Place your bet"
    }

    /** A new spread of ships. The odds don't care, but the player might. */
    fun shuffleFleet() {
        if (phase != DzPhase.BETTING) return
        fleet = DestroyerRules.placeFleet(random)
    }

    /**
     * The player's own admiralty, while the bet is still open: drag a hull to
     * new water. The anchor is clamped onto the map, and a move that would
     * lay it across another ship is refused — the hull springs back.
     */
    fun moveShip(index: Int, row: Int, col: Int): Boolean {
        if (phase != DzPhase.BETTING) return false
        val old = fleet.getOrNull(index) ?: return false
        val horizontal = old.cells.map { it / DestroyerRules.GRID }.distinct().size == 1
        val maxRow = if (horizontal) DestroyerRules.GRID - 1 else DestroyerRules.GRID - old.size
        val maxCol = if (horizontal) DestroyerRules.GRID - old.size else DestroyerRules.GRID - 1
        val ship = DestroyerRules.shipAt(
            row.coerceIn(0, maxRow), col.coerceIn(0, maxCol), old.size, horizontal,
        ) ?: return false
        if (!DestroyerRules.clearOf(ship, fleet.filterIndexed { i, _ -> i != index })) return false
        fleet = fleet.toMutableList().also { it[index] = ship }
        return true
    }

    /** Tap a hull to swing it about its anchor, if the water there is open. */
    fun rotateShip(index: Int): Boolean {
        if (phase != DzPhase.BETTING) return false
        val old = fleet.getOrNull(index) ?: return false
        val horizontal = old.cells.map { it / DestroyerRules.GRID }.distinct().size == 1
        val row = old.cells.min() / DestroyerRules.GRID
        val col = old.cells.min() % DestroyerRules.GRID
        val maxRow = if (!horizontal) DestroyerRules.GRID - 1 else DestroyerRules.GRID - old.size
        val maxCol = if (!horizontal) DestroyerRules.GRID - old.size else DestroyerRules.GRID - 1
        val ship = DestroyerRules.shipAt(
            row.coerceIn(0, maxRow), col.coerceIn(0, maxCol), old.size, !horizontal,
        ) ?: return false
        if (!DestroyerRules.clearOf(ship, fleet.filterIndexed { i, _ -> i != index })) return false
        fleet = fleet.toMutableList().also { it[index] = ship }
        return true
    }

    fun deal() {
        if (phase != DzPhase.BETTING) return
        if (bet <= 0) { message = "Place your bet first"; return }
        spend(bet.toDouble())
        lastBet = bet
        stake = bet
        bet = 0
        hitCells.clear(); missCells.clear()
        lives = DestroyerRules.LIVES
        lastRoll = null
        missilesBought = 0
        boughtShotLive = false
        results = emptyList(); lastWin = 0
        phase = DzPhase.TARGETING
        message = "Fire — ${DestroyerRules.LIVES} shots in the rack"
    }

    /**
     * Where the dice said the shot went — the tray's physics settles and
     * hands the faces here. A fresh hit is a free shot; anything else burns
     * one. A settle that lands after the hand is over — a stray flick in the
     * closing pause — must not fire a phantom shot or settle the hand twice.
     */
    fun shotLands(row: Int, col: Int) {
        if (phase != DzPhase.TARGETING || lives <= 0) return
        lastRoll = row to col
        val cell = row * DestroyerRules.GRID + col
        val struck = fleet.firstOrNull { cell in it.cells }
        if (struck != null && cell !in hitCells) {
            hitCells.add(cell)
            if (boughtShotLive) {
                missilesBought = (missilesBought - 1).coerceAtLeast(0)
                boughtShotLive = false
            }
            val sunk = struck.cells.all { it in hitCells }
            message = when {
                sunk -> "${struck.size}-ship sunk!"
                else -> "Hit — free shot"
            }
        } else {
            if (cell !in hitCells && struck == null) missCells.add(cell)
            lives--
            boughtShotLive = false
            message = if (struck != null) "Same water twice — that costs a shot"
            else "Miss"
        }
        val allSunk = DestroyerRules.sunkCount(fleet, hitCells.toSet()) == fleet.size
        if (allSunk) {
            viewModelScope.launch { delay(480); settleHand() }
        } else if (lives <= 0) {
            viewModelScope.launch { delay(480); offerMissile() }
        }
    }

    /**
     * The rack is empty but the fleet still floats: the house offers one more
     * missile at the floor machine's own price — 25/22 of what the shot is
     * worth on this exact board. No purse for it, and the hand just settles.
     */
    private fun offerMissile() {
        if (phase != DzPhase.TARGETING) return
        val price = DestroyerRules.missilePrice(fleet, hitCells.toSet(), stake)
        if (missilesBought >= DestroyerRules.MISSILE_LIMIT || bankroll < price) {
            settleHand(); return
        }
        missilePrice = price
        phase = DzPhase.OFFER
        message = "Out of shells — one more missile?"
    }

    fun buyMissile() {
        if (phase != DzPhase.OFFER || bankroll < missilePrice) return
        spend(missilePrice.toDouble())
        missilesBought++
        boughtShotLive = true
        lives = 1
        phase = DzPhase.TARGETING
        message = "One missile in the rack — make it count"
    }

    fun collectHand() {
        if (phase != DzPhase.OFFER) return
        settleHand()
    }

    private fun settleHand() {
        if (phase != DzPhase.TARGETING && phase != DzPhase.OFFER) return
        val hits = hitCells.toSet()
        val units = DestroyerRules.settle(fleet, hits)
        lastWin = units * stake
        collect(lastWin.toDouble())

        val out = mutableListOf<DzResult>()
        fleet.sortedByDescending { it.size }.forEach { ship ->
            val h = ship.cells.count { it in hits }
            val pay = DestroyerRules.shipPay(ship.size, h)
            if (pay > 0) {
                val sunk = h == ship.size
                out.add(DzResult("${ship.size}-ship ${if (sunk) "sunk" else "$h hits"}", pay))
            }
        }
        val sunk = DestroyerRules.sunkCount(fleet, hits)
        if (DestroyerRules.FLEET_BONUS[sunk] > 0) {
            out.add(DzResult("$sunk ships down", DestroyerRules.FLEET_BONUS[sunk]))
        }
        results = out

        val net = lastWin - stake
        message = when {
            campaign && bankroll >= goal -> "🏆 GOAL REACHED!"
            sunk == fleet.size -> "FLEET DESTROYED — ${unitsLabel(units)}"
            net > 0 -> "Paid ${unitsLabel(units)}"
            units > 0 -> "Back ${unitsLabel(units)} of the stake"
            else -> "The fleet sails on"
        }
        phase = DzPhase.RESULT
    }

    private fun unitsLabel(units: Int) = "$units-for-1"

    fun nextHand(repeat: Boolean) {
        if (phase != DzPhase.RESULT) return
        resetHand()
        if (repeat && lastBet <= bankroll) bet = lastBet
        message = "Place your bet"
    }

    private fun resetHand() {
        phase = DzPhase.BETTING
        bet = 0; stake = 0
        hitCells.clear(); missCells.clear()
        lives = DestroyerRules.LIVES
        lastRoll = null

        results = emptyList(); lastWin = 0
        fleet = DestroyerRules.placeFleet(random)
    }

    // ---- broke ----

    fun buyBackIn() {
        if (phase != DzPhase.BETTING || totalAtRisk > 0 || bankroll >= 25) return
        if (campaign) Campaign.takeMarker() else freePurse = FreePlay.buyIn
    }

    fun raiseGoal() = Campaign.raiseGoal()
    fun restartCampaign() {
        Campaign.restart()
        message = "Place your bet"
    }
}
