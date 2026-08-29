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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** §7.4. */
class CreateSessionViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository
    private val communityRepository = ServiceLocator.communityRepository

    private val _locations = MutableStateFlow<List<CampusLocation>>(emptyList())
    val locations: StateFlow<List<CampusLocation>> = _locations

    private val _createResult = MutableStateFlow<ActionResult?>(null)
    val createResult: StateFlow<ActionResult?> = _createResult

    init {
        loadCampusLocations()
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
        // TODO: implementation for prefilling
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
        isGated: Boolean
    ) {
        viewModelScope.launch {
            val endTimeMillis = startTimeMillis + (durationMinutes * 60000L)
            
            // We use tags instead of specific course objects now.
            // For the Session model, we might need a dummy course or update the model.
            // Since we're bypassing backend, we'll just build a session with dummy course info if needed.
            
            val session = Session(
                id = "", 
                communityId = "HCMUS", 
                hostUid = "brrTa7ftM0PaHJd68aFFx0HcsRI3",
                courseId = "GENERAL", // Dummy since course selection is removed
                courseName = "General Study",
                courseCategory = CourseCategory.OTHER,
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
                mode = if (isGated) com.studyfinder.app.model.SessionMode.GATED else com.studyfinder.app.model.SessionMode.OPEN
            )
            
            val result = sessionRepository.createSession(session)
            if (result is com.studyfinder.app.util.Result.Success) {
                _createResult.value = ActionResult.Success
            } else if (result is com.studyfinder.app.util.Result.Error) {
                _createResult.value = ActionResult.Failure(result.message)
            }
        }
    }
    
    fun resetResult() {
        _createResult.value = null
    }
}
