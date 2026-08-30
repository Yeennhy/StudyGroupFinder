package com.studyfinder.app.ui.sessionmanage

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.studyfinder.app.R

class ConfirmationDialogFragment : DialogFragment() {

    private var _binding: com.studyfinder.app.databinding.DialogConfirmationBinding? = null
    private val viewBinding get() = _binding!!

    private var onConfirm: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var onSecondary: (() -> Unit)? = null
    private var title: String = ""
    private var subtitle: String = ""
    private var buttonText: String = ""
    private var cancelText: String = "Go Back"
    private var secondaryButtonText: String? = null
    private var iconRes: Int = R.drawable.ic_tick
    private var iconBgColor: Int = Color.parseColor("#AAD6A7") // theme_green
    private var iconTint: Int? = null
    private var confirmBtnBgRes: Int = R.drawable.bg_yellow_btn
    private var goBackBtnBgRes: Int = R.drawable.bg_yellow_btn

    companion object {
        fun newInstance(
            title: String,
            subtitle: String,
            buttonText: String,
            cancelText: String = "Go Back",
            secondaryButtonText: String? = null,
            iconRes: Int = R.drawable.ic_tick,
            iconBgColor: Int = Color.parseColor("#AAD6A7"),
            iconTint: Int? = null,
            confirmBtnBgRes: Int = R.drawable.bg_yellow_btn,
            goBackBtnBgRes: Int = R.drawable.bg_yellow_btn
        ): ConfirmationDialogFragment {
            return ConfirmationDialogFragment().apply {
                this.title = title
                this.subtitle = subtitle
                this.buttonText = buttonText
                this.cancelText = cancelText
                this.secondaryButtonText = secondaryButtonText
                this.iconRes = iconRes
                this.iconBgColor = iconBgColor
                this.iconTint = iconTint
                this.confirmBtnBgRes = confirmBtnBgRes
                this.goBackBtnBgRes = goBackBtnBgRes
            }
        }
    }

    fun setOnConfirmListener(listener: () -> Unit) {
        this.onConfirm = listener
    }

    fun setOnCancelListener(listener: () -> Unit) {
        this.onCancel = listener
    }

    fun setOnSecondaryListener(listener: () -> Unit) {
        this.onSecondary = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = com.studyfinder.app.databinding.DialogConfirmationBinding.inflate(inflater, container, false)
        _binding = binding
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
        
        viewBinding.tvTitle.text = title
        viewBinding.tvSubtitle.text = subtitle
        viewBinding.btnConfirm.text = buttonText
        viewBinding.btnGoBack.text = cancelText
        viewBinding.ivIcon.setImageResource(iconRes)
        viewBinding.iconContainer.setCardBackgroundColor(iconBgColor)
        
        iconTint?.let {
            viewBinding.ivIcon.imageTintList = ColorStateList.valueOf(it)
        } ?: run {
            viewBinding.ivIcon.imageTintList = null
        }

        viewBinding.btnSecondary.isVisible = secondaryButtonText != null
        secondaryButtonText?.let {
            viewBinding.btnSecondary.text = it
        }
        
        viewBinding.btnConfirm.setBackgroundResource(confirmBtnBgRes)
        viewBinding.btnGoBack.setBackgroundResource(goBackBtnBgRes)

        viewBinding.btnGoBack.setOnClickListener {
            onCancel?.invoke()
            dismiss()
        }
        viewBinding.btnConfirm.setOnClickListener {
            onConfirm?.invoke()
            dismiss()
        }
        viewBinding.btnSecondary.setOnClickListener {
            onSecondary?.invoke()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
