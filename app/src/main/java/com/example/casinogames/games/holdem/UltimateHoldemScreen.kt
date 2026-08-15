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
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
import com.example.casinogames.ui.common.CardHeight
import com.example.casinogames.ui.common.CardWidth
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
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(vm, onBack)
            Text(
                "ULTIMATE TEXAS HOLD'EM \u00B7 PAY TABLES",
                fontSize = 11.sp,
                letterSpacing = 0.14.em,
                fontWeight = FontWeight.Black,
                color = NeonPurple.copy(alpha = 0.85f),
                modifier = Modifier
                    .clickable { showPays = true }
                    .padding(top = 1.dp, bottom = 3.dp),
            )
            Felt(vm)
            ResultRows(vm)
            // The art runs out before the screen does, so a clean strip of the
            // same felt is stretched over whatever is left.
            Image(
                painter = painterResource(R.drawable.uth_felt_fill),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentScale = ContentScale.FillBounds,
            )
            if (vm.phase == UthPhase.BETTING) ChipRail(vm)
            Spacer(Modifier.height(8.dp))
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
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
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
/** Height of the felt art as a fraction of its width. */
private const val FELT_ASPECT = 1.4102f

private val DEALER_X = floatArrayOf(0.4325f, 0.5759f)
private val BOARD_X = floatArrayOf(0.2136f, 0.3571f, 0.5037f, 0.6504f, 0.7917f)
private val PLAYER_X = floatArrayOf(0.4463f, 0.5537f)

/**
 * The felt is the table art itself. Cards, the message line and the chips are
 * the only things drawn on top, each placed against what the art paints.
 */
@Composable
private fun Felt(vm: UltimateHoldemViewModel) {
    val betting = vm.phase == UthPhase.BETTING
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        val h = w * FELT_ASPECT
        Image(
            painter = painterResource(R.drawable.uth_felt),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
        vm.dealerCards.forEachIndexed { i, card ->
            FeltCard(w, h, DEALER_X.getOrElse(i) { 0.5f }, 0.1085f) {
                PlayingCardView(card, faceUp = vm.dealerRevealed)
            }
        }
        vm.board.forEachIndexed { i, card ->
            FeltCard(w, h, BOARD_X.getOrElse(i) { 0.5f }, 0.2833f) {
                PlayingCardView(card, faceUp = i < vm.boardRevealed)
            }
        }
        vm.playerCards.forEachIndexed { i, card ->
            FeltCard(w, h, PLAYER_X.getOrElse(i) { 0.5f }, 0.6330f) {
                PlayingCardView(card, faceUp = true)
            }
        }
        Text(
            vm.message,
            fontSize = 15.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            color = if (vm.phase == UthPhase.RESULT && vm.results.sumOf { it.net } > 0) FeltGreen
            else P.OffWhite.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().offset(y = h * 0.4500f - 11.dp),
        )
        BlockSpot(
            w, h, fx = 0.5016f, fy = 0.7430f, fSize = 0.1403f,
            amount = if (betting) vm.trips else vm.tripsStake,
            onClick = { vm.addTrips() }.takeIf { betting },
        )
        BlockSpot(
            w, h, fx = 0.4144f, fy = 0.8296f, fSize = 0.1137f,
            amount = if (betting) vm.ante else vm.anteStake,
            onClick = { vm.addAnte() }.takeIf { betting },
        )
        BlockSpot(
            w, h, fx = 0.5909f, fy = 0.8296f, fSize = 0.1137f,
            amount = if (betting) vm.ante else vm.blindStake,
            onClick = null,
        )
        BlockSpot(
            w, h, fx = 0.5016f, fy = 0.9314f, fSize = 0.1307f,
            amount = vm.playStake,
            onClick = null,
        )
    }
}

/** Drops a card onto one of the outlines painted into the felt. */
@Composable
private fun FeltCard(
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp,
    fx: Float,
    fy: Float,
    content: @Composable () -> Unit,
) {
    Box(Modifier.offset(x = w * fx - CardWidth / 2, y = h * fy - CardHeight / 2)) {
        content()
    }
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
 * The betting area is the table art itself. Only the chips and the hit areas
 * are drawn on top, positioned against the spots painted into the felt.
 */
@Composable
private fun BetSpots(vm: UltimateHoldemViewModel) {
    val betting = vm.phase == UthPhase.BETTING
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        val h = w * BLOCK_ASPECT
        Image(
            painter = painterResource(R.drawable.uth_betting_block),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
        BlockSpot(
            w, h, fx = 0.5016f, fy = 0.3551f, fSize = 0.1403f,
            amount = if (betting) vm.trips else vm.tripsStake,
            onClick = { vm.addTrips() }.takeIf { betting },
        )
        BlockSpot(
            w, h, fx = 0.4144f, fy = 0.5758f, fSize = 0.1137f,
            amount = if (betting) vm.ante else vm.anteStake,
            onClick = { vm.addAnte() }.takeIf { betting },
        )
        // The blind always matches the ante, so it takes no chips of its own.
        BlockSpot(
            w, h, fx = 0.5909f, fy = 0.5758f, fSize = 0.1137f,
            amount = if (betting) vm.ante else vm.blindStake,
            onClick = null,
        )
        BlockSpot(
            w, h, fx = 0.5016f, fy = 0.8349f, fSize = 0.1307f,
            amount = vm.playStake,
            onClick = null,
        )
    }
}

/** Height of the betting art as a fraction of its width. */
private const val BLOCK_ASPECT = 0.5537f

/** A chip resting on one of the spots painted into the felt. */
@Composable
private fun BlockSpot(
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp,
    fx: Float,
    fy: Float,
    fSize: Float,
    amount: Int,
    onClick: (() -> Unit)?,
) {
    val size = w * fSize
    Box(
        Modifier
            .offset(x = w * fx - size / 2, y = h * fy - size / 2)
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (amount > 0) PlacedBetChip(amount, size = size * 0.84f, artFor = ::uthChipArt)
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
                imageRes = uthChipArt(chip.value),
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
        Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 14.dp),
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
