package com.example.casinogames.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun PlayingCardView(card: Card, faceUp: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 0f else 180f,
        animationSpec = tween(480),
        label = "cardFlip",
    )
    Box(
        Modifier
            .size(CardWidth, CardHeight)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 16f * density
            }
    ) {
        if (rotation <= 90f) {
            CardFront(card)
        } else {
            CardBack(Modifier.graphicsLayer { rotationY = 180f })
        }
    }
}

@Composable
private fun CardFront(card: Card) {
    val ink = if (card.suit.isRed) Color(0xFFB3222E) else Color(0xFF141014)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF6EC), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x66000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp)
    ) {
        Text(
            card.rank.label,
            color = ink, fontSize = 13.sp,
            fontWeight = FontWeight.Black, lineHeight = 13.sp,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(card.suit.symbol, color = ink, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
        Text(
            card.rank.label,
            color = ink, fontSize = 10.sp, fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .graphicsLayer { rotationZ = 180f },
        )
    }
}

@Composable
private fun CardBack(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    0f to Color(0xFF401012), 0.5f to Color(0xFF401012),
                    0.5f to Color(0xFF521619), 1f to Color(0xFF521619),
                    start = Offset.Zero,
                    end = Offset(24f, 24f),
                    tileMode = TileMode.Repeated,
                ),
                RoundedCornerShape(6.dp),
            )
            .border(2.dp, CasinoPalette.GoldTrim, RoundedCornerShape(6.dp))
    )
}
