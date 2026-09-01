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

    init {
        viewModelScope.launch {
            upcomingSessions.collect { state ->
                if (state is UiState.Success && state.data.isNotEmpty()) {
                    // Automatically jump to the month of the first upcoming session
                    val firstSessionDate = DateTimeUtils.toLocalDate(state.data.first().startTimeMillis)
                    _currentMonth.value = YearMonth.from(firstSessionDate)
                    _selectedDate.value = firstSessionDate
                }
            }
        }
    }

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    val upcomingSessions = sessionRepository.observeMySessions()
        .map { state ->
            val now = System.currentTimeMillis()
            fun future(list: List<Session>) = list.filter { 
                it.endTimeMillis > now && it.status == com.studyfinder.app.model.SessionStatus.UPCOMING 
            }.sortedBy { it.startTimeMillis }
            
            when (state) {
                is UiState.Success -> UiState.Success(future(state.data))
                is UiState.Offline -> UiState.Offline(future(state.cached))
                else -> state
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, UiState.Loading)

    val listItems: StateFlow<UiState<List<MySessionListItem>>> = upcomingSessions.map { state ->
        when (state) {
            is UiState.Success -> groupByDay(state.data)
                .let { if (it.isEmpty()) UiState.Empty() else UiState.Success(it) }
            is UiState.Offline -> groupByDay(state.cached)
                .let { if (it.isEmpty()) UiState.Empty() else UiState.Offline(it) }
            is UiState.Error -> UiState.Error(state.message, state.cause)
            is UiState.Empty -> UiState.Empty(state.message)
            is UiState.Loading -> UiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, UiState.Loading)

    private fun groupByDay(sessions: List<Session>): List<MySessionListItem> {
        val items = mutableListOf<MySessionListItem>()
        val grouped = sessions.groupBy { DateTimeUtils.toLocalDate(it.startTimeMillis) }
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        grouped.keys.sorted().forEach { date ->
            val label = when (date) {
                today -> "Today"
                tomorrow -> "Tomorrow"
                else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d"))
            }
            items.add(MySessionListItem.Header(label))
            grouped[date]?.forEach { items.add(MySessionListItem.SessionItem(it)) }
        }
        return items
    }

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
}
