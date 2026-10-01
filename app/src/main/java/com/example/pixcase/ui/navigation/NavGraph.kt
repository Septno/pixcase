package com.example.pixcase.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.pixcase.R
import com.example.pixcase.ui.common.PlaceholderScreen
import com.example.pixcase.ui.feature.timeline.TimelineRoute
import com.example.pixcase.ui.feature.viewer.ViewerRoute

/** 顶级路由。 */
private object Routes {
    const val TIMELINE = "timeline"
    const val ALBUMS = "albums"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"

    const val VIEWER_ARG_PHOTO_ID = "photoId"

    /**
     * 全屏查看器。只带 photoId,不带下标 —— 下标是「加载窗口内的相对位置」,
     * ContentObserver 触发失效刷新后窗口会变,下标随即错位。
     */
    const val VIEWER = "viewer/{$VIEWER_ARG_PHOTO_ID}"

    fun viewer(photoId: Long): String = "viewer/$photoId"
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
 * 主导航图。5 个顶级路由 + 底部 NavigationBar,外加不在底栏里的全屏查看器。
 */
@Composable
fun PixcaseNavGraph(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // 只有底栏自己的路由才显示底栏;查看器的 route pattern 不在 bottomNavItems 里,天然不匹配。
    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }
    // 查看器要铺满屏幕:不让 Scaffold 按系统栏内缩内容,改由查看器自己避开状态栏。
    val contentInsets = if (currentDestination?.route == Routes.VIEWER) {
        WindowInsets(0)
    } else {
        ScaffoldDefaults.contentWindowInsets
    }

    Scaffold(
        bottomBar = { if (showBottomBar) PixcaseBottomBar(navController) },
        contentWindowInsets = contentInsets
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TIMELINE,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.TIMELINE) {
                TimelineRoute(
                    onPhotoClick = { photoId -> navController.navigate(Routes.viewer(photoId)) }
                )
            }
            composable(Routes.ALBUMS) { AlbumsPlaceholder() }
            composable(Routes.SEARCH) { SearchPlaceholder() }
            composable(Routes.FAVORITES) { FavoritesPlaceholder() }
            composable(Routes.SETTINGS) { SettingsPlaceholder() }
            composable(
                route = Routes.VIEWER,
                arguments = listOf(
                    navArgument(Routes.VIEWER_ARG_PHOTO_ID) { type = NavType.LongType }
                )
            ) { entry ->
                // 取时间线那一份 back stack entry 拿到同一个 ViewModel 实例 ——
                // 这是两条界面复用同一份分页数据的全部机制。在这一层取好再传下去,
                // 免得查看器也要知道导航作用域。
                val timelineEntry = remember(entry) { navController.getBackStackEntry(Routes.TIMELINE) }
                ViewerRoute(
                    photoId = entry.arguments?.getLong(Routes.VIEWER_ARG_PHOTO_ID) ?: 0L,
                    timelineViewModel = hiltViewModel(timelineEntry),
                    onBack = { navController.popBackStack() }
                )
            }
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
