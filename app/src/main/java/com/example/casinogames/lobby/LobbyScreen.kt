package com.example.casinogames.lobby

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.casinogames.R
import com.example.casinogames.ui.common.formatMoney

enum class GameId(val available: Boolean) {
    BACCARAT(true),
    /** Opens the blackjack variants page rather than a table directly. */
    BLACKJACK(true),
    ULTIMATE_TEXAS_HOLDEM(true),
    ROULETTE(true),
    CRAPS(false),
}

private val LobbyBlack = Color(0xFF040308)
private val NeonPurpleDim = Color(0x998B30D9)
private val CampaignPink = Color(0xFFE24BC8)

/** The page art, and where each card's band sits inside it (851 x 1785). */
private const val PageWidth = 851f
private const val PageHeight = 1785f
private val CardBands = listOf(
    GameId.BACCARAT to (676f to 871f),
    GameId.BLACKJACK to (887f to 1081f),
    GameId.ULTIMATE_TEXAS_HOLDEM to (1099f to 1291f),
    GameId.ROULETTE to (1309f to 1481f),
    GameId.CRAPS to (1498f to 1670f),
)
/** The menu button and the campaign line, painted out of the art so they can live. */
private const val MenuCentreX = 80f
private const val MenuCentreY = 556f
private const val CampaignCentreY = 625f

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
                if (campaign) {
                    val ctx = LocalContext.current
                    val prefs = remember {
                        ctx.getSharedPreferences("campaign", Context.MODE_PRIVATE)
                    }
                    val saved = remember { prefs.getFloat("bankroll", 5000f).toDouble() }
                    val goal = remember { prefs.getFloat("goal", 1_000_000f).toDouble() }
                    Text(
                        "CAMPAIGN · ${formatMoney(saved)} / ${formatMoney(goal)}",
                        fontSize = (artWidth.value * 0.033f).sp,
                        letterSpacing = 0.1.em,
                        fontWeight = FontWeight.Black,
                        color = CampaignPink,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = artHeight * (CampaignCentreY / PageHeight) - 12.dp),
                    )
                }
                if (onBack != null) {
                    val menu = artWidth * 0.105f
                    Box(
                        Modifier
                            .offset(
                                x = artWidth * (MenuCentreX / PageWidth) - menu / 2,
                                y = artHeight * (MenuCentreY / PageHeight) - menu / 2,
                            )
                            .size(menu)
                            .border(1.5.dp, NeonPurpleDim, CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("☰", color = Color(0xFFB98CFF), fontSize = (menu.value * 0.4f).sp)
                    }
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
