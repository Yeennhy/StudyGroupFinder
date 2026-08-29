package com.studyfinder.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Common date/time formatting and manipulation (§7.2, §7.3, §7.4, §7.6).
 */
object DateTimeUtils {

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    fun formatDateTime(millis: Long): String {
        val instant = Instant.ofEpochMilli(millis)
        val zonedDateTime = instant.atZone(ZoneId.systemDefault())
        return "${zonedDateTime.format(dateFormatter)}, ${zonedDateTime.format(timeFormatter)}"
    }

    fun formatDate(millis: Long): String {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)
    }

    fun formatTime(millis: Long): String {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)
    }

    fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return when {
            hours > 0 && remainingMinutes > 0 -> "$hours hr $remainingMinutes min"
            hours > 0 -> "$hours hr"
            else -> "$remainingMinutes min"
        }
    }

    fun toLocalDate(millis: Long): LocalDate {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
}
