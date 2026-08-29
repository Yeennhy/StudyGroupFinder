package com.studyfinder.app.ui.sessioncreate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.CampusLocation
import com.studyfinder.app.model.Course
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

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses

    private val _locations = MutableStateFlow<List<CampusLocation>>(emptyList())
    val locations: StateFlow<List<CampusLocation>> = _locations

    private val _createResult = MutableStateFlow<ActionResult?>(null)
    val createResult: StateFlow<ActionResult?> = _createResult

    init {
        loadCourses()
        loadCampusLocations()
    }

    /** Dropdown sources, seeded per community (§3.1). */
    fun loadCourses() {
        // Mock data for UI testing as requested
        _courses.value = listOf(
            Course("DSA", "Data Structures and Algorithms", CourseCategory.DSA),
            Course("MOB", "Mobile Development", CourseCategory.PROGRAMMING),
            Course("CAL2", "Calculus 2", CourseCategory.CALCULUS),
            Course("ENG", "Academic English", CourseCategory.ENGLISH)
        )
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
        course: Course,
        location: CampusLocation,
        tagType: TagType,
        expectation: ExpectationLevel,
        startTimeMillis: Long,
        durationMinutes: Int,
        capacity: Int,
        isGated: Boolean
    ) {
        viewModelScope.launch {
            // Stubbed backend implementation as requested
            // In a real implementation, we would call sessionRepository.createSession(...)
            _createResult.value = ActionResult.Success
        }
    }
    
    fun resetResult() {
        _createResult.value = null
    }
}
