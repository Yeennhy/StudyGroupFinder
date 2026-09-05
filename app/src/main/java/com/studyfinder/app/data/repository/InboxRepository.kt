package com.studyfinder.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.studyfinder.app.data.remote.firestore.FirestoreMappers
import com.studyfinder.app.data.remote.firestore.FirestoreRefs
import com.studyfinder.app.data.remote.firestore.FirestoreRefs.Field
import com.studyfinder.app.model.InboxItem
import com.studyfinder.app.model.InboxType
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await

class InboxRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun observeInbox(): Flow<UiState<List<InboxItem>>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(UiState.Error("User not signed in"))
            close()
            return@callbackFlow
        }

        var sawServer = false
        val listener = FirestoreRefs.inbox(uid)
            .orderBy(Field.CREATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(UiState.Error(error.message ?: "Fetch failed", error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { FirestoreMappers.toInboxItem(it) }
                    val fromCache = snapshot.metadata.isFromCache
                    if (!fromCache) sawServer = true
                    if (fromCache && sawServer) {
                        trySend(UiState.Offline(items))
                    } else {
                        trySend(UiState.Success(items))
                    }
                }
            }
        awaitClose { listener.remove() }
    }.onStart { emit(UiState.Loading) }

    suspend fun markRead(itemId: String): ActionResult {
        return try {
            val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
            FirestoreRefs.inbox(uid).document(itemId).update(Field.READ, true).await()
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Update failed", e)
        }
    }

    /** Host invites someone by student ID. */
    suspend fun sendInvite(toUid: String, sessionId: String): ActionResult {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not signed in")
            val item = InboxItem(
                type = InboxType.INVITE,
                sessionId = sessionId,
                fromUid = myUid,
                message = "You have been invited to join a study session!"
            )
            FirestoreRefs.inbox(toUid).add(FirestoreMappers.inboxPayload(item, myUid)).await()
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Invite failed", e)
        }
    }

    /** Someone asks to join a gated session; the host is told. */
    suspend fun sendJoinRequestNotice(toHostUid: String, sessionId: String): ActionResult {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not signed in")
            val item = InboxItem(
                type = InboxType.JOIN_REQUEST,
                sessionId = sessionId,
                fromUid = myUid,
                message = "Someone has requested to join your session."
            )
            FirestoreRefs.inbox(toHostUid).add(FirestoreMappers.inboxPayload(item, myUid)).await()
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Notice failed", e)
        }
    }

    suspend fun fanOutSystemMessage(
        sessionId: String,
        recipientUids: List<String>,
        message: String,
    ): ActionResult {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not signed in")
            val batch = db.batch()
            
            recipientUids.filter { it != myUid }.forEach { uid ->
                val item = InboxItem(
                    type = InboxType.SYSTEM,
                    sessionId = sessionId,
                    fromUid = myUid,
                    message = message
                )
                val ref = FirestoreRefs.inbox(uid).document()
                batch.set(ref, FirestoreMappers.inboxPayload(item, myUid))
            }
            
            batch.commit().await()
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Fan-out failed", e)
        }
    }
}
