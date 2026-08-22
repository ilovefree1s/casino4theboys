package com.example.casinogames.games.roulette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.floor
import com.example.casinogames.games.roulette.RouletteEngine.DOUBLE_ZERO
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
import com.example.casinogames.ui.common.CasinoChip
import com.example.casinogames.ui.common.OutlinedText
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P

/** The classic colours: green baize, red and black pockets, green zeroes. */
private val Baize = Color(0xFF0E3B1A)
private val BaizeDeep = Color(0xFF0A2B13)
private val PocketRed = Color(0xFFB3222E)
private val PocketBlack = Color(0xFF15121A)
private val ZeroGreen = Color(0xFF1D7A34)
private val Line = Color(0xB3F5F1E8)
private val WinGreen = Color(0xFF57E06A)
/** The marker that takes whichever pocket stops under it. */
private val MarkerGold = Color(0xFFFFD24D)

@Composable
fun RouletteScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = onBack,
    vm: RouletteViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    Box(Modifier.fillMaxSize().background(BaizeDeep)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 10.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onBack, vm)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x26FFFFFF)))
            Spacer(Modifier.height(4.dp))
            Text(
                if (vm.campaign) "CAMPAIGN · GOAL \$${formatMoney(vm.goal)}"
                else "AMERICAN ROULETTE · 0 AND 00",
                fontSize = 10.sp,
                letterSpacing = 0.24.em,
                color = if (vm.campaign) Color(0xCCFFD24D) else Color(0x8CFFFFFF),
            )
            Spacer(Modifier.height(4.dp))
            HistoryRail(vm)
            Reel(vm)
            BallAndMessage(vm)
            Spacer(Modifier.height(6.dp))
            Felt(vm, Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            if (vm.phase != RoulettePhase.SPINNING) ChipRack(vm)
            Spacer(Modifier.height(8.dp))
            ActionButtons(vm)
        }
        if (vm.campaign && vm.phase != RoulettePhase.SPINNING &&
            vm.bankroll < 25 && vm.totalStaked == 0
        ) {
            CampaignGameOver(onStartOver = {
                vm.buyBackIn()
                onGameOverExit()
            })
        }
        if (vm.campaign && vm.phase != RoulettePhase.SPINNING &&
            vm.bankroll >= vm.goal && vm.totalStaked == 0
        ) {
            CampaignComplete(
                goal = vm.goal,
                nextGoal = vm.goal * 100,
                onGoBigger = vm::raiseGoal,
                onStartOver = {
                    vm.restartCampaign()
                    onGameOverExit()
                },
            )
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, vm: RouletteViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹ LOBBY",
            color = P.OffWhite.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.1.em,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "BANKROLL ",
                    fontSize = 9.sp, letterSpacing = 0.12.em,
                    color = P.OffWhite.copy(alpha = 0.6f),
                )
                OutlinedText(
                    formatMoney(vm.bankroll),
                    fontSize = 14.sp, color = Color(0xFFF2E28A), outlineWidth = 1.dp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "STAKED ",
                    fontSize = 9.sp, letterSpacing = 0.12.em,
                    color = P.OffWhite.copy(alpha = 0.6f),
                )
                OutlinedText(
                    formatMoney(vm.totalStaked.toDouble()),
                    fontSize = 14.sp, color = P.OffWhite, outlineWidth = 1.dp,
                )
            }
        }
    }
}

@Composable
private fun pocketColor(pocket: Int): Color = when {
    pocket == 0 || pocket == DOUBLE_ZERO -> ZeroGreen
    RouletteEngine.isRed(pocket) -> PocketRed
    else -> PocketBlack
}

