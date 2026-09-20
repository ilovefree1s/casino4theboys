package com.example.casinogames.games.craps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.sp
import com.example.casinogames.R
import com.example.casinogames.lobby.CampaignPlate

/** The two tables behind the lobby's CRAPS card. Both are built. */
enum class CrapsVariant(val available: Boolean) {
    CRAPLESS(true),
    DESTROYER(true),
}

private val PageBlack = Color(0xFF000000)
private val Jade = Color(0xFF1FA971)
private val Pink = Color(0xFFFF40A0)

/**
 * The craps room. There is no painted page for it yet, so the two cards are
 * drawn live in the manner of the painted ones: a neon plate, the name in a
 * heavy slant, two lines of pitch and a PLAY ring.
 */
@Composable
fun CrapsMenuScreen(
    onBack: () -> Unit,
    onPick: (CrapsVariant) -> Unit,
    campaign: Boolean = false,
) {
    Box(Modifier.fillMaxSize().background(PageBlack)) {
        Image(
            painter = painterResource(R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.35f),
            contentScale = ContentScale.Crop,
        )
        BoxWithConstraints(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp),
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0x99B98CFF), CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onBack,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("☰", color = Color(0xFFB98CFF), fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Image(
                    painter = painterResource(R.drawable.lobby_logo),
                    contentDescription = "4 The Boys",
                    modifier = Modifier.fillMaxWidth(0.86f),
                    contentScale = ContentScale.FillWidth,
                )
                CampaignLine(campaign)
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    GameCard(
                        name = "CRAPLESS",
                        pip = "⚄",
                        lines = listOf(
                            "SEVEN WINS · NOTHING LOSES",
                            "TEN POINTS · FREE ODDS · PROPS",
                        ),
                        color = Jade,
                        fill = Color(0xFF07231B),
                    ) { onPick(CrapsVariant.CRAPLESS) }
                    GameCard(
                        name = "DESTROYER",
                        pip = "⚅",
                        lines = listOf(
                            "BATTLESHIP DICE",
                            "CALL THE CELL · SINK THE FLEET",
                        ),
                        color = Pink,
                        fill = Color(0xFF16040C),
                    ) { onPick(CrapsVariant.DESTROYER) }
                }
                Spacer(Modifier.weight(1.4f))
            }
        }
    }
}

@Composable
private fun CampaignLine(campaign: Boolean) {
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        CampaignPlate(campaign = campaign, artWidth = 380.dp, topOffset = 0.dp)
    }
}

@Composable
private fun GameCard(
    name: String,
    pip: String,
    lines: List<String>,
    color: Color,
    fill: Color,
    onClick: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(fill)
            .border(2.dp, color, RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        val unit = maxWidth / 100f
        val density = LocalDensity.current
        fun sp(units: Float) = with(density) { (unit * units).toSp() }
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = with(density) { maxWidth.toPx() },
                )
            )
        )
        Text(
            pip,
            color = color,
            fontSize = sp(16f),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = unit * 5f).alpha(0.65f),
        )
        Column(Modifier.align(Alignment.CenterStart).padding(start = unit * 26f)) {
            Text(
                name,
                color = color,
                fontSize = sp(8.4f),
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.02.em,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(unit * 1.6f))
            lines.forEach {
                Text(
                    it,
                    color = Color(0xFFF5F1E8),
                    fontSize = sp(2.9f),
                    letterSpacing = 0.04.em,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = unit * 0.8f),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = unit * 5f)
                .size(unit * 17f)
                .border(2.dp, color, CircleShape),
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
