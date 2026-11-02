package com.mhss.app.widget

import org.koin.core.annotation.Single

/**
 * Desktop: no home-screen widgets. Widget update requests are no-ops.
 */
@Single(binds = [WidgetUpdater::class])
class WidgetUpdaterImplJvm : WidgetUpdater {

    override suspend fun updateAll(type: WidgetUpdater.WidgetType) {
        // Not applicable on desktop.
    }
}
