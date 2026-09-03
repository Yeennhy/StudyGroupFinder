package com.studyfinder.app.ui.sessioncreate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.CampusLocation
import com.studyfinder.app.model.CourseCategory
import com.studyfinder.app.model.ExpectationLevel
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.TagType
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** §7.4. */
class CreateSessionViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository
    private val profileRepository = ServiceLocator.profileRepository

    private val _locations = MutableStateFlow<List<CampusLocation>>(emptyList())
    val locations: StateFlow<List<CampusLocation>> = _locations

    private val _prefilledSession = MutableStateFlow<Session?>(null)
    val prefilledSession: StateFlow<Session?> = _prefilledSession

    private val _createResult = MutableSharedFlow<ActionResult>(replay = 0)
    val createResult: SharedFlow<ActionResult> = _createResult.asSharedFlow()

    private var currentCommunityId: String? = null
    private var prefillSessionId: String? = null

    init {
        loadCampusLocations()
        viewModelScope.launch {
            profileRepository.observeCurrentProfile().collect { state ->
                if (state is UiState.Success) {
                    currentCommunityId = state.data.communityId
                }
            }
        }
    }

    fun loadCampusLocations() {
        // Mock data for UI testing as requested
        _locations.value = listOf(
            CampusLocation("LIB", "Main Library", 10.7629, 106.6822),
            CampusLocation("SRA", "Study Room A", 10.7631, 106.6825),
            CampusLocation("HALL-B", "Hall B", 10.7630, 106.6820),
            CampusLocation("CAFE", "Campus Cafe", 10.7625, 106.6815)
        )
    }

    /** Copies every field except date/time, which must be re-picked (§7.6). */
    fun prefillFrom(sessionId: String) {
        prefillSessionId = sessionId
        viewModelScope.launch {
            sessionRepository.observeSession(sessionId).collectLatest { state ->
                if (state is UiState.Success) {
                    _prefilledSession.value = state.data
                }
            }
        }
    }

    fun submit(
        title: String,
        description: String,
        goals: String,
        location: CampusLocation,
        tagType: TagType,
        expectation: ExpectationLevel,
        startTimeMillis: Long,
        durationMinutes: Int,
        capacity: Int,
        isGated: Boolean,
        tags: List<String> = emptyList()
    ) {
        val communityId = currentCommunityId
        if (communityId == null) {
            viewModelScope.launch {
                _createResult.emit(ActionResult.Failure("Community not selected"))
            }
            return
        }

        viewModelScope.launch {
            val endTimeMillis = startTimeMillis + (durationMinutes * 60000L)
            
            val session = Session(
                id = "", 
                communityId = communityId, 
                hostUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
                courseId = _prefilledSession.value?.courseId ?: "GENERAL",
                courseName = _prefilledSession.value?.courseName ?: "General Study",
                courseCategory = _prefilledSession.value?.courseCategory ?: CourseCategory.OTHER,
                tagType = tagType,
                expectationLevel = expectation,
                title = title,
                description = description,
                goals = goals,
                locationName = location.name,
                lat = location.lat,
                lng = location.lng,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
                capacity = capacity,
                mode = if (isGated) com.studyfinder.app.model.SessionMode.GATED else com.studyfinder.app.model.SessionMode.OPEN,
                tags = tags
            )
            
            val result = sessionRepository.createSession(session)
            if (result is com.studyfinder.app.util.Result.Success) {
                // If it was a "pick up", invite everyone from the old one.
                // We launch this in a separate job so the Success screen shows
                // immediately after the session itself is saved (§7.6).
                prefillSessionId?.let { oldId ->
                    viewModelScope.launch {
                        sessionRepository.inviteAllFrom(oldId, result.data)
                    }
                }
                _createResult.emit(ActionResult.Success)
            } else if (result is com.studyfinder.app.util.Result.Error) {
                _createResult.emit(ActionResult.Failure(result.message))
            }
        }
    }
    
    fun resetResult() {
        // No-op for SharedFlow
    }
}
