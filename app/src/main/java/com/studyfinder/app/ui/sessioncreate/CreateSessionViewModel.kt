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

class CreateSessionViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository
    private val profileRepository = ServiceLocator.profileRepository

    private val _locations = MutableStateFlow<List<CampusLocation>>(emptyList())
    val locations: StateFlow<List<CampusLocation>> = _locations

    private val _prefilledSession = MutableStateFlow<Session?>(null)
    val prefilledSession: StateFlow<Session?> = _prefilledSession

    private val _createResult = MutableSharedFlow<ActionResult>(replay = 0)
    val createResult: SharedFlow<ActionResult> = _createResult.asSharedFlow()

    // Draft Session Data (Persists across rotation)
    val draftDate = MutableStateFlow(java.util.Calendar.getInstance())
    val draftDurationMinutes = MutableStateFlow(90)
    val draftCapacity = MutableStateFlow(4)
    val draftIsGated = MutableStateFlow(false)
    private val _draftTags = MutableStateFlow<List<String>>(emptyList())
    val draftTags: StateFlow<List<String>> = _draftTags

    fun addDraftTag(tag: String) {
        if (!_draftTags.value.contains(tag)) {
            _draftTags.value = _draftTags.value + tag
        }
    }

    fun removeDraftTag(tag: String) {
        _draftTags.value = _draftTags.value - tag
    }

    fun setDraftTags(tags: List<String>) {
        _draftTags.value = tags
    }

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
        _locations.value = listOf(
            CampusLocation("LIB", "Main Library", 10.7629, 106.6822),
            CampusLocation("SRA", "Study Room A", 10.7631, 106.6825),
            CampusLocation("HALL-B", "Hall B", 10.7630, 106.6820),
            CampusLocation("CAFE", "Campus Cafe", 10.7625, 106.6815)
        )
    }

    /** Copies fields from an existing session for pre-filling. */
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
        courseId: String,
        courseName: String,
        courseCategory: CourseCategory,
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
                courseId = courseId.trim().ifBlank { "GENERAL" },
                courseName = courseName.trim().ifBlank { "General Study" },
                courseCategory = courseCategory,
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
