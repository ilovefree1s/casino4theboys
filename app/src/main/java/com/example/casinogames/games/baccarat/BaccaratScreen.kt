package com.example.casinogames.games.baccarat

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.casinogames.R
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.ui.common.CasinoChip
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.FreePlayBuyIn
import com.example.casinogames.ui.common.OutlinedText
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.PlayingCardView
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P

/*
 * The table is one piece of art — baccarattable.png, 941x1672 — and every
 * live thing sits on coordinates measured off it: tap bands over the painted
 * spots, chips on their centres, dark plates over the painted example money
 * so the real figures can stand where the paint put them. The rack and the
 * buttons live on the black below the sheet.
 */
private const val ART_W = 941f
private const val ART_H = 1672f

/** Places a box on the art's own pixels. */
private fun Modifier.artBox(k: Float, x0: Float, y0: Float, x1: Float, y1: Float): Modifier =
    this
        .offset(x = (x0 * k).dp, y = (y0 * k).dp)
        .size(width = ((x1 - x0) * k).dp, height = ((y1 - y0) * k).dp)

/** One painted bet spot: where it is, what shape its win glow takes. */
private data class SpotDef(
    val type: BetType,
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val corner: Float,
    val round: Boolean = false,
)

private val SPOTS = listOf(
    SpotDef(BetType.PLAYER_PAIR, 63f, 497f, 330f, 657f, 14f),
    SpotDef(BetType.TIE, 337f, 497f, 603f, 657f, 14f),
    SpotDef(BetType.BANKER_PAIR, 610f, 497f, 898f, 657f, 14f),
    SpotDef(BetType.FORTUNE_7, 55f, 680f, 455f, 905f, 26f),
    SpotDef(BetType.GOLDEN_8, 488f, 680f, 898f, 905f, 26f),
    SpotDef(BetType.HEAVENLY_9, 55f, 928f, 455f, 1150f, 26f),
    SpotDef(BetType.BLAZING_7S, 488f, 928f, 898f, 1150f, 26f),
    SpotDef(BetType.COVER_ALL, 365f, 810f, 575f, 1020f, 0f, round = true),
    SpotDef(BetType.PLAYER, 150f, 1227f, 386f, 1463f, 0f, round = true),
    SpotDef(BetType.BANKER, 557f, 1227f, 793f, 1463f, 0f, round = true),
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

    // A soft violet floor-glow rises from the bottom edge, so the working
    // strip below the sheet reads as the same room, not dead black.
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Color(0xFF040308))
                val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height)
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(Color(0x4D5A1E8C), Color(0x00000000)),
                        center = c,
                        radius = size.width * 0.95f,
                    ),
                    radius = size.width * 0.95f,
                    center = c,
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                // Fit the sheet whole; the black it leaves below is working
                // room for the rack and the buttons.
                val artW = minOf(maxWidth, maxHeight * (ART_W / ART_H))
                val artH = artW * (ART_H / ART_W)
                val k = artW.value / ART_W
                Box(Modifier.width(artW).height(artH)) {
                    Image(
                        painter = painterResource(R.drawable.baccarattable),
                        contentDescription = "Baccarat table",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                    ArtLayer(vm, winningSpots, k, onBack)
                    // The painted gold tab in the corner pulls the road down.
                    Box(
                        Modifier
                            .artBox(k, 788f, 6f, 912f, 66f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { roadOpen = !roadOpen }
                    )
                    Column(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-14 * k).dp, y = (72 * k).dp)
                            .zIndex(3f)
                    ) {
                        AnimatedVisibility(
                            visible = roadOpen,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(
                                Modifier
                                    .background(Color(0xF2240F10), RoundedCornerShape(14.dp))
                                    .border(1.5.dp, P.GoldTrim, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                BeadRoad(vm)
                            }
                        }
                    }
                }
            }
            // The strip below the sheet: results, the rack and the buttons.
            if (vm.phase == Phase.RESULT) WinningsRow(vm)
            Spacer(Modifier.height(4.dp))
            if (vm.phase == Phase.BETTING) ChipRack(vm)
            Spacer(Modifier.height(6.dp))
            ActionButtons(vm)
            Spacer(Modifier.height(8.dp))
        }
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

