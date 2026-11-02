package com.mhss.app.mybrain.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Android: enables test tag -> resource id mapping for UI-automation.
 */
actual fun Modifier.testTagsAsResourceIdForPlatform(): Modifier =
    semantics { testTagsAsResourceId = true }
