package com.example.casinogames.games.miniuth

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.casinogames.R
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.games.core.Card
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
import com.example.casinogames.ui.common.CasinoChip
import com.example.casinogames.ui.common.FreePlayBuyIn
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.PlayingCardView
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P

private val TableBlack = Color(0xFF04060F)

/** The real felt is navy with red and gold lettering; the table takes its colours from that. */
val MiniRed = Color(0xFFFF5A5A)
private val MiniGold = Color(0xFFFFD24D)
private val LossRed = Color(0xFFFF4D4D)
private val WinGreen = Color(0xFF57E06A)

/** Washes the shared background art navy: its luminance, scaled into the felt's blue. */
private val NavyWash = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.075f, 0.147f, 0.029f, 0f, 0f,
            0.120f, 0.235f, 0.046f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

@Composable
fun MiniUthScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = {},
    vm: MiniUthViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    var showPays by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(TableBlack)) {
        Image(
            painter = painterResource(R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.5f),
            contentScale = ContentScale.Crop,
            colorFilter = NavyWash,
        )
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp),
        ) {
            val drop = (maxHeight * 0.04f).coerceAtMost(32.dp)
            Column(
                Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopBar(vm, onBack)
                Spacer(Modifier.height(drop))
                HoleRow(
                    "DEALER", MiniRed.copy(alpha = 0.8f), vm.dealerCards.firstOrNull(),
                    faceUp = vm.dealerRevealed,
                    badge = vm.settlement?.dealerHand?.category?.label?.takeIf { vm.dealerRevealed },
                )
                Spacer(Modifier.height(8.dp))
                TableDivider()
                Spacer(Modifier.height(8.dp))
                BoardRow(vm)
                Spacer(Modifier.height(8.dp))
                TableDivider()
                Spacer(Modifier.height(8.dp))
                HoleRow(
                    "YOUR CARD", MiniGold, vm.playerCards.firstOrNull(),
                    faceUp = true,
                    badge = vm.playerHandNow?.category?.label,
                )
                Spacer(Modifier.height(4.dp))
                MessageLine(vm)
                ResultRows(vm)
            }
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BetSpots(vm, onShowPays = { showPays = true })
                Spacer(Modifier.height(6.dp))
                if (vm.phase == MiniPhase.BETTING) ChipRail(vm)
                Spacer(Modifier.height(6.dp))
                Actions(vm)
            }
        }

        if (showPays) PayTables { showPays = false }
        if (vm.campaign && vm.bankroll >= vm.goal && vm.phase == MiniPhase.RESULT) {
            CampaignComplete(
                goal = vm.goal,
                nextGoal = vm.goal * 100,
                onGoBigger = { vm.raiseGoal() },
                onStartOver = { vm.restartCampaign(); onGameOverExit() },
            )
        } else if (vm.bankroll < 25 && vm.phase == MiniPhase.BETTING && vm.totalAtRisk == 0) {
            if (vm.campaign) {
                CampaignGameOver(onDone = onGameOverExit)
            } else {
                FreePlayBuyIn(
                    onDismiss = {},
                    onConfirm = { FreePlay.buyIn = it; vm.buyBackIn() },
                )
            }
        }
    }
}

