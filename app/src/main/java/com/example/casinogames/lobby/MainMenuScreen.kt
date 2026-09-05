package com.example.casinogames.lobby

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.casinogames.R
import com.example.casinogames.campaign.FreePlay
import com.example.casinogames.ui.common.formatMoney
import kotlin.math.roundToInt

// Dead flat black: the page art runs to black at its own edges, so any tint
// behind it shows as a band where the image stops.
private val MenuBlack = Color(0xFF000000)

@Composable
fun MainMenuScreen(
    onCampaign: () -> Unit,
    onPlayTesting: () -> Unit,
    onSettings: () -> Unit,
) {
    var buyInOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MenuBlack)) {
        Image(
            painter = painterResource(R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.55f),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.7f))
            Image(
                painter = painterResource(R.drawable.menu_logo),
                contentDescription = "4 The Boys Casino Games",
                modifier = Modifier.fillMaxWidth(0.92f),
                contentScale = ContentScale.FillWidth,
            )
            Spacer(Modifier.weight(0.6f))
            MenuButton(R.drawable.menu_btn_campaign, "Campaign", onCampaign)
            Spacer(Modifier.height(14.dp))
            MenuButton(R.drawable.menu_btn_testing, "Play testing") { buyInOpen = true }
            Spacer(Modifier.height(14.dp))
            MenuButton(R.drawable.menu_btn_settings, "Settings", onSettings)
            Spacer(Modifier.weight(1f))
        }
        if (buyInOpen) {
            BuyInDialog(
                onDismiss = { buyInOpen = false },
                onConfirm = { amount ->
                    FreePlay.buyIn = amount
                    buyInOpen = false
                    onPlayTesting()
                },
            )
        }
    }
}

/**
 * Play testing's front door: pick the buy-in before sitting down. The slider
 * walks $5K to $50K in $2,500 clicks, and every free-play table opens on the
 * chosen figure.
 */
@Composable
private fun BuyInDialog(onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    val accent = Color(0xFFB98CFF)
    var amount by remember {
        mutableFloatStateOf(FreePlay.buyIn.toFloat().coerceIn(5_000f, 50_000f))
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF60A0612))
                .border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "PLAY TESTING",
                color = accent, fontSize = 13.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.2.em,
            )
            Spacer(Modifier.height(10.dp))
            Text("BUY-IN", color = Color(0x99FFFFFF), fontSize = 10.sp, letterSpacing = 0.14.em)
            Text(
                formatMoney(amount.toDouble()),
                color = Color(0xFFE8C169), fontSize = 28.sp, fontWeight = FontWeight.Black,
            )
            Slider(
                value = amount,
                onValueChange = { amount = (it / 2_500f).roundToInt() * 2_500f },
                valueRange = 5_000f..50_000f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = accent.copy(alpha = 0.25f),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("$5K", color = Color(0x66FFFFFF), fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text("$50K", color = Color(0x66FFFFFF), fontSize = 10.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "TAKE A SEAT",
                color = Color(0xFF0A0612), fontSize = 13.sp, fontWeight = FontWeight.Black,
                letterSpacing = 0.1.em,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
                    .clickable { onConfirm(amount.toDouble()) }
                    .padding(horizontal = 26.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun MenuButton(res: Int, desc: String, onClick: () -> Unit) {
    Image(
        painter = painterResource(res),
        contentDescription = desc,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MenuBlack)) {
        Image(
            painter = painterResource(R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.35f),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp),
        ) {
            Text(
                "‹ MENU",
                color = Color(0xCCFFFFFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "SETTINGS",
                    color = Color(0xFFB98CFF),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.2.em,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Nothing to tweak yet — coming soon.",
                    color = Color(0x99FFFFFF),
                    fontSize = 13.sp,
                )
            }
        }
    }
}
