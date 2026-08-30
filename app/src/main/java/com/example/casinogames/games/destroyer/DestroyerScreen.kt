package com.example.casinogames.games.destroyer

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
import androidx.compose.ui.graphics.Color
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
import com.example.casinogames.campaign.MARKER_AMOUNT
import com.example.casinogames.games.destroyer.DestroyerRules.GRID
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
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
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopBar(vm, onBack)
                Spacer(Modifier.height(10.dp))
                Board(vm)
                Spacer(Modifier.height(8.dp))
                StatusRow(vm, onShowPays = { showPays = true })
                Spacer(Modifier.height(4.dp))
                MessageLine(vm)
                ResultRows(vm)
            }
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BetRow(vm)
                Spacer(Modifier.height(6.dp))
                if (vm.phase == DzPhase.BETTING) ChipRail(vm)
                Spacer(Modifier.height(6.dp))
                Actions(vm)
            }
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
                RebuyPrompt { vm.buyBackIn() }
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

/**
 * The player's own waters: their ships in outline, red pegs where the dice
 * hit them, pale pegs in open sea. A sunk ship's whole hull goes red.
 */
@Composable
private fun Board(vm: DestroyerViewModel) {
    val hits = vm.hitCells.toSet()
    val misses = vm.missCells.toSet()
    val shipCells = vm.fleet.flatMap { it.cells }.toSet()
    val sunkCells = vm.fleet
        .filter { ship -> ship.cells.all { it in hits } }
        .flatMap { it.cells }.toSet()
    val target = vm.lastRoll?.let { (r, c) -> r * GRID + c }

    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF06111C))
            .border(1.5.dp, SeaLine, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(GRID) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(GRID) { col ->
                    val cell = row * GRID + col
                    val onShip = cell in shipCells
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    cell in sunkCells -> HitRed.copy(alpha = 0.30f)
                                    onShip -> Steel.copy(alpha = 0.16f)
                                    else -> Color(0x14FFFFFF)
                                }
                            )
                            .border(
                                if (cell == target) 2.dp else 1.dp,
                                when {
                                    cell == target -> Brass
                                    cell in sunkCells -> HitRed
                                    onShip -> SteelDim
                                    else -> Color(0x1AFFFFFF)
                                },
                                RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            cell in hits -> Peg(HitRed)
                            cell in misses -> Peg(Color(0x66F5F1E8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Peg(color: Color) {
    Box(Modifier.size(14.dp).clip(CircleShape).background(color))
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
            if (roll == null) "— · —" else "${roll.first + 1} · ${roll.second + 1}",
            color = Steel, fontSize = 13.sp, fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            "PAY TABLE",
            color = SteelDim, fontSize = 10.sp, fontWeight = FontWeight.Black,
            letterSpacing = 0.08.em,
            modifier = Modifier.clickable(onClick = onShowPays).padding(4.dp),
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
                ActionButton(
                    if (vm.rolling) "…" else "FIRE", HitRed, true, Modifier.weight(1f),
                    vm::roll.takeIf { !vm.rolling },
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

@Composable
private fun RebuyPrompt(onBuyIn: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE6000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("OUT OF CHIPS", color = HitRed, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            Text(
                "BUY BACK IN (${formatMoney(MARKER_AMOUNT)})",
                color = Color(0xFF050408),
                fontSize = 13.sp, fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brass)
                    .clickable(onClick = onBuyIn)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}
