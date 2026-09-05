package com.studyfinder.app.ui.community

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.studyfinder.app.util.applyFadeThroughTransitions
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentCommunitySelectionBinding
import com.studyfinder.app.ui.common.StateRenderer
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.setupHeader
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CommunitySelectionFragment : Fragment() {

    private var _binding: FragmentCommunitySelectionBinding? = null
    private val binding get() = _binding!!
    private val args: CommunitySelectionFragmentArgs by navArgs()
    private val viewModel: CommunityViewModel by viewModels()

    private val adapter = CommunityListAdapter { community -> viewModel.join(community.id) }

    private var searchWatcher: TextWatcher? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        applyFadeThroughTransitions()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCommunitySelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader(
            binding.appHeader,
            "Select Community",
            showHistory = false,
            showBackBtn = args.isEditMode,
            showAvatar = false,
        )

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val spanCount = if (isLandscape) 3 else 2
        binding.rvCommunities.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.rvCommunities.adapter = adapter
        binding.stateError.btnStateRetry.setOnClickListener { viewModel.loadAllViaRest() }

        wireSearch()
        observeState()
        observeCities()
        observeJoin()
    }

    private fun wireSearch() {
        searchWatcher = object : TextWatcher {
            private val runnable = Runnable {
                // Both search + city filter run client-side over the REST list.
                viewModel.search(binding.etSearch.text?.toString().orEmpty())
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

    private fun observeCities() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.cities, viewModel.selectedCity) { cities, selected ->
                    renderCityChips(cities, selected)
                }.collect { }
            }
        }
    }

    private fun renderCityChips(cities: List<String>, selectedCity: String?) {
        val row = binding.rowCityChips as ViewGroup
        row.removeAllViews()
        val labels = listOf(getString(R.string.city_all)) + cities
        labels.forEach { label ->
            val isAll = label == getString(R.string.city_all)
            val selected = (isAll && selectedCity == null) || label == selectedCity
            row.addView(TextView(requireContext()).apply {
                text = label
                setBackgroundResource(
                    if (selected) R.drawable.bg_pill_selected else R.drawable.bg_pill_unselected_offset
                )
                setTextColor(ContextCompat.getColor(requireContext(), R.color.graphite))
                typeface = ResourcesCompat.getFont(requireContext(), R.font.pjsans_bold)
                gravity = Gravity.CENTER
                
                val d = resources.displayMetrics.density
                setPadding(
                    (16 * d).toInt(),
                    (8 * d).toInt(),
                    (19 * d).toInt(),
                    (11 * d).toInt()
                )
                val lp = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                lp.marginStart = if (isAll) 0 else 16
                layoutParams = lp
                setOnClickListener {
                    val nextCity = if (isAll) null else label
                    binding.etSearch.text?.clear()
                    viewModel.filterByCity(nextCity)
                }
            })
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    StateRenderer.render(
                        state = state,
                        loadingView = binding.stateLoading.root,
                        emptyView = binding.stateEmpty.root,
                        errorView = binding.stateError.root,
                        offlineView = binding.stateOfflineBanner.root,
                        contentView = binding.rvCommunities,
                        transitionContainer = binding.layoutCommunityListContainer
                    )
                    
                    binding.stateError.tvStateErrorDetail.apply {
                        if (state is UiState.Error) {
                            text = state.message
                            visibility = View.VISIBLE
                        } else {
                            visibility = View.GONE
                        }
                    }

                    val list = when (state) {
                        is UiState.Success -> state.data
                        is UiState.Offline -> state.cached
                        else -> emptyList()
                    }
                    adapter.submitList(list)
                }
            }
        }
    }

    private fun observeJoin() {
        viewModel.joinResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ActionResult.Success -> {
                    viewModel.clearJoinResult()
                    onJoined()
                }
                is ActionResult.Failure -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    viewModel.clearJoinResult()
                }
                ActionResult.Idle -> Unit
            }
        }
    }

    /** Join succeeded. First-time users continue; editors just go back. */
    private fun onJoined() {
        if (args.isEditMode) {
            findNavController().popBackStack()
        } else {
            findNavController().navigate(
                CommunitySelectionFragmentDirections
                    .actionCommunitySelectionFragmentToHomeFragment()
            )
        }
    }

    override fun onDestroyView() {
        binding.etSearch.removeTextChangedListener(searchWatcher)
        searchWatcher = null
        binding.rvCommunities.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
