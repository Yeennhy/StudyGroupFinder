package com.studyfinder.app.ui.sessioncreate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.studyfinder.app.databinding.FragmentCreateSessionBinding
import com.studyfinder.app.util.setupHeader
import com.studyfinder.app.model.SessionViewMode

/**
 * Create session (§7.4). Reached from Home's `+` FAB, or from a past session
 * via "continue from last time" (§7.6), in which case
 * [CreateSessionFragmentArgs.prefillFromSessionId] is non-null.
 *
 * Time input is a start plus a **duration**, not two independent date-times —
 * the form stores the computed `endTime`, which the overlap check (§7.2) and
 * the past/upcoming split (§7.6) both depend on.
 */
class CreateSessionFragment : Fragment() {

    private var _binding: FragmentCreateSessionBinding? = null
    private val binding get() = _binding!!
    private val args: CreateSessionFragmentArgs by navArgs()
    private val viewModel: CreateSessionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCreateSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader(binding.appHeader, "Create Session", showHistory = false)
        // §7.4 Implementation: course dropdown, chips, date/time pickers.
    }

    /** Created — replace this screen in the back stack with the new session. */
    private fun onCreated(sessionId: String) {
        findNavController().navigate(
            CreateSessionFragmentDirections
                .actionCreateSessionFragmentToSessionDetailFragment(
                    sessionId = sessionId,
                    viewMode = SessionViewMode.LIVE,
                )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
