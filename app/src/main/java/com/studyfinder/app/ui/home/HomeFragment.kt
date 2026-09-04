package com.studyfinder.app.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.studyfinder.app.util.applyFadeThroughTransitions
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentHomeBinding
import com.studyfinder.app.model.CourseCategory
import com.studyfinder.app.model.ExpectationLevel
import com.studyfinder.app.model.SessionSort
import com.studyfinder.app.model.SessionViewMode
import com.studyfinder.app.model.TagType
import com.studyfinder.app.ui.common.StateRenderer
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.setupHeader
import com.studyfinder.app.util.setupNavbar
import kotlinx.coroutines.launch

/**
 * Home / Upcoming sessions.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private val adapter = SessionListAdapter { session -> openSession(session.id) }

    private var searchWatcher: TextWatcher? = null

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchLocationThenSort() else viewModel.onLocationPermissionDenied()
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        applyFadeThroughTransitions()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNavbar(binding.navBar)
        setupHeader(binding.appHeader, "Home", showHistory = true, showBackBtn = false, showAvatar = true)

        binding.rvSessions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSessions.adapter = adapter

        binding.fabAdd.setOnClickListener { openCreateSession() }
        binding.stateError.btnStateRetry.setOnClickListener { viewModel.retry() }
        binding.btnExpandFilters.setOnClickListener { viewModel.toggleFiltersExpanded() }

        wireSearch()
        wireSessionTypeChips()
        wireCourseTypeChips()
        wireExpectationLevelChips()
        wireToggleAndSort()
        observeState()
    }

    // ------------------------------------------------------------------ wiring

    private fun wireSearch() {
        searchWatcher = object : TextWatcher {
            private val runnable = Runnable {
                viewModel.setCourseIdQuery(binding.etSearch.text?.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.etSearch.removeCallbacks(runnable)
                binding.etSearch.postDelayed(runnable, 350)
            }
        }
        binding.etSearch.addTextChangedListener(searchWatcher)
    }

    private fun wireSessionTypeChips() {
        val row = listOf(
            binding.chipAllSessions to null,
            binding.chipMidterm to TagType.MIDTERM,
            binding.chipFinal to TagType.FINAL,
            binding.chipReview to TagType.NORMAL,
        )
        row.forEach { (chip, tag) ->
            chip.setOnClickListener {
                selectInRow(row.map { it.first }, chip)
                viewModel.setTagType(tag)
            }
        }
    }

    private fun wireCourseTypeChips() {
        val row = listOf(
            binding.chipTypeAll to null,
            binding.chipTypePhysics to CourseCategory.PHYSICS,
            binding.chipTypeCalculus to CourseCategory.CALCULUS,
            binding.chipTypeDsa to CourseCategory.DSA,
            binding.chipTypeProgramming to CourseCategory.PROGRAMMING,
            binding.chipTypeEnglish to CourseCategory.ENGLISH,
            binding.chipTypeArts to CourseCategory.ARTS,
            binding.chipTypeSocial to CourseCategory.SOCIAL,
            binding.chipTypeOther to CourseCategory.OTHER,
        )
        row.forEach { (chip, category) ->
            chip.setOnClickListener {
                selectInRow(row.map { it.first }, chip)
                viewModel.setCourseCategory(category)
            }
        }
    }

    private fun wireExpectationLevelChips() {
        val row = listOf(
            binding.chipExpAll to null,
            binding.chipExpPass to ExpectationLevel.PASS,
            binding.chipExpCasual to ExpectationLevel.CASUAL,
            binding.chipExpGrind to ExpectationLevel.OVERACHIEVING,
        )
        row.forEach { (chip, level) ->
            chip.setOnClickListener {
                selectInRow(row.map { it.first }, chip)
                viewModel.setExpectationLevel(level)
            }
        }
    }

    private fun wireToggleAndSort() {
        binding.toggleConflicting.setOnClickListener {
            val next = binding.toggleConflicting.tag != true
            binding.toggleConflicting.tag = next
            binding.toggleConflicting.setBackgroundResource(
                if (next) R.drawable.bg_toggle_selected else R.drawable.bg_toggle_unselected_offset
            )
            viewModel.setHideOverlapping(next)
        }

        binding.tvSort.setOnClickListener { anchor ->
            PopupMenu(requireContext(), anchor).apply {
                menu.add(0, 1, 0, R.string.home_sort_time)
                menu.add(0, 2, 1, R.string.home_sort_name_asc)
                menu.add(0, 3, 2, R.string.home_sort_name_desc)
                menu.add(0, 4, 3, R.string.home_sort_distance)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> viewModel.setSort(SessionSort.TIME)
                        2 -> viewModel.setSort(SessionSort.NAME_ASC)
                        3 -> viewModel.setSort(SessionSort.NAME_DESC)
                        4 -> requestDistanceSort()
                    }
                    // Label follows the *actual* sort (see observeState) — a
                    // failed GPS fetch silently reverts to time, so don't set
                    // it optimistically here.
                    true
                }
                show()
            }
        }
    }

    // ------------------------------------------------------------------ location

    private fun requestDistanceSort() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) fetchLocationThenSort()
        else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationThenSort() {
        val client = LocationServices.getFusedLocationProviderClient(requireContext())
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        viewModel.sortByDistance(loc.latitude, loc.longitude)
                    } else {
                        client.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null) {
                                    viewModel.sortByDistance(last.latitude, last.longitude)
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "Location unavailable — sorted by time instead",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    viewModel.onLocationPermissionDenied()
                                }
                            }
                            .addOnFailureListener { viewModel.onLocationPermissionDenied() }
                    }
                }
                .addOnFailureListener { viewModel.onLocationPermissionDenied() }
        } catch (e: SecurityException) {
            viewModel.onLocationPermissionDenied()
        }
    }

    // ------------------------------------------------------------------ state

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filters.collect { f ->
                        binding.tvSort.setText(
                            when (f.sort) {
                                SessionSort.TIME -> R.string.home_sort_time
                                SessionSort.NAME_ASC -> R.string.home_sort_name_asc
                                SessionSort.NAME_DESC -> R.string.home_sort_name_desc
                                SessionSort.DISTANCE -> R.string.home_sort_distance
                            }
                        )
                    }
                }
                launch {
                    viewModel.isFiltersExpanded.collect { expanded ->
                        updateFiltersVisibility(expanded)
                    }
                }
                viewModel.state.collect { state ->
                    StateRenderer.render(
                        state = state,
                        loadingView = binding.stateLoading.root,
                        emptyView = binding.stateEmpty.root,
                        errorView = binding.stateError.root,
                        offlineView = binding.stateOfflineBanner.root,
                        contentView = binding.rvSessions,
                    )
                    
                    if (state is UiState.Error) {
                        android.util.Log.e("STUDY_FINDER_DEBUG", "Home load failed: ${state.message}", state.cause)
                        
                        binding.stateError.tvStateErrorMessage.setText(R.string.home_error)
                        binding.stateError.tvStateErrorDetail.isVisible = true
                        binding.stateError.tvStateErrorDetail.text = state.message
                    }

                    val rows = when (state) {
                        is UiState.Success -> state.data
                        is UiState.Offline -> state.cached
                        else -> emptyList()
                    }
                    adapter.submitList(rows)
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun updateFiltersVisibility(expanded: Boolean) {
        // Smooth layout transition for the overall container
        TransitionManager.beginDelayedTransition(binding.root as ViewGroup, AutoTransition().apply {
            duration = 300
        })

        val filterRows = listOf(
            binding.scrollCategoryFilters,
            binding.scrollCourseTypeFilters,
            binding.scrollExpectationFilters
        )

        filterRows.forEachIndexed { index, view ->
            if (expanded) {
                // Staggered entrance: Fade in + slide up from a slight offset
                view.isVisible = true
                view.alpha = 0f
                view.translationY = 20f
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setStartDelay(index * 70L)
                    .start()
            } else {
                // Exit: Fade out quickly
                view.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .setStartDelay(0)
                    .withEndAction { view.isVisible = false }
                    .start()
            }
        }

        val rotation = if (expanded) 90f else 0f
        binding.btnExpandFilters.animate().rotation(rotation).setDuration(250).start()
        
        binding.btnExpandFilters.setBackgroundResource(
            if (expanded) R.drawable.bg_toggle_selected else R.drawable.bg_toggle_unselected_offset
        )
    }

    private fun selectInRow(all: List<TextView>, selected: TextView) {
        all.forEach { chip ->
            val isSelected = chip === selected
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_pill_selected else R.drawable.bg_pill_unselected_offset
            )
        }
    }

    private fun openSession(sessionId: String) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToSessionDetailFragment(
                sessionId = sessionId,
                viewMode = SessionViewMode.LIVE,
            )
        )
    }

    /** Open the session creation screen. */
    private fun openCreateSession() {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToCreateSessionFragment(
                prefillFromSessionId = null
            )
        )
    }

    override fun onDestroyView() {
        binding.etSearch.removeTextChangedListener(searchWatcher)
        searchWatcher = null
        binding.rvSessions.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
