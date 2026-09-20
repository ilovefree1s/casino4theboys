package com.example.casinogames.games.craps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.casinogames.games.destroyer.DiceTray
import com.example.casinogames.games.destroyer.DiceTrayState
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
import com.example.casinogames.ui.common.CasinoChip
import com.example.casinogames.ui.common.FreePlayBuyIn
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P
import kotlin.random.Random

/** The felt: a deep jade, nothing like hold'em's acid green. */
private val FeltDeep = Color(0xFF07231B)
private val FeltJade = Color(0xFF0E4634)
private val FeltLine = Color(0xFFEFE7D2)
val CrapsRed = Color(0xFFE04B42)
private val CrapsGold = Color(0xFFE8C169)
private val WinGreen = Color(0xFF57E06A)
private val LossRed = Color(0xFFFF6A5E)

@Composable
fun CrapsScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = {},
    vm: CrapsViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    var showProps by remember { mutableStateOf(false) }
    var showPays by remember { mutableStateOf(false) }

    // The dice are Destroyer's tray, which already throws and settles a pair.
    val tray = remember { DiceTrayState() }
    LaunchedEffect(Unit) {
        tray.onSettle = { values -> vm.settleRoll(values[0], values[1]) }
    }

    Box(Modifier.fillMaxSize().background(FeltDeep)) {
        Image(
            painter = painterResource(R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.16f),
            contentScale = ContentScale.Crop,
        )
        // One column, with the throwing area taking whatever the felt and the
        // rail do not want. A taller tray is a better throw, so the space a
        // big phone has spare goes there.
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(vm, onBack)
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x4D000000))
                    .border(1.5.dp, FeltLine.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
            ) {
                DiceTray(
                    tray, Modifier.fillMaxSize(),
                    dieSize = 46.dp,
                    faceColor = Color(0xFFF7F3EA),
                    pipColor = Color(0xFF0A2019),
                    lineColor = FeltLine.copy(alpha = 0.45f),
                    guide = "FLICK FROM BELOW THE LINE",
                )
            }
            Spacer(Modifier.height(4.dp))
            Headline(vm)
            MessageLine(vm)
            ResultPills(vm)
            Spacer(Modifier.height(6.dp))
            Felt(vm, onProps = { showProps = true }, onPays = { showPays = true })
            Spacer(Modifier.height(6.dp))
            ChipRail(vm)
            Spacer(Modifier.height(6.dp))
            Actions(vm, tray)
        }

        if (showProps) PropsDrawer(vm) { showProps = false }
        if (showPays) PayTables { showPays = false }
        if (vm.campaign && vm.bankroll >= vm.goal && vm.phase == CrapsPhase.BETTING) {
            CampaignComplete(
                goal = vm.goal,
                nextGoal = vm.goal * 100,
                onGoBigger = { vm.raiseGoal() },
                onStartOver = { vm.restartCampaign(); onGameOverExit() },
            )
        } else if (vm.bankroll < 25 && vm.phase == CrapsPhase.BETTING && vm.totalAtRisk == 0) {
            if (vm.campaign) {
                CampaignGameOver(onDone = onGameOverExit)
            } else {
                FreePlayBuyIn(onDismiss = {}, onConfirm = { FreePlay.buyIn = it; vm.buyBackIn() })
            }
        }
    }
}

