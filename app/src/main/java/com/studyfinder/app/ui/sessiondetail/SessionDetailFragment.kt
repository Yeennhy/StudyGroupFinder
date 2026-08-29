package com.studyfinder.app.ui.sessiondetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.studyfinder.app.databinding.FragmentSessionDetailBinding
import com.studyfinder.app.util.setupHeader

/**
 * Session detail (§7.3).
 *
 * Header: course, tag type, time, location, capacity as X/Y joined.
 * Body: description, goals/agenda, expectation level, member avatars.
 *
 * A realtime `addSnapshotListener` stays open while this screen is visible —
 * that is what makes a host's edit or cancellation appear live without any
 * push notification (§8).
 *
 * The action button is a nine-row state machine (§7.3) driven by one document
 * read, `members/{myUid}`. `viewMode = PAST` suppresses every action — the
 * spec's "past view mode", reached from History.
 */
class SessionDetailFragment : Fragment() {

    private var _binding: FragmentSessionDetailBinding? = null
    private val binding get() = _binding!!
    private val args: SessionDetailFragmentArgs by navArgs()
    private val viewModel: SessionDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSessionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader(binding.appHeader, "Session Details", showHistory = false, showBackBtn = true, showAvatar = false)
        // §7.3 Implementation: observe session + membership.
    }

    /** Host only — state-machine row 3. */
    private fun openManage() {
        findNavController().navigate(
            SessionDetailFragmentDirections
                .actionSessionDetailFragmentToSessionManageFragment(args.sessionId)
        )
    }

    /** Tapping a member avatar opens their read-only profile (§7.7). */
    private fun openMemberProfile(uid: String) {
        findNavController().navigate(
            SessionDetailFragmentDirections
                .actionSessionDetailFragmentToProfileFragment(uid)
        )
    }

    /**
     * "Continue from last time" — the spec puts this button inside past
     * session detail, and it also re-invites the original members (§7.6).
     */
    private fun continueFromThisSession() {
        findNavController().navigate(
            SessionDetailFragmentDirections
                .actionSessionDetailFragmentToCreateSessionFragment(args.sessionId)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
