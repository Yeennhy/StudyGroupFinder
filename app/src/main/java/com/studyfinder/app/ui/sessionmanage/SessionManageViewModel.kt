package com.studyfinder.app.ui.sessionmanage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.data.remote.firestore.FirestoreMappers
import com.studyfinder.app.data.remote.firestore.FirestoreRefs
import com.studyfinder.app.model.CampusLocation
import com.studyfinder.app.model.MemberStatus
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionMember
import com.studyfinder.app.model.UserProfile
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** §7.5. */
class SessionManageViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository
    private val inboxRepository = ServiceLocator.inboxRepository
    private val profileRepository = ServiceLocator.profileRepository

    private val _session = MutableStateFlow<UiState<Session>>(UiState.Loading)
    val session: StateFlow<UiState<Session>> = _session

    private val _pendingSession = MutableStateFlow<Session?>(null)
    val pendingSession: StateFlow<Session?> = _pendingSession

    private val _pendingRequests = MutableStateFlow<UiState<List<SessionMember>>>(UiState.Loading)
    val pendingRequests: StateFlow<UiState<List<SessionMember>>> = _pendingRequests

    private val _members = MutableStateFlow<UiState<List<SessionMember>>>(UiState.Loading)
    val members: StateFlow<UiState<List<SessionMember>>> = _members

    private val _removedMemberUids = MutableStateFlow<Set<String>>(emptySet())
    val removedMemberUids: StateFlow<Set<String>> = _removedMemberUids

    private val _locations = MutableStateFlow<List<CampusLocation>>(emptyList())
    val locations: StateFlow<List<CampusLocation>> = _locations

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults: StateFlow<List<UserProfile>> = _searchResults

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult

    private var currentSessionId: String? = null

    init {
        loadCampusLocations()
    }

    private fun loadCampusLocations() {
        // Mock data for UI testing as requested
        _locations.value = listOf(
            CampusLocation("LIB", "Main Library", 10.7629, 106.6822),
            CampusLocation("SRA", "Study Room A", 10.7631, 106.6825),
            CampusLocation("HALL-B", "Hall B", 10.7630, 106.6820),
            CampusLocation("CAFE", "Campus Cafe", 10.7625, 106.6815)
        )
    }

    fun start(sessionId: String) {
        currentSessionId = sessionId
        viewModelScope.launch {
            sessionRepository.observeSession(sessionId).collectLatest {
                _session.value = it
            }
        }
        observePendingRequests(sessionId)
        observeMembers(sessionId)
    }

    private fun observePendingRequests(sessionId: String) {
        viewModelScope.launch {
            sessionRepository.observePendingRequests(sessionId).collectLatest {
                _pendingRequests.value = it
            }
        }
    }

    private fun observeMembers(sessionId: String) {
        viewModelScope.launch {
            sessionRepository.observeMembers(sessionId).collectLatest {
                _members.value = it
            }
        }
    }

    fun approve(uid: String) {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _actionResult.value = sessionRepository.approveRequest(sid, uid)
        }
    }

    fun reject(uid: String) {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _actionResult.value = sessionRepository.rejectRequest(sid, uid)
        }
    }

    fun removeMember(uid: String) {
        _removedMemberUids.value = _removedMemberUids.value + uid
    }

    fun saveEdits(session: Session) {
        _pendingSession.value = session
    }

    fun hasUnsavedChanges(): Boolean {
        return _pendingSession.value != null || _removedMemberUids.value.isNotEmpty()
    }

    fun submitChanges() {
        val sid = currentSessionId ?: return
        val sessionToSave = _pendingSession.value ?: (_session.value as? UiState.Success)?.data ?: return
        val toRemove = _removedMemberUids.value

        viewModelScope.launch {
            try {
                // 1. Save session edits
                if (_pendingSession.value != null) {
                    sessionRepository.editSession(sessionToSave)
                }

                // 2. Remove members
                toRemove.forEach { uid ->
                    sessionRepository.leaveOrRemove(sid, uid)
                }

                _actionResult.value = ActionResult.Success
                _pendingSession.value = null
                _removedMemberUids.value = emptySet()
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Failure(e.message ?: "Update failed")
            }
        }
    }

    fun cancelSession() {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _actionResult.value = sessionRepository.cancelSession(sid)
        }
    }

    fun finishSession() {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _actionResult.value = sessionRepository.finishSession(sid)
        }
    }

    fun attachMaterial(uri: Uri) {
        // TODO: Implementation for storage upload then db update
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchResults.value = profileRepository.findByStudentId(query)
        }
    }

    fun inviteUser(uid: String) {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            try {
                // 1. Create member doc with status INVITED
                FirestoreRefs.member(sid, uid).set(
                    FirestoreMappers.memberPayload(MemberStatus.INVITED)
                ).await()
                
                // 2. Send inbox notification
                inboxRepository.sendInvite(uid, sid)
                
                _actionResult.value = ActionResult.Success
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Failure(e.message ?: "Invite failed")
            }
        }
    }
    
    fun resetActionResult() {
        _actionResult.value = null
    }
}
