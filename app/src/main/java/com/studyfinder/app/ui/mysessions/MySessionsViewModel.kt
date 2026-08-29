package com.studyfinder.app.ui.mysessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.Session
import com.studyfinder.app.util.DateTimeUtils
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** §7.6. */
class MySessionsViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository

    enum class ViewType { LIST, CALENDAR }

    private val _viewType = MutableStateFlow(ViewType.LIST)
    val viewType: StateFlow<ViewType> = _viewType

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    val upcomingSessions = sessionRepository.observeMySessions()
        .map { state ->
            if (state is UiState.Success && state.data.isNotEmpty()) {
                val now = System.currentTimeMillis()
                UiState.Success(state.data.filter { it.endTimeMillis > now }
                    .sortedBy { it.startTimeMillis })
            } else if (state is UiState.Success || state is UiState.Empty) {
                // Fallback to static mock data for demo/testing
                val uid = "brrTa7ftM0PaHJd68aFFx0HcsRI3"
                val now = System.currentTimeMillis()
                UiState.Success(listOf(
                    Session(
                        id = "mock-1",
                        title = "Intro to Algorithms",
                        courseName = "Data Structures",
                        locationName = "Floor 9 Library",
                        startTimeMillis = now + 3600000,
                        endTimeMillis = now + 10800000,
                        joinedCount = 4,
                        capacity = 6,
                        memberUids = listOf(uid),
                        communityId = "HCMUS"
                    ),
                    Session(
                        id = "mock-2",
                        title = "Calc III Study Group",
                        courseName = "Calculus 3",
                        locationName = "Library Rm 402",
                        startTimeMillis = now + 86400000 + 18000000,
                        endTimeMillis = now + 86400000 + 25200000,
                        joinedCount = 8,
                        capacity = 15,
                        memberUids = listOf(uid),
                        communityId = "HCMUS"
                    )
                ))
            } else state
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, UiState.Loading)

    val listItems: StateFlow<UiState<List<MySessionListItem>>> = upcomingSessions.map { state ->
        if (state is UiState.Success) {
            val items = mutableListOf<MySessionListItem>()
            val grouped = state.data.groupBy { DateTimeUtils.toLocalDate(it.startTimeMillis) }
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            grouped.keys.sorted().forEach { date ->
                val label = when (date) {
                    today -> "Today"
                    tomorrow -> "Tomorrow"
                    else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d"))
                }
                items.add(MySessionListItem.Header(label))
                grouped[date]?.forEach { session ->
                    items.add(MySessionListItem.SessionItem(session))
                }
            }
            UiState.Success(items)
        } else if (state is UiState.Error) {
            UiState.Error(state.message, state.cause)
        } else if (state is UiState.Empty) {
            UiState.Empty(state.message)
        } else {
            UiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, UiState.Loading)

    val calendarDays: StateFlow<List<CalendarDayAdapter.Day>> = combine(
        upcomingSessions, _currentMonth
    ) { state, month ->
        val sessions = if (state is UiState.Success) state.data else emptyList()
        val sessionsByDate = sessions.groupBy { DateTimeUtils.toLocalDate(it.startTimeMillis) }
        
        val days = mutableListOf<CalendarDayAdapter.Day>()
        val firstOfMonth = month.atDay(1)
        
        // Find the start of the grid (previous month overflow)
        // DayOfWeek: 1 (Mon) to 7 (Sun). We want Mon-Sun grid.
        val startOffset = firstOfMonth.dayOfWeek.value - 1
        val gridStart = firstOfMonth.minusDays(startOffset.toLong())
        
        // 42 cells (6 weeks) to cover any month
        for (i in 0 until 42) {
            val date = gridStart.plusDays(i.toLong())
            days.add(
                CalendarDayAdapter.Day(
                    date = date,
                    sessionCount = sessionsByDate[date]?.size ?: 0,
                    inCurrentMonth = date.monthValue == month.monthValue
                )
            )
        }
        days
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedDateSessions: StateFlow<List<Session>> = combine(
        upcomingSessions, _selectedDate
    ) { state, date ->
        if (state is UiState.Success) {
            state.data.filter { DateTimeUtils.toLocalDate(it.startTimeMillis) == date }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setViewType(type: ViewType) {
        _viewType.value = type
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun nextMonth() {
        _currentMonth.value = _currentMonth.value.plusMonths(1)
    }

    fun prevMonth() {
        _currentMonth.value = _currentMonth.value.minusMonths(1)
    }

    /**
     * Seeds mock data locally if Firestore is empty/unreachable for testing (§7.6).
     */
    fun seedMockData() {
        viewModelScope.launch {
            val uid = "brrTa7ftM0PaHJd68aFFx0HcsRI3"
            val now = com.google.firebase.Timestamp.now()
            val calendar = java.util.Calendar.getInstance()
            
            // Mock Session 1 (Today)
            val session1 = mapOf(
                "communityId" to "HCMUS",
                "hostUid" to "other-user",
                "title" to "Intro to Algorithms",
                "courseName" to "Data Structures",
                "locationName" to "Floor 9 Library",
                "startTime" to now,
                "endTime" to com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis() + 7200000)),
                "joinedCount" to 4,
                "capacity" to 6,
                "memberUids" to listOf(uid, "other-user"),
                "status" to "upcoming",
                "mode" to "open"
            )

            // Mock Session 2 (Tomorrow)
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 14)
            val startTime2 = calendar.time
            calendar.add(java.util.Calendar.HOUR, 2)
            val endTime2 = calendar.time

            val session2 = mapOf(
                "communityId" to "HCMUS",
                "hostUid" to uid,
                "title" to "Calc III Study Group",
                "courseName" to "Calculus 3",
                "locationName" to "Library Rm 402",
                "startTime" to com.google.firebase.Timestamp(startTime2),
                "endTime" to com.google.firebase.Timestamp(endTime2),
                "joinedCount" to 8,
                "capacity" to 15,
                "memberUids" to listOf(uid),
                "status" to "upcoming",
                "mode" to "gated"
            )

            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("sessions").add(session1)
                db.collection("sessions").add(session2)
            } catch (e: Exception) {
                // Ignore if rules fail
            }
        }
    }
}
