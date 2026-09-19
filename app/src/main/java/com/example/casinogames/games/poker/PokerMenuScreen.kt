package com.example.casinogames.games.poker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.casinogames.R
import com.example.casinogames.games.miniuth.MiniRed
import com.example.casinogames.lobby.CampaignPlate

/**
 * The four tables behind the lobby's POKER card. All are built.
 */
enum class PokerVariant(val available: Boolean) {
    ULTIMATE_TEXAS_HOLDEM(true),
    DJ_WILD(true),
    TEXAS_SHOOTOUT(true),
    MINI_UTH(true),
}

// Dead flat black: the page art runs to black at its own edges, so any tint
// behind it would show as a band where the image stops.
private val PageBlack = Color(0xFF000000)

/**
 * The page art, and the marks measured off it (941 x 2096). It is the lobby
 * page's own header — logo, HOTEL & CASINO, the healed campaign line — with
 * the three painted poker cards composited where the lobby's cards sit, one
 * crop of the pokerbuttons sheet (art/pokerbuttons-sheet.png rows 400-1240)
 * scaled to the lobby cards' width and laid at (44, 734). The canvas was then
 * grown 330px in plain black to leave a slot for a fourth card under them.
 */
private const val PageWidth = 941f
private const val PageHeight = 2096f
private val CardBands = listOf(
    PokerVariant.ULTIMATE_TEXAS_HOLDEM to (750f to 1034f),
    PokerVariant.DJ_WILD to (1084f to 1417f),
    PokerVariant.TEXAS_SHOOTOUT to (1434f to 1746f),
)
/**
 * Mini UTH came after the sheet was painted, so its card is drawn live in the
 * slot the grown canvas leaves — the painted cards' edges and Shootout's
 * height, so the four read as a set.
 */
private const val MiniTop = 1766f
private const val MiniBottom = 2078f
private const val CardLeft = 61f
private const val CardRight = 880f
/** The art paints the menu button; only its press is caught — the lobby's mark. */
private const val MenuCentreX = 75f
private const val MenuCentreY = 87f
private const val MenuDiameter = 90f
/** Where the lobby's printed campaign line was healed out, for the live one to sit. */
private const val CampaignCentreY = 620f

/**
 * The poker page is one piece of art, drawn as it was made, with each card's
 * band a tap target. It carries the campaign line and room plate the same way
 * the lobby does — it is a room you stand in, not a table you sit at.
 */
@Composable
fun PokerMenuScreen(
    onBack: () -> Unit,
    onPick: (PokerVariant) -> Unit,
    campaign: Boolean = false,
) {
    Box(Modifier.fillMaxSize().background(PageBlack)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            // Fit the page whole, whichever edge runs out first.
            val artWidth = minOf(maxWidth, maxHeight * (PageWidth / PageHeight))
            val artHeight = artWidth * (PageHeight / PageWidth)
            Box(Modifier.align(Alignment.Center).width(artWidth).height(artHeight)) {
                Image(
                    painter = painterResource(R.drawable.poker_page),
                    contentDescription = "Poker — Ultimate Texas Hold'em, DJ Wild, Texas Shootout or Mini UTH",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                CampaignPlate(
                    campaign = campaign,
                    artWidth = artWidth,
                    topOffset = artHeight * (CampaignCentreY / PageHeight),
                )
                val menu = artWidth * (MenuDiameter / PageWidth)
                Box(
                    Modifier
                        .offset(
                            x = artWidth * (MenuCentreX / PageWidth) - menu / 2,
                            y = artHeight * (MenuCentreY / PageHeight) - menu / 2,
                        )
                        .size(menu)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack,
                        )
                )
                MiniUthCard(
                    modifier = Modifier
                        .offset(
                            x = artWidth * (CardLeft / PageWidth),
                            y = artHeight * (MiniTop / PageHeight),
                        )
                        .width(artWidth * ((CardRight - CardLeft) / PageWidth))
                        .height(artHeight * ((MiniBottom - MiniTop) / PageHeight)),
                    onClick = { onPick(PokerVariant.MINI_UTH) },
                )
                CardBands.forEach { (variant, band) ->
                    val (top, bottom) = band
                    Box(
                        Modifier
                            .offset(y = artHeight * (top / PageHeight))
                            .fillMaxWidth()
                            .height(artHeight * ((bottom - top) / PageHeight))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = variant.available,
                            ) { onPick(variant) }
                    )
                }
            }
        }
    }
}

/**
 * A live card in the art's own manner: a neon-edged plate, the name in a
 * heavy slant, two lines of pitch and a PLAY ring on the right. Red, off the
 * real felt's lettering, so it is none of the painted three's colours.
 */
@Composable
private fun MiniUthCard(modifier: Modifier, onClick: () -> Unit) {
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF12040A))
            .border(2.dp, MiniRed, RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        // Everything is sized off the plate so it scales with the page art.
        val unit = maxWidth / 100f
        val density = LocalDensity.current
        fun sp(units: Float) = with(density) { (unit * units).toSp() }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(MiniRed.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = with(density) { maxWidth.toPx() },
                    )
                )
        )
        // One big pip where the painted cards put their card art: one card is the game.
        Text(
            // The text-style heart: the bare code point draws as a fat emoji.
            "♥︎",
            color = MiniRed,
            fontSize = sp(12f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = unit * 6f)
                .alpha(0.6f),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = unit * 25f),
        ) {
            Text(
                "MINI UTH",
                color = MiniRed,
                fontSize = sp(9f),
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.02.em,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(unit * 1.5f))
            Text(
                "ONE CARD · THREE-CARD HANDS",
                color = Color(0xFFF5F1E8),
                fontSize = sp(3f),
                letterSpacing = 0.04.em,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(unit * 1.2f))
            Text(
                "3X  •  2X  •  1X",
                color = Color(0xFFF5F1E8),
                fontSize = sp(3f),
                letterSpacing = 0.04.em,
                maxLines = 1,
                softWrap = false,
            )
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = unit * 5f)
                .size(unit * 17f)
                .border(2.dp, MiniRed, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "PLAY",
                color = Color(0xFFF5F1E8),
                fontSize = sp(4.4f),
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.04.em,
            )
        }
    }
}