@Composable
private fun TopBar(vm: CrapsViewModel, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹ CRAPS",
            color = CrapsGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        Text("BANKROLL ", color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(formatMoney(vm.bankroll), color = CrapsGold, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(10.dp))
        Text("AT RISK ", color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(vm.totalAtRisk.toString(), color = P.OffWhite, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

/** The call over the tray: the number just thrown, and what it did. */
@Composable
private fun Headline(vm: CrapsViewModel) {
    val roll = vm.lastRoll
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (roll != null) {
            Text(
                "${roll.total}",
                color = if (vm.lastRoll?.total == 7 && !vm.comeOut) LossRed else CrapsGold,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            vm.headline.ifEmpty { "CRAPLESS CRAPS" },
            color = FeltLine,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.1.em,
        )
    }
}

@Composable
private fun MessageLine(vm: CrapsViewModel) {
    Text(
        vm.message,
        color = P.OffWhite.copy(alpha = 0.8f),
        fontSize = 12.sp,
        fontStyle = FontStyle.Italic,
        textAlign = TextAlign.Center,
    )
}

/** A throw settles many bets at once, so the lines run across, not down. */
@Composable
private fun ResultPills(vm: CrapsViewModel) {
    if (vm.results.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        vm.results.forEach { r ->
            Row(
                Modifier
                    .background(Color(0xCC061A14), RoundedCornerShape(999.dp))
                    .border(
                        1.dp,
                        if (r.net > 0) CrapsGold else FeltLine.copy(alpha = 0.22f),
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 9.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(r.label, color = P.OffWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    when {
                        r.net > 0 -> "+${formatMoney(r.net)}"
                        r.net < 0 -> "−${formatMoney(-r.net)}"
                        else -> "·"
                    },
                    color = if (r.net > 0) WinGreen else if (r.net < 0) LossRed else P.OffWhite.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * One end of the table, which is all a single player ever reaches. Ten point
 * boxes in two rows of five, mirrored around the seven that is missing from
 * them, then the come, the field and the line.
 */
@Composable
private fun Felt(vm: CrapsViewModel, onProps: () -> Unit, onPays: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FeltJade)
            .border(2.dp, FeltLine.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        NumberRow(vm, listOf(2, 3, 4, 5, 6))
        NumberRow(vm, listOf(8, 9, 10, 11, 12))
        Band(
            label = "COME",
            sub = "seven wins · any number travels",
            amount = vm.bets.comeFlat,
            color = FeltLine,
            enabled = !vm.comeOut,
            onClick = { vm.addCome() },
        )
        Band(
            label = "FIELD",
            sub = "2 3 4 9 10 11 12 · two pays double, twelve triple",
            amount = vm.bets.field,
            color = CrapsGold,
            enabled = true,
            onClick = { vm.addField() },
        )
        // The line and the odds behind it, side by side.
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.weight(1.6f)) {
                Band(
                    label = "PASS LINE",
                    sub = if (vm.comeOut) "seven wins · nothing loses" else "point ${vm.point}",
                    amount = vm.bets.pass,
                    color = CrapsRed,
                    enabled = vm.comeOut,
                    onClick = { vm.addPass() },
                )
            }
            Box(Modifier.weight(1f)) {
                Band(
                    label = "ODDS",
                    sub = vm.point?.let {
                        val (n, d) = CrapsRules.oddsPay(it)
                        "$n to $d"
                    } ?: "free odds",
                    amount = vm.bets.passOdds,
                    color = FeltLine,
                    enabled = vm.point != null && vm.bets.pass > 0,
                    onClick = { vm.addPassOdds() },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            SmallButton("PROPS  ▸", Modifier.weight(1f), onProps)
            SmallButton("PAYS  ▸", Modifier.weight(1f), onPays)
        }
    }
}

@Composable
private fun NumberRow(vm: CrapsViewModel, numbers: List<Int>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        numbers.forEach { n -> Box(Modifier.weight(1f)) { NumberBox(vm, n) } }
    }
}

/**
 * A point box. The painted numeral carries the place bet over it; a come bet
 * that has travelled here wears its own small chip in the corner, and tapping
 * that chip lays the odds behind it.
 */
@Composable
private fun NumberBox(vm: CrapsViewModel, n: Int) {
    val isPoint = vm.point == n
    val placed = vm.bets.place[n] ?: 0
    val come = vm.bets.comePoints[n]
    // Place bets sleep through a come out, the way the dealer turns them off.
    val asleep = vm.comeOut && placed > 0
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (isPoint) CrapsRed.copy(alpha = 0.22f) else Color(0x33000000))
            .border(
                if (isPoint) 2.dp else 1.dp,
                if (isPoint) CrapsRed else FeltLine.copy(alpha = 0.45f),
                RoundedCornerShape(7.dp),
            )
            .clickable(enabled = vm.phase == CrapsPhase.BETTING) { vm.addPlace(n) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$n",
            color = FeltLine.copy(alpha = if (placed > 0) 0.25f else 0.8f),
            fontSize = 25.sp,
            fontWeight = FontWeight.Black,
        )
        if (placed > 0) {
            Box(Modifier.alpha(if (asleep) 0.4f else 1f)) {
                PlacedBetChip(placed, size = 34.dp)
            }
        }
        if (asleep) {
            Text(
                "OFF",
                color = LossRed,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp),
            )
        }
        // The come bet's own chip, and the target for laying its odds.
        if (come != null) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(1.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xCC061A14))
                    .border(1.dp, CrapsGold, RoundedCornerShape(5.dp))
                    .clickable(enabled = vm.phase == CrapsPhase.BETTING) { vm.addComeOdds(n) }
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "C ${come.flat}",
                    color = CrapsGold, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1,
                )
                if (come.odds > 0) {
                    Text(
                        "+${come.odds}",
                        color = FeltLine, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                }
            }
        }
        // The puck, sat on the number the shooter is chasing.
        if (isPoint) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(FeltLine)
                    .border(1.5.dp, CrapsRed, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("ON", color = Color(0xFF0A2019), fontSize = 7.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** A painted band across the felt: a name, its small print and any chip on it. */
@Composable
private fun Band(
    label: String,
    sub: String,
    amount: Int,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(color.copy(alpha = if (enabled) 0.16f else 0.05f), Color(0x22000000))
                )
            )
            .border(1.dp, color.copy(alpha = if (enabled) 0.7f else 0.22f), RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                label,
                color = color.copy(alpha = if (enabled) 1f else 0.4f),
                fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.1.em,
            )
            Text(
                sub,
                color = P.OffWhite.copy(alpha = if (enabled) 0.6f else 0.25f),
                fontSize = 8.sp, maxLines = 1,
            )
        }
        if (amount > 0) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                PlacedBetChip(amount, size = 34.dp)
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, FeltLine.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = FeltLine, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.08.em)
    }
}

@Composable
private fun ChipRail(vm: CrapsViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val racked = chipsFor(vm.bankroll, vm.limits)
        LaunchedEffect(racked) {
            if (racked.none { it.value == vm.selectedChip }) vm.selectedChip = racked.last().value
        }
        racked.forEach { chip ->
            CasinoChip(
                imageRes = chip.imageRes,
                contentDescription = "${chip.value} chip",
                selected = vm.selectedChip == chip.value,
                onClick = { vm.selectedChip = chip.value },
                selectedColor = CrapsGold,
            )
        }
    }
}

