package com.example.casinogames.games.holdem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
import com.example.casinogames.ui.common.CasinoChip
import com.example.casinogames.ui.common.EmptyCardSlot
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.PlayingCardView
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P

private val TableBlack = Color(0xFF040308)
private val NeonPurple = Color(0xFFB14BFF)
private val NeonPurpleDim = Color(0x998B30D9)
private val NeonPink = Color(0xFFFF3FD8)
private val FeltGreen = Color(0xFF39D98A)

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
            modifier = Modifier.fillMaxSize().alpha(0.4f),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(vm, onBack)
            Text(
                "ULTIMATE TEXAS HOLD'EM · PAY TABLES",
                fontSize = 11.sp,
                letterSpacing = 0.14.em,
                fontWeight = FontWeight.Black,
                color = NeonPurple.copy(alpha = 0.85f),
                modifier = Modifier
                    .clickable { showPays = true }
                    .padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(4.dp))
            DealerRow(vm)
            Spacer(Modifier.height(10.dp))
            BoardRow(vm)
            Spacer(Modifier.height(10.dp))
            PlayerRow(vm)
            Spacer(Modifier.height(8.dp))
            MessageLine(vm)
            ResultRows(vm)
            Spacer(Modifier.weight(1f))
            BetSpots(vm)
            Spacer(Modifier.height(10.dp))
            if (vm.phase == UthPhase.BETTING) ChipRail(vm)
            Spacer(Modifier.height(10.dp))
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
        Modifier.fillMaxWidth().padding(top = 6.dp),
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
                vm.dealerCards.forEach { PlayingCardView(it, faceUp = vm.dealerRevealed) }
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
                PlayingCardView(card, faceUp = i < vm.boardRevealed)
            }
        }
    }
}

@Composable
private fun PlayerRow(vm: UltimateHoldemViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.playerCards.isEmpty()) {
                repeat(2) { EmptyCardSlot() }
            } else {
                vm.playerCards.forEach { PlayingCardView(it, faceUp = true) }
            }
        }
        Spacer(Modifier.height(6.dp))
        val hand = vm.settlement?.playerHand?.category?.label
        Label("YOUR HAND", NeonPurple, hand)
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
    val won = vm.phase == UthPhase.RESULT && vm.results.sumOf { it.net } > 0
    Text(
        vm.message,
        fontSize = 15.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        color = if (won) FeltGreen else P.OffWhite.copy(alpha = 0.92f),
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
                    color = if (r.net > 0) FeltGreen else if (r.net < 0) NeonPink
                    else P.OffWhite.copy(alpha = 0.6f),
                    fontSize = 12.sp, fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun BetSpots(vm: UltimateHoldemViewModel) {
    val betting = vm.phase == UthPhase.BETTING
    Row(
        Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Spot(
            "TRIPS", NeonPink,
            amount = if (betting) vm.trips else vm.tripsStake,
            modifier = Modifier.weight(1f),
            onClick = { vm.addTrips() }.takeIf { betting },
        )
        Spot(
            "ANTE", NeonPurple,
            amount = if (betting) vm.ante else vm.anteStake,
            modifier = Modifier.weight(1f),
            onClick = { vm.addAnte() }.takeIf { betting },
        )
        Spot(
            "BLIND", NeonPurple,
            amount = if (betting) vm.ante else vm.blindStake,
            modifier = Modifier.weight(1f),
            onClick = null,
        )
        Spot(
            "PLAY", FeltGreen,
            amount = vm.playStake,
            modifier = Modifier.weight(1f),
            onClick = null,
        )
    }
}

@Composable
private fun Spot(
    label: String,
    color: Color,
    amount: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .border(1.5.dp, if (amount > 0) color else color.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (amount > 0) PlacedBetChip(amount, size = 46.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (amount > 0) color else color.copy(alpha = 0.55f),
            fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.1.em,
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
                BlindPay.entries.map {
                    it.label to if (it.multiplier == 1.5) "3 to 2" else "${it.multiplier.toInt()} to 1"
                },
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
