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
 * The two tables behind the lobby's POKER card. DJ Wild's rules are written
 * and tested; its felt is not built yet, so the card does not open.
 */
enum class PokerVariant(val available: Boolean) {
    ULTIMATE_TEXAS_HOLDEM(true),
    DJ_WILD(false),
}

// Dead flat black: the page art runs to black at its own edges, so any tint
// behind it would show as a band where the image stops.
private val PageBlack = Color(0xFF000000)

/** The page art, and the marks measured off it (853 x 1844). */
private const val PageWidth = 853f
private const val PageHeight = 1844f
private val CardBands = listOf(
    PokerVariant.ULTIMATE_TEXAS_HOLDEM to (886f to 1166f),
    PokerVariant.DJ_WILD to (1202f to 1490f),
)
/** The art paints the menu button; only its press is caught. */
private const val MenuCentreX = 74f
private const val MenuCentreY = 192f
private const val MenuDiameter = 86f
/** Where the printed campaign line was healed out, for the live one to sit. */
private const val CampaignCentreY = 813f

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
                    contentDescription = "Poker — Ultimate Texas Hold'em or DJ Wild",
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
