package com.studyfinder.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.util.ActionResult
import kotlinx.coroutines.launch

/**
 * Shared by Splash, Login, Signup and Forgot Password (§7.0).
 */
class AuthViewModel : ViewModel() {

    private val authRepository = ServiceLocator.authRepository

    private val _result = MutableLiveData<ActionResult>()
    val result: LiveData<ActionResult> = _result

    /** Which of the three Splash routes applies (§7.0). */
    enum class StartRoute { LOGIN, COMMUNITY_SELECTION, HOME }

    fun resolveStartRoute(onResolved: (StartRoute) -> Unit) {
        // Implementation logic can be added later
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _result.value = authRepository.signIn(email, password)
        }
    }

    fun signUp(email: String, password: String, name: String, studentId: String) {
        viewModelScope.launch {
            _result.value = authRepository.signUp(email, password, name, studentId)
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _result.value = authRepository.sendPasswordReset(email)
        }
    }

    /** Clears Auth, the Room cache and prefs, then Login pops the whole stack. */
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
