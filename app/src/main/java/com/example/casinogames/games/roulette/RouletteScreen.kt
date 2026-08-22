package com.example.casinogames.games.roulette

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.casinogames.R
import com.example.casinogames.games.roulette.RouletteArt as A
import com.example.casinogames.games.roulette.RouletteEngine.DOUBLE_ZERO
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.formatMoney
import kotlin.math.floor

private val TableBlack = Color(0xFF080509)
private val PocketRed = Color(0xFFB3222E)
private val PocketBlack = Color(0xFF15121A)
private val ZeroGreen = Color(0xFF1D7A34)
private val NeonPurple = Color(0xFFB98CFF)
private val WinGreen = Color(0xFF57E06A)
/** The marker that takes whichever pocket stops under it. */
private val MarkerGold = Color(0xFFFFD24D)

private fun pocketColor(pocket: Int): Color = when {
    pocket == 0 || pocket == DOUBLE_ZERO -> ZeroGreen
    RouletteEngine.isRed(pocket) -> PocketRed
    else -> PocketBlack
}

/**
 * The table is one piece of art, measured in [RouletteArt]. Everything live —
 * the money, the spin belt, the chips and every tap target — is laid on the
 * marks the art was drawn around.
 */
@Composable
fun RouletteScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = onBack,
    vm: RouletteViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    Box(Modifier.fillMaxSize().background(TableBlack)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            // Fit the table whole, whichever edge runs out first.
            val artHeight = minOf(maxHeight, maxWidth * (A.H / A.W))
            val artWidth = artHeight * (A.W / A.H)
            // One art pixel, in dp.
            val k = artWidth.value / A.W
            Box(Modifier.align(Alignment.Center).width(artWidth).height(artHeight)) {
                Image(
                    painter = painterResource(R.drawable.roulette_felt),
                    contentDescription = "Roulette table",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                Money(vm, k)
                Belt(vm, k)
                Felt(vm, k)
                Outside(vm, k)
                Rack(vm, k)
                Buttons(vm, k)
                Message(vm, k)
                // The art paints the back arrow; this only catches the press.
                Box(Modifier.artBox(k, 0f, 0f, A.BACK_RIGHT, A.BACK_BOTTOM).tap(onBack))
            }
        }
        if (vm.campaign && vm.phase != RoulettePhase.SPINNING &&
            vm.bankroll < 25 && vm.totalStaked == 0
        ) {
            CampaignGameOver(onStartOver = { vm.buyBackIn(); onGameOverExit() })
        }
        if (vm.campaign && vm.phase != RoulettePhase.SPINNING &&
            vm.bankroll >= vm.goal && vm.totalStaked == 0
        ) {
            CampaignComplete(
                goal = vm.goal,
                nextGoal = vm.goal * 100,
                onGoBigger = vm::raiseGoal,
                onStartOver = { vm.restartCampaign(); onGameOverExit() },
            )
        }
    }
}

/** Places a box on the art's own coordinates. */
private fun Modifier.artBox(k: Float, x0: Float, y0: Float, x1: Float, y1: Float): Modifier =
    this.offset(x = (x0 * k).dp, y = (y0 * k).dp)
        .size(width = ((x1 - x0) * k).dp, height = ((y1 - y0) * k).dp)

/** A press with no ripple: the art already shows what is being pressed. */
@Composable
private fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

