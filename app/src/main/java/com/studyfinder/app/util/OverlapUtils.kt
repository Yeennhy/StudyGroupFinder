package com.studyfinder.app.util

import com.studyfinder.app.model.BusyInterval

/**
 * Logic to detect sessions that overlap with user availability.
 */
object OverlapUtils {

    /**
     * Checks if a candidate session overlaps with any known busy intervals.
     *
     * Half-open interval test: a.startTime < b.endTime && b.startTime < a.endTime.
     */
    fun findOverlap(
        candidateStart: Long,
        candidateEnd: Long,
        busyIntervals: List<BusyInterval>,
    ): BusyInterval? {
        return busyIntervals.find { it.overlaps(candidateStart, candidateEnd) }
    }

    fun hasOverlap(
        candidateStart: Long,
        candidateEnd: Long,
        busyIntervals: List<BusyInterval>,
    ): Boolean {
        return findOverlap(candidateStart, candidateEnd, busyIntervals) != null
    }
}
