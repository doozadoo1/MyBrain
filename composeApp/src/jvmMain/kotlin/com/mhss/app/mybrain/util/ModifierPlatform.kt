package com.mhss.app.mybrain.util

import androidx.compose.ui.Modifier

/**
 * testTagsAsResourceId is an Android-only semantics property (Espresso /
 * uiautomator resource ids); no equivalent on desktop.
 */
actual fun Modifier.testTagsAsResourceIdForPlatform(): Modifier = this
