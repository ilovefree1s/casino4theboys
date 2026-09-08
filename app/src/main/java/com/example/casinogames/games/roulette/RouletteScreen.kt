package com.example.casinogames.games.roulette

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import com.example.casinogames.R
import com.example.casinogames.games.roulette.RouletteArt as A
import com.example.casinogames.games.roulette.RouletteEngine.DOUBLE_ZERO
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.CASINO_CHIPS
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.ui.common.FreePlayBuyIn
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
                Badge(k)
                Money(vm, k)
                Belt(vm, k)
                // Chip anchors live here so a stack dragged between the felt
                // and the outside bands keeps one shared record of where it
                // sits.
                val anchors = remember { mutableStateOf(mapOf<String, Anchor>()) }
                Felt(vm, k, anchors)
                Outside(vm, k, anchors)
                Rack(vm, k)
                Buttons(vm, k)
                Message(vm, k)
                // The art paints the back arrow; this only catches the press.
                Box(Modifier.artBox(k, 0f, 0f, A.BACK_RIGHT, A.BACK_BOTTOM).tap(onBack))
            }
        }
        // A finished round still has its chips showing, but they are spent
        // and settled — waiting for them to clear left a broke player looking
        // at a table that answered nothing they pressed.
        val settled = vm.phase == RoulettePhase.RESULT ||
            (vm.phase == RoulettePhase.BETTING && vm.totalStaked == 0)
        if (settled && vm.bankroll < 25) {
            if (vm.campaign) {
                CampaignGameOver(onDone = onGameOverExit)
            } else {
                FreePlayBuyIn(
                    onDismiss = {},
                    onConfirm = { FreePlay.buyIn = it; vm.buyBackIn() },
                )
            }
        } else if (vm.campaign && settled && vm.bankroll >= vm.goal) {
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

/**
 * Free play has no campaign to lose, so a bust is just a refill — but it
 * still has to be offered, or the table sits there refusing every press.
 */

/** The house badge, clipped round: the art it came on has black corners. */
@Composable
private fun Badge(k: Float) {
    val r = A.LOGO_SIZE / 2f
    Image(
        painter = painterResource(R.drawable.fourtheboys_spot),
        contentDescription = "4 The Boys",
        modifier = Modifier
            .artBox(
                k,
                A.LOGO_MID_X - r, A.LOGO_MID_Y - r,
                A.LOGO_MID_X + r, A.LOGO_MID_Y + r,
            )
            .clip(CircleShape),
        contentScale = ContentScale.Fit,
    )
}

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
private const val BeltCellW = 96f
/** How far the marker's pointer bites down into the strip. */
private const val BeltPointer = 22f

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
    val strip = A.BELT_BOTTOM - A.BELT_TOP
    val corner = RoundedCornerShape((10f * k).dp)

    Box(
        Modifier
            .artBox(k, A.BELT_LEFT, A.BELT_TOP, A.BELT_RIGHT, A.BELT_BOTTOM)
            .clip(corner)
            .background(Color(0xCC05030A)),
        contentAlignment = Alignment.Center,
    ) {
        // Measured unbounded: the belt is wider than the strip, and if it were
        // capped to fit, its middle cell would not be the one the marker sits
        // over.
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
                        .height((strip * k).dp)
                        .padding(horizontal = (3f * k).dp, vertical = (6f * k).dp)
                        .background(pocketColor(pocket), RoundedCornerShape((8f * k).dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        RouletteEngine.label(pocket),
                        color = Color.White,
                        fontSize = (46f * k).sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }
        // Pockets run in and out of the dark rather than being cut off square.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to TableBlack,
                    0.11f to Color.Transparent,
                    0.89f to Color.Transparent,
                    1f to TableBlack,
                )
            )
        )
        // The rail the rest of the table is drawn with, over the fade.
        Box(Modifier.fillMaxSize().border((2f * k).dp, NeonPurple.copy(alpha = 0.55f), corner))
        // The marker: a frame on the pocket under it and a pointer biting in.
        Box(
            Modifier
                .width((BeltCellW * k).dp)
                .height((strip * k).dp)
                .padding(vertical = (3f * k).dp)
                .border((3f * k).dp, MarkerGold, RoundedCornerShape((8f * k).dp))
        )
        Canvas(
            Modifier
                .width((34f * k).dp)
                .height((strip * k).dp)
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
private fun Felt(
    vm: RouletteViewModel,
    k: Float,
    anchors: androidx.compose.runtime.MutableState<Map<String, Anchor>>,
) {
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
            var dragPx by remember(id) { mutableStateOf(Offset.Zero) }
            val density = LocalDensity.current.density
            Box(
                Modifier
                    .artBox(k, x - d / 2, y - d / 2, x + d / 2, y + d / 2)
                    .offset { IntOffset(dragPx.x.roundToInt(), dragPx.y.roundToInt()) }
                    .zIndex(if (dragPx != Offset.Zero) 5f else 1f)
                    .chipDrag(vm, k, density, id, x, y, anchors) { dragPx = it }
            ) {
                PlacedBetChip(amount, size = (d * k).dp)
            }
        }
    }
}

