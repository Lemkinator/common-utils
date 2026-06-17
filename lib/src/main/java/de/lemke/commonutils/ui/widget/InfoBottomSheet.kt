/*
 * Copyright 2024-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("unused")

package de.lemke.commonutils.ui.widget

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
import de.lemke.commonutils.databinding.WidgetInfoBottomsheetBinding
import dev.oneuiproject.oneui.app.SemBottomSheetDialogFragment

/** Bottom sheet dialog that displays a title and a message, used for informational overlays. */
class InfoBottomSheet : SemBottomSheetDialogFragment() {
    private lateinit var binding: WidgetInfoBottomsheetBinding

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // onDismissListener?.onDismiss(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = WidgetInfoBottomsheetBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            behavior.skipCollapsed = true
            setOnShowListener { behavior.state = STATE_EXPANDED }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.getInt(KEY_TEXT_GRAVITY)?.also {
            binding.infoTitle.gravity = it
            binding.infoMessage.gravity = it
        }
        arguments?.getString(KEY_TITLE).also {
            if (it.isNullOrBlank()) {
                binding.infoTitle.isVisible = false
            } else {
                binding.infoTitle.text = it
            }
        }
        arguments?.getString(KEY_MESSAGE).also {
            if (it.isNullOrBlank()) {
                binding.infoMessage.isVisible = false
            } else {
                binding.infoMessage.text = it
            }
        }
    }

    companion object {
        /** Shows an [InfoBottomSheet] with string-resource [titleResId] and [messageResId]. */
        fun FragmentActivity.showInfoBottomSheet(
            @StringRes titleResId: Int,
            @StringRes messageResId: Int,
            textGravity: Int? = null,
        ) = showInfoBottomSheet(getString(titleResId), getString(messageResId), textGravity)

        /** Shows an [InfoBottomSheet] with the given [title] and [message]. */
        fun FragmentActivity.showInfoBottomSheet(
            title: String,
            message: String,
            textGravity: Int? = null,
        ) = showInfoBottomSheet(supportFragmentManager, title, message, textGravity)

        /** Shows an [InfoBottomSheet] with string-resource [titleResId] and [messageResId]. */
        fun Fragment.showInfoBottomSheet(
            @StringRes titleResId: Int,
            @StringRes messageResId: Int,
            textGravity: Int? = null,
        ) = showInfoBottomSheet(getString(titleResId), getString(messageResId), textGravity)

        /** Shows an [InfoBottomSheet] with the given [title] and [message]. */
        fun Fragment.showInfoBottomSheet(
            title: String,
            message: String,
            textGravity: Int? = null,
        ) = showInfoBottomSheet(childFragmentManager, title, message, textGravity)

        /** Shows an [InfoBottomSheet] using the given [fragmentManager], [title], [message], and optional [textGravity]. */
        fun showInfoBottomSheet(
            fragmentManager: FragmentManager,
            title: String,
            message: String,
            textGravity: Int? = null,
        ) = newInstance(title, message, textGravity ?: CENTER).show(fragmentManager, InfoBottomSheet::class.java.simpleName)

        private fun newInstance(
            title: String,
            message: String,
            textGravity: Int,
        ) = InfoBottomSheet().apply {
            arguments =
                Bundle().apply {
                    putString(KEY_TITLE, title)
                    putString(KEY_MESSAGE, message)
                    putInt(KEY_TEXT_GRAVITY, textGravity)
                }
        }

        /** Bundle argument key for the sheet title. */
        const val KEY_TITLE = "key_title"

        /** Bundle argument key for the sheet message. */
        const val KEY_MESSAGE = "key_message"

        /** Bundle argument key for the text gravity applied to title and message. */
        const val KEY_TEXT_GRAVITY = "key_text_gravity"
    }
}
