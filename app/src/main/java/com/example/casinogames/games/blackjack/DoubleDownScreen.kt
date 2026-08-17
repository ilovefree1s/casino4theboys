package com.example.casinogames.games.blackjack

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.casinogames.games.core.Card
import com.example.casinogames.ui.common.CampaignComplete
import com.example.casinogames.ui.common.CampaignGameOver
import com.example.casinogames.ui.common.CardHeight
import com.example.casinogames.ui.common.CardWidth
import com.example.casinogames.ui.common.CasinoChip
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
/** The one warm thing on this felt, borrowed from the house coin. */
private val Gold = Color(0xFFE8C169)
/** The felt is black and white; the verdict is the one thing that is not. */
private val WinGreen = Color(0xFF57E06A)
private val LoseRed = Color(0xFFFF4D4D)

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
    var showOdds by remember { mutableStateOf(false) }
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
            Table(vm, Modifier.weight(1f))
            BetRow(vm, onShowOdds = { showOdds = true })
            if (vm.phase == BjPhase.BETTING) {
                Spacer(Modifier.height(12.dp))
                ChipRack(vm)
            }
            Spacer(Modifier.height(10.dp))
            ActionButtons(vm)
        }
        if (showOdds) OddsOverlay { showOdds = false }
        if (vm.campaign && vm.phase == BjPhase.BETTING && vm.bankroll < 25 && vm.nothingAtStake) {
            CampaignGameOver(onStartOver = {
                vm.buyBackIn()
                onGameOverExit()
            })
        }
        if (vm.campaign && vm.phase == BjPhase.BETTING && vm.bankroll >= vm.goal && vm.nothingAtStake) {
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

/**
 * The table is one piece of art — titles, rules and the two card slots — and
 * everything live is positioned on top of it by where those marks fall in the
 * artwork (1024 x 1536).
 */
private const val ArtWidth = 1024f
private const val ArtHeight = 1536f
private const val DealerSlotCentre = 471f
private const val MessageCentre = 709f
private const val PlayerSlotCentre = 1296f
/** The card slots the artwork was drawn around; cards are cut to fit them. */
private const val SlotWidth = 223f
private const val SlotHeight = 332f
/** Dealt a little under the drawn slot, so a long hand has room to spread. */
private const val SlotScale = 0.85f
/** How many cards the felt lays out side by side before the hand starts to fan. */
private const val LaidOutCards = 5
/** Just under the player's cards, where the hand's verdict is laid. */
private const val PlayerCardsUnder = PlayerSlotCentre + SlotHeight * SlotScale / 2f + 30f
/** The totals ride on centre, in the gap the art leaves above each hand. */
private const val DealerTotalMark = DealerSlotCentre - SlotHeight * SlotScale / 2f - 26f
private const val PlayerTotalMark = PlayerSlotCentre - SlotHeight * SlotScale / 2f - 26f

@Composable
private fun Table(vm: DoubleDownViewModel, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Fit the layout whole, whichever edge runs out first.
        val artHeight = minOf(maxHeight, maxWidth * (ArtHeight / ArtWidth))
        val artWidth = artHeight * (ArtWidth / ArtHeight)
        Box(
            Modifier
                .align(Alignment.Center)
                .width(artWidth)
                .height(artHeight)
        ) {
            Image(
                painter = painterResource(R.drawable.doubledownmadnesslayout),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            // Cards are drawn to the size of the slot the art was built around.
            val slotHeight = artHeight * (SlotHeight / ArtHeight) * SlotScale
            val slotWidth = artWidth * (SlotWidth / ArtWidth) * SlotScale
            val cardScale = slotHeight / CardHeight

            AtMark(artHeight, DealerSlotCentre, slotHeight) {
                CardSlot(
                    cards = vm.dealerCards,
                    faceUp = { i -> i == 0 || vm.holeRevealed },
                    slotWidth = slotWidth,
                    slotHeight = slotHeight,
                    scale = cardScale,
                    available = artWidth,
                )
            }
            AtMark(artHeight, MessageCentre, 44.dp) { MessageLine(vm) }
            AtMark(artHeight, PlayerSlotCentre, slotHeight) {
                CardSlot(
                    cards = vm.hand?.cards ?: emptyList(),
                    faceUp = { true },
                    slotWidth = slotWidth,
                    slotHeight = slotHeight,
                    scale = cardScale,
                    available = artWidth,
                )
            }
            AtMark(artHeight, DealerTotalMark, 24.dp) { DealerBadge(vm) }
            AtMark(artHeight, PlayerTotalMark, 24.dp) { PlayerBadges(vm) }
            if (vm.phase == BjPhase.RESULT) {
                // Laid under the hand it is reporting, clear of the cards.
                AtMark(artHeight, PlayerCardsUnder, 30.dp) {
                    vm.handResult?.let { r -> ResultPill(r) }
                }
            }
        }
    }
}

/**
 * The dashed landing box, drawn only while a side is short of its second card:
 * one card fills it exactly, and once the hand spreads there is nothing left
 * for the box to mark.
 */
@Composable
private fun CardSlot(
    cards: List<Card>,
    faceUp: (Int) -> Boolean,
    slotWidth: Dp,
    slotHeight: Dp,
    scale: Float,
    available: Dp,
) {
    Box(contentAlignment = Alignment.Center) {
        if (cards.size < 2) {
            Box(
                Modifier
                    .size(slotWidth, slotHeight)
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xE6FFFFFF),
                            style = Stroke(
                                width = 2.dp.toPx() * scale,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(9.dp.toPx() * scale, 6.dp.toPx() * scale)
                                ),
                            ),
                            cornerRadius = CornerRadius(4.dp.toPx() * scale),
                        )
                    }
            )
        }
        val gap = 6.dp * scale
        if (cards.size <= LaidOutCards) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                cards.forEachIndexed { i, card ->
                    PlayingCardView(card, faceUp = faceUp(i), scale = scale)
                }
            }
        } else {
            // Five is as wide as the felt goes, so beyond that the hand fans:
            // the leftmost card is the bottom of the pile and each new one
            // overlaps it to the right, however long the run gets.
            val cardWidth = CardWidth * scale
            val step = minOf(
                cardWidth + gap,
                (available - cardWidth) / (cards.size - 1).toFloat(),
            )
            Box {
                cards.forEachIndexed { i, card ->
                    Box(Modifier.offset(x = step * i.toFloat() - step * (cards.size - 1) / 2f)) {
                        PlayingCardView(card, faceUp = faceUp(i), scale = scale)
                    }
                }
            }
        }
    }
}

