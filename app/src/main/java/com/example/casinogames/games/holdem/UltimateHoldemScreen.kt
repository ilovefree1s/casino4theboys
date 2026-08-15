package com.example.casinogames.games.holdem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.casinogames.R
import com.example.casinogames.games.core.Card
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
import com.example.casinogames.ui.common.CasinoChip
import com.example.casinogames.ui.common.EmptyCardSlot
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.PlayingCardView
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P

private val TableBlack = Color(0xFF030703)
private val NeonPurple = Color(0xFF90FF3D)
private val NeonPurpleDim = Color(0x9962B82B)
private val NeonPink = Color(0xFF90FF3D)
private val FeltGreen = Color(0xFF90FF3D)
private val LossRed = Color(0xFFFF4D4D)

/**
 * Flattens the background art to brightness and paints it back in the felt's
 * green. Tinting the purple original directly just muddies it, because purple
 * has almost no green in it to multiply.
 */
private val GreenWash = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.170f, 0.333f, 0.062f, 0f, 0f,
            0.300f, 0.590f, 0.110f, 0f, 0f,
            0.072f, 0.141f, 0.026f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

/** The felt's own chip art, keyed by chip value. */
private fun uthChipArt(value: Int): Int = when (value) {
    25 -> R.drawable.uth_chip_25
    100 -> R.drawable.uth_chip_100
    500 -> R.drawable.uth_chip_500
    1000 -> R.drawable.uth_chip_1k
    5000 -> R.drawable.uth_chip_5k
    else -> R.drawable.chip_25k
}

@Composable
fun UltimateHoldemScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = {},
    vm: UltimateHoldemViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    var showPays by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(TableBlack)) {
        Image(
            painter = painterResource(R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.5f),
            contentScale = ContentScale.Crop,
            colorFilter = GreenWash,
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                // Lifts the spots, chips and buttons clear of the bottom edge.
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(vm, onBack)
            DealerRow(vm)
            Spacer(Modifier.height(7.dp))
            TableDivider()
            Spacer(Modifier.height(11.dp))
            BoardRow(vm)
            Spacer(Modifier.height(7.dp))
            TableDivider()
            Spacer(Modifier.height(14.dp))
            PlayerRow(vm)
            Spacer(Modifier.height(5.dp))
            MessageLine(vm)
            Spacer(Modifier.weight(1f))
            BetSpots(vm)
            Spacer(Modifier.height(6.dp))
            if (vm.phase == UthPhase.BETTING) ChipRail(vm)
            Spacer(Modifier.height(6.dp))
            Actions(vm)
        }

        if (showPays) PayTables { showPays = false }
        if (vm.campaign && vm.bankroll >= vm.goal && vm.phase == UthPhase.RESULT) {
            CampaignComplete(
                goal = vm.goal,
                nextGoal = vm.goal * 100,
                onGoBigger = { vm.raiseGoal() },
                onStartOver = { vm.restartCampaign(); onGameOverExit() },
            )
        } else if (vm.bankroll < 25 && vm.phase == UthPhase.BETTING && vm.ante == 0) {
            if (vm.campaign) {
                CampaignGameOver(onStartOver = { vm.restartCampaign(); onGameOverExit() })
            } else {
                RebuyPrompt { vm.buyBackIn() }
            }
        }
    }
}

@Composable
private fun TopBar(vm: UltimateHoldemViewModel, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹ LOBBY",
            color = NeonPurple,
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
            color = NeonPurple, fontSize = 14.sp, fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(10.dp))
        Text("AT RISK ", color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(
            vm.totalAtRisk.toString(),
            color = P.OffWhite, fontSize = 14.sp, fontWeight = FontWeight.Black,
        )
    }
}

/** Fades in from the felt at both ends so it reads as a table marking. */
@Composable
private fun TableDivider() {
    Box(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(1.5.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.15f to NeonPurpleDim,
                    0.5f to NeonPurple,
                    0.85f to NeonPurpleDim,
                    1f to Color.Transparent,
                )
            )
    )
}

/** Once a hand is decided, the five that won stay lit and the rest fall back. */
private fun playedAlpha(vm: UltimateHoldemViewModel, card: Card): Float =
    if (vm.winningCards.isEmpty() || card in vm.winningCards) 1f else 0.3f

