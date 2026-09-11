package com.example.casinogames.games.poker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.casinogames.R
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

/**
 * The page art, and the marks measured off it (941 x 1766). It is the lobby
 * page's own header — logo, HOTEL & CASINO, the healed campaign line — with
 * the three poker cards composited where the lobby's cards sit, one crop of
 * the pokerbuttons sheet (art/pokerbuttons-sheet.png rows 400-1240) scaled
 * to the lobby cards' width and laid at (44, 734).
 */
private const val PageWidth = 941f
private const val PageHeight = 1766f
private val CardBands = listOf(
    PokerVariant.ULTIMATE_TEXAS_HOLDEM to (750f to 1034f),
    PokerVariant.DJ_WILD to (1084f to 1417f),
    PokerVariant.TEXAS_SHOOTOUT to (1434f to 1746f),
)
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
