package com.mhss.app.mybrain.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * Desktop widget substitute: binds the jvmMain no-op [WidgetUpdater]
 * implementation that lives in com.mhss.app.widget (the Android WidgetModule
 * is app-only and excluded on desktop).
 */
@Module
@ComponentScan("com.mhss.app.widget")
class DesktopWidgetModule
