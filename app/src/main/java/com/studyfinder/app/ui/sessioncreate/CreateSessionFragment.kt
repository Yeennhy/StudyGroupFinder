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

/**
 * Fragment for creating a new study session.
 */
class CreateSessionFragment : Fragment() {

    private var _binding: FragmentCreateSessionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CreateSessionViewModel by viewModels()
    private val args: CreateSessionFragmentArgs by navArgs()

    private var selectedDate = Calendar.getInstance()
    private var durationMinutes = 90
    private var capacity = 4
    private var isGated = false
    private val selectedTags = mutableListOf<String>()

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
            isGated = false
            updateToggleUI()
        }
        binding.toggleOnlyRequests.setOnClickListener {
            isGated = true
            updateToggleUI()
        }
        updateToggleUI()
    }

    private fun updateToggleUI() {
        binding.toggleOpenToAll.setBackgroundResource(if (!isGated) R.drawable.bg_segment_selected else android.R.color.transparent)
        binding.toggleOnlyRequests.setBackgroundResource(if (isGated) R.drawable.bg_segment_selected else android.R.color.transparent)
    }

    private fun setupSchedule() {
        updateDateText()
        updateTimeText()

        binding.etDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(selectedDate.timeInMillis)
                .build()
            picker.addOnPositiveButtonClickListener { selection ->
                if (selection != null) {
                    selectedDate.timeInMillis = selection
                    updateDateText()
                }
            }
            picker.show(parentFragmentManager, "DATE_PICKER")
        }

        binding.etTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(selectedDate.get(Calendar.HOUR_OF_DAY))
                .setMinute(selectedDate.get(Calendar.MINUTE))
                .build()
            picker.addOnPositiveButtonClickListener {
                selectedDate.set(Calendar.HOUR_OF_DAY, picker.hour)
                selectedDate.set(Calendar.MINUTE, picker.minute)
                updateTimeText()
            }
            picker.show(parentFragmentManager, "TIME_PICKER")
        }

        binding.seekDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Map 0-6 to 30m, 1h, 1h30m, 2h, 2h30m, 3h, 3h30m
                durationMinutes = (progress + 1) * 30
                val hours = durationMinutes / 60
                val mins = durationMinutes % 60
                binding.tvDurationValue.text = if (hours > 0) {
                    if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
                } else "${mins}m"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        // Set initial text
        binding.seekDuration.progress = 2 // 1h 30m
    }

    private fun updateDateText() {
        binding.etDate.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate.time))
    }

    private fun updateTimeText() {
        binding.etTime.setText(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(selectedDate.time))
    }

    private fun setupCapacity() {
        binding.tvCapacity.text = capacity.toString()
        binding.btnCapacityPlus.setOnClickListener {
            capacity++
            binding.tvCapacity.text = capacity.toString()
        }
        binding.btnCapacityMinus.setOnClickListener {
            if (capacity > 2) {
                capacity--
                binding.tvCapacity.text = capacity.toString()
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
                startTimeMillis = selectedDate.timeInMillis,
                durationMinutes = durationMinutes,
                capacity = capacity,
                isGated = isGated,
                tags = selectedTags
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
                    addTagToUi(tag)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addTagToUi(tag: String) {
        if (selectedTags.contains(tag)) return
        selectedTags.add(tag)
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
                selectedTags.remove(tag)
                binding.tagContainer.removeView(this)
            }
        }
        binding.tagContainer.addView(chip, binding.tagContainer.childCount - 1)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
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
            
            capacity = session.capacity
            tvCapacity.text = capacity.toString()
            
            isGated = session.mode == com.studyfinder.app.model.SessionMode.GATED
            updateToggleUI()

            tagContainer.removeAllViews()
            session.tags.forEach { addTagToUi(it) }
            // Add back the "+" button
            tagContainer.addView(btnAddTag)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
