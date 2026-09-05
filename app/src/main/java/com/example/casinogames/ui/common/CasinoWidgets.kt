package com.example.casinogames.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.casinogames.campaign.CAMPAIGN_START
import com.example.casinogames.campaign.Campaign
import com.example.casinogames.campaign.DEBT_CEILING
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.campaign.FreePlayLimits
import com.example.casinogames.campaign.MARKER_AMOUNT
import com.example.casinogames.campaign.MARKER_INTEREST
import com.example.casinogames.campaign.TableLimits
import androidx.compose.ui.text.font.FontStyle
import com.example.casinogames.R
import com.example.casinogames.ui.theme.CasinoPalette
import java.util.Locale

data class ChipDef(val value: Int, val imageRes: Int)

/** The house chip set, shared by every game's rack and bet spots. */
val CASINO_CHIPS = listOf(
    ChipDef(25, R.drawable.chip_25),
    ChipDef(100, R.drawable.chip_100),
    ChipDef(500, R.drawable.chip_500),
    ChipDef(1000, R.drawable.chip_1k),
    ChipDef(5000, R.drawable.chip_5k),
)

/** Gold 25K chip — the top of the rack, and only the top rooms rack it. */
val GOLD_CHIP = ChipDef(25_000, R.drawable.chip_25k)
private val ALL_CHIPS = CASINO_CHIPS + GOLD_CHIP

/**
 * The rack a table lays out. In the campaign the room decides: no chip is
 * put out that the table would refuse, so the rack itself tells you where
 * you are. Free play has no room, so it goes on the purse as it always did.
 */
fun chipsFor(bankroll: Double, limits: TableLimits = FreePlayLimits): List<ChipDef> {
    val ceiling = when {
        limits.enforced -> limits.max
        bankroll >= 100_000 -> GOLD_CHIP.value
        else -> CASINO_CHIPS.last().value
    }
    return ALL_CHIPS.filter { it.value <= ceiling }.ifEmpty { listOf(ALL_CHIPS.first()) }
}

/** Formats a bankroll amount: whole numbers without decimals, otherwise 2 places. */
fun formatMoney(value: Double): String =
    if (value % 1.0 == 0.0) String.format(Locale.US, "%,.0f", value)
    else String.format(Locale.US, "%,.2f", value)

/** Bold text with the felt-sign ink outline, like the printed table lettering. */
@Composable
fun OutlinedText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    outlineColor: Color = CasinoPalette.Ink,
    outlineWidth: Dp = 2.dp,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
) {
    val strokePx = with(LocalDensity.current) { outlineWidth.toPx() * 2 }
    val base = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.SansSerif,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
    )
    Box(modifier) {
        Text(
            text,
            style = base.copy(
                color = outlineColor,
                drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
            ),
            textAlign = textAlign,
        )
        Text(text, style = base.copy(color = color), textAlign = textAlign)
    }
}

/** Small ink pill showing the amount staked on a bet spot. */
@Composable
fun BetAmountBadge(amount: Int, modifier: Modifier = Modifier) {
    if (amount <= 0) return
    Box(
        modifier = modifier
            .offset(y = 10.dp)
            .background(CasinoPalette.Ink, RoundedCornerShape(999.dp))
            .border(1.5.dp, CasinoPalette.GoldTrim, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(
            String.format(Locale.US, "%,d", amount),
            color = Color(0xFFEDD98B),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/**
 * A staked bet sitting on the felt as a real chip. Exact denominations show the
 * chip alone (its value is printed on the art); mixed amounts show the largest
 * chip that fits, dimmed, with the total printed across it chip-sized.
 */
@Composable
fun PlacedBetChip(
    amount: Int,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    /** Lets a table swap in its own chip art, keyed by chip value. */
    artFor: ((Int) -> Int)? = null,
) {
    if (amount <= 0) return
    val chip = ALL_CHIPS.lastOrNull { it.value <= amount }
    val exact = chip != null && chip.value == amount
    val fontScale = size.value / 50f
    Box(
        modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (chip != null) {
            Image(
                painter = painterResource(artFor?.invoke(chip.value) ?: chip.imageRes),
                contentDescription = "$amount staked",
                modifier = Modifier.fillMaxSize().scale(1.06f),
                contentScale = ContentScale.Crop,
            )
        }
        if (!exact) {
            val label = String.format(Locale.US, "%,d", amount)
            Box(
                Modifier.fillMaxSize().background(Color(0xA30E0A0B)),
                contentAlignment = Alignment.Center,
            ) {
                OutlinedText(
                    label,
                    fontSize = when {
                        label.length <= 3 -> (17 * fontScale).sp
                        label.length <= 5 -> (13 * fontScale).sp
                        else -> (10 * fontScale).sp
                    },
                    color = CasinoPalette.WinGlow,
                    outlineWidth = 1.dp,
                )
            }
        }
    }
}

/**
 * Full-screen campaign bust overlay. While the credit line holds, the house
 * covers the player with another marker. Once what is owed hits the ceiling
 * there is nothing left to sign, and the campaign is over — the overlay does
 * the signing or the burying itself, so every table shows the same house.
 */
@Composable
fun CampaignGameOver(onDone: () -> Unit) {
    val credit = Campaign.canTakeMarker
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2050408))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "TAPPED OUT",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.14.em,
                color = Color(0xFFFF3B5C),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (credit) "The purse is gone. The house will cover you."
                else "The purse is gone, and the house is done covering you.",
                fontSize = 13.sp,
                color = Color(0xB3FFFFFF),
            )
            if (Campaign.debt > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Already owing ${formatMoney(Campaign.debt)}" +
                        if (credit) "" else " of a ${formatMoney(DEBT_CEILING)} line",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF3B5C),
                )
            }
            if (!credit) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Seek Gamblers Anonymous.",
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xB3FFFFFF),
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                if (credit) "SIGN A ${formatMoney(MARKER_AMOUNT)} MARKER"
                else "START A NEW CAMPAIGN",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.08.em,
                color = Color(0xFF050408),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFFFD24D))
                    .clickable {
                        if (credit) Campaign.takeMarker() else Campaign.restart()
                        onDone()
                    }
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
            Spacer(Modifier.height(10.dp))
            // The whole point of the marker: it is not a free reset.
            Text(
                if (credit) {
                    "${formatMoney(MARKER_AMOUNT * (1 + MARKER_INTEREST))} to clear · " +
                        "no moving up until you do"
                } else {
                    "the debt dies with the run · fresh ${formatMoney(CAMPAIGN_START)} " +
                        "in the Basement"
                },
                fontSize = 10.sp,
                letterSpacing = 0.04.em,
                color = Color(0x8CFFFFFF),
            )
        }
    }
}

