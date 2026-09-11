package com.example.casinogames.games.shootout

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
import androidx.compose.ui.unit.TextUnit
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
import com.example.casinogames.ui.common.EmptyCardSlot
import com.example.casinogames.ui.common.FreePlayBuyIn
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.PlayingCardView
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P

private val TableBlack = Color(0xFF0A0602)

/** Amber neon for a western table, with a pale brass for the bonus. */
val ShootoutAmber = Color(0xFFFFB13D)
private val Brass = Color(0xFFFFE0A3)
private val LossRed = Color(0xFFFF4D4D)
private val WinGreen = Color(0xFF57E06A)

/**
 * Washes the shared background art amber: its luminance, scaled into the
 * table's own colour, the same trick the hold'em table plays with green.
 */
private val AmberWash = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.206f, 0.405f, 0.079f, 0f, 0f,
            0.072f, 0.141f, 0.027f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

@Composable
fun ShootoutScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = {},
    vm: ShootoutViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    var showPays by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(TableBlack)) {
        Image(
            painter = painterResource(R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.42f),
            contentScale = ContentScale.Crop,
            colorFilter = AmberWash,
        )
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp),
        ) {
            val cardScale = if (maxWidth < 380.dp) 0.88f else 1f
            val drop = (maxHeight * 0.03f).coerceAtMost(24.dp)
            Column(
                Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopBar(vm, onBack)
                Spacer(Modifier.height(drop))
                DealerRow(vm, cardScale)
                Spacer(Modifier.height(6.dp))
                TableDivider()
                Spacer(Modifier.height(8.dp))
                BoardRow(vm, cardScale)
                Spacer(Modifier.height(8.dp))
                TableDivider()
                Spacer(Modifier.height(8.dp))
                PlayerRow(vm, cardScale)
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
                if (vm.phase == ShootoutPhase.BETTING) ChipRail(vm)
                Spacer(Modifier.height(6.dp))
                Actions(vm)
            }
        }

        if (showPays) PayTables { showPays = false }
        if (vm.campaign && vm.bankroll >= vm.goal && vm.phase == ShootoutPhase.RESULT) {
            CampaignComplete(
                goal = vm.goal,
                nextGoal = vm.goal * 100,
                onGoBigger = { vm.raiseGoal() },
                onStartOver = { vm.restartCampaign(); onGameOverExit() },
            )
        } else if (vm.bankroll < 25 && vm.phase == ShootoutPhase.BETTING && vm.totalAtRisk == 0) {
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
private fun TopBar(vm: ShootoutViewModel, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹ POKER",
            color = ShootoutAmber,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        Text("BANKROLL ", color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(formatMoney(vm.bankroll), color = Brass, fontSize = 14.sp, fontWeight = FontWeight.Black)
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
            .background(ShootoutAmber.copy(alpha = 0.28f))
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
                    .background(ShootoutAmber.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                    .border(1.dp, ShootoutAmber, RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * The dealer's four stay down until the showdown; then the two the house way
 * kept turn over and the discards fade.
 */
@Composable
private fun DealerRow(vm: ShootoutViewModel, scale: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val badge = vm.settlements.firstOrNull()?.dealerHand?.category?.label
            ?.takeIf { vm.dealerRevealed }
        RowLabel("DEALER", ShootoutAmber.copy(alpha = 0.75f), badge)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(4) { i ->
                val card = vm.dealerFour.getOrNull(i)
                if (card == null) {
                    SlotBox(scale)
                } else {
                    val kept = vm.dealerRevealed && card in vm.dealerKept &&
                        // Two of the same card can both be dealt; count them once.
                        vm.dealerFour.take(i).count { it == card } < vm.dealerKept.count { it == card }
                    Box(Modifier.alpha(if (vm.dealerRevealed && !kept) 0.3f else 1f)) {
                        PlayingCardView(card, faceUp = kept, scale = scale)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardRow(vm: ShootoutViewModel, scale: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RowLabel("BOARD", ShootoutAmber.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { i ->
                val card = vm.board.getOrNull(i)
                if (card == null) SlotBox(scale * 0.92f)
                else PlayingCardView(card, faceUp = true, scale = scale * 0.92f)
            }
        }
    }
}

/**
 * Four face up to choose from; a tapped card lifts and rings amber. Once the
 * choice is made the discards fade, and a split shows as two pairs with a
 * gap between them.
 */
@Composable
private fun PlayerRow(vm: ShootoutViewModel, scale: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val label = when {
            vm.split -> "YOUR HANDS"
            else -> "YOUR HAND"
        }
        val badge = vm.settlements.singleOrNull()?.playerHand?.category?.label
        RowLabel(label, Brass, badge)
        Spacer(Modifier.height(4.dp))
        if (vm.hands.size == 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                vm.hands.forEachIndexed { h, hand ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            hand.forEach { PlayingCardView(it, faceUp = true, scale = scale) }
                        }
                        val s = vm.settlements.getOrNull(h)
                        Text(
                            s?.playerHand?.category?.label ?: "HAND ${h + 1}",
                            color = when (s?.outcome) {
                                ShootoutRules.Outcome.WIN -> WinGreen
                                ShootoutRules.Outcome.LOSE -> LossRed
                                null -> Brass.copy(alpha = 0.7f)
                            },
                            fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.08.em,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        } else {
            val choosing = vm.phase == ShootoutPhase.CHOOSING
            val kept = vm.hands.firstOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                repeat(4) { i ->
                    val card = vm.playerFour.getOrNull(i)
                    if (card == null) {
                        SlotBox(scale)
                    } else {
                        val picked = i in vm.picked
                        val discarded = kept != null && !vm.picked.contains(i)
                        Box(
                            Modifier
                                .offset(y = if (picked && choosing) (-8).dp else 0.dp)
                                .alpha(if (discarded) 0.3f else 1f)
                                .then(
                                    if (picked && choosing)
                                        Modifier.border(2.dp, ShootoutAmber, RoundedCornerShape(6.dp))
                                    else Modifier
                                )
                                .then(if (choosing) Modifier.clickable { vm.togglePick(i) } else Modifier)
                        ) {
                            PlayingCardView(card, faceUp = true, scale = scale)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotBox(scale: Float) {
    Box(Modifier.size(52.dp * scale, 78.dp * scale), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().drawBehind {
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
}

@Composable
private fun MessageLine(vm: ShootoutViewModel) {
    val settled = vm.settlements
    val tone = when {
        vm.phase != ShootoutPhase.RESULT -> P.OffWhite.copy(alpha = 0.9f)
        settled.any { it.badBeatWin != null } -> Brass
        settled.any { it.bonusWin != null && it.bonusWin.payout > 0 } -> Brass
        settled.any { it.outcome == ShootoutRules.Outcome.WIN } -> WinGreen
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
private fun ResultRows(vm: ShootoutViewModel) {
    if (vm.phase != ShootoutPhase.RESULT) return
    Spacer(Modifier.height(4.dp))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        vm.results.forEach { r ->
            Row(
                Modifier
                    .background(Color(0xCC0A0602), RoundedCornerShape(999.dp))
                    .border(
                        1.dp,
                        if (r.net > 0) Brass else P.OffWhite.copy(alpha = 0.25f),
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
 * The poker bet and the bonus side by side, the bonus ladder in the right
 * gutter. A split hand shows the doubled stake on both spots.
 */
@Composable
private fun BetSpots(vm: ShootoutViewModel, onShowPays: () -> Unit) {
    val betting = vm.phase == ShootoutPhase.BETTING
    val spot = 48.dp
    val gap = 8.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The poker bet rides above the two side bets, which sit tight
        // together under it; each ladder stands in the gutter beside its
        // own diamond — Bad Beat left, Bonus right.
        val gutter = (maxWidth - (spot * 2 + gap)) / 2
        Column(
            // Lifted a little off the rail, so the spots sit mid-ladder
            // rather than crowding the chips.
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CircleSpot(
                "POKER", ShootoutAmber,
                amount = if (betting) vm.poker else vm.pokerStake * vm.handCount,
                size = spot,
                onClick = { vm.addPoker() }.takeIf { betting },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                DiamondSpot(
                    "BAD\nBEAT", Brass,
                    amount = if (betting) vm.badBeat else vm.badBeatStake * vm.handCount,
                    size = spot,
                    onClick = { vm.addBadBeat() }.takeIf { betting },
                )
                DiamondSpot(
                    "BONUS", Brass,
                    amount = if (betting) vm.bonus else vm.bonusStake * vm.handCount,
                    size = spot,
                    onClick = { vm.addBonus() }.takeIf { betting },
                )
            }
        }
        FeltPayTable(
            title = "BAD BEAT",
            color = Brass,
            rows = ShootoutRules.BadBeatPay.entries.map {
                shortName(it.label) to "${formatWhole(it.payout)}-to-1"
            },
            width = gutter - 10.dp,
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 2.dp),
            onClick = onShowPays,
        )
        FeltPayTable(
            title = "BONUS",
            color = Brass,
            rows = ShootoutRules.BonusPay.entries.map {
                shortName(it.label) to "${formatWhole(it.payout)}-to-1"
            },
            width = gutter - 10.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 2.dp),
            onClick = onShowPays,
        )
    }
}

/** The felt ladders are narrow with three spots between them; the hands go by their short names there. */
private fun shortName(label: String): String = when (label) {
    "Suited Five of a Kind" -> "Suited Quints"
    "Suited Four of a Kind" -> "Suited Quads"
    "Five of a Kind" -> "Quints"
    "Four of a Kind" -> "Quads"
    "Three of a Kind" -> "Trips"
    else -> label
}

private fun formatWhole(v: Int): String = String.format(java.util.Locale.US, "%,d", v)

@Composable
private fun FeltPayTable(
    title: String,
    color: Color,
    rows: List<Pair<String, String>>,
    width: Dp,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 8.sp,
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
                color = color, fontSize = 11.sp,
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
private fun FeltPayRow(label: String, value: String, color: Color, fontSize: TextUnit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = P.OffWhite.copy(alpha = 0.88f),
            fontSize = fontSize, lineHeight = fontSize * 1.15f, maxLines = 1,
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
            color = color, fontSize = fontSize, lineHeight = fontSize * 1.15f,
            fontWeight = FontWeight.Black, maxLines = 1,
        )
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
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.06.em,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChipRail(vm: ShootoutViewModel) {
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
private fun Actions(vm: ShootoutViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (vm.phase) {
            ShootoutPhase.BETTING -> {
                ActionButton("CLEAR", ShootoutAmber, false, Modifier.weight(1f), vm::clearBets)
                ActionButton("DEAL", Brass, true, Modifier.weight(1.4f), vm::deal)
            }
            ShootoutPhase.CHOOSING -> {
                if (vm.canTakeMarker) {
                    ActionButton(
                        "MARKER +5,000", P.GoldTrim, false, Modifier.weight(1f), vm::takeMarker,
                    )
                } else {
                    ActionButton(
                        "SPLIT  ${formatWhole(vm.splitCost)}", ShootoutAmber, false, Modifier.weight(1f),
                        vm::splitHands.takeIf { vm.canSplit },
                    )
                }
                ActionButton(
                    "KEEP TWO", Brass, true, Modifier.weight(1.4f),
                    vm::keepTwo.takeIf { vm.canKeep },
                )
            }
            ShootoutPhase.RESULT -> {
                ActionButton("NEW BET", ShootoutAmber, false, Modifier.weight(1f)) { vm.nextHand(false) }
                ActionButton("REBET", Brass, true, Modifier.weight(1.4f)) { vm.nextHand(true) }
            }
            else -> {
                Spacer(Modifier.weight(1f))
                Text(
                    if (vm.phase == ShootoutPhase.DEALING) "DEALING" else "SHOWDOWN",
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
        Modifier.fillMaxSize().background(Color(0xF20A0602)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp)
                .background(Color(0xF2100904), RoundedCornerShape(14.dp))
                .border(1.5.dp, ShootoutAmber.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            Text(
                "BONUS", color = Brass, fontSize = 12.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.1.em,
            )
            Spacer(Modifier.height(4.dp))
            ShootoutRules.BonusPay.entries.forEach {
                Row(Modifier.fillMaxWidth()) {
                    Text(it.label, color = P.OffWhite, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (it.payout > 0) "${formatWhole(it.payout)}-to-1" else "push",
                        color = Brass, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Paid on your best five, win or lose; a straight is the floor. Six decks " +
                    "in the shoe, so the same card can come twice: four of the very same " +
                    "card is suited quads, five of the very same card tops the ladder.",
                color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "BAD BEAT", color = Brass, fontSize = 12.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.1.em,
            )
            Spacer(Modifier.height(4.dp))
            ShootoutRules.BadBeatPay.entries.forEach {
                Row(Modifier.fillMaxWidth()) {
                    Text(it.label, color = P.OffWhite, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${formatWhole(it.payout)}-to-1",
                        color = Brass, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Pays the hand that lost the showdown, either side, when it was trips or " +
                    "better. The dealer takes ties, so a tied hand of yours counts as beaten.",
                color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "THE TABLE", color = ShootoutAmber, fontSize = 12.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.1.em,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Four cards each. Keep any two, or split into two hands for a second " +
                    "poker bet (and a second bonus if you made one). The dealer keeps two " +
                    "by house way, five come out, best five of seven. The poker bet pays " +
                    "even money and the dealer wins ties.",
                color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp,
            )
        }
    }
}
