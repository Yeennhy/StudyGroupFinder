package com.studyfinder.app.model

import java.io.Serializable

/**
 * A study session — `sessions/{sessionId}` in Firestore.

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
    val endTimeMillis: Long = 0L,
    val capacity: Int = 0,
    val joinedCount: Int = 0,
    val memberUids: List<String> = emptyList(),
    val mode: SessionMode = SessionMode.OPEN,
    val status: SessionStatus = SessionStatus.UPCOMING,
    val materialUrls: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
) : Serializable {
    val isFull: Boolean get() = joinedCount >= capacity

    fun isPast(nowMillis: Long): Boolean = endTimeMillis < nowMillis

    fun containsBlockedUser(blocked: Set<String>): Boolean =
        memberUids.any { it in blocked }
}

/** A row of `sessions/{sessionId}/members/{uid}`. */
data class SessionMember(
    val uid: String = "",
    val status: MemberStatus = MemberStatus.PENDING,
    val joinedAtMillis: Long = 0L,
    /** Filled in from the `users` collection for the avatar row. */
    val profile: UserProfile? = null,
)


data class BusyInterval(
    val startMillis: Long,
    val endMillis: Long,
    val label: String = "",
) {
    /** Standard half-open interval overlap test. */
    fun overlaps(otherStart: Long, otherEnd: Long): Boolean =
        startMillis < otherEnd && otherStart < endMillis
}
