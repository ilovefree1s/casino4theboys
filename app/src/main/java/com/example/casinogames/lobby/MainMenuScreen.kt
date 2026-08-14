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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.casinogames.R

private val MenuBlack = Color(0xFF040308)

@Composable
fun MainMenuScreen(
    onCampaign: () -> Unit,
    onPlayTesting: () -> Unit,
    onSettings: () -> Unit,
) {
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
            MenuButton(R.drawable.menu_btn_testing, "Play testing", onPlayTesting)
            Spacer(Modifier.height(14.dp))
            MenuButton(R.drawable.menu_btn_settings, "Settings", onSettings)
            Spacer(Modifier.weight(1f))
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
