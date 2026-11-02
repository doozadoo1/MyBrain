package com.mhss.app.mybrain

import com.mhss.app.alarm.di.AlarmModule
import com.mhss.app.data.NoteDataModule
import com.mhss.app.data.di.AiDataModule
import com.mhss.app.data.di.BookmarksDataModule
import com.mhss.app.data.di.CalendarDataModule
import com.mhss.app.data.di.DiaryDataModule
import com.mhss.app.data.di.SettingsDataModule
import com.mhss.app.data.di.TasksDataModule
import com.mhss.app.database.di.DesktopDatabaseModule
import com.mhss.app.datetime.DateTimeModule
import com.mhss.app.di.CoroutinesModule
import com.mhss.app.domain.di.AiDomainModule
import com.mhss.app.domain.di.BookmarksDomainModule
import com.mhss.app.domain.di.CalendarDomainModule
import com.mhss.app.domain.di.DiaryDomainModule
import com.mhss.app.domain.di.NoteDomainModule
import com.mhss.app.domain.di.SettingsDomainModule
import com.mhss.app.domain.di.TasksDomainModule
import com.mhss.app.mybrain.di.DesktopWidgetModule
import com.mhss.app.mybrain.di.MainPresentationModule
import com.mhss.app.mybrain.sync.di.LocalSyncModule
import com.mhss.app.preferences.di.PreferencesModule
import com.mhss.app.preferences.di.PreferencesPlatformModule
import com.mhss.app.presentation.di.AiPresentationModule
import com.mhss.app.presentation.di.BookmarksPresentationModule
import com.mhss.app.presentation.di.CalendarPresentationModule
import com.mhss.app.presentation.di.DiaryPresentationModule
import com.mhss.app.presentation.di.NotePresentationModule
import com.mhss.app.presentation.di.SettingsPresentationModule
import com.mhss.app.presentation.di.TasksPresentationModule
import com.mhss.app.storage.di.StorageModule
import org.koin.core.annotation.KoinApplication

/**
 * Desktop Koin graph. Same wiring as MyBrainKoinApp on Android minus the
 * Android-only modules (widget, app functions).
 */
@KoinApplication(
    modules = [
        DateTimeModule::class,
        MainPresentationModule::class,
        AlarmModule::class,
        PreferencesModule::class,
        PreferencesPlatformModule::class,
        StorageModule::class,
        NotePresentationModule::class,
        NoteDataModule::class,
        NoteDomainModule::class,
        DiaryPresentationModule::class,
        DiaryDataModule::class,
        DiaryDomainModule::class,
        TasksPresentationModule::class,
        TasksDataModule::class,
        TasksDomainModule::class,
        SettingsPresentationModule::class,
        SettingsDataModule::class,
        SettingsDomainModule::class,
        CalendarPresentationModule::class,
        CalendarDataModule::class,
        CalendarDomainModule::class,
        BookmarksPresentationModule::class,
        BookmarksDataModule::class,
        BookmarksDomainModule::class,
        AiDataModule::class,
        AiDomainModule::class,
        AiPresentationModule::class,
        CoroutinesModule::class,
        DesktopDatabaseModule::class,
        DesktopWidgetModule::class,
        LocalSyncModule::class
    ]
)
class DesktopKoinApp
