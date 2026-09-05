package com.example.casinogames.games.destroyer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import com.example.casinogames.R
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.games.destroyer.DestroyerRules.GRID
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
import com.example.casinogames.ui.common.FreePlayBuyIn
import com.example.casinogames.ui.common.CasinoChip
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P

/**
 * The felt's colours are taken off pink4theboys.png itself: the hot magenta
 * ring and its pale glow. Hits stay battleship red and the money stays brass —
 * both read clearly against the pink.
 */
private val TableBlack = Color(0xFF0C0308)
private val Steel = Color(0xFFFF40A0)
private val SteelDim = Color(0x99FF40A0)
private val PalePink = Color(0xFFFFC4E4)
private val HitRed = Color(0xFFFF3B4D)
private val Brass = Color(0xFFE8C169)
private val SeaLine = Color(0xFF6E1C4A)

@Composable
fun DestroyerScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = {},
    vm: DestroyerViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    var showPays by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(TableBlack)) {
        Image(
            painter = painterResource(R.drawable.pink4theboys),
            contentDescription = null,
            // Dimmed the way every felt is, so the board reads over it.
            modifier = Modifier.fillMaxSize().alpha(0.19f),
            contentScale = ContentScale.Crop,
        )
        // The tray's dice ARE the dice: their settle is the shot. One state
        // outside the composables, so a hand survives recomposition.
        // Die 0 calls the row letter, die 1 calls the column number. Rows
        // are lettered from the bottom up — A is the near water — so the
        // letter counts back from the last row.
        val tray = remember { DiceTrayState(letterDie = 0) }
        LaunchedEffect(Unit) {
            tray.onSettle = { values -> vm.shotLands(GRID - values[0], values[1] - 1) }
        }
        LaunchedEffect(vm.phase) {
            if (vm.phase == DzPhase.TARGETING) tray.home()
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(vm, onBack)
            Spacer(Modifier.height(6.dp))
            Board(vm)
            Spacer(Modifier.height(6.dp))
            StatusRow(vm, onShowPays = { showPays = true })
            Spacer(Modifier.height(2.dp))
            MessageLine(vm)
            ResultRows(vm)
            Spacer(Modifier.height(6.dp))
            // The bottom of the felt: chips and the bet while betting, the
            // dice once the hand is on. It takes whatever height the board
            // leaves — the throw still runs up the tray, away from the hand.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x8C140610))
                    .border(1.5.dp, Steel.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (vm.phase == DzPhase.BETTING) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BetRow(vm)
                        Spacer(Modifier.height(14.dp))
                        ChipRail(vm)
                    }
                } else {
                    DiceTray(tray, Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(8.dp))
            Actions(vm)
        }

        if (showPays) PayTable { showPays = false }
        if (vm.campaign && vm.bankroll >= vm.goal && vm.phase == DzPhase.RESULT) {
            CampaignComplete(
                goal = vm.goal,
                nextGoal = vm.goal * 100,
                onGoBigger = { vm.raiseGoal() },
                onStartOver = { vm.restartCampaign(); onGameOverExit() },
            )
        } else if (vm.bankroll < 25 && vm.phase == DzPhase.BETTING && vm.totalAtRisk == 0) {
            if (vm.campaign) {
                CampaignGameOver(onDone = onGameOverExit)
            } else {
                // Busted at play testing: the buy-in slider comes straight
                // back up, and the seat refills at whatever it says.
                FreePlayBuyIn(
                    onDismiss = {},
                    onConfirm = { FreePlay.buyIn = it; vm.buyBackIn() },
                )
            }
        }
    }
}

