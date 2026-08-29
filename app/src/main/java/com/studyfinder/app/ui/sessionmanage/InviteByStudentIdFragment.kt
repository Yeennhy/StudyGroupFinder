package com.studyfinder.app.ui.sessionmanage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.studyfinder.app.R
import com.studyfinder.app.databinding.FragmentInviteByStudentIdBinding
import com.studyfinder.app.util.setupHeader

/**
 * Invite a member by student ID (§7.5).
 *
 * Selecting a result writes **two** things — `members/{uid}` with
 * `status = invited` (the state that makes the Accept Invite button appear on
 * Session Detail) and an inbox item (the notification). Writing only one of
 * the two is the most likely bug on this screen.
 *
 * Handle the zero-result case (a typo, the common one) and the multi-result
 * case explicitly.
 */
class InviteByStudentIdFragment : Fragment() {

    private var _binding: FragmentInviteByStudentIdBinding? = null
    private val binding get() = _binding!!
    private val args: InviteByStudentIdFragmentArgs by navArgs()
    private val viewModel: SessionManageViewModel by viewModels()

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
            showAvatar = false,
            rightBtnIcon = R.drawable.ic_hourglass,
            rightBtn2Icon = R.drawable.ic_upload
        )

        // TODO(§7.5): search users by studentId (needs a single-field index),
        //  then invite -> member doc + inbox item.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
