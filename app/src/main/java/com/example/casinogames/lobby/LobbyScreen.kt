package com.example.casinogames.lobby

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
import androidx.compose.ui.unit.dp
import com.example.casinogames.R

enum class GameId(val available: Boolean) {
    BACCARAT(true),
    /** Opens the blackjack variants page rather than a table directly. */
    BLACKJACK(true),
    /** Opens the poker variants page rather than a table directly. */
    POKER(true),
    ROULETTE(true),
    CRAPS(false),
}

// Dead flat black: the page art runs to black at its own edges, and any
// tint behind it showed as a band where the image stops.
private val LobbyBlack = Color(0xFF000000)

/** The page art, and where each card's band sits inside it (941 x 1610). */
private const val PageWidth = 941f
private const val PageHeight = 1610f
/**
 * Measured off the art itself. The page came in as a phone screenshot with a
 * status bar on it, so it was cropped, the printed campaign line healed out
 * to read live, and the cards dropped forty pixels into the space at the foot
 * to leave room for the line and the room plate above them.
 */
private val CardBands = listOf(
    GameId.BACCARAT to (666f to 859f),
    GameId.BLACKJACK to (872f to 1060f),
    GameId.POKER to (1069f to 1256f),
    GameId.ROULETTE to (1269f to 1439f),
    GameId.CRAPS to (1449f to 1600f),
)
/**
 * The art paints the menu button itself, so only its press is caught. The
 * campaign line was healed out of the page and is drawn live on its mark.
 */
private const val MenuCentreX = 81f
private const val MenuCentreY = 67f
private const val MenuDiameter = 86f
private const val CampaignCentreY = 578f

/**
 * The lobby is one piece of art. Rather than rebuilding the cards, it is drawn
 * as it was made and each card's band is a tap target — with the menu button
 * and the campaign line put back live, since one moves and the other is the
 * player's own bankroll.
 */
@Composable
fun LobbyScreen(
    onOpenGame: (GameId) -> Unit,
    campaign: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().background(LobbyBlack)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Fit the page whole, whichever edge runs out first.
            val artWidth = minOf(maxWidth, maxHeight * (PageWidth / PageHeight))
            val artHeight = artWidth * (PageHeight / PageWidth)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(artWidth)
                    .height(artHeight)
            ) {
                Image(
                    painter = painterResource(R.drawable.lobby_page),
                    contentDescription = "4 The Boys — Hotel & Casino",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                CampaignPlate(
                    campaign = campaign,
                    artWidth = artWidth,
                    topOffset = artHeight * (CampaignCentreY / PageHeight),
                )
                if (onBack != null) {
                    // The art draws the button; this only catches the press.
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
                }
                CardBands.forEach { (game, band) ->
                    val (top, bottom) = band
                    Box(
                        Modifier
                            .offset(y = artHeight * (top / PageHeight))
                            .fillMaxWidth()
                            .height(artHeight * ((bottom - top) / PageHeight))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = game.available,
                            ) { onOpenGame(game) }
                    )
                }
            }
        }
    }
}

