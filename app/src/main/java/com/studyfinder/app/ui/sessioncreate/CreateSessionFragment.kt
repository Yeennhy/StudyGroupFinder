package com.studyfinder.app.ui.sessioncreate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentCreateSessionBinding
import com.studyfinder.app.model.ExpectationLevel
import com.studyfinder.app.model.TagType
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.setupHeader
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** §7.4. */
class CreateSessionFragment : Fragment() {

    private var _binding: FragmentCreateSessionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CreateSessionViewModel by viewModels()

    private var selectedDate = Calendar.getInstance()
    private var durationMinutes = 90
    private var capacity = 4
    private var isGated = false

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

        observeViewModel()
    }

    private fun setupSpinners() {
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
        // TODO: Tag logic we will do later

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

            viewModel.submit(
                title = title,
                description = binding.etDescription.text.toString(),
                goals = binding.etGoals.text.toString(),
                location = location,
                tagType = tagType,
                expectation = expectation,
                startTimeMillis = selectedDate.timeInMillis,
                durationMinutes = durationMinutes,
                capacity = capacity,
                isGated = isGated
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.createResult.collect { result ->
                if (result is ActionResult.Success) {
                    viewModel.resetResult()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
