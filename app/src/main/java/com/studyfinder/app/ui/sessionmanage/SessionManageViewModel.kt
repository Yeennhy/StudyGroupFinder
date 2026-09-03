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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** §7.5. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManageViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository
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

    private val _searchQuery = MutableStateFlow("")
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val searchResults: StateFlow<List<UserProfile>> = combine(_session, _searchQuery) { sessionState, query ->
        Pair(sessionState, query)
    }.debounce(300) // Debounce search to prevent waterfall lag (§7.5)
    .flatMapLatest { (sessionState, query) ->
        val communityId = (sessionState as? UiState.Success)?.data?.communityId
        flow {
            if (query.isBlank()) {
                // When empty, show students from the same community as a recommendation
                if (communityId != null) {
                    emit(profileRepository.findByCommunity(communityId))
                } else {
                    emit(emptyList())
                }
            } else {
                // When searching, look for anyone by ID or Name prefix
                emit(profileRepository.searchUsers(query))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            _isLoading.value = true
            _actionResult.value = sessionRepository.approveRequest(sid, uid)
            _isLoading.value = false
        }
    }

    fun reject(uid: String) {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _actionResult.value = sessionRepository.rejectRequest(sid, uid)
            _isLoading.value = false
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
            _isLoading.value = true
            try {
                // 1. Save session edits
                if (_pendingSession.value != null) {
                    sessionRepository.editSession(sessionToSave)
                }

                // 2. Remove members in parallel (§7.5)
                if (toRemove.isNotEmpty()) {
                    coroutineScope {
                        toRemove.map { uid ->
                            async { sessionRepository.leaveOrRemove(sid, uid) }
                        }.awaitAll()
                    }
                }

                _actionResult.value = ActionResult.Success
                _pendingSession.value = null
                _removedMemberUids.value = emptySet()
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Failure(e.message ?: "Update failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelSession() {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _actionResult.value = sessionRepository.cancelSession(sid)
            _isLoading.value = false
        }
    }

    fun finishSession() {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _actionResult.value = sessionRepository.finishSession(sid)
            _isLoading.value = false
        }
    }

    fun attachMaterial(uri: Uri, context: android.content.Context) {
        val sid = currentSessionId ?: return
        viewModelScope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Failed to read file")
                
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                } ?: "${System.currentTimeMillis()}.pdf"

                _actionResult.value = sessionRepository.uploadToSupabase(bytes, fileName, sid)
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Failure(e.message ?: "Upload failed")
            }
        }
    }

    fun searchUsers(query: String) {
        _searchQuery.value = query
    }

    fun inviteUser(uid: String) {
        val sid = currentSessionId ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Use a batch to perform both actions in one request for snappiness (§7.4)
                val batch = db.batch()
                
                // 1. Create member doc with status INVITED
                val memberRef = FirestoreRefs.member(sid, uid)
                batch.set(memberRef, FirestoreMappers.memberPayload(MemberStatus.INVITED))
                
                // 2. Send inbox notification
                val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: throw Exception("Not signed in")
                val item = com.studyfinder.app.model.InboxItem(
                    type = com.studyfinder.app.model.InboxType.INVITE,
                    sessionId = sid,
                    fromUid = myUid,
                    message = "You have been invited to join a study session!"
                )
                val inboxRef = FirestoreRefs.inbox(uid).document()
                batch.set(inboxRef, FirestoreMappers.inboxPayload(item, myUid))
                
                // Fire-and-forget: The write is committed to the local cache and the
                // network queue immediately. We don't await the network confirmation.
                batch.commit()
                
                _actionResult.value = ActionResult.Success
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Failure(e.message ?: "Invite failed")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun resetActionResult() {
        _actionResult.value = null
    }
}
