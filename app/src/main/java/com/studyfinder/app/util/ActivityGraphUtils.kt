package com.studyfinder.app.util

import com.studyfinder.app.model.ActivityCell
import java.time.LocalDate

object ActivityGraphUtils {

    /**
     * Building the grid (columns = weeks, rows = 7 days)
     * Walks from the Sunday on/before the 90-day window start.
     */
    fun buildWeeks(counts: Map<LocalDate, Int>, days: Int = 90): List<List<ActivityCell>> {
        val today = LocalDate.now()
        val start = today.minusDays(days.toLong())
            .let { it.minusDays(it.dayOfWeek.value.toLong() % 7) } // back to Sunday
            
        return generateSequence(start) { it.plusWeeks(1) }
            .takeWhile { it <= today }
            .map { weekStart ->
                (0..6).map { offset ->
                    val d = weekStart.plusDays(offset.toLong())
                    ActivityCell(d, counts[d] ?: 0)
                }
            }
            .toList()
    }
}
