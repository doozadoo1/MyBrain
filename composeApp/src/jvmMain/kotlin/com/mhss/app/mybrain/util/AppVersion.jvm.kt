package com.mhss.app.mybrain.util

import androidx.compose.runtime.Composable

private const val DESKTOP_VERSION = "1.0"

@Composable
actual fun getAppVersion(): String = DESKTOP_VERSION

actual fun supportsMaterialYouTheme(): Boolean = false
