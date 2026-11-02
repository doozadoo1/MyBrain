package com.mhss.app.mybrain.data.repository

import com.mhss.app.alarm.model.Alarm
import com.mhss.app.alarm.repository.AlarmScheduler
import org.koin.core.annotation.Factory

/**
 * Desktop: exact alarms rely on Android's AlarmManager + notification stack,
 * which is out of scope for Phase 1. Scheduling requests are no-ops and the
 * capability is reported as unavailable.
 */
@Factory
class AlarmSchedulerImpl : AlarmScheduler {

    override fun scheduleAlarm(alarm: Alarm) {
        // Not supported on desktop in Phase 1.
    }

    override fun cancelAlarm(schedulerId: Int) {
        // Not supported on desktop in Phase 1.
    }

    override fun canScheduleExactAlarms(): Boolean = false
}
