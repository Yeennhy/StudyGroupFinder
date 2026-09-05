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
 * Firebase Auth, email/password provider.
 *
 * Firebase persists the session across restarts, which is why [currentUid]
 * alone decides whether Splash routes to Login or onward.
 */
class AuthRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val currentUid: String? get() = auth.currentUser?.uid

    val currentEmail: String? get() = auth.currentUser?.email

    /** Gates joining a *verified* community. */
    val isEmailVerified: Boolean get() = auth.currentUser?.isEmailVerified == true

    suspend fun reloadUser() {
        auth.currentUser?.reload()?.await()
    }

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
     * reads the user document.
     */
    suspend fun signUp(
        email: String,
        password: String,
        name: String,
        studentId: String,
    ): ActionResult {
        val uid = try {
            auth.createUserWithEmailAndPassword(email, password).await().user?.uid
                ?: return ActionResult.Failure("Account creation failed")
        } catch (e: Exception) {
            return handleException(e, "Registration failed")
        }

        return try {
            // Write user doc immediately
            val profile = UserProfile(
                uid = uid,
                name = name,
                studentId = studentId,
                createdAtMillis = System.currentTimeMillis()
            )
            FirestoreRefs.user(uid).set(FirestoreMappers.profilePayload(profile)).await()

            // Best-effort: resendVerificationEmail() covers retrying this step,
            // so it shouldn't fail an otherwise-complete signup.
            runCatching { auth.currentUser?.sendEmailVerification()?.await() }

            ActionResult.Success
        } catch (e: Exception) {
            // The Auth account exists but the profile write failed - roll it back
            // so the account doesn't become a signed-in ghost with no profile doc,
            // and the same email can be retried cleanly.
            runCatching { auth.currentUser?.delete()?.await() }
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

    /** Clears Auth, the Room cache and SharedPreferences flags. */
    suspend fun signOut() {
        auth.signOut()

        // Sign-out immediately navigates and drops the whole back stack, which
        // clears the calling ViewModel's scope - run the actual wipe under
        // NonCancellable so it can't be cut off mid-clear by that teardown.
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            val db = ServiceLocator.database
            db.sessionDao().clear()
            db.communityDao().clear()
            db.mySessionDao().clear()
            db.profileDao().clear()
        }
    }
}