@Composable
private fun Money(vm: RouletteViewModel, k: Float) {
    // The art leaves this corner blank, labels and all: a figure that grew
    // would otherwise run into a painted word.
    listOf(
        A.BANKROLL_MID to "BANKROLL  ${formatMoney(vm.bankroll)}",
        A.STAKED_MID to "STAKED  ${formatMoney(vm.totalStaked.toDouble())}",
    ).forEach { (mid, text) ->
        Box(
            Modifier.artBox(k, 0f, mid - 22f, A.MONEY_RIGHT, mid + 22f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text,
                color = Color.White,
                fontSize = (26f * k).sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

/** Cells either side of the marker; enough to fill the belt and overhang it. */
private const val BeltHalf = 6
private const val BeltCellW = 92f
private const val BeltPointer = 24f

/**
 * The wheel unrolled, in the strip the table leaves above the zero row:
 * pockets ride past in their real order and the marker in the middle takes
 * whichever one stops under it.
 */
@Composable
private fun Belt(vm: RouletteViewModel, k: Float) {
    val travel = remember { Animatable(vm.reelStop.toFloat()) }
    LaunchedEffect(vm.spinId) {
        if (vm.spinId == 0) return@LaunchedEffect
        travel.animateTo(
            vm.reelStop.toFloat(),
            // Away hard, then a long die-out into the pocket.
            tween(SPIN_MILLIS, easing = CubicBezierEasing(0.08f, 0.82f, 0.16f, 1f)),
        )
    }
    val cellPx = with(LocalDensity.current) { (BeltCellW * k).dp.toPx() }
    val base = floor(travel.value).toInt()
    val frac = travel.value - base
    val cellTop = A.BELT_TOP + BeltPointer

    Box(
        Modifier.artBox(k, 0f, A.BELT_TOP, A.W, A.BELT_BOTTOM),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .artBox(k, 0f, 0f, A.W, A.BELT_BOTTOM - cellTop)
                .offset(y = (BeltPointer * k).dp)
                .clip(RoundedCornerShape((8f * k).dp))
                .background(Color(0x66000000)),
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
                for (i in -BeltHalf..BeltHalf) {
                    val pocket = RouletteEngine.WHEEL[
                        Math.floorMod(base + i, RouletteEngine.WHEEL.size)
                    ]
                    Box(
                        Modifier
                            .width((BeltCellW * k).dp)
                            .height(((A.BELT_BOTTOM - cellTop) * k).dp)
                            .padding(horizontal = (4f * k).dp)
                            .background(pocketColor(pocket), RoundedCornerShape((6f * k).dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            RouletteEngine.label(pocket),
                            color = Color.White,
                            fontSize = (34f * k).sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
        // The marker: a frame on the pocket under it and a pointer above.
        Box(
            Modifier
                .width((BeltCellW * k).dp)
                .height(((A.BELT_BOTTOM - A.BELT_TOP) * k).dp)
                .border((3f * k).dp, MarkerGold, RoundedCornerShape((8f * k).dp))
        )
        Canvas(
            Modifier
                .width((30f * k).dp)
                .height(((A.BELT_BOTTOM - A.BELT_TOP) * k).dp)
        ) {
            val w = size.width
            drawPath(
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(w / 2f, (BeltPointer * k).dp.toPx())
                    lineTo(0f, 0f)
                    lineTo(w, 0f)
                    close()
                },
                MarkerGold,
            )
        }
    }
}

/**
 * The number grid: one surface that maps a press to the nearest spot, and the
 * chips already placed drawn back over the same marks.
 */
@Composable
private fun Felt(vm: RouletteViewModel, k: Float) {
    val anchors = remember { mutableStateOf(mapOf<String, Anchor>()) }
    val gridBottom = A.GRID_TOP + A.ROWS * A.ROW_PITCH
    val cellW = (A.GRID_RIGHT - A.GRID_LEFT) / 3f

    Box(
        Modifier
            .artBox(k, A.GRID_LEFT, A.ZERO_TOP, A.GRID_RIGHT, gridBottom)
            .pointerInput(k) {
                detectTapGestures { tap ->
                    // Into the resolver's own space: the zero row is its first
                    // row, the twelve number rows follow at one row each.
                    val artX = tap.x / size.width * (A.GRID_RIGHT - A.GRID_LEFT)
                    val artY = A.ZERO_TOP + tap.y / size.height * (gridBottom - A.ZERO_TOP)
                    val y = if (artY < A.GRID_TOP) {
                        (artY - A.ZERO_TOP) / (A.ZERO_BOTTOM - A.ZERO_TOP) * A.ROW_PITCH
                    } else {
                        A.ROW_PITCH + (artY - A.GRID_TOP)
                    }
                    val hit = resolveTap(Offset(artX, y), cellW, A.ROW_PITCH)
                        ?: return@detectTapGestures
                    vm.addChip(hit.first, hit.second)
                    if (vm.bets.containsKey(hit.first)) {
                        anchors.value =
                            anchors.value.filterKeys { it in vm.bets } + (hit.first to hit.third)
                    }
                }
            },
    )
    anchors.value.forEach { (id, a) ->
        vm.bets[id]?.let { amount ->
            // Anchors come back in grid rows, the zero row counting as one.
            val x = A.GRID_LEFT + a.x * cellW
            val y = if (a.y <= 1f) {
                A.ZERO_TOP + a.y * (A.ZERO_BOTTOM - A.ZERO_TOP)
            } else {
                A.GRID_TOP + (a.y - 1f) * A.ROW_PITCH
            }
            val d = 46f
            Box(Modifier.artBox(k, x - d / 2, y - d / 2, x + d / 2, y + d / 2)) {
                PlacedBetChip(amount, size = (d * k).dp)
            }
        }
    }
}

/** Dozens down the side, the 2-to-1 row, and the even-money row. */
@Composable
private fun Outside(vm: RouletteViewModel, k: Float) {
    A.DOZEN_BANDS.forEachIndexed { i, (top, bottom) ->
        OutsideSpot(
            vm, k, "dz-$i", RouletteEngine.dozen(i),
            A.DOZENS_LEFT, top, A.DOZENS_RIGHT, bottom,
        )
    }
    (0..2).forEach { c ->
        OutsideSpot(
            vm, k, "col-$c", RouletteEngine.column(c),
            A.COLUMN_EDGES[c], A.COLUMN_BETS_TOP, A.COLUMN_EDGES[c + 1], A.COLUMN_BETS_BOTTOM,
        )
    }
    val even = listOf(
        "low" to RouletteEngine.LOW,
        "even" to RouletteEngine.EVEN,
        "red" to RouletteEngine.RED,
        "black" to RouletteEngine.BLACK,
        "odd" to RouletteEngine.ODD,
        "high" to RouletteEngine.HIGH,
    )
    even.forEachIndexed { i, (id, def) ->
        OutsideSpot(
            vm, k, id, def,
            A.EVEN_MONEY_EDGES[i], A.EVEN_MONEY_TOP, A.EVEN_MONEY_EDGES[i + 1], A.EVEN_MONEY_BOTTOM,
        )
    }
}

@Composable
private fun OutsideSpot(
    vm: RouletteViewModel,
    k: Float,
    id: String,
    def: RouletteEngine.Bet,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
) {
    Box(
        Modifier.artBox(k, x0, y0, x1, y1).tap { vm.addChip(id, def) },
        contentAlignment = Alignment.Center,
    ) {
        vm.bets[id]?.let { PlacedBetChip(it, size = (46f * k).dp) }
    }
}

/** The rack is painted; this rings whichever chip is chosen and takes taps. */
@Composable
private fun Rack(vm: RouletteViewModel, k: Float) {
    chipsFor(vm.bankroll).forEachIndexed { i, chip ->
        val mid = A.CHIP_FIRST_MID + i * A.CHIP_PITCH
        val r = A.CHIP_DIAMETER / 2f
        Box(
            Modifier
                .artBox(k, mid - r, A.CHIPS_TOP, mid + r, A.CHIPS_BOTTOM)
                .tap { vm.selectedChip = chip.value }
        ) {
            if (vm.selectedChip == chip.value) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border((4f * k).dp, MarkerGold, CircleShape)
                )
            }
        }
    }
}

/** Undo, spin and rebet, over the buttons the art paints. */
@Composable
private fun Buttons(vm: RouletteViewModel, k: Float) {
    val actions = listOf<() -> Unit>(vm::undoChip, vm::spin, vm::rebet)
    A.BUTTON_EDGES.forEachIndexed { i, (x0, x1) ->
        Box(
            Modifier
                .artBox(k, x0, A.BUTTONS_TOP, x1, A.BUTTONS_BOTTOM)
                .tap { if (vm.phase != RoulettePhase.SPINNING) actions[i]() }
        )
    }
}

/** What the table is saying, over the felt while it is not being bet on. */
@Composable
private fun Message(vm: RouletteViewModel, k: Float) {
    if (vm.phase == RoulettePhase.BETTING && vm.message.isBlank()) return
    if (vm.phase == RoulettePhase.BETTING) return
    val net = vm.lastWin - vm.totalStaked
    Box(
        Modifier.artBox(k, A.GRID_LEFT, A.GRID_TOP + 10f, A.GRID_RIGHT, A.GRID_TOP + 90f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            vm.message,
            fontSize = (30f * k).sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = when {
                vm.phase == RoulettePhase.RESULT && net > 0 -> WinGreen
                vm.phase == RoulettePhase.RESULT && vm.lastWin <= 0.0 -> Color(0xFFFF4D4D)
                else -> Color.White
            },
            modifier = Modifier
                .background(Color(0xD9000000), RoundedCornerShape((999f * k).dp))
                .padding(horizontal = (24f * k).dp, vertical = (8f * k).dp),
        )
    }
}

private data class Anchor(val x: Float, val y: Float)

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
