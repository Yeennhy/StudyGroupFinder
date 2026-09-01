package com.studyfinder.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionStatus
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** §7.6. Same query as My Sessions, filtered to `endTime` in the past. */
class HistoryViewModel : ViewModel() {

    private val sessionRepository = ServiceLocator.sessionRepository

    private val _exportResult = MutableStateFlow<ActionResult?>(null)
    val exportResult: StateFlow<ActionResult?> = _exportResult

    val historySessions = sessionRepository.observeMySessions(includeCancelled = true)
        .map { state ->
            val now = System.currentTimeMillis()
            fun past(list: List<Session>) = list
                .filter { it.isPast(now) || it.status == SessionStatus.CANCELLED }
                .sortedByDescending { it.startTimeMillis }
            when (state) {
                is UiState.Success -> past(state.data)
                    .let { if (it.isEmpty()) UiState.Empty() else UiState.Success(it) }
                is UiState.Offline -> past(state.cached)
                    .let { if (it.isEmpty()) UiState.Empty() else UiState.Offline(it) }
                else -> state
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    fun exportPdf() {
        viewModelScope.launch {
            // TODO: Real PDF logic
            _exportResult.value = ActionResult.Success
        }
    }
    
    fun resetExportResult() {
        _exportResult.value = null
    }
}
