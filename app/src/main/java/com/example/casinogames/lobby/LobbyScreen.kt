package com.example.casinogames.lobby

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    FREE_BET_BLACKJACK(true),
    ULTIMATE_TEXAS_HOLDEM(false),
    ROULETTE(false),
    CRAPS(false),
}

private val LobbyBlack = Color(0xFF040308)
private val NeonPurpleDim = Color(0x998B30D9)
private val CampaignGold = Color(0xFFFFD24D)

@Composable
fun LobbyScreen(
    onOpenGame: (GameId) -> Unit,
    campaign: Boolean = false,
    onBack: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().background(LobbyBlack)) {
        Image(
            painter = painterResource(R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.35f),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 8.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .border(1.5.dp, NeonPurpleDim, CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("☰", color = Color(0xFFB98CFF), fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            Image(
                painter = painterResource(R.drawable.lobby_logo),
                contentDescription = "4 The Boys — for the boys Hotel & Casino",
                modifier = Modifier.fillMaxWidth(0.9f),
                contentScale = ContentScale.FillWidth,
            )
            if (campaign) {
                val ctx = LocalContext.current
                val prefs = remember { ctx.getSharedPreferences("campaign", Context.MODE_PRIVATE) }
                val saved = remember { prefs.getFloat("bankroll", 5000f).toDouble() }
                val goal = remember { prefs.getFloat("goal", 1_000_000f).toDouble() }
                Text(
                    "CAMPAIGN · ${formatMoney(saved)} / ${formatMoney(goal)}",
                    fontSize = 13.sp,
                    letterSpacing = 0.12.em,
                    fontWeight = FontWeight.Black,
                    color = CampaignGold,
                )
                Spacer(Modifier.height(14.dp))
            } else {
                Spacer(Modifier.height(6.dp))
            }
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GameCard(R.drawable.lobby_card_baccarat, "Baccarat") { onOpenGame(GameId.BACCARAT) }
                GameCard(R.drawable.lobby_card_blackjack, "Free Bet Blackjack") { onOpenGame(GameId.FREE_BET_BLACKJACK) }
                GameCard(R.drawable.lobby_card_holdem, "Ultimate Texas Hold'em — coming soon", null)
                GameCard(R.drawable.lobby_card_roulette, "Roulette — coming soon", null)
                GameCard(R.drawable.lobby_card_craps, "Craps — coming soon", null)
            }
        }
    }
}

@Composable
private fun GameCard(res: Int, desc: String, onClick: (() -> Unit)?) {
    Image(
        painter = painterResource(res),
        contentDescription = desc,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        contentScale = ContentScale.FillWidth,
    )
}
