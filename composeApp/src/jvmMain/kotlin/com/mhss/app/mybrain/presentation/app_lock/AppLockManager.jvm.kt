package com.mhss.app.mybrain.presentation.app_lock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Desktop implementation: there is no biometric hardware, so the lock screen
 * simply auto-unlocks ("authentication succeeds" immediately).
 */
actual class AppLockManager {

    private val resultChannel = MutableSharedFlow<AuthResult>(extraBufferCapacity = 4)

    actual val resultFlow: Flow<AuthResult> = resultChannel

    actual fun showAuthPrompt() {
        resultChannel.tryEmit(AuthResult.Success)
    }

    actual fun canUseFeature(): Boolean = false
}
