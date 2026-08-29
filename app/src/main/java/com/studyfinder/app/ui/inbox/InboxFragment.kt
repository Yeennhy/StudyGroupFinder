package com.studyfinder.app.ui.inbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.studyfinder.app.databinding.FragmentInboxBinding
import com.studyfinder.app.model.SessionViewMode
import com.studyfinder.app.util.setupHeader
import com.studyfinder.app.util.setupNavbar

/**
 * Invites + notifications, merged into one screen (§7.8).
 *
 * Per the spec an invite row carries **two** buttons — Accept (joins in
 * place) and Details (navigates to Session Detail). Not Accept/Decline.
 *
 * Because the merge also carries the edit/cancel/removal fan-out from §7.5,
 * this screen is **not** optional: without it a member is never told that a
 * session moved or was cancelled.
 */
class InboxFragment : Fragment() {

    private var _binding: FragmentInboxBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InboxViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNavbar(binding.navBar)
        setupHeader(binding.header, "Inbox", showHistory = true)
        // §7.8 Implementation: inbox list, hide blocked, etc.
    }

    private fun openSession(sessionId: String) {
        findNavController().navigate(
            InboxFragmentDirections.actionInboxFragmentToSessionDetailFragment(
                sessionId = sessionId,
                viewMode = SessionViewMode.LIVE,
            )
        )
    }

    /** `join_request` rows are host-facing and link to management (§7.8). */
    private fun openManage(sessionId: String) {
        findNavController().navigate(
            InboxFragmentDirections.actionInboxFragmentToSessionManageFragment(sessionId)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
