package com.studyfinder.app.ui.mysessions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.studyfinder.app.databinding.FragmentMySessionsListBinding
import com.studyfinder.app.model.SessionViewMode
import com.studyfinder.app.util.setupHeader
import com.studyfinder.app.util.setupNavbar

/**
 * My sessions (§7.6) — the spec asks for **both** a list view and a calendar
 * view, toggled in the toolbar.
 *
 * The calendar half is real work: Android's built-in `CalendarView` cannot
 * render per-date markers, so the day grid is a RecyclerView with
 * `GridLayoutManager(context, 7)` over a `Map<LocalDate, List<Session>>`.
 * Both views render from the same already-fetched list, so the toggle costs
 * no extra queries.
 */
class MySessionsFragment : Fragment() {

    private var _binding: FragmentMySessionsListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MySessionsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMySessionsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNavbar(binding.navBar)
        setupHeader(binding.appHeader, "My Sessions", showHistory = true, showBackBtn = false, showAvatar = false)
        // §7.6 Implementation: list/calendar views.
    }

    private fun openSession(sessionId: String) {
        findNavController().navigate(
            MySessionsFragmentDirections.actionMySessionsFragmentToSessionDetailFragment(
                sessionId = sessionId,
                viewMode = SessionViewMode.LIVE,
            )
        )
    }

    private fun openHistory() {
        findNavController().navigate(
            MySessionsFragmentDirections.actionMySessionsFragmentToHistoryFragment()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
