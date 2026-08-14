package com.example.casinogames.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Shared "for the boys" felt palette, used across game tables.
object CasinoPalette {
    val Felt = Color(0xFF5C1A1B)
    val FeltDeep = Color(0xFF471314)
    val Ink = Color(0xFF0E0A0B)
    val OffWhite = Color(0xFFF5F1E8)
    val BankerRed = Color(0xFFB5301F)
    val PlayerBlue = Color(0xFF2F6FBF)
    val TieGreen = Color(0xFF2F8F4E)
    val GoldText = Color(0xFFFFF049)
    val GoldTrim = Color(0xFFD9B23A)
    val WinGlow = Color(0xFFFFE45C)
}

@Composable
fun CasinoGamesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CasinoPalette.GoldTrim,
            background = CasinoPalette.FeltDeep,
            surface = CasinoPalette.Felt,
            onBackground = CasinoPalette.OffWhite,
            onSurface = CasinoPalette.OffWhite,
        ),
        content = content,
    )
}
