package com.apia.musicplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apia.musicplayer.ui.components.MiniPlayer
import com.apia.musicplayer.ui.navigation.MusicNavGraph
import com.apia.musicplayer.ui.navigation.Screen

@Composable
fun MusicPlayerApp() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                if (currentRoute != Screen.Player.route) {
                    MiniPlayer(onClick = { navController.navigate(Screen.Player.route) })
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Screen.Library.route,
                        onClick = { go(Screen.Library.route) },
                        icon = { Icon(Icons.Default.LibraryMusic, null) },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Search.route,
                        onClick = { go(Screen.Search.route) },
                        icon = { Icon(Icons.Default.Search, null) },
                        label = { Text("Search") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Torrent.route,
                        onClick = { go(Screen.Torrent.route) },
                        icon = { Icon(Icons.Default.TravelExplore, null) },
                        label = { Text("Find") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Downloads.route,
                        onClick = { go(Screen.Downloads.route) },
                        icon = { Icon(Icons.Default.Download, null) },
                        label = { Text("Downloads") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Settings.route,
                        onClick = { go(Screen.Settings.route) },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Settings") }
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
