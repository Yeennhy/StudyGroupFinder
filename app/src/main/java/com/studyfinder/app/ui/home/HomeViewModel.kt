package com.studyfinder.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.CourseCategory
import com.studyfinder.app.model.ExpectationLevel
import com.studyfinder.app.model.SessionSort
import com.studyfinder.app.model.TagType
import com.studyfinder.app.model.BusyInterval
import com.studyfinder.app.model.Session
import com.studyfinder.app.util.LocationUtils
import com.studyfinder.app.util.OverlapUtils
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * §7.2. Holds the full filter/sort state so a rotation does not reset it, and
 * composes the client-side annotations Home needs on top of the server query:
 * distance (Haversine), schedule overlap, and blocked-member greying.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository
    private val profileRepository = ServiceLocator.profileRepository
    private val authRepository = ServiceLocator.authRepository

    data class Filters(
        val courseIdQuery: String? = null,
        val tagType: TagType? = null,
        val courseCategory: CourseCategory? = null,
        val expectationLevel: ExpectationLevel? = null,
        val sort: SessionSort = SessionSort.TIME,
        /** Grey out rather than hide, so the list never silently shrinks (§7.2). */
        val hideOverlapping: Boolean = false,
    )

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    /** Set once by [sortByDistance]; null until the user asks for a distance sort. */
    private val _myLocation = MutableStateFlow<DoubleArray?>(null)

    private val _isFiltersExpanded = MutableStateFlow(true)
    val isFiltersExpanded: StateFlow<Boolean> = _isFiltersExpanded.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<SessionListAdapter.Row>>>(UiState.Loading)
    val state: StateFlow<UiState<List<SessionListAdapter.Row>>> = _state.asStateFlow()

    private var pipeline: Job? = null

    init {
        start()
    }

    /** Re-run from scratch — used on first load and by the error-state Retry. */
    fun retry() = start()

    private fun start() {
        pipeline?.cancel()
        _state.value = UiState.Loading
        pipeline = viewModelScope.launch {
            val profileState = profileRepository.observeCurrentProfile()
                .first { it !is UiState.Loading }
            val rawCommunityId = (profileState as? UiState.Success)?.data?.communityId
            val communityId = rawCommunityId?.uppercase() // Force uppercase to match normalized data

            if (communityId.isNullOrBlank()) {
                _state.value = UiState.Empty("Join a community to see sessions.")
                return@launch
            }

            val myUid = authRepository.currentUid

            // Availability = the sessions the current user has already joined,
            // observed live so joining/leaving updates the overlap greying
            // without leaving Home (§7.2).
            val busyFlow = sessionRepository.observeMySessions().map { st ->
                val mine = (st as? UiState.Success)?.data
                    ?: (st as? UiState.Offline)?.cached
                    ?: emptyList()
                mine.map { BusyInterval(it.startTimeMillis, it.endTimeMillis, it.title) }
            }

            _filters
                .flatMapLatest { f ->
                    combine(
                        // Search is applied client-side over title/courseId/
                        // courseName (substring, case-insensitive) so "calc"
                        // matches "Calculus 1..." and no extra composite index
                        // is needed — only the chips filter server-side.
                        sessionRepository.observeCommunitySessions(
                            communityId, null, f.tagType, f.courseCategory, f.expectationLevel
                        ),
                        profileRepository.observeBlockedUids(),
                        _myLocation,
                        busyFlow,
                    ) { sessions, blocked, loc, busy ->
                        buildRows(sessions, blocked, loc, f, busy, myUid)
                    }
                }
                .collect { _state.value = it }
        }
    }

    private fun buildRows(
        sessionsState: UiState<List<Session>>,
        blocked: Set<String>,
        loc: DoubleArray?,
        f: Filters,
        busy: List<BusyInterval>,
        myUid: String?,
    ): UiState<List<SessionListAdapter.Row>> {
        val sessions = when (sessionsState) {
            is UiState.Success -> sessionsState.data
            is UiState.Offline -> sessionsState.cached
            is UiState.Empty -> emptyList()
            is UiState.Error -> return UiState.Error(sessionsState.message, sessionsState.cause)
            is UiState.Loading -> return UiState.Loading
        }

        val query = f.courseIdQuery?.trim()?.lowercase()
        val filtered = if (query.isNullOrEmpty()) sessions else sessions.filter { s ->
            s.title.lowercase().contains(query) ||
                s.courseId.lowercase().contains(query) ||
                s.courseName.lowercase().contains(query)
        }

        var rows = filtered.map { s ->
            val distanceKm = if (
                f.sort == SessionSort.DISTANCE && loc != null && s.lat != null && s.lng != null
            ) {
                LocationUtils.distanceKm(loc[0], loc[1], s.lat!!, s.lng!!)
            } else null

            SessionListAdapter.Row(
                session = s,
                distanceKm = distanceKm,
                overlapsAvailability = myUid != null &&
                    !s.memberUids.contains(myUid) &&
                    OverlapUtils.hasOverlap(s.startTimeMillis, s.endTimeMillis, busy),
                containsBlockedUser = s.containsBlockedUser(blocked),
            )
        }

        rows = when (f.sort) {
            SessionSort.TIME -> rows.sortedBy { it.session.startTimeMillis }
            SessionSort.NAME_ASC ->
                rows.sortedBy { it.session.title.lowercase() }
            SessionSort.NAME_DESC ->
                rows.sortedByDescending { it.session.title.lowercase() }
            SessionSort.DISTANCE ->
                rows.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        }

        if (f.hideOverlapping) {
            rows = rows.filterNot { it.overlapsAvailability }
        }

        return when {
            rows.isEmpty() -> UiState.Empty()
            sessionsState is UiState.Offline -> UiState.Offline(rows)
            else -> UiState.Success(rows)
        }
    }

    // ------------------------------------------------------------------ intents

    fun setSort(sort: SessionSort) {
        if (sort == SessionSort.DISTANCE) return // needs a location — use sortByDistance()
        _myLocation.value = null
        _filters.value = _filters.value.copy(sort = sort)
    }

    /** Free-text search — matched client-side against title / courseId /
     *  courseName (substring, case-insensitive). */
    fun setCourseIdQuery(query: String?) {
        val normalized = query?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized == _filters.value.courseIdQuery) return
        _filters.value = _filters.value.copy(courseIdQuery = normalized)
    }

    /** Spec: session type chips — normal / midterm / final. */
    fun setTagType(tagType: TagType?) {
        _filters.value = _filters.value.copy(tagType = tagType)
    }

    /** Spec: course type chips — physics / calculus / DSA / … */
    fun setCourseCategory(category: CourseCategory?) {
        _filters.value = _filters.value.copy(courseCategory = category)
    }

    /** Added: expectation level filter chips — pass / casual / overachieving */
    fun setExpectationLevel(level: ExpectationLevel?) {
        _filters.value = _filters.value.copy(expectationLevel = level)
    }

    fun setHideOverlapping(hide: Boolean) {
        _filters.value = _filters.value.copy(hideOverlapping = hide)
    }

    fun toggleFiltersExpanded() {
        _isFiltersExpanded.value = !_isFiltersExpanded.value
    }

    /**
     * Called by the Fragment after it has the one-shot `getCurrentLocation()`
     * fix. Switches the sort to distance and re-annotates every row (§7.2).
     */
    fun sortByDistance(lat: Double, lng: Double) {
        _myLocation.value = doubleArrayOf(lat, lng)
        _filters.value = _filters.value.copy(sort = SessionSort.DISTANCE)
    }

    /** Location permission refused — silent fallback, no error UI (§7.2). */
    fun onLocationPermissionDenied() {
        _myLocation.value = null
        if (_filters.value.sort == SessionSort.DISTANCE) {
            _filters.value = _filters.value.copy(sort = SessionSort.TIME)
        }
    }
}
