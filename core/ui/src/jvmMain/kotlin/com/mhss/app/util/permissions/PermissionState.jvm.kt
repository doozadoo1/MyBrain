package com.mhss.app.util.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Desktop has no runtime permission model: everything is treated as granted
 * (calendar/notification features are gated by backend availability anyway).
 */
@Stable
private class DesktopPermissionState(permission: Permission) : PermissionState {

    override var shouldShowRationale by mutableStateOf(false)

    override var isGranted by mutableStateOf(true)

    override fun launchRequest() {
        // No-op: nothing to request on desktop.
    }

    override fun refresh() {
        isGranted = true
    }

    override fun openAppSettings() {
        // No-op.
    }
}

@Composable
actual fun rememberPermissionState(
    permission: Permission
): PermissionState = remember(permission) { DesktopPermissionState(permission) }
