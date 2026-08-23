package com.example.casinogames.lobby

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.casinogames.R
import com.example.casinogames.campaign.Campaign
import com.example.casinogames.campaign.Room
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
    var showRooms by remember { mutableStateOf(false) }
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
                    // Read live off the shared campaign — the lobby used to
                    // snapshot the purse off disk and then show a stale figure
                    // for the rest of the session.
                    Column(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = artHeight * (CampaignCentreY / PageHeight) - 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "CAMPAIGN · ${formatMoney(Campaign.bankroll)} / " +
                                formatMoney(Campaign.goal),
                            fontSize = (artWidth.value * 0.033f).sp,
                            letterSpacing = 0.1.em,
                            fontWeight = FontWeight.Black,
                            color = CampaignPink,
                        )
                        Spacer(Modifier.height(5.dp))
                        // One line only: the art leaves a single gap here
                        // before the first game card, so a marker takes the
                        // room plate's limits rather than a row of its own.
                        val owing = Campaign.debt > 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RoomPlate(
                                room = Campaign.room,
                                scale = artWidth.value,
                                showLimits = !owing,
                                onClick = { showRooms = true },
                            )
                            if (owing) MarkerPlate(scale = artWidth.value)
                        }
                    }
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
        if (showRooms) RoomPicker(onDismiss = { showRooms = false })
    }
}

private val RoomGold = Color(0xFFE8C169)
private val MarkerRed = Color(0xFFFF3B5C)

/** The room you are playing, and what its tables will take. Tap to move. */
@Composable
private fun RoomPlate(room: Room, scale: Float, showLimits: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xCC0B0713))
            .border(1.dp, RoomGold.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            room.roomName.uppercase(),
            fontSize = (scale * 0.026f).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.1.em,
            color = RoomGold,
        )
        if (showLimits) {
            Text(
                "${room.sign}  ·  ${room.sideSign}",
                fontSize = (scale * 0.023f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xB3FFFFFF),
            )
        }
        Text("▸", fontSize = (scale * 0.024f).sp, color = RoomGold)
    }
}

/** What is owed the house. Nothing moves up a room until it is squared. */
@Composable
private fun MarkerPlate(scale: Float) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xCC1A0409))
            .border(1.dp, MarkerRed.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            "MARKER ${formatMoney(Campaign.debt)}",
            fontSize = (scale * 0.025f).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.08.em,
            color = MarkerRed,
        )
        val payable = Campaign.canPayMarker
        Text(
            if (payable) "PAY IT" else "SHORT",
            fontSize = (scale * 0.024f).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.08.em,
            color = if (payable) Color(0xFF050408) else Color(0x66FFFFFF),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (payable) RoomGold else Color(0x1AFFFFFF))
                .clickable(enabled = payable) { Campaign.payMarker() }
                .padding(horizontal = 9.dp, vertical = 2.dp),
        )
    }
}

/**
 * The floor, room by room. Anything at or below where you are is always open
 * — walking down is free — and going up wants the buy-in and a clean slate
 * with the house.
 */
@Composable
private fun RoomPicker(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2050408))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 380.dp).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "THE FLOOR",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.16.em,
                color = RoomGold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (Campaign.debt > 0) "Square the marker before moving up"
                else "Higher rooms pay faster and bust quicker",
                fontSize = 11.sp,
                color = if (Campaign.debt > 0) MarkerRed else Color(0x99FFFFFF),
            )
            Spacer(Modifier.height(16.dp))
            Room.entries.forEach { room ->
                RoomRow(room, onPick = { Campaign.enter(room); onDismiss() })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RoomRow(room: Room, onPick: () -> Unit) {
    val here = Campaign.room == room
    val open = Campaign.canEnter(room)
    val shut = Campaign.blockedReason(room)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (here) Color(0x1FE8C169) else Color(0xCC0B0713))
            .border(
                if (here) 1.5.dp else 1.dp,
                when {
                    here -> RoomGold
                    open -> Color(0x59B98CFF)
                    else -> Color(0x1AFFFFFF)
                },
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = open && !here, onClick = onPick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                room.roomName.uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.1.em,
                color = if (open) RoomGold else Color(0x59FFFFFF),
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    here -> "YOU ARE HERE"
                    shut != null -> shut
                    else -> "WALK IN"
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.08.em,
                color = when {
                    here -> Color(0xCCFFFFFF)
                    shut != null -> Color(0x73FF3B5C)
                    else -> Color(0x99FFFFFF)
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "${room.sign}  ·  ${room.sideSign}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (open) Color(0xB3FFFFFF) else Color(0x40FFFFFF),
        )
    }
}
