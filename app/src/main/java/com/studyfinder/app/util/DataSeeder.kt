package com.studyfinder.app.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.studyfinder.app.data.remote.firestore.FirestoreRefs
import com.studyfinder.app.model.CourseCategory
import com.studyfinder.app.model.ExpectationLevel
import com.studyfinder.app.model.InboxType
import com.studyfinder.app.model.MemberStatus
import com.studyfinder.app.model.SessionMode
import com.studyfinder.app.model.SessionStatus
import com.studyfinder.app.model.TagType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

/**
 * Seeds a full, test-shaped dataset for every schema in §3.1 of the dev plan.
 *
 * Covers the cases the UI branches actually need to exercise:
 *  - verified vs free-for-all communities, several cities (Community filter)
 *  - ≥3 real campus locations + multi-category course lists per community
 *  - a "serial member" (`u-mem-rach`) double-booked across overlapping sessions
 *    (OverlapUtils / BusyInterval on Home)
 *  - a blocked-user pair, where the blocked user is in a session roster
 *  - sessions across every mode × tagType × courseCategory × expectationLevel
 *  - one full session, invited/pending member rows (Inbox invite / join_request)
 *  - inbox items of all three types for the signed-in user
 *
 * SEEDING CHECKLIST — do this in order, do NOT skip step 4:
 *  1. Firebase Console → Firestore → Rules: temporarily set
 *       allow read, write: if true;
 *     for `match /databases/{database}/documents { match /{document=**} }`.
 *  2. Launch a debug build once; wait for the "Seeding complete!" toast.
 *  3. Immediately paste the real rules from §4 of the dev plan back. Do not
 *     leave the project world-writable.
 *  4. The SharedPreferences flags below stop a re-seed on the next launch. To
 *     force a re-seed, clear app data or bump [GLOBAL_SEED_VERSION].
 *
 * Global data is seeded once. Rows tied to the signed-in account (block doc +
 * inbox items) are seeded once per uid — if you seed before logging in, kill
 * and reopen the app once after your first login so those run.
 */
object DataSeeder {

    private const val TAG = "DataSeeder"
    private const val PREFS = "data_seeder"
    private const val GLOBAL_SEED_VERSION = 2
    private const val KEY_GLOBAL = "global_seed_version"
    private const val KEY_USER_PREFIX = "user_seed_"

    // Stable seeded user ids (NOT Firebase Auth uids — these represent other
    // people for member lists, rosters and browse screens).
    private const val U_HOST = "u-host-power"          // hosts many sessions
    private const val U_MEM_RACH = "u-mem-rach"        // over-committed member
    private const val U_BLOCKED = "u-blocked-sample"   // blocked by current user
    private const val U_A = "u-alice"
    private const val U_B = "u-bob"
    private const val U_C = "u-carol"
    private const val U_D = "u-dan"
    private const val U_E = "u-erin"

