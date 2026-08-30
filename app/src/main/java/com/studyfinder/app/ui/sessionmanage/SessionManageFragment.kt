package com.studyfinder.app.ui.sessionmanage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
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
        onRemove = { viewModel.removeMember(it.uid) },
        onClick = { openMemberProfile(it.uid) }
    )

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

        setupRecyclerViews()
        viewModel.start(args.sessionId)

        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.session.collectLatest { state ->
                    if (state is UiState.Success && viewModel.pendingSession.value == null) {
                        bindSession(state.data)
                    }
                }
            }
            launch {
                viewModel.pendingSession.collectLatest { pending ->
                    pending?.let { bindSession(it) }
                }
            }
            launch {
                viewModel.pendingRequests.collectLatest { state ->
                    if (state is UiState.Success) {
                        requestAdapter.submitList(state.data)
                        binding.cardPendingRq.isVisible = state.data.isNotEmpty()
                    }
                }
            }
            launch {
                viewModel.members.collectLatest { state ->
                    if (state is UiState.Success) {
                        val sessionState = viewModel.session.value
                        if (sessionState is UiState.Success) {
                            val hostUid = sessionState.data.hostUid
                            val nonHostMembers = state.data.filter { it.uid != hostUid }
                            memberAdapter.submitList(nonHostMembers)
                            
                            val hostMember = state.data.find { it.uid == hostUid }
                            hostMember?.let { bindHost(it) }
                        }
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
                Glide.with(this).load(url).circleCrop().into(binding.ivAvatar)
            }
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
            session.courseCategory.wire.let { addTag(it, R.color.ginkgo_yellow) }
            session.tagType.wire.let { addTag(it, R.color.light_blue) }
            
            tvAttendeesCount.text = "Attendees (${session.joinedCount}/${session.capacity})"
            
            val isGated = session.mode == SessionMode.GATED
            toggleOpenToAll.setBackgroundResource(if (!isGated) R.drawable.bg_segment_manage_selected else android.R.color.transparent)
            toggleOnlyRequests.setBackgroundResource(if (isGated) R.drawable.bg_segment_manage_selected else android.R.color.transparent)
        }
    }

    private fun addTag(text: String, colorRes: Int) {
        val chip = Chip(requireContext()).apply {
            this.text = text
            setChipBackgroundColorResource(colorRes)
            setTextColor(requireContext().getColor(R.color.graphite))
            chipStrokeWidth = 3.5f * resources.displayMetrics.density
            setChipStrokeColorResource(R.color.graphite)
            chipCornerRadius = 99f * resources.displayMetrics.density
            isCloseIconVisible = false
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
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
