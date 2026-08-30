package com.studyfinder.app.model

/**
 * Every enum stored in Firestore is persisted as its [wire] string, never as
 * an ordinal — ordinals break the moment someone reorders a constant, and the
 * Firestore console has to stay human-readable (§3.1).
 */
interface WireEnum {
    val wire: String
}

/** `sessions.tagType` — the spec's "session type" filter chips (§7.2). */
enum class TagType(override val wire: String) : WireEnum {
    NORMAL("normal"),
    MIDTERM("midterm"),
    FINAL("final");

    companion object {
        fun from(wire: String?): TagType? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * `sessions.courseCategory` — the spec's "course type" filter chips (§7.2).
 * Distinct from `courseId`: a category spans many course IDs, so it cannot be
 * derived at query time. Seeded per community alongside the course list.
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

/** `sessions.expectationLevel` — an enum, not free text, because Home sorts by it (§3.1). */
enum class ExpectationLevel(override val wire: String) : WireEnum {
    PASS("pass"),
    CASUAL("casual"),
    OVERACHIEVING("overachieving");

    companion object {
        fun from(wire: String?): ExpectationLevel? = entries.firstOrNull { it.wire == wire }
    }
}

/** `sessions.mode` — chosen at creation time (§7.4). */
enum class SessionMode(override val wire: String) : WireEnum {
    OPEN("open"),
    GATED("gated");

    companion object {
        fun from(wire: String?): SessionMode? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * `sessions.status`. Deliberately has no `completed` value — nothing
 * serverless can flip that flag when the end time passes, so past-vs-upcoming
 * is derived from `endTime` client-side (§3.1).
 */
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
 * possible without scanning the inbox (§3.1). Neither [INVITED] nor [PENDING]
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

/** `users/{uid}/inbox/{id}.type` (§3.1). */
enum class InboxType(override val wire: String) : WireEnum {
    INVITE("invite"),
    JOIN_REQUEST("join_request"),
    SYSTEM("system");

    companion object {
        fun from(wire: String?): InboxType? = entries.firstOrNull { it.wire == wire }
    }
}

/** Home's sort toggle (§7.2). [DISTANCE] requires the location permission. */
enum class SessionSort {
    TIME,
    EXPECTATION_LEVEL,
    DISTANCE,
}

/**
 * Which face Session Detail shows. [PAST] is the spec's "past view mode",
 * reached from History — every action button is suppressed (§7.3 row 1).
 */
enum class SessionViewMode {
    LIVE,
    PAST,
}
