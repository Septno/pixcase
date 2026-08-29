package com.example.pixcase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.example.pixcase.ui.navigation.PixcaseNavGraph
import com.example.pixcase.ui.theme.PixcaseTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 唯一 Activity。Compose 入口,挂载主题 + Navigation。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixcaseRoot()
        }
    }
}

@Composable
private fun PixcaseRoot() {
    PixcaseTheme {
        val navController = rememberNavController()
        PixcaseNavGraph(navController = navController)
    }
}
