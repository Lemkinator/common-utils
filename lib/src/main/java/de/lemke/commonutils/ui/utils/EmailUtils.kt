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
package de.lemke.commonutils.ui.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SENDTO
import android.content.Intent.EXTRA_EMAIL
import android.content.Intent.EXTRA_SUBJECT
import android.content.Intent.EXTRA_TEXT
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.util.Log
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import de.lemke.commonutils.R

private const val TAG = "EmailUtils"

/** Opens a mail client to send an email to [email] with the given [subject] and [text]. */
fun Context.sendEmail(
    email: String,
    subject: String,
    text: String,
): Boolean = sendEmail(arrayOf(email), subject, text)

/** Opens a mail client to send an email to [emails] with the given [subject] and [text]. */
fun Context.sendEmail(
    emails: Array<String>,
    subject: String,
    text: String,
): Boolean =
    try {
        Intent(ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(EXTRA_EMAIL, emails)
            putExtra(EXTRA_SUBJECT, subject)
            putExtra(EXTRA_TEXT, text)
            if (this@sendEmail !is Activity) addFlags(FLAG_ACTIVITY_NEW_TASK)
            startActivity(this)
        }
        true
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "Failed to send email", e)
        toast(getString(R.string.commonutils_no_email_app_installed))
        false
    }

/** Sends a help request email with a pre-filled body. */
fun Context.sendEmailHelp(
    email: String,
    subject: String,
) = sendEmail(email, subject, getString(R.string.commonutils_help_email_text))

/** Sends an "about me" contact email with a pre-filled body. */
fun Context.sendEmailAboutMe(
    email: String,
    subject: String,
) = sendEmail(email, subject, getString(R.string.commonutils_about_email_text))

/** Sends a bug report email with a pre-filled body. */
fun Fragment.sendEmailBugReport(
    email: String,
    subject: String,
) = requireContext().sendEmail(email, subject, getString(R.string.commonutils_bug_report_email_text))
