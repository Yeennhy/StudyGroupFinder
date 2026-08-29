package com.studyfinder.app.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.data.remote.firestore.FirestoreMappers
import com.studyfinder.app.data.remote.firestore.FirestoreRefs
import com.studyfinder.app.data.remote.firestore.FirestoreRefs.Field
import com.studyfinder.app.model.UserProfile
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Profile, photo upload, block list and the activity graph (§7.7).
 */
class ProfileRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val profileDao = ServiceLocator.database.profileDao()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeCurrentProfile(): Flow<UiState<UserProfile>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(UiState.Error("User not signed in"))
            close()
            return@callbackFlow
        }

        val listener = FirestoreRefs.user(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(UiState.Error(error.message ?: "Fetch failed", error))
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = FirestoreMappers.toUserProfile(snapshot)
                if (profile != null) {
                    // Sync to Room
                    scope.launch {
                        profileDao.upsert(FirestoreMappers.toEntity(profile))
                    }
                    trySend(UiState.Success(profile))
                }
            }
        }
        awaitClose { listener.remove() }
    }.onStart { emit(UiState.Loading) }

    fun observeProfile(uid: String): Flow<UiState<UserProfile>> = callbackFlow {
        val listener = FirestoreRefs.user(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(UiState.Error(error.message ?: "Fetch failed", error))
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = FirestoreMappers.toUserProfile(snapshot)
                if (profile != null) {
                    trySend(UiState.Success(profile))
                }
            } else {
                trySend(UiState.Error("Profile not found"))
            }
        }
        awaitClose { listener.remove() }
    }.onStart { emit(UiState.Loading) }

    suspend fun updateProfile(profile: UserProfile): ActionResult {
        return try {
            val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
            FirestoreRefs.user(uid).update(FirestoreMappers.profilePayload(profile)).await()
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Update failed", e)
        }
    }

    suspend fun uploadProfilePhoto(localUri: Uri): ActionResult {
        return try {
            val uid = auth.currentUser?.uid ?: throw Exception("Not signed in")
            val ref = storage.reference.child("users/$uid/profile/avatar.jpg")
            
            ref.putFile(localUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            
            FirestoreRefs.user(uid).update(Field.PHOTO_URL, downloadUrl).await()
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Upload failed", e)
        }
    }

    suspend fun findByStudentId(studentId: String): List<UserProfile> {
        val snapshot = FirestoreRefs.users()
            .whereEqualTo(Field.STUDENT_ID, studentId)
            .get()
            .await()
        return snapshot.documents.mapNotNull { FirestoreMappers.toUserProfile(it) }
    }

    fun observeActivityByDate(): Flow<Map<LocalDate, Int>> {
        return ServiceLocator.sessionRepository.observeMySessions().map { state ->
            if (state is UiState.Success) {
                state.data.groupBy { 
                    Instant.ofEpochMilli(it.startTimeMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }.mapValues { it.value.size }
            } else {
                emptyMap()
            }
        }
    }

    // ------------------------------------------------------------ block list

    fun observeBlockedUids(): Flow<Set<String>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }

        val listener = FirestoreRefs.blocked(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                trySend(snapshot.documents.map { it.id }.toSet())
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun blockUser(uid: String): ActionResult {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not signed in")
            FirestoreRefs.blocked(myUid).document(uid).set(mapOf(Field.CREATED_AT to com.google.firebase.Timestamp.now())).await()
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Block failed", e)
        }
    }

    suspend fun unblockUser(uid: String): ActionResult {
        return try {
            val myUid = auth.currentUser?.uid ?: throw Exception("Not signed in")
            FirestoreRefs.blocked(myUid).document(uid).delete().await()
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Unblock failed", e)
        }
    }
}