/** Everything live on the sheet, on the art's own coordinates. */
@Composable
private fun ArtLayer(
    vm: BaccaratViewModel,
    winningSpots: Set<BetType>,
    k: Float,
    onBack: () -> Unit,
) {
    val hand = vm.hand

    // The painted < LOBBY is the way out.
    Box(
        Modifier
            .artBox(k, 40f, 78f, 220f, 138f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onBack,
            )
    )

    // Dark plates over the painted example money; the real figures on top.
    MoneyPlate(k, 480f, 82f, 636f, 128f) {
        OutlinedText(
            formatMoney(vm.bankroll),
            fontSize = (34 * k).sp, color = P.WinGlow, outlineWidth = 1.dp,
        )
    }
    MoneyPlate(k, 718f, 82f, 905f, 128f) {
        OutlinedText(
            formatMoney(vm.totalStaked.toDouble()),
            fontSize = (34 * k).sp, color = P.OffWhite, outlineWidth = 1.dp,
        )
    }
    Box(
        Modifier
            .artBox(k, 100f, 140f, 841f, 182f)
            .background(Color(0xFF040308)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "PUNTO BANCO · 8 DECKS · ${vm.shoeCount} CARDS IN SHOE",
            fontSize = (23 * k).sp,
            letterSpacing = 0.28.em,
            color = P.OffWhite.copy(alpha = 0.55f),
            maxLines = 1,
        )
    }

    // Hand totals, pinned beside the painted names once cards show.
    if (vm.revealedPlayer > 0 && hand != null) {
        TotalBadge(
            BaccaratEngine.handTotal(hand.player.take(vm.revealedPlayer)),
            P.PlayerBlue, Modifier.artBox(k, 380f, 202f, 458f, 246f),
        )
    }
    if (vm.revealedBanker > 0 && hand != null) {
        TotalBadge(
            BaccaratEngine.handTotal(hand.banker.take(vm.revealedBanker)),
            P.BankerRed, Modifier.artBox(k, 800f, 202f, 878f, 246f),
        )
    }

    // The cards, over the painted slots. Nothing dealt leaves the painted
    // dashed slot to say so itself.
    CardRow(
        cards = hand?.player ?: emptyList(),
        dealt = vm.dealtPlayer, shown = vm.revealedPlayer, k = k,
        modifier = Modifier.artBox(k, 74f, 275f, 454f, 448f),
    )
    CardRow(
        cards = hand?.banker ?: emptyList(),
        dealt = vm.dealtBanker, shown = vm.revealedBanker, k = k,
        modifier = Modifier.artBox(k, 487f, 275f, 867f, 448f),
    )

    // The table talk, on a plate over the painted greeting.
    Box(
        Modifier
            .artBox(k, 205f, 436f, 737f, 496f)
            .clip(RoundedCornerShape((10 * k).dp))
            .background(Color(0xFF060012)),
        contentAlignment = Alignment.Center,
    ) {
        val highlight = vm.phase == Phase.RESULT && vm.lastReturn > 0
        OutlinedText(
            vm.message + if (highlight) " · returned ${formatMoney(vm.lastReturn)}" else "",
            fontSize = (30 * k).sp,
            color = if (highlight) P.WinGlow else P.OffWhite,
            outlineWidth = 1.dp,
            textAlign = TextAlign.Center,
        )
    }

    // The painted spots, live: a tap band, the chip and the win glow each.
    SPOTS.forEach { def ->
        val amount = vm.bets[def.type] ?: 0
        val won = def.type in winningSpots
        val shape: Shape =
            if (def.round) CircleShape else RoundedCornerShape((def.corner * k).dp)
        Box(
            Modifier
                .artBox(k, def.x0, def.y0, def.x1, def.y1)
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
            PlacedBetChip(amount, size = (110 * k).dp)
        }
    }
}

@Composable
private fun MoneyPlate(
    k: Float,
    x0: Float, y0: Float, x1: Float, y1: Float,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .artBox(k, x0, y0, x1, y1)
            .background(Color(0xFF040308)),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun TotalBadge(total: Int, color: Color, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
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
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            )
        }
    }
}

@Composable
private fun CardRow(
    cards: List<com.example.casinogames.games.core.Card>,
    dealt: Int,
    shown: Int,
    k: Float,
    modifier: Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (cards.isNotEmpty() && dealt > 0) {
            // The painted slot is 117 art px wide; the card view scales to it.
            val scale = (117f * k) / 52f
            Row(horizontalArrangement = Arrangement.spacedBy((6 * k).dp)) {
                cards.take(dealt).forEachIndexed { i, card ->
                    PlayingCardView(card, faceUp = i < shown, scale = scale)
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
        Modifier.widthIn(max = 420.dp).padding(top = 2.dp),
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    color = P.OffWhite,
                )
                Text(
                    if (won) "+${formatMoney(profit)}" else "push",
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
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
            .background(if (solid) P.GoldTrim else Color.Transparent)
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
            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
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
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
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
        Text(letter, fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = P.OffWhite)
    }
}
