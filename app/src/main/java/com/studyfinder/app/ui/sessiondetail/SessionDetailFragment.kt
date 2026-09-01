package com.studyfinder.app.ui.sessiondetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.studyfinder.app.R
import com.studyfinder.app.ServiceLocator
import com.studyfinder.app.databinding.FragmentSessionDetailBinding
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.SessionMember
import com.studyfinder.app.model.SessionViewMode
import com.studyfinder.app.ui.sessiondetail.SessionDetailViewModel.ActionState
import com.studyfinder.app.ui.sessionmanage.ConfirmationDialogFragment
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.DateTimeUtils
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.setupHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Session detail (§7.3).
 */
class SessionDetailFragment : Fragment() {

    private var _binding: FragmentSessionDetailBinding? = null
    private val binding get() = _binding!!
    private val args: SessionDetailFragmentArgs by navArgs()
    private val viewModel: SessionDetailViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()

    private val attendeeAdapter = AttendeeAdapter { openMemberProfile(it.uid) }
    private val pendingRequestAdapter = AttendeeAdapter { openMemberProfile(it.uid) }
    private val materialAdapter = MaterialAdapter { /* TODO: Open URL */ }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.attachMaterial(it) }
    }

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

        setupRecyclerViews()
        setupListeners()
        viewModel.start(args.sessionId, args.viewMode)

        binding.stateEmpty.tvStateEmptyMessage.setText(R.string.empty_session_detail)
        binding.stateError.btnStateRetry.setOnClickListener {
            viewModel.start(args.sessionId, args.viewMode)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.session.collectLatest { state ->
                    com.studyfinder.app.ui.common.StateRenderer.render(
                        state = state,
                        loadingView = binding.stateLoading.root,
                        emptyView = binding.stateEmpty.root,
                        errorView = binding.stateError.root,
                        offlineView = binding.stateOfflineBanner.root,
                        contentView = binding.scrollContent,
                    )
                    when (state) {
                        is UiState.Success -> bindSession(state.data)
                        is UiState.Offline -> bindSession(state.cached)
                        else -> {}
                    }
                }
            }
            launch {
                viewModel.actionState.collectLatest { state ->
                    bindActionButton(state)
                }
            }
            launch {
                combine(
                    viewModel.session,
                    viewModel.members
                ) { sessionState, membersState ->
                    Pair(sessionState, membersState)
                }.collectLatest { (sessionState, membersState) ->
                    val sessionData = when (sessionState) {
                        is UiState.Success -> sessionState.data
                        is UiState.Offline -> sessionState.cached
                        else -> null
                    }
                    
                    if (membersState is UiState.Success<List<SessionMember>> && sessionData != null) {
                        val hostUid = sessionData.hostUid
                        val attendees = membersState.data.filter { 
                            it.uid != hostUid && it.uid in sessionData.memberUids
                        }
                        attendeeAdapter.submitList(attendees)
                        
                        val pending = membersState.data.filter { 
                            it.status == com.studyfinder.app.model.MemberStatus.PENDING
                        }
                        pendingRequestAdapter.submitList(pending)
                        binding.cardPendingRq.isVisible = pending.isNotEmpty()
                        binding.tvPendingCount.text = "Pending Requests (${pending.size})"
                        
                        val host = membersState.data.find { it.uid == hostUid }
                        host?.let { bindHost(it) }

                        updateAttendeeCount(sessionData.joinedCount, sessionData.capacity)
                    }
                }
            }
            launch {
                viewModel.actionResult.collectLatest { result ->
                    if (result is ActionResult.Success) {
                        Toast.makeText(context, "Success", Toast.LENGTH_SHORT).show()
                        viewModel.resetActionResult()
                    } else if (result is ActionResult.Failure) {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        viewModel.resetActionResult()
                    }
                }
            }
            launch {
                ServiceLocator.profileRepository.observeBlockedUids().collectLatest { blocked ->
                    attendeeAdapter.setBlockedUids(blocked)
                    pendingRequestAdapter.setBlockedUids(blocked)
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        binding.rvAttendees.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = attendeeAdapter
        }
        binding.rvPendingRequests.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pendingRequestAdapter
        }
        binding.rvMaterials.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = materialAdapter
        }
    }

    private fun setupListeners() {
        binding.uploadBtn.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }
        binding.rowInviteStudents.setOnClickListener {
            openInviteByStudentId()
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
            addTag(session.courseCategory.wire, R.color.ginkgo_yellow)
            addTag(session.tagType.wire, R.color.light_blue)

            tvSessionDescription.text = session.description
            tvAgenda.text = session.goals
            
            materialAdapter.submitList(session.materialUrls)

            val isHost = session.hostUid == auth.currentUser?.uid
            uploadBtnContainer.isVisible = isHost
            rowInviteStudents.isVisible = isHost
        }
    }

    private fun updateAttendeeCount(acceptedCount: Int, capacity: Int) {
        binding.tvAttendeesCount.text = getString(R.string.attendees_count_format, acceptedCount, capacity)
    }

    private fun bindHost(member: SessionMember) {
        binding.apply {
            tvHostName.text = getString(R.string.host_name_format, member.profile?.name ?: "Unknown")
            tvHostId.text = member.profile?.studentId ?: member.uid
            
            member.profile?.photoUrl?.let { url ->
                if (url.isNotBlank()) {
                    Glide.with(this@SessionDetailFragment).load(url).circleCrop().into(ivAvatar)
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_profile)
                }
            } ?: ivAvatar.setImageResource(R.drawable.ic_profile)

            rowHost.setOnClickListener { openMemberProfile(member.uid) }
        }
    }

    private fun addTag(text: String, colorRes: Int) {
        val chip = Chip(requireContext()).apply {
            this.text = text
            setChipBackgroundColorResource(colorRes)
            setTextColor(requireContext().getColor(R.color.graphite))
            chipStrokeWidth = 2.5f
            setChipStrokeColorResource(R.color.graphite)
            chipCornerRadius = 20f
            isCloseIconVisible = false
        }
        binding.tagContainerInfo.addView(chip)
    }

    private fun bindActionButton(state: ActionState) {
        binding.btnJoinSession.apply {
            isEnabled = true
            alpha = 1.0f
            setBackgroundResource(R.drawable.bg_yellow_btn)

            when (state) {
                ActionState.PastView -> {
                    text = "Pick up Session"
                    setOnClickListener { continueFromThisSession() }
                }
                ActionState.Cancelled -> {
                    text = "Cancelled by Host"
                    isEnabled = false
                    alpha = 0.5f
                    setBackgroundResource(R.drawable.bg_gray_btn)
                }
                ActionState.Manage -> {
                    text = "Manage Session"
                    setOnClickListener { openManage() }
                }
                ActionState.AcceptInvite -> {
                    text = "Accept Invite"
                    setOnClickListener { viewModel.acceptInvite() }
                }
                ActionState.Leave -> {
                    text = "Leave Session"
                    setBackgroundResource(R.drawable.bg_clay_btn)
                    setOnClickListener { viewModel.leave() }
                }
                ActionState.RequestPending -> {
                    text = "Request Pending"
                    isEnabled = false
                    setBackgroundResource(R.drawable.bg_gray_btn)
                }
                ActionState.Full -> {
                    text = "Session Full"
                    isEnabled = false
                    setBackgroundResource(R.drawable.bg_gray_btn)
                }
                ActionState.Blocked -> {
                    text = "Contains Blocked User"
                    isEnabled = false
                    setBackgroundResource(R.drawable.bg_gray_btn)
                }
                ActionState.Join -> {
                    text = "Join Session"
                    setOnClickListener { viewModel.join() }
                }
                ActionState.RequestToJoin -> {
                    text = "Request to Join"
                    setOnClickListener { viewModel.requestToJoin() }
                }
            }
        }
    }

    private fun openInviteByStudentId() {
        findNavController().navigate(
            SessionDetailFragmentDirections
                .actionSessionDetailFragmentToInviteByStudentIdFragment(args.sessionId)
        )
    }

    private fun openManage() {
        findNavController().navigate(
            SessionDetailFragmentDirections
                .actionSessionDetailFragmentToSessionManageFragment(args.sessionId)
        )
    }

    private fun openMemberProfile(uid: String) {
        findNavController().navigate(
            SessionDetailFragmentDirections
                .actionSessionDetailFragmentToProfileFragment(uid)
        )
    }

    private fun continueFromThisSession() {
        ConfirmationDialogFragment.newInstance(
            title = "Pick up Session?",
            subtitle = "A new session will be created with the same details. All previous members will be automatically invited.",
            buttonText = "Continue",
            iconRes = R.drawable.ic_history,
            iconBgColor = requireContext().getColor(R.color.ginkgo_yellow)
        ).apply {
            setOnConfirmListener {
                findNavController().navigate(
                    SessionDetailFragmentDirections
                        .actionSessionDetailFragmentToCreateSessionFragment(args.sessionId)
                )
            }
        }.show(parentFragmentManager, "PickUpConfirmation")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
