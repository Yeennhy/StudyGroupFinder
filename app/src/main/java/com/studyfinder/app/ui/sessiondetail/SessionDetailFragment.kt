package com.studyfinder.app.ui.sessiondetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.studyfinder.app.util.applyFadeThroughTransitions
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
    private lateinit var materialAdapter: MaterialAdapter

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.attachMaterial(it, requireContext()) }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        applyFadeThroughTransitions()
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

        materialAdapter = MaterialAdapter(
            onClick = { openMaterialUrl(it) },
            onDelete = { url -> showDeleteMaterialConfirmation(url) }
        )

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
                        is UiState.Success -> {
                            bindSession(state.data)
                            // Only redirect to history if we are in LIVE mode and the session just finished.
                            // If we are already in PAST mode (viewing history), don't redirect.
                            if (args.viewMode == SessionViewMode.LIVE && 
                                state.data.status == com.studyfinder.app.model.SessionStatus.FINISHED) {
                                redirectToHistory()
                            }
                        }
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
                    viewModel.isLoading,
                    viewModel.members
                ) { loading, membersState ->
                    loading || membersState is UiState.Loading
                }.collectLatest { showOverlay ->
                    binding.actionLoadingOverlay.isVisible = showOverlay
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

                    val membersList = when (membersState) {
                        is UiState.Success -> membersState.data
                        is UiState.Offline -> membersState.cached
                        else -> null
                    }
                    
                    if (membersList != null && sessionData != null) {
                        val hostUid = sessionData.hostUid
                        val attendees = membersList.filter { 
                            it.uid != hostUid && it.uid in sessionData.memberUids
                        }
                        attendeeAdapter.submitList(attendees)
                        
                        val pending = membersList.filter { 
                            it.status == com.studyfinder.app.model.MemberStatus.PENDING
                        }
                        pendingRequestAdapter.submitList(pending)
                        binding.cardPendingRq.isVisible = pending.isNotEmpty()
                        binding.tvPendingCount.text = "Pending Requests (${pending.size})"
                        
                        val host = membersList.find { it.uid == hostUid }
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
        binding.btnMarkFinished.setOnClickListener {
            showFinishConfirmation()
        }
    }

    private fun showFinishConfirmation() {
        ConfirmationDialogFragment.newInstance(
            title = "Finish Session?",
            subtitle = "This will mark the session as completed for everyone.",
            buttonText = "Finish",
            iconRes = R.drawable.ic_tick,
            iconBgColor = requireContext().getColor(R.color.theme_green)
        ).apply {
            setOnConfirmListener { viewModel.finishSession() }
        }.show(parentFragmentManager, "FinishConfirmation")
    }

    private fun showDeleteMaterialConfirmation(url: String) {
        ConfirmationDialogFragment.newInstance(
            title = "Delete Material?",
            subtitle = "Are you sure you want to remove this file from the session?",
            buttonText = "Delete",
            iconRes = R.drawable.ic_x,
            iconBgColor = requireContext().getColor(R.color.theme_clay),
            confirmBtnBgRes = R.drawable.bg_clay_btn
        ).apply {
            setOnConfirmListener { viewModel.deleteMaterial(url) }
        }.show(parentFragmentManager, "DeleteMaterialConfirmation")
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

            tvSessionDescription.text = session.description
            tvAgenda.text = session.goals
            
            materialAdapter.submitList(session.materialUrls)

            val isHost = session.hostUid == auth.currentUser?.uid
            val isUpcoming = session.status == com.studyfinder.app.model.SessionStatus.UPCOMING
            
            materialAdapter.setHost(isHost)
            cardMaterials.isVisible = session.materialUrls.isNotEmpty() || (isHost && isUpcoming)
            uploadBtnContainer.isVisible = isHost && isUpcoming
            rowInviteStudents.isVisible = isHost && isUpcoming
            btnMarkFinished.isVisible = isHost && isUpcoming
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
                    ivAvatar.setPadding(0, 0, 0, 0)
                    Glide.with(this@SessionDetailFragment).load(url).circleCrop().into(ivAvatar)
                } else {
                    val p = (10 * resources.displayMetrics.density).toInt()
                    ivAvatar.setPadding(p, p, p, p)
                    ivAvatar.setImageResource(R.drawable.ic_profile)
                }
            } ?: run {
                val p = (10 * resources.displayMetrics.density).toInt()
                ivAvatar.setPadding(p, p, p, p)
                ivAvatar.setImageResource(R.drawable.ic_profile)
            }

            rowHost.setOnClickListener { openMemberProfile(member.uid) }
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

    private fun openMaterialUrl(url: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No application found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun redirectToHistory() {
        findNavController().navigate(
            R.id.historyFragment,
            null,
            androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build()
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
