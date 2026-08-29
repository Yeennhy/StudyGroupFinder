package com.studyfinder.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.data.remote.firestore.FirestoreMappers
import com.studyfinder.app.data.remote.firestore.FirestoreRefs
import com.studyfinder.app.data.remote.firestore.FirestoreRefs.Field
import com.studyfinder.app.data.remote.rest.CommunityRestMapper
import com.studyfinder.app.data.remote.rest.RetrofitClient
import com.studyfinder.app.model.CampusLocation
import com.studyfinder.app.model.Community
import com.studyfinder.app.model.Course
import com.studyfinder.app.model.CourseCategory
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await

/**
 * Community selection (§7.1) — the one screen with two data sources.
 */
class CommunityRepository {

    private val communityDao = ServiceLocator.database.communityDao()

    /**
     * Initial "browse all" list, fetched over **Retrofit/REST**, not the SDK.
     * This is the course's external-API requirement; do not quietly swap it
     * for a Firestore query (§7.1).
     */
    fun observeAllViaRest(): Flow<UiState<List<Community>>> = flow<UiState<List<Community>>> {
        val response = RetrofitClient.publicCommunityApi.listCommunities()
        val communities = CommunityRestMapper.toCommunities(response)
        
        // Cache to Room
        communityDao.upsertAll(communities.map { FirestoreMappers.toEntity(it) })
        
        emit(UiState.Success(communities))
    }.onStart {
        emit(UiState.Loading)
    }.catch { e ->
        emit(UiState.Error(e.message ?: "Failed to fetch communities", e))
    }

    /** Search / filter as you type goes through the SDK, which is faster here. */
    fun searchByCity(city: String): Flow<UiState<List<Community>>> = flow<UiState<List<Community>>> {
        val snapshot = FirestoreRefs.communities()
            .whereEqualTo(Field.CITY, city)
            .get()
            .await()
        
        val communities = snapshot.documents.mapNotNull { FirestoreMappers.toCommunity(it) }
        emit(UiState.Success(communities))
    }.onStart {
        emit(UiState.Loading)
    }.catch { e ->
        emit(UiState.Error(e.message ?: "Search failed", e))
    }

    fun searchByName(query: String): Flow<UiState<List<Community>>> = flow<UiState<List<Community>>> {
        // Simple case-insensitive search (startWith)
        val snapshot = FirestoreRefs.communities()
            .whereGreaterThanOrEqualTo(Field.NAME, query)
            .whereLessThanOrEqualTo(Field.NAME, query + "\uf8ff")
            .get()
            .await()
        
        val communities = snapshot.documents.mapNotNull { FirestoreMappers.toCommunity(it) }
        emit(UiState.Success(communities))
    }.onStart {
        emit(UiState.Loading)
    }.catch { e ->
        emit(UiState.Error(e.message ?: "Search failed", e))
    }

    /**
     * Checks the email domain against the whitelist before writing
     * `communityId` onto the user doc; fails with a readable message when a
     * verified community rejects the domain (§7.1).
     */
    suspend fun joinCommunity(communityId: String): ActionResult {
        return try {
            val communityDoc = FirestoreRefs.community(communityId).get().await()
            val community = FirestoreMappers.toCommunity(communityDoc) 
                ?: return ActionResult.Failure("Community not found")

            val user = FirebaseAuth.getInstance().currentUser
            if (community.verified && !community.acceptsEmail(user?.email)) {
                return ActionResult.Failure("Email domain not allowed for this community")
            }

            val uid = user?.uid ?: return ActionResult.Failure("User not signed in")
            FirestoreRefs.user(uid).update(Field.COMMUNITY_ID, communityId).await()
            
            ActionResult.Success
        } catch (e: Exception) {
            ActionResult.Failure(e.message ?: "Failed to join community", e)
        }
    }

    /** Seeded per community; drives the Create Session dropdowns (§7.4). */
    suspend fun coursesFor(communityId: String): List<Course> {
        val snapshot = FirestoreRefs.community(communityId).collection("courses").get().await()
        return snapshot.documents.map { doc ->
            Course(
                id = doc.id,
                name = doc.getString("name").orEmpty(),
                category = CourseCategory.from(doc.getString("category")) ?: CourseCategory.OTHER
            )
        }
    }

    /** Predefined campus locations, so sessions get lat/lng without geocoding. */
    suspend fun locationsFor(communityId: String): List<CampusLocation> {
        val snapshot = FirestoreRefs.community(communityId).collection("locations").get().await()
        return snapshot.documents.map { doc ->
            CampusLocation(
                id = doc.id,
                name = doc.getString("name").orEmpty(),
                lat = doc.getDouble("lat") ?: 0.0,
                lng = doc.getDouble("lng") ?: 0.0
            )
        }
    }
}