/**
 * The spot under a dropped chip, resolved the way a tap would be: the grid
 * hands the point to the tap resolver, the outside bands answer by rect.
 */
private fun resolveDropSpot(
    artX: Float,
    artY: Float,
): Triple<String, RouletteEngine.Bet, Anchor?>? {
    val gridBottom = A.GRID_TOP + A.ROWS * A.ROW_PITCH
    val cellW = (A.GRID_RIGHT - A.GRID_LEFT) / 3f
    if (artX >= A.GRID_LEFT && artX <= A.GRID_RIGHT &&
        artY >= A.ZERO_TOP && artY <= gridBottom
    ) {
        val x = artX - A.GRID_LEFT
        val y = if (artY < A.GRID_TOP) {
            (artY - A.ZERO_TOP) / (A.ZERO_BOTTOM - A.ZERO_TOP) * A.ROW_PITCH
        } else {
            A.ROW_PITCH + (artY - A.GRID_TOP)
        }
        val hit = resolveTap(Offset(x, y), cellW, A.ROW_PITCH) ?: return null
        return Triple(hit.first, hit.second, hit.third)
    }
    A.DOZEN_BANDS.forEachIndexed { i, (top, bottom) ->
        if (artX >= A.DOZENS_LEFT && artX <= A.DOZENS_RIGHT && artY >= top && artY <= bottom) {
            return Triple("dz-$i", RouletteEngine.dozen(i), null)
        }
    }
    for (c in 0..2) {
        if (artX >= A.COLUMN_EDGES[c] && artX <= A.COLUMN_EDGES[c + 1] &&
            artY >= A.COLUMN_BETS_TOP && artY <= A.COLUMN_BETS_BOTTOM
        ) {
            return Triple("col-$c", RouletteEngine.column(c), null)
        }
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
        if (artX >= A.EVEN_MONEY_EDGES[i] && artX <= A.EVEN_MONEY_EDGES[i + 1] &&
            artY >= A.EVEN_MONEY_TOP && artY <= A.EVEN_MONEY_BOTTOM
        ) {
            return Triple(id, def, null)
        }
    }
    return null
}

/** Drag a placed stack to another spot; the drop lands the way a tap would. */
private fun Modifier.chipDrag(
    vm: RouletteViewModel,
    k: Float,
    density: Float,
    id: String,
    startX: Float,
    startY: Float,
    anchors: androidx.compose.runtime.MutableState<Map<String, Anchor>>,
    onDrag: (Offset) -> Unit,
): Modifier = this.pointerInput(id, k) {
    awaitEachGesture {
        val down = awaitFirstDown()
        if (vm.phase != RoulettePhase.BETTING) return@awaitEachGesture
        var total = Offset.Zero
        drag(down.id) { change ->
            total += change.positionChange()
            change.consume()
            onDrag(total)
        }
        onDrag(Offset.Zero)
        if (total.getDistance() < 14f) return@awaitEachGesture
        val artX = startX + total.x / (k * density)
        val artY = startY + total.y / (k * density)
        val hit = resolveDropSpot(artX, artY) ?: return@awaitEachGesture
        if (hit.first == id) return@awaitEachGesture
        vm.moveChip(id, hit.first, hit.second)
        val third = hit.third
        anchors.value = if (third != null && vm.bets.containsKey(hit.first)) {
            anchors.value.filterKeys { it in vm.bets } + (hit.first to third)
        } else {
            anchors.value.filterKeys { it in vm.bets }
        }
    }
}

/** Dozens down the side, the 2-to-1 row, and the even-money row. */
@Composable
private fun Outside(
    vm: RouletteViewModel,
    k: Float,
    anchors: androidx.compose.runtime.MutableState<Map<String, Anchor>>,
) {
    A.DOZEN_BANDS.forEachIndexed { i, (top, bottom) ->
        OutsideSpot(
            vm, k, anchors, "dz-$i", RouletteEngine.dozen(i),
            A.DOZENS_LEFT, top, A.DOZENS_RIGHT, bottom,
        )
    }
    (0..2).forEach { c ->
        OutsideSpot(
            vm, k, anchors, "col-$c", RouletteEngine.column(c),
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
            vm, k, anchors, id, def,
            A.EVEN_MONEY_EDGES[i], A.EVEN_MONEY_TOP, A.EVEN_MONEY_EDGES[i + 1], A.EVEN_MONEY_BOTTOM,
        )
    }
}

