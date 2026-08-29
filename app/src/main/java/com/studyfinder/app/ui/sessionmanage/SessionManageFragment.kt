package com.studyfinder.app.ui.sessionmanage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.studyfinder.app.databinding.FragmentSessionManageBinding
import com.studyfinder.app.util.setupHeader

/**
 * Host-only management (§7.5).
 *
 * Pending requests (gated sessions only), edit, cancel, remove member,
 * invite by student ID, attach study material.
 *
 * ⚠️ Every notify path here writes into **other users'** inbox
 * subcollections. That is the one cross-user write the rules allow, and it is
 * create-only (§4). PERMISSION_DENIED here means the rules, not the code.
 */
class SessionManageFragment : Fragment() {

    private var _binding: FragmentSessionManageBinding? = null
    private val binding get() = _binding!!
    private val args: SessionManageFragmentArgs by navArgs()
    private val viewModel: SessionManageViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSessionManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader(binding.appHeader, "Manage Session", showHistory = false)
        // §7.5 Implementation: pending requests, roster, edit form, etc.
    }

    private fun openInviteByStudentId() {
        findNavController().navigate(
            SessionManageFragmentDirections
                .actionSessionManageFragmentToInviteByStudentIdFragment(args.sessionId)
        )
    }

    private fun openMemberProfile(uid: String) {
        findNavController().navigate(
            SessionManageFragmentDirections
                .actionSessionManageFragmentToProfileFragment(uid)
        )
    }

    /** Cancelling sets status = cancelled and fans out; the screen then pops. */
    private fun onSessionCancelled() {
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
