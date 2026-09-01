package com.studyfinder.app.ui.inbox

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.studyfinder.app.databinding.FragmentInboxBinding
import com.studyfinder.app.model.InboxItem
import com.studyfinder.app.model.SessionViewMode
import com.studyfinder.app.ui.common.StateRenderer
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.setupHeader
import com.studyfinder.app.util.setupNavbar
import kotlinx.coroutines.launch

/**
 * Invites + notifications, merged into one screen (§7.8).
 */
class InboxFragment : Fragment() {

    private var _binding: FragmentInboxBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InboxViewModel by viewModels()

    private val adapter = InboxAdapter(
        onAccept = { item -> item.sessionId?.let { viewModel.accept(it, item.id) } },
        onDetails = ::onDetails,
        onMarkRead = { item -> viewModel.markRead(item.id) },
    )

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* reminders simply won't post if denied — no hard dependency (§9) */ }

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
        setupHeader(binding.header, "Inbox", showHistory = true, showBackBtn = false, showAvatar = false)

        binding.rvInbox.layoutManager = LinearLayoutManager(requireContext())
        binding.rvInbox.adapter = adapter
        binding.btnRetryInbox.setOnClickListener { viewModel.observeInbox() }

        maybeRequestNotificationPermission()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    StateRenderer.render(
                        state = state,
                        loadingView = binding.progressInbox,
                        emptyView = binding.tvEmptyInbox,
                        errorView = binding.layoutErrorInbox,
                        offlineView = binding.bannerOfflineInbox,
                        contentView = binding.rvInbox,
                    )
                    val rows = when (state) {
                        is UiState.Success -> state.data
                        is UiState.Offline -> state.cached
                        else -> emptyList()
                    }
                    adapter.submitList(rows)
                }
            }
        }
    }

    /**
     * §9: ask for POST_NOTIFICATIONS once, in context — the Inbox is where
     * session reminders and updates surface. Denial is harmless.
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return

        val prefs = requireContext()
            .getSharedPreferences("inbox_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_post_notifications", false)) return
        prefs.edit().putBoolean("asked_post_notifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun onDetails(item: InboxItem) {
        val sessionId = item.sessionId ?: return
        when (item.type) {
            com.studyfinder.app.model.InboxType.JOIN_REQUEST -> openManage(sessionId)
            else -> openSession(sessionId)
        }
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
        binding.rvInbox.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
