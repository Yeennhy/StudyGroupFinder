package com.studyfinder.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.CourseCategory
import com.studyfinder.app.model.SessionSort
import com.studyfinder.app.model.TagType
import com.studyfinder.app.util.LocationUtils
import com.studyfinder.app.util.OverlapUtils
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
        val sort: SessionSort = SessionSort.TIME,
        /** Grey out rather than hide, so the list never silently shrinks (§7.2). */
        val hideOverlapping: Boolean = false,
    )

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    /** Set once by [sortByDistance]; null until the user asks for a distance sort. */
    private val _myLocation = MutableStateFlow<DoubleArray?>(null)

    private val _state = MutableStateFlow<UiState<List<SessionListAdapter.Row>>>(UiState.Loading)
    val state: StateFlow<UiState<List<SessionListAdapter.Row>>> = _state.asStateFlow()

    init {
        start()
    }

    /** Re-run from scratch — used on first load and by the error-state Retry. */
    fun retry() = start()

    private fun start() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val profileState = profileRepository.observeCurrentProfile()
                .first { it !is UiState.Loading }
            val communityId = (profileState as? UiState.Success)?.data?.communityId
            if (communityId.isNullOrBlank()) {
                _state.value = UiState.Empty("Join a community to see sessions.")
                return@launch
            }

            // Availability = the sessions the current user has already joined (§7.2).
            val busy = sessionRepository.getBusyIntervals()
            val myUid = authRepository.currentUid

            _filters
                .flatMapLatest { f ->
                    combine(
                        sessionRepository.observeCommunitySessions(
                            communityId, f.courseIdQuery, f.tagType, f.courseCategory,
                        ),
                        profileRepository.observeBlockedUids(),
                        _myLocation,
                    ) { sessions, blocked, loc ->
                        buildRows(sessions, blocked, loc, f, busy, myUid)
                    }
                }
                .collect { _state.value = it }
        }
    }

    private fun buildRows(
        sessionsState: UiState<List<com.studyfinder.app.model.Session>>,
        blocked: Set<String>,
        loc: DoubleArray?,
        f: Filters,
        busy: List<com.studyfinder.app.model.BusyInterval>,
        myUid: String?,
    ): UiState<List<SessionListAdapter.Row>> {
        val sessions = when (sessionsState) {
            is UiState.Success -> sessionsState.data
            is UiState.Offline -> sessionsState.cached
            is UiState.Empty -> emptyList()
            is UiState.Error -> return UiState.Error(sessionsState.message, sessionsState.cause)
            is UiState.Loading -> return UiState.Loading
        }

        var rows = sessions.map { s ->
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

    fun setHideOverlapping(hide: Boolean) {
        _filters.value = _filters.value.copy(hideOverlapping = hide)
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
