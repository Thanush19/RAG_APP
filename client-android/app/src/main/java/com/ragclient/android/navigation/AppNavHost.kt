package com.ragclient.android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ragclient.android.feature.ask.AskScreen
import com.ragclient.android.feature.auth.AuthScreen
import com.ragclient.android.feature.library.LibraryScreen
import com.ragclient.android.feature.search.SearchScreen
import com.ragclient.android.feature.viewer.ViewerScreen

object Routes {
    const val AUTH = "auth"
    const val LIBRARY = "library"
    const val ASK = "ask"
    const val SEARCH = "search"
    const val VIEWER = "viewer/{documentId}/{page}"

    fun viewer(documentId: String, page: Int = 1) = "viewer/$documentId/$page"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.LIBRARY, "Library", Icons.Filled.Home),
    BottomDestination(Routes.ASK, "Ask", Icons.Filled.Info),
    BottomDestination(Routes.SEARCH, "Search", Icons.Filled.Search)
)

/**
 * Phone-first shell (client plan D3). The NavigationRail/list-detail
 * variants for medium+ window sizes arrive in M2 via
 * calculateWindowSizeClass, per the adaptive skill.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (bottomDestinations.any { it.route == currentRoute }) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY, // M1 gates this on session
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.AUTH) { AuthScreen() }
            composable(Routes.LIBRARY) { LibraryScreen() }
            composable(Routes.ASK) { AskScreen() }
            composable(Routes.SEARCH) { SearchScreen() }
            composable(
                route = Routes.VIEWER,
                arguments = listOf(
                    navArgument("documentId") { type = NavType.StringType },
                    navArgument("page") { type = NavType.IntType; defaultValue = 1 }
                )
            ) { entry ->
                ViewerScreen(
                    documentId = entry.arguments?.getString("documentId"),
                    page = entry.arguments?.getInt("page")
                )
            }
        }
    }
}
