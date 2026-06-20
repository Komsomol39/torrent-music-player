package com.apia.musicplayer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.apia.musicplayer.ui.screens.equalizer.EqualizerScreen
import com.apia.musicplayer.ui.screens.library.LibraryScreen
import com.apia.musicplayer.ui.screens.player.PlayerScreen
import com.apia.musicplayer.ui.screens.search.SearchScreen
import com.apia.musicplayer.ui.screens.settings.SettingsScreen
import com.apia.musicplayer.ui.screens.torrent.TorrentDownloadsScreen
import com.apia.musicplayer.ui.screens.torrent.TorrentSearchScreen

sealed class Screen(val route: String) {
    object Library   : Screen("library")
    object Player    : Screen("player")
    object Search    : Screen("search")
    object Torrent   : Screen("torrent")
    object Downloads : Screen("downloads")
    object Equalizer : Screen("equalizer")
    object Settings  : Screen("settings")
}

@Composable
fun MusicNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Library.route) {
        composable(Screen.Library.route) {
            LibraryScreen(onTrackClick = { navController.navigate(Screen.Player.route) })
        }
        composable(Screen.Player.route) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onEqualizer = { navController.navigate(Screen.Equalizer.route) }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(onTrackClick = { navController.navigate(Screen.Player.route) })
        }
        // Torrent — keepState через restoreState
        composable(Screen.Torrent.route) { TorrentSearchScreen() }
        composable(Screen.Downloads.route) { TorrentDownloadsScreen() }
        composable(Screen.Equalizer.route) { EqualizerScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}
