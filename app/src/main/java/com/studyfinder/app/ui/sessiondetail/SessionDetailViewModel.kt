package com.studyfinder.app.ui.sessiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.MemberStatus
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionMember
import com.studyfinder.app.model.SessionMode
import com.studyfinder.app.model.SessionStatus
import com.studyfinder.app.model.SessionViewMode
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** §7.3. */
class SessionDetailViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository
    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

    private val _session = MutableStateFlow<UiState<Session>>(UiState.Loading)
    val session: StateFlow<UiState<Session>> = _session

    private val _members = MutableStateFlow<UiState<List<SessionMember>>>(UiState.Loading)
    val members: StateFlow<UiState<List<SessionMember>>> = _members

    private val _myMembership = MutableStateFlow<SessionMember?>(null)
    val myMembership: StateFlow<SessionMember?> = _myMembership

    private val _blockedUids = MutableStateFlow<Set<String>>(emptySet())

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult

    private var viewMode: SessionViewMode = SessionViewMode.LIVE
    private var currentSessionId: String? = null

    val actionState: StateFlow<ActionState> = combine(_session, _myMembership, _blockedUids) { sessionState, membership, blocked ->
        val currentUid = auth.currentUser?.uid ?: ""
        if (sessionState is UiState.Success) {
            resolveActionState(sessionState.data, membership, viewMode, blocked, currentUid)
        } else {
            ActionState.Join
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActionState.Join)

    sealed interface ActionState {
        data object PastView : ActionState
        data object Cancelled : ActionState
        data object Manage : ActionState
        data object AcceptInvite : ActionState
        data object Leave : ActionState
        data object RequestPending : ActionState
        data object Full : ActionState
        data object Blocked : ActionState
        data object Join : ActionState
        data object RequestToJoin : ActionState
    }

    fun start(sessionId: String, viewMode: SessionViewMode) {
        this.currentSessionId = sessionId
        this.viewMode = viewMode
        
        viewModelScope.launch {
            sessionRepository.observeSession(sessionId).collectLatest {
                _session.value = it
            }
        }
        
        viewModelScope.launch {
            sessionRepository.observeMembers(sessionId).collectLatest {
                _members.value = it
            }
        }

        viewModelScope.launch {
            sessionRepository.observeMyMembership(sessionId).collectLatest {
                _myMembership.value = it
            }
        }

        viewModelScope.launch {
            ServiceLocator.profileRepository.observeBlockedUids().collectLatest {
                _blockedUids.value = it
            }
        }
    }

    fun resolveActionState(
        session: Session,
        myMembership: SessionMember?,
        viewMode: SessionViewMode,
        blocked: Set<String>,
        currentUid: String
    ): ActionState {
        // Row 1: Past View
        if (viewMode == SessionViewMode.PAST) return ActionState.PastView

        // Row 2: Cancelled
        if (session.status == SessionStatus.CANCELLED) return ActionState.Cancelled

        // Row 3: Host
        if (session.hostUid == currentUid) return ActionState.Manage

        // Member check (highest priority join-state check)
        if (currentUid in session.memberUids) return ActionState.Leave

        // Row 8: Full (Should block joining/accepting)
        if (session.isFull) return ActionState.Full

        // Row 7: Blocked
        if (session.containsBlockedUser(blocked)) return ActionState.Blocked

        // Row 4: Invited
        if (myMembership?.status == MemberStatus.INVITED) return ActionState.AcceptInvite

        // Row 6: Pending
        if (myMembership?.status == MemberStatus.PENDING) return ActionState.RequestPending

        // Rows 9 & 10: Join / Request
        return if (session.mode == SessionMode.OPEN) ActionState.Join else ActionState.RequestToJoin
    }

    fun join() {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _actionResult.value = sessionRepository.joinOpenSession(sid)
        }
    }

    fun requestToJoin() {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _actionResult.value = sessionRepository.requestToJoin(sid)
        }
    }

    fun acceptInvite() {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _actionResult.value = sessionRepository.acceptInvite(sid)
        }
    }

    fun cancelRequest() {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _actionResult.value = sessionRepository.cancelJoinRequest(sid)
        }
    }

    fun leave() {
        val sid = currentSessionId ?: return
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _actionResult.value = sessionRepository.leaveOrRemove(sid, uid)
        }
    }

    fun attachMaterial(uri: android.net.Uri) {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            try {
                val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                    .child("sessions/$sid/materials/${System.currentTimeMillis()}")
                
                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                
                _actionResult.value = sessionRepository.attachMaterial(sid, downloadUrl)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Failure(e.message ?: "Upload failed")
            }
        }
    }
    
    fun resetActionResult() {
        _actionResult.value = null
    }
}