@Composable
private fun HistoryRail(vm: RouletteViewModel) {
    if (vm.history.isEmpty()) { Spacer(Modifier.height(20.dp)); return }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        vm.history.forEach { p ->
            Box(
                Modifier
                    .size(20.dp)
                    .background(pocketColor(p), CircleShape)
                    .border(1.dp, Color(0x66FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    RouletteEngine.label(p),
                    color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

/** Cells either side of the marker; enough to fill the belt and overhang it. */
private const val ReelHalf = 6
private val ReelCellWidth = 46.dp

/**
 * The wheel unrolled: pockets ride past in their real order and the marker in
 * the middle takes whichever one stops under it. The view model says where the
 * belt must come to rest; the run itself lives here.
 */
@Composable
private fun Reel(vm: RouletteViewModel) {
    val travel = remember { Animatable(vm.reelStop.toFloat()) }
    LaunchedEffect(vm.spinId) {
        if (vm.spinId == 0) return@LaunchedEffect
        travel.animateTo(
            vm.reelStop.toFloat(),
            // Away hard, then a long die-out into the pocket.
            tween(SPIN_MILLIS, easing = CubicBezierEasing(0.08f, 0.82f, 0.16f, 1f)),
        )
    }
    val cellPx = with(LocalDensity.current) { ReelCellWidth.toPx() }
    val base = floor(travel.value).toInt()
    val frac = travel.value - base

    Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x59000000)),
            contentAlignment = Alignment.Center,
        ) {
            // Measured unbounded: the belt is wider than the screen, and if it
            // were capped to fit, its middle cell would not be the one the
            // marker sits over.
            Row(
                Modifier
                    .wrapContentWidth(unbounded = true)
                    .graphicsLayer { translationX = -frac * cellPx }
            ) {
                for (i in -ReelHalf..ReelHalf) {
                    val pocket = RouletteEngine.WHEEL[
                        Math.floorMod(base + i, RouletteEngine.WHEEL.size)
                    ]
                    Box(
                        Modifier
                            .width(ReelCellWidth)
                            .height(42.dp)
                            .padding(horizontal = 2.dp)
                            .background(pocketColor(pocket), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            RouletteEngine.label(pocket),
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
        // The marker: a frame on the pocket under it and a pointer above.
        Box(
            Modifier
                .width(ReelCellWidth)
                .height(52.dp)
                .border(2.dp, MarkerGold, RoundedCornerShape(6.dp))
        )
        Canvas(Modifier.size(width = 16.dp, height = 52.dp)) {
            val w = size.width
            drawPath(
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(w / 2f, 9.dp.toPx())
                    lineTo(0f, 0f)
                    lineTo(w, 0f)
                    close()
                },
                MarkerGold,
            )
        }
    }
}

@Composable
private fun BallAndMessage(vm: RouletteViewModel) {
    Row(
        Modifier.height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val net = vm.lastWin - vm.totalStaked
        Text(
            vm.message,
            fontSize = 15.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = when {
                vm.phase == RoulettePhase.RESULT && net > 0 -> WinGreen
                vm.phase == RoulettePhase.RESULT && vm.lastWin <= 0.0 -> Color(0xFFFF4D4D)
                else -> P.OffWhite.copy(alpha = 0.92f)
            },
        )
    }
}

/**
 * The number grid: the zero row, twelve rows of three, and every bet between
 * them. One tap surface maps presses to the nearest spot — a cell's middle is
 * a straight-up, a shared edge is a split, a crossing is a corner, the left
 * rail takes streets and six-lines — the way chips find their place on a real
 * felt. Placed chips are drawn back over the same geometry.
 */
@Composable
private fun Felt(vm: RouletteViewModel, modifier: Modifier = Modifier) {
    // The grid takes whatever height the screen can give it, never more:
    // sized off the space left after the outside rows, not off the width.
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            NumberGrid(vm, Modifier.weight(3f).fillMaxSize())
            Spacer(Modifier.width(6.dp))
            // Dozens ride beside the grid, each level with its third.
            Column(Modifier.weight(0.55f).fillMaxSize()) {
                (0..2).forEach { d ->
                    OutsideCell(
                        vm, "dz-$d", RouletteEngine.dozen(d),
                        listOf("1st", "2nd", "3rd")[d] + " 12",
                        Modifier.fillMaxWidth().weight(1f),
                    )
                    if (d < 2) Spacer(Modifier.height(3.dp))
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        // The three column bets under their columns, then the even-money row.
        Row(Modifier.fillMaxWidth()) {
            Row(Modifier.weight(3f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0..2).forEach { c ->
                    OutsideCell(
                        vm, "col-$c", RouletteEngine.column(c), "2 TO 1",
                        Modifier.weight(1f).height(30.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Spacer(Modifier.weight(0.55f))
        }
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            OutsideCell(vm, "low", RouletteEngine.LOW, "1-18", Modifier.weight(1f).height(34.dp))
            OutsideCell(vm, "even", RouletteEngine.EVEN, "EVEN", Modifier.weight(1f).height(34.dp))
            OutsideCell(
                vm, "red", RouletteEngine.RED, "RED",
                Modifier.weight(1f).height(34.dp), fill = PocketRed,
            )
            OutsideCell(
                vm, "black", RouletteEngine.BLACK, "BLACK",
                Modifier.weight(1f).height(34.dp), fill = PocketBlack,
            )
            OutsideCell(vm, "odd", RouletteEngine.ODD, "ODD", Modifier.weight(1f).height(34.dp))
            OutsideCell(vm, "high", RouletteEngine.HIGH, "19-36", Modifier.weight(1f).height(34.dp))
        }
    }
}

@Composable
private fun OutsideCell(
    vm: RouletteViewModel,
    id: String,
    def: RouletteEngine.Bet,
    label: String,
    modifier: Modifier,
    fill: Color = Baize,
) {
    Box(
        modifier
            .background(fill, RoundedCornerShape(4.dp))
            .border(1.dp, Line, RoundedCornerShape(4.dp))
            .clickable { vm.addChip(id, def) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black,
            letterSpacing = 0.04.em, textAlign = TextAlign.Center,
        )
        vm.bets[id]?.let { PlacedBetChip(it, size = 24.dp) }
    }
}

/** Where a chip sits on the grid, in cell units — (column, row) of its anchor. */
private data class Anchor(val x: Float, val y: Float)

@Composable
private fun NumberGrid(vm: RouletteViewModel, modifier: Modifier = Modifier) {
    var sizePx by remember { mutableStateOf(Offset.Zero) }
    // Chips keyed by spot id, anchored where the tap resolved. Entries whose
    // bets are gone simply stop drawing — the amount lookup comes up empty.
    val anchors = remember { mutableStateOf(mapOf<String, Anchor>()) }
    val density = LocalDensity.current

    Box(
        modifier
            .background(Baize, RoundedCornerShape(6.dp))
            .onSizeChanged { sizePx = Offset(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val cellW = size.width / 3f
                    val cellH = size.height / 13f
                    val hit = resolveTap(tap, cellW, cellH) ?: return@detectTapGestures
                    vm.addChip(hit.first, hit.second)
                    if (vm.bets.containsKey(hit.first)) {
                        anchors.value =
                            anchors.value.filterKeys { it in vm.bets } + (hit.first to hit.third)
                    }
                }
            },
    ) {
        GridLines()
        val cellW = with(density) { (sizePx.x / 3f).toDp() }
        val cellH = with(density) { (sizePx.y / 13f).toDp() }
        anchors.value.forEach { (id, a) ->
            vm.bets[id]?.let { amount ->
                Box(
                    Modifier
                        .offset(x = cellW * a.x - 12.dp, y = cellH * a.y - 12.dp)
                        .size(24.dp),
                ) {
                    PlacedBetChip(amount, size = 24.dp)
                }
            }
        }
    }
}

/** The painted grid: zero row on top, then 1-36 in twelve rows of three. */
@Composable
private fun GridLines() {
    Column(Modifier.fillMaxSize().padding(1.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            NumberCell(0, Modifier.weight(1.5f))
            NumberCell(DOUBLE_ZERO, Modifier.weight(1.5f))
        }
        (0 until 12).forEach { r ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                (0 until 3).forEach { c ->
                    NumberCell(r * 3 + c + 1, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RowScope.NumberCell(pocket: Int, modifier: Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .padding(0.75.dp)
            .background(pocketColor(pocket), RoundedCornerShape(2.dp))
            .border(0.75.dp, Line.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            RouletteEngine.label(pocket),
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black,
        )
    }
}

/**
 * Turns a press into (spot id, bet, chip anchor). Distances are measured to
 * the nearest grid line: close to a crossing is a corner, close to one line is
 * a split, the left rail is streets and six-lines, anywhere else the cell.
 */
private fun resolveTap(
    tap: Offset,
    cellW: Float,
    cellH: Float,
): Triple<String, RouletteEngine.Bet, Anchor>? {
    val e = RouletteEngine
    // A share of each side, not of the shorter one: cells are far wider
    // than they are tall, and one figure for both turned the top and bottom
    // quarter of every pocket into a split.
    val snapX = cellW * 0.18f
    // Tighter still down the short side: a row is barely a finger tall, so the
    // seam has to be aimed at rather than fallen into.
    val snapY = cellH * 0.15f

    // The zero row answers by itself: 0, 00, their split, and the top line.
    if (tap.y < cellH + snapY) {
        val onBoundary = tap.y > cellH - snapY
        return when {
            !onBoundary && kotlin.math.abs(tap.x - cellW * 1.5f) < snapX ->
                Triple("sp-0-00", e.split(0, DOUBLE_ZERO), Anchor(1.5f, 0.5f))
            !onBoundary && tap.x < cellW * 1.5f ->
                Triple("s-0", e.straight(0), Anchor(0.75f, 0.5f))
            !onBoundary ->
                Triple("s-00", e.straight(DOUBLE_ZERO), Anchor(2.25f, 0.5f))
            // On the seam under the zeroes.
            tap.x < snapX -> Triple("tl", e.TOP_LINE, Anchor(0f, 1f))
            tap.x < cellW -> Triple("sp-0-1", e.split(0, 1), Anchor(0.5f, 1f))
            tap.x < cellW * 2f ->
                Triple("st-z", e.street(setOf(0, DOUBLE_ZERO, 2)), Anchor(1.5f, 1f))
            else -> Triple("sp-00-3", e.split(DOUBLE_ZERO, 3), Anchor(2.5f, 1f))
        }
    }

    // Row and column of the tapped cell, 0-based within the 12x3 block.
    val r = ((tap.y - cellH) / cellH).toInt().coerceIn(0, 11)
    val c = (tap.x / cellW).toInt().coerceIn(0, 2)
    val n = r * 3 + c + 1
    val nearLeft = tap.x % cellW < snapX && c > 0
    val nearRight = tap.x % cellW > cellW - snapX && c < 2
    val yInRow = (tap.y - cellH) % cellH
    val nearTop = yInRow < snapY
    val nearBottom = yInRow > cellH - snapY && r < 11
    val gridY = (tap.y - cellH) / cellH + 1f   // in grid rows, zero row included

    // The left rail: a street on the row, a six-line on the seam between rows.
    if (tap.x < snapX) {
        return if (nearBottom || (nearTop && r > 0)) {
            val top = if (nearTop) r - 1 else r
            val six = ((top * 3 + 1)..(top * 3 + 6)).toSet()
            Triple("sl-$top", e.sixLine(six), Anchor(0f, top + 2f))
        } else {
            val street = setOf(r * 3 + 1, r * 3 + 2, r * 3 + 3)
            Triple("st-$r", e.street(street), Anchor(0f, r + 1.5f))
        }
    }

    val vertical = nearLeft || nearRight
    val horizontal = nearTop && r > 0 || nearBottom
    return when {
        vertical && horizontal -> {
            // A corner: name it by its top-left pocket.
            val topRow = if (nearTop) r - 1 else r
            val leftCol = if (nearLeft) c - 1 else c
            val tl = topRow * 3 + leftCol + 1
            Triple(
                "c-$tl",
                e.corner(setOf(tl, tl + 1, tl + 3, tl + 4)),
                Anchor(leftCol + 1f, topRow + 2f),
            )
        }
        vertical -> {
            val left = if (nearLeft) n - 1 else n
            Triple(
                "sp-$left-${left + 1}",
                e.split(left, left + 1),
                Anchor((if (nearLeft) c else c + 1).toFloat(), r + 1.5f),
            )
        }
        horizontal -> {
            val upper = if (nearTop) n - 3 else n
            Triple(
                "sp-$upper-${upper + 3}",
                e.split(upper, upper + 3),
                Anchor(c + 0.5f, (if (nearTop) r else r + 1) + 1f),
            )
        }
        else -> Triple("s-$n", e.straight(n), Anchor(c + 0.5f, r + 1.5f))
    }
}

@Composable
private fun ChipRack(vm: RouletteViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chipsFor(vm.bankroll).forEach { chip ->
            CasinoChip(
                imageRes = chip.imageRes,
                contentDescription = "${chip.value} chip",
                selected = vm.selectedChip == chip.value,
                onClick = { vm.selectedChip = chip.value },
                selectedColor = Color(0xFFF2E28A),
            )
        }
    }
}

@Composable
private fun ActionButtons(vm: RouletteViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(46.dp),
    ) {
        when (vm.phase) {
            RoulettePhase.BETTING -> {
                PillButton("UNDO", Modifier.weight(1f), onClick = vm::undoChip)
                PillButton("SPIN", Modifier.weight(1.4f), solid = true, onClick = vm::spin)
                if (vm.bets.isEmpty()) {
                    PillButton("REBET", Modifier.weight(1f), onClick = vm::rebet)
                } else {
                    PillButton("CLEAR", Modifier.weight(1f), onClick = vm::clearBets)
                }
                if (!vm.campaign && vm.bankroll < 25 && vm.totalStaked == 0) {
                    PillButton("BUY IN", Modifier.weight(1f), onClick = vm::buyBackIn)
                }
            }
            RoulettePhase.RESULT -> {
                PillButton("NEW BETS", Modifier.weight(1f), solid = true, onClick = vm::nextSpin)
            }
            RoulettePhase.SPINNING -> {
                Spacer(Modifier.weight(1f))
                Text(
                    "NO MORE BETS",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.15.em,
                    color = P.OffWhite.copy(alpha = 0.8f),
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (solid) Color(0xFFF2E28A) else Color(0x22FFFFFF))
            .border(
                1.5.dp,
                if (solid) Color(0xFFF2E28A) else Color(0x66F5F1E8),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (solid) Color(0xFF1A1508) else P.OffWhite,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            letterSpacing = 0.08.em,
        )
    }
}
