package com.mhss.app.data

import com.mhss.app.domain.model.Calendar
import com.mhss.app.domain.model.CalendarEvent
import com.mhss.app.domain.repository.CalendarRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/**
 * Desktop stub of [CalendarRepository]: the Android implementation reads and
 * writes the system calendar via ContentResolver, which does not exist on the
 * JVM. Calendar features are out of scope for the desktop port (phase 1), so
 * this reports an empty calendar and ignores all mutations.
 */
@Single
class CalendarRepositoryImplJvm(
    @Named("ioDispatcher") private val ioDispatcher: CoroutineDispatcher
) : CalendarRepository {

    override fun observeChanges(): Flow<Unit> = emptyFlow()

    override suspend fun getEvents(
        excludedCalendars: List<Int>,
        until: Long?,
        limit: Int?
    ): List<CalendarEvent> = withContext(ioDispatcher) { emptyList() }

    override suspend fun getEvents(
        start: Long,
        end: Long,
        excludedCalendars: List<Int>
    ): List<CalendarEvent> = withContext(ioDispatcher) { emptyList() }

    override suspend fun searchEventsByTitleWithinRange(
        start: Long,
        end: Long,
        titleQuery: String,
        excludedCalendars: List<Int>
    ): List<CalendarEvent> = withContext(ioDispatcher) { emptyList() }

    override suspend fun getCalendars(): List<Calendar> = withContext(ioDispatcher) { emptyList() }

    override suspend fun getEventById(id: Long): CalendarEvent? = withContext(ioDispatcher) { null }

    override suspend fun addEvent(event: CalendarEvent): Long? = withContext(ioDispatcher) { null }

    override suspend fun deleteEvent(event: CalendarEvent) = withContext(ioDispatcher) { }

    override suspend fun updateEvent(event: CalendarEvent) = withContext(ioDispatcher) { }

    override suspend fun createCalendar() = withContext(ioDispatcher) { }
}
