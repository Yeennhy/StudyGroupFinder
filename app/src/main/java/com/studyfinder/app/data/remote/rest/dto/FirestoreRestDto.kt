package com.studyfinder.app.data.remote.rest.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs for Firestore's REST API responses.
 */

@JsonClass(generateAdapter = true)
data class CommunityListResponse(
    val documents: List<FirestoreDocument>? = null,
    val nextPageToken: String? = null,
)

@JsonClass(generateAdapter = true)
data class FirestoreDocument(
    /** Full resource path; the ID is `name.substringAfterLast('/')`. */
    val name: String? = null,
    val fields: CommunityFields? = null,
    val createTime: String? = null,
    val updateTime: String? = null,
)

@JsonClass(generateAdapter = true)
data class CommunityFields(
    @Json(name = "name") val communityName: StringValue? = null,
    val city: StringValue? = null,
    val imageUrl: StringValue? = null,
    val verified: BoolValue? = null,
    val domainWhitelist: ArrayValue? = null,
    val createdAt: TimestampValue? = null,
)

@JsonClass(generateAdapter = true)
data class StringValue(val stringValue: String? = null)

@JsonClass(generateAdapter = true)
data class BoolValue(val booleanValue: Boolean? = null)

@JsonClass(generateAdapter = true)
data class TimestampValue(val timestampValue: String? = null)

@JsonClass(generateAdapter = true)
data class ArrayValue(val arrayValue: ArrayValues? = null)

@JsonClass(generateAdapter = true)
data class ArrayValues(val values: List<StringValue>? = null)