@Composable
private fun TopBar(vm: MiniUthViewModel, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹ POKER",
            color = MiniRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        Text("BANKROLL ", color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(formatMoney(vm.bankroll), color = MiniGold, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(10.dp))
        Text("AT RISK ", color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(
            vm.totalAtRisk.toString(),
            color = P.OffWhite, fontSize = 14.sp, fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun TableDivider() {
    Box(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(1.5.dp)
            .background(MiniRed.copy(alpha = 0.28f))
    )
}

@Composable
private fun RowLabel(text: String, color: Color, badge: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text,
            color = color, fontSize = 10.sp,
            fontWeight = FontWeight.Black, letterSpacing = 0.18.em,
        )
        if (badge != null) {
            Text(
                badge,
                color = P.OffWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(MiniRed.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                    .border(1.dp, MiniRed, RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** One card a side: that is the whole of a hand's own holding here. */
@Composable
private fun HoleRow(label: String, color: Color, card: Card?, faceUp: Boolean, badge: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RowLabel(label, color, badge)
        Spacer(Modifier.height(4.dp))
        if (card == null) SlotBox() else PlayingCardView(card, faceUp = faceUp, scale = 1.1f)
    }
}

/** The flop is two cards, the river the third; a gap sets the river apart. */
@Composable
private fun BoardRow(vm: MiniUthViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RowLabel("FLOP  ·  RIVER", MiniRed.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                if (i == 1) Spacer(Modifier.width(5.dp))
                if (i == 2) Spacer(Modifier.width(16.dp))
                val card = vm.community.getOrNull(i)
                if (card == null) SlotBox() else PlayingCardView(card, faceUp = true, scale = 1.1f)
            }
        }
    }
}

@Composable
private fun SlotBox() {
    Box(
        Modifier.size(52.dp * 1.1f, 78.dp * 1.1f).drawBehind {
            drawRoundRect(
                color = Color(0x4DF5F1E8),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            )
        }
    )
}

@Composable
private fun MessageLine(vm: MiniUthViewModel) {
    val s = vm.settlement
    val tone = when {
        vm.phase != MiniPhase.RESULT || s == null -> P.OffWhite.copy(alpha = 0.9f)
        s.outcome == MiniUthRules.Outcome.WIN -> WinGreen
        s.outcome == MiniUthRules.Outcome.PUSH -> P.OffWhite
        s.flushPlusWin != null || s.bonusWin != null -> MiniGold
        else -> LossRed
    }
    Text(
        vm.message,
        color = tone,
        fontSize = 14.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ResultRows(vm: MiniUthViewModel) {
    if (vm.phase != MiniPhase.RESULT) return
    Spacer(Modifier.height(4.dp))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        vm.results.forEach { r ->
            Row(
                Modifier
                    .background(Color(0xCC04060F), RoundedCornerShape(999.dp))
                    .border(
                        1.dp,
                        if (r.net > 0) MiniGold else P.OffWhite.copy(alpha = 0.25f),
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(r.label, color = P.OffWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        r.net > 0 -> "+${formatMoney(r.net)}"
                        r.net < 0 -> "−${formatMoney(-r.net)}"
                        else -> "push"
                    },
                    color = if (r.net > 0) WinGreen else if (r.net < 0) LossRed else P.OffWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

/**
 * The two side bets ride either side of the middle with their ladders in the
 * gutters, the ante and blind on the centre line, the play bet under them
 * with the blind's ladder beside it.
 */
@Composable
private fun BetSpots(vm: MiniUthViewModel, onShowPays: () -> Unit) {
    val betting = vm.phase == MiniPhase.BETTING
    val spot = 50.dp
    val gap = 16.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gutter = (maxWidth - (spot * 2 + gap)) / 2
            Row(
                Modifier.align(Alignment.BottomCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                DiamondSpot(
                    "FLUSH\nPLUS", MiniGold,
                    amount = if (betting) vm.flushPlus else vm.flushPlusStake,
                    size = spot,
                    onClick = { vm.addFlushPlus() }.takeIf { betting },
                )
                DiamondSpot(
                    "3 CARD", MiniGold,
                    amount = if (betting) vm.bonus else vm.bonusStake,
                    size = spot,
                    onClick = { vm.addBonus() }.takeIf { betting },
                )
            }
            FeltPayTable(
                "FLUSH PLUS", MiniGold,
                MiniUthRules.FlushPlusPay.entries.map { it.label to "${it.payout}-to-1" },
                width = gutter - 6.dp,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp),
                onClick = onShowPays,
            )
            FeltPayTable(
                "3 CARD BONUS", MiniGold,
                MiniUthRules.BonusPay.entries.map { it.label to "${it.payout}-to-1" },
                width = gutter - 6.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp),
                onClick = onShowPays,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleSpot(
                "ANTE", MiniRed,
                amount = if (betting) vm.ante else vm.anteStake,
                size = spot,
                onClick = { vm.addAnte() }.takeIf { betting },
            )
            Box(Modifier.width(gap), contentAlignment = Alignment.Center) {
                Text("=", color = MiniRed.copy(alpha = 0.85f), fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
            // The blind always matches the ante, so it takes no chips itself.
            CircleSpot(
                "BLIND", MiniRed,
                amount = if (betting) vm.ante else vm.blindStake,
                size = spot,
                onClick = null,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gutter = (maxWidth - (spot * 2 + gap)) / 2
            Column(
                Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircleSpot("PLAY", MiniRed, vm.playStake, spot, null)
            }
            FeltPayTable(
                "PLAY", MiniRed,
                listOf("Before the flop" to "3x", "After the flop" to "2x", "On the river" to "1x"),
                width = gutter - 6.dp,
                modifier = Modifier.align(Alignment.BottomStart),
                onClick = onShowPays,
            )
            FeltPayTable(
                "BLIND", MiniRed,
                MiniUthRules.BlindPay.entries.map { it.label to "${it.payout}-to-1" } +
                    ("Other hands" to "push"),
                width = gutter - 6.dp,
                modifier = Modifier.align(Alignment.BottomEnd),
                onClick = onShowPays,
            )
        }
    }
}

@Composable
private fun FeltPayTable(
    title: String,
    color: Color,
    rows: List<Pair<String, String>>,
    width: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .width(width)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.5f)))
            Text(
                title,
                color = color, fontSize = 10.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.08.em,
                modifier = Modifier.padding(horizontal = 4.dp),
                maxLines = 1,
            )
            Box(Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.5f)))
        }
        Spacer(Modifier.height(2.dp))
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    color = P.OffWhite.copy(alpha = 0.88f),
                    fontSize = 8.sp, lineHeight = 9.2.sp, maxLines = 1,
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
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 2.dp.toPx())),
                            )
                        }
                )
                Text(
                    value,
                    color = color, fontSize = 8.sp, lineHeight = 9.2.sp,
                    fontWeight = FontWeight.Black, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CircleSpot(label: String, color: Color, amount: Int, size: Dp, onClick: (() -> Unit)?) {
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
private fun DiamondSpot(label: String, color: Color, amount: Int, size: Dp, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size * 0.78f)
                .rotate(45f)
                .border(2.dp, if (amount > 0) color else color.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
        )
        SpotContents(label, color, amount, size)
    }
}

@Composable
private fun SpotContents(label: String, color: Color, amount: Int, size: Dp) {
    if (amount > 0) {
        PlacedBetChip(amount, size = size * 0.74f)
    } else {
        Text(
            label,
            color = color.copy(alpha = 0.75f),
            fontSize = if (label.lines().maxOf { it.length } > 5) 8.sp else 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.06.em,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChipRail(vm: MiniUthViewModel) {
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
                selectedColor = MiniGold,
            )
        }
    }
}

private fun formatWhole(v: Int): String = String.format(java.util.Locale.US, "%,d", v)

@Composable
private fun Actions(vm: MiniUthViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (vm.phase) {
            MiniPhase.BETTING -> {
                ActionButton("CLEAR", MiniRed, false, Modifier.weight(1f), vm::clearBets)
                ActionButton("DEAL", MiniGold, true, Modifier.weight(1.4f), vm::deal)
            }
            MiniPhase.PRE_FLOP, MiniPhase.FLOP -> {
                val mult = if (vm.phase == MiniPhase.PRE_FLOP) 3 else 2
                ActionButton("CHECK", MiniRed, false, Modifier.weight(1f), vm::check)
                ActionButton(
                    "${mult}X  ${formatWhole(vm.playCost)}", MiniGold, true, Modifier.weight(1.4f),
                    vm::raise.takeIf { vm.canRaise },
                )
            }
            MiniPhase.RIVER -> {
                ActionButton("FOLD", LossRed, false, Modifier.weight(1f), vm::fold)
                if (vm.canTakeMarker) {
                    ActionButton("MARKER +5,000", P.GoldTrim, true, Modifier.weight(1.6f), vm::takeMarker)
                } else {
                    ActionButton(
                        "1X  ${formatWhole(vm.playCost)}", MiniGold, true, Modifier.weight(1.4f),
                        vm::raise.takeIf { vm.canRaise },
                    )
                }
            }
            MiniPhase.RESULT -> {
                ActionButton("NEW BET", MiniRed, false, Modifier.weight(1f)) { vm.nextHand(false) }
                ActionButton("REBET", MiniGold, true, Modifier.weight(1.4f)) { vm.nextHand(true) }
            }
            else -> {
                Spacer(Modifier.weight(1f))
                Text(
                    if (vm.phase == MiniPhase.SHOWDOWN) "SHOWDOWN" else "DEALING",
                    color = P.OffWhite.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.16.em,
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    color: Color,
    solid: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
) {
    val live = onClick != null
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (solid) color.copy(alpha = if (live) 0.16f else 0.06f) else Color.Transparent)
            .border(
                if (solid) 2.dp else 1.5.dp,
                color.copy(alpha = if (live) 0.9f else 0.3f),
                RoundedCornerShape(999.dp),
            )
            .then(if (live) Modifier.clickable(onClick = onClick!!) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color.copy(alpha = if (live) 1f else 0.4f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.1.em,
        )
    }
}

@Composable
private fun PayTables(onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xF204060F)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp)
                .background(Color(0xF2070A18), RoundedCornerShape(14.dp))
                .border(1.5.dp, MiniRed.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            PayList("BLIND", MiniRed, MiniUthRules.BlindPay.entries.map { it.label to "${it.payout}-to-1" })
            Note("Pays only when your hand beats the dealer. Other winning hands push it, and so does a tie.")
            PayList("FLUSH PLUS", MiniGold, MiniUthRules.FlushPlusPay.entries.map { it.label to "${it.payout}-to-1" })
            Note("Pays on your final three-card hand, win or lose.")
            PayList("3 CARD BONUS", MiniGold, MiniUthRules.BonusPay.entries.map { it.label to "${it.payout}-to-1" })
            Note("Pays on your final three-card hand, win or lose.")
            Text(
                "THE TABLE", color = MiniRed, fontSize = 12.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.1.em,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "One card each, a two-card flop and a river. Every hand must use its own " +
                    "card: yours plus the best two on the board. Raise once — 3x before the " +
                    "flop, 2x after it, 1x on the river — or fold on the river. A straight " +
                    "beats a flush with three cards. The dealer always plays.",
                color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun PayList(title: String, color: Color, rows: List<Pair<String, String>>) {
    Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.1.em)
    Spacer(Modifier.height(4.dp))
    rows.forEach { (label, value) ->
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = P.OffWhite, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}
