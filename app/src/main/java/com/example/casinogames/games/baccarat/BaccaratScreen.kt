package com.example.casinogames.games.baccarat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.casinogames.R
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.games.core.Card
import com.example.casinogames.ui.common.CasinoChip
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.EmptyCardSlot
import com.example.casinogames.ui.common.FreePlayBuyIn
import com.example.casinogames.ui.common.OutlinedText
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.PlayingCardView
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P

/*
 * The table is the baccarat.png sheet, 852x1846, worn full-bleed: the paint
 * carries the spots and the ambience, and the live layer carries everything
 * that moves — the money, the hands and their cards across the open top, tap
 * bands and chips on the painted spots, and the rack and buttons standing on
 * the painted floor at the bottom.
 */
private const val ART_W = 852f
private const val ART_H = 1846f

/** Places a box on the art's own pixels, stretched with the sheet. */
private fun Modifier.artBox(
    kx: Float, ky: Float,
    x0: Float, y0: Float, x1: Float, y1: Float,
): Modifier =
    this
        .offset(x = (x0 * kx).dp, y = (y0 * ky).dp)
        .size(width = ((x1 - x0) * kx).dp, height = ((y1 - y0) * ky).dp)

/** One painted bet spot: where it is, what shape its glow takes. */
private data class SpotDef(
    val type: BetType,
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val corner: Float,
    val round: Boolean = false,
)

private val SPOTS = listOf(
    SpotDef(BetType.PLAYER_PAIR, 75f, 645f, 311f, 775f, 14f),
    SpotDef(BetType.TIE, 311f, 645f, 537f, 775f, 14f),
    SpotDef(BetType.BANKER_PAIR, 537f, 645f, 775f, 775f, 14f),
    SpotDef(BetType.FORTUNE_7, 77f, 792f, 407f, 977f, 22f),
    SpotDef(BetType.GOLDEN_8, 443f, 792f, 774f, 977f, 22f),
    SpotDef(BetType.HEAVENLY_9, 77f, 1001f, 407f, 1193f, 22f),
    SpotDef(BetType.BLAZING_7S, 443f, 1001f, 774f, 1193f, 22f),
    SpotDef(BetType.COVER_ALL, 331f, 899f, 519f, 1087f, 0f, round = true),
    SpotDef(BetType.PLAYER, 150f, 1258f, 356f, 1466f, 0f, round = true),
    SpotDef(BetType.BANKER, 492f, 1270f, 698f, 1476f, 0f, round = true),
)

