package com.mhss.app.preferences.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import java.io.File

@Module
actual class PreferencesPlatformModule {

    @Single
    fun dataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
        produceFile = { preferencesFile().path.toPath() }
    )
}

private fun preferencesFile(): File {
    val appData = System.getenv("APPDATA")
    val base = if (!appData.isNullOrBlank() && File(appData).isDirectory) {
        File(appData, "MyBrain")
    } else {
        File(System.getProperty("user.home"), ".mybrain")
    }
    return File(base.apply { mkdirs() }, "settings.preferences_pb")
}
