package com.apia.musicplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apia.musicplayer.ui.navigation.MusicNavGraph
import com.apia.musicplayer.ui.navigation.Screen
import com.apia.musicplayer.ui.components.MiniPlayer

@Composable
fun MusicPlayerApp() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            Column {
                // Mini player above nav bar (show when not on full player screen)
                if (currentRoute != Screen.Player.route) {
                    MiniPlayer(onClick = { navController.navigate(Screen.Player.route) })
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Library.route,
                        onClick = { navController.navigate(Screen.Library.route) },
                        icon = { Icon(Icons.Default.LibraryMusic, null) },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Search.route,
                        onClick = { navController.navigate(Screen.Search.route) },
                        icon = { Icon(Icons.Default.Search, null) },
                        label = { Text("Search") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Torrent.route,
                        onClick = { navController.navigate(Screen.Torrent.route) },
                        icon = { Icon(Icons.Default.Download, null) },
                        label = { Text("Torrent") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            MusicNavGraph(navController = navController)
        }
    }
}