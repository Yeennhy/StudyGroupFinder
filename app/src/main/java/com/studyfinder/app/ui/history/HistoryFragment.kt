package com.studyfinder.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.studyfinder.app.databinding.FragmentHistoryBinding
import com.studyfinder.app.model.SessionViewMode
import com.studyfinder.app.util.setupHeader

/**
 * Session history (§7.6) — time, location and tags per row.
 *
 * Rows open Session Detail in **past view mode**, where every action button
 * is suppressed and the "continue from last time" button lives (§7.3 row 1).
 * Cancelled sessions appear struck through rather than vanishing.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()

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
        setupHeader(binding.appHeader, "History", showHistory = false)
        // §7.6 Implementation: past list.
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
