package com.studyfinder.app.ui.sessioncreate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.studyfinder.app.util.applyFadeThroughTransitions
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentCreateSessionBinding
import com.studyfinder.app.model.ExpectationLevel
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.TagType
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.setupHeader
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateSessionFragment : Fragment() {

    private var _binding: FragmentCreateSessionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CreateSessionViewModel by viewModels()
    private val args: CreateSessionFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        applyFadeThroughTransitions()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCreateSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader(binding.appHeader, "Create Session", showBackBtn = true)

        setupSpinners()
        setupToggles()
        setupSchedule()
        setupCapacity()
        setupSubmit()

        args.prefillFromSessionId?.let {
            viewModel.prefillFrom(it)
        }

        observeViewModel()
    }

    private fun setupSpinners() {
        // Course Category
        val categories = com.studyfinder.app.model.CourseCategory.entries.map { it.wire.replaceFirstChar { c -> c.uppercase() } }
        binding.spinnerCourseCategory.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)

        // Preparing for (TagType)
        val preppingFor = TagType.entries.map { it.wire.replaceFirstChar { c -> c.uppercase() } }
        binding.spinnerPreparingFor.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, preppingFor)

        // Expectation Level
        val expectations = ExpectationLevel.entries.map { it.wire.replaceFirstChar { c -> c.uppercase() } }
        binding.spinnerExpectation.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, expectations)

        // Locations from ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.locations.collect { locations ->
                if (locations.isNotEmpty()) {
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, locations.map { it.name })
                    binding.spinnerCampus.adapter = adapter
                }
            }
        }
    }

    private fun setupToggles() {
        binding.toggleOpenToAll.setOnClickListener {
            viewModel.draftIsGated.value = false
        }
        binding.toggleOnlyRequests.setOnClickListener {
            viewModel.draftIsGated.value = true
        }
    }

    private fun updateToggleUI(isGated: Boolean) {
        binding.toggleOpenToAll.setBackgroundResource(if (!isGated) R.drawable.bg_segment_selected else android.R.color.transparent)
        binding.toggleOnlyRequests.setBackgroundResource(if (isGated) R.drawable.bg_segment_selected else android.R.color.transparent)
    }

    private fun setupSchedule() {
        binding.etDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(viewModel.draftDate.value.timeInMillis)
                .build()
            picker.addOnPositiveButtonClickListener { selection ->
                if (selection != null) {
                    val cal = Calendar.getInstance().apply { timeInMillis = selection }
                    viewModel.draftDate.value = cal
                }
            }
            picker.show(parentFragmentManager, "DATE_PICKER")
        }

        binding.etTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(viewModel.draftDate.value.get(Calendar.HOUR_OF_DAY))
                .setMinute(viewModel.draftDate.value.get(Calendar.MINUTE))
                .build()
            picker.addOnPositiveButtonClickListener {
                val cal = Calendar.getInstance().apply { 
                    timeInMillis = viewModel.draftDate.value.timeInMillis
                    set(Calendar.HOUR_OF_DAY, picker.hour)
                    set(Calendar.MINUTE, picker.minute)
                }
                viewModel.draftDate.value = cal
            }
            picker.show(parentFragmentManager, "TIME_PICKER")
        }

        binding.seekDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    viewModel.draftDurationMinutes.value = (progress + 1) * 30
                }
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
    }

    private fun updateDateText(date: java.util.Date) {
        binding.etDate.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date))
    }

    private fun updateTimeText(date: java.util.Date) {
        binding.etTime.setText(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date))
    }

    private fun setupCapacity() {
        binding.btnCapacityPlus.setOnClickListener {
            viewModel.draftCapacity.value++
        }
        binding.btnCapacityMinus.setOnClickListener {
            if (viewModel.draftCapacity.value > 2) {
                viewModel.draftCapacity.value--
            }
        }
    }

    private fun setupSubmit() {
        binding.btnAddTag.setOnClickListener {
            showAddTagDialog()
        }

        binding.btnCreateSession.setOnClickListener {
            val title = binding.etTitle.text.toString()
            if (title.isBlank()) {
                Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val location = viewModel.locations.value.getOrNull(binding.spinnerCampus.selectedItemPosition)
            val tagType = TagType.entries.getOrNull(binding.spinnerPreparingFor.selectedItemPosition) ?: TagType.NORMAL
            val expectation = ExpectationLevel.entries.getOrNull(binding.spinnerExpectation.selectedItemPosition) ?: ExpectationLevel.PASS

            if (location == null) {
                Toast.makeText(context, "Please wait for data to load", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.stateLoading.root.isVisible = true
            viewModel.submit(
                title = title,
                description = binding.etDescription.text.toString(),
                goals = binding.etGoals.text.toString(),
                courseId = binding.etCourseId.text.toString(),
                courseName = binding.etCourseName.text.toString(),
                courseCategory = com.studyfinder.app.model.CourseCategory.entries[binding.spinnerCourseCategory.selectedItemPosition],
                location = location,
                tagType = tagType,
                expectation = expectation,
                startTimeMillis = viewModel.draftDate.value.timeInMillis,
                durationMinutes = viewModel.draftDurationMinutes.value,
                capacity = viewModel.draftCapacity.value,
                isGated = viewModel.draftIsGated.value,
                tags = viewModel.draftTags.value
            )
        }
    }

    private fun showAddTagDialog() {
        val editText = android.widget.EditText(requireContext())
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Tag")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val tag = editText.text.toString().trim()
                if (tag.isNotBlank()) {
                    viewModel.addDraftTag(tag)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addTagToUi(tag: String) {
        val chip = Chip(requireContext()).apply {
            text = tag
            isCloseIconVisible = true
            setCloseIconTintResource(R.color.graphite)
            setChipBackgroundColorResource(R.color.light_blue)
            setChipStrokeColorResource(R.color.graphite)
            chipStrokeWidth = 2.0f * resources.displayMetrics.density
            chipCornerRadius = 99f * resources.displayMetrics.density
            setTextColor(requireContext().getColor(R.color.graphite))
            typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.pjsans_bold)
            chipMinHeight = 36f * resources.displayMetrics.density
            setEnsureMinTouchTargetSize(false)

            setOnCloseIconClickListener {
                viewModel.removeDraftTag(tag)
            }
        }
        binding.tagContainer.addView(chip, binding.tagContainer.childCount - 1)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.draftDate.collect { cal ->
                    updateDateText(cal.time)
                    updateTimeText(cal.time)
                }
            }
            launch {
                viewModel.draftDurationMinutes.collect { mins ->
                    val hours = mins / 60
                    val m = mins % 60
                    binding.tvDurationValue.text = if (hours > 0) {
                        if (m > 0) "${hours}h ${m}m" else "${hours}h"
                    } else "${m}m"
                    val progress = (mins / 30) - 1
                    if (binding.seekDuration.progress != progress) {
                        binding.seekDuration.progress = progress
                    }
                }
            }
            launch {
                viewModel.draftCapacity.collect { cap ->
                    binding.tvCapacity.text = cap.toString()
                }
            }
            launch {
                viewModel.draftIsGated.collect { gated ->
                    updateToggleUI(gated)
                }
            }
            launch {
                viewModel.draftTags.collect { tags ->
                    syncTagsToUi(tags)
                }
            }
            launch {
                viewModel.prefilledSession.collect { session ->
                    session?.let { fillUi(it) }
                }
            }
            launch {
                viewModel.createResult.collect { result ->
                    binding.stateLoading.root.isVisible = false
                    if (result is ActionResult.Success) {
                        findNavController().navigate(
                            CreateSessionFragmentDirections.actionCreateSessionFragmentToSuccessFragment(
                                message = "Session Created!",
                                subtitle = "Your study group is now live.",
                                buttonText = "View My Sessions",
                                isSignupSuccess = false
                            )
                        )
                    } else if (result is ActionResult.Failure) {
                        Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun syncTagsToUi(tags: List<String>) {
        // Simple sync: remove all chips (except add button) and re-add
        val count = binding.tagContainer.childCount
        if (count > 1) {
            binding.tagContainer.removeViews(0, count - 1)
        }
        tags.forEach { addTagToUi(it) }
    }

    private fun fillUi(session: Session) {
        binding.apply {
            etCourseId.setText(session.courseId)
            etCourseName.setText(session.courseName)
            spinnerCourseCategory.setSelection(com.studyfinder.app.model.CourseCategory.entries.indexOf(session.courseCategory))

            etTitle.setText(session.title)
            etDescription.setText(session.description)
            etGoals.setText(session.goals)
            
            spinnerPreparingFor.setSelection(TagType.entries.indexOf(session.tagType))
            spinnerExpectation.setSelection(ExpectationLevel.entries.indexOf(session.expectationLevel))
            
            // Location selection needs to wait for spinner Campus to be populated
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.locations.collect { locations ->
                    val index = locations.indexOfFirst { it.name == session.locationName }
                    if (index != -1) spinnerCampus.setSelection(index)
                }
            }
            
            viewModel.draftCapacity.value = session.capacity
            viewModel.draftIsGated.value = session.mode == com.studyfinder.app.model.SessionMode.GATED

            val cal = Calendar.getInstance().apply { timeInMillis = session.startTimeMillis }
            viewModel.draftDate.value = cal
            viewModel.draftDurationMinutes.value = ((session.endTimeMillis - session.startTimeMillis) / 60000).toInt()

            viewModel.setDraftTags(session.tags)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
