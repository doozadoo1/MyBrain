package com.mhss.app.mybrain.util

import androidx.compose.ui.Modifier

/**
 * Platform-specific test-tag/resource-id hook (Android-only property,
 * no-op actuals elsewhere).
 */
expect fun Modifier.testTagsAsResourceIdForPlatform(): Modifier
