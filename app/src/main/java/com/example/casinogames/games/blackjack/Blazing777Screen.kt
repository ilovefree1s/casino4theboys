package com.example.casinogames.games.blackjack

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import com.example.casinogames.ui.common.OutlinedText
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.PlayingCardView
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P

private val NeonPurple = Color(0xFF8B30D9)
private val NeonBlue = Color(0xFF2E7BFF)
private val NeonMagenta = Color(0xFFFF3FD8)
private val HotPink = Color(0xFFFF1493)
private val BlazeOrange = Color(0xFFFF7A1A)
private val TableBlack = Color(0xFF040308)

@Composable
fun Blazing777Screen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = onBack,
    vm: Blazing777ViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    var showPayTable by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(TableBlack)) {
        Image(
            painter = painterResource(R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.45f),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .navigationBarsPadding()
                .padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onBack, vm)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x26FFFFFF)))
            Spacer(Modifier.height(6.dp))
            // The pay tables live behind this line now that the spots carry no labels.
            Text(
                if (vm.campaign) "CAMPAIGN · GOAL \$${formatMoney(vm.goal)}"
                else "BLAZING 777s · 8 DECKS · ${vm.shoeCount} CARDS IN SHOE",
                fontSize = 10.sp,
                letterSpacing = 0.24.em,
                color = if (vm.campaign) Color(0xCCFFD24D) else Color(0x8CFFFFFF),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { showPayTable = true }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            DealerArea(vm)
            Spacer(Modifier.height(8.dp))
            MessageLine(vm)
            SidePills(vm)
            Spacer(Modifier.weight(1f))
            PlayerArea(vm)
            Spacer(Modifier.weight(1f))
            if (vm.phase == BjPhase.BETTING) {
                BetSpots(vm, onShowPayTable = { showPayTable = true })
                Spacer(Modifier.height(12.dp))
                ChipRack(vm)
                Spacer(Modifier.height(10.dp))
            } else if (vm.playerHands.size < 3) {
                BetSpots(vm, onShowPayTable = { showPayTable = true })
                Spacer(Modifier.height(10.dp))
            }
            ActionButtons(vm)
        }
        if (showPayTable) PayTableOverlay { showPayTable = false }
        if (vm.campaign && vm.phase == BjPhase.BETTING &&
            vm.bankroll < 25 && vm.totalAtRisk == 0
        ) {
            CampaignGameOver(onStartOver = {
                vm.buyBackIn()
                onGameOverExit()
            })
        }
        if (vm.campaign && vm.phase == BjPhase.BETTING &&
            vm.bankroll >= vm.goal && vm.totalAtRisk == 0
        ) {
            CampaignComplete(
                goal = vm.goal,
                nextGoal = vm.goal * 100,
                onGoBigger = vm::raiseGoal,
                onStartOver = {
                    vm.restartCampaign()
                    onGameOverExit()
                },
            )
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, vm: Blazing777ViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹ BLACKJACK",
            color = P.OffWhite.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.1.em,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "BANKROLL ",
                    fontSize = 9.sp, letterSpacing = 0.12.em,
                    color = P.OffWhite.copy(alpha = 0.6f),
                )
                OutlinedText(
                    formatMoney(vm.bankroll),
                    fontSize = 14.sp, color = P.WinGlow, outlineWidth = 1.dp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "STAKED ",
                    fontSize = 9.sp, letterSpacing = 0.12.em,
                    color = P.OffWhite.copy(alpha = 0.6f),
                )
                OutlinedText(
                    formatMoney(vm.totalAtRisk.toDouble()),
                    fontSize = 14.sp, color = P.OffWhite, outlineWidth = 1.dp,
                )
            }
        }
    }
}

@Composable
private fun DealerArea(vm: Blazing777ViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.fb_header_dealer),
                contentDescription = "Dealer",
                modifier = Modifier.width(300.dp),
                contentScale = ContentScale.FillWidth,
            )
            val visible = if (vm.holeRevealed) vm.dealerCards else vm.dealerCards.take(1)
            if (visible.isNotEmpty()) {
                val t = BlackjackCore.total(visible)
                TotalBadge(
                    text = if (vm.holeRevealed && BlackjackCore.isBlackjack(vm.dealerCards)) "BJ"
                    else "$t",
                    color = if (vm.holeRevealed && BlackjackCore.total(vm.dealerCards) > 21)
                        Color(0xFF8E2B1E) else NeonPurple,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (vm.dealerCards.isEmpty()) {
                EmptyCardSlot()
            } else {
                vm.dealerCards.forEachIndexed { i, card ->
                    PlayingCardView(card, faceUp = i == 0 || vm.holeRevealed)
                }
            }
        }
    }
}

