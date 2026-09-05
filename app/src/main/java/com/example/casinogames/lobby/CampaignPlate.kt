package com.example.casinogames.lobby

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.casinogames.R
import com.example.casinogames.campaign.Campaign
import com.example.casinogames.campaign.Room
import com.example.casinogames.ui.common.formatMoney

private val CampaignPink = Color(0xFFE24BC8)

/**
 * The purse, the room and any marker outstanding, on the mark a page leaves
 * for them. Both the lobby and the poker page wear it, so it lives here
 * rather than in either of them.
 *
 * Free play has no campaign behind it and would leave the gap empty, so it
 * says what the mode is instead.
 */
@Composable
fun BoxScope.CampaignPlate(campaign: Boolean, artWidth: Dp, topOffset: Dp) {
    var showRooms by remember { mutableStateOf(false) }
    if (!campaign) {
        Text(
            "PLAY TESTING  ·  NO LIMITS  ·  NOTHING SAVED",
            fontSize = (artWidth.value * 0.026f).sp,
            letterSpacing = 0.12.em,
            fontWeight = FontWeight.Black,
            color = Color(0x73B98CFF),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = topOffset),
        )
        return
    }
    Column(
        Modifier.align(Alignment.TopCenter).offset(y = topOffset - 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "CAMPAIGN · ${formatMoney(Campaign.bankroll)} / ${formatMoney(Campaign.goal)}",
            fontSize = (artWidth.value * 0.033f).sp,
            letterSpacing = 0.1.em,
            fontWeight = FontWeight.Black,
            color = CampaignPink,
        )
        Spacer(Modifier.height(5.dp))
        // One line only: the art leaves a single gap here before the first
        // card, so a marker takes the room plate's limits rather than a row
        // of its own.
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
    if (showRooms) RoomPicker(onDismiss = { showRooms = false })
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

/*
 * The floor art, measured: five cards on the 941x1672 sheet, every one of
 * them painted WALK IN — the live layer decides which of them mean it.
 */
private const val FLOOR_ART_W = 941f
private const val FLOOR_ART_H = 1672f
private val FLOOR_BANDS = arrayOf(
    384f to 561f, 585f to 760f, 784f to 965f, 989f to 1168f, 1192f to 1373f,
)

/**
 * The floor, room by room, on the painted sheet. Anything at or below where
 * you are is always open — walking down is free — and going up wants the
 * buy-in and a clean slate with the house. A shut room dims and wears the
 * reason over its painted WALK IN; the room you are in wears a gold ring.
 */
@Composable
private fun RoomPicker(onDismiss: () -> Unit) {
    // Its own window, not a Box in the page: the lobby composes its card tap
    // bands after the plate, so an in-page scrim sat underneath them and taps
    // on a room fell through to whatever game lay behind it.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF040209))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            val artW = minOf(maxWidth, maxHeight * (FLOOR_ART_W / FLOOR_ART_H))
            val artH = artW * (FLOOR_ART_H / FLOOR_ART_W)
            val k = artW.value / FLOOR_ART_W
            Box(Modifier.align(Alignment.Center).width(artW).height(artH)) {
                Image(
                    painter = painterResource(R.drawable.floorrooms),
                    contentDescription = "The floor",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                Room.entries.forEachIndexed { i, room ->
                    val (y0, y1) = FLOOR_BANDS[i]
                    val here = Campaign.room == room
                    val open = Campaign.canEnter(room)
                    val shut = Campaign.blockedReason(room)
                    Box(
                        Modifier
                            .offset(x = (44 * k).dp, y = (y0 * k).dp)
                            .width(((897 - 44) * k).dp)
                            .height(((y1 - y0) * k).dp)
                            .clip(RoundedCornerShape((26 * k).dp))
                            .then(
                                if (here) {
                                    Modifier.border(2.dp, RoomGold, RoundedCornerShape((26 * k).dp))
                                } else Modifier
                            )
                            .clickable(enabled = open && !here) {
                                Campaign.enter(room)
                                onDismiss()
                            },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        if (!open) Box(Modifier.fillMaxSize().background(Color(0xA6050308)))
                        when {
                            here -> FloorTag("YOU ARE HERE", RoomGold, k)
                            !open -> FloorTag(shut?.uppercase() ?: "LOCKED", MarkerRed, k)
                        }
                    }
                }
            }
        }
    }
}

/** A small plate laid over the card's painted WALK IN, saying otherwise. */
@Composable
private fun FloorTag(text: String, color: Color, k: Float) {
    Text(
        text,
        fontSize = (25 * k).sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.06.em,
        color = color,
        modifier = Modifier
            .padding(end = (38 * k).dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xE60B0713))
            .border(1.dp, color.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
            .padding(horizontal = (20 * k).dp, vertical = (10 * k).dp),
    )
}
