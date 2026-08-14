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
import com.example.casinogames.games.baccarat.BaccaratScreen
import com.example.casinogames.games.blackjack.FreeBetScreen
import com.example.casinogames.lobby.GameId
import com.example.casinogames.lobby.LobbyScreen
import com.example.casinogames.lobby.MainMenuScreen
import com.example.casinogames.lobby.SettingsScreen
import com.example.casinogames.ui.theme.CasinoGamesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CasinoGamesTheme {
                var screen by rememberSaveable { mutableStateOf("menu") }
                when (screen) {
                    "campaign" -> {
                        BackHandler { screen = "menu" }
                        LobbyScreen(
                            campaign = true,
                            onOpenGame = { if (it.available) screen = "campaign:${it.name}" },
                            onBack = { screen = "menu" },
                        )
                    }
                    "campaign:${GameId.BACCARAT.name}" -> {
                        BackHandler { screen = "campaign" }
                        BaccaratScreen(
                            onBack = { screen = "campaign" },
                            campaign = true,
                            onGameOverExit = { screen = "menu" },
                        )
                    }
                    "campaign:${GameId.FREE_BET_BLACKJACK.name}" -> {
                        BackHandler { screen = "campaign" }
                        FreeBetScreen(
                            onBack = { screen = "campaign" },
                            campaign = true,
                            onGameOverExit = { screen = "menu" },
                        )
                    }
                    "settings" -> {
                        BackHandler { screen = "menu" }
                        SettingsScreen(onBack = { screen = "menu" })
                    }
                    "lobby" -> {
                        BackHandler { screen = "menu" }
                        LobbyScreen(
                            onOpenGame = { if (it.available) screen = it.name },
                            onBack = { screen = "menu" },
                        )
                    }
                    GameId.BACCARAT.name -> {
                        BackHandler { screen = "lobby" }
                        BaccaratScreen(onBack = { screen = "lobby" })
                    }
                    GameId.FREE_BET_BLACKJACK.name -> {
                        BackHandler { screen = "lobby" }
                        FreeBetScreen(onBack = { screen = "lobby" })
                    }
                    else -> MainMenuScreen(
                        onCampaign = { screen = "campaign" },
                        onPlayTesting = { screen = "lobby" },
                        onSettings = { screen = "settings" },
                    )
                }
            }
        }
    }
}
