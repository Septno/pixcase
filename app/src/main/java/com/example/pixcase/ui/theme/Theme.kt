package com.example.pixcase.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = PixcasePrimary,
        onPrimary = PixcaseOnPrimary,
        primaryContainer = PixcasePrimaryContainer,
        onPrimaryContainer = PixcaseOnPrimaryContainer,
        secondary = PixcaseSecondary,
        onSecondary = PixcaseOnSecondary,
        secondaryContainer = PixcaseSecondaryContainer,
        onSecondaryContainer = PixcaseOnSecondaryContainer,
        tertiary = PixcaseTertiary,
        onTertiary = PixcaseOnTertiary,
        tertiaryContainer = PixcaseTertiaryContainer,
        onTertiaryContainer = PixcaseOnTertiaryContainer,
        error = PixcaseError,
        onError = PixcaseOnError,
        errorContainer = PixcaseErrorContainer,
        onErrorContainer = PixcaseOnErrorContainer,
        background = PixcaseBackground,
        onBackground = PixcaseOnBackground,
        surface = PixcaseSurface,
        onSurface = PixcaseOnSurface,
        surfaceVariant = PixcaseSurfaceVariant,
        onSurfaceVariant = PixcaseOnSurfaceVariant,
        outline = PixcaseOutline
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = PixcaseDarkPrimary,
        onPrimary = PixcaseDarkOnPrimary,
        background = PixcaseDarkBackground,
        onBackground = PixcaseDarkOnBackground,
        surface = PixcaseDarkSurface,
        onSurface = PixcaseDarkOnSurface
    )

/**
 * 全局 Material 3 主题。
 *
 * @param darkTheme 是否深色模式,默认跟系统
 * @param dynamicColor 是否使用 Android 12+ 的 Dynamic Color(壁纸取色);关闭后用品牌色
 */
@Composable
fun PixcaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PixcaseTypography,
        content = content
    )
}
