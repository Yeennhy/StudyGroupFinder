package com.studyfinder.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.data.remote.firestore.FirestoreMappers
import com.studyfinder.app.data.remote.firestore.FirestoreRefs
import com.studyfinder.app.model.UserProfile
import com.studyfinder.app.util.ActionResult
import kotlinx.coroutines.tasks.await

/**
 * Firebase Auth, email/password provider (§7.0).
 *
 * Firebase persists the session across restarts, which is why [currentUid]
 * alone decides whether Splash routes to Login or onward.
 */
class AuthRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val currentUid: String? get() = auth.currentUser?.uid

    val currentEmail: String? get() = auth.currentUser?.email

    /** Gates joining a *verified* community — see §7.1. */
    val isEmailVerified: Boolean get() = auth.currentUser?.isEmailVerified == true

    suspend fun signIn(email: String, password: String): ActionResult {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            ActionResult.Success
        } catch (e: Exception) {
            handleException(e, "Authentication failed")
        }
    }

    /**
     * Creates the Auth account, writes `users/{uid}`, then sends the
     * verification email — in that order, because every downstream screen
     * reads the user document (§7.0).
     */
    suspend fun signUp(
        email: String,
        password: String,
        name: String,
        studentId: String,
    ): ActionResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return ActionResult.Failure("Account creation failed")

            // Write user doc immediately
            val profile = UserProfile(
                uid = uid,
                name = name,
                studentId = studentId,
                createdAtMillis = System.currentTimeMillis()
            )
            FirestoreRefs.user(uid).set(FirestoreMappers.profilePayload(profile)).await()

            // Send verification email
            auth.currentUser?.sendEmailVerification()?.await()

            ActionResult.Success
        } catch (e: Exception) {
            handleException(e, "Registration failed")
        }
    }

    suspend fun sendPasswordReset(email: String): ActionResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            ActionResult.Success
        } catch (e: Exception) {
            handleException(e, "Failed to send reset email")
        }
    }

    suspend fun resendVerificationEmail(): ActionResult {
        return try {
            auth.currentUser?.sendEmailVerification()?.await()
            ActionResult.Success
        } catch (e: Exception) {
            handleException(e, "Failed to resend verification email")
        }
    }

    private fun handleException(e: Exception, defaultMsg: String): ActionResult {
        return if (e is FirebaseAuthException) {
            ActionResult.Failure(
                message = e.message ?: defaultMsg,
                cause = e,
                errorCode = e.errorCode
            )
        } else {
            ActionResult.Failure(message = e.message ?: defaultMsg, cause = e)
        }
    }

    /** Clears Auth, the Room cache and SharedPreferences flags (§7.0). */
    suspend fun signOut() {
        auth.signOut()
        
        // Wipe local database
        val db = ServiceLocator.database
        db.sessionDao().clear()
        db.communityDao().clear()
        db.mySessionDao().clear()
        db.profileDao().clear()
    }
}
