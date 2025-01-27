@file:Suppress("unused")

package de.lemke.commonutils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment

private const val TAG = "EmailUtils"

fun Context.sendEmail(email: String, subject: String, text: String, noEmailAppInstalledText: String? = null): Boolean =
    sendEmail(arrayOf(email), subject, text, noEmailAppInstalledText)

fun Context.sendEmail(emails: Array<String>, subject: String, text: String, noEmailAppInstalledText: String? = null): Boolean = try {
    Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, emails)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
        startActivity(this)
    }
    true
} catch (e: Exception) {
    e.printStackTrace()
    toast(noEmailAppInstalledText ?: getString(R.string.no_email_app_installed))
    false
}

fun Context.sendEmailHelp(email: String, subject: String, noEmailAppInstalledText: String? = null): Boolean =
    sendEmail(email, subject, "By far, this is the greatest app I’ve ever used!", noEmailAppInstalledText)

fun Context.sendEmailAboutMe(email: String, subject: String, noEmailAppInstalledText: String? = null): Boolean =
    sendEmail(email, subject, "This is the best app I’ve ever seen in my life!", noEmailAppInstalledText)

fun Fragment.sendEmailBugReport(email: String, subject: String, noEmailAppInstalledText: String? = null): Boolean =
    requireContext().sendEmail(
        email,
        subject,
        "I can’t imagine a better app than this — it’s perfect and doesn’t need any improvements. But here’s a bug report anyway: ",
        noEmailAppInstalledText
    )

