@file:Suppress("unused")

package de.lemke.commonutils

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SENDTO
import android.content.Intent.EXTRA_EMAIL
import android.content.Intent.EXTRA_SUBJECT
import android.content.Intent.EXTRA_TEXT
import androidx.core.net.toUri
import androidx.fragment.app.Fragment

private const val TAG = "EmailUtils"

fun Context.sendEmail(email: String, subject: String, text: String, noEmailAppInstalledText: String? = null): Boolean =
    sendEmail(arrayOf(email), subject, text, noEmailAppInstalledText)

fun Context.sendEmail(emails: Array<String>, subject: String, text: String, noEmailAppInstalledText: String? = null): Boolean = try {
    Intent(ACTION_SENDTO).apply {
        data = "mailto:".toUri()
        putExtra(EXTRA_EMAIL, emails)
        putExtra(EXTRA_SUBJECT, subject)
        putExtra(EXTRA_TEXT, text)
        startActivity(this)
    }
    true
} catch (e: Exception) {
    e.printStackTrace()
    toast(noEmailAppInstalledText ?: getString(R.string.commonutils_no_email_app_installed))
    false
}

fun Context.sendEmailHelp(email: String, subject: String, noEmailAppInstalledText: String? = null): Boolean =
    sendEmail(email, subject, getString(R.string.commonutils_help_email_text), noEmailAppInstalledText)

fun Context.sendEmailAboutMe(email: String, subject: String, noEmailAppInstalledText: String? = null): Boolean =
    sendEmail(email, subject, getString(R.string.commonutils_about_email_text), noEmailAppInstalledText)

fun Fragment.sendEmailBugReport(email: String, subject: String, noEmailAppInstalledText: String? = null): Boolean =
    requireContext().sendEmail(email, subject, getString(R.string.commonutils_bug_report_email_text), noEmailAppInstalledText)

