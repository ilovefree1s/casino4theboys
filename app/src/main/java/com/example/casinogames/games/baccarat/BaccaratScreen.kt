package com.example.casinogames.games.baccarat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.casinogames.games.core.Card
import com.example.casinogames.ui.common.CasinoChip
import com.example.casinogames.ui.common.chipsFor
import com.example.casinogames.ui.common.EmptyCardSlot
import com.example.casinogames.ui.common.OutlinedText
import com.example.casinogames.ui.common.PlacedBetChip
import com.example.casinogames.ui.common.PlayingCardView
import com.example.casinogames.ui.common.formatMoney
import com.example.casinogames.ui.theme.CasinoPalette as P


@Composable
fun BaccaratScreen(
    onBack: () -> Unit,
    campaign: Boolean = false,
    onGameOverExit: () -> Unit = onBack,
    vm: BaccaratViewModel = viewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(campaign) { vm.enterMode(campaign) }
    val hand = vm.hand
    val winningSpots =
        if (hand != null && vm.phase == Phase.RESULT) BaccaratEngine.winningSpots(hand)
        else emptySet()
    var roadOpen by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0f to P.Felt, 0.55f to P.Felt, 1f to P.FeltDeep,
                )
            )
    ) {
        Watermarks()

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            // The table is designed against this height; shorter screens render
            // the whole felt proportionally smaller instead of clipping DEAL.
            val designHeight = 900.dp
            val fit = (maxHeight.value / designHeight.value).coerceAtMost(1f)
            val columnModifier = if (fit < 1f) {
                Modifier
                    .requiredHeight(designHeight)
                    .graphicsLayer {
                        scaleX = fit
                        scaleY = fit
                        // The over-tall layout is centered by the parent, so
                        // scaling about the center lands it exactly in bounds.
                        transformOrigin = TransformOrigin.Center
                    }
                    .padding(horizontal = 12.dp)
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            }
            Column(
                columnModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
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
            Spacer(Modifier.height(8.dp))
            MessageLine(vm)
            WinningsRow(vm)
            Spacer(Modifier.height(12.dp))
            PairTieRow(vm, winningSpots)
            Spacer(Modifier.height(10.dp))
            SidePlacards(vm, winningSpots)
            Spacer(Modifier.height(12.dp))
            MainCircles(vm, winningSpots)
            Spacer(Modifier.height(10.dp))
            ChipRack(vm)
            Spacer(Modifier.height(10.dp))
            ActionButtons(vm)
            if (!vm.campaign && vm.bankroll < 25 && vm.totalStaked == 0 && vm.phase == Phase.BETTING) {
                Spacer(Modifier.height(16.dp))
                PillButton("Buy back in (5,000)", solid = true, onClick = vm::buyBackIn)
            }
            }
        }
        if (vm.campaign && vm.phase == Phase.BETTING &&
            vm.bankroll < 25 && vm.totalStaked == 0
        ) {
            com.example.casinogames.ui.common.CampaignGameOver(onStartOver = {
                vm.buyBackIn()
                onGameOverExit()
            })
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
    }
}

/** Bead road that drops down from the top-right, with a pull tab under the status bar. */
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
        // End padding keeps clear of the road drawer tab in the corner.
        BankrollRow(vm, Modifier.padding(end = 58.dp))
    }
}

@Composable
private fun Watermarks() {
    Box(Modifier.fillMaxSize()) {
        Watermark(38.sp, Modifier.align(Alignment.TopCenter).padding(top = 30.dp))
        Watermark(30.sp, Modifier.align(Alignment.CenterStart).offset(x = (-30).dp, y = (-140).dp))
        Watermark(30.sp, Modifier.align(Alignment.CenterEnd).offset(x = 30.dp, y = (-140).dp))
        Watermark(34.sp, Modifier.align(Alignment.Center))
        Watermark(28.sp, Modifier.align(Alignment.BottomStart).offset(x = (-24).dp, y = (-160).dp))
        Watermark(28.sp, Modifier.align(Alignment.BottomEnd).offset(x = 24.dp, y = (-160).dp))
        Watermark(36.sp, Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))
    }
}

