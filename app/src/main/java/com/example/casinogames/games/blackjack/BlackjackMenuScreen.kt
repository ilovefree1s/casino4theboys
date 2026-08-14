package com.example.casinogames.games.blackjack

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.casinogames.R

private val MenuBlack = Color(0xFF040308)
private val NeonPurpleDim = Color(0x998B30D9)

enum class BlackjackVariant { FREE_BET, BLAZING_777 }

@Composable
fun BlackjackMenuScreen(
    onBack: () -> Unit,
    onPick: (BlackjackVariant) -> Unit,
) {
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
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 8.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .border(1.5.dp, NeonPurpleDim, CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = Color(0xFFB98CFF), fontSize = 20.sp)
                }
                Spacer(Modifier.weight(1f))
            }
            Image(
                painter = painterResource(R.drawable.bj_menu_header),
                contentDescription = "Blackjack variants",
                modifier = Modifier.fillMaxWidth(0.78f),
                contentScale = ContentScale.FillWidth,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VariantCard(R.drawable.bj_card_freebet, "Free Bet Blackjack") {
                    onPick(BlackjackVariant.FREE_BET)
                }
                VariantCard(R.drawable.bj_card_blazing, "Blazing 777s with TriLux") {
                    onPick(BlackjackVariant.BLAZING_777)
                }
            }
        }
    }
}

@Composable
private fun VariantCard(res: Int, desc: String, onClick: () -> Unit) {
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
