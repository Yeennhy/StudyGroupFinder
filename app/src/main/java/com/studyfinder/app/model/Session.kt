package com.studyfinder.app.model

import java.io.Serializable

/**
 * A study session — `sessions/{sessionId}` in Firestore (§3.1).
 *
 * Used directly by the UI; there is no separate domain layer (§3.2).
 */
data class Session(
    val id: String = "",
    val communityId: String = "",
    val hostUid: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val courseCategory: CourseCategory = CourseCategory.OTHER,
    val tagType: TagType = TagType.NORMAL,
    val expectationLevel: ExpectationLevel = ExpectationLevel.PASS,
    val title: String = "",
    val description: String = "",
    val goals: String = "",
    val locationName: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val startTimeMillis: Long = 0L,
    /** Required — the overlap check in §7.2 cannot work without it. */
    val endTimeMillis: Long = 0L,
    val capacity: Int = 0,
    /** Always equals `memberUids.size` — both move in one transaction (§3.1). */
    val joinedCount: Int = 0,
    /**
     * Uids with `accepted` or `admin` status, denormalised onto the session so
     * "which sessions did I join?" is a flat `array-contains` query (§3.1).
     */
    val memberUids: List<String> = emptyList(),
    val mode: SessionMode = SessionMode.OPEN,
    val status: SessionStatus = SessionStatus.UPCOMING,
    val materialUrls: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
) : Serializable {
    val isFull: Boolean get() = joinedCount >= capacity

    /** Past-vs-upcoming is derived, never stored (§3.1). */
    fun isPast(nowMillis: Long): Boolean = endTimeMillis < nowMillis

    /** Powers the spec's block-user behaviour on Home (§7.2, §7.7). */
    fun containsBlockedUser(blocked: Set<String>): Boolean =
        memberUids.any { it in blocked }
}

/** A row of `sessions/{sessionId}/members/{uid}` (§3.1). */
data class SessionMember(
    val uid: String = "",
    val status: MemberStatus = MemberStatus.PENDING,
    val joinedAtMillis: Long = 0L,
    /** Filled in from the `users` collection for the avatar row (§7.3). */
    val profile: UserProfile? = null,
)

/**
 * One "I am busy then" interval feeding the overlap check on Home (§7.2).
 *
 * The core plan produces these only from joined sessions; device-calendar
 * import (§11.1), if ever built, appends to the same list and needs no
 * screen changes.
 */
data class BusyInterval(
    val startMillis: Long,
    val endMillis: Long,
    val label: String = "",
) {
    /** Standard half-open interval overlap test (§7.2). */
    fun overlaps(otherStart: Long, otherEnd: Long): Boolean =
        startMillis < otherEnd && otherStart < endMillis
}