@Composable
fun BaccaratScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = onBack,
    vm: BaccaratViewModel = viewModel(),
) {
    LaunchedEffect(campaign) { vm.enterMode(campaign) }
    val hand = vm.hand
    val winningSpots =
        if (hand != null && vm.phase == Phase.RESULT) BaccaratEngine.winningSpots(hand)
        else emptySet()
    var roadOpen by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF040308))) {
        val kx = maxWidth.value / ART_W
        val ky = maxHeight.value / ART_H
        Image(
            painter = painterResource(R.drawable.baccarat),
            contentDescription = "Baccarat table",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        // The painted spots, live: a tap band, the chip and the glow each.
        SPOTS.forEach { def ->
            val amount = vm.bets[def.type] ?: 0
            val won = def.type in winningSpots
            val shape: Shape =
                if (def.round) CircleShape else RoundedCornerShape((def.corner * kx).dp)
            Box(
                Modifier
                    .artBox(kx, ky, def.x0, def.y0, def.x1, def.y1)
                    .clip(shape)
                    .background(
                        when {
                            won -> P.WinGlow.copy(alpha = 0.22f)
                            amount > 0 -> Color(0x21000000)
                            else -> Color.Transparent
                        }
                    )
                    .then(
                        when {
                            won -> Modifier.border(2.5.dp, P.WinGlow, shape)
                            amount > 0 -> Modifier.border(1.5.dp, Color(0x8CFFFFFF), shape)
                            else -> Modifier
                        }
                    )
                    .clickable(
                        enabled = vm.phase == Phase.BETTING,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { vm.addBet(def.type) },
                contentAlignment = Alignment.Center,
            ) {
                PlacedBetChip(amount, size = (150 * kx).dp)
            }
        }

        // The table talk, on a plate over the painted greeting.
        Box(
            Modifier
                .artBox(kx, ky, 190f, 583f, 662f, 643f)
                .clip(RoundedCornerShape((10 * kx).dp))
                .background(Color(0xFF060012)),
            contentAlignment = Alignment.Center,
        ) {
            val highlight = vm.phase == Phase.RESULT && vm.lastReturn > 0
            OutlinedText(
                vm.message + if (highlight) " · returned ${formatMoney(vm.lastReturn)}" else "",
                fontSize = (26 * kx).sp,
                color = if (highlight) P.WinGlow else P.OffWhite,
                outlineWidth = 1.dp,
                textAlign = TextAlign.Center,
            )
        }

        // The open top of the sheet: the live top bar, the names and the
        // cards — the part the paint leaves to the game.
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onBack, vm)
            Text(
                "PUNTO BANCO · 8 DECKS · ${vm.shoeCount} CARDS IN SHOE",
                fontSize = 10.sp,
                letterSpacing = 0.3.em,
                color = P.OffWhite.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
            HandsRow(vm)
        }

        // The painted floor at the bottom carries the working strip.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (vm.phase == Phase.RESULT) {
                WinningsRow(vm)
                Spacer(Modifier.height(6.dp))
            }
            if (vm.phase == Phase.BETTING) {
                ChipRack(vm)
                Spacer(Modifier.height(8.dp))
            }
            ActionButtons(vm)
        }

        RoadDrawer(
            vm,
            open = roadOpen,
            onToggle = { roadOpen = !roadOpen },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp)
                .zIndex(2f),
        )

        if (!vm.campaign && vm.bankroll < 25 && vm.totalStaked == 0 && vm.phase == Phase.BETTING) {
            FreePlayBuyIn(
                onDismiss = {},
                onConfirm = { FreePlay.buyIn = it; vm.buyBackIn() },
            )
        }
        if (vm.campaign && vm.phase == Phase.BETTING &&
            vm.bankroll < 25 && vm.totalStaked == 0
        ) {
            com.example.casinogames.ui.common.CampaignGameOver(onDone = onGameOverExit)
        }
        if (vm.campaign && vm.phase == Phase.BETTING &&
            vm.bankroll >= vm.goal && vm.totalStaked == 0
        ) {
            com.example.casinogames.ui.common.CampaignComplete(
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

/** Bead road that drops down from the top-right, with a pull tab. */
@Composable
private fun RoadDrawer(
    vm: BaccaratViewModel,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                Modifier
                    .padding(bottom = 6.dp)
                    .background(Color(0xF2240F10), RoundedCornerShape(14.dp))
                    .border(1.5.dp, P.GoldTrim, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BeadRoad(vm)
            }
        }
        val arrowRotation by animateFloatAsState(
            targetValue = if (open) 180f else 0f,
            animationSpec = tween(240),
            label = "roadArrow",
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE60E0A0B))
                .border(1.5.dp, P.GoldTrim, RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "▼",
                fontSize = 10.sp,
                color = P.GoldTrim,
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
            )
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, vm: BaccaratViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹ LOBBY",
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
            Modifier.padding(end = 58.dp),
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
                    formatMoney(vm.totalStaked.toDouble()),
                    fontSize = 14.sp, color = P.OffWhite, outlineWidth = 1.dp,
                )
            }
        }
    }
}