    /** Entry point. Cheap to call on every launch — the flags short-circuit. */
    fun seedAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        val needGlobal = prefs.getInt(KEY_GLOBAL, 0) < GLOBAL_SEED_VERSION
        val needUser = currentUid != null && !prefs.getBoolean(KEY_USER_PREFIX + currentUid, false)
        if (!needGlobal && !needUser) return

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (needGlobal) {
                    Toast.makeText(context, "Seeding database…", Toast.LENGTH_SHORT).show()
                    seedCommunities()
                    seedUsers()
                    seedSessions()
                    prefs.edit().putInt(KEY_GLOBAL, GLOBAL_SEED_VERSION).apply()
                }
                if (needUser && currentUid != null) {
                    seedCurrentUserExtras(currentUid)
                    prefs.edit().putBoolean(KEY_USER_PREFIX + currentUid, true).apply()
                }
                Toast.makeText(context, "Seeding complete!", Toast.LENGTH_LONG).show()
                Log.d(TAG, "Seeding finished successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Seeding failed", e)
                Toast.makeText(context, "Seeding failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------------------------------------------------------------- communities

    private suspend fun seedCommunities() {
        val communities = listOf(
            CommunityData(
                id = "HCMUS",
                name = "University of Science, VNU-HCM",
                city = "Ho Chi Minh City",
                verified = true,
                domains = listOf("fitus.edu.vn", "hcmus.edu.vn"),
                courses = listOf(
                    CourseData("CSC10004", "Data Structures and Algorithms", CourseCategory.DSA),
                    CourseData("CSC10008", "Mobile Application Development", CourseCategory.PROGRAMMING),
                    CourseData("MTH00003", "Calculus 1", CourseCategory.CALCULUS),
                    CourseData("PHY00002", "Physics 2", CourseCategory.PHYSICS),
                    CourseData("ENG00010", "Academic English", CourseCategory.ENGLISH),
                    CourseData("ART00001", "Design Thinking", CourseCategory.ARTS),
                    CourseData("SOC00004", "Introduction to Sociology", CourseCategory.SOCIAL),
                ),
                locations = listOf(
                    LocationData("LIB", "Main Library, 9th Floor", 10.76280, 106.68220),
                    LocationData("SRA", "Study Room A - Building F", 10.76315, 106.68255),
                    LocationData("HALLB", "Hall B - Building E", 10.76240, 106.68180),
                    LocationData("CANTEEN", "Canteen Study Corner", 10.76350, 106.68300),
                ),
            ),
            CommunityData(
                id = "FPT-HCM",
                name = "FPT University HCM",
                city = "Ho Chi Minh City",
                verified = true,
                domains = listOf("fpt.edu.vn"),
                courses = listOf(
                    CourseData("PRN212", "Basic Cross-Platform App Programming", CourseCategory.PROGRAMMING),
                    CourseData("MAD101", "Discrete Mathematics", CourseCategory.CALCULUS),
                    CourseData("CSD201", "Data Structures and Algorithms", CourseCategory.DSA),
                    CourseData("ENW492c", "Writing Research Papers", CourseCategory.ENGLISH),
                ),
                locations = listOf(
                    LocationData("BETA", "Beta Building - Room 201", 10.84110, 106.80990),
                    LocationData("GAMMA", "Gamma Building - Library", 10.84160, 106.81040),
                    LocationData("ALPHA", "Alpha Hall", 10.84070, 106.80930),
                ),
            ),
            CommunityData(
                id = "HUST",
                name = "Hanoi University of Science and Technology",
                city = "Hanoi",
                verified = true,
                domains = listOf("hust.edu.vn", "sis.hust.edu.vn"),
                courses = listOf(
                    CourseData("IT3010", "Data Structures and Algorithms", CourseCategory.DSA),
                    CourseData("MI1111", "Calculus 1", CourseCategory.CALCULUS),
                    CourseData("PH1110", "Physics 1", CourseCategory.PHYSICS),
                    CourseData("IT4409", "Web Technologies", CourseCategory.PROGRAMMING),
                ),
                locations = listOf(
                    LocationData("TAHOAT", "Ta Quang Buu Library", 21.00450, 105.84330),
                    LocationData("D3", "Building D3 - Room 305", 21.00520, 105.84280),
                    LocationData("C1", "Building C1 - Lobby", 21.00390, 105.84400),
                ),
            ),
            CommunityData(
                id = "OPEN-STUDY-DN",
                name = "Da Nang Open Study Circle",
                city = "Da Nang",
                verified = false,
                domains = emptyList(),
                courses = listOf(
                    CourseData("GEN-DSA", "Algorithms Practice", CourseCategory.DSA),
                    CourseData("GEN-ENG", "Conversational English", CourseCategory.ENGLISH),
                    CourseData("GEN-ART", "Sketching Club", CourseCategory.ARTS),
                    CourseData("GEN-SOC", "Current Affairs Reading", CourseCategory.SOCIAL),
                ),
                locations = listOf(
                    LocationData("DNLIB", "Da Nang Public Library", 16.06780, 108.22080),
                    LocationData("CAFE43", "Cafe 43 - Quiet Room", 16.07220, 108.21500),
                    LocationData("PARK", "APEC Park Pavilion", 16.05900, 108.22400),
                ),
            ),
            CommunityData(
                id = "OPEN-STUDY-CT",
                name = "Can Tho Learners Hub",
                city = "Can Tho",
                verified = false,
                domains = emptyList(),
                courses = listOf(
                    CourseData("CT-CAL", "Calculus Refresher", CourseCategory.CALCULUS),
                    CourseData("CT-PHY", "Physics Problem Solving", CourseCategory.PHYSICS),
                    CourseData("CT-OTH", "Study Skills Workshop", CourseCategory.OTHER),
                ),
                locations = listOf(
                    LocationData("CTULIB", "CTU Learning Resource Center", 10.02990, 105.76870),
                    LocationData("NINHKIEU", "Ninh Kieu Study Space", 10.03350, 105.78840),
                    LocationData("COWORK", "Can Tho Coworking Hub", 10.04100, 105.77600),
                ),
            ),
        )

        for (comm in communities) {
            val ref = FirestoreRefs.community(comm.id)
            ref.set(
                mapOf(
                    "name" to comm.name,
                    "city" to comm.city,
                    "verified" to comm.verified,
                    "domainWhitelist" to comm.domains,
                    "createdAt" to Timestamp.now(),
                )
            ).await()

            val courseColl = ref.collection("courses")
            for (course in comm.courses) {
                courseColl.document(course.id).set(
                    mapOf("name" to course.name, "category" to course.category.wire)
                ).await()
            }

            val locColl = ref.collection("locations")
            for (loc in comm.locations) {
                locColl.document(loc.id).set(
                    mapOf("name" to loc.name, "lat" to loc.lat, "lng" to loc.lng)
                ).await()
            }
        }
    }

    // --------------------------------------------------------------------- users

    private suspend fun seedUsers() {
        val users = listOf(
            UserData(U_HOST, "Tran Minh Host", "21120100", "HCMUS", "Computer Science", "Software Engineering", "2021", "I run a lot of study sessions."),
            UserData(U_MEM_RACH, "Pham Thi Busy", "21120101", "HCMUS", "Mathematics", "Applied Math", "2021", "Joined too many groups this semester."),
            UserData(U_BLOCKED, "Le Van Noise", "21120102", "HCMUS", "Physics", "Physics", "2021", "…"),
            UserData(U_A, "Nguyen Van A", "21120001", "HCMUS", "Computer Science", "Software Engineering", "2021", "DSA enthusiast."),
            UserData(U_B, "Le Thi B", "21120002", "HCMUS", "Mathematics", "Applied Math", "2021", "I love Calculus!"),
            UserData(U_C, "Do Van C", "21120003", "HCMUS", "Computer Science", "Data Science", "2022", "Learning mobile dev."),
            UserData(U_D, "Vo Thi D", "21120004", "HCMUS", "Literature", "English", "2022", "English club regular."),
            UserData(U_E, "Bui Van E", "21120005", "HCMUS", "Sociology", "Sociology", "2020", "Reading group host."),
        )

        for (user in users) {
            FirestoreRefs.user(user.uid).set(
                mapOf(
                    "name" to user.name,
                    "studentId" to user.studentId,
                    "communityId" to user.communityId,
                    "department" to user.department,
                    "major" to user.major,
                    "admissionYear" to user.admissionYear,
                    "bio" to user.bio,
                    "photoUrl" to "",
                    "createdAt" to Timestamp.now(),
                )
            ).await()
        }

        // A block pair among seeded users so member rosters have a blocked
        // person even before the signed-in user blocks anyone.
        FirestoreRefs.blocked(U_A).document(U_BLOCKED).set(mapOf("createdAt" to Timestamp.now())).await()
    }

    // ------------------------------------------------------------------ sessions

    private suspend fun seedSessions() {
        // Reference clock: "tomorrow 09:00" local.
        val base = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val hour = 60 * 60 * 1000L

        // id, community, host, courseId, courseName, category, tag, expectation,
        // title, locationName, lat, lng, startOffset(ms), durationHours, capacity,
        // mode, accepted(list), pending(list), invited(list)
        val sessions = listOf(
            SeedSession("s-dsa-review", "HCMUS", U_HOST, "CSC10004", "Data Structures and Algorithms",
                CourseCategory.DSA, TagType.MIDTERM, ExpectationLevel.OVERACHIEVING,
                "Hardcore DSA Midterm Review", "Main Library, 9th Floor", 10.76280, 106.68220,
                0, 2, 5, SessionMode.OPEN,
                accepted = listOf(U_A, U_B), pending = emptyList(), invited = emptyList()),

            SeedSession("s-calc-help", "HCMUS", U_B, "MTH00003", "Calculus 1",
                CourseCategory.CALCULUS, TagType.NORMAL, ExpectationLevel.CASUAL,
                "Calculus 1 Homework Help", "Study Room A - Building F", 10.76315, 106.68255,
                3 * hour, 2, 10, SessionMode.GATED,
                accepted = listOf(U_C), pending = listOf(U_D), invited = emptyList()),

            SeedSession("s-phys-final-full", "HCMUS", U_HOST, "PHY00002", "Physics 2",
                CourseCategory.PHYSICS, TagType.FINAL, ExpectationLevel.OVERACHIEVING,
                "Physics 2 Final Cram (FULL)", "Hall B - Building E", 10.76240, 106.68180,
                5 * hour, 3, 4, SessionMode.OPEN,
                accepted = listOf(U_A, U_B, U_C), pending = emptyList(), invited = emptyList()),

            SeedSession("s-mobile-sync", "HCMUS", U_HOST, "CSC10008", "Mobile Application Development",
                CourseCategory.PROGRAMMING, TagType.NORMAL, ExpectationLevel.PASS,
                "Mobile Dev Project Sync", "Canteen Study Corner", 10.76350, 106.68300,
                26 * hour, 2, 6, SessionMode.GATED,
                accepted = listOf(U_C), pending = listOf(U_E), invited = listOf(U_D)),

            SeedSession("s-english-club", "HCMUS", U_D, "ENG00010", "Academic English",
                CourseCategory.ENGLISH, TagType.NORMAL, ExpectationLevel.CASUAL,
                "English Speaking Club", "Canteen Study Corner", 10.76350, 106.68300,
                28 * hour, 1, 8, SessionMode.OPEN,
                accepted = listOf(U_C), pending = emptyList(), invited = emptyList()),

            // Overlap pair: U_MEM_RACH is accepted in BOTH, and they overlap.
            SeedSession("s-overlap-a", "HCMUS", U_C, "CSC10004", "Data Structures and Algorithms",
                CourseCategory.DSA, TagType.NORMAL, ExpectationLevel.CASUAL,
                "Morning Algo Warmup", "Study Room A - Building F", 10.76315, 106.68255,
                0, 2, 6, SessionMode.OPEN,
                accepted = listOf(U_MEM_RACH), pending = emptyList(), invited = emptyList()),
            SeedSession("s-overlap-b", "HCMUS", U_B, "MTH00003", "Calculus 1",
                CourseCategory.CALCULUS, TagType.NORMAL, ExpectationLevel.CASUAL,
                "Morning Calculus Group", "Hall B - Building E", 10.76240, 106.68180,
                hour, 2, 6, SessionMode.OPEN,
                accepted = listOf(U_MEM_RACH), pending = emptyList(), invited = emptyList()),

            // Roster contains U_BLOCKED (blocked by U_A in seedUsers; and by the
            // signed-in user in seedCurrentUserExtras).
            SeedSession("s-arts-workshop", "HCMUS", U_E, "ART00001", "Design Thinking",
                CourseCategory.ARTS, TagType.NORMAL, ExpectationLevel.CASUAL,
                "Design Thinking Workshop", "Main Library, 9th Floor", 10.76280, 106.68220,
                30 * hour, 2, 8, SessionMode.OPEN,
                accepted = listOf(U_BLOCKED, U_A), pending = emptyList(), invited = emptyList()),

            SeedSession("s-social-read", "HCMUS", U_E, "SOC00004", "Introduction to Sociology",
                CourseCategory.SOCIAL, TagType.NORMAL, ExpectationLevel.PASS,
                "Sociology Reading Circle", "Canteen Study Corner", 10.76350, 106.68300,
                50 * hour, 2, 10, SessionMode.OPEN,
                accepted = emptyList(), pending = emptyList(), invited = emptyList()),

            SeedSession("s-dsa-gated-mid", "HCMUS", U_HOST, "CSC10004", "Data Structures and Algorithms",
                CourseCategory.DSA, TagType.MIDTERM, ExpectationLevel.OVERACHIEVING,
                "DSA Gated Deep Dive", "Hall B - Building E", 10.76240, 106.68180,
                52 * hour, 2, 5, SessionMode.GATED,
                accepted = listOf(U_A), pending = listOf(U_B, U_C), invited = emptyList()),

            // Other communities — light coverage.
            SeedSession("s-fpt-prn", "FPT-HCM", U_HOST, "PRN212", "Basic Cross-Platform App Programming",
                CourseCategory.PROGRAMMING, TagType.MIDTERM, ExpectationLevel.PASS,
                "PRN212 Slot Test Prep", "Beta Building - Room 201", 10.84110, 106.80990,
                4 * hour, 2, 6, SessionMode.OPEN,
                accepted = emptyList(), pending = emptyList(), invited = emptyList()),
            SeedSession("s-hust-algo", "HUST", U_HOST, "IT3010", "Data Structures and Algorithms",
                CourseCategory.DSA, TagType.FINAL, ExpectationLevel.OVERACHIEVING,
                "IT3010 Final Marathon", "Ta Quang Buu Library", 21.00450, 105.84330,
                6 * hour, 3, 8, SessionMode.GATED,
                accepted = emptyList(), pending = emptyList(), invited = emptyList()),
            SeedSession("s-dn-english", "OPEN-STUDY-DN", U_D, "GEN-ENG", "Conversational English",
                CourseCategory.ENGLISH, TagType.NORMAL, ExpectationLevel.CASUAL,
                "Beach English Meetup", "Cafe 43 - Quiet Room", 16.07220, 108.21500,
                8 * hour, 2, 12, SessionMode.OPEN,
                accepted = emptyList(), pending = emptyList(), invited = emptyList()),
        )

        for (s in sessions) {
            val memberUids = s.accepted.toMutableList().apply { add(0, s.host) }
            val start = base + s.startOffset
            val end = start + s.durationHours * hour
            FirestoreRefs.session(s.id).set(
                mapOf(
                    "communityId" to s.community,
                    "hostUid" to s.host,
                    "courseId" to s.courseId,
                    "courseName" to s.courseName,
                    "courseCategory" to s.category.wire,
                    "tagType" to s.tag.wire,
                    "expectationLevel" to s.expectation.wire,
                    "title" to s.title,
                    "description" to "Seeded session for testing: ${s.title}.",
                    "goals" to "Cover the key topics and practice together.",
                    "locationName" to s.locationName,
                    "lat" to s.lat,
                    "lng" to s.lng,
                    "startTime" to Timestamp(Date(start)),
                    "endTime" to Timestamp(Date(end)),
                    "capacity" to s.capacity,
                    "joinedCount" to memberUids.size,
                    "memberUids" to memberUids,
                    "mode" to s.mode.wire,
                    "status" to SessionStatus.UPCOMING.wire,
                    "materialUrls" to emptyList<String>(),
                    "tags" to emptyList<String>(),
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now(),
                )
            ).await()

            // members subcollection: host = admin, accepted = accepted,
            // pending / invited do NOT enter memberUids or joinedCount.
            writeMember(s.id, s.host, MemberStatus.ADMIN)
            s.accepted.forEach { writeMember(s.id, it, MemberStatus.ACCEPTED) }
            s.pending.forEach { writeMember(s.id, it, MemberStatus.PENDING) }
            s.invited.forEach { writeMember(s.id, it, MemberStatus.INVITED) }

            // A join_request inbox item to the host for each pending requester.
            s.pending.forEach { requester ->
                FirestoreRefs.inbox(s.host).add(
                    mapOf(
                        "type" to InboxType.JOIN_REQUEST.wire,
                        "sessionId" to s.id,
                        "fromUid" to requester,
                        "message" to "Requested to join \"${s.title}\".",
                        "read" to false,
                        "createdAt" to Timestamp.now(),
                    )
                ).await()
            }
            // An invite inbox item to each invited user.
            s.invited.forEach { invitee ->
                FirestoreRefs.inbox(invitee).add(
                    mapOf(
                        "type" to InboxType.INVITE.wire,
                        "sessionId" to s.id,
                        "fromUid" to s.host,
                        "message" to "You are invited to \"${s.title}\".",
                        "read" to false,
                        "createdAt" to Timestamp.now(),
                    )
                ).await()
            }
        }
    }

    private suspend fun writeMember(sessionId: String, uid: String, status: MemberStatus) {
        FirestoreRefs.member(sessionId, uid).set(
            mapOf("status" to status.wire, "joinedAt" to Timestamp.now())
        ).await()
    }

    // ------------------------------------------------- signed-in account extras

    /**
     * Rows that only make sense once we know the real uid: a block against a
     * seeded user who sits in a roster, plus one inbox item of each type so the
     * Inbox screen has all three rows to render.
     */
    private suspend fun seedCurrentUserExtras(uid: String) {
        // Block a user who is a member of s-arts-workshop -> that card greys out
        // on Home with a "contains a blocked user" notice.
        FirestoreRefs.blocked(uid).document(U_BLOCKED)
            .set(mapOf("createdAt" to Timestamp.now())).await()

        // invite -> also needs a members/{uid} row so "Accept Invite" works and
        // Details opens Session Detail.
        writeMember("s-english-club", uid, MemberStatus.INVITED)
        FirestoreRefs.inbox(uid).add(
            mapOf(
                "type" to InboxType.INVITE.wire,
                "sessionId" to "s-english-club",
                "fromUid" to U_D,
                "message" to "You are invited to \"English Speaking Club\".",
                "read" to false,
                "createdAt" to Timestamp.now(),
            )
        ).await()

        // join_request -> host-facing. Make the signed-in user host of a fresh
        // session with a pending requester so "Details" opens Session Manage.
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 2)
            set(Calendar.HOUR_OF_DAY, 14); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        FirestoreRefs.session("s-my-hosted").set(
            mapOf(
                "communityId" to "HCMUS",
                "hostUid" to uid,
                "courseId" to "CSC10008",
                "courseName" to "Mobile Application Development",
                "courseCategory" to CourseCategory.PROGRAMMING.wire,
                "tagType" to TagType.NORMAL.wire,
                "expectationLevel" to ExpectationLevel.CASUAL.wire,
                "title" to "My Hosted Session (test)",
                "description" to "Seeded: you host this, expect a join request.",
                "goals" to "Try approving the pending request.",
                "locationName" to "Study Room A - Building F",
                "lat" to 10.76315,
                "lng" to 106.68255,
                "startTime" to Timestamp(Date(start)),
                "endTime" to Timestamp(Date(start + 2 * 60 * 60 * 1000L)),
                "capacity" to 5,
                "joinedCount" to 1,
                "memberUids" to listOf(uid),
                "mode" to SessionMode.GATED.wire,
                "status" to SessionStatus.UPCOMING.wire,
                "materialUrls" to emptyList<String>(),
                "tags" to emptyList<String>(),
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now(),
            )
        ).await()
        writeMember("s-my-hosted", uid, MemberStatus.ADMIN)
        writeMember("s-my-hosted", U_C, MemberStatus.PENDING)
        FirestoreRefs.inbox(uid).add(
            mapOf(
                "type" to InboxType.JOIN_REQUEST.wire,
                "sessionId" to "s-my-hosted",
                "fromUid" to U_C,
                "message" to "Do Van C requested to join \"My Hosted Session (test)\".",
                "read" to false,
                "createdAt" to Timestamp.now(),
            )
        ).await()

        // system
        FirestoreRefs.inbox(uid).add(
            mapOf(
                "type" to InboxType.SYSTEM.wire,
                "sessionId" to null,
                "fromUid" to null,
                "message" to "Welcome to Study Group Finder! Your test data is ready.",
                "read" to false,
                "createdAt" to Timestamp.now(),
            )
        ).await()
    }

    // ------------------------------------------------------------------- models

    private data class CommunityData(
        val id: String,
        val name: String,
        val city: String,
        val verified: Boolean,
        val domains: List<String>,
        val courses: List<CourseData>,
        val locations: List<LocationData>,
    )

    private data class UserData(
        val uid: String,
        val name: String,
        val studentId: String,
        val communityId: String,
        val department: String,
        val major: String,
        val admissionYear: String,
        val bio: String,
    )

    private data class CourseData(val id: String, val name: String, val category: CourseCategory)
    private data class LocationData(val id: String, val name: String, val lat: Double, val lng: Double)

    private data class SeedSession(
        val id: String,
        val community: String,
        val host: String,
        val courseId: String,
        val courseName: String,
        val category: CourseCategory,
        val tag: TagType,
        val expectation: ExpectationLevel,
        val title: String,
        val locationName: String,
        val lat: Double,
        val lng: Double,
        val startOffset: Long,
        val durationHours: Int,
        val capacity: Int,
        val mode: SessionMode,
        val accepted: List<String>,
        val pending: List<String>,
        val invited: List<String>,
    )
}
