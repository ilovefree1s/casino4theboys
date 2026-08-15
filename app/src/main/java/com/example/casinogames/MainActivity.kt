package com.example.casinogames

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.casinogames.games.baccarat.BaccaratScreen
import com.example.casinogames.games.blackjack.Blazing777Screen
import com.example.casinogames.games.blackjack.BlackjackMenuScreen
import com.example.casinogames.games.blackjack.BlackjackVariant
import com.example.casinogames.games.blackjack.FreeBetScreen
import com.example.casinogames.games.holdem.UltimateHoldemScreen
import com.example.casinogames.lobby.GameId
import com.example.casinogames.lobby.LobbyScreen
import com.example.casinogames.lobby.MainMenuScreen
import com.example.casinogames.lobby.SettingsScreen
import com.example.casinogames.ui.theme.CasinoGamesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Hide the navigation bar; a swipe from the edge brings it back briefly.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        setContent {
            CasinoGamesTheme {
                // Screen keys are "<mode>:<place>", mode being c(ampaign) or t(esting).
                var screen by rememberSaveable { mutableStateOf("menu") }
                val mode = screen.substringBefore(':', "")
                val place = screen.substringAfter(':', screen)
                val campaign = mode == "c"
                val lobby = "$mode:lobby"

                when {
                    place == "settings" -> {
                        BackHandler { screen = "menu" }
                        SettingsScreen(onBack = { screen = "menu" })
                    }
                    place == "lobby" -> {
                        BackHandler { screen = "menu" }
                        LobbyScreen(
                            campaign = campaign,
                            onOpenGame = { if (it.available) screen = "$mode:${it.name}" },
                            onBack = { screen = "menu" },
                        )
                    }
                    place == GameId.BACCARAT.name -> {
                        BackHandler { screen = lobby }
                        BaccaratScreen(
                            onBack = { screen = lobby },
                            campaign = campaign,
                            onGameOverExit = { screen = "menu" },
                        )
                    }
                    place == GameId.ULTIMATE_TEXAS_HOLDEM.name -> {
                        BackHandler { screen = lobby }
                        UltimateHoldemScreen(
                            onBack = { screen = lobby },
                            campaign = campaign,
                            onGameOverExit = { screen = "menu" },
                        )
                    }
                    place == GameId.BLACKJACK.name -> {
                        BackHandler { screen = lobby }
                        BlackjackMenuScreen(
                            onBack = { screen = lobby },
                            onPick = { screen = "$mode:${it.name}" },
                        )
                    }
                    place == BlackjackVariant.FREE_BET.name -> {
                        val back = "$mode:${GameId.BLACKJACK.name}"
                        BackHandler { screen = back }
                        FreeBetScreen(
                            onBack = { screen = back },
                            campaign = campaign,
                            onGameOverExit = { screen = "menu" },
                        )
                    }
                    place == BlackjackVariant.BLAZING_777.name -> {
                        val back = "$mode:${GameId.BLACKJACK.name}"
                        BackHandler { screen = back }
                        Blazing777Screen(
                            onBack = { screen = back },
                            campaign = campaign,
                            onGameOverExit = { screen = "menu" },
                        )
                    }
                    else -> MainMenuScreen(
                        onCampaign = { screen = "c:lobby" },
                        onPlayTesting = { screen = "t:lobby" },
                        onSettings = { screen = "t:settings" },
                    )
                }
            }
        }
    }
}
