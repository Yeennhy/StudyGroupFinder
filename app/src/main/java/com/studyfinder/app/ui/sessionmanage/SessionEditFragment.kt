package com.studyfinder.app.ui.sessionmanage

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentSessionEditBinding
import com.studyfinder.app.model.ExpectationLevel
import com.studyfinder.app.model.Session
import com.studyfinder.app.model.TagType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SessionEditFragment : DialogFragment() {

    private var _binding: FragmentSessionEditBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: Session

    private var selectedDate = Calendar.getInstance()
    private var durationMinutes = 90
    private var capacity = 4
    private val selectedTags = mutableListOf<String>()

    companion object {
        private const val ARG_SESSION = "session"

        fun newInstance(session: Session): SessionEditFragment {
            val fragment = SessionEditFragment()
            val args = Bundle()
            args.putSerializable(ARG_SESSION, session)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(ARG_SESSION, Session::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable(ARG_SESSION) as Session
        }
        selectedDate.timeInMillis = session.startTimeMillis
        durationMinutes = ((session.endTimeMillis - session.startTimeMillis) / 60000).toInt()
        capacity = session.capacity
        selectedTags.clear()
        selectedTags.addAll(session.tags)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSessionEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
            setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.6f)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.apply {
            etDialogTitle.setText(session.title)
            etDialogDescription.setText(session.description)
            etDialogGoals.setText(session.goals)
            
            updateDateText()
            updateTimeText()
            
            // Preparing for (TagType)
            val preppingFor = TagType.entries.map { it.wire.replaceFirstChar { c -> c.uppercase() } }
            spinnerDialogPreparingFor.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, preppingFor)
            spinnerDialogPreparingFor.setSelection(TagType.entries.indexOf(session.tagType))

            // Expectation Level
            val expectations = ExpectationLevel.entries.map { it.wire.replaceFirstChar { c -> c.uppercase() } }
            spinnerDialogExpectation.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, expectations)
            spinnerDialogExpectation.setSelection(ExpectationLevel.entries.indexOf(session.expectationLevel))

            // Locations
            (parentFragment as? SessionManageFragment)?.viewModel?.let { vm ->
                viewLifecycleOwner.lifecycleScope.launch {
                    vm.locations.collect { locations ->
                        if (locations.isNotEmpty()) {
                            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, locations.map { it.name })
                            spinnerDialogCampus.adapter = adapter
                            val currentIndex = locations.indexOfFirst { it.name == session.locationName }
                            if (currentIndex != -1) spinnerDialogCampus.setSelection(currentIndex)
                        }
                    }
                }
            }

            tvDialogDurationValue.text = formatDuration(durationMinutes)
            seekDialogDuration.progress = (durationMinutes / 30) - 1

            tvDialogCapacity.text = capacity.toString()

            tagContainerDialog.removeAllViews()
            selectedTags.forEach { addTagToUi(it) }
            tagContainerDialog.addView(btnAddTagDialog)
        }
    }

    private fun setupListeners() {
        binding.apply {
            kickBtn.setOnClickListener { dismiss() }
            btnCancelDialog.setOnClickListener { dismiss() }
            
            etDialogDate.setOnClickListener {
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

            etDialogTime.setOnClickListener {
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

            btnDialogCapacityMinus.setOnClickListener {
                if (capacity > 2) {
                    capacity--
                    tvDialogCapacity.text = capacity.toString()
                }
            }
            
            btnDialogCapacityPlus.setOnClickListener {
                capacity++
                tvDialogCapacity.text = capacity.toString()
            }

            seekDialogDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    durationMinutes = (progress + 1) * 30
                    tvDialogDurationValue.text = formatDuration(durationMinutes)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            btnAddTagDialog.setOnClickListener {
                showAddTagDialog()
            }

            btnSaveChanges.setOnClickListener {
                val title = etDialogTitle.text.toString()
                if (title.isBlank()) {
                    Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val tagType = TagType.entries.getOrNull(spinnerDialogPreparingFor.selectedItemPosition) ?: TagType.NORMAL
                val expectation = ExpectationLevel.entries.getOrNull(spinnerDialogExpectation.selectedItemPosition) ?: ExpectationLevel.PASS
                
                val vm = (parentFragment as? SessionManageFragment)?.viewModel
                val location = vm?.locations?.value?.getOrNull(spinnerDialogCampus.selectedItemPosition)

                val updatedSession = session.copy(
                    title = title,
                    description = etDialogDescription.text.toString(),
                    goals = etDialogGoals.text.toString(),
                    tagType = tagType,
                    expectationLevel = expectation,
                    locationName = location?.name ?: session.locationName,
                    lat = location?.lat ?: session.lat,
                    lng = location?.lng ?: session.lng,
                    startTimeMillis = selectedDate.timeInMillis,
                    endTimeMillis = selectedDate.timeInMillis + (durationMinutes * 60000L),
                    capacity = capacity,
                    tags = selectedTags
                )
                vm?.saveEdits(updatedSession)
                dismiss()
            }
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
        if (!selectedTags.contains(tag)) {
            selectedTags.add(tag)
        }
        val chip = Chip(requireContext()).apply {
            text = tag
            isCloseIconVisible = true
            setCloseIconTintResource(R.color.graphite)
            setChipBackgroundColorResource(R.color.light_blue)
            setChipStrokeColorResource(R.color.graphite)
            chipStrokeWidth = 3.5f * resources.displayMetrics.density
            chipCornerRadius = 99f * resources.displayMetrics.density
            setTextColor(requireContext().getColor(R.color.graphite))
            typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.pjsans_bold)
            chipMinHeight = 36f * resources.displayMetrics.density
            setEnsureMinTouchTargetSize(false)

            setOnCloseIconClickListener {
                selectedTags.remove(tag)
                binding.tagContainerDialog.removeView(this)
            }
        }
        binding.tagContainerDialog.addView(chip, binding.tagContainerDialog.childCount - 1)
    }

    private fun updateDateText() {
        binding.etDialogDate.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate.time))
    }

    private fun updateTimeText() {
        binding.etDialogTime.setText(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(selectedDate.time))
    }

    private fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) {
            if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        } else "${mins}m"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