@Composable
private fun TopBar(vm: DestroyerViewModel, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹ LOBBY",
            color = Steel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        Text("BANKROLL ", color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(
            formatMoney(vm.bankroll),
            color = Brass, fontSize = 14.sp, fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(10.dp))
        Text("AT RISK ", color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(
            vm.totalAtRisk.toString(),
            color = P.OffWhite, fontSize = 14.sp, fontWeight = FontWeight.Black,
        )
    }
}

/*
 * The battle map is the player's own art, and its geometry is measured off
 * it: the grid runs x 180..1221 and y 43..1031 of the 1254px square, letters
 * down the side naming the rows and numbers along the foot naming the
 * columns — so the letter die calls the row and the number die the column.
 */
private const val MAP_ART = 1254f
private const val MAP_X0 = 180f / MAP_ART
private const val MAP_X1 = 1221f / MAP_ART
private const val MAP_Y0 = 43f / MAP_ART
private const val MAP_Y1 = 1031f / MAP_ART

/*
 * Cell centres measured off the painted borders themselves: the art's grid
 * breathes a little (the last column and row run tighter), and a uniform
 * grid left every peg sitting up-left of its cell.
 */
private val COL_CX = floatArrayOf(271f, 449.5f, 625.5f, 802f, 978f, 1146.5f)
private val ROW_CY = floatArrayOf(123.5f, 293f, 463f, 633.5f, 801f, 959f)
private const val CELL_W_ART = 175f
private const val CELL_H_ART = 168f

/**
 * The player's own waters: the battle map with their ships lying on it, red
 * pegs where the dice hit, pale pegs in open sea, and the last shot ringed
 * in brass. A sunk ship burns red.
 */
@Composable
private fun Board(vm: DestroyerViewModel) {
    val hits = vm.hitCells.toSet()
    val misses = vm.missCells.toSet()

    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val w = maxWidth
        val cellW = w * (CELL_W_ART / MAP_ART)
        val cellH = w * (CELL_H_ART / MAP_ART)
        fun left(col: Int) = w * (COL_CX[col] / MAP_ART) - cellW / 2
        fun top(row: Int) = w * (ROW_CY[row] / MAP_ART) - cellH / 2

        // Open water under the grid: the map's cells are glass, and the sea
        // shows through them.
        Box(
            Modifier
                .offset(x = w * MAP_X0, y = w * MAP_Y0)
                .size(w * (MAP_X1 - MAP_X0), w * (MAP_Y1 - MAP_Y0))
                .background(
                    // The 1000 chip's own electric blue, sampled off its art.
                    Brush.verticalGradient(
                        listOf(Color(0xFF0230A0), Color(0xFF0350D0), Color(0xFF0464F0))
                    ),
                    RoundedCornerShape(6.dp),
                )
        )
        Image(
            painter = painterResource(R.drawable.battlemap),
            contentDescription = "Battle map",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        // The fleet, each hull lying across its cells. The art is drawn
        // lengthwise, so a ship standing in a column is turned on its side.
        // While the bet is open the player is the admiral: drag a hull to new
        // water, tap it to swing it round; a berth that will not take it
        // springs the hull back.
        val betting = vm.phase == DzPhase.BETTING
        val density = LocalDensity.current
        val cellWpx = with(density) { cellW.toPx() }
        val cellHpx = with(density) { cellH.toPx() }
        var thirdSeen = false
        vm.fleet.forEachIndexed { shipIndex, ship ->
            val res = when (ship.size) {
                4 -> R.drawable.fourslots
                2 -> R.drawable.twoslot
                else -> if (thirdSeen) R.drawable.second3slot
                else { thirdSeen = true; R.drawable.first3slot }
            }
            val rows = ship.cells.map { it / GRID }
            val cols = ship.cells.map { it % GRID }
            val horizontal = rows.distinct().size == 1
            val sunk = ship.cells.all { it in hits }
            // Spanned off the measured cells, so a long hull ends where its
            // last berth really is, not where a uniform grid says it should.
            val boxW = if (horizontal) left(cols.max()) + cellW - left(cols.min()) else cellW
            val boxH = if (horizontal) cellH else top(rows.max()) + cellH - top(rows.min())
            var dragPx by remember(ship) { mutableStateOf(Offset.Zero) }
            Box(
                Modifier
                    .offset(x = left(cols.min()), y = top(rows.min()))
                    .offset { IntOffset(dragPx.x.roundToInt(), dragPx.y.roundToInt()) }
                    .size(boxW, boxH)
                    .zIndex(if (dragPx != Offset.Zero) 2f else 1f)
                    .pointerInput(betting, shipIndex, ship) {
                        if (!betting) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var moved = false
                            var total = Offset.Zero
                            drag(down.id) { change ->
                                total += change.positionChange()
                                change.consume()
                                if (total.getDistance() > 10f) moved = true
                                dragPx = total
                            }
                            if (moved) {
                                val dRow = (total.y / cellHpx).roundToInt()
                                val dCol = (total.x / cellWpx).roundToInt()
                                vm.moveShip(shipIndex, rows.min() + dRow, cols.min() + dCol)
                                dragPx = Offset.Zero
                            } else {
                                dragPx = Offset.Zero
                                vm.rotateShip(shipIndex)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(res),
                    contentDescription = "${ship.size}-ship",
                    modifier = (
                        if (horizontal) Modifier.fillMaxSize()
                        else Modifier
                            .requiredSize(width = boxH, height = boxW)
                            .graphicsLayer { rotationZ = 90f }
                        ).padding(2.dp)
                        .alpha(if (sunk) 0.55f else 1f),
                    // Stretched to cover every cell of its berth: the 4-ship art is
                    // drawn 3:1, and fitted it only reached three of its four.
                    contentScale = ContentScale.FillBounds,
                )
                if (sunk) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                            .background(HitRed.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                            .border(1.5.dp, HitRed, RoundedCornerShape(8.dp))
                    )
                }
            }
        }

        // After first blood a ship tips its hand: every unstruck cell shows
        // what the next hit there is worth, and the last cell standing
        // wears the full sink pay.
        vm.fleet.forEach { ship ->
            val struck = ship.cells.count { it in hits }
            if (struck == 0 || struck == ship.size) return@forEach
            val nextPay = DestroyerRules.MILESTONES.getValue(ship.size)[struck + 1]
                ?: return@forEach
            ship.cells.filter { it !in hits }.forEach { cell ->
                Box(
                    Modifier
                        .offset(x = left(cell % GRID), y = top(cell / GRID))
                        .size(cellW, cellH)
                        .zIndex(3f),
                    contentAlignment = Alignment.Center,
                ) {
                    // The tag talks money, not odds: the stake times the rung
                    // the next hit reaches.
                    Text(
                        "$" + formatMoney((nextPay * vm.stake).toDouble()),
                        color = Brass, fontSize = 11.sp,
                        fontWeight = FontWeight.Black, lineHeight = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC0A1420))
                            .border(1.dp, Brass.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // The called coordinate lights its letter and number badges — and
        // its whole row and column wash gold, converging on the cell where
        // they cross. Two quiet washes; the target cell wears both.
        vm.lastRoll?.let { (r, c) ->
            Box(
                Modifier
                    .offset(x = left(0), y = top(r))
                    .size(left(GRID - 1) + cellW - left(0), cellH)
                    .zIndex(3f)
                    .background(Brass.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            )
            Box(
                Modifier
                    .offset(x = left(c), y = top(0))
                    .size(cellW, top(GRID - 1) + cellH - top(0))
                    .zIndex(3f)
                    .background(Brass.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            )
            val badge = w * (155f / MAP_ART)
            val glow = Modifier
                .size(badge)
                .zIndex(3f)
                .border(1.5.dp, Brass.copy(alpha = 0.75f), CircleShape)
                .background(Brass.copy(alpha = 0.10f), CircleShape)
            Box(
                Modifier
                    .offset(x = w * (93f / MAP_ART) - badge / 2, y = top(r) + cellH / 2 - badge / 2)
                    .then(glow)
            )
            Box(
                Modifier
                    .offset(x = left(c) + cellW / 2 - badge / 2, y = w * (1131f / MAP_ART) - badge / 2)
                    .then(glow)
            )
        }

        // Pegs cell by cell — floated above the hulls, or a red peg on a
        // ship would vanish under its own target. A hit is the classic red
        // peg; a miss is a little red X right where the washes converge.
        for (cell in 0 until DestroyerRules.CELLS) {
            val row = cell / GRID
            val col = cell % GRID
            if (cell in hits) {
                Box(
                    Modifier
                        .offset(x = left(col) + cellW / 2 - 7.dp, y = top(row) + cellH / 2 - 7.dp)
                        .size(14.dp)
                        .zIndex(3f)
                        .clip(CircleShape)
                        .background(HitRed)
                        .border(1.dp, Color(0x66000000), CircleShape)
                )
            } else if (cell in misses) {
                Box(
                    Modifier
                        .offset(x = left(col) + cellW / 2 - 12.dp, y = top(row) + cellH / 2 - 12.dp)
                        .size(24.dp)
                        .zIndex(3f)
                        .drawBehind {
                            val s = size.width
                            val pad = s * 0.18f
                            drawLine(
                                HitRed, Offset(pad, pad), Offset(s - pad, s - pad),
                                strokeWidth = s * 0.19f, cap = StrokeCap.Round,
                            )
                            drawLine(
                                HitRed, Offset(s - pad, pad), Offset(pad, s - pad),
                                strokeWidth = s * 0.19f, cap = StrokeCap.Round,
                            )
                        }
                )
            }
        }
    }
}

/** Shots left as shells in the rack, the last roll, and the way to the pays. */
@Composable
private fun StatusRow(vm: DestroyerViewModel, onShowPays: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("SHOTS ", color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp)
        repeat(DestroyerRules.LIVES) { i ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (i < vm.lives) Brass else Color(0x33FFFFFF))
            )
        }
        Spacer(Modifier.width(14.dp))
        val roll = vm.lastRoll
        Text(
            // Called the battleship way, lettered from the bottom row up.
            if (roll == null) "——" else "${'A' + (GRID - 1 - roll.first)}${roll.second + 1}",
            color = Steel, fontSize = 13.sp, fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(14.dp))
        // A button that looks like one: the dim text version went unnoticed.
        Text(
            "ⓘ PAYS",
            color = Brass, fontSize = 11.sp, fontWeight = FontWeight.Black,
            letterSpacing = 0.08.em,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, Brass.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
                .clickable(onClick = onShowPays)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun MessageLine(vm: DestroyerViewModel) {
    Text(
        vm.message,
        color = when {
            vm.phase == DzPhase.RESULT && vm.lastWin > vm.stake -> Brass
            vm.phase == DzPhase.RESULT && vm.lastWin == 0 -> HitRed
            else -> P.OffWhite.copy(alpha = 0.9f)
        },
        fontSize = 16.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ResultRows(vm: DestroyerViewModel) {
    if (vm.phase != DzPhase.RESULT || vm.results.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    vm.results.forEach { r ->
        Row(
            Modifier
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xD90A1420))
                .border(1.dp, Brass.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(r.label, color = P.OffWhite.copy(alpha = 0.9f), fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold)
            Text("${r.units}-for-1", color = Brass, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

/** The one bet, and a shuffle for players who want their own spread. */
@Composable
private fun BetRow(vm: DestroyerViewModel) {
    val betting = vm.phase == DzPhase.BETTING
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (betting) {
            Text(
                "SHUFFLE\nFLEET",
                color = SteelDim, fontSize = 10.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center, lineHeight = 12.sp, letterSpacing = 0.06.em,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = vm::shuffleFleet)
                    .padding(8.dp),
            )
        }
        Box(
            Modifier
                .size(56.dp)
                .then(
                    if (betting) Modifier.clickable(onClick = vm::addChip) else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            val amount = if (betting) vm.bet else vm.stake
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (amount > 0) Brass else Brass.copy(alpha = 0.4f), CircleShape)
            )
            if (amount > 0) PlacedBetChip(amount, size = 42.dp)
            else Text("BET", color = Brass.copy(alpha = 0.75f), fontSize = 10.sp,
                fontWeight = FontWeight.Black)
        }
        if (betting) Spacer(Modifier.width(52.dp))
    }
}

@Composable
private fun ChipRail(vm: DestroyerViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val racked = chipsFor(vm.bankroll, vm.limits)
        LaunchedEffect(racked) {
            if (racked.none { it.value == vm.selectedChip }) {
                vm.selectedChip = racked.last().value
            }
        }
        racked.forEach { chip ->
            CasinoChip(
                imageRes = chip.imageRes,
                contentDescription = "${chip.value} chip",
                selected = vm.selectedChip == chip.value,
                onClick = { vm.selectedChip = chip.value },
                selectedColor = Brass,
            )
        }
    }
}

@Composable
private fun Actions(vm: DestroyerViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (vm.phase) {
            DzPhase.BETTING -> {
                ActionButton("CLEAR", Steel, false, Modifier.weight(1f), vm::clearBet)
                ActionButton("DEAL", Brass, true, Modifier.weight(1.4f), vm::deal)
            }
            DzPhase.TARGETING -> {
                // No fire button: the dice in the tray are the trigger.
                Text(
                    "flick the dice to fire",
                    color = SteelDim,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    textAlign = TextAlign.Center,
                )
            }
            DzPhase.RESULT -> {
                ActionButton("NEW BET", Steel, false, Modifier.weight(1f)) { vm.nextHand(false) }
                ActionButton("REBET", Brass, true, Modifier.weight(1.4f)) { vm.nextHand(true) }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    color: Color,
    solid: Boolean,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    val live = onClick != null
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (solid) color.copy(alpha = if (live) 0.16f else 0.06f) else Color.Transparent)
            .border(1.5.dp, color.copy(alpha = if (live) 0.9f else 0.3f), RoundedCornerShape(999.dp))
            .then(if (live) Modifier.clickable(onClick = onClick!!) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color.copy(alpha = if (live) 1f else 0.4f),
            fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.1.em,
        )
    }
}

@Composable
private fun PayTable(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF203070C))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, Steel.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("DESTROYER PAYS", color = Brass, fontSize = 14.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.14.em)
            Spacer(Modifier.height(4.dp))
            PayRow("2-ship sunk", "3")
            PayRow("3-ship · 2 hits / sunk", "3 / 10")
            PayRow("4-ship · 2 / 3 hits / sunk", "3 / 4 / 40")
            Spacer(Modifier.height(6.dp))
            PayRow("2 ships down", "+51")
            PayRow("3 ships down", "+251")
            PayRow("FLEET DESTROYED", "+501")
            Spacer(Modifier.height(8.dp))
            Text(
                "All pays are for-1 — the stake buys the shots.\n" +
                    "Four shots; a fresh hit is free, twice-hit water is a miss.",
                color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp, lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun PayRow(label: String, pay: String) {
    Row(Modifier.width(250.dp)) {
        Text(label, color = P.OffWhite.copy(alpha = 0.88f), fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(pay, color = Brass, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

