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
import com.example.casinogames.games.shootout.ShootoutAmber
import com.example.casinogames.lobby.CampaignPlate

/**
 * The three tables behind the lobby's POKER card. All are built.
 */
enum class PokerVariant(val available: Boolean) {
    ULTIMATE_TEXAS_HOLDEM(true),
    DJ_WILD(true),
    TEXAS_SHOOTOUT(true),
}

// Dead flat black: the page art runs to black at its own edges, so any tint
// behind it would show as a band where the image stops.
private val PageBlack = Color(0xFF000000)

/** The page art, and the marks measured off it (853 x 1844). */
private const val PageWidth = 853f
private const val PageHeight = 1844f
/**
 * Both cards were dropped 48px down the page: the room plate is drawn live and
 * its pill sat over the top edge of the UTH card, which the art could not know
 * about. There is black to spare below them.
 */
private val CardBands = listOf(
    PokerVariant.ULTIMATE_TEXAS_HOLDEM to (934f to 1214f),
    PokerVariant.DJ_WILD to (1250f to 1538f),
)
/** The art paints the menu button; only its press is caught. */
private const val MenuCentreX = 74f
private const val MenuCentreY = 192f
private const val MenuDiameter = 86f
/** Where the printed campaign line was healed out, for the live one to sit. */
private const val CampaignCentreY = 813f
/**
 * Texas Shootout came after the page was painted, so its card is drawn live
 * in the black the art left under DJ Wild — the painted cards' width and a
 * little shorter, so the three read as a set.
 */
private const val ShootoutTop = 1578f
private const val ShootoutBottom = 1800f
private const val CardLeft = 76f
private const val CardRight = 778f

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
                    contentDescription = "Poker — Ultimate Texas Hold'em, DJ Wild or Texas Shootout",
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
                ShootoutCard(
                    modifier = Modifier
                        .offset(
                            x = artWidth * (CardLeft / PageWidth),
                            y = artHeight * (ShootoutTop / PageHeight),
                        )
                        .width(artWidth * ((CardRight - CardLeft) / PageWidth))
                        .height(artHeight * ((ShootoutBottom - ShootoutTop) / PageHeight)),
                    onClick = { onPick(PokerVariant.TEXAS_SHOOTOUT) },
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
 * heavy slant, two lines of pitch and a PLAY ring on the right. Amber, so it
 * is neither hold'em's green nor DJ Wild's blue.
 */
@Composable
private fun ShootoutCard(modifier: Modifier, onClick: () -> Unit) {
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF120B03))
            .border(2.dp, ShootoutAmber, RoundedCornerShape(22.dp))
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
                        listOf(ShootoutAmber.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = with(density) { maxWidth.toPx() },
                    )
                )
        )
        // A big faded pip where the painted cards put their card art.
        Text(
            "♠",
            color = ShootoutAmber,
            fontSize = sp(15f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = unit * 5f)
                .alpha(0.5f),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = unit * 19f),
        ) {
            Text(
                "TEXAS SHOOTOUT",
                color = ShootoutAmber,
                fontSize = sp(6.6f),
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.02.em,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(unit * 2f))
            Text(
                "FOUR CARDS · KEEP TWO OR SPLIT",
                color = Color(0xFFF5F1E8),
                fontSize = sp(3f),
                letterSpacing = 0.04.em,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(unit * 1.5f))
            Text(
                "BONUS  •  DEALER WINS TIES",
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
                .border(2.dp, ShootoutAmber, CircleShape),
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
