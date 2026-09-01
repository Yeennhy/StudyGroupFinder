package com.studyfinder.app.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.studyfinder.app.util.applyFadeThroughTransitions
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentProfileBinding
import com.studyfinder.app.model.UserProfile
import com.studyfinder.app.util.ActivityGraphUtils
import com.studyfinder.app.ui.sessionmanage.ConfirmationDialogFragment
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import com.studyfinder.app.util.setupHeader
import com.studyfinder.app.util.setupNavbar
import java.io.File

/**
 * Profile (§7.7) — two viewing modes in one destination.
 *
 * `uid == null` is **self view**: editable, with photo upload and sign-out.
 * A non-null `uid` is the **read-only view** reached from a member list,
 * where the only action is Block.
 *
 * Fields per the spec: community, department, major, khóa tuyển
 * (`admissionYear`), name, student ID, bio.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val args: ProfileFragmentArgs by navArgs()
    private val viewModel: ProfileViewModel by viewModels()

    private val isSelfView: Boolean get() = args.uid == null
    private var isEditing: Boolean = false

    private var tempCameraUri: Uri? = null
    private var pendingAvatarUri: Uri? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            updateAvatarUi(uri)
        }
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempCameraUri?.let { updateAvatarUi(it) }
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
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
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNavbar(binding.navBar)
        
        viewModel.start(args.uid)

        binding.btnCommunityArrow.setOnClickListener {
            changeCommunity()
        }

        binding.btnEditDetails.setOnClickListener {
            toggleEditMode()
        }

        binding.btnSaveChanges.setOnClickListener {
            saveChanges()
        }

        binding.btnEditAvatar.setOnClickListener {
            showAvatarSourceDialog()
        }

        binding.btnUnblock.setOnClickListener {
            args.uid?.let { showUnblockConfirmationDialog(it) }
        }

        // Only self-view can edit or change community (§7.7)
        binding.btnEditDetails.isVisible = isSelfView
        binding.btnCommunityArrow.isVisible = isSelfView

        observeViewModel()
        
        // §7.7 Implementation: Profile fields, photo upload, etc.
    }

    private fun observeViewModel() {
        binding.stateEmpty.tvStateEmptyMessage.setText(R.string.empty_profile)
        binding.stateError.btnStateRetry.setOnClickListener { activity?.recreate() }

        viewModel.profile.observe(viewLifecycleOwner) { state ->
            // Don't yank the form out from under an in-progress edit.
            if (!isEditing) {
                com.studyfinder.app.ui.common.StateRenderer.render(
                    state = state,
                    loadingView = binding.stateLoading.root,
                    emptyView = binding.stateEmpty.root,
                    errorView = binding.stateError.root,
                    offlineView = binding.stateOfflineBanner.root,
                    contentView = binding.scrollContent,
                )
            }
            when (state) {
                is UiState.Success -> {
                    if (!isEditing) bindProfile(state.data)
                }
                is UiState.Offline -> {
                    if (!isEditing) bindProfile(state.cached)
                }
                is UiState.Error -> {
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        viewModel.isBlocked.observe(viewLifecycleOwner) { isBlocked ->
            updateBlockUi(isBlocked)
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ActionResult.Success -> {
                    Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                    isEditing = false
                    setEditMode(false)
                    // Refresh UI with the latest data from the ViewModel
                    (viewModel.profile.value as? UiState.Success)?.data?.let { bindProfile(it) }
                    viewModel.clearSaveResult()
                }
                is ActionResult.Failure -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    viewModel.clearSaveResult()
                }
                else -> {}
            }
        }

        viewModel.activityCells.observe(viewLifecycleOwner) { cells ->
            val weeks = ActivityGraphUtils.buildWeeks(cells.associate { it.date to it.count })
            binding.activityGraph.submitData(weeks)
        }
    }

    private fun bindProfile(profile: UserProfile) {
        binding.tvProfileName.text = profile.name
        binding.tvProfileId.text = "ID: ${profile.studentId}"
        binding.tvProfileBio.text = profile.bio
        binding.tvCommunityName.text = profile.communityId // TODO: Get name from ID
        binding.tvDepartmentValue.text = profile.department
        binding.tvMajorValue.text = profile.major
        binding.tvAdmissionYearValue.text = profile.admissionYear

        if (profile.photoUrl.isNotBlank()) {
            applyAvatarStyle(true)
            Glide.with(this)
                .load(profile.photoUrl)
                .circleCrop()
                .into(binding.ivAvatar)
        } else {
            applyAvatarStyle(false)
        }
    }

    private fun showAvatarSourceDialog() {
        ConfirmationDialogFragment.newInstance(
            title = "Change Photo",
            subtitle = "Select your profile picture source",
            buttonText = "Camera",
            cancelText = "Gallery",
            secondaryButtonText = "Cancel",
            iconRes = R.drawable.ic_profile,
            iconBgColor = ContextCompat.getColor(requireContext(), R.color.theme_gray),
            iconTint = ContextCompat.getColor(requireContext(), R.color.graphite)
        ).apply {
            setOnConfirmListener { launchCamera() }
            setOnCancelListener { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }.show(parentFragmentManager, "AvatarSourceDialog")
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
            return
        }

        try {
            val tempFile = File(requireContext().cacheDir, "images").apply { mkdirs() }
                .let { File(it, "temp_avatar_${System.currentTimeMillis()}.jpg") }
            
            val authority = "${requireContext().packageName}.fileprovider"
            tempCameraUri = FileProvider.getUriForFile(requireContext(), authority, tempFile)
            takePicture.launch(tempCameraUri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAvatarUi(uri: Uri) {
        pendingAvatarUri = uri
        applyAvatarStyle(true)
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .into(binding.ivAvatar)
    }

    private fun updateBlockUi(isBlocked: Boolean) {
        val showBlockInfo = !isSelfView && isBlocked
        
        binding.divAvatarCard.isVisible = showBlockInfo
        binding.blockmsg.isVisible = showBlockInfo
        binding.btnUnblock.isVisible = showBlockInfo

        // Re-setup header to show/hide block button
        setupHeader(
            binding.appHeader,
            "Profile",
            showBackBtn = !isSelfView,
            showAvatar = false,
            rightBtnIcon = when {
                isSelfView -> R.drawable.ic_signout
                !isBlocked -> R.drawable.ic_block
                else -> null
            },
            onRightBtnClick = {
                if (isSelfView) {
                    showSignOutConfirmationDialog()
                } else if (!isBlocked) {
                    ConfirmationDialogFragment.newInstance(
                        title = "Block User",
                        subtitle = "Are you sure you want to block this user?",
                        buttonText = "Block",
                        cancelText = "Cancel",
                        iconRes = R.drawable.ic_block,
                        iconBgColor = ContextCompat.getColor(requireContext(), R.color.deep_red),
                        iconTint = ContextCompat.getColor(requireContext(), R.color.white),
                        confirmBtnBgRes = R.drawable.bg_red_btn
                    ).apply {
                        setOnConfirmListener { args.uid?.let { viewModel.blockUser(it) } }
                    }.show(parentFragmentManager, "BlockConfirmationDialog")
                }
            }
        )
    }

    private fun applyAvatarStyle(hasPhoto: Boolean) {
        binding.ivAvatar.apply {
            if (hasPhoto) {
                setPadding(0, 0, 0, 0)
                scaleType = ImageView.ScaleType.CENTER_CROP
                imageTintList = null
            } else {
                val paddingPx = (35 * resources.displayMetrics.density).toInt()
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                scaleType = ImageView.ScaleType.FIT_CENTER
                imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.graphite)
                )
                setImageResource(R.drawable.ic_profile)
            }
        }
    }

    private fun toggleEditMode() {
        if (!isSelfView) return
        
        isEditing = !isEditing
        setEditMode(isEditing)
    }

    private fun setEditMode(enabled: Boolean) {
        // Toggle visibility of read-only views
        binding.tvProfileName.isVisible = !enabled
        binding.tvProfileId.isVisible = !enabled
        binding.tvProfileBio.isVisible = !enabled
        binding.tvDepartmentValue.isVisible = !enabled
        binding.tvMajorValue.isVisible = !enabled
        binding.tvAdmissionYearValue.isVisible = !enabled
        binding.cardSessionActivity.isVisible = !enabled
        binding.cardCommunity.isVisible = !enabled

        // Toggle visibility of editing views
        binding.tvEditName.isVisible = enabled
        binding.etProfileName.isVisible = enabled
        binding.tvEditBio.isVisible = enabled
        binding.etProfileBio.isVisible = enabled
        binding.etDepartmentValue.isVisible = enabled
        binding.etMajorValue.isVisible = enabled
        binding.etAdmissionYearValue.isVisible = enabled
        binding.btnEditAvatar.isVisible = enabled
        binding.btnSaveChanges.isVisible = enabled

        // Keep edit button visible if self-view, toggle its icon
        binding.btnEditDetails.isVisible = isSelfView
        binding.btnCommunityArrow.isVisible = isSelfView && !enabled

        binding.ivEditDetailsIcon.setImageResource(
            if (enabled) R.drawable.ic_x else R.drawable.ic_edit_pencil
        )

        if (enabled) {
            // Copy data to EditTexts
            binding.etProfileName.setText(binding.tvProfileName.text)
            binding.etProfileBio.setText(binding.tvProfileBio.text)
            binding.etDepartmentValue.setText(binding.tvDepartmentValue.text)
            binding.etMajorValue.setText(binding.tvMajorValue.text)
            binding.etAdmissionYearValue.setText(binding.tvAdmissionYearValue.text)
        }
    }

    private fun saveChanges() {
        val currentProfile = (viewModel.profile.value as? UiState.Success)?.data ?: return
        
        pendingAvatarUri?.let {
            viewModel.uploadPhoto(it)
        }
        
        val updatedProfile = currentProfile.copy(
            name = binding.etProfileName.text.toString(),
            bio = binding.etProfileBio.text.toString(),
            department = binding.etDepartmentValue.text.toString(),
            major = binding.etMajorValue.text.toString(),
            admissionYear = binding.etAdmissionYearValue.text.toString()
        )

        viewModel.save(updatedProfile)
    }

    /** The spec's "community edit in profile" entry point (§7.1). */
    private fun changeCommunity() {
        findNavController().navigate(
            ProfileFragmentDirections.actionProfileFragmentToCommunitySelectionFragment(
                isEditMode = true
            )
        )
    }

    private fun showUnblockConfirmationDialog(uid: String) {
        ConfirmationDialogFragment.newInstance(
            title = "Unblock User",
            subtitle = "Are you sure you want to unblock this user?",
            buttonText = "Unblock",
            cancelText = "Cancel",
            iconRes = R.drawable.ic_block,
            iconBgColor = ContextCompat.getColor(requireContext(), R.color.theme_gray),
            iconTint = ContextCompat.getColor(requireContext(), R.color.graphite)
        ).apply {
            setOnConfirmListener { viewModel.unblockUser(uid) }
        }.show(parentFragmentManager, "UnblockConfirmationDialog")
    }

    private fun showSignOutConfirmationDialog() {
        ConfirmationDialogFragment.newInstance(
            title = "Sign Out",
            subtitle = "Are you sure you want to sign out?",
            buttonText = "Sign Out",
            cancelText = "Cancel",
            iconRes = R.drawable.ic_signout,
            iconBgColor = ContextCompat.getColor(requireContext(), R.color.theme_gray)
        ).apply {
            setOnConfirmListener { signOut() }
        }.show(parentFragmentManager, "SignOutDialog")
    }

    /** Sign-out clears the Room cache, then pops the whole stack (§7.0). */
    private fun signOut() {
        viewModel.signOut()
        findNavController().navigate(
            ProfileFragmentDirections.actionProfileFragmentToLoginFragment()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
