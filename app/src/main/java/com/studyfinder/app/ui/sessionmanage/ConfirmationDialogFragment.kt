package com.studyfinder.app.ui.sessionmanage

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.studyfinder.app.R
import com.studyfinder.app.databinding.DialogConfirmationBinding

class ConfirmationDialogFragment : DialogFragment() {

    private var _binding: DialogConfirmationBinding? = null
    private val binding get() = _binding!!

    private var onConfirm: (() -> Unit)? = null
    private var title: String = ""
    private var subtitle: String = ""
    private var buttonText: String = ""
    private var iconRes: Int = R.drawable.ic_tick
    private var iconBgColor: Int = Color.parseColor("#AAD6A7") // theme_green
    private var confirmBtnBgRes: Int = R.drawable.bg_yellow_btn
    private var goBackBtnBgRes: Int = R.drawable.bg_yellow_btn

    companion object {
        fun newInstance(
            title: String,
            subtitle: String,
            buttonText: String,
            iconRes: Int = R.drawable.ic_tick,
            iconBgColor: Int = Color.parseColor("#AAD6A7"),
            confirmBtnBgRes: Int = R.drawable.bg_yellow_btn,
            goBackBtnBgRes: Int = R.drawable.bg_yellow_btn
        ): ConfirmationDialogFragment {
            return ConfirmationDialogFragment().apply {
                this.title = title
                this.subtitle = subtitle
                this.buttonText = buttonText
                this.iconRes = iconRes
                this.iconBgColor = iconBgColor
                this.confirmBtnBgRes = confirmBtnBgRes
                this.goBackBtnBgRes = goBackBtnBgRes
            }
        }
    }

    fun setOnConfirmListener(listener: () -> Unit) {
        this.onConfirm = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogConfirmationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.6f)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.tvTitle.text = title
        binding.tvSubtitle.text = subtitle
        binding.btnConfirm.text = buttonText
        binding.ivIcon.setImageResource(iconRes)
        binding.iconContainer.setCardBackgroundColor(iconBgColor)
        
        binding.btnConfirm.setBackgroundResource(confirmBtnBgRes)
        binding.btnGoBack.setBackgroundResource(goBackBtnBgRes)

        binding.btnGoBack.setOnClickListener { dismiss() }
        binding.btnConfirm.setOnClickListener {
            onConfirm?.invoke()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
