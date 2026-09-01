package com.studyfinder.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckProfileTest {
    @Test
    fun checkMainUserProfile() {
        runBlocking {
            val db = FirebaseFirestore.getInstance()
            val uid = "yW0hZ4StYEPJdI8gHCfmbNJF4z23"
            val doc = db.collection("users").document(uid).get().await()
            if (doc.exists()) {
                Log.d("PROFILE_CHECK", "User: ${doc.id}, communityId: '${doc.getString("communityId")}'")
            } else {
                Log.d("PROFILE_CHECK", "User $uid does not exist in Firestore")
            }
        }
    }
}
