package com.example.casinogames.games.djwild

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
import com.example.casinogames.games.core.Card
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
import com.example.casinogames.ui.common.CasinoChip
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.PlayingCardView
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P

private val TableBlack = Color(0xFF02050A)

/**
 * The felt's colours are taken off dj4theboys.png itself rather than picked by
 * eye: the neon ring, its bright core and the glow behind it. The background
 * is already blue, so unlike the hold'em table it needs no colour wash.
 */
private val NeonBlue = Color(0xFF2097F9)
private val IceBlue = Color(0xFF9BE3FB)
private val DeepBlue = Color(0xFF042799)
private val LossRed = Color(0xFFFF4D4D)
private val WinGreen = Color(0xFF57E06A)

@Composable
fun DjWildScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = {},
    vm: DjWildViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    var showPays by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(TableBlack)) {
        Image(
            painter = painterResource(R.drawable.dj4theboys),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.19f),
            contentScale = ContentScale.Crop,
        )
        // The table is two blocks, not one column: the hands hang off the top
        // bar and the spots, chips and buttons stand on the bottom edge. Run as
        // one column, a tall result — five pills on a short phone — pushed the
        // buttons off the screen entirely, with no way back to a new hand.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                // Lifts the spots, chips and buttons clear of the bottom edge.
                .padding(bottom = 18.dp),
        ) {
            // The hands sit off the top bar, but only as far as the screen can
            // spare — a short one gives the felt below it back.
            val drop = (maxHeight * 0.07f).coerceAtMost(60.dp)
            Column(
                Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopBar(vm, onBack)
                Spacer(Modifier.height(drop))
                HandRow(vm, dealer = true)
                Spacer(Modifier.height(6.dp))
                TableDivider()
                Spacer(Modifier.height(10.dp))
                HandRow(vm, dealer = false)
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
                if (vm.phase == DjPhase.BETTING) ChipRail(vm)
                Spacer(Modifier.height(6.dp))
                Actions(vm)
            }
        }

        if (showPays) PayTables { showPays = false }
        if (vm.campaign && vm.bankroll >= vm.goal && vm.phase == DjPhase.RESULT) {
            CampaignComplete(
                goal = vm.goal,
                nextGoal = vm.goal * 100,
                onGoBigger = { vm.raiseGoal() },
                onStartOver = { vm.restartCampaign(); onGameOverExit() },
            )
        } else if (vm.bankroll < 25 && vm.phase == DjPhase.BETTING && vm.totalAtRisk == 0) {
            if (vm.campaign) {
                CampaignGameOver(onDone = onGameOverExit)
            } else {
                RebuyPrompt { vm.buyBackIn() }
            }
        }
    }
}

@Composable
private fun TopBar(vm: DjWildViewModel, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹ POKER",
            color = NeonBlue,
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
            color = IceBlue, fontSize = 14.sp, fontWeight = FontWeight.Black,
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
            .background(NeonBlue.copy(alpha = 0.28f))
    )
}

/**
 * Five cards a side. The dealer's stay down until the showdown, and a wild is
 * ringed so it can be picked out of the hand at a glance.
 */
