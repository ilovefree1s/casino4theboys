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
import androidx.compose.ui.graphics.Brush
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
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.EmptyCardSlot
import com.example.casinogames.ui.common.OutlinedText
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.PlayingCardView
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P

private val NeonPurple = Color(0xFF8B30D9)
private val NeonBlue = Color(0xFF2E7BFF)
private val NeonMagenta = Color(0xFFFF3FD8)
private val HotPink = Color(0xFFFF1493)
private val TableBlack = Color(0xFF040308)

@Composable
fun FreeBetScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = onBack,
    vm: FreeBetViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    var showPayTable by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(TableBlack)
    ) {
        Image(
            painter = painterResource(R.drawable.background),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.45f),
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
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x26FFFFFF))
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (vm.campaign) "CAMPAIGN · GOAL \$${formatMoney(vm.goal)}"
                else "FREE BET BLACKJACK · 8 DECKS · ${vm.shoeCount} CARDS IN SHOE",
                fontSize = 10.sp,
                letterSpacing = 0.24.em,
                color = if (vm.campaign) Color(0xCCFFD24D) else Color(0x8CFFFFFF),
            )
            Spacer(Modifier.weight(1f))
            DealerArea(vm)
            Spacer(Modifier.height(8.dp))
            MessageLine(vm)
            ResultPills(vm)
            Spacer(Modifier.weight(1f))
            PlayerArea(vm)
            Spacer(Modifier.weight(1f))
            if (vm.phase == BjPhase.BETTING) {
                BetSpots(vm, onShowPayTable = { showPayTable = true })
                Spacer(Modifier.height(12.dp))
                ChipRack(vm)
                Spacer(Modifier.height(10.dp))
            } else if (vm.playerHands.size < 3) {
                // Spots stay on the felt unless splits need the room.
                BetSpots(vm, onShowPayTable = { showPayTable = true })
                Spacer(Modifier.height(10.dp))
            } else {
                PotStatusRow(vm)
            }
            ActionButtons(vm)
        }
        if (showPayTable) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000))
                    .clickable { showPayTable = false },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.fb_paytable),
                    contentDescription = "4 The Boys pay table",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(34.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, NeonMagenta.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
        if (vm.campaign && vm.phase == BjPhase.BETTING &&
            vm.bankroll < 25 && vm.bet == 0 && vm.potBet == 0
        ) {
            CampaignGameOver(onStartOver = {
                vm.buyBackIn()
                onGameOverExit()
            })
        }
        if (vm.campaign && vm.phase == BjPhase.BETTING &&
            vm.bankroll >= vm.goal && vm.bet == 0 && vm.potBet == 0
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
private fun TopBar(onBack: () -> Unit, vm: FreeBetViewModel) {
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
private fun DealerArea(vm: FreeBetViewModel) {
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
                    color = if (vm.holeRevealed && BlackjackCore.total(vm.dealerCards) > 21 &&
                        !FreeBetRules.dealerPushes(vm.dealerCards)
                    ) Color(0xFF8E2B1E) else NeonPurple,
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
private fun MessageLine(vm: FreeBetViewModel) {
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ResultPills(vm: FreeBetViewModel) {
    if (vm.phase != BjPhase.RESULT || vm.results.isEmpty()) return
    // Per-hand results render over each hand's cards; only extras (4 The Boys) show here.
    val extras = vm.results.drop(vm.playerHands.size)
    if (extras.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp).widthIn(max = 400.dp),
    ) {
        extras.forEach { r -> ResultPill(r) }
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
        Text(
            r.label,
            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = P.OffWhite,
        )
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
private fun PlayerArea(vm: FreeBetViewModel) {
    if (vm.playerHands.isEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            YourHandHeader(badge = null, badgeColor = NeonBlue, coins = 0)
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
                rowHands.forEach { (i, hand) ->
                    PlayerHandColumn(vm, i, hand)
                }
            }
        }
    }
}

@Composable
private fun YourHandHeader(badge: String?, badgeColor: Color, coins: Int) {
    Box(contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.fb_header_yourhand),
            contentDescription = "Your hand",
            modifier = Modifier.width(300.dp),
            contentScale = ContentScale.FillWidth,
        )
        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (badge != null) TotalBadge(badge, badgeColor)
            repeat(coins) { FreeBetPill() }
        }
    }
}

/** The gold logo marks a hand that took a free split or a free double. */
@Composable
private fun FreeBetPill() {
    Image(
        painter = painterResource(R.drawable.fourtheboys_gold),
        contentDescription = "Free bet",
        modifier = Modifier.size(30.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun PlayerHandColumn(vm: FreeBetViewModel, i: Int, hand: BjHand) {
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
        val coins = (if (hand.isFree) 1 else 0) + (if (hand.freeDoubled) 1 else 0)
        if (single) {
            YourHandHeader(
                badge = badge,
                badgeColor = badgeColor,
                coins = coins,
            )
        } else {
            PlayerHeader(
                active = active,
                label = "HAND ${i + 1}",
                badge = badge,
                badgeColor = badgeColor,
                coins = coins,
            )
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
private fun PlayerHeader(
    active: Boolean,
    label: String,
    badge: String?,
    coins: Int = 0,
    badgeColor: Color = NeonBlue,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (active) {
            Text("▶", fontSize = 11.sp, color = P.WinGlow)
        }
        OutlinedText(
            label,
            fontSize = 15.sp,
            color = NeonBlue,
            letterSpacing = 0.08.em,
            outlineWidth = 1.5.dp,
        )
        if (badge != null) TotalBadge(badge, badgeColor)
        repeat(coins) { FreeBetPill() }
    }
}

@Composable
private fun BetSpots(vm: FreeBetViewModel, onShowPayTable: () -> Unit) {
    val inPlay = vm.phase != BjPhase.BETTING
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.fb_paytable),
            contentDescription = "4 The Boys pay table",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onShowPayTable),
            contentScale = ContentScale.FillWidth,
        )
        Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.fb_spot_bet),
                contentDescription = "Bet spot",
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .clickable(enabled = vm.phase == BjPhase.BETTING) { vm.addChip() },
                contentScale = ContentScale.Fit,
            )
            PlacedBetChip(vm.bet)
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-48).dp, y = (-26).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.fourtheboys_spot),
                    contentDescription = "4 The Boys side bet",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable(enabled = vm.phase == BjPhase.BETTING) { vm.addPotChip() },
                    contentScale = ContentScale.Fit,
                )
                PlacedBetChip(if (vm.phase == BjPhase.BETTING) vm.potBet else vm.potStake)
            }
            if (inPlay && vm.freeCoins > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "COINS × ${vm.freeCoins}",
                    fontSize = 9.sp, fontWeight = FontWeight.Black,
                    color = P.Ink, letterSpacing = 0.05.em,
                    modifier = Modifier
                        .background(P.GoldTrim, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun PotStatusRow(vm: FreeBetViewModel) {
    if (vm.potStake <= 0 && vm.freeCoins <= 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.fourtheboys_spot),
            contentDescription = "4 The Boys",
            modifier = Modifier.size(34.dp),
            contentScale = ContentScale.Fit,
        )
        if (vm.potStake > 0) PlacedBetChip(vm.potStake, size = 36.dp)
        if (vm.freeCoins > 0) {
            Text(
                "COINS × ${vm.freeCoins}",
                fontSize = 9.sp, fontWeight = FontWeight.Black,
                color = P.Ink, letterSpacing = 0.05.em,
                modifier = Modifier
                    .background(P.GoldTrim, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ChipRack(vm: FreeBetViewModel) {
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
private fun ActionButtons(vm: FreeBetViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(50.dp),
    ) {
        when (vm.phase) {
            BjPhase.BETTING -> {
                ImgButton(R.drawable.btn_undo, "Undo", vm::undoChip)
                ImgButton(R.drawable.btn_deal, "Deal", vm::deal)
                ImgButton(R.drawable.btn_clear, "Clear", vm::clearBet)
                if (!vm.campaign && vm.bankroll < 25 && vm.bet == 0) {
                    PillButton("Buy back in", onClick = vm::buyBackIn)
                }
            }
            BjPhase.PLAYER_TURN -> {
                if (vm.canHit) ImgButton(R.drawable.btn_hit, "Hit", vm::hit)
                ImgButton(R.drawable.btn_stand, "Stand", vm::stand)
                if (vm.canDouble) {
                    ImgButton(
                        if (vm.doubleIsFree) R.drawable.badge_free_double else R.drawable.btn_double,
                        "Double",
                        vm::doubleDown,
                    )
                }
                if (vm.canSplit) {
                    ImgButton(
                        if (vm.splitIsFree) R.drawable.badge_free_split else R.drawable.btn_split,
                        "Split",
                        vm::split,
                    )
                }
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
private fun PillButton(
    text: String,
    onClick: () -> Unit,
) {
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
