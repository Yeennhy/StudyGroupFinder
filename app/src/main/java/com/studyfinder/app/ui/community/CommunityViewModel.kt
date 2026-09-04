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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CommunityViewModel : ViewModel() {

    private val communityRepository = ServiceLocator.communityRepository

    /** Raw REST result — Loading / Success / Offline / Error. */
    private val _source = MutableStateFlow<UiState<List<Community>>>(UiState.Loading)
    private val _query = MutableStateFlow("")
    private val _city = MutableStateFlow<String?>(null)

    private val _joinResult = MutableLiveData<ActionResult>(ActionResult.Idle)
    val joinResult: LiveData<ActionResult> = _joinResult

    private var loadJob: Job? = null

    /** Distinct cities from the REST list — powers the city chip row. */
    val cities: StateFlow<List<String>> = _source
        .map { s ->
            val list = (s as? UiState.Success)?.data
                ?: (s as? UiState.Offline)?.cached
                ?: emptyList()
            list.map { it.city }.filter { it.isNotBlank() }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<UiState<List<Community>>> =
        combine(_source, _query, _city) { source, query, city ->
            val (base, offline) = when (source) {
                is UiState.Loading -> return@combine UiState.Loading
                is UiState.Error -> return@combine source
                is UiState.Empty -> emptyList<Community>() to false
                is UiState.Success -> source.data to false
                is UiState.Offline -> source.cached to true
            }

            var list = base
            city?.let { c -> list = list.filter { it.city.equals(c, ignoreCase = true) } }
            val q = query.trim().lowercase()
            if (q.isNotEmpty()) {
                list = list.filter {
                    it.name.lowercase().contains(q) || it.city.lowercase().contains(q)
                }
            }

            when {
                list.isEmpty() -> UiState.Empty()
                offline -> UiState.Offline(list)
                else -> UiState.Success(list)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    init {
        loadAllViaRest()
    }

    /** The REST-backed browse list. */
    fun loadAllViaRest() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            communityRepository.observeAllViaRest().collect { s ->
                // Keep the last good list visible while a refresh is loading.
                if (s is UiState.Loading && _source.value is UiState.Success) return@collect
                _source.value = s
            }
        }
    }

    /** Free-text search — case-insensitive substring over name + city. */
    fun search(q: String) {
        _query.value = q
    }

    fun filterByCity(c: String?) {
        _city.value = c
    }

    /**
     * Fails with a readable message when a verified community's domain
     * whitelist rejects the email.
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