@Composable
private fun HandRow(vm: DjWildViewModel, dealer: Boolean) {
    val cards = if (dealer) vm.dealerCards else vm.playerCards
    val faceUp = !dealer || vm.dealerRevealed
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (dealer) "DEALER" else "YOUR HAND",
            color = if (dealer) NeonBlue.copy(alpha = 0.75f) else IceBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.18.em,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { i ->
                val card = cards.getOrNull(i)
                if (card == null) {
                    EmptySlot()
                } else {
                    val wild = faceUp && DjWildDeck.isWild(card)
                    Box(
                        Modifier.then(
                            if (wild) Modifier.border(2.dp, IceBlue, RoundedCornerShape(6.dp))
                            else Modifier
                        )
                    ) {
                        PlayingCardView(card, faceUp = faceUp, scale = 1.045f)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySlot() {
    Box(
        Modifier
            // Matches a dealt card at the row's scale, so the row does not
            // jump width as the cards land.
            .size(width = 54.dp, height = 81.dp)
            .drawBehind {
                drawRoundRect(
                    color = Color(0x59FFFFFF),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(7.dp.toPx(), 5.dp.toPx())
                        ),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                )
            }
    )
}

@Composable
private fun MessageLine(vm: DjWildViewModel) {
    val s = vm.settlement
    val tone = when {
        vm.phase != DjPhase.RESULT -> P.OffWhite.copy(alpha = 0.9f)
        s == null -> P.OffWhite
        s.badBeatWin != null || s.tripsWin != null -> IceBlue
        s.outcome == DjWildRules.DjOutcome.WIN -> WinGreen
        s.outcome == DjWildRules.DjOutcome.PUSH -> P.OffWhite
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

/** What each bet did, once the hand is settled. */
@Composable
private fun ResultRows(vm: DjWildViewModel) {
    if (vm.phase != DjPhase.RESULT) return
    Spacer(Modifier.height(4.dp))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        vm.results.forEach { r ->
            Row(
                Modifier
                    .background(Color(0xCC050810), RoundedCornerShape(999.dp))
                    .border(
                        1.dp,
                        if (r.net > 0) IceBlue else P.OffWhite.copy(alpha = 0.25f),
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
 * Trips and Bad Beat ride either side of the middle, their ladders in the
 * gutters, with the ante, blind and play down the centre line.
 */
@Composable
private fun BetSpots(vm: DjWildViewModel, onShowPays: () -> Unit) {
    val betting = vm.phase == DjPhase.BETTING
    val spot = 50.dp
    val gap = 16.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The two side bets, each with its ladder in the gutter beside it.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gutter = (maxWidth - (spot * 2 + gap)) / 2
            Row(
                Modifier.align(Alignment.BottomCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                // Each spot stands under its own ladder — Bad Beat left,
                // Trips right — so the chips sit beside what they pay.
                DiamondSpot(
                    // Two lines: the diamond is taller than it is wide inside.
                    "BAD\nBEAT", IceBlue,
                    amount = if (betting) vm.badBeat else vm.badBeatStake,
                    size = spot,
                    onClick = { vm.addBadBeat() }.takeIf { betting },
                )
                DiamondSpot(
                    "TRIPS", IceBlue,
                    amount = if (betting) vm.trips else vm.tripsStake,
                    size = spot,
                    onClick = { vm.addTrips() }.takeIf { betting },
                )
            }
            FeltPayTable(
                title = "BAD BEAT",
                color = IceBlue,
                rows = DjWildRules.BadBeatPay.entries.map {
                    it.label to "${formatWhole(it.payout)}-to-1"
                },
                width = gutter - 6.dp,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp),
                onClick = onShowPays,
            )
            FeltPayTable(
                title = "TRIPS",
                color = IceBlue,
                // Two columns on this one, the way the felt prints it.
                rows = DjWildRules.TripsPay.entries.map {
                    it.label to (it.natural?.let { n -> "${it.wild} / ${formatWhole(n)}" }
                        ?: "${it.wild}")
                },
                width = gutter - 6.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp),
                onClick = onShowPays,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleSpot(
                "ANTE", NeonBlue,
                amount = if (betting) vm.ante else vm.anteStake,
                size = spot,
                onClick = { vm.addAnte() }.takeIf { betting },
            )
            Box(Modifier.width(gap), contentAlignment = Alignment.Center) {
                Text("=", color = NeonBlue.copy(alpha = 0.85f), fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
            // The blind always matches the ante, so it takes no chips itself.
            CircleSpot(
                "BLIND", NeonBlue,
                amount = if (betting) vm.ante else vm.blindStake,
                size = spot,
                onClick = null,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gutter = (maxWidth - (spot * 2 + gap)) / 2
            // One decision on this table, not three streets, so the play bet
            // needs a line rather than a ladder.
            Column(
                Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircleSpot("PLAY", NeonBlue, vm.playStake, spot, null)
                Text(
                    "PLAY = 2X ANTE",
                    color = NeonBlue.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            FeltPayTable(
                title = "BLIND",
                color = NeonBlue,
                rows = DjWildRules.BlindPay.entries.map {
                    it.label to "${formatWhole(it.payout)}-to-1"
                },
                width = gutter - 6.dp,
                modifier = Modifier.align(Alignment.BottomEnd),
                onClick = onShowPays,
            )
        }
    }
}

private fun formatWhole(v: Int): String = String.format(java.util.Locale.US, "%,d", v)

/** A pay table printed straight onto the felt, ruled heading and dotted leaders. */
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
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(1.5.dp.toPx(), 2.dp.toPx())
                        ),
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
private fun SpotContents(label: String, color: Color, amount: Int, size: Dp) {
    if (amount > 0) {
        PlacedBetChip(amount, size = size * 0.74f)
    } else {
        Text(
            label,
            color = color.copy(alpha = 0.75f),
            // Sized off the longest line, so a stacked label keeps full size.
            fontSize = if (label.lines().maxOf { it.length } > 6) 8.sp else 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.06.em,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChipRail(vm: DjWildViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val racked = chipsFor(vm.bankroll, vm.limits)
        // A chip picked in a richer room must not follow the player down.
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
                selectedColor = IceBlue,
            )
        }
    }
}

@Composable
private fun Actions(vm: DjWildViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (vm.phase) {
            DjPhase.BETTING -> {
                // No undo: CLEAR takes the whole bet back, and the row matches
                // the result row's shape — quiet on the left, the go on the right.
                ActionButton("CLEAR", NeonBlue, false, Modifier.weight(1f), vm::clearBets)
                ActionButton("DEAL", IceBlue, true, Modifier.weight(1.4f), vm::deal)
            }
            DjPhase.DECISION -> {
                ActionButton("FOLD", LossRed, false, Modifier.weight(1f), vm::fold)
                if (vm.canTakeMarker) {
                    // Short of the play bet: the pit lends at the felt rather
                    // than fold a good hand for want of chips.
                    ActionButton(
                        "MARKER +5,000", P.GoldTrim, true, Modifier.weight(1.6f),
                        vm::takeMarker,
                    )
                } else {
                    ActionButton(
                        "PLAY  ${formatWhole(vm.playCost)}", IceBlue, true, Modifier.weight(1.6f),
                        vm::play.takeIf { vm.canPlay },
                    )
                }
            }
            DjPhase.RESULT -> {
                ActionButton("NEW BET", NeonBlue, false, Modifier.weight(1f)) { vm.nextHand(false) }
                ActionButton("REBET", IceBlue, true, Modifier.weight(1.4f)) { vm.nextHand(true) }
            }
            else -> {
                Spacer(Modifier.weight(1f))
                Text(
                    "DEALING",
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

/**
 * Free play has no campaign to lose, so a bust is just a refill — but it still
 * has to be offered, or the table sits there refusing every press.
 */
@Composable
private fun RebuyPrompt(onBuyIn: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC02050A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("OUT OF CHIPS", color = LossRed, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            Text(
                "BUY BACK IN",
                color = IceBlue, fontSize = 14.sp, fontWeight = FontWeight.Black,
                modifier = Modifier
                    .border(1.5.dp, NeonBlue, RoundedCornerShape(999.dp))
                    .clickable(onClick = onBuyIn)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/** Tap any ladder and all three fill the screen, since they are set small. */
@Composable
private fun PayTables(onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xF202050A)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp)
                .background(Color(0xF2050810), RoundedCornerShape(14.dp))
                .border(1.5.dp, NeonBlue.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            PayList(
                "TRIPS · WILD / NATURAL", IceBlue,
                DjWildRules.TripsPay.entries.map {
                    it.label to (it.natural?.let { n -> "${it.wild} / ${formatWhole(n)}" }
                        ?: "${it.wild}")
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "A hand holding a 2 not used as a wild counts as natural.",
                color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp,
            )
            Spacer(Modifier.height(12.dp))
            PayList(
                "BAD BEAT", IceBlue,
                DjWildRules.BadBeatPay.entries.map { it.label to "${formatWhole(it.payout)}-to-1" },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pays the hand that lost the showdown, either side.",
                color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp,
            )
            Spacer(Modifier.height(12.dp))
            PayList(
                "BLIND", NeonBlue,
                DjWildRules.BlindPay.entries.map { it.label to "${formatWhole(it.payout)}-to-1" },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Trips or less is a push. Every deuce and the joker play wild.",
                color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp,
            )
        }
    }
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
