package com.example.pixcase.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.pixcase.R
import com.example.pixcase.ui.common.PlaceholderScreen

/** 顶级路由(计划 § 阶段 0 五个空骨架路由)。 */
private object Routes {
    const val TIMELINE = "timeline"
    const val ALBUMS = "albums"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
}

/** 底部导航栏条目。 */
private data class BottomNavItem(
    val route: String,
    @StringRes val labelResId: Int,
    val icon: ImageVector
)

private val bottomNavItems =
    listOf(
        BottomNavItem(Routes.TIMELINE, R.string.nav_timeline, Icons.Outlined.Image),
        BottomNavItem(Routes.ALBUMS, R.string.nav_albums, Icons.Outlined.PhotoAlbum),
        BottomNavItem(Routes.SEARCH, R.string.nav_search, Icons.Outlined.Search),
        BottomNavItem(Routes.FAVORITES, R.string.nav_favorites, Icons.Outlined.Favorite),
        BottomNavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings)
    )

/**
 * 主导航图。5 个顶级路由 + 底部 NavigationBar。
 * 阶段 0 各路由仅显示空 placeholder,后续阶段填充真实 UI。
 */
@Composable
fun PixcaseNavGraph(navController: NavHostController) {
    Scaffold(
        bottomBar = { PixcaseBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TIMELINE,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.TIMELINE) { TimelinePlaceholder() }
            composable(Routes.ALBUMS) { AlbumsPlaceholder() }
            composable(Routes.SEARCH) { SearchPlaceholder() }
            composable(Routes.FAVORITES) { FavoritesPlaceholder() }
            composable(Routes.SETTINGS) { SettingsPlaceholder() }
        }
    }
}

@Composable
private fun PixcaseBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.labelResId)) }
            )
        }
    }
}

// 各顶级路由的占位界面,后续阶段替换为真实屏幕。
@Composable
private fun TimelinePlaceholder() {
    PlaceholderScreen(Routes.TIMELINE)
}

@Composable
private fun AlbumsPlaceholder() {
    PlaceholderScreen(Routes.ALBUMS)
}

@Composable
private fun SearchPlaceholder() {
    PlaceholderScreen(Routes.SEARCH)
}

@Composable
private fun FavoritesPlaceholder() {
    PlaceholderScreen(Routes.FAVORITES)
}

@Composable
private fun SettingsPlaceholder() {
    PlaceholderScreen(Routes.SETTINGS)
}
