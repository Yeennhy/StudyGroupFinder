package com.studyfinder.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.data.remote.firestore.FirestoreMappers
import com.studyfinder.app.data.remote.firestore.FirestoreRefs
import com.studyfinder.app.data.remote.firestore.FirestoreRefs.Field
import com.studyfinder.app.model.BusyInterval
import com.studyfinder.app.model.CourseCategory
import com.studyfinder.app.model.MemberStatus
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionMember
import com.studyfinder.app.model.SessionStatus
import com.studyfinder.app.model.TagType
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.Result
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
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
    ): Flow<UiState<List<Session>>> = callbackFlow {
        var query: Query = FirestoreRefs.sessions()
            .whereEqualTo(Field.COMMUNITY_ID, communityId)
            .orderBy(Field.START_TIME, Query.Direction.ASCENDING)

        if (!courseIdQuery.isNullOrBlank()) {
            query = query.whereEqualTo(Field.COURSE_ID, courseIdQuery)
        }
        if (tagType != null) {
            query = query.whereEqualTo(Field.TAG_TYPE, tagType.wire)
        }
        if (courseCategory != null) {
            query = query.whereEqualTo(Field.COURSE_CATEGORY, courseCategory.wire)
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(UiState.Error(error.message ?: "Fetch failed", error))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val sessions = snapshot.documents.mapNotNull { FirestoreMappers.toSession(it) }
                
                // Cache to Room
                val now = System.currentTimeMillis()
                scope.launch {
                    sessionDao.upsertAll(sessions.map { FirestoreMappers.toEntity(it, now) })
                }
                
                trySend(UiState.Success(sessions))
            }
        }
        awaitClose { listener.remove() }
    }.onStart { emit(UiState.Loading) }

    /**
     * Live updates while Session Detail is open (§7.3).
     */
    fun observeSession(sessionId: String): Flow<UiState<Session>> = callbackFlow {
        val listener = FirestoreRefs.session(sessionId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(UiState.Error(error.message ?: "Fetch failed", error))
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val session = FirestoreMappers.toSession(snapshot)
                if (session != null) {
                    trySend(UiState.Success(session))
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
                    trySend(UiState.Success(members))
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

    fun observeMySessions(): Flow<UiState<List<Session>>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(UiState.Error("User not signed in"))
            close()
            return@callbackFlow
        }

        val listener = FirestoreRefs.sessions()
            .whereArrayContains(Field.MEMBER_UIDS, uid)
            .orderBy(Field.START_TIME, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(UiState.Error(error.message ?: "Fetch failed", error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val sessions = snapshot.documents.mapNotNull { FirestoreMappers.toSession(it) }
                        .filter { it.status != SessionStatus.CANCELLED }
                    
                    // Cache to Room
                    val now = System.currentTimeMillis()
                    scope.launch {
                        mySessionDao.upsertAll(sessions.map { FirestoreMappers.toMySessionEntity(it, now) })
                    }
                    
                    trySend(UiState.Success(sessions))
                }
            }
        awaitClose { listener.remove() }
    }.onStart { emit(UiState.Loading) }

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
                    trySend(UiState.Success(members))
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

    // -------------------------------------------------------- transactions

    suspend fun joinOpenSession(sessionId: String): ActionResult = performMembershipChange(sessionId) {
        val uid = auth.currentUser?.uid ?: return@performMembershipChange ActionResult.Failure("Not signed in")
        val sessionRef = FirestoreRefs.session(sessionId)
        val memberRef = FirestoreRefs.member(sessionId, uid)

        db.runTransaction { transaction ->
            val sessionDoc = transaction.get(sessionRef)
            val joinedCount = sessionDoc.getLong(Field.JOINED_COUNT) ?: 0
            val capacity = sessionDoc.getLong(Field.CAPACITY) ?: 0
            
            if (joinedCount >= capacity) throw Exception("Session is full")
            
            transaction.update(sessionRef, 
                Field.JOINED_COUNT, FieldValue.increment(1),
                Field.MEMBER_UIDS, FieldValue.arrayUnion(uid),
                Field.UPDATED_AT, FieldValue.serverTimestamp()
            )
            transaction.set(memberRef, FirestoreMappers.memberPayload(MemberStatus.ACCEPTED))
        }.await()
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
            val joinedCount = sessionDoc.getLong(Field.JOINED_COUNT) ?: 0
            val capacity = sessionDoc.getLong(Field.CAPACITY) ?: 0
            
            if (joinedCount >= capacity) throw Exception("Session is full")

            transaction.update(sessionRef, 
                Field.JOINED_COUNT, FieldValue.increment(1),
                Field.MEMBER_UIDS, FieldValue.arrayUnion(uid),
                Field.UPDATED_AT, FieldValue.serverTimestamp()
            )
            transaction.update(memberRef, Field.STATUS, MemberStatus.ACCEPTED.wire)
        }.await()
        ActionResult.Success
    }

    suspend fun approveRequest(sessionId: String, uid: String): ActionResult = try {
        val sessionRef = FirestoreRefs.session(sessionId)
        val memberRef = FirestoreRefs.member(sessionId, uid)

        db.runTransaction { transaction ->
            val sessionDoc = transaction.get(sessionRef)
            val joinedCount = sessionDoc.getLong(Field.JOINED_COUNT) ?: 0
            val capacity = sessionDoc.getLong(Field.CAPACITY) ?: 0
            
            if (joinedCount >= capacity) throw Exception("Session is full")

            transaction.update(sessionRef, 
                Field.JOINED_COUNT, FieldValue.increment(1),
                Field.MEMBER_UIDS, FieldValue.arrayUnion(uid),
                Field.UPDATED_AT, FieldValue.serverTimestamp()
            )
            transaction.update(memberRef, Field.STATUS, MemberStatus.ACCEPTED.wire)
        }.await()
        
        // Notify user via inbox
        ServiceLocator.inboxRepository.fanOutSystemMessage(sessionId, listOf(uid), "Your request to join has been approved!")
        
        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Approval failed", e)
    }

    suspend fun rejectRequest(sessionId: String, uid: String): ActionResult = try {
        FirestoreRefs.member(sessionId, uid).delete().await()
        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Rejection failed", e)
    }

    suspend fun leaveOrRemove(sessionId: String, uid: String): ActionResult = try {
        val sessionRef = FirestoreRefs.session(sessionId)
        val memberRef = FirestoreRefs.member(sessionId, uid)
        val isSelf = uid == auth.currentUser?.uid

        db.runTransaction { transaction ->
            transaction.update(sessionRef, 
                Field.JOINED_COUNT, FieldValue.increment(-1),
                Field.MEMBER_UIDS, FieldValue.arrayRemove(uid),
                Field.UPDATED_AT, FieldValue.serverTimestamp()
            )
            transaction.delete(memberRef)
        }.await()

        if (!isSelf) {
            ServiceLocator.inboxRepository.fanOutSystemMessage(sessionId, listOf(uid), "You have been removed from the session.")
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
            
            db.batch().apply {
                set(sessionRef, FirestoreMappers.sessionCreatePayload(session, uid))
                set(memberRef, FirestoreMappers.memberPayload(MemberStatus.ADMIN))
            }.commit().await()
            
            Result.Success(sessionRef.id)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Creation failed", e)
        }
    }

    suspend fun editSession(session: Session): ActionResult = try {
        val sessionRef = FirestoreRefs.session(session.id)
        sessionRef.update(FirestoreMappers.sessionEditPayload(session)).await()
        
        // Notify members
        val memberUids = session.memberUids.filter { it != auth.currentUser?.uid }
        if (memberUids.isNotEmpty()) {
            ServiceLocator.inboxRepository.fanOutSystemMessage(session.id, memberUids, "The session details have been updated.")
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
        
        // Notify members
        val memberUids = session.memberUids.filter { it != auth.currentUser?.uid }
        if (memberUids.isNotEmpty()) {
            ServiceLocator.inboxRepository.fanOutSystemMessage(sessionId, memberUids, "The session has been cancelled by the host.")
        }
        
        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Cancellation failed", e)
    }

    suspend fun finishSession(sessionId: String): ActionResult = try {
        FirestoreRefs.session(sessionId).update(Field.STATUS, SessionStatus.FINISHED.wire).await()
        ActionResult.Success
    } catch (e: Exception) {
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

    suspend fun inviteAllFrom(previousSessionId: String, newSessionId: String): ActionResult = try {
        val oldSessionDoc = FirestoreRefs.session(previousSessionId).get().await()
        val oldMemberUids = (oldSessionDoc.get(Field.MEMBER_UIDS) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val currentUid = auth.currentUser?.uid
        
        val toInvite = oldMemberUids.filter { it != currentUid }
        
        toInvite.forEach { uid ->
            ServiceLocator.inboxRepository.sendInvite(uid, newSessionId)
            FirestoreRefs.member(newSessionId, uid).set(FirestoreMappers.memberPayload(MemberStatus.INVITED)).await()
        }
        
        ActionResult.Success
    } catch (e: Exception) {
        ActionResult.Failure(e.message ?: "Invites failed", e)
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
