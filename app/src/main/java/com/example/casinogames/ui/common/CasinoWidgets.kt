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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

/** Gold 25K chip — joins the rack once a 100k bankroll is reached. */
val GOLD_CHIP = ChipDef(25_000, R.drawable.chip_25k)
private val ALL_CHIPS = CASINO_CHIPS + GOLD_CHIP

fun chipsFor(bankroll: Double): List<ChipDef> =
    if (bankroll >= 100_000) ALL_CHIPS else CASINO_CHIPS

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

/** Full-screen campaign bust overlay: the run is dead until they start over. */
@Composable
fun CampaignGameOver(onStartOver: () -> Unit) {
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
                "GAME OVER",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.14.em,
                color = Color(0xFFFF3B5C),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "The bankroll's gone — campaign busted.",
                fontSize = 13.sp,
                color = Color(0xB3FFFFFF),
            )
            Spacer(Modifier.height(30.dp))
            Text(
                "GO TO THE BANK AND GET A LOAN",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.08.em,
                color = Color(0xFF050408),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFFFD24D))
                    .clickable(onClick = onStartOver)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
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