@Composable
private fun Actions(vm: CrapsViewModel, tray: DiceTrayState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.phase == CrapsPhase.ROLLING) {
            Spacer(Modifier.weight(1f))
            Text(
                "IN THE AIR",
                color = P.OffWhite.copy(alpha = 0.7f),
                fontWeight = FontWeight.Black, letterSpacing = 0.16.em,
            )
            Spacer(Modifier.weight(1f))
            return@Row
        }
        if (vm.canRebet) {
            ActionButton("SAME BET", CrapsGold, false, Modifier.weight(1f), vm::rebet)
        } else {
            ActionButton("TAKE DOWN", FeltLine, false, Modifier.weight(1f), vm::clearBets)
        }
        ActionButton(
            if (vm.comeOut) "COME OUT" else "ROLL",
            CrapsRed, true, Modifier.weight(1.4f),
            {
                vm.rolling()
                tray.launch(0.45f + Random.nextFloat() * 0.35f)
            }.takeIf { vm.canRoll },
        )
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
            .background(if (solid) color.copy(alpha = if (live) 0.2f else 0.06f) else Color.Transparent)
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
            fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.1.em,
        )
    }
}

/** The centre of the table: the hardways and the one-roll props. */
@Composable
private fun PropsDrawer(vm: CrapsViewModel, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xF207231B)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .padding(20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF20B3428))
                .border(1.5.dp, FeltLine.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "HARDWAYS", color = CrapsGold, fontSize = 12.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.1.em,
            )
            Text(
                "The pair before the easy way, and before a seven.",
                color = P.OffWhite.copy(alpha = 0.55f), fontSize = 9.sp,
            )
            Spacer(Modifier.height(6.dp))
            CrapsRules.HARD_WAYS.forEach { n ->
                PropRow(
                    "Hard $n",
                    "${CrapsRules.hardPay(n)} to 1",
                    vm.bets.hard[n] ?: 0,
                ) { vm.addHard(n) }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "ONE ROLL", color = CrapsGold, fontSize = 12.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.1.em,
            )
            Text(
                "Settled on the very next throw, win or lose.",
                color = P.OffWhite.copy(alpha = 0.55f), fontSize = 9.sp,
            )
            Spacer(Modifier.height(6.dp))
            Prop.entries.forEach { p ->
                PropRow(p.label, "${p.pays} to 1", vm.bets.props[p] ?: 0) { vm.addProp(p) }
            }
            Spacer(Modifier.height(12.dp))
            SmallButton("CLOSE", Modifier.fillMaxWidth(), onDismiss)
        }
    }
}

