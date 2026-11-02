package com.mhss.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Desktop has no Material-You dynamic color source; the theme falls back to
 * the default (brand) Material3 schemes.
 */
@Composable
internal actual fun resolvePlatformColorScheme(
    darkTheme: Boolean,
    useDynamicColors: Boolean
): ColorScheme? = null