@Composable
private fun TotalBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(color, RoundedCornerShape(999.dp))
            .border(2.dp, P.Ink, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 1.dp)
    ) {
        Text(text, color = P.OffWhite, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MessageLine(vm: Blazing777ViewModel) {
    val win = vm.phase == BjPhase.RESULT && vm.results.sumOf { it.net } > 0
    val text = if (vm.phase == BjPhase.BETTING) "⚡  ${vm.message}  ⚡" else vm.message
    Text(
        text,
        fontSize = 15.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        color = if (win) P.WinGlow else P.OffWhite.copy(alpha = 0.92f),
        textAlign = TextAlign.Center,
    )
}

/** Side-bet hits announce themselves the moment the deal lands. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SidePills(vm: Blazing777ViewModel) {
    val live = vm.phase != BjPhase.BETTING
    // Blazing 7s is on the house, so it announces itself whether or not TriLux is up.
    val blazing = vm.blazingWin?.takeIf { live }
    val trilux = vm.triluxWin?.takeIf { live && vm.triluxStake > 0 }
    if (blazing == null && trilux == null) return
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp).widthIn(max = 400.dp),
    ) {
        // The free bet quotes no odds — it isn't paying yet.
        blazing?.let { HitPill(it.label, BlazeOrange) }
        trilux?.let { HitPill("${it.label} · ${it.payout}:1", NeonMagenta) }
    }
}

@Composable
private fun HitPill(text: String, color: Color) {
    Text(
        text,
        fontSize = 11.sp, fontWeight = FontWeight.Black, color = color,
        modifier = Modifier
            .background(Color(0xE60E0A0B), RoundedCornerShape(999.dp))
            .border(1.5.dp, color, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun PlayerArea(vm: Blazing777ViewModel) {
    if (vm.playerHands.isEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            YourHandHeader(badge = null, badgeColor = NeonBlue)
            Spacer(Modifier.height(8.dp))
            EmptyCardSlot()
        }
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        vm.playerHands.withIndex().chunked(2).forEach { rowHands ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Top,
            ) {
                rowHands.forEach { (i, hand) -> PlayerHandColumn(vm, i, hand) }
            }
        }
    }
}

@Composable
private fun YourHandHeader(badge: String?, badgeColor: Color) {
    Box(contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.fb_header_yourhand),
            contentDescription = "Your hand",
            modifier = Modifier.width(300.dp),
            contentScale = ContentScale.FillWidth,
        )
        if (badge != null) {
            TotalBadge(badge, badgeColor, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun PlayerHandColumn(vm: Blazing777ViewModel, i: Int, hand: BjHand) {
    val active = vm.phase == BjPhase.PLAYER_TURN && vm.activeHand == i && !hand.done
    val total = BlackjackCore.total(hand.cards)
    val bust = BlackjackCore.isBust(hand.cards)
    val single = vm.playerHands.size == 1
    Column(
        Modifier.alpha(
            if (!single && !active && vm.phase == BjPhase.PLAYER_TURN) 0.35f else 1f
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val badge = when {
            hand.cards.isEmpty() -> null
            single && BlackjackCore.isBlackjack(hand.cards) -> "BJ"
            else -> "$total"
        }
        val badgeColor = if (bust) Color(0xFF8E2B1E) else NeonBlue
        if (single) {
            YourHandHeader(badge = badge, badgeColor = badgeColor)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (active) Text("▶", fontSize = 11.sp, color = P.WinGlow)
                OutlinedText(
                    "HAND ${i + 1}",
                    fontSize = 15.sp,
                    color = NeonBlue,
                    letterSpacing = 0.08.em,
                    outlineWidth = 1.5.dp,
                )
                if (badge != null) TotalBadge(badge, badgeColor)
                if (hand.doubled) {
                    Text(
                        "2×",
                        fontSize = 9.sp, fontWeight = FontWeight.Black, color = P.Ink,
                        modifier = Modifier
                            .background(P.GoldTrim, RoundedCornerShape(999.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                hand.cards.forEach { card -> PlayingCardView(card, faceUp = true) }
            }
            if (vm.phase == BjPhase.RESULT) {
                vm.results.getOrNull(i)?.let { r -> ResultPill(r) }
            }
        }
    }
}

@Composable
private fun ResultPill(r: BjResult) {
    val won = r.net > 0
    Row(
        Modifier
            .background(Color(0xE60E0A0B), RoundedCornerShape(999.dp))
            .border(
                1.5.dp,
                if (won) NeonMagenta else P.OffWhite.copy(alpha = 0.35f),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(r.label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = P.OffWhite)
        Text(
            when {
                r.net > 0 -> "+${formatMoney(r.net)}"
                r.net < 0 -> "−${formatMoney(-r.net)}"
                else -> "push"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = when {
                r.net > 0 -> P.WinGlow
                r.net < 0 -> Color(0xFFFF9C8A)
                else -> P.OffWhite.copy(alpha = 0.7f)
            },
        )
    }
}

@Composable
private fun BetSpots(vm: Blazing777ViewModel, onShowPayTable: () -> Unit) {
    val betting = vm.phase == BjPhase.BETTING
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.fb_spot_bet),
                contentDescription = "Bet spot",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .clickable(enabled = betting) { vm.addChip() },
                contentScale = ContentScale.Fit,
            )
            // Once dealt, the stake lives on the hand rather than the spot.
            PlacedBetChip(if (betting) vm.bet else vm.playerHands.firstOrNull()?.stake ?: 0)
        }
        SideSpot(
            label = "TRI\nLUX",
            color = NeonMagenta,
            amount = if (betting) vm.triluxBet else vm.triluxStake,
            enabled = betting,
            onClick = vm::addTriluxChip,
        )
    }
}

@Composable
private fun SideSpot(
    label: String,
    color: Color,
    amount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(74.dp)
                .background(Color(0x66000000), CircleShape)
                .border(2.dp, color.copy(alpha = 0.85f), CircleShape)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 12.sp,
                letterSpacing = 0.06.em,
                textAlign = TextAlign.Center,
            )
        }
        PlacedBetChip(amount, size = 44.dp)
    }
}

@Composable
private fun ChipRack(vm: Blazing777ViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chipsFor(vm.bankroll).forEach { chip ->
            CasinoChip(
                imageRes = chip.imageRes,
                contentDescription = "${chip.value} chip",
                selected = vm.selectedChip == chip.value,
                onClick = { vm.selectedChip = chip.value },
                selectedColor = HotPink,
            )
        }
    }
}

@Composable
private fun ActionButtons(vm: Blazing777ViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(50.dp),
    ) {
        when (vm.phase) {
            BjPhase.BETTING -> {
                ImgButton(R.drawable.btn_deal, "Deal", vm::deal)
                ImgButton(R.drawable.btn_undo, "Undo", vm::undoChip)
                ImgButton(R.drawable.btn_clear, "Clear", vm::clearBet)
                if (!vm.campaign && vm.bankroll < 25 && vm.bet == 0) {
                    PillButton("Buy back in", onClick = vm::buyBackIn)
                }
            }
            BjPhase.PLAYER_TURN -> {
                ImgButton(R.drawable.btn_hit, "Hit", vm::hit)
                ImgButton(R.drawable.btn_stand, "Stand", vm::stand)
                if (vm.canDouble) ImgButton(R.drawable.btn_double, "Double", vm::doubleDown)
                if (vm.canSplit) ImgButton(R.drawable.btn_split, "Split", vm::split)
            }
            BjPhase.RESULT -> {
                ImgButton(R.drawable.btn_rebet, "Rebet", { vm.nextHand(true) })
                ImgButton(R.drawable.btn_newbet, "New bet", { vm.nextHand(false) })
            }
            else -> {
                Spacer(Modifier.weight(1f))
                Text(
                    "DEALING",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.15.em,
                    color = P.OffWhite.copy(alpha = 0.8f),
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.ImgButton(res: Int, desc: String, onClick: () -> Unit) {
    Image(
        painter = painterResource(res),
        contentDescription = desc,
        modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(P.GoldTrim)
            .border(2.dp, P.Ink, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Text(
            text,
            color = P.Ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            letterSpacing = 0.06.em,
        )
    }
}

@Composable
private fun PayTableOverlay(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .padding(24.dp)
                .background(Color(0xF20E0A0B), RoundedCornerShape(14.dp))
                .border(1.5.dp, NeonMagenta.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            PayList(
                "BLAZING 7s · FREE",
                BlazeOrange,
                Blazing777Rules.BlazingWin.entries.map { it.label to it.payout },
            )
            Spacer(Modifier.height(16.dp))
            PayList(
                "TRILUX",
                NeonMagenta,
                Blazing777Rules.TriluxWin.entries.map { it.label to it.payout },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Both read your two cards plus the dealer's up card. " +
                    "Blazing 7s rides free and pays nothing yet.",
                fontSize = 10.sp,
                color = P.OffWhite.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun PayList(title: String, color: Color, rows: List<Pair<String, Int>>) {
    Text(
        title,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.12.em,
    )
    Spacer(Modifier.height(6.dp))
    rows.forEach { (label, pays) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(label, fontSize = 12.sp, color = P.OffWhite.copy(alpha = 0.85f))
            Spacer(Modifier.weight(1f))
            Text("$pays to 1", fontSize = 12.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}
