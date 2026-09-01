package com.studyfinder.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.InboxItem
import com.studyfinder.app.util.DateTimeUtils
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

/** §7.8. */
class InboxViewModel : ViewModel() {

    private val inboxRepository = ServiceLocator.inboxRepository
    private val sessionRepository = ServiceLocator.sessionRepository
    private val profileRepository = ServiceLocator.profileRepository

    private val _state = MutableStateFlow<UiState<List<InboxRow>>>(UiState.Loading)
    val state: StateFlow<UiState<List<InboxRow>>> = _state.asStateFlow()

    private var pipeline: Job? = null

    init {
        observeInbox()
    }

    /**
     * Inbox stream, with items from blocked users filtered out (§7.8), then
     * bucketed by day with a [InboxRow.DatePill] before each group.
     */
    fun observeInbox() {
        pipeline?.cancel()
        pipeline = viewModelScope.launch {
            combine(
                inboxRepository.observeInbox(),
                profileRepository.observeBlockedUids(),
            ) { inboxState, blocked ->
                when (inboxState) {
                    is UiState.Loading -> UiState.Loading
                    is UiState.Error -> UiState.Error(inboxState.message, inboxState.cause)
                    is UiState.Empty -> UiState.Empty()
                    is UiState.Offline -> toRows(inboxState.cached, blocked)?.let { UiState.Offline(it) }
                        ?: UiState.Empty()
                    is UiState.Success -> toRows(inboxState.data, blocked)?.let { UiState.Success(it) }
                        ?: UiState.Empty()
                }
            }.collect { _state.value = it }
        }
    }

    private fun toRows(items: List<InboxItem>, blocked: Set<String>): List<InboxRow>? {
        val visible = items
            .filter { it.fromUid == null || it.fromUid !in blocked }
            .sortedByDescending { it.createdAtMillis }
        if (visible.isEmpty()) return null

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val rows = mutableListOf<InboxRow>()
        var lastDate: LocalDate? = null
        visible.forEach { item ->
            val date = DateTimeUtils.toLocalDate(item.createdAtMillis)
            if (date != lastDate) {
                val label = when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> DateTimeUtils.formatDate(item.createdAtMillis)
                }
                rows.add(InboxRow.DatePill(label))
                lastDate = date
            }
            rows.add(InboxRow.Item(item))
        }
        return rows
    }

    /** Accept in place — runs the invitee transaction without leaving the screen. */
    fun accept(sessionId: String, itemId: String) {
        viewModelScope.launch {
            val result = sessionRepository.acceptInvite(sessionId)
            if (result is com.studyfinder.app.util.ActionResult.Success) {
                inboxRepository.markRead(itemId)
            }
        }
    }

    fun markRead(itemId: String) {
        viewModelScope.launch { inboxRepository.markRead(itemId) }
    }
}
