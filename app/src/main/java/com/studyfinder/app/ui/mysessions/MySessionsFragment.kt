package com.studyfinder.app.ui.mysessions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentMySessionsBinding
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionViewMode
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.setupHeader
import com.studyfinder.app.util.setupNavbar
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * My sessions (§7.6).
 */
class MySessionsFragment : Fragment() {

    private var _binding: FragmentMySessionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MySessionsViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()

    private val listAdapter = MySessionListAdapter { session -> handleSessionClick(session) }
    private val calendarDayAdapter = CalendarDayAdapter { date -> viewModel.selectDate(date) }
    private val calendarSessionAdapter = CalendarSessionAdapter { session -> handleSessionClick(session) }

    private fun handleSessionClick(session: Session) {
        val currentUid = auth.currentUser?.uid
        if (session.hostUid == currentUid) {
            findNavController().navigate(
                MySessionsFragmentDirections.actionMySessionsFragmentToSessionManageFragment(session.id)
            )
        } else {
            findNavController().navigate(
                MySessionsFragmentDirections.actionMySessionsFragmentToSessionDetailFragment(session.id, SessionViewMode.LIVE)
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMySessionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupNavbar(binding.navBar)
        setupHeader(binding.appHeader, "My Sessions", showHistory = true, showBackBtn = false, showAvatar = false) {
            openHistory()
        }

        setupListView()
        setupCalendarView()

        val pjsansBold = ResourcesCompat.getFont(requireContext(), R.font.pjsans_bold)
        val pjsansRegular = ResourcesCompat.getFont(requireContext(), R.font.pjsans_regular)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.viewType.collect { type ->
                val isList = type == MySessionsViewModel.ViewType.LIST
                binding.groupList.isVisible = isList
                binding.groupCalendar.isVisible = !isList
                
                binding.toggleListView.setBackgroundResource(if (isList) R.drawable.bg_segment_myses else 0)
                binding.toggleListView.setTextColor(context?.getColor(if (isList) R.color.graphite else R.color.light_graphite)!!)
                binding.toggleListView.typeface = if (isList) pjsansBold else pjsansRegular

                binding.toggleCalendar.setBackgroundResource(if (!isList) R.drawable.bg_segment_myses else 0)
                binding.toggleCalendar.setTextColor(context?.getColor(if (!isList) R.color.graphite else R.color.light_graphite)!!)
                binding.toggleCalendar.typeface = if (!isList) pjsansBold else pjsansRegular
            }
        }

        binding.toggleListView.setOnClickListener { viewModel.setViewType(MySessionsViewModel.ViewType.LIST) }
        binding.toggleCalendar.setOnClickListener { viewModel.setViewType(MySessionsViewModel.ViewType.CALENDAR) }
    }

    private fun setupListView() {
        binding.rvSessions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = listAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.listItems.collect { state ->
                if (state is UiState.Success) {
                    listAdapter.submitList(state.data)
                }
            }
        }
    }

    private fun setupCalendarView() {
        binding.rvCalendarDays.apply {
            layoutManager = GridLayoutManager(context, 7)
            adapter = calendarDayAdapter
        }

        binding.rvSelectedDateSessions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = calendarSessionAdapter
        }

        binding.btnNextMonth.setOnClickListener { viewModel.nextMonth() }
        binding.btnPrevMonth.setOnClickListener { viewModel.prevMonth() }

        val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
        
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.currentMonth.collect { month ->
                    binding.tvMonthYear.text = month.format(monthFormatter)
                }
            }
            launch {
                viewModel.calendarDays.collect { days ->
                    calendarDayAdapter.submitList(days)
                }
            }
            launch {
                viewModel.selectedDate.collect { date ->
                    calendarDayAdapter.setSelected(date)
                    binding.rowSelectedDate.tvDateLabel.text = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
                }
            }
            launch {
                viewModel.selectedDateSessions.collect { sessions ->
                    calendarSessionAdapter.submitList(sessions)
                }
            }
        }
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
