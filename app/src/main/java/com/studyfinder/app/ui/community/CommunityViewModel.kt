package com.studyfinder.app.ui.community

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.model.Community
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** §7.1. */
class CommunityViewModel : ViewModel() {

    private val communityRepository = ServiceLocator.communityRepository

    private val _state = MutableStateFlow<UiState<List<Community>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Community>>> = _state.asStateFlow()

    /** Distinct cities seen in the REST browse list — powers the city filter UI. */
    private val _cities = MutableStateFlow<List<String>>(emptyList())
    val cities: StateFlow<List<String>> = _cities.asStateFlow()

    private val _joinResult = MutableLiveData<ActionResult>(ActionResult.Idle)
    val joinResult: LiveData<ActionResult> = _joinResult

    private var queryJob: Job? = null

    init {
        loadAllViaRest()
    }

    /** The REST-backed browse list — the course's external-API requirement. */
    fun loadAllViaRest() {
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            communityRepository.observeAllViaRest().collect { s ->
                if (s is UiState.Success) {
                    _cities.value = s.data.map { it.city }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                }
                _state.value = s.normalizeEmpty()
            }
        }
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            loadAllViaRest()
            return
        }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            communityRepository.searchByName(q).collect { _state.value = it.normalizeEmpty() }
        }
    }

    fun filterByCity(city: String) {
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            communityRepository.searchByCity(city).collect { _state.value = it.normalizeEmpty() }
        }
    }

    /**
     * Fails with a readable message when a verified community rejects the
     * email domain or the account email is unverified — an inline error, not a
     * silent no-op (§7.1).
     */
    fun join(communityId: String) {
        _joinResult.value = ActionResult.Idle
        viewModelScope.launch {
            _joinResult.value = communityRepository.joinCommunity(communityId)
        }
    }

    fun clearJoinResult() {
        _joinResult.value = ActionResult.Idle
    }

    private fun UiState<List<Community>>.normalizeEmpty(): UiState<List<Community>> =
        if (this is UiState.Success && data.isEmpty()) UiState.Empty() else this
}
