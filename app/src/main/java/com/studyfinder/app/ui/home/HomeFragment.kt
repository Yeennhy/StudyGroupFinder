package com.studyfinder.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
import com.studyfinder.app.model.SessionSort
import com.studyfinder.app.model.SessionViewMode
import com.studyfinder.app.model.TagType
import com.studyfinder.app.ui.common.StateRenderer
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.setupHeader
import com.studyfinder.app.util.setupNavbar
import kotlinx.coroutines.launch

/**
 * Home / Upcoming sessions — the browse-and-join lobby (§7.2).
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
        binding.btnRetryHome.setOnClickListener { viewModel.retry() }

        wireSearch()
        wireSessionTypeChips()
        wireCourseTypeChips()
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

    private fun wireToggleAndSort() {
        binding.toggleConflicting.setOnClickListener {
            val next = binding.toggleConflicting.tag != true
            binding.toggleConflicting.tag = next
            binding.toggleConflicting.setBackgroundResource(
                if (next) R.drawable.bg_toggle_selected else R.drawable.bg_toggle_unselected
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
                    (anchor as TextView).text = item.title
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

    private fun fetchLocationThenSort() {
        val client = LocationServices.getFusedLocationProviderClient(requireContext())
        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) viewModel.sortByDistance(loc.latitude, loc.longitude)
                    else viewModel.onLocationPermissionDenied()
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
                viewModel.state.collect { state ->
                    StateRenderer.render(
                        state = state,
                        loadingView = binding.progressHome,
                        emptyView = binding.tvEmptyHome,
                        errorView = binding.layoutErrorHome,
                        offlineView = binding.bannerOfflineHome,
                        contentView = binding.rvSessions,
                    )
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

    private fun selectInRow(all: List<TextView>, selected: TextView) {
        all.forEach { chip ->
            val isSelected = chip === selected
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_pill_selected else R.drawable.bg_pill_unselected
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

    /** The spec's "+ button from Home" (§7.4). */
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