/** Full-screen campaign victory overlay: bank the win and climb, or cash out and restart. */
@Composable
fun CampaignComplete(
    goal: Double,
    nextGoal: Double,
    onGoBigger: () -> Unit,
    onStartOver: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2050408))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏆", fontSize = 52.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "CAMPAIGN COMPLETE",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.1.em,
                color = Color(0xFFFFD24D),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "You hit \$${formatMoney(goal)} — the boys are rich.",
                fontSize = 13.sp,
                color = Color(0xB3FFFFFF),
            )
            Spacer(Modifier.height(34.dp))
            Text(
                "GO FOR \$${formatMoney(nextGoal)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.08.em,
                color = Color(0xFF050408),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFFFD24D))
                    .clickable(onClick = onGoBigger)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "START OVER · \$5,000",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.08.em,
                color = Color(0xCCFFFFFF),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(999.dp))
                    .clickable(onClick = onStartOver)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/** Casino chip rendered from sprite art; lifts and glows when selected. */
@Composable
fun CasinoChip(
    imageRes: Int,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = CasinoPalette.WinGlow,
) {
    Box(
        modifier = modifier
            .offset(y = if (selected) (-4).dp else 0.dp)
            .size(50.dp)
            .then(
                if (selected) Modifier.border(1.dp, selectedColor, CircleShape)
                else Modifier
            )
            .padding(if (selected) 1.dp else 0.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().scale(1.06f),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * Play testing's buy-in picker: the front door from the menu, and the same
 * dialog again when a busted purse buys back. The slider walks $5K to $50K
 * in $2,500 clicks; TAKE A SEAT hands the chosen figure to [onConfirm].
 */
@Composable
fun FreePlayBuyIn(onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    val accent = Color(0xFFB98CFF)
    var amount by remember {
        mutableFloatStateOf(FreePlay.buyIn.toFloat().coerceIn(5_000f, 50_000f))
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF60A0612))
                .border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "PLAY TESTING",
                color = accent, fontSize = 13.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.2.em,
            )
            Spacer(Modifier.height(10.dp))
            Text("BUY-IN", color = Color(0x99FFFFFF), fontSize = 10.sp, letterSpacing = 0.14.em)
            Text(
                formatMoney(amount.toDouble()),
                color = Color(0xFFE8C169), fontSize = 28.sp, fontWeight = FontWeight.Black,
            )
            Slider(
                value = amount,
                onValueChange = { amount = Math.round(it / 2_500f) * 2_500f },
                valueRange = 5_000f..50_000f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = accent.copy(alpha = 0.25f),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("$5K", color = Color(0x66FFFFFF), fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text("$50K", color = Color(0x66FFFFFF), fontSize = 10.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "TAKE A SEAT",
                color = Color(0xFF0A0612), fontSize = 13.sp, fontWeight = FontWeight.Black,
                letterSpacing = 0.1.em,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
                    .clickable { onConfirm(amount.toDouble()) }
                    .padding(horizontal = 26.dp, vertical = 11.dp),
            )
        }
    }
}
