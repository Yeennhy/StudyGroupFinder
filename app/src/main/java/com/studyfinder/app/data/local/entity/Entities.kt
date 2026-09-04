package com.studyfinder.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room mirrors only what a screen needs to paint while offline.
 *
 * Firestore is the sole source of truth; nothing in the UI or a ViewModel
 * ever writes here — only the repository does, immediately after a successful
 * fetch.
 */

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val communityId: String,
    val hostUid: String,
    val courseId: String,
    val courseName: String,
    val courseCategory: String,
    val tagType: String,
    val expectationLevel: String,
    val title: String,
    val description: String,
    val goals: String,
    val locationName: String,
    val lat: Double?,
    val lng: Double?,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val capacity: Int,
    val joinedCount: Int,
    val memberUids: List<String>,
    val mode: String,
    val status: String,
    val materialUrls: List<String>,
    val cachedAtMillis: Long,
)

@Entity(tableName = "communities")
data class CommunityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val city: String,
    val imageUrl: String,
    val verified: Boolean,
    val domainWhitelist: List<String>,
)

/**
 * A lightweight cache of the sessions the current user has joined — the
 * `whereArrayContains("memberUids", uid)` result. Kept separate from
 * [SessionEntity] so the Home cache and the My Sessions cache can be
 * refreshed independently.
 */
@Entity(tableName = "my_sessions")
data class MySessionEntity(
    @PrimaryKey val sessionId: String,
    val title: String,
    val courseName: String,
    val locationName: String,
    val tagType: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val status: String,
    val cachedAtMillis: Long,
)

/** Single-row table holding the signed-in user's profile. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val studentId: String,
    val communityId: String,
    val department: String,
    val major: String,
    val admissionYear: String,
    val bio: String,
    val photoUrl: String,
)
