package com.example.casinogames.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.casinogames.R
import com.example.casinogames.games.core.Card
import com.example.casinogames.ui.theme.CasinoPalette

val CardWidth = 52.dp
val CardHeight = 78.dp

@Composable
fun EmptyCardSlot() {
    Box(
        Modifier
            .size(CardWidth, CardHeight)
            .drawBehind {
                drawRoundRect(
                    color = Color(0x4DF5F1E8),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 5.dp.toPx())
                        ),
                    ),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
    )
}

/** [scale] draws the same card larger, pips and all, for tables with room. */
@Composable
fun PlayingCardView(card: Card, faceUp: Boolean, scale: Float = 1f) {
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 0f else 180f,
        animationSpec = tween(480),
        label = "cardFlip",
    )
    Box(
        Modifier
            .size(CardWidth * scale, CardHeight * scale)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 16f * density
            }
    ) {
        if (rotation <= 90f) {
            CardFront(card, scale)
        } else {
            CardBack(Modifier.graphicsLayer { rotationY = 180f }, scale)
        }
    }
}

@Composable
private fun CardFront(card: Card, scale: Float = 1f) {
    val ink = if (card.suit.isRed) Color(0xFFB3222E) else Color(0xFF141014)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF6EC), RoundedCornerShape(6.dp * scale))
            .border(1.dp * scale, Color(0x66000000), RoundedCornerShape(6.dp * scale))
            .padding(horizontal = 5.dp * scale, vertical = 4.dp * scale)
    ) {
        Text(
            card.rank.label,
            color = ink, fontSize = 13.sp * scale,
            fontWeight = FontWeight.Black, lineHeight = 13.sp * scale,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            card.suit.symbol,
            color = ink, fontSize = 20.sp * scale,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            card.rank.label,
            color = ink, fontSize = 10.sp * scale, fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .graphicsLayer { rotationZ = 180f },
        )
    }
}

@Composable
private fun CardBack(modifier: Modifier = Modifier, scale: Float = 1f) {
    Image(
        painter = painterResource(R.drawable.card_back),
        contentDescription = null,
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp * scale)),
        contentScale = ContentScale.Crop,
    )
}
