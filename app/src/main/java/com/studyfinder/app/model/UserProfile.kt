package com.studyfinder.app.model

/**
 * `users/{uid}` — document ID is the Firebase Auth UID.
 */
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val studentId: String = "",
    val communityId: String = "",
    val department: String = "",
    val major: String = "",
    val admissionYear: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val createdAtMillis: Long = 0L,
) {
    val hasCommunity: Boolean get() = communityId.isNotBlank()
}
