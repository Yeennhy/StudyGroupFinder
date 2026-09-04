package com.studyfinder.app.data.remote.firestore

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreRefs {

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    // ---- top level ----
    fun communities(): CollectionReference = db.collection("communities")
    fun community(id: String): DocumentReference = communities().document(id)

    fun users(): CollectionReference = db.collection("users")
    fun user(uid: String): DocumentReference = users().document(uid)

    fun sessions(): CollectionReference = db.collection("sessions")
    fun session(id: String): DocumentReference = sessions().document(id)

    // ---- subcollections ----

    /** Membership rows. Always written together with the parent's counters. */
    fun members(sessionId: String): CollectionReference =
        session(sessionId).collection("members")

    fun member(sessionId: String, uid: String): DocumentReference =
        members(sessionId).document(uid)

    /** Invites + notifications merged into one screen. */
    fun inbox(uid: String): CollectionReference = user(uid).collection("inbox")

    /**
     * Private block list — a subcollection rather than a field, because the
     * parent user document is world-readable to signed-in users.
     */
    fun blocked(uid: String): CollectionReference = user(uid).collection("blocked")

    // ---- field names, so string literals live in exactly one file ----
    object Field {
        const val COMMUNITY_ID = "communityId"
        const val COURSE_ID = "courseId"
        const val COURSE_NAME = "courseName"
        const val COURSE_CATEGORY = "courseCategory"
        const val TAG_TYPE = "tagType"
        const val EXPECTATION_LEVEL = "expectationLevel"
        const val START_TIME = "startTime"
        const val END_TIME = "endTime"
        const val STATUS = "status"
        const val MEMBER_UIDS = "memberUids"
        const val JOINED_COUNT = "joinedCount"
        const val CAPACITY = "capacity"
        const val JOINED_AT = "joinedAt"
        const val HOST_UID = "hostUid"
        const val STUDENT_ID = "studentId"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        const val CITY = "city"
        const val TITLE = "title"
        const val DESCRIPTION = "description"
        const val GOALS = "goals"
        const val LOCATION_NAME = "locationName"
        const val LAT = "lat"
        const val LNG = "lng"
        const val MODE = "mode"
        const val MATERIAL_URLS = "materialUrls"
        const val NAME = "name"
        const val DEPARTMENT = "department"
        const val MAJOR = "major"
        const val ADMISSION_YEAR = "admissionYear"
        const val BIO = "bio"
        const val PHOTO_URL = "photoUrl"
        const val IMAGE_URL = "imageUrl"
        const val VERIFIED = "verified"
        const val DOMAIN_WHITELIST = "domainWhitelist"
        const val TYPE = "type"
        const val SESSION_ID = "sessionId"
        const val FROM_UID = "fromUid"
        const val MESSAGE = "message"
        const val READ = "read"
    }
}
