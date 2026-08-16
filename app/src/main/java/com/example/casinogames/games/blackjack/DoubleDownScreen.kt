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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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

private val Chalk = Color(0xFFF2F2F2)
private val Silver = Color(0xFFB0B0B0)
private val Ash = Color(0xFF5A5A5A)
private val TableBlack = Color(0xFF050505)

/**
 * The house art is neon purple and blue; this table wears the same layout in
 * black and white, so every piece of it is drawn through a drained palette.
 */
private val Monochrome = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun DoubleDownScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = onBack,
    vm: DoubleDownViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    Box(
        Modifier
            .fillMaxSize()
            .background(TableBlack)
    ) {
        Image(
            painter = painterResource(R.drawable.dd_background),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f),
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
                else "DOUBLE DOWN MADNESS · 8 DECKS · ${vm.shoeCount} CARDS IN SHOE",
                fontSize = 10.sp,
                letterSpacing = 0.24.em,
                color = if (vm.campaign) Color(0xCCFFD24D) else Color(0x8CFFFFFF),
            )
            Spacer(Modifier.weight(1f))
            DealerArea(vm)
            Spacer(Modifier.height(8.dp))
            MessageLine(vm)
            Spacer(Modifier.weight(1f))
            PlayerArea(vm)
            Spacer(Modifier.weight(1f))
            BetSpot(vm)
            if (vm.phase == BjPhase.BETTING) {
                Spacer(Modifier.height(12.dp))
                ChipRack(vm)
            }
            Spacer(Modifier.height(10.dp))
            ActionButtons(vm)
        }
        if (vm.campaign && vm.phase == BjPhase.BETTING && vm.bankroll < 25 && vm.bet == 0) {
            CampaignGameOver(onStartOver = {
                vm.buyBackIn()
                onGameOverExit()
            })
        }
        if (vm.campaign && vm.phase == BjPhase.BETTING && vm.bankroll >= vm.goal && vm.bet == 0) {
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
private fun TopBar(onBack: () -> Unit, vm: DoubleDownViewModel) {
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
                    fontSize = 14.sp, color = Chalk, outlineWidth = 1.dp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "STAKED ",
                    fontSize = 9.sp, letterSpacing = 0.12.em,
                    color = P.OffWhite.copy(alpha = 0.6f),
                )
                OutlinedText(
                    formatMoney(vm.totalStaked.toDouble()),
                    fontSize = 14.sp, color = Silver, outlineWidth = 1.dp,
                )
            }
        }
    }
}

@Composable
private fun DealerArea(vm: DoubleDownViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.fb_header_dealer),
                contentDescription = "Dealer",
                modifier = Modifier.width(300.dp),
                contentScale = ContentScale.FillWidth,
                colorFilter = Monochrome,
            )
            val visible = if (vm.holeRevealed) vm.dealerCards else vm.dealerCards.take(1)
            if (visible.isNotEmpty()) {
                val t = BlackjackCore.total(visible)
                val busted = vm.holeRevealed && BlackjackCore.total(vm.dealerCards) > 21 &&
                    BlackjackCore.total(vm.dealerCards) != 22
                TotalBadge(
                    text = if (vm.holeRevealed && BlackjackCore.isBlackjack(vm.dealerCards)) "BJ"
                    else "$t",
                    color = if (busted) Ash else Chalk,
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
        Text(text, color = TableBlack, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MessageLine(vm: DoubleDownViewModel) {
    val net = vm.results.sumOf { it.net }
    val settled = vm.phase == BjPhase.RESULT
    val text = if (vm.phase == BjPhase.BETTING) "⚡  ${vm.message}  ⚡" else vm.message
    Text(
        text,
        fontSize = if (settled && net < 0) 18.sp else 15.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        color = when {
            settled && net > 0 -> Chalk
            settled && net < 0 -> Ash
            else -> P.OffWhite.copy(alpha = 0.92f)
        },
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PlayerArea(vm: DoubleDownViewModel) {
    val hand = vm.hand
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val cards = hand?.cards ?: emptyList()
        val badge = when {
            cards.isEmpty() -> null
            BlackjackCore.isBlackjack(cards) -> "BJ"
            else -> "${BlackjackCore.total(cards)}"
        }
        YourHandHeader(
            badge = badge,
            badgeColor = if (BlackjackCore.isBust(cards)) Ash else Chalk,
            doubled = hand?.doubled == true,
        )
        Spacer(Modifier.height(8.dp))
        Box(contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (cards.isEmpty()) {
                    EmptyCardSlot()
                } else {
                    cards.forEach { card -> PlayingCardView(card, faceUp = true) }
                }
            }
            if (vm.phase == BjPhase.RESULT) {
                vm.results.firstOrNull()?.let { r -> ResultPill(r) }
            }
        }
    }
}

@Composable
private fun YourHandHeader(badge: String?, badgeColor: Color, doubled: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.fb_header_yourhand),
            contentDescription = "Your hand",
            modifier = Modifier.width(300.dp),
            contentScale = ContentScale.FillWidth,
            colorFilter = Monochrome,
        )
        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (badge != null) TotalBadge(badge, badgeColor)
            if (doubled) {
                Text(
                    "2×",
                    color = TableBlack, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .background(Silver, RoundedCornerShape(999.dp))
                        .border(2.dp, P.Ink, RoundedCornerShape(999.dp))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultPill(r: BjResult) {
    val won = r.net > 0
    Row(
        Modifier
            .background(Color(0xE6070707), RoundedCornerShape(999.dp))
            .border(
                1.5.dp,
                if (won) Chalk else P.OffWhite.copy(alpha = 0.35f),
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
                r.net > 0 -> Chalk
                r.net < 0 -> Ash
                else -> P.OffWhite.copy(alpha = 0.7f)
            },
        )
    }
}

@Composable
private fun BetSpot(vm: DoubleDownViewModel) {
    Box(contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.fb_spot_bet),
            contentDescription = "Bet spot",
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .clickable(enabled = vm.phase == BjPhase.BETTING) { vm.addChip() },
            contentScale = ContentScale.Fit,
            colorFilter = Monochrome,
        )
        PlacedBetChip(if (vm.phase == BjPhase.BETTING) vm.bet else vm.hand?.stake ?: 0)
    }
}

@Composable
private fun ChipRack(vm: DoubleDownViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chipsFor(vm.bankroll).forEach { chip ->
            CasinoChip(
                imageRes = chip.imageRes,
                contentDescription = "${chip.value} chip",
                selected = vm.selectedChip == chip.value,
                onClick = { vm.selectedChip = chip.value },
                selectedColor = Chalk,
            )
        }
    }
}

@Composable
private fun ActionButtons(vm: DoubleDownViewModel) {
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
                if (vm.canStand) ImgButton(R.drawable.btn_stand, "Stand", vm::stand)
                if (vm.canDouble) ImgButton(R.drawable.btn_double, "Double", vm::doubleDown)
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
        colorFilter = Monochrome,
    )
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Chalk)
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
