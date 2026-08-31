package com.studyfinder.app.ui.profile

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.ActivityCell
import com.studyfinder.app.model.UserProfile
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** §7.7. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel : ViewModel() {

    private val profileRepository = ServiceLocator.profileRepository
    private val authRepository = ServiceLocator.authRepository

    private val uidFlow = MutableStateFlow<String?>(null)

    val profile: LiveData<UiState<UserProfile>> = uidFlow.flatMapLatest { uid ->
        if (uid == null) {
            profileRepository.observeCurrentProfile()
        } else {
            profileRepository.observeProfile(uid)
        }
    }.asLiveData()

    val isBlocked: LiveData<Boolean> = combine(uidFlow, profileRepository.observeBlockedUids()) { uid, blockedUids ->
        uid != null && blockedUids.contains(uid)
    }.asLiveData()

    private val _saveResult = MutableLiveData<ActionResult>(ActionResult.Idle)
    val saveResult: LiveData<ActionResult> = _saveResult

    /** null uid = self view. */
    fun start(uid: String?) {
        uidFlow.value = uid
    }

    fun save(profile: UserProfile) {
        viewModelScope.launch {
            _saveResult.value = profileRepository.updateProfile(profile)
        }
    }

    fun clearSaveResult() {
        _saveResult.value = ActionResult.Idle
    }

    /** Same Storage path for both the camera Intent and the Photo Picker. */
    fun uploadPhoto(uri: Uri) {
        viewModelScope.launch {
            _saveResult.value = profileRepository.uploadProfilePhoto(uri)
        }
    }

    /**
     * Activity graph (§7.7). Reuses the My Sessions query result — NOT a
     * `collectionGroup("members")` query, which §4 does not permit.
     */
    val activityCells: LiveData<List<ActivityCell>> = uidFlow.flatMapLatest { uid ->
        val targetUid = uid ?: authRepository.currentUid ?: ""
        ServiceLocator.sessionRepository.observeUserSessions(targetUid)
    }.map { state ->
        if (state is UiState.Success) {
            state.data.groupBy {
                Instant.ofEpochMilli(it.startTimeMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }.map { (date, sessions) ->
                ActivityCell(date, sessions.size)
            }
        } else {
            emptyList()
        }
    }.asLiveData()

    /**
     * Writes `users/{myUid}/blocked/{theirUid}` — a private subcollection, so
     * the blocked person cannot read the list (§3.1). The visible effect is on
     * Home: sessions whose member list contains them are greyed out (§7.2).
     */
    fun blockUser(uid: String) {
        viewModelScope.launch {
            profileRepository.blockUser(uid)
        }
    }

    fun unblockUser(uid: String) {
        viewModelScope.launch {
            profileRepository.unblockUser(uid)
        }
    }

    fun resendVerificationEmail() {
        TODO("§7.0")
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
