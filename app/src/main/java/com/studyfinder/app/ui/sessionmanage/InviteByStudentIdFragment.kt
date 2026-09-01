package com.studyfinder.app.ui.sessionmanage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.studyfinder.app.util.applyFadeThroughTransitions
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentInviteByStudentIdBinding
import com.studyfinder.app.ui.sessiondetail.InviteStudentAdapter
import com.studyfinder.app.util.ActionResult
import com.studyfinder.app.util.setupHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Invite a member by student ID (§7.5).
 */
class InviteByStudentIdFragment : Fragment() {

    private var _binding: FragmentInviteByStudentIdBinding? = null
    private val binding get() = _binding!!
    private val args: InviteByStudentIdFragmentArgs by navArgs()
    private val viewModel: SessionManageViewModel by viewModels()

    private val adapter = InviteStudentAdapter { user ->
        viewModel.inviteUser(user.uid)
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
        _binding = FragmentInviteByStudentIdBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupHeader(
            binding = binding.appHeader,
            title = "Invite Student",
            showBackBtn = true,
            showAvatar = false
        )

        binding.rvStudents.layoutManager = LinearLayoutManager(context)
        binding.rvStudents.adapter = adapter
        binding.stateEmpty.tvStateEmptyMessage.setText(R.string.empty_student_search)

        viewModel.start(args.sessionId)

        binding.etSearch.addTextChangedListener {
            viewModel.searchUsers(it?.toString().orEmpty())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.searchResults.collectLatest { results ->
                    adapter.submitList(results)
                    val hasQuery = binding.etSearch.text?.isNotBlank() == true
                    binding.stateEmpty.root.visibility =
                        if (results.isEmpty() && hasQuery) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.actionResult.collectLatest { result ->
                    if (result is ActionResult.Success) {
                        viewModel.resetActionResult()
                        // Use string ID navigation if SafeArgs isn't generated yet or causing issues
                        val bundle = Bundle().apply {
                            putString("message", "Invitation Sent!")
                            putString("subtitle", "The student has been notified.")
                            putString("buttonText", "Back to Management")
                            putBoolean("isSignupSuccess", false)
                        }
                        findNavController().navigate(R.id.action_inviteByStudentIdFragment_to_successFragment, bundle)
                    } else if (result is ActionResult.Failure) {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        viewModel.resetActionResult()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
