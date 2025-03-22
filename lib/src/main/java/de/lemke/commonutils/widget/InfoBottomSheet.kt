@file:Suppress("unused")

package de.lemke.commonutils.widget

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.lemke.commonutils.databinding.WidgetInfoBottomsheetBinding

class InfoBottomSheet : BottomSheetDialogFragment() {
    private lateinit var binding: WidgetInfoBottomsheetBinding

    companion object {
        fun FragmentActivity.showInfoBottomSheet(title: String, message: String, textGravity: Int = Gravity.CENTER) =
            showInfoBottomSheet(supportFragmentManager, title, message, textGravity)

        fun Fragment.showInfoBottomSheet(title: String, message: String, textGravity: Int = Gravity.CENTER) =
            showInfoBottomSheet(childFragmentManager, title, message, textGravity)

        fun showInfoBottomSheet(fragmentManager: FragmentManager, title: String, message: String, textGravity: Int = Gravity.CENTER) =
            newInstance(title, message, textGravity).show(fragmentManager, InfoBottomSheet::class.java.simpleName)

        private fun newInstance(title: String, message: String, textGravity: Int = Gravity.CENTER) = InfoBottomSheet().apply {
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

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        //onDismissListener?.onDismiss(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = WidgetInfoBottomsheetBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            behavior.skipCollapsed = true
            setOnShowListener { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val textGravity = arguments?.getInt(KEY_TEXT_GRAVITY) ?: Gravity.CENTER
        binding.widgetInfoTitle.text = arguments?.getString(KEY_TITLE) ?: ""
        binding.widgetInfoTitle.gravity = textGravity
        binding.widgetInfoMessage.text = arguments?.getString(KEY_MESSAGE) ?: ""
        binding.widgetInfoMessage.gravity = textGravity
    }
}