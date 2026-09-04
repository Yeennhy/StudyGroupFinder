package com.studyfinder.app.model

/**
 * `communities/{communityId}`.
 *
 * The only publicly-readable collection — it is what the pre-login REST
 * screen fetches over Retrofit.
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
     * Client-side gate for the join flow. Checks the email's domain
     * (the part after `@`) against [domainWhitelist]. A whitelist entry
     * matches the domain itself *or* any subdomain of it, so `hcmus.edu.vn`
     * accepts `me@hcmus.edu.vn` and `me@student.hcmus.edu.vn` alike.
     */
    fun acceptsEmail(email: String?): Boolean {
        if (!verified) return true
        val domain = email?.substringAfterLast('@', "")?.trim()?.lowercase().orEmpty()
        if (domain.isEmpty()) return false
        return domainWhitelist.any { raw ->
            val d = raw.trim().lowercase().removePrefix("@").removePrefix(".")
            d.isNotEmpty() && (domain == d || domain.endsWith(".$d"))
        }
    }
}

/**
 * A course offered in a community, seeded per community alongside the
 * category list so Create Session can use a dropdown instead of free text.
 */
data class Course(
    val id: String = "",
    val name: String = "",
    val category: CourseCategory = CourseCategory.OTHER,
)

/**
 * A predefined campus location, so sessions get real lat/lng for the
 * proximity sort without any geocoding.
 */
data class CampusLocation(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)