/** Centres content on a mark in the artwork, measured in the art's own pixels. */
@Composable
private fun BoxScope.AtMark(
    artHeight: Dp,
    centre: Float,
    height: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = artHeight * (centre / ArtHeight) - height / 2)
            .height(height),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun DealerBadge(vm: DoubleDownViewModel, modifier: Modifier = Modifier) {
    val visible = if (vm.holeRevealed) vm.dealerCards else vm.dealerCards.take(1)
    if (visible.isEmpty()) return
    val full = BlackjackCore.total(vm.dealerCards)
    val busted = vm.holeRevealed && full > 21 && full != 22
    Box(modifier) {
        TotalBadge(
            text = if (vm.holeRevealed && BlackjackCore.isBlackjack(vm.dealerCards)) "BJ"
            else "${BlackjackCore.total(visible)}",
            color = if (busted) Ash else Chalk,
        )
    }
}

@Composable
private fun PlayerBadges(vm: DoubleDownViewModel, modifier: Modifier = Modifier) {
    val cards = vm.hand?.cards ?: return
    if (cards.isEmpty()) return
    val units = vm.hand?.let { if (it.betUnit > 0) it.stake / it.betUnit else 1 } ?: 1
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        TotalBadge(
            text = if (BlackjackCore.isBlackjack(cards)) "BJ" else "${BlackjackCore.total(cards)}",
            color = if (BlackjackCore.isBust(cards)) Ash else Chalk,
        )
        if (units > 1) {
            Text(
                "${units}×",
                color = TableBlack, fontSize = 11.sp, fontWeight = FontWeight.Black,
                modifier = Modifier
                    .background(Silver, RoundedCornerShape(999.dp))
                    .border(2.dp, P.Ink, RoundedCornerShape(999.dp))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
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
    val settled = vm.phase == BjPhase.RESULT
    val verdict = if (settled) vm.verdict else 0
    val text = if (vm.phase == BjPhase.BETTING) "⚡  ${vm.message}  ⚡" else vm.message
    Text(
        text,
        fontSize = if (verdict < 0) 18.sp else 15.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        color = when {
            verdict > 0 -> WinGreen
            verdict < 0 -> LoseRed
            else -> P.OffWhite.copy(alpha = 0.92f)
        },
        textAlign = TextAlign.Center,
    )
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
        // Everything inside the pill reads white; only its ring marks a win.
        Text(r.label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Chalk)
        Text(
            when {
                r.net > 0 -> "+${formatMoney(r.net)}"
                r.net < 0 -> "−${formatMoney(-r.net)}"
                else -> "push"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = Chalk,
        )
    }
}

/**
 * The bet spot keeps the artwork's centre line; the odds placard sits to its
 * left and the house's side of the bargain to its right — the way TriLux
 * flanks the bet on the Blazing felt.
 */
@Composable
private fun BetRow(vm: DoubleDownViewModel, onShowOdds: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Take every dp between the felt's edge and the bet spot: the art is
        // far larger than it is ever drawn, so the gap is the only limit.
        val placard = minOf(
            (maxWidth - BetSpotSize) / 2 - OddsPlacardGap,
            OddsPlacardMax,
        ) * 0.90f
        Image(
            painter = painterResource(R.drawable.dd_push22_odds),
            contentDescription = "Push 22 odds",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(placard)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onShowOdds),
            contentScale = ContentScale.FillWidth,
        )
        BetSpot(vm)
        val betting = vm.phase == BjPhase.BETTING
        Column(
            Modifier.align(Alignment.CenterEnd).width(118.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The side bet's verdict sits on the coin it was staked against.
            if (vm.phase == BjPhase.RESULT) {
                vm.push22Result?.let { r ->
                    ResultPill(r)
                    Spacer(Modifier.height(4.dp))
                }
            }
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(63.dp)
                        .border(2.dp, Gold.copy(alpha = 0.9f), CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .clickable(enabled = betting) { vm.addPush22Chip() },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.fourtheboys_gold),
                        contentDescription = "Push 22 side bet",
                        // Clipped round so the art's square backing never shows.
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
                PlacedBetChip(if (betting) vm.push22Bet else vm.push22Stake, size = 38.dp)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "PUSH 22",
                color = Gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.14.em,
            )
        }
    }
}

private val BetSpotSize = 88.dp
/** Breathing room between the odds and the bet spot's ring. */
private val OddsPlacardGap = 8.dp
/** A ceiling so the placard does not sprawl on a tablet. */
private val OddsPlacardMax = 190.dp

/** Tap the placard and the odds fill the screen, since they are set small. */
@Composable
private fun OddsOverlay(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.dd_push22_odds),
            contentDescription = "Push 22 odds",
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp),
            contentScale = ContentScale.FillWidth,
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
                .size(BetSpotSize)
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
                if (!vm.campaign && vm.bankroll < 25 && vm.nothingAtStake) {
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
