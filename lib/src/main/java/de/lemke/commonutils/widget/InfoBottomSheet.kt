@file:Suppress("unused")

package de.lemke.commonutils.widget

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity.CENTER
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.lemke.commonutils.databinding.WidgetInfoBottomsheetBinding

class InfoBottomSheet : BottomSheetDialogFragment() {
    private lateinit var binding: WidgetInfoBottomsheetBinding

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        //onDismissListener?.onDismiss(dialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        WidgetInfoBottomsheetBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onCreateDialog(savedInstanceState: Bundle?) = (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
        behavior.skipCollapsed = true
        setOnShowListener { behavior.state = STATE_EXPANDED }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.getInt(KEY_TEXT_GRAVITY)?.also {
            binding.infoTitle.gravity = it
            binding.infoMessage.gravity = it
        }
        arguments?.getString(KEY_TITLE).also {
            if (it.isNullOrBlank()) binding.infoTitle.isVisible = false
            else binding.infoTitle.text = it
        }
        arguments?.getString(KEY_MESSAGE).also {
            if (it.isNullOrBlank()) binding.infoMessage.isVisible = false
            else binding.infoMessage.text = it
        }
    }

    companion object {
        fun FragmentActivity.showInfoBottomSheet(
            @StringRes titleResId: Int,
            @StringRes messageResId: Int,
            textGravity: Int? = null,
        ) = showInfoBottomSheet(getString(titleResId), getString(messageResId), textGravity)

        fun FragmentActivity.showInfoBottomSheet(title: String, message: String, textGravity: Int? = null) =
            showInfoBottomSheet(supportFragmentManager, title, message, textGravity)

        fun Fragment.showInfoBottomSheet(
            @StringRes titleResId: Int,
            @StringRes messageResId: Int,
            textGravity: Int? = null,
        ) = showInfoBottomSheet(getString(titleResId), getString(messageResId), textGravity)

        fun Fragment.showInfoBottomSheet(title: String, message: String, textGravity: Int? = null) =
            showInfoBottomSheet(childFragmentManager, title, message, textGravity)

        fun showInfoBottomSheet(fragmentManager: FragmentManager, title: String, message: String, textGravity: Int? = null) =
            newInstance(title, message, textGravity ?: CENTER).show(fragmentManager, InfoBottomSheet::class.java.simpleName)

        private fun newInstance(title: String, message: String, textGravity: Int = CENTER) = InfoBottomSheet().apply {
            arguments = Bundle().apply {
                putString(KEY_TITLE, title)
                putString(KEY_MESSAGE, message)
                putInt(KEY_TEXT_GRAVITY, textGravity)
            }
        }

        const val KEY_TITLE = "key_title"
        const val KEY_MESSAGE = "key_message"
        const val KEY_TEXT_GRAVITY = "key_text_gravity"
    }
}