@file:Suppress("unused")

package de.lemke.commonutils.widget

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.lemke.commonutils.databinding.WidgetInfoBottomsheetBinding
import kotlin.apply

class InfoBottomSheet(
    private val activity: FragmentActivity,
    private val title: String = "",
    private val message: String = "",
    private val onDismissListener: DialogInterface.OnDismissListener? = null,
    private val textGravity: Int = Gravity.CENTER
) : BottomSheetDialogFragment() {
    private lateinit var binding: WidgetInfoBottomsheetBinding

    constructor(
        activity: FragmentActivity,
        @StringRes title: Int,
        @StringRes message: Int,
        onDismissListener: DialogInterface.OnDismissListener? = null,
        textGravity: Int = Gravity.CENTER
    ) : this(
        activity = activity,
        title = activity.getString(title),
        message = activity.getString(message),
        onDismissListener = onDismissListener,
        textGravity = textGravity
    )

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.onDismiss(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = WidgetInfoBottomsheetBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            behavior.skipCollapsed = true
            setOnShowListener { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.widgetInfoTitle.text = title
        binding.widgetInfoTitle.gravity = textGravity
        binding.widgetInfoMessage.text = message
        binding.widgetInfoMessage.gravity = textGravity
    }

    fun show() {
        show(activity.supportFragmentManager, InfoBottomSheet::class.java.simpleName)
    }
}