@Composable
private fun Watermark(size: TextUnit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "for the boys",
            fontFamily = FontFamily.Cursive,
            fontSize = size,
            color = Color(0x1AF5E1D7),
            maxLines = 1,
        )
        Text(
            "HOTEL & CASINO",
            fontSize = size * 0.24f,
            letterSpacing = 0.45.em,
            fontWeight = FontWeight.SemiBold,
            color = Color(0x29C85A50),
            maxLines = 1,
        )
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

@Composable
private fun MessageLine(vm: BaccaratViewModel) {
    val highlight = vm.phase == Phase.RESULT && vm.lastReturn > 0
    val text = vm.message +
        if (highlight) " · returned ${formatMoney(vm.lastReturn)}" else ""
    OutlinedText(
        text,
        fontSize = 14.sp,
        color = if (highlight) P.WinGlow else P.OffWhite,
        outlineWidth = 1.dp,
        textAlign = TextAlign.Center,
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WinningsRow(vm: BaccaratViewModel) {
    if (vm.phase != Phase.RESULT || vm.lastWinnings.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        Modifier.widthIn(max = 420.dp).padding(top = 6.dp),
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

private data class PairBoxDef(
    val type: BetType,
    val top: String,
    val mid: String?,
    val pays: String,
    val color: Color,
    val big: Boolean = false,
)

@Composable
private fun PairTieRow(vm: BaccaratViewModel, winningSpots: Set<BetType>) {
    val boxes = listOf(
        PairBoxDef(BetType.PLAYER_PAIR, "PLAYER", "PAIR", "11 to 1", P.PlayerBlue),
        PairBoxDef(BetType.TIE, "TIE", null, "8 to 1", P.TieGreen, big = true),
        PairBoxDef(BetType.BANKER_PAIR, "BANKER", "PAIR", "11 to 1", P.BankerRed),
    )
    Row(
        Modifier
            .widthIn(max = 440.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .border(4.dp, P.OffWhite, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        boxes.forEachIndexed { i, box ->
            if (i > 0) Box(Modifier.width(4.dp).fillMaxHeight().background(P.OffWhite))
            val active = (vm.bets[box.type] ?: 0) > 0
            val won = box.type in winningSpots
            Box(
                Modifier
                    .weight(if (box.big) 1.1f else 1f)
                    .fillMaxHeight()
                    .background(
                        when {
                            won -> P.WinGlow.copy(alpha = 0.25f)
                            active -> Color(0x38000000)
                            else -> Color.Transparent
                        }
                    )
                    .clickable(enabled = vm.phase == Phase.BETTING) { vm.addBet(box.type) }
                    .padding(top = 12.dp, bottom = 16.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedText(
                        box.top,
                        fontSize = if (box.big) 26.sp else 15.sp,
                        color = box.color,
                        outlineWidth = 1.5.dp,
                        letterSpacing = 0.05.em,
                    )
                    if (box.mid != null) {
                        OutlinedText(box.mid, fontSize = 15.sp, color = box.color, outlineWidth = 1.5.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedText(box.pays, fontSize = 14.sp, color = P.OffWhite, outlineWidth = 1.5.dp)
                }
                PlacedBetChip(
                    vm.bets[box.type] ?: 0,
                    Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

private data class PlacardDef(
    val type: BetType,
    val title: String,
    val cjk: String,
    val gradient: List<Color>,
    val columns: List<List<String>>,
    val textDark: Boolean = true,
)

@Composable
private fun SidePlacards(vm: BaccaratViewModel, winningSpots: Set<BetType>) {
    val placards = listOf(
        PlacardDef(
            BetType.FORTUNE_7, "FORTUNE 7", "威連好運 7",
            listOf(Color(0xFFF5A623), Color(0xFFE08A12)),
            listOf(listOf("Banker Wins with", "a 3-Card 7", "PAYS 40 to 1")),
        ),
        PlacardDef(
            BetType.GOLDEN_8, "GOLDEN 8", "金牌平安 8",
            listOf(Color(0xFF9BBF3B), Color(0xFF7FA32A)),
            listOf(listOf("Player Wins with", "a 3-Card 8", "PAYS 25 to 1")),
        ),
        PlacardDef(
            BetType.HEAVENLY_9, "HEAVENLY 9", "天上人間 9",
            listOf(Color(0xFFBFDCEF), Color(0xFF9CC4E0)),
            listOf(
                listOf("P & B Have", "3-Card 9", "PAYS 75 to 1"),
                listOf("P or B Has", "3-Card 9", "PAYS 10 to 1"),
            ),
        ),
        PlacardDef(
            BetType.BLAZING_7S, "BLAZING 7s", "燒倍烈火 7s",
            listOf(Color(0xFFD6402A), Color(0xFFB02415)),
            listOf(
                listOf("P & B Have", "3-Card 7s", "PAYS 125 to 1"),
                listOf("P or B Has", "3-Card 7s", "PAYS 25 to 1"),
            ),
            textDark = false,
        ),
    )
    Box(Modifier.widthIn(max = 460.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            placards.chunked(2).forEach { rowDefs ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowDefs.forEach { def ->
                        Placard(
                            def,
                            amount = vm.bets[def.type] ?: 0,
                            won = def.type in winningSpots,
                            enabled = vm.phase == Phase.BETTING,
                            onClick = { vm.addBet(def.type) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        CoverAllMedallion(vm, winningSpots, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun Placard(
    def: PlacardDef,
    amount: Int,
    won: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .then(
                if (won) Modifier.border(3.dp, P.WinGlow, RoundedCornerShape(14.dp))
                else if (amount > 0) Modifier.border(2.dp, Color(0xA6FFFFFF), RoundedCornerShape(14.dp))
                else Modifier
            )
            .padding(if (won) 3.dp else if (amount > 0) 2.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(def.gradient))
            .border(3.dp, P.Ink, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(top = 8.dp, bottom = 14.dp, start = 6.dp, end = 6.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedText(
                def.title,
                fontSize = 16.sp,
                color = P.GoldText,
                outlineWidth = 1.5.dp,
                letterSpacing = 0.04.em,
                textAlign = TextAlign.Center,
            )
            OutlinedText(def.cjk, fontSize = 12.sp, color = P.GoldText, outlineWidth = 1.dp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.height(IntrinsicSize.Min)) {
                def.columns.forEachIndexed { i, lines ->
                    if (i > 0) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(
                                    if (def.textDark) P.Ink else P.GoldText.copy(alpha = 0.8f)
                                )
                        )
                    }
                    Column(
                        Modifier.weight(1f).padding(horizontal = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        lines.forEach { line ->
                            if (def.textDark) {
                                Text(
                                    line,
                                    color = P.Ink,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                OutlinedText(
                                    line,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    color = P.GoldText,
                                    outlineWidth = 1.dp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
        PlacedBetChip(amount, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun CoverAllMedallion(
    vm: BaccaratViewModel,
    winningSpots: Set<BetType>,
    modifier: Modifier = Modifier,
) {
    val amount = vm.bets[BetType.COVER_ALL] ?: 0
    val won = BetType.COVER_ALL in winningSpots
    Box(
        modifier
            .size(96.dp)
            .then(
                if (won) Modifier.border(3.dp, P.WinGlow, CircleShape)
                else if (amount > 0) Modifier.border(2.dp, Color(0xA6FFFFFF), CircleShape)
                else Modifier
            )
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFFFFE45C), Color(0xFFD9A21B)),
                    center = androidx.compose.ui.geometry.Offset.Unspecified,
                ),
                CircleShape,
            )
            .border(3.dp, P.Ink, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = vm.phase == Phase.BETTING) { vm.addBet(BetType.COVER_ALL) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "THE FORTUNE EVENT PAYS",
                fontSize = 6.sp, fontWeight = FontWeight.ExtraBold,
                color = P.Ink, letterSpacing = 0.05.em,
            )
            OutlinedText("COVER", fontSize = 15.sp, color = Color(0xFFC22A18), outlineWidth = 1.dp, lineHeight = 15.sp)
            OutlinedText("ALL", fontSize = 15.sp, color = Color(0xFFC22A18), outlineWidth = 1.dp, lineHeight = 15.sp)
            Text(
                "PAYS 6 TO 1",
                fontSize = 6.sp, fontWeight = FontWeight.ExtraBold,
                color = P.Ink, letterSpacing = 0.05.em,
            )
        }
        PlacedBetChip(amount)
    }
}

@Composable
private fun MainCircles(vm: BaccaratViewModel, winningSpots: Set<BetType>) {
    Row(horizontalArrangement = Arrangement.spacedBy(50.dp)) {
        MainCircle("PLAYER", P.PlayerBlue, BetType.PLAYER, vm, BetType.PLAYER in winningSpots)
        MainCircle("BANKER", P.BankerRed, BetType.BANKER, vm, BetType.BANKER in winningSpots)
    }
}

@Composable
private fun MainCircle(
    label: String,
    color: Color,
    type: BetType,
    vm: BaccaratViewModel,
    won: Boolean,
) {
    val active = (vm.bets[type] ?: 0) > 0
    Box(Modifier.size(width = 140.dp, height = 136.dp)) {
        ArchedLabel(
            label, color,
            Modifier.align(Alignment.TopCenter).size(width = 140.dp, height = 56.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .size(102.dp)
                .background(
                    when {
                        won -> P.WinGlow.copy(alpha = 0.18f)
                        active -> Color(0x33000000)
                        else -> Color.Transparent
                    },
                    CircleShape,
                )
                .border(2.dp, if (won) P.WinGlow else P.Ink, CircleShape)
                .padding(2.dp)
                .border(6.dp, color, CircleShape)
                .padding(6.dp)
                .border(2.dp, P.Ink, CircleShape)
                .clip(CircleShape)
                .clickable(enabled = vm.phase == Phase.BETTING) { vm.addBet(type) },
        )
        PlacedBetChip(
            vm.bets[type] ?: 0,
            Modifier.align(Alignment.BottomCenter).offset(y = (-26).dp),
        )
    }
}

@Composable
private fun ArchedLabel(text: String, color: Color, modifier: Modifier) {
    val fillColor = color.toArgb()
    val inkColor = P.Ink.toArgb()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = android.graphics.Path().apply {
            moveTo(w * 0.04f, h * 0.98f)
            quadTo(w * 0.5f, -h * 0.05f, w * 0.96f, h * 0.98f)
        }
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 18.sp.toPx()
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD
            )
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val tracking = paint.textSize * 0.1f
        val glyphWidths = FloatArray(text.length)
        paint.getTextWidths(text, glyphWidths)
        val textLength = glyphWidths.sum() + tracking * (text.length - 1)

        val measure = android.graphics.PathMeasure(path, false)
        // Draw each glyph rotated to the arc's tangent; unlike drawTextOnPath,
        // this never silently drops glyphs that run past the measured path.
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        drawIntoCanvas { canvas ->
            var distance = (measure.length - textLength) / 2f
            for (i in text.indices) {
                val center = (distance + glyphWidths[i] / 2f)
                    .coerceIn(0f, measure.length)
                measure.getPosTan(center, pos, tan)
                val angle = Math.toDegrees(
                    kotlin.math.atan2(tan[1].toDouble(), tan[0].toDouble())
                ).toFloat()
                canvas.nativeCanvas.save()
                canvas.nativeCanvas.rotate(angle, pos[0], pos[1])
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 2.dp.toPx()
                paint.color = inkColor
                canvas.nativeCanvas.drawText(text, i, i + 1, pos[0], pos[1], paint)
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = fillColor
                canvas.nativeCanvas.drawText(text, i, i + 1, pos[0], pos[1], paint)
                canvas.nativeCanvas.restore()
                distance += glyphWidths[i] + tracking
            }
        }
    }
}

@Composable
private fun ChipRack(vm: BaccaratViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chipsFor(vm.bankroll).forEach { chip ->
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
                PillButton("DEAL", solid = true, onClick = vm::deal)
                PillButton("Undo", solid = false, onClick = vm::undoBet)
                PillButton("Clear", solid = false, onClick = vm::clearBets)
            }
            Phase.RESULT -> {
                PillButton("REBET", solid = true, onClick = { vm.nextHand(true) })
                PillButton("New bets", solid = false, onClick = { vm.nextHand(false) })
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
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            letterSpacing = 0.08.em,
        )
    }
}

@Composable
private fun BankrollRow(vm: BaccaratViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier,
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
