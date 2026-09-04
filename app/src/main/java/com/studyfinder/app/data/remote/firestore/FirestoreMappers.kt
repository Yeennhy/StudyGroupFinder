package com.studyfinder.app.data.remote.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.studyfinder.app.data.local.entity.CommunityEntity
import com.studyfinder.app.data.local.entity.MySessionEntity
import com.studyfinder.app.data.local.entity.ProfileEntity
import com.studyfinder.app.data.local.entity.SessionEntity
import com.studyfinder.app.data.remote.firestore.FirestoreRefs.Field
import com.studyfinder.app.model.Community
import com.studyfinder.app.model.CourseCategory
import com.studyfinder.app.model.ExpectationLevel
import com.studyfinder.app.model.InboxItem
import com.studyfinder.app.model.InboxType
import com.studyfinder.app.model.MemberStatus
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionMember
import com.studyfinder.app.model.SessionMode
import com.studyfinder.app.model.SessionStatus
import com.studyfinder.app.model.TagType
import com.studyfinder.app.model.UserProfile

/**
 * Firestore document ⇄ model ⇄ Room entity conversion.
 *
 * There is no separate domain layer — these are the only mapping functions in
 * the app, and they all live here.
 */
object FirestoreMappers {

    // ---- Firestore -> model ----
    fun toSession(doc: DocumentSnapshot): Session? {
        if (!doc.exists()) return null
        return Session(
            id = doc.id,
            communityId = doc.getString(Field.COMMUNITY_ID).orEmpty(),
            hostUid = doc.getString(Field.HOST_UID).orEmpty(),
            courseId = doc.getString(Field.COURSE_ID).orEmpty(),
            courseName = doc.getString(Field.COURSE_NAME).orEmpty(),
            courseCategory = CourseCategory.from(doc.getString(Field.COURSE_CATEGORY)) ?: CourseCategory.OTHER,
            tagType = TagType.from(doc.getString(Field.TAG_TYPE)) ?: TagType.NORMAL,
            expectationLevel = ExpectationLevel.from(doc.getString(Field.EXPECTATION_LEVEL)) ?: ExpectationLevel.PASS,
            title = doc.getString(Field.TITLE).orEmpty(),
            description = doc.getString(Field.DESCRIPTION).orEmpty(),
            goals = doc.getString(Field.GOALS).orEmpty(),
            locationName = doc.getString(Field.LOCATION_NAME).orEmpty(),
            lat = doc.getDouble(Field.LAT),
            lng = doc.getDouble(Field.LNG),
            startTimeMillis = doc.getTimestamp(Field.START_TIME)?.toDate()?.time ?: 0L,
            endTimeMillis = doc.getTimestamp(Field.END_TIME)?.toDate()?.time ?: 0L,
            capacity = doc.getLong(Field.CAPACITY)?.toInt() ?: 0,
            joinedCount = doc.getLong(Field.JOINED_COUNT)?.toInt() ?: 0,
            memberUids = (doc.get(Field.MEMBER_UIDS) as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            mode = SessionMode.from(doc.getString(Field.MODE)) ?: SessionMode.OPEN,
            status = SessionStatus.from(doc.getString(Field.STATUS)) ?: SessionStatus.UPCOMING,
            materialUrls = (doc.get(Field.MATERIAL_URLS) as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            tags = (doc.get("tags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            createdAtMillis = doc.getTimestamp(Field.CREATED_AT)?.toDate()?.time ?: 0L,
            updatedAtMillis = doc.getTimestamp(Field.UPDATED_AT)?.toDate()?.time ?: 0L,
        )
    }

    fun toCommunity(doc: DocumentSnapshot): Community? {
        if (!doc.exists()) return null
        return Community(
            id = doc.id,
            name = doc.getString(Field.NAME).orEmpty(),
            city = doc.getString(Field.CITY).orEmpty(),
            imageUrl = doc.getString(Field.IMAGE_URL).orEmpty(),
            verified = doc.getBoolean(Field.VERIFIED) ?: false,
            domainWhitelist = (doc.get(Field.DOMAIN_WHITELIST) as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            createdAtMillis = doc.getTimestamp(Field.CREATED_AT)?.toDate()?.time ?: 0L,
        )
    }

    fun toUserProfile(doc: DocumentSnapshot): UserProfile? {
        if (!doc.exists()) return null
        return UserProfile(
            uid = doc.id,
            name = doc.getString(Field.NAME).orEmpty(),
            studentId = doc.getString(Field.STUDENT_ID).orEmpty(),
            communityId = doc.getString(Field.COMMUNITY_ID).orEmpty(),
            department = doc.getString(Field.DEPARTMENT).orEmpty(),
            major = doc.getString(Field.MAJOR).orEmpty(),
            admissionYear = doc.getString(Field.ADMISSION_YEAR).orEmpty(),
            bio = doc.getString(Field.BIO).orEmpty(),
            photoUrl = doc.getString(Field.PHOTO_URL).orEmpty(),
            createdAtMillis = doc.getTimestamp(Field.CREATED_AT)?.toDate()?.time ?: 0L,
        )
    }

    fun toSessionMember(doc: DocumentSnapshot): SessionMember? {
        if (!doc.exists()) return null
        return SessionMember(
            uid = doc.id,
            status = MemberStatus.from(doc.getString(Field.STATUS)) ?: MemberStatus.PENDING,
            joinedAtMillis = doc.getTimestamp(Field.JOINED_AT)?.toDate()?.time ?: 0L,
        )
    }

    fun toInboxItem(doc: DocumentSnapshot): InboxItem? {
        if (!doc.exists()) return null
        return InboxItem(
            id = doc.id,
            type = InboxType.from(doc.getString(Field.TYPE)) ?: InboxType.SYSTEM,
            sessionId = doc.getString(Field.SESSION_ID),
            fromUid = doc.getString(Field.FROM_UID),
            message = doc.getString(Field.MESSAGE).orEmpty(),
            read = doc.getBoolean(Field.READ) ?: false,
            createdAtMillis = doc.getTimestamp(Field.CREATED_AT)?.toDate()?.time ?: 0L,
        )
    }

    // ---- model -> Firestore ----

    /**
     * The create payload must already carry hostUid, joinedCount = 1 and
     * memberUids = [hostUid], or the security rules reject it.
     */
    fun sessionCreatePayload(session: Session, hostUid: String): Map<String, Any?> = mapOf<String, Any?>(
        Field.COMMUNITY_ID to session.communityId,
        Field.HOST_UID to hostUid,
        Field.COURSE_ID to session.courseId,
        Field.COURSE_NAME to session.courseName,
        Field.COURSE_CATEGORY to session.courseCategory.wire,
        Field.TAG_TYPE to session.tagType.wire,
        Field.EXPECTATION_LEVEL to session.expectationLevel.wire,
        Field.TITLE to session.title,
        Field.DESCRIPTION to session.description,
        Field.GOALS to session.goals,
        Field.LOCATION_NAME to session.locationName,
        Field.LAT to session.lat,
        Field.LNG to session.lng,
        Field.START_TIME to Timestamp(java.util.Date(session.startTimeMillis)),
        Field.END_TIME to Timestamp(java.util.Date(session.endTimeMillis)),
        Field.CAPACITY to session.capacity,
        Field.JOINED_COUNT to 1,
        Field.MEMBER_UIDS to listOf(hostUid),
        Field.MODE to session.mode.wire,
        Field.STATUS to SessionStatus.UPCOMING.wire,
        Field.MATERIAL_URLS to session.materialUrls,
        "tags" to session.tags,
        Field.CREATED_AT to Timestamp.now(),
        Field.UPDATED_AT to Timestamp.now(),
    )

    fun sessionEditPayload(session: Session): Map<String, Any?> = mapOf<String, Any?>(
        Field.TITLE to session.title,
        Field.DESCRIPTION to session.description,
        Field.GOALS to session.goals,
        Field.COURSE_ID to session.courseId,
        Field.COURSE_NAME to session.courseName,
        Field.COURSE_CATEGORY to session.courseCategory.wire,
        Field.LOCATION_NAME to session.locationName,
        Field.LAT to session.lat,
        Field.LNG to session.lng,
        Field.START_TIME to Timestamp(java.util.Date(session.startTimeMillis)),
        Field.END_TIME to Timestamp(java.util.Date(session.endTimeMillis)),
        Field.CAPACITY to session.capacity,
        Field.MODE to session.mode.wire,
        "tags" to session.tags,
        Field.UPDATED_AT to Timestamp.now(),
    )

    fun profilePayload(profile: UserProfile): Map<String, Any?> = mapOf<String, Any?>(
        Field.NAME to profile.name,
        Field.STUDENT_ID to profile.studentId,
        Field.COMMUNITY_ID to profile.communityId,
        Field.DEPARTMENT to profile.department,
        Field.MAJOR to profile.major,
        Field.ADMISSION_YEAR to profile.admissionYear,
        Field.BIO to profile.bio,
        Field.PHOTO_URL to profile.photoUrl,
        Field.UPDATED_AT to Timestamp.now(),
    )

    /**
     * Cross-user inbox writes are constrained by the rules: fromUid must be
     * the caller, read must be false, message under 500 chars.
     */
    fun inboxPayload(item: InboxItem, fromUid: String): Map<String, Any?> = mapOf<String, Any?>(
        Field.TYPE to item.type.wire,
        Field.SESSION_ID to item.sessionId,
        Field.FROM_UID to fromUid,
        Field.MESSAGE to item.message,
        Field.READ to false,
        Field.CREATED_AT to Timestamp.now(),
    )

    fun memberPayload(status: MemberStatus): Map<String, Any?> = mapOf<String, Any?>(
        Field.STATUS to status.wire,
        Field.JOINED_AT to Timestamp.now()
    )

    // ---- model -> Room ----
    fun toEntity(session: Session, cachedAtMillis: Long): SessionEntity = SessionEntity(
        id = session.id,
        communityId = session.communityId,
        hostUid = session.hostUid,
        courseId = session.courseId,
        courseName = session.courseName,
        courseCategory = session.courseCategory.wire,
        tagType = session.tagType.wire,
        expectationLevel = session.expectationLevel.wire,
        title = session.title,
        description = session.description,
        goals = session.goals,
        locationName = session.locationName,
        lat = session.lat,
        lng = session.lng,
        startTimeMillis = session.startTimeMillis,
        endTimeMillis = session.endTimeMillis,
        capacity = session.capacity,
        joinedCount = session.joinedCount,
        memberUids = session.memberUids,
        mode = session.mode.wire,
        status = session.status.wire,
        materialUrls = session.materialUrls,
        cachedAtMillis = cachedAtMillis
    )

    fun toMySessionEntity(session: Session, cachedAtMillis: Long): MySessionEntity = MySessionEntity(
        sessionId = session.id,
        title = session.title,
        courseName = session.courseName,
        locationName = session.locationName,
        tagType = session.tagType.wire,
        startTimeMillis = session.startTimeMillis,
        endTimeMillis = session.endTimeMillis,
        status = session.status.wire,
        cachedAtMillis = cachedAtMillis
    )

    fun toEntity(profile: UserProfile): ProfileEntity = ProfileEntity(
        uid = profile.uid,
        name = profile.name,
        studentId = profile.studentId,
        communityId = profile.communityId,
        department = profile.department,
        major = profile.major,
        admissionYear = profile.admissionYear,
        bio = profile.bio,
        photoUrl = profile.photoUrl
    )

    fun toEntity(community: Community): CommunityEntity = CommunityEntity(
        id = community.id,
        name = community.name,
        city = community.city,
        imageUrl = community.imageUrl,
        verified = community.verified,
        domainWhitelist = community.domainWhitelist
    )

    // ---- Room -> model ----
    fun toModel(entity: SessionEntity): Session = Session(
        id = entity.id,
        communityId = entity.communityId,
        hostUid = entity.hostUid,
        courseId = entity.courseId,
        courseName = entity.courseName,
        courseCategory = CourseCategory.from(entity.courseCategory) ?: CourseCategory.OTHER,
        tagType = TagType.from(entity.tagType) ?: TagType.NORMAL,
        expectationLevel = ExpectationLevel.from(entity.expectationLevel) ?: ExpectationLevel.PASS,
        title = entity.title,
        description = entity.description,
        goals = entity.goals,
        locationName = entity.locationName,
        lat = entity.lat,
        lng = entity.lng,
        startTimeMillis = entity.startTimeMillis,
        endTimeMillis = entity.endTimeMillis,
        capacity = entity.capacity,
        joinedCount = entity.joinedCount,
        memberUids = entity.memberUids,
        mode = SessionMode.from(entity.mode) ?: SessionMode.OPEN,
        status = SessionStatus.from(entity.status) ?: SessionStatus.UPCOMING,
        materialUrls = entity.materialUrls,
    )

    fun toModel(entity: ProfileEntity): UserProfile = UserProfile(
        uid = entity.uid,
        name = entity.name,
        studentId = entity.studentId,
        communityId = entity.communityId,
        department = entity.department,
        major = entity.major,
        admissionYear = entity.admissionYear,
        bio = entity.bio,
        photoUrl = entity.photoUrl
    )
    
    fun toModel(entity: CommunityEntity): Community = Community(
        id = entity.id,
        name = entity.name,
        city = entity.city,
        imageUrl = entity.imageUrl,
        verified = entity.verified,
        domainWhitelist = entity.domainWhitelist
    )
}
