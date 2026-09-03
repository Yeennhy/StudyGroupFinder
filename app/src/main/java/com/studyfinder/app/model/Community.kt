package com.studyfinder.app.model

/**
 * `communities/{communityId}` (§3.1).
 *
 * The only publicly-readable collection — it is what the pre-login REST
 * screen in §7.1 fetches over Retrofit.
 */
data class Community(
    val id: String = "",
    val name: String = "",
    val city: String = "",
    val imageUrl: String = "",
    /** true = joining requires an email domain in [domainWhitelist]. */
    val verified: Boolean = false,
    val domainWhitelist: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
) {
    /**
     * Client-side gate for the join flow (§7.1). Meaningful only when the
     * account's email is verified — see §7.0.
     */
    fun acceptsEmail(email: String?): Boolean {
        if (!verified) return true
        val domain = email?.substringAfterLast('@', "")?.lowercase().orEmpty()
        return domain.isNotEmpty() && domainWhitelist.any { it.lowercase() == domain }
    }
}

/**
 * A course offered in a community, seeded per community alongside the
 * category list so Create Session can use a dropdown instead of free text
 * (§3.1, §7.4).
 */
data class Course(
    val id: String = "",
    val name: String = "",
    val category: CourseCategory = CourseCategory.OTHER,
)

/**
 * A predefined campus location, so sessions get real lat/lng for the
 * proximity sort without any geocoding (§3.1, §7.2).
 */
data class CampusLocation(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)
