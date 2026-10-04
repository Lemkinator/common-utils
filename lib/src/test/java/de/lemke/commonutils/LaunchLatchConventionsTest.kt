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
package de.lemke.commonutils

import com.lemonappdev.konsist.api.Konsist
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.writeText

class LaunchLatchConventionsTest : ShouldSpec() {
    init {
        should("report each raw launch name") {
            val source =
                """
                startActivity(intent)
                startActivities(intents)
                startActivityForResult(intent, 1)
                startActivityIfNeeded(intent, 1)
                startActivityFromFragment(fragment, intent, 1)
                startIntentSender(sender, null, 0, 0, 0)
                startIntentSenderForResult(sender, 1, null, 0, 0, 0)
                val picker = registerForActivityResult(GetContent()) { }
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe
                listOf(
                    LaunchLatchViolation(1, "startActivity"),
                    LaunchLatchViolation(2, "startActivities"),
                    LaunchLatchViolation(3, "startActivityForResult"),
                    LaunchLatchViolation(4, "startActivityIfNeeded"),
                    LaunchLatchViolation(5, "startActivityFromFragment"),
                    LaunchLatchViolation(6, "startIntentSender"),
                    LaunchLatchViolation(7, "startIntentSenderForResult"),
                    LaunchLatchViolation(8, "registerForActivityResult"),
                )
        }
        should("report a launch with a receiver and with a safe-call receiver") {
            val source =
                """
                activity.startActivity(intent)
                context?.startActivity (intent)
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe
                listOf(LaunchLatchViolation(1, "startActivity"), LaunchLatchViolation(2, "startActivity"))
        }
        should("report method references to a launch") {
            val source =
                """
                val launch = ::startActivity
                val launchVia = context::startActivity
                val sender = ::startIntentSenderForResult
                val register = this::registerForActivityResult
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe
                listOf(
                    LaunchLatchViolation(1, "::startActivity"),
                    LaunchLatchViolation(2, "context::startActivity"),
                    LaunchLatchViolation(3, "::startIntentSenderForResult"),
                    LaunchLatchViolation(4, "this::registerForActivityResult"),
                )
        }
        should("report startIntentSenderForResult with a receiver") {
            LaunchLatchConventions.violations("activity.startIntentSenderForResult(sender, 1, null, 0, 0, 0, null)") shouldBe
                listOf(LaunchLatchViolation(1, "startIntentSenderForResult"))
        }
        should("accept the launch latch helpers") {
            val source =
                """
                safeStartActivity(intent)
                singleLaunchActivity(intent)
                private val picker = registerForSingleLaunchResult(GetContent()) { }
                """.trimIndent()

            LaunchLatchConventions.violations(source).shouldBeEmpty()
        }
        should("report a show in the expression body of a declaration") {
            LaunchLatchConventions.violations("fun open() = dialog.show()") shouldBe listOf(LaunchLatchViolation(1, "dialog.show"))
        }
        should("accept declarations of launch and show names") {
            val source =
                """
                override fun startActivity(intent: Intent) = Unit
                fun Context.startActivity(x: Int) = Unit
                fun <T> Foo<T>.startActivity(x: T) = Unit
                override fun show(manager: FragmentManager, tag: String?) = Unit
                """.trimIndent()

            LaunchLatchConventions.violations(source).shouldBeEmpty()
        }
        should("accept a declaration wrapped after its receiver") {
            val source =
                """
                fun Foo.
                    show() = Unit
                fun <T> Bar<T>
                    .startActivity(x: T) = Unit
                """.trimIndent()

            LaunchLatchConventions.violations(source).shouldBeEmpty()
        }
        should("report a show on a line after a declaration header") {
            val source =
                """
                fun open()
                    = dialog
                        .show()
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(3, "dialog.show"))
        }
        should("report a show in a default argument, a default lambda and an anonymous function") {
            val source =
                """
                fun open(
                    shown: Unit = dialog.show(),
                    action: Dialog.() -> Unit = { show() },
                ) = Unit
                val action = fun Dialog.() { show() }
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe
                listOf(LaunchLatchViolation(2, "dialog.show"), LaunchLatchViolation(3, "show"), LaunchLatchViolation(5, "show"))
        }
        should("report every line of a multi-line file in source order") {
            val source =
                """
                class MainActivity : AppCompatActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        startActivity(intent)

                        dialog.show()
                        val launcher = registerForActivityResult(GetContent()) { }
                    }
                }
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe
                listOf(
                    LaunchLatchViolation(4, "startActivity"),
                    LaunchLatchViolation(6, "dialog.show"),
                    LaunchLatchViolation(7, "registerForActivityResult"),
                )
        }
        should("ignore a launch in a line comment") {
            val source =
                """
                // startActivity(intent)
                startActivity(intent)
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(2, "startActivity"))
        }
        should("ignore a launch in a block comment") {
            val source =
                """
                /* startActivity(intent)
                   startActivity(intent) */
                startActivity(intent)
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(3, "startActivity"))
        }
        should("ignore a launch in a nested block comment") {
            val source =
                """
                /* outer /* inner */ startActivity(intent) */
                startActivity(intent)
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(2, "startActivity"))
        }
        should("ignore a launch and a show in KDoc") {
            val source =
                """
                /**
                 * Calls startActivity(intent) and dialog.show().
                 */
                fun open() {
                    startActivity(intent)
                }
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(5, "startActivity"))
        }
        should("ignore a launch in a string with an escaped quote") {
            val source =
                """
                val text = "startActivity(intent) \" dialog.show()"
                startActivity(intent)
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(2, "startActivity"))
        }
        should("ignore a launch in a raw string") {
            val source = "val text = \"\"\"\nstartActivity(intent)\n\"\"\"\nstartActivity(intent)"

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(4, "startActivity"))
        }
        should("ignore a launch and a show in string templates") {
            val source = "val text = \"\${dialog.show()} \${\"startActivity(x)\"}\"\nstartActivity(intent)"

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(2, "startActivity"))
        }
        should("not open a string at a quote char literal") {
            val source =
                """
                val apostrophe = '\''
                val quote = '"'
                startActivity(intent)
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(3, "startActivity"))
        }
        should("not open a literal at a quote inside a backtick identifier") {
            val source =
                """
                fun `it's a "quote`() = Unit
                startActivity(intent)
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(2, "startActivity"))
        }
        should("report a show named by a backtick identifier") {
            LaunchLatchConventions.violations("dialog.`show`()") shouldBe listOf(LaunchLatchViolation(1, "dialog.show"))
        }
        should("report a show of a dialog variable") {
            LaunchLatchConventions.violations("dialog.show()") shouldBe listOf(LaunchLatchViolation(1, "dialog.show"))
        }
        should("report a bare show in apply") {
            val source =
                """
                AlertDialog.Builder(this).create().apply {
                    setTitle(R.string.title)
                    show()
                }
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(3, "show"))
        }
        should("report a bare show in with") {
            val source =
                """
                with(dialog) {
                    show()
                }
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(2, "show"))
        }
        should("report a bare show in a DialogFragment subclass") {
            val source =
                """
                class InfoDialog : DialogFragment() {
                    fun open(manager: FragmentManager) {
                        show(manager, TAG)
                    }
                }
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(3, "show"))
        }
        should("report a bare showNow") {
            LaunchLatchConventions.violations("showNow(supportFragmentManager, TAG)") shouldBe listOf(LaunchLatchViolation(1, "showNow"))
        }
        should("report showNow with a receiver") {
            LaunchLatchConventions.violations("sheet.showNow(fm, TAG)") shouldBe listOf(LaunchLatchViolation(1, "sheet.showNow"))
        }
        should("report a show with type arguments") {
            LaunchLatchConventions.violations("sheets.show<InfoSheet>(supportFragmentManager)") shouldBe
                listOf(LaunchLatchViolation(1, "sheets.show"))
        }
        should("report a show with a trailing lambda") {
            LaunchLatchConventions.violations("dialog.show { setTitle(R.string.title) }") shouldBe
                listOf(LaunchLatchViolation(1, "dialog.show"))
        }
        should("report a super show inside an overriding show") {
            val source =
                """
                override fun show(manager: FragmentManager, tag: String?) {
                    super.show(manager, tag)
                }
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(2, "super.show"))
        }
        should("report a show of it in a safe-call let") {
            LaunchLatchConventions.violations("dialog?.let { it.show() }") shouldBe listOf(LaunchLatchViolation(1, "it.show"))
        }
        should("check the last receiver segment, not an allowlisted root") {
            LaunchLatchConventions.violations("toast.dialog.show()") shouldBe listOf(LaunchLatchViolation(1, "toast.dialog.show"))
        }
        should("report a one-line builder show by its receiver chain") {
            LaunchLatchConventions.violations("AlertDialog.Builder(this).setTitle(R.string.t).show()") shouldBe
                listOf(LaunchLatchViolation(1, "AlertDialog.Builder.setTitle.show"))
        }
        should("report a multi-line builder show on the line of show") {
            val source =
                """
                AlertDialog
                    .Builder(this)
                    .setTitle(R.string.title)
                    .setPositiveButton(R.string.ok) { _, _ -> finish() }
                    .show()
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe
                listOf(LaunchLatchViolation(5, "AlertDialog.Builder.setTitle.setPositiveButton.show"))
        }
        should("report a show behind safe calls and non-null assertions") {
            LaunchLatchConventions.violations("sheet?.dialog!!.show()") shouldBe listOf(LaunchLatchViolation(1, "sheet.dialog.show"))
        }
        should("report a dialog type that the file does not import") {
            val source =
                """
                package de.lemke.app

                class Loader(private val activity: Activity) {
                    fun load() {
                        ProgressDialog(activity).show()
                    }
                }
                """.trimIndent()

            LaunchLatchConventions.violations(source) shouldBe listOf(LaunchLatchViolation(5, "ProgressDialog.show"))
        }
        should("report a show of a parenthesized group by the chain that ends the group") {
            LaunchLatchConventions.violations("(sheet as? AlertDialog)?.show()") shouldBe
                listOf(LaunchLatchViolation(1, "AlertDialog.show"))
        }
        should("report a show reference without a receiver") {
            LaunchLatchConventions.violations("val open = ::show") shouldBe listOf(LaunchLatchViolation(1, "::show"))
        }
        should("report a show reference with a receiver") {
            LaunchLatchConventions.violations("val open = dialog::show") shouldBe listOf(LaunchLatchViolation(1, "dialog::show"))
        }
        should("report a showNow reference") {
            LaunchLatchConventions.violations("val open = ::showNow") shouldBe listOf(LaunchLatchViolation(1, "::showNow"))
        }
        listOf(
            "Toast.makeText(this, R.string.x, Toast.LENGTH_SHORT).show()",
            "Snackbar.make(view, R.string.x, Snackbar.LENGTH_SHORT).setAction(R.string.y) { undo() }.show()",
            "binding.sortPopupMenu.show()",
            "tipPopup.show(TipPopup.Direction.DEFAULT)",
            "PopupMenu(this, anchor).show()",
            "snackbar?.show()",
            "(popup as Toast).show()",
            "dialog.showOnce(TAG)",
            "imm.showSoftInput(view, 0)",
            "if (dialog.isShowing) dismiss()",
            "show < limit && x > (y)",
        ).forEach { source ->
            should("accept $source") {
                LaunchLatchConventions.violations(source).shouldBeEmpty()
            }
        }
        should("accept an app type passed as an extra show receiver") {
            LaunchLatchConventions.violations("searchBar.show()", extraShowReceivers = setOf("SearchBar")).shouldBeEmpty()
        }
        should("report an app type that is not passed as an extra show receiver") {
            LaunchLatchConventions.violations("searchBar.show()") shouldBe listOf(LaunchLatchViolation(1, "searchBar.show"))
        }
        should("keep the defaults when extra show receivers are passed") {
            val source =
                """
                searchBar.show()
                Toast.makeText(this, R.string.x, Toast.LENGTH_SHORT).show()
                dialog.show()
                """.trimIndent()

            LaunchLatchConventions.violations(source, extraShowReceivers = setOf("SearchBar")) shouldBe
                listOf(LaunchLatchViolation(3, "dialog.show"))
            LaunchLatchConventions.defaultShowReceivers shouldBe setOf("Snackbar", "Toast", "PopupMenu", "TipPopup")
        }
        should("list every hit of a scope as project path, line and match") {
            val dir = createTempDirectory(Path("build", "tmp").toAbsolutePath(), "launch-latch")
            try {
                dir.resolve("Clean.kt").writeText("package demo\n\nfun clean() = safeStartActivity(intent)\n")
                dir.resolve("Hits.kt").writeText("package demo\n\nfun open() {\n    startActivity(intent)\n    dialog.show()\n}\n")
                val scope = Konsist.scopeFromExternalDirectories(listOf(dir.toString()))
                val hitsPath = "/lib/build/tmp/${dir.name}/Hits.kt"

                val error = shouldThrow<AssertionError> { scope.assertLaunchLatchConventions() }

                error.message shouldBe "2 calls bypass the launch latch:\n$hitsPath:4: startActivity\n$hitsPath:5: dialog.show"
            } finally {
                dir.toFile().deleteRecursively()
            }
        }
        should("print a nested project path with / separators") {
            val dir = Path("build", "tmp", "launch-latch-paths").toAbsolutePath()
            try {
                val ui = dir.resolve("demo").resolve("ui").createDirectories()
                ui.resolve("Hits.kt").writeText("package demo.ui\n\nfun open() = startActivity(intent)\n")
                val scope = Konsist.scopeFromExternalDirectories(listOf(dir.toString()))

                val error = shouldThrow<AssertionError> { scope.assertLaunchLatchConventions() }

                error.message shouldBe
                    "1 call bypasses the launch latch:\n/lib/build/tmp/launch-latch-paths/demo/ui/Hits.kt:3: startActivity"
            } finally {
                dir.toFile().deleteRecursively()
            }
        }
        should("pass a scope of clean files") {
            val dir = createTempDirectory("launch-latch")
            try {
                dir.resolve("Clean.kt").writeText("package demo\n\nfun clean() = safeStartActivity(intent)\n")
                dir.resolve("Toasts.kt").writeText("package demo\n\nfun done() = Toast.makeText(this, R.string.x, 0).show()\n")
                val scope = Konsist.scopeFromExternalDirectories(listOf(dir.toString()))

                shouldNotThrowAny { scope.assertLaunchLatchConventions() }
            } finally {
                dir.toFile().deleteRecursively()
            }
        }
    }
}
