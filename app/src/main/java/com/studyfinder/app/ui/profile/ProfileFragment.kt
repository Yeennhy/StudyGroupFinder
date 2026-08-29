package com.studyfinder.app.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentProfileBinding
import com.studyfinder.app.util.setupHeader
import com.studyfinder.app.util.setupNavbar
import java.io.File
import android.widget.Toast
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.UiState
import com.studyfinder.app.model.UserProfile

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
        setupHeader(
            binding.appHeader,
            "Profile",
            showBackBtn = !isSelfView,
            showAvatar = false,
            rightBtnIcon = if (isSelfView) R.drawable.ic_signout else R.drawable.ic_block,
            onRightBtnClick = {
                if (isSelfView) {
                    signOut()
                }
            }
        )

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

        observeViewModel()
        
        // §7.7 Implementation: Profile fields, photo upload, etc.
    }

    private fun observeViewModel() {
        viewModel.profile.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    // TODO: Show progress
                }
                is UiState.Success -> {
                    val profile = state.data
                    if (!isEditing) {
                        bindProfile(profile)
                    }
                }
                is UiState.Error -> {
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ActionResult.Success -> {
                    Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                    isEditing = false
                    setEditMode(false)
                    viewModel.clearSaveResult()
                }
                is ActionResult.Failure -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    viewModel.clearSaveResult()
                }
                else -> {}
            }
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
            Glide.with(this)
                .load(profile.photoUrl)
                .circleCrop()
                .into(binding.ivAvatar)
        }
    }

    private fun showAvatarSourceDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Profile Picture")
            .setItems(arrayOf("Select from Gallery", "Take Photo")) { _, which ->
                when (which) {
                    0 -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    1 -> launchCamera()
                }
            }
            .show()
    }

    private fun launchCamera() {
        val tempFile = File(requireContext().cacheDir, "images").apply { mkdirs() }
            .let { File(it, "temp_avatar_${System.currentTimeMillis()}.jpg") }
        
        val authority = "${requireContext().packageName}.fileprovider"
        tempCameraUri = FileProvider.getUriForFile(requireContext(), authority, tempFile)
        takePicture.launch(tempCameraUri)
    }

    private fun updateAvatarUi(uri: Uri) {
        pendingAvatarUri = uri
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .into(binding.ivAvatar)
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

        // Toggle visibility of editing views
        binding.etProfileName.isVisible = enabled
        binding.etProfileBio.isVisible = enabled
        binding.etDepartmentValue.isVisible = enabled
        binding.etMajorValue.isVisible = enabled
        binding.etAdmissionYearValue.isVisible = enabled
        binding.btnEditAvatar.isVisible = enabled
        binding.btnSaveChanges.isVisible = enabled

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

    /** Sign-out clears the Room cache, then pops the whole stack (§7.0). */
    private fun signOut() {
        findNavController().navigate(
            ProfileFragmentDirections.actionProfileFragmentToLoginFragment()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
