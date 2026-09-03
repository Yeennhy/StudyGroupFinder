package com.studyfinder.app.data.remote.rest

import com.studyfinder.app.data.remote.rest.dto.CommunityListResponse
import com.studyfinder.app.model.Community
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The one deliberate REST call in the whole app (§7.1) — this is what
 * satisfies the course's external-API requirement.
 *
 * It hits Firestore's public REST endpoint for the `communities` collection,
 * which is unauthenticated read-only data (§4). Everything else in the app
 * goes through the Firebase SDK instead.
 */
interface PublicCommunityApi {
    @GET("v1/projects/studygroupfinder-42da7/databases/(default)/documents/communities")
    suspend fun listCommunities(
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null,
    ): CommunityListResponse
}

/**
 * Maps the wire format down to the plain [Community] model at the repository
 * boundary, so no other layer ever sees a `stringValue` wrapper (§7.1).
 */
object CommunityRestMapper {
    fun toCommunities(response: CommunityListResponse): List<Community> {
        return response.documents?.mapNotNull { doc ->
            val fields = doc.fields ?: return@mapNotNull null
            Community(
                id = doc.name?.substringAfterLast('/').orEmpty(),
                name = fields.communityName?.stringValue.orEmpty(),
                city = fields.city?.stringValue.orEmpty(),
                imageUrl = fields.imageUrl?.stringValue.orEmpty(),
                verified = fields.verified?.booleanValue ?: false,
                domainWhitelist = fields.domainWhitelist?.arrayValue?.values?.mapNotNull { it.stringValue } ?: emptyList(),
                // REST timestamps are ISO 8601 strings, we'll keep 0 for now as it's not critical for selection
                createdAtMillis = 0L 
            )
        } ?: emptyList()
    }
}
