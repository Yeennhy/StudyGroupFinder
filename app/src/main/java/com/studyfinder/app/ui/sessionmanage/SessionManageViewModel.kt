package com.studyfinder.app.ui.sessionmanage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionMember
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** §7.5. */
class SessionManageViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository
    private val inboxRepository = ServiceLocator.inboxRepository

    private val _session = MutableStateFlow<UiState<Session>>(UiState.Loading)
    val session: StateFlow<UiState<Session>> = _session

    private val _pendingRequests = MutableStateFlow<UiState<List<SessionMember>>>(UiState.Loading)
    val pendingRequests: StateFlow<UiState<List<SessionMember>>> = _pendingRequests

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult

    private var currentSessionId: String? = null

    fun start(sessionId: String) {
        currentSessionId = sessionId
        viewModelScope.launch {
            sessionRepository.observeSession(sessionId).collectLatest {
                _session.value = it
            }
        }
        observePendingRequests(sessionId)
    }

    private fun observePendingRequests(sessionId: String) {
        viewModelScope.launch {
            sessionRepository.observePendingRequests(sessionId).collectLatest {
                _pendingRequests.value = it
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
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _actionResult.value = sessionRepository.leaveOrRemove(sid, uid)
        }
    }

    fun saveEdits(session: Session) {
        viewModelScope.launch {
            _actionResult.value = sessionRepository.editSession(session)
        }
    }

    fun cancelSession() {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _actionResult.value = sessionRepository.cancelSession(sid)
        }
    }

    fun attachMaterial(uri: Uri) {
        // TODO: Implementation for storage upload then db update
    }
    
    fun resetActionResult() {
        _actionResult.value = null
    }
}
