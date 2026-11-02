package com.mhss.app.datetime

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import org.koin.core.annotation.Single
import java.time.format.TextStyle
import java.util.Locale
import java.time.format.DateTimeFormatter as JavaDateTimeFormatter

@Single(binds = [DateTimeFormatter::class])
class DesktopDateTimeFormatter : DateTimeFormatter {

    override val is24HourFormat: Boolean = false

    private val calendarMappingFormatter = JavaDateTimeFormatter.ofPattern("EEEE d, MMM yyy", Locale.getDefault())
    private val calendarEventsDayFormatter: JavaDateTimeFormatter = JavaDateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

    override fun formatEventsDayName(date: LocalDate): String {
        return calendarEventsDayFormatter.format(date.toJavaLocalDate())
    }

    override fun formatDateDependingOnDay(timestamp: Long): String {
        val localDT = timestamp.localDateTime
        val hourPatternString = if (is24HourFormat) "H:mm" else "h:mm a"
        val datePattern = when {
            localDT.isToday() -> hourPatternString
            localDT.isCurrentYear() -> "MMM dd, $hourPatternString"
            else -> "MMM dd, yyyy $hourPatternString"
        }
        return JavaDateTimeFormatter.ofPattern(datePattern, Locale.getDefault()).format(localDT.toJavaLocalDateTime())
    }

    override fun fullDate(timestamp: Long): String {
        val localDT = timestamp.localDateTime
        val hourPattern = if (is24HourFormat) "H:mm" else "h:mm a"
        return JavaDateTimeFormatter.ofPattern("MMM dd, yyyy $hourPattern", Locale.getDefault()).format(localDT.toJavaLocalDateTime())
    }

    override fun formatDateForMapping(timestamp: Long): String {
        return calendarMappingFormatter.format(timestamp.localDateTime.toJavaLocalDateTime())
    }

    override fun formatDateForMapping(date: LocalDate): String {
        return calendarMappingFormatter.format(date.toJavaLocalDate())
    }

    override fun formatTime(timestamp: Long): String {
        val minutes = timestamp % HOUR_MILLIS
        val pattern = if (is24HourFormat) "H:mm" else if (minutes == 0L) "h a" else "h:mm a"
        return JavaDateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(timestamp.localDateTime.toJavaLocalDateTime())
    }

    override fun formatDate(timestamp: Long, forceShowYear: Boolean): String {
        val localDT = timestamp.localDateTime
        val pattern = if (localDT.isCurrentYear() && !forceShowYear) "EEE, MMM dd" else "EEE, MMM dd, yyyy"
        return JavaDateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(localDT.toJavaLocalDateTime())
    }

    override fun monthName(timestamp: Long): String {
        val localDT = timestamp.localDateTime
        val pattern = if (localDT.isCurrentYear()) "MMMM" else "MMMM yyyy"
        return JavaDateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(localDT.toJavaLocalDateTime())
    }

    override fun getDisplayName(dayOfWeek: DayOfWeek): String {
        return dayOfWeek.toJavaDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    override fun monthName(date: LocalDate): String {
        val javaDateTime = date.toJavaLocalDate()
        val pattern = if (javaDateTime.isCurrentYear()) "MMMM" else "MMMM yyyy"
        return JavaDateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(javaDateTime)
    }

    override fun formatEventStartEnd(
        start: Long,
        end: Long,
        allDayString: String,
        eventTimeAt: String,
        eventTime: String,
        location: String?,
        allDay: Boolean
    ): String {
        return if (allDay) {
            allDayString
        } else {
            if (!location.isNullOrBlank()) {
                String.format(
                    Locale.getDefault(),
                    eventTimeAt,
                    formatTime(start),
                    formatTime(end),
                    location
                )
            } else {
                String.format(
                    Locale.getDefault(),
                    eventTime,
                    formatTime(start),
                    formatTime(end)
                )
            }
        }
    }
}

fun java.time.LocalDate.isCurrentYear(): Boolean {
    return year == now().localDateTime.year
}
