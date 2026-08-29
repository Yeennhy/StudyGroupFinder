package com.studyfinder.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.studyfinder.app.databinding.FragmentHomeBinding
import com.studyfinder.app.model.SessionViewMode
import com.studyfinder.app.util.setupHeader
import com.studyfinder.app.util.setupNavbar

/**
 * Home / Upcoming sessions — the browse-and-join lobby (§7.2).
 *
 * Carries, in one screen: community-scoped list, sort by time / expectation
 * level / distance, search by course ID, two independent filter chip groups
 * (session type and course type), blocked-user hiding, overlap greying, and
 * the `+` FAB entry point to Create Session.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

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
        // §7.2 Implementation: adapter, sort toggle, search, both chip groups, etc.
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
        super.onDestroyView()
        _binding = null
    }
}