@Composable
private fun PropRow(label: String, pays: String, amount: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (amount > 0) CrapsGold.copy(alpha = 0.12f) else Color(0x22000000))
            .border(
                1.dp,
                if (amount > 0) CrapsGold else FeltLine.copy(alpha = 0.25f),
                RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = P.OffWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (amount > 0) {
            Text(
                formatMoney(amount.toDouble()),
                color = CrapsGold, fontSize = 12.sp, fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(pays, color = FeltLine.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

/** Every ladder on the table, and the one rule that makes this game its own. */
@Composable
private fun PayTables(onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xF207231B)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .padding(20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF20B3428))
                .border(1.5.dp, FeltLine.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "CRAPLESS CRAPS", color = CrapsRed, fontSize = 14.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.1.em,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Seven wins the come out and nothing loses it. Two, three, eleven and " +
                    "twelve are points here, not craps and a natural, so there is no don't " +
                    "pass and no don't come. That comfort is paid for on the line, which is " +
                    "why the free odds behind it matter more here than anywhere.",
                color = P.OffWhite.copy(alpha = 0.6f), fontSize = 10.sp,
            )
            Spacer(Modifier.height(12.dp))
            PayList(
                "FREE ODDS", FeltLine,
                CrapsRules.POINTS.distinctBy { CrapsRules.oddsPay(it) }.map {
                    val (n, d) = CrapsRules.oddsPay(it)
                    val pair = CrapsRules.POINTS.filter { p -> CrapsRules.oddsPay(p) == (n to d) }
                    pair.joinToString(" or ") to "$n to $d"
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "No edge at all, capped at ${CrapsRules.MAX_ODDS_MULTIPLE}x the flat bet.",
                color = P.OffWhite.copy(alpha = 0.55f), fontSize = 9.sp,
            )
            Spacer(Modifier.height(12.dp))
            PayList(
                "PLACE", CrapsGold,
                CrapsRules.POINTS.distinctBy { CrapsRules.placePay(it) }.map {
                    val (n, d) = CrapsRules.placePay(it)
                    val pair = CrapsRules.POINTS.filter { p -> CrapsRules.placePay(p) == (n to d) }
                    pair.joinToString(" or ") to "$n to $d"
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Off on the come out, the way the dealer turns them down.",
                color = P.OffWhite.copy(alpha = 0.55f), fontSize = 9.sp,
            )
            Spacer(Modifier.height(12.dp))
            PayList(
                "FIELD", CrapsGold,
                listOf(
                    "Two" to "2 to 1",
                    "Twelve" to "3 to 1",
                    "3, 4, 9, 10 or 11" to "1 to 1",
                ),
            )
            Spacer(Modifier.height(12.dp))
            PayList("HARDWAYS", CrapsGold, CrapsRules.HARD_WAYS.map { "Hard $it" to "${CrapsRules.hardPay(it)} to 1" })
            Spacer(Modifier.height(12.dp))
            PayList("ONE ROLL", CrapsGold, Prop.entries.map { it.label to "${it.pays} to 1" })
            Spacer(Modifier.height(12.dp))
            SmallButton("CLOSE", Modifier.fillMaxWidth(), onDismiss)
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
