package com.example.casinogames.games.blackjack

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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.casinogames.R

private val MenuBlack = Color(0xFF040308)
private val NeonPurpleDim = Color(0x998B30D9)

enum class BlackjackVariant { FREE_BET, BLAZING_777, DOUBLE_DOWN }

/** The page art, and where each variant's card sits inside it. */
private const val PageWidth = 941f
private const val PageHeight = 1672f
private val CardBands = listOf(
    BlackjackVariant.FREE_BET to (466f to 854f),
    BlackjackVariant.BLAZING_777 to (883f to 1227f),
    BlackjackVariant.DOUBLE_DOWN to (1269f to 1622f),
)

/**
 * The whole variants page is one piece of art. Rather than slicing it into
 * cards, it is drawn as it was made and each card's band is a tap target.
 */
@Composable
fun BlackjackMenuScreen(
    onBack: () -> Unit,
    onPick: (BlackjackVariant) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MenuBlack)) {
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
                    painter = painterResource(R.drawable.bj_modes_page),
                    contentDescription = "Blackjack variants",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
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
                            ) { onPick(variant) }
                    )
                }
            }
        }
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 18.dp, top = 8.dp)
                .size(44.dp)
                .border(1.5.dp, NeonPurpleDim, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("←", color = Color(0xFFB98CFF), fontSize = 20.sp)
        }
    }
}
