package com.mhss.app.mybrain

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mhss.app.mybrain.presentation.app_lock.AppLockManager
import com.mhss.app.ui.ThemeSettings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import org.koin.plugin.module.dsl.startKoin

/**
 * Desktop (Windows/Linux/macOS) entry point. Mirrors MyBrainApplication +
 * MainActivity: start Koin, resolve MainViewModel, show the window.
 */
fun main() {
    startKoin<DesktopKoinApp> {
        allowOverride(true)
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "My Brain",
            state = rememberWindowState(width = 1100.dp, height = 800.dp)
        ) {
            val viewModel: com.mhss.app.mybrain.presentation.main.MainViewModel = koinViewModel()
            val appLockManager = AppLockManager()
            val dateTimeFormatter = koinInject<com.mhss.app.datetime.DateTimeFormatter>()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle(ThemeSettings.LIGHT.value)
            CompositionLocalProvider(
                com.mhss.app.datetime.LocalDateTimeFormatter provides dateTimeFormatter
            ) {
                MyBrainApp(
                    viewModel = viewModel,
                    isDarkMode = themeMode == ThemeSettings.DARK.value,
                    appLockManager = appLockManager
                )
            }
        }
    }
}