@Composable
private fun OutsideSpot(
    vm: RouletteViewModel,
    k: Float,
    anchors: androidx.compose.runtime.MutableState<Map<String, Anchor>>,
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
        vm.bets[id]?.let { amount ->
            var dragPx by remember(id) { mutableStateOf(Offset.Zero) }
            val density = LocalDensity.current.density
            Box(
                Modifier
                    .offset { IntOffset(dragPx.x.roundToInt(), dragPx.y.roundToInt()) }
                    .zIndex(if (dragPx != Offset.Zero) 5f else 1f)
                    .chipDrag(
                        vm, k, density, id,
                        (x0 + x1) / 2f, (y0 + y1) / 2f, anchors,
                    ) { dragPx = it },
            ) {
                PlacedBetChip(amount, size = (46f * k).dp)
            }
        }
    }
}

/** The rack is painted; this rings whichever chip is chosen and takes taps. */
@Composable
private fun Rack(vm: RouletteViewModel, k: Float) {
    // The five chips are painted into the felt, so a room that will not take
    // one cannot simply have it left off the rack — it gets covered over
    // instead, and stops answering.
    val rack = chipsFor(vm.bankroll, vm.limits)
    val racked = rack.map { it.value }.toSet()
    // A chip picked in a richer room must not follow the player down.
    LaunchedEffect(racked) {
        if (vm.selectedChip !in racked) vm.selectedChip = rack.last().value
    }
    CASINO_CHIPS.forEachIndexed { i, chip ->
        val mid = A.CHIP_FIRST_MID + i * A.CHIP_PITCH
        val r = A.CHIP_DIAMETER / 2f
        val out = chip.value !in racked
        Box(
            Modifier
                .artBox(k, mid - r, A.CHIPS_TOP, mid + r, A.CHIPS_BOTTOM)
                .tap { if (!out) vm.selectedChip = chip.value }
        ) {
            when {
                out -> Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xCC05030A))
                )
                vm.selectedChip == chip.value -> Box(
                    Modifier
                        .fillMaxSize()
                        .border((4f * k).dp, MarkerGold, CircleShape)
                )
            }
        }
    }
}

/**
 * Undo, spin and rebet. The art leaves this band bare so the three can go
 * dead while the wheel is running rather than sitting there looking live.
 */
@Composable
private fun Buttons(vm: RouletteViewModel, k: Float) {
    val live = vm.phase != RoulettePhase.SPINNING
    val actions = listOf<() -> Unit>(vm::undoChip, vm::spin, vm::rebet)
    val labels = listOf("UNDO", "SPIN", "REBET")
    A.BUTTON_EDGES.forEachIndexed { i, (x0, x1) ->
        val spin = i == 1
        val pill = RoundedCornerShape(50)
        Box(
            Modifier
                .artBox(k, x0, A.BUTTONS_TOP, x1, A.BUTTONS_BOTTOM)
                .alpha(if (live) 1f else 0.4f)
                .clip(pill)
                .background(
                    if (spin) {
                        Brush.verticalGradient(
                            listOf(Color(0xFF6B32B8), Color(0xFF32135F)),
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color(0xCC170E24), Color(0xCC0B0713)))
                    }
                )
                .border(
                    ((if (spin) 3.5f else 2.5f) * k).dp,
                    if (spin) Color(0xFFD9BBFF) else NeonPurple.copy(alpha = 0.7f),
                    pill,
                )
                .tap { if (live) actions[i]() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                labels[i],
                color = if (spin) Color.White else Color(0xFFE4D7FF),
                fontSize = ((if (spin) 44f else 36f) * k).sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (2f * k).sp,
            )
        }
    }
}

/** What the table is saying, over the felt while it is not being bet on. */
@Composable
private fun Message(vm: RouletteViewModel, k: Float) {
    val betting = vm.phase == RoulettePhase.BETTING
    // Betting is silent — the name of every chip laid would strobe over the
    // felt — but a refusal has to be seen or the press reads as a dead table.
    if (betting && vm.notice == null) return
    val text = if (betting) vm.notice.orEmpty() else vm.message
    val net = vm.lastWin - vm.totalStaked
    Box(
        Modifier.artBox(k, A.GRID_LEFT, A.GRID_TOP + 10f, A.GRID_RIGHT, A.GRID_TOP + 90f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = (30f * k).sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = when {
                betting -> MarkerGold
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
