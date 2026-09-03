package com.studyfinder.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared by Splash, Login, Signup and Forgot Password (§7.0).
 */
class AuthViewModel : ViewModel() {

    private val authRepository = ServiceLocator.authRepository
    private val profileRepository = ServiceLocator.profileRepository

    private val _result = MutableLiveData<ActionResult>()
    val result: LiveData<ActionResult> = _result

    /** Which of the three Splash routes applies (§7.0). */
    enum class StartRoute { LOGIN, COMMUNITY_SELECTION, HOME }

    /**
     * Decides the Splash route (§7.0):
     *  - no signed-in user                 -> LOGIN
     *  - signed in, no communityId yet     -> COMMUNITY_SELECTION
     *  - signed in with a community        -> HOME
     *
     * A failed / empty profile read is treated as "no community yet".
     */
    fun resolveStartRoute(onResolved: (StartRoute) -> Unit) {
        viewModelScope.launch {
            if (authRepository.currentUid == null) {
                onResolved(StartRoute.LOGIN)
                return@launch
            }
            // Don't let a slow / offline profile read hang the splash forever.
            val state = withTimeoutOrNull(8_000L) {
                profileRepository.observeCurrentProfile().first { it !is UiState.Loading }
            }
            val route = when (state) {
                is UiState.Success ->
                    if (state.data.hasCommunity) StartRoute.HOME
                    else StartRoute.COMMUNITY_SELECTION
                else -> StartRoute.COMMUNITY_SELECTION
            }
            onResolved(route)
        }
    }

    fun clearResult() {
        _result.value = ActionResult.Idle
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
