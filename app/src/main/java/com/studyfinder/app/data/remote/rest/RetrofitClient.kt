package com.studyfinder.app.data.remote.rest

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Retrofit setup for the single REST call in
 */
object RetrofitClient {

    const val BASE_URL = "https://firestore.googleapis.com/"

    val publicCommunityApi: PublicCommunityApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(PublicCommunityApi::class.java)
    }
}
