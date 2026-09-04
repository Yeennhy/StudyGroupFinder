package com.studyfinder.app.model

/**
 * Every enum stored in Firestore is persisted as its [wire] string,
 */
interface WireEnum {
    val wire: String
}

/** `sessions.tagType` — session filter tags. */
enum class TagType(override val wire: String) : WireEnum {
    NORMAL("normal"),
    MIDTERM("midterm"),
    FINAL("final");

    companion object {
        fun from(wire: String?): TagType? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * `sessions.courseCategory` — course category tags.

 */
enum class CourseCategory(override val wire: String) : WireEnum {
    PHYSICS("physics"),
    CALCULUS("calculus"),
    DSA("dsa"),
    PROGRAMMING("programming"),
    ENGLISH("english"),
    ARTS("arts"),
    SOCIAL("social"),
    OTHER("other");

    companion object {
        fun from(wire: String?): CourseCategory? = entries.firstOrNull { it.wire == wire }
    }
}

enum class ExpectationLevel(override val wire: String) : WireEnum {
    PASS("pass"),
    CASUAL("casual"),
    OVERACHIEVING("overachieving");

    companion object {
        fun from(wire: String?): ExpectationLevel? = entries.firstOrNull { it.wire == wire }
    }
}

enum class SessionMode(override val wire: String) : WireEnum {
    OPEN("open"),
    GATED("gated");

    companion object {
        fun from(wire: String?): SessionMode? = entries.firstOrNull { it.wire == wire }
    }
}

enum class SessionStatus(override val wire: String) : WireEnum {
    UPCOMING("upcoming"),
    CANCELLED("cancelled"),
    FINISHED("finished");

    companion object {
        fun from(wire: String?): SessionStatus? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * `sessions/{id}/members/{uid}.status`.
 *
 * [INVITED] is what makes the "Accept Invite" button on Session Detail
 * possible without scanning the inbox. Neither [INVITED] nor [PENDING]
 * counts toward `joinedCount` / `memberUids`.
 */
enum class MemberStatus(override val wire: String) : WireEnum {
    INVITED("invited"),
    PENDING("pending"),
    ACCEPTED("accepted"),
    ADMIN("admin");

    companion object {
        fun from(wire: String?): MemberStatus? = entries.firstOrNull { it.wire == wire }
    }
}

/** `users/{uid}/inbox/{id}.type`. */
enum class InboxType(override val wire: String) : WireEnum {
    INVITE("invite"),
    JOIN_REQUEST("join_request"),
    SYSTEM("system");

    companion object {
        fun from(wire: String?): InboxType? = entries.firstOrNull { it.wire == wire }
    }
}

/** Home's sort options. [DISTANCE] requires the location permission. */
enum class SessionSort {
    /** Soonest start time first. */
    TIME,
    /** Session title A → Z. */
    NAME_ASC,
    /** Session title Z → A. */
    NAME_DESC,
    /** Closest campus location first (Haversine on lat/lng). */
    DISTANCE,
}

/**
 * Which face Session Detail shows. [PAST] is reached from History —
 * every action button is suppressed.
 */
enum class SessionViewMode {
    LIVE,
    PAST,
}
