package com.studyfinder.app.ui.sessionmanage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.studyfinder.app.util.applyFadeThroughTransitions
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentSessionManageBinding
import com.studyfinder.app.model.MemberStatus
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionMember
import com.studyfinder.app.util.ActionResult
import com.bumptech.glide.Glide
import com.studyfinder.app.model.SessionMode
import com.studyfinder.app.util.DateTimeUtils
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.setupHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Host-only management (§7.5).
 */
class SessionManageFragment : Fragment() {

    private var _binding: FragmentSessionManageBinding? = null
    private val binding get() = _binding!!
    private val args: SessionManageFragmentArgs by navArgs()
    val viewModel: SessionManageViewModel by viewModels()

    private val requestAdapter = PendingRequestAdapter(
        onApprove = { viewModel.approve(it.uid) },
        onReject = { viewModel.reject(it.uid) }
    )

    private val memberAdapter = ManageMemberAdapter(
        onRemove = { showRemoveMemberConfirmation(it) },
        onClick = { openMemberProfile(it.uid) }
    )

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        applyFadeThroughTransitions()
    }

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
        setupHeader(binding.appHeader, "Manage Session", showHistory = false, showBackBtn = true, showAvatar = false)
        
        // Custom back button logic
        binding.appHeader.backBtnContainer.setOnClickListener {
            handleBackNavigation()
        }
        
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        setupRecyclerViews()
        viewModel.start(args.sessionId)

        binding.stateEmpty.tvStateEmptyMessage.setText(R.string.empty_session_detail)
        binding.stateError.btnStateRetry.setOnClickListener { viewModel.start(args.sessionId) }

        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.isLoading.collectLatest { loading ->
                    binding.actionLoadingOverlay.isVisible = loading
                }
            }
            launch {
                viewModel.session.collectLatest { state ->
                    com.studyfinder.app.ui.common.StateRenderer.render(
                        state = state,
                        loadingView = binding.stateLoading.root,
                        emptyView = binding.stateEmpty.root,
                        errorView = binding.stateError.root,
                        offlineView = binding.stateOfflineBanner.root,
                        contentView = null,
                    )
                    
                    val session = (state as? UiState.Success)?.data ?: (state as? UiState.Offline)?.cached
                    if (session?.status == com.studyfinder.app.model.SessionStatus.CANCELLED) {
                        onSessionCancelled()
                    }
                }
            }
            launch {
                combine(viewModel.session, viewModel.pendingSession, viewModel.removedMemberUids) { original, pending, removed ->
                    Triple(original, pending, removed)
                }.collectLatest { (original, pending, _) ->
                    val sessionToBind = pending ?: (original as? UiState.Success)?.data
                    sessionToBind?.let { bindSession(it) }
                }
            }
            launch {
                viewModel.pendingRequests.collectLatest { state ->
                    if (state is UiState.Success) {
                        requestAdapter.submitList(state.data)
                        binding.cardPendingRq.isVisible = state.data.isNotEmpty()
                        binding.tvPendingCount.text = "Pending Requests (${state.data.size})"
                    }
                }
            }
            launch {
                combine(viewModel.members, viewModel.removedMemberUids, viewModel.session) { membersState, removedUids, sessionState ->
                    Triple(membersState, removedUids, sessionState)
                }.collectLatest { (membersState, removedUids, sessionState) ->
                    if (membersState is UiState.Success && sessionState is UiState.Success) {
                        val hostUid = sessionState.data.hostUid
                        val acceptedMembers = membersState.data.filter { 
                            it.uid != hostUid && 
                            (it.status == MemberStatus.ACCEPTED || it.status == MemberStatus.ADMIN) &&
                            !removedUids.contains(it.uid)
                        }
                        memberAdapter.submitList(acceptedMembers)
                        
                        val hostMember = membersState.data.find { it.uid == hostUid }
                        hostMember?.let { bindHost(it) }

                        val totalCount = membersState.data.count { 
                            (it.status == MemberStatus.ACCEPTED || it.status == MemberStatus.ADMIN) &&
                            !removedUids.contains(it.uid)
                        }
                        binding.tvAttendeesCount.text = getString(R.string.attendees_count_format, totalCount, sessionState.data.capacity)
                    }
                }
            }
            launch {
                viewModel.actionResult.collectLatest { result ->
                    if (result is ActionResult.Success) {
                        Toast.makeText(context, "Action successful", Toast.LENGTH_SHORT).show()
                        viewModel.resetActionResult()
                    } else if (result is ActionResult.Failure) {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        viewModel.resetActionResult()
                    }
                }
            }
        }

        binding.btnCancelSession.setOnClickListener {
            showCancelConfirmation()
        }
        
        binding.btnSave.setOnClickListener {
            showFinishConfirmation()
        }
        
        binding.btnEditSession.setOnClickListener {
            openEditDialog()
        }

        binding.rowInviteStudents.setOnClickListener {
            openInviteByStudentId()
        }

        binding.toggleOpenToAll.setOnClickListener {
            updateSessionMode(SessionMode.OPEN)
        }

        binding.toggleOnlyRequests.setOnClickListener {
            updateSessionMode(SessionMode.GATED)
        }
    }

    private fun showRemoveMemberConfirmation(member: SessionMember) {
        val dialog = ConfirmationDialogFragment.newInstance(
            title = "Remove Member?",
            subtitle = "Are you sure you want to remove ${member.profile?.name ?: "this student"}? They will be notified.",
            buttonText = "Remove",
            iconRes = R.drawable.ic_x,
            iconBgColor = requireContext().getColor(R.color.theme_clay),
            confirmBtnBgRes = R.drawable.bg_clay_btn,
            goBackBtnBgRes = R.drawable.bg_yellow_btn
        )
        dialog.setOnConfirmListener {
            viewModel.removeMember(member.uid)
        }
        dialog.show(childFragmentManager, "RemoveMember")
    }

    private fun handleBackNavigation() {
        if (viewModel.hasUnsavedChanges()) {
            val dialog = ConfirmationDialogFragment.newInstance(
                title = "Discard Changes?",
                subtitle = "You have unsaved changes. Do you want to save them before leaving?",
                buttonText = "Save",
                goBackText = "Discard",
                iconRes = R.drawable.ic_warning,
                iconBgColor = requireContext().getColor(R.color.ginkgo_yellow),
                confirmBtnBgRes = R.drawable.bg_yellow_btn,
                goBackBtnBgRes = R.drawable.bg_yellow_btn
            )
            dialog.setOnConfirmListener {
                viewModel.submitChanges()
                findNavController().popBackStack()
            }
            dialog.setOnGoBackListener {
                findNavController().popBackStack()
            }
            dialog.show(childFragmentManager, "DiscardChanges")
        } else {
            findNavController().popBackStack()
        }
    }

    private fun showCancelConfirmation() {
        val dialog = ConfirmationDialogFragment.newInstance(
            title = "Cancel Session?",
            subtitle = "This will notify all members and stop any further activity.",
            buttonText = "Cancel Session",
            iconRes = R.drawable.ic_x,
            iconBgColor = requireContext().getColor(R.color.theme_clay),
            confirmBtnBgRes = R.drawable.bg_clay_btn,
            goBackBtnBgRes = R.drawable.bg_yellow_btn
        )
        dialog.setOnConfirmListener {
            viewModel.cancelSession()
        }
        dialog.show(childFragmentManager, "CancelConfirm")
    }

    private fun showFinishConfirmation() {
        val dialog = ConfirmationDialogFragment.newInstance(
            title = "Save Changes?",
            subtitle = "This will update the study session details for all members.",
            buttonText = "Save",
            iconRes = R.drawable.ic_tick,
            iconBgColor = requireContext().getColor(R.color.theme_green),
            confirmBtnBgRes = R.drawable.bg_yellow_btn,
            goBackBtnBgRes = R.drawable.bg_yellow_btn
        )
        dialog.setOnConfirmListener {
            viewModel.submitChanges()
        }
        dialog.show(childFragmentManager, "FinishConfirm")
    }

    private fun updateSessionMode(mode: SessionMode) {
        val sessionState = viewModel.session.value
        if (sessionState is UiState.Success) {
            val updated = sessionState.data.copy(mode = mode)
            viewModel.saveEdits(updated)
        }
    }

    private fun setupRecyclerViews() {
        binding.rvPendingRequests.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = requestAdapter
        }
        binding.rvAttendees.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = memberAdapter
        }
    }

    private fun bindHost(member: SessionMember) {
        binding.tvHostName.text = "${member.profile?.name ?: "Unknown"} (Host)"
        binding.tvHostId.text = member.profile?.studentId ?: member.uid
        
        member.profile?.photoUrl?.let { url ->
            if (url.isNotBlank()) {
                binding.ivAvatar.setPadding(0, 0, 0, 0)
                Glide.with(this).load(url).circleCrop().into(binding.ivAvatar)
            } else {
                val p = (10 * resources.displayMetrics.density).toInt()
                binding.ivAvatar.setPadding(p, p, p, p)
                binding.ivAvatar.setImageResource(R.drawable.ic_profile)
            }
        } ?: run {
            val p = (10 * resources.displayMetrics.density).toInt()
            binding.ivAvatar.setPadding(p, p, p, p)
            binding.ivAvatar.setImageResource(R.drawable.ic_profile)
        }
    }

    private fun bindSession(session: Session) {
        binding.apply {
            tvSessionTitle.text = session.title
            tvSessionTime.text = DateTimeUtils.formatTime(session.startTimeMillis)
            tvSessionLocation.text = session.locationName
            
            val durationMinutes = ((session.endTimeMillis - session.startTimeMillis) / 60000).toInt()
            tvSessionDuration.text = DateTimeUtils.formatDuration(durationMinutes)

            tagContainerInfo.removeAllViews()
            addTag(session.courseCategory.wire)
            addTag(session.tagType.wire)
            session.tags.forEach { addTag(it) }
            
            val isGated = session.mode == SessionMode.GATED
            toggleOpenToAll.setBackgroundResource(if (!isGated) R.drawable.bg_segment_manage_selected else android.R.color.transparent)
            toggleOnlyRequests.setBackgroundResource(if (isGated) R.drawable.bg_segment_manage_selected else android.R.color.transparent)
        }
    }

    private fun addTag(text: String) {
        val tagColors = listOf(
            R.color.ginkgo_yellow, R.color.theme_blue, R.color.light_blue,
            R.color.light_graphite, R.color.deep_red, R.color.theme_clay,
            R.color.theme_red, R.color.theme_green, R.color.theme_gray,
            R.color.theme_teal, R.color.theme_cream, R.color.gray_dot,
            R.color.brown_dot, R.color.graphite_10, R.color.activity_mid
        )
        val randomColor = tagColors.random()

        val chip = Chip(requireContext()).apply {
            this.text = text
            setChipBackgroundColorResource(randomColor)
            setTextColor(requireContext().getColor(R.color.graphite))
            chipStrokeWidth = 2.0f * resources.displayMetrics.density
            setChipStrokeColorResource(R.color.graphite)
            chipCornerRadius = 99f * resources.displayMetrics.density
            isCloseIconVisible = false
            typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.pjsans_bold)
            chipMinHeight = 36f * resources.displayMetrics.density
            setEnsureMinTouchTargetSize(false)
        }
        binding.tagContainerInfo.addView(chip)
    }

    private fun openEditDialog() {
        val sessionState = viewModel.session.value
        if (sessionState is UiState.Success) {
            val dialog = SessionEditFragment.newInstance(sessionState.data)
            dialog.show(childFragmentManager, "SessionEditFragment")
        }
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
        findNavController().navigate(
            R.id.homeFragment,
            null,
            androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
