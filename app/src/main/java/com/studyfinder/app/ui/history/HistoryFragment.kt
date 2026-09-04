package com.studyfinder.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentHistoryBinding
import com.studyfinder.app.model.SessionViewMode
import com.studyfinder.app.ui.common.StateRenderer
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.setupHeader
import com.studyfinder.app.util.applyFadeThroughTransitions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Session history.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()

    private val adapter = HistoryAdapter { session ->
        openPastSession(session.id)
    }

    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.exportPdfToUri(requireContext(), it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFadeThroughTransitions()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader(
            binding = binding.appHeader,
            title = "History",
            showHistory = false,
            showBackBtn = true,
            showAvatar = false
        )
        
        binding.btnExport.setOnClickListener {
            savePdfLauncher.launch("StudySessionHistory.pdf")
        }

        binding.rvHistory.layoutManager = LinearLayoutManager(context)
        binding.rvHistory.adapter = adapter

        binding.stateEmpty.tvStateEmptyMessage.setText(R.string.empty_history)
        binding.stateError.btnStateRetry.setOnClickListener { activity?.recreate() }

        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.historySessions.collectLatest { state ->
                    StateRenderer.render(
                        state = state,
                        loadingView = binding.stateLoading.root,
                        emptyView = binding.stateEmpty.root,
                        errorView = binding.stateError.root,
                        offlineView = binding.stateOfflineBanner.root,
                        contentView = binding.rvHistory,
                    )
                    val items = when (state) {
                        is UiState.Success -> state.data
                        is UiState.Offline -> state.cached
                        else -> emptyList()
                    }
                    adapter.submitList(items)
                }
            }
            launch {
                viewModel.exportResult.collectLatest { result ->
                    if (result is ActionResult.Success) {
                        viewModel.resetExportResult()
                        val bundle = Bundle().apply {
                            putString("message", "History Exported!")
                            putString("subtitle", "Your session history has been saved as PDF.")
                            putString("buttonText", "Back to History")
                            putBoolean("isSignupSuccess", false)
                        }
                        findNavController().navigate(R.id.action_historyFragment_to_successFragment, bundle)
                    }
                }
            }
        }
    }

    private fun openPastSession(sessionId: String) {
        findNavController().navigate(
            HistoryFragmentDirections.actionHistoryFragmentToSessionDetailFragment(
                sessionId = sessionId,
                viewMode = SessionViewMode.PAST,
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
