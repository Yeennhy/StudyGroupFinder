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

    private var loadJob: Job? = null

    /** Full REST list, kept so search / city filter run client-side. */
    private var all: List<Community> = emptyList()
    private var wasOffline = false

    /** Current query + city, applied together over [all]. */
    private var query: String = ""
    private var city: String? = null

    init {
        loadAllViaRest()
    }

    /** The REST-backed browse list — the course's external-API requirement. */
    fun loadAllViaRest() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            communityRepository.observeAllViaRest().collect { s ->
                when (s) {
                    is UiState.Loading -> if (all.isEmpty()) _state.value = UiState.Loading
                    is UiState.Error -> if (all.isEmpty()) _state.value = s
                    is UiState.Success -> {
                        all = s.data
                        wasOffline = false
                        publishCities()
                        applyFilters()
                    }
                    is UiState.Offline -> {
                        all = s.cached
                        wasOffline = true
                        publishCities()
                        applyFilters()
                    }
                    is UiState.Empty -> {
                        all = emptyList()
                        applyFilters()
                    }
                }
            }
        }
    }

    /** Free-text search — case-insensitive substring over name + city (§7.1). */
    fun search(q: String) {
        query = q.trim()
        applyFilters()
    }

    fun filterByCity(c: String?) {
        city = c
        applyFilters()
    }

    private fun applyFilters() {
        var list = all
        city?.let { c -> list = list.filter { it.city.equals(c, ignoreCase = true) } }
        if (query.isNotEmpty()) {
            val q = query.lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) || it.city.lowercase().contains(q)
            }
        }
        _state.value = when {
            list.isEmpty() -> UiState.Empty()
            wasOffline -> UiState.Offline(list)
            else -> UiState.Success(list)
        }
    }

    private fun publishCities() {
        _cities.value = all.map { it.city }.filter { it.isNotBlank() }.distinct().sorted()
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
}