@Composable
private fun DealerRow(vm: UltimateHoldemViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val sub = vm.settlement
        Label("DEALER", NeonPink, sub?.dealerHand?.category?.label.takeIf { vm.dealerRevealed })
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.dealerCards.isEmpty()) {
                repeat(2) { EmptyCardSlot() }
            } else {
                vm.dealerCards.forEach { card ->
                    Box(Modifier.alpha(playedAlpha(vm, card))) {
                        PlayingCardView(card, faceUp = vm.dealerRevealed)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardRow(vm: UltimateHoldemViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (vm.board.isEmpty()) {
            repeat(5) { EmptyCardSlot() }
        } else {
            vm.board.forEachIndexed { i, card ->
                Box(Modifier.alpha(playedAlpha(vm, card))) {
                    PlayingCardView(card, faceUp = i < vm.boardRevealed)
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(vm: UltimateHoldemViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ResultColumn(vm, amounts = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.playerCards.isEmpty()) {
                repeat(2) { EmptyCardSlot() }
            } else {
                vm.playerCards.forEach { card ->
                    Box(Modifier.alpha(playedAlpha(vm, card))) {
                        PlayingCardView(card, faceUp = true)
                    }
                }
            }
        }
        ResultColumn(vm, amounts = true)
      }
        Spacer(Modifier.height(6.dp))
        val hand = vm.settlement?.playerHand?.category?.label
        Label("YOUR HAND", NeonPurple, hand)
    }
}

/** The settlement reads down either side of the player's hand. */
@Composable
private fun ResultColumn(vm: UltimateHoldemViewModel, amounts: Boolean) {
    if (vm.phase != UthPhase.RESULT) {
        Spacer(Modifier.width(78.dp))
        return
    }
    Column(
        Modifier.width(78.dp).padding(horizontal = 6.dp),
        horizontalAlignment = if (amounts) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        vm.results.forEach { r ->
            if (amounts) {
                Text(
                    if (r.net >= 0) "+${formatMoney(r.net)}" else "−${formatMoney(-r.net)}",
                    color = if (r.net > 0) FeltGreen else if (r.net < 0) LossRed
                    else P.OffWhite.copy(alpha = 0.6f),
                    fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1,
                )
            } else {
                Text(
                    r.label.substringBefore(" ·"),
                    color = P.OffWhite.copy(alpha = 0.85f),
                    fontSize = 12.sp, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String, color: Color, badge: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            color = color, fontSize = 12.sp,
            fontWeight = FontWeight.Black, letterSpacing = 0.14.em,
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                badge,
                color = P.OffWhite, fontSize = 11.sp, fontWeight = FontWeight.Black,
                modifier = Modifier
                    .background(color.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                    .border(1.dp, color, RoundedCornerShape(999.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun MessageLine(vm: UltimateHoldemViewModel) {
    val settled = vm.phase == UthPhase.RESULT
    val net = if (settled) vm.results.sumOf { it.net } else 0.0
    val won = settled && net > 0
    val lost = settled && net < 0
    Text(
        vm.message,
        fontSize = if (lost) 18.sp else 15.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        color = if (won) FeltGreen else if (lost) LossRed
        else P.OffWhite.copy(alpha = 0.92f),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ResultRows(vm: UltimateHoldemViewModel) {
    if (vm.phase != UthPhase.RESULT) return
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier.widthIn(max = 320.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        vm.results.forEach { r ->
            Row(Modifier.fillMaxWidth()) {
                Text(r.label, color = P.OffWhite.copy(alpha = 0.8f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    if (r.net >= 0) "+${formatMoney(r.net)}" else "−${formatMoney(-r.net)}",
                    color = if (r.net > 0) FeltGreen else if (r.net < 0) LossRed
                    else P.OffWhite.copy(alpha = 0.6f),
                    fontSize = 12.sp, fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

/**
 * The standard Ultimate layout: Trips on its own diamond, the Ante and Blind
 * paired as equals, and the Play spot below the Ante.
 */
@Composable
private fun BetSpots(vm: UltimateHoldemViewModel) {
    val betting = vm.phase == UthPhase.BETTING
    val spot = 52.dp
    val gap = 18.dp
    val shift = 0.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // The Trips pays sit beside the diamond, resting on the Ante row below.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gutter = (maxWidth - (spot * 2 + gap)) / 2
            Row(
                Modifier.align(Alignment.BottomCenter).offset(x = shift),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DiamondSpot(
                    label = "TRIPS",
                    color = NeonPink,
                    amount = if (betting) vm.trips else vm.tripsStake,
                    size = spot,
                    onClick = { vm.addTrips() }.takeIf { betting },
                )
            }
            FeltPayTable(
                title = "TRIPS",
                color = NeonPink,
                rows = TripsPay.entries.map { it.label to "${it.multiplier}-to-1" },
                width = gutter - 6.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp),
            )
        }
        Row(
            Modifier.offset(x = shift),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleSpot(
                "ANTE", NeonPurple,
                amount = if (betting) vm.ante else vm.anteStake,
                size = spot,
                onClick = { vm.addAnte() }.takeIf { betting },
            )
            Box(Modifier.width(gap), contentAlignment = Alignment.Center) {
                Text(
                    "=",
                    color = NeonPurple.copy(alpha = 0.85f),
                    fontSize = 22.sp, fontWeight = FontWeight.Black,
                )
            }
            // The blind always matches the ante, so it takes no chips itself.
            CircleSpot(
                "BLIND", NeonPurple,
                amount = if (betting) vm.ante else vm.blindStake,
                size = spot,
                onClick = null,
            )
        }
        // The spots stay centred on screen; the two ladders take the gutters
        // either side, sitting just above the chip rail.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gutter = (maxWidth - (spot * 2 + gap)) / 2
            Row(
                Modifier.align(Alignment.TopCenter).offset(x = shift),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleSpot("PLAY", FeltGreen, vm.playStake, spot, null)
            }
            FeltPayTable(
                title = "PLAY BET",
                color = FeltGreen,
                rows = listOf(
                    "Before the Flop" to "3x or 4x",
                    "After the Flop" to "2x",
                    "On the River" to "1x",
                ),
                width = gutter - 8.dp,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 7.dp),
            )
            FeltPayTable(
                title = "BLIND",
                color = NeonPurple,
                rows = BlindPay.entries.map { it.label to blindOdds(it) },
                width = gutter - 6.dp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

private fun blindOdds(pay: BlindPay): String = "${pay.multiplier.toInt()}-to-1"

/** A pay table printed straight onto the felt, ruled heading and dotted leaders. */
@Composable
private fun FeltPayTable(
    title: String,
    color: Color,
    rows: List<Pair<String, String>>,
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 9.sp,
) {
    Column(
        modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.5f)))
            Text(
                title,
                color = color, fontSize = 12.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.08.em,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.5f)))
        }
        Spacer(Modifier.height(2.dp))
        rows.forEach { (label, value) -> FeltPayRow(label, value, color, fontSize) }
    }
}

@Composable
private fun FeltPayRow(
    label: String,
    value: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = P.OffWhite.copy(alpha = 0.88f),
            fontSize = fontSize, lineHeight = fontSize * 1.1f, maxLines = 1,
        )
        Box(
            Modifier
                .weight(1f)
                .height(3.dp)
                .padding(horizontal = 3.dp)
                .drawBehind {
                    drawLine(
                        color = Color(0x8AF5F1E8),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(1.5.dp.toPx(), 2.dp.toPx())
                        ),
                    )
                }
        )
        Text(
            value,
            color = color, fontSize = fontSize, lineHeight = fontSize * 1.1f,
            fontWeight = FontWeight.Black, maxLines = 1,
        )
    }
}

@Composable
private fun CircleSpot(
    label: String,
    color: Color,
    amount: Int,
    size: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)?,
) {
    Box(
        Modifier
            .size(size)
            .border(2.dp, if (amount > 0) color else color.copy(alpha = 0.4f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        SpotContents(label, color, amount, size)
    }
}

@Composable
private fun DiamondSpot(
    label: String,
    color: Color,
    amount: Int,
    size: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)?,
) {
    Box(
        Modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // A square stood on its corner; the chip and label stay upright inside it.
        Box(
            Modifier
                .size(size * 0.78f)
                .rotate(45f)
                .border(
                    2.dp,
                    if (amount > 0) color else color.copy(alpha = 0.4f),
                    RoundedCornerShape(3.dp),
                )
        )
        SpotContents(label, color, amount, size)
    }
}

@Composable
private fun SpotContents(
    label: String,
    color: Color,
    amount: Int,
    size: androidx.compose.ui.unit.Dp,
) {
    if (amount > 0) {
        PlacedBetChip(amount, size = size * 0.74f)
    } else {
        Text(
            label,
            color = color.copy(alpha = 0.75f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.08.em,
        )
    }
}

@Composable
private fun ChipRail(vm: UltimateHoldemViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chipsFor(vm.bankroll).forEach { chip ->
            CasinoChip(
                imageRes = chip.imageRes,
                contentDescription = "${chip.value} chip",
                selected = vm.selectedChip == chip.value,
                onClick = { vm.selectedChip = chip.value },
                selectedColor = NeonPink,
            )
        }
    }
}

@Composable
private fun Actions(vm: UltimateHoldemViewModel) {
    Row(
        Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (vm.phase) {
            UthPhase.BETTING -> {
                ActionButton("UNDO", NeonPurpleDim, Modifier.weight(1f)) { vm.undoChip() }
                ActionButton("DEAL", NeonPurple, Modifier.weight(1f)) { vm.deal() }
                ActionButton("CLEAR", NeonPurpleDim, Modifier.weight(1f)) { vm.clearBet() }
            }
            UthPhase.PRE_FLOP, UthPhase.FLOP, UthPhase.RIVER -> {
                vm.playChoices.forEach { mult ->
                    ActionButton("PLAY ${mult}x", FeltGreen, Modifier.weight(1f)) { vm.play(mult) }
                }
                if (vm.canCheck) {
                    ActionButton("CHECK", NeonPurpleDim, Modifier.weight(1f)) { vm.check() }
                }
                if (vm.canFold) {
                    ActionButton("FOLD", NeonPink, Modifier.weight(1f)) { vm.fold() }
                }
            }
            UthPhase.RESULT -> {
                ActionButton("REBET", NeonPurple, Modifier.weight(1f)) { vm.nextHand(true) }
                ActionButton("NEW BET", NeonPurpleDim, Modifier.weight(1f)) { vm.nextHand(false) }
            }
            else -> Spacer(Modifier.height(44.dp))
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(44.dp)
            .background(Color(0xCC0E0A0B), RoundedCornerShape(999.dp))
            .border(1.5.dp, color, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = color, fontSize = 13.sp,
            fontWeight = FontWeight.Black, letterSpacing = 0.08.em,
        )
    }
}

@Composable
private fun RebuyPrompt(onBuyIn: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("OUT OF CHIPS", color = NeonPink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            Text(
                "BUY BACK IN",
                color = P.OffWhite, fontSize = 14.sp, fontWeight = FontWeight.Black,
                modifier = Modifier
                    .border(1.5.dp, NeonPurple, RoundedCornerShape(999.dp))
                    .clickable(onClick = onBuyIn)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun PayTables(onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE6000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .padding(24.dp)
                .background(Color(0xF20E0A0B), RoundedCornerShape(14.dp))
                .border(1.5.dp, NeonPurple.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            PayList(
                "BLIND", NeonPurple,
                BlindPay.entries.map { it.label to "${it.multiplier.toInt()} to 1" },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Less than a straight pushes.",
                fontSize = 10.sp, color = P.OffWhite.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(16.dp))
            PayList(
                "TRIPS", NeonPink,
                TripsPay.entries.map { it.label to "${it.multiplier} to 1" },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Ante and Blind are posted together. Raise 4x or 3x before the flop, " +
                    "2x after it, or 1x on the river. The dealer needs a pair to open — " +
                    "without one, the Ante pushes. Trips is settled on your own five cards.",
                fontSize = 10.sp,
                color = P.OffWhite.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun PayList(title: String, color: Color, rows: List<Pair<String, String>>) {
    Text(
        title,
        color = color, fontSize = 14.sp,
        fontWeight = FontWeight.Black, letterSpacing = 0.12.em,
    )
    Spacer(Modifier.height(6.dp))
    rows.forEach { (label, pays) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(label, fontSize = 12.sp, color = P.OffWhite.copy(alpha = 0.85f))
            Spacer(Modifier.weight(1f))
            Text(pays, fontSize = 12.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}
