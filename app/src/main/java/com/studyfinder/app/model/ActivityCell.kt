package com.studyfinder.app.model

import java.time.LocalDate

/**
 * A single day in the activity graph.
 */
data class ActivityCell(
    val date: LocalDate,
    val count: Int
)