@Composable
private fun HandsRow(vm: BaccaratViewModel) {
    val hand = vm.hand
    Row(
        Modifier.fillMaxWidth().widthIn(max = 460.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        HandSide(
            name = "PLAYER",
            color = P.PlayerBlue,
            cards = hand?.player ?: emptyList(),
            dealt = vm.dealtPlayer,
            shown = vm.revealedPlayer,
            dimmed = vm.phase == Phase.RESULT && hand != null &&
                hand.outcome != Outcome.PLAYER && hand.outcome != Outcome.TIE,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(width = 3.dp, height = 36.dp)
                .background(P.OffWhite, RoundedCornerShape(2.dp))
        )
        HandSide(
            name = "BANKER",
            color = P.BankerRed,
            cards = hand?.banker ?: emptyList(),
            dealt = vm.dealtBanker,
            shown = vm.revealedBanker,
            dimmed = vm.phase == Phase.RESULT && hand != null &&
                hand.outcome != Outcome.BANKER && hand.outcome != Outcome.TIE,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HandSide(
    name: String,
    color: Color,
    cards: List<Card>,
    dealt: Int,
    shown: Int,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val total = BaccaratEngine.handTotal(cards.take(shown))
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.alpha(if (dimmed) 0.55f else 1f),
        ) {
            OutlinedText(name, fontSize = 21.sp, color = color, letterSpacing = 0.06.em)
            if (shown > 0) {
                Box(
                    Modifier
                        .background(color, RoundedCornerShape(999.dp))
                        .border(2.dp, P.Ink, RoundedCornerShape(999.dp))
                        .padding(horizontal = 9.dp, vertical = 1.dp)
                ) {
                    Text(
                        "$total",
                        color = P.OffWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
        Box(
            Modifier
                .padding(top = 6.dp, bottom = 12.dp)
                .fillMaxWidth()
                .height(3.dp)
                .background(P.OffWhite, RoundedCornerShape(2.dp))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (cards.isEmpty() || dealt == 0) {
                EmptyCardSlot()
            } else {
                cards.take(dealt).forEachIndexed { i, card ->
                    PlayingCardView(card, faceUp = i < shown)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WinningsRow(vm: BaccaratViewModel) {
    if (vm.phase != Phase.RESULT || vm.lastWinnings.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        Modifier.widthIn(max = 420.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        vm.lastWinnings.forEach { (type, profit) ->
            val won = profit > 0
            Row(
                Modifier
                    .background(Color(0xB30E0A0B), RoundedCornerShape(999.dp))
                    .border(
                        1.5.dp,
                        if (won) P.GoldTrim else P.OffWhite.copy(alpha = 0.35f),
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    type.displayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = P.OffWhite,
                )
                Text(
                    if (won) "+${formatMoney(profit)}" else "push",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (won) P.WinGlow else P.OffWhite.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun ChipRack(vm: BaccaratViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            )
        }
    }
}

@Composable
private fun ActionButtons(vm: BaccaratViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (vm.phase) {
            Phase.BETTING -> {
                PillButton("Undo", solid = false, onClick = vm::undoBet)
                PillButton("DEAL", solid = true, onClick = vm::deal)
                PillButton("Clear", solid = false, onClick = vm::clearBets)
            }
            Phase.RESULT -> {
                PillButton("New bets", solid = false, onClick = { vm.nextHand(false) })
                PillButton("REBET", solid = true, onClick = { vm.nextHand(true) })
            }
            Phase.DEALING -> {
                Text(
                    "DEALING",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.15.em,
                    color = P.OffWhite.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PillButton(text: String, solid: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (solid) P.GoldTrim else Color(0x8C0A0510))
            .border(
                if (solid) 2.dp else 1.5.dp,
                if (solid) P.Ink else P.OffWhite.copy(alpha = 0.45f),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp)
    ) {
        Text(
            text,
            color = if (solid) P.Ink else P.OffWhite,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            letterSpacing = 0.08.em,
        )
    }
}

@Composable
private fun BeadRoad(vm: BaccaratViewModel) {
    val counts = vm.handLog.groupingBy { it.outcome }.eachCount()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "BEAD ROAD · P ${counts[Outcome.PLAYER] ?: 0} · B ${counts[Outcome.BANKER] ?: 0} · T ${counts[Outcome.TIE] ?: 0}",
            fontSize = 10.sp,
            letterSpacing = 0.3.em,
            color = P.OffWhite.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            Modifier
                .widthIn(min = 220.dp)
                .background(Color(0x47000000), RoundedCornerShape(8.dp))
                .border(2.dp, P.OffWhite.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (vm.handLog.isEmpty()) {
                Text(
                    "Results appear here as you play",
                    fontSize = 11.sp,
                    color = P.OffWhite.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 40.dp),
                )
            } else {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .widthIn(max = 340.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    vm.handLog.chunked(6).forEach { column ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            column.forEach { record -> Bead(record.outcome) }
                            repeat(6 - column.size) { Spacer(Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
        if (vm.handLog.isNotEmpty()) {
            Text(
                "LAST ${minOf(10, vm.handLog.size)} HANDS",
                fontSize = 9.sp,
                letterSpacing = 0.25.em,
                color = P.OffWhite.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            Column(
                Modifier.width(190.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val recent = vm.handLog.takeLast(10).asReversed()
                recent.forEachIndexed { i, record ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "#${vm.handLog.size - i}",
                            fontSize = 10.sp,
                            color = P.OffWhite.copy(alpha = 0.5f),
                            modifier = Modifier.width(30.dp),
                        )
                        Bead(record.outcome)
                        Spacer(Modifier.weight(1f))
                        val net = record.net
                        Text(
                            when {
                                net > 0 -> "+${formatMoney(net)}"
                                net < 0 -> "−${formatMoney(-net)}"
                                else -> "even"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                net > 0 -> P.WinGlow
                                net < 0 -> Color(0xFFFF9C8A)
                                else -> P.OffWhite.copy(alpha = 0.6f)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Bead(outcome: Outcome) {
    val (color, letter) = when (outcome) {
        Outcome.PLAYER -> P.PlayerBlue to "P"
        Outcome.BANKER -> P.BankerRed to "B"
        Outcome.TIE -> P.TieGreen to "T"
    }
    Box(
        Modifier
            .size(16.dp)
            .background(color, CircleShape)
            .border(1.5.dp, P.Ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = P.OffWhite)
    }
}
