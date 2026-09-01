package com.studyfinder.app.data.remote.supabase

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage

object SupabaseClientProvider {
    private const val SUPABASE_URL = "https://cmdwxhxdoxromfnqnvub.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_vGpFa5kvasZ8gH9tLV_vzQ_OLZfnq_d"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Storage)
    }
}
