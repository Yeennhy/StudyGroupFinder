package com.studyfinder.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.StudyFinderApp
import com.studyfinder.app.data.remote.firestore.FirestoreMappers
import com.studyfinder.app.data.remote.firestore.FirestoreRefs
import com.studyfinder.app.data.remote.firestore.FirestoreRefs.Field
import com.studyfinder.app.data.remote.supabase.SupabaseClientProvider
import com.studyfinder.app.model.BusyInterval
import com.studyfinder.app.model.CourseCategory
import com.studyfinder.app.model.ExpectationLevel
import com.studyfinder.app.model.MemberStatus
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionMember
import com.studyfinder.app.model.SessionStatus
import com.studyfinder.app.model.TagType
import com.studyfinder.app.notification.ReminderWorker
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.Result
import com.studyfinder.app.util.UiState
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Sessions, membership and every transaction in §3.1.
 */
class SessionRepository {

    private val db = FirebaseFirestore.getInstance()
    private val sessionDao = ServiceLocator.database.sessionDao()
    private val mySessionDao = ServiceLocator.database.mySessionDao()
    private val auth = FirebaseAuth.getInstance()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)

    // ---------------------------------------------------------------- reads

    /**
     * Home's list (§7.2).
     */
    fun observeCommunitySessions(
        communityId: String,
        courseIdQuery: String? = null,
        tagType: TagType? = null,
        courseCategory: CourseCategory? = null,
        expectationLevel: ExpectationLevel? = null,
    ): Flow<UiState<List<Session>>> = callbackFlow {
        var query: Query = FirestoreRefs.sessions()
            .whereEqualTo(Field.COMMUNITY_ID, communityId)

        if (!courseIdQuery.isNullOrBlank()) {
            query = query.whereEqualTo(Field.COURSE_ID, courseIdQuery)
        }
        if (tagType != null) {
            query = query.whereEqualTo(Field.TAG_TYPE, tagType.wire)
        }
        if (courseCategory != null) {
            query = query.whereEqualTo(Field.COURSE_CATEGORY, courseCategory.wire)
        }
        if (expectationLevel != null) {
            query = query.whereEqualTo(Field.EXPECTATION_LEVEL, expectationLevel.wire)
        }

        // Apply sort AFTER all equality filters
        query = query.orderBy(Field.START_TIME, Query.Direction.ASCENDING)

        var sawServer = false
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(UiState.Error(error.message ?: "Fetch failed", error))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val now = System.currentTimeMillis()
                val sessions = snapshot.documents.mapNotNull { FirestoreMappers.toSession(it) }
                
                // §Lazy Finishing: If we see an UPCOMING session that has ended, trigger a DB update.
                // This works for any user because of the updated Security Rules.
                sessions.forEach { session ->
                    if (session.status == SessionStatus.UPCOMING && session.isPast(now)) {
                        scope.launch { finishSession(session.id) }
                    }
                }

                val upcomingOnly = sessions.filter { it.status == SessionStatus.UPCOMING && !it.isPast(now) }
                
                // Cache to Room
                scope.launch {
                    sessionDao.upsertAll(upcomingOnly.map { FirestoreMappers.toEntity(it, now) })
                }

                // Cache-only data *after* we've already seen the server once =
                // the connection dropped (§2.1). The first cold-start cache tick
                // is skipped so the banner doesn't flash on every open.
                val fromCache = snapshot.metadata.isFromCache
                if (!fromCache) sawServer = true
                if (fromCache && sawServer) {
                    trySend(UiState.Offline(sessions))
                } else {
                    trySend(UiState.Success(sessions))
                }
            }
        }
        awaitClose { listener.remove() }
    }.onStart { emit(UiState.Loading) }

    /**
     * Live updates while Session Detail is open (§7.3).
     */
    fun observeSession(sessionId: String): Flow<UiState<Session>> = callbackFlow {
        var sawServer = false
        val listener = FirestoreRefs.session(sessionId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(UiState.Error(error.message ?: "Fetch failed", error))
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val session = FirestoreMappers.toSession(snapshot)
                if (session != null) {
                    // §Lazy Finishing: Trigger update if user opens an expired session
                    val now = System.currentTimeMillis()
                    if (session.status == SessionStatus.UPCOMING && session.isPast(now)) {
                        scope.launch { finishSession(session.id) }
                    }

                    val fromCache = snapshot.metadata.isFromCache
                    if (!fromCache) sawServer = true
                    if (fromCache && sawServer) trySend(UiState.Offline(session))
                    else trySend(UiState.Success(session))
                } else {
                    trySend(UiState.Error("Failed to parse session"))
                }
            } else {
                trySend(UiState.Error("Session not found"))
            }
        }
        awaitClose { listener.remove() }
    }.onStart { emit(UiState.Loading) }

    fun observeMembers(sessionId: String): Flow<UiState<List<SessionMember>>> = callbackFlow {
        val listener = FirestoreRefs.members(sessionId)
            .orderBy(Field.JOINED_AT, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(UiState.Error(error.message ?: "Fetch failed", error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val members = snapshot.documents.mapNotNull { FirestoreMappers.toSessionMember(it) }
                    
                    // Parallel profile fetching to avoid waterfall lag (§7.5)
                    scope.launch {
                        try {
                            val membersWithProfiles = coroutineScope {
                                members.map { member ->
                                    async {
                                        val profileDoc = FirestoreRefs.user(member.uid).get().await()
                                        member.copy(profile = FirestoreMappers.toUserProfile(profileDoc))
                                    }
                                }.awaitAll()
                            }
                            trySend(UiState.Success(membersWithProfiles))
                        } catch (e: Exception) {
                            trySend(UiState.Error("Profile sync failed", e))
                        }
                    }
                }
            }
        awaitClose { listener.remove() }
    }.onStart { emit(UiState.Loading) }

    fun observeMyMembership(sessionId: String): Flow<SessionMember?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = FirestoreRefs.member(sessionId, uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                trySend(FirestoreMappers.toSessionMember(snapshot))
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    fun observeUserSessions(uid: String, includeCancelled: Boolean = false): Flow<UiState<List<Session>>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(UiState.Success(emptyList()))
            close()
            return@callbackFlow
        }

        var sawServer = false
        val listener = FirestoreRefs.sessions()
            .whereArrayContains(Field.MEMBER_UIDS, uid)
            .orderBy(Field.START_TIME, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(UiState.Error(error.message ?: "Fetch failed", error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val now = System.currentTimeMillis()
                    var sessions = snapshot.documents.mapNotNull { FirestoreMappers.toSession(it) }

                    // §Lazy Finishing: Update user's own joined sessions if they are past due
                    sessions.forEach { session ->
                        if (session.status == SessionStatus.UPCOMING && session.isPast(now)) {
                            scope.launch { finishSession(session.id) }
                        }
                    }

                    if (!includeCancelled) {
                        sessions = sessions.filter { it.status != SessionStatus.CANCELLED }
                    }

                    // Cache to Room if it's current user
                    if (uid == auth.currentUser?.uid) {
                        val now = System.currentTimeMillis()
                        scope.launch {
                            mySessionDao.clear() // Clear old cache to remove "ghost" sessions
                            mySessionDao.upsertAll(sessions.map { FirestoreMappers.toMySessionEntity(it, now) })
                        }
                    }

                    val fromCache = snapshot.metadata.isFromCache
                    if (!fromCache) sawServer = true
                    if (fromCache && sawServer) trySend(UiState.Offline(sessions))
                    else trySend(UiState.Success(sessions))
                }
            }
        awaitClose { listener.remove() }
    }.onStart { emit(UiState.Loading) }

    fun observeMySessions(includeCancelled: Boolean = false): Flow<UiState<List<Session>>> {
        val uid = auth.currentUser?.uid ?: return flow { emit(UiState.Error("User not signed in")) }
        return observeUserSessions(uid, includeCancelled)
    }

    fun observePendingRequests(sessionId: String): Flow<UiState<List<SessionMember>>> = callbackFlow {
        val listener = FirestoreRefs.members(sessionId)
            .whereEqualTo(Field.STATUS, MemberStatus.PENDING.wire)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(UiState.Error(error.message ?: "Fetch failed", error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val members = snapshot.documents.mapNotNull { FirestoreMappers.toSessionMember(it) }
                    
                    // Parallel profile fetching to avoid waterfall lag (§7.5)
                    scope.launch {
                        try {
                            val membersWithProfiles = coroutineScope {
                                members.map { member ->
                                    async {
                                        val profileDoc = FirestoreRefs.user(member.uid).get().await()
                                        member.copy(profile = FirestoreMappers.toUserProfile(profileDoc))
                                    }
                                }.awaitAll()
                            }
                            trySend(UiState.Success(membersWithProfiles))
                        } catch (e: Exception) {
                            trySend(UiState.Error("Profile sync failed", e))
                        }
                    }
                }
            }
        awaitClose { listener.remove() }
    }.onStart { emit(UiState.Loading) }

    suspend fun getBusyIntervals(): List<BusyInterval> {
        val sessions = observeMySessions().first()
        return if (sessions is UiState.Success) {
            sessions.data.map { 
                BusyInterval(it.startTimeMillis, it.endTimeMillis, it.title)
            }
        } else {
            emptyList()
        }
    }

    // ------------------------------------------------ reminder scheduling (§8)

    /** Default lead time before a session's start — the dev plan leaves the
     *  exact figure open (§8). */
    private val reminderLeadMillis = 15 * 60 * 1000L

    /**
     * Best-effort: schedule a WorkManager reminder. If the session is passed,
     * we skip fetching it from network if we already have the model.
     */
    private suspend fun scheduleReminder(sessionId: String) {
        try {
            val snap = FirestoreRefs.session(sessionId).get().await()
            val session = FirestoreMappers.toSession(snap) ?: return
            scheduleReminder(session)
        } catch (_: Exception) {}
    }

    private fun scheduleReminder(session: Session) {
        try {
            if (session.status != SessionStatus.UPCOMING) return
            val delay = session.startTimeMillis - reminderLeadMillis - System.currentTimeMillis()
            ReminderWorker.schedule(StudyFinderApp.instance, session.id, delay, session.title)
        } catch (_: Exception) {}
    }

    private fun cancelReminder(sessionId: String) {
        try {
            ReminderWorker.cancel(StudyFinderApp.instance, sessionId)
        } catch (_: Exception) {
        }
    }

    // -------------------------------------------------------- transactions

    suspend fun joinOpenSession(sessionId: String): ActionResult = performMembershipChange(sessionId) {
        val uid = auth.currentUser?.uid ?: return@performMembershipChange ActionResult.Failure("Not signed in")
        val sessionRef = FirestoreRefs.session(sessionId)
        val memberRef = FirestoreRefs.member(sessionId, uid)

        db.runTransaction { transaction ->
            val sessionDoc = transaction.get(sessionRef)
            val members = (sessionDoc.get(Field.MEMBER_UIDS) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val joinedCount = sessionDoc.getLong(Field.JOINED_COUNT) ?: 0
            val capacity = sessionDoc.getLong(Field.CAPACITY) ?: 0
            
            if (joinedCount >= capacity && !members.contains(uid)) throw Exception("Session is full")
            
            if (!members.contains(uid)) {
                transaction.update(sessionRef, 
                    Field.JOINED_COUNT, FieldValue.increment(1),
                    Field.MEMBER_UIDS, FieldValue.arrayUnion(uid),
                    Field.UPDATED_AT, FieldValue.serverTimestamp()
                )
            }
            transaction.set(memberRef, FirestoreMappers.memberPayload(MemberStatus.ACCEPTED))
        }.await()
        scheduleReminder(sessionId)
        ActionResult.Success
    }

    suspend fun requestToJoin(sessionId: String): ActionResult = try {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        val memberRef = FirestoreRefs.member(sessionId, uid)
        memberRef.set(FirestoreMappers.memberPayload(MemberStatus.PENDING)).await()
        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Request failed", e)
    }

    suspend fun cancelJoinRequest(sessionId: String): ActionResult = try {
        val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
        FirestoreRefs.member(sessionId, uid).delete().await()
        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Cancel failed", e)
    }

    suspend fun acceptInvite(sessionId: String): ActionResult = performMembershipChange(sessionId) {
        val uid = auth.currentUser?.uid ?: return@performMembershipChange ActionResult.Failure("Not signed in")
        val sessionRef = FirestoreRefs.session(sessionId)
        val memberRef = FirestoreRefs.member(sessionId, uid)

        db.runTransaction { transaction ->
            val sessionDoc = transaction.get(sessionRef)
            val members = (sessionDoc.get(Field.MEMBER_UIDS) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val joinedCount = sessionDoc.getLong(Field.JOINED_COUNT) ?: 0
            val capacity = sessionDoc.getLong(Field.CAPACITY) ?: 0
            
            if (joinedCount >= capacity && !members.contains(uid)) throw Exception("Session is full")

            if (!members.contains(uid)) {
                transaction.update(sessionRef, 
                    Field.JOINED_COUNT, FieldValue.increment(1),
                    Field.MEMBER_UIDS, FieldValue.arrayUnion(uid),
                    Field.UPDATED_AT, FieldValue.serverTimestamp()
                )
            }
            transaction.update(memberRef, Field.STATUS, MemberStatus.ACCEPTED.wire)
        }.await()
        scheduleReminder(sessionId)
        ActionResult.Success
    }

    suspend fun approveRequest(sessionId: String, uid: String): ActionResult = try {
        val sessionRef = FirestoreRefs.session(sessionId)
        val memberRef = FirestoreRefs.member(sessionId, uid)

        db.runTransaction { transaction ->
            val sessionDoc = transaction.get(sessionRef)
            val members = (sessionDoc.get(Field.MEMBER_UIDS) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val joinedCount = sessionDoc.getLong(Field.JOINED_COUNT) ?: 0
            val capacity = sessionDoc.getLong(Field.CAPACITY) ?: 0
            
            if (joinedCount >= capacity && !members.contains(uid)) throw Exception("Session is full")

            if (!members.contains(uid)) {
                transaction.update(sessionRef, 
                    Field.JOINED_COUNT, FieldValue.increment(1),
                    Field.MEMBER_UIDS, FieldValue.arrayUnion(uid),
                    Field.UPDATED_AT, FieldValue.serverTimestamp()
                )
            }
            transaction.update(memberRef, Field.STATUS, MemberStatus.ACCEPTED.wire)
        }.await()
        
        // Notify user via inbox in background
        scope.launch {
            ServiceLocator.inboxRepository.fanOutSystemMessage(sessionId, listOf(uid), "Your request to join has been approved!")
        }
        scheduleReminder(sessionId)

        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Approval failed", e)
    }

    suspend fun rejectRequest(sessionId: String, uid: String): ActionResult = try {
        FirestoreRefs.member(sessionId, uid).delete().await()
        
        // Notify user about rejection in background
        scope.launch {
            try {
                val sessionDoc = FirestoreRefs.session(sessionId).get().await()
                val session = FirestoreMappers.toSession(sessionDoc)
                val title = session?.title ?: "a session"
                val item = com.studyfinder.app.model.InboxItem(
                    type = com.studyfinder.app.model.InboxType.SYSTEM,
                    sessionId = sessionId,
                    fromUid = auth.currentUser?.uid,
                    message = "Your request to join \"$title\" was not accepted."
                )
                val inboxRef = FirestoreRefs.inbox(uid).document()
                inboxRef.set(FirestoreMappers.inboxPayload(item, auth.currentUser?.uid ?: "")).await()
            } catch (e: Exception) {
                // Background notification failure shouldn't crash the app
            }
        }

        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Rejection failed", e)
    }

    suspend fun leaveOrRemove(sessionId: String, uid: String): ActionResult = try {
        val sessionRef = FirestoreRefs.session(sessionId)
        val memberRef = FirestoreRefs.member(sessionId, uid)
        val isSelf = uid == auth.currentUser?.uid

        db.runTransaction { transaction ->
            val sessionDoc = transaction.get(sessionRef)
            val members = (sessionDoc.get(Field.MEMBER_UIDS) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val memberDoc = transaction.get(memberRef)
            val status = memberDoc.getString(Field.STATUS)
            
            // Only decrement if they were actually in the roster
            if (members.contains(uid)) {
                transaction.update(sessionRef, 
                    Field.JOINED_COUNT, FieldValue.increment(-1),
                    Field.MEMBER_UIDS, FieldValue.arrayRemove(uid),
                    Field.UPDATED_AT, FieldValue.serverTimestamp()
                )
            }
            transaction.delete(memberRef)
        }.await()

        if (!isSelf) {
            ServiceLocator.inboxRepository.fanOutSystemMessage(sessionId, listOf(uid), "You have been removed from the session.")
        } else {
            // My own reminder is no longer wanted (§8).
            cancelReminder(sessionId)
        }

        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Operation failed", e)
    }

    // ------------------------------------------------------------ host CRUD

    suspend fun createSession(session: Session): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
            val sessionRef = FirestoreRefs.sessions().document()
            val memberRef = FirestoreRefs.member(sessionRef.id, uid)
            
            // Optimistic Create (§7.4): We commit the batch and return Success immediately.
            // Firestore handles the queueing and local cache update, making the transition
            // to the Success screen feel instant even on slow networks.
            db.batch().apply {
                set(sessionRef, FirestoreMappers.sessionCreatePayload(session, uid))
                set(memberRef, FirestoreMappers.memberPayload(MemberStatus.ADMIN))
            }.commit() // No await() here for snappiness

            // Schedule reminder immediately using the local data
            val createdSession = session.copy(id = sessionRef.id)
            scheduleReminder(createdSession)
            
            Result.Success(sessionRef.id)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Creation failed", e)
        }
    }

    suspend fun editSession(session: Session): ActionResult = try {
        val sessionRef = FirestoreRefs.session(session.id)
        sessionRef.update(FirestoreMappers.sessionEditPayload(session)).await()
        
        // Notify members - Background Fan-out (§7.5)
        val memberUids = session.memberUids.filter { it != auth.currentUser?.uid }
        if (memberUids.isNotEmpty()) {
            scope.launch {
                ServiceLocator.inboxRepository.fanOutSystemMessage(
                    session.id, 
                    memberUids, 
                    "Details for \"${session.title}\" have been updated."
                )
            }
        }
        
        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Update failed", e)
    }

    suspend fun cancelSession(sessionId: String): ActionResult = try {
        val sessionRef = FirestoreRefs.session(sessionId)
        val snapshot = sessionRef.get().await()
        val session = FirestoreMappers.toSession(snapshot) ?: throw Exception("Session not found")
        
        sessionRef.update(Field.STATUS, SessionStatus.CANCELLED.wire).await()
        cancelReminder(sessionId)

        // Notify members - Background Fan-out (§7.5)
        val memberUids = session.memberUids.filter { it != auth.currentUser?.uid }
        if (memberUids.isNotEmpty()) {
            scope.launch {
                ServiceLocator.inboxRepository.fanOutSystemMessage(
                    sessionId, 
                    memberUids, 
                    "The session \"${session.title}\" has been cancelled by the host."
                )
            }
        }
        
        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Cancellation failed", e)
    }

    suspend fun finishSession(sessionId: String): ActionResult = try {
        android.util.Log.d("SessionRepo", "Auto-finishing session: $sessionId")
        FirestoreRefs.session(sessionId).update(Field.STATUS, SessionStatus.FINISHED.wire).await()
        ActionResult.Success
    } catch (e: Exception) {
        android.util.Log.e("SessionRepo", "Auto-finish failed for $sessionId: ${e.message}")
        ActionResult.Failure(e.message ?: "Operation failed", e)
    }

    suspend fun attachMaterial(sessionId: String, materialUrl: String): ActionResult = try {
        FirestoreRefs.session(sessionId).update(
            Field.MATERIAL_URLS, FieldValue.arrayUnion(materialUrl),
            Field.UPDATED_AT, FieldValue.serverTimestamp()
        ).await()
        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Attachment failed", e)
    }

    suspend fun deleteMaterial(sessionId: String, materialUrl: String): ActionResult = try {
        // 1. Remove from Firestore first so it vanishes from the UI immediately
        FirestoreRefs.session(sessionId).update(
            Field.MATERIAL_URLS, FieldValue.arrayRemove(materialUrl),
            Field.UPDATED_AT, FieldValue.serverTimestamp()
        ).await()

        // 2. Attempt to delete from Supabase Storage
        try {
            val bucket = SupabaseClientProvider.client.storage.from("materials")
            // Path is the part after "/public/materials/"
            val path = materialUrl.substringAfter("/public/materials/")
            if (path != materialUrl) {
                bucket.delete(path)
            }
        } catch (e: Exception) {
            // Log Supabase failure but don't fail the whole action since Firestore is already updated
            android.util.Log.e("SessionRepo", "Supabase file delete failed: ${e.message}")
        }

        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Deletion failed", e)
    }

    suspend fun uploadToSupabase(bytes: ByteArray, fileName: String, sessionId: String): ActionResult {
        return try {
            val bucket = SupabaseClientProvider.client.storage.from("materials")
            val path = "$sessionId/$fileName"
            bucket.upload(path, bytes)
            val publicUrl = bucket.publicUrl(path)
            attachMaterial(sessionId, publicUrl)
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Upload failed", e)
        }
    }

    suspend fun inviteAllFrom(previousSessionId: String, newSessionId: String): ActionResult {
        return try {
            val oldSessionDoc = FirestoreRefs.session(previousSessionId).get().await()
            val oldMemberUids = (oldSessionDoc.get(Field.MEMBER_UIDS) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val currentUid = auth.currentUser?.uid
            
            val toInvite = oldMemberUids.filter { it != currentUid }
            if (toInvite.isEmpty()) return ActionResult.Success

            // Use a batch for efficiency (§7.5)
            val batch = db.batch()
            toInvite.forEach { uid ->
                // 1. Create membership doc
                val memberRef = FirestoreRefs.member(newSessionId, uid)
                batch.set(memberRef, FirestoreMappers.memberPayload(MemberStatus.INVITED))
                
                // 2. Add inbox item
                val inboxRef = FirestoreRefs.inbox(uid).document()
                val item = com.studyfinder.app.model.InboxItem(
                    type = com.studyfinder.app.model.InboxType.INVITE,
                    sessionId = newSessionId,
                    fromUid = currentUid ?: "",
                    message = "You have been invited to join a study session!"
                )
                batch.set(inboxRef, FirestoreMappers.inboxPayload(item, currentUid ?: ""))
            }
            
            batch.commit().await()
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Invites failed", e)
        }
    }

    fun splitByTime(
        sessions: List<Session>,
        nowMillis: Long = System.currentTimeMillis(),
    ): Pair<List<Session>, List<Session>> {
        return sessions.partition { it.endTimeMillis >= nowMillis }
    }

    fun isCancelled(session: Session): Boolean = session.status == SessionStatus.CANCELLED

    private suspend fun performMembershipChange(sessionId: String, block: suspend () -> ActionResult): ActionResult {
        return try {
            block()
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Operation failed", e)
        }
    }
}
