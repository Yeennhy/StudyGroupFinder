package com.studyfinder.app.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.Timestamp
import com.studyfinder.app.data.remote.firestore.FirestoreRefs
import com.studyfinder.app.model.CourseCategory
import com.studyfinder.app.model.ExpectationLevel
import com.studyfinder.app.model.InboxType
import com.studyfinder.app.model.SessionMode
import com.studyfinder.app.model.SessionStatus
import com.studyfinder.app.model.TagType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * Utility to seed the initial database state for ALL schemas in firestore_schema.artifact.md.
 *
 * IMPORTANT: Temporarily change Firestore rules to 'allow write: if true'
 * for all collections before running this.
 */
object DataSeeder {

    private const val TAG = "DataSeeder"

    fun seedAll(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Toast.makeText(context, "Seeding full database...", Toast.LENGTH_SHORT).show()
                
                seedCommunities()
                seedUsersAndRelationships()
                seedSessionsAndMembers()
                
                Toast.makeText(context, "Seeding complete!", Toast.LENGTH_LONG).show()
                Log.d(TAG, "Seeding finished successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Seeding failed", e)
                Toast.makeText(context, "Seeding failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun seedCommunities() {
        val communities = listOf(
            CommunityData(
                id = "HCMUS",
                name = "University of Science, VNU-HCM",
                city = "Ho Chi Minh City",
                verified = true,
                domains = listOf("fitus.edu.vn", "hcmus.edu.vn"),
                courses = listOf(
                    CourseData("DSA", "Data Structures and Algorithms", CourseCategory.DSA),
                    CourseData("MOB", "Mobile Application Development", CourseCategory.PROGRAMMING),
                    CourseData("CAL1", "Calculus 1", CourseCategory.CALCULUS),
                    CourseData("PH2", "Physics 2", CourseCategory.PHYSICS)
                ),
                locations = listOf(
                    LocationData("LIB", "Main Library", 10.7629, 106.6822),
                    LocationData("SRA", "Study Room A", 10.7631, 106.6825),
                    LocationData("HALL-B", "Hall B", 10.7630, 106.6820)
                )
            ),
            CommunityData(
                id = "FPT-HCM",
                name = "FPT University HCM",
                city = "Ho Chi Minh City",
                verified = true,
                domains = listOf("fpt.edu.vn"),
                courses = listOf(
                    CourseData("PRN211", "Basic Cross-Platform Application", CourseCategory.PROGRAMMING),
                    CourseData("MAD101", "Discrete Mathematics", CourseCategory.CALCULUS)
                ),
                locations = listOf(
                    LocationData("HALC", "Hall C", 10.8411, 106.8099)
                )
            ),
            CommunityData(
                id = "UIT",
                name = "University of Information Technology",
                city = "Ho Chi Minh City",
                verified = true,
                domains = listOf("uit.edu.vn"),
                courses = listOf(
                    CourseData("CS112", "Data Structures", CourseCategory.DSA)
                ),
                locations = listOf(
                    LocationData("BUILD-E", "Building E", 10.8701, 106.8030)
                )
            )
        )

        for (comm in communities) {
            val ref = FirestoreRefs.community(comm.id)
            ref.set(mapOf(
                "name" to comm.name,
                "city" to comm.city,
                "verified" to comm.verified,
                "domainWhitelist" to comm.domains,
                "createdAt" to Timestamp.now()
            )).await()

            val courseColl = ref.collection("courses")
            for (course in comm.courses) {
                courseColl.document(course.id).set(mapOf(
                    "name" to course.name,
                    "category" to course.category.wire
                )).await()
            }

            val locColl = ref.collection("locations")
            for (loc in comm.locations) {
                locColl.document(loc.id).set(mapOf(
                    "name" to loc.name,
                    "lat" to loc.lat,
                    "lng" to loc.lng
                )).await()
            }
        }
    }

    private suspend fun seedUsersAndRelationships() {
        val users = listOf(
            UserData(
                uid = "hcmus-user-1",
                name = "Nguyen Van A",
                studentId = "21120001",
                communityId = "HCMUS",
                department = "Computer Science",
                major = "Software Engineering",
                admissionYear = "2021",
                bio = "DSA enthusiast.",
                photoUrl = ""
            ),
            UserData(
                uid = "hcmus-user-2",
                name = "Le Thi B",
                studentId = "21120002",
                communityId = "HCMUS",
                department = "Mathematics",
                major = "Applied Math",
                admissionYear = "2021",
                bio = "I love Calculus!",
                photoUrl = ""
            )
        )

        for (user in users) {
            val userRef = FirestoreRefs.user(user.uid)
            userRef.set(mapOf(
                "name" to user.name,
                "studentId" to user.studentId,
                "communityId" to user.communityId,
                "department" to user.department,
                "major" to user.major,
                "admissionYear" to user.admissionYear,
                "bio" to user.bio,
                "photoUrl" to user.photoUrl,
                "createdAt" to Timestamp.now()
            )).await()

            val inboxRef = userRef.collection("inbox")
            inboxRef.add(mapOf(
                "type" to InboxType.SYSTEM.wire,
                "message" to "Welcome to HCMUS Study Groups, ${user.name}!",
                "read" to false,
                "createdAt" to Timestamp.now()
            )).await()
        }

        FirestoreRefs.blocked("hcmus-user-1").document("blocked-sample-id").set(mapOf(
            "createdAt" to Timestamp.now()
        )).await()
    }

    private suspend fun seedSessionsAndMembers() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val startTime = calendar.time
        calendar.add(Calendar.HOUR, 2)
        val endTime = calendar.time

        // Session 1: HCMUS DSA Session
        val dsaSession = mapOf(
            "communityId" to "HCMUS",
            "hostUid" to "hcmus-user-1",
            "courseId" to "DSA",
            "courseName" to "Data Structures and Algorithms",
            "courseCategory" to CourseCategory.DSA.wire,
            "tagType" to TagType.MIDTERM.wire,
            "expectationLevel" to ExpectationLevel.OVERACHIEVING.wire,
            "title" to "Hardcore DSA Review",
            "description" to "Solving advanced tree problems.",
            "goals" to "Solve 5 Hard LeetCode problems",
            "locationName" to "Main Library",
            "lat" to 10.7629,
            "lng" to 106.6822,
            "startTime" to Timestamp(startTime),
            "endTime" to Timestamp(endTime),
            "capacity" to 5,
            "joinedCount" to 1,
            "memberUids" to listOf("hcmus-user-1"),
            "mode" to SessionMode.OPEN.wire,
            "status" to SessionStatus.UPCOMING.wire,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )
        
        val dsaSessionId = "hcmus-dsa-review"
        FirestoreRefs.sessions().document(dsaSessionId).set(dsaSession).await()
        FirestoreRefs.member(dsaSessionId, "hcmus-user-1").set(mapOf(
            "status" to "admin",
            "joinedAt" to Timestamp.now()
        )).await()

        // Session 2: HCMUS Calculus Session
        val calSession = mapOf(
            "communityId" to "HCMUS",
            "hostUid" to "hcmus-user-2",
            "courseId" to "CAL1",
            "courseName" to "Calculus 1",
            "courseCategory" to CourseCategory.CALCULUS.wire,
            "tagType" to TagType.NORMAL.wire,
            "expectationLevel" to ExpectationLevel.CASUAL.wire,
            "title" to "Calculus 1 Homework Help",
            "description" to "Helping each other with derivatives.",
            "goals" to "Finish week 4 assignments",
            "locationName" to "Study Room A",
            "lat" to 10.7631,
            "lng" to 106.6825,
            "startTime" to Timestamp(startTime),
            "endTime" to Timestamp(endTime),
            "capacity" to 10,
            "joinedCount" to 1,
            "memberUids" to listOf("hcmus-user-2"),
            "mode" to SessionMode.GATED.wire,
            "status" to SessionStatus.UPCOMING.wire,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )

        val calSessionId = "hcmus-calc-help"
        FirestoreRefs.sessions().document(calSessionId).set(calSession).await()
        FirestoreRefs.member(calSessionId, "hcmus-user-2").set(mapOf(
            "status" to "admin",
            "joinedAt" to Timestamp.now()
        )).await()
    }

    private data class CommunityData(
        val id: String,
        val name: String,
        val city: String,
        val verified: Boolean,
        val domains: List<String>,
        val courses: List<CourseData>,
        val locations: List<LocationData>
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
        val photoUrl: String
    )

    private data class CourseData(val id: String, val name: String, val category: CourseCategory)
    private data class LocationData(val id: String, val name: String, val lat: Double, val lng: Double)
}
