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

import com.lemonappdev.konsist.api.container.KoScope

/** One raw launch or dialog show that bypasses the launch latch. */
data class LaunchLatchViolation(
    val line: Int,
    val match: String,
)

/**
 * Finds raw activity launches and dialog shows in Kotlin source that bypass the common-utils launch latch.
 *
 * Konsist has no type resolution, so both rules match names: a launch by its function name, a show by the
 * names in its receiver chain. Comments, string literals, char literals and backtick identifiers
 * never match.
 */
object LaunchLatchConventions {
    /** Receiver types whose `show` is no dialog: Snackbar, Toast, PopupMenu, TipPopup. */
    val defaultShowReceivers: Set<String> = setOf("Snackbar", "Toast", "PopupMenu", "TipPopup")

    private const val RAW_QUOTE = "\"\"\""
    private const val TEMPLATE_START = "\${"
    private const val REFERENCE = "::"

    private val launchName =
        Regex(
            "\\b(startActivity|startActivities|startActivityForResult|startActivityIfNeeded|startActivityFromFragment|" +
                "startIntentSender|startIntentSenderForResult|registerForActivityResult)\\b",
        )
    private val showName = Regex("""\b(show|showNow)\b""")
    private val declarationPrefix = Regex("""\bfun\s[^(){}=;]*$""")
    private val closingBrackets = mapOf(')' to '(', ']' to '[', '}' to '{', '>' to '<')

    /** The violations in [source], in source order. */
    fun violations(
        source: String,
        extraShowReceivers: Set<String> = emptySet(),
    ): List<LaunchLatchViolation> {
        val code = stripLiterals(source)
        val allowed = defaultShowReceivers + extraShowReceivers
        val launches = launchName.findAll(code).mapNotNull { code.launchViolation(it.range.first, it.value) }
        val shows = showName.findAll(code).mapNotNull { code.showViolation(it.range.first, it.value, allowed) }
        return (launches + shows)
            .sortedBy { it.first }
            .map { (index, match) -> LaunchLatchViolation(code.lineAt(index), match) }
            .toList()
    }

    private fun String.launchViolation(
        start: Int,
        name: String,
    ): Pair<Int, String>? =
        usageAt(start, name)?.let { usage ->
            Pair(start, if (usage.isReference) usage.referenceText(name) else name)
        }

    private fun String.showViolation(
        start: Int,
        name: String,
        allowed: Set<String>,
    ): Pair<Int, String>? {
        val usage = usageAt(start, name)?.takeUnless { isAllowedReceiver(it.receiver, allowed) } ?: return null
        val text =
            when {
                usage.isReference -> usage.referenceText(name)
                usage.receiver.isEmpty() -> name
                else -> usage.receiver.joinToString(".", postfix = ".$name") { it.name }
            }
        return start to text
    }

    /** The call or reference of the name at [start], or null for a declaration or any other use. */
    private fun String.usageAt(
        start: Int,
        name: String,
    ): Usage? {
        val before = skipWhitespaceBackward(start)
        val isReference = before >= REFERENCE.length && startsWith(REFERENCE, before - REFERENCE.length)
        return when {
            isReference -> Usage(receiverChain(before - REFERENCE.length), isReference = true)
            !isSegmentCall(start + name.length) || isDeclaration(start) -> null
            else -> Usage(receiverBefore(before), isReference = false)
        }
    }

    private fun String.isParenthesisAt(index: Int): Boolean = getOrNull(skipWhitespaceForward(index)) == '('

    private fun String.isDeclaration(start: Int): Boolean =
        declarationPrefix.containsMatchIn(substring(lastIndexOf('\n', start - 1) + 1, start))

    private fun String.receiverBefore(end: Int): List<Segment> {
        val access = memberAccessLength(end)
        return if (access == 0) emptyList() else receiverChain(end - access)
    }

    /**
     * The receiver to check is the last segment that is no call (`dialog` in `toast.dialog`), or the root if every
     * segment is a call (`PopupMenu(this, anchor)`).
     */
    private fun String.isAllowedReceiver(
        receiver: List<Segment>,
        allowed: Set<String>,
    ): Boolean {
        val checked = receiver.lastOrNull { !isSegmentCall(it.end) } ?: receiver.firstOrNull() ?: return false
        val typeName = checked.name.replaceFirstChar(Char::uppercaseChar)
        return allowed.any(typeName::endsWith)
    }

    private fun String.isSegmentCall(end: Int): Boolean {
        val next = skipWhitespaceForward(end)
        return when (getOrNull(next)) {
            '(', '{' -> true
            '<' -> isParenthesisAt(closingAngleIndex(next) + 1)
            else -> false
        }
    }

    private fun String.closingAngleIndex(openIndex: Int): Int {
        var depth = 0
        var index = openIndex
        while (index < length) {
            when (this[index]) {
                '<' -> depth++
                '>' -> if (--depth == 0) return index
            }
            index++
        }
        return length
    }

    /** The segments of the call chain that ends at the access at [accessIndex], from its root; arguments and `!!` are skipped. */
    private fun String.receiverChain(accessIndex: Int): List<Segment> {
        val end = skipCallSuffixes(accessIndex)
        val start = identifierStart(end)
        if (start == end) return emptyList()
        val segment = Segment(substring(start, end), end)
        val beforeAccess = skipWhitespaceBackward(start)
        val access = memberAccessLength(beforeAccess)
        return if (access == 0) listOf(segment) else receiverChain(beforeAccess - access) + segment
    }

    private fun String.skipWhitespaceBackward(end: Int): Int {
        var index = end
        while (index > 0 && this[index - 1].isWhitespace()) index--
        return index
    }

    private fun String.skipWhitespaceForward(start: Int): Int {
        var index = start
        while (index < length && this[index].isWhitespace()) index++
        return index
    }

    /** Skips whitespace, `!!` and bracketed groups (arguments, lambdas, type arguments) that end at [end]. */
    private fun String.skipCallSuffixes(end: Int): Int {
        var index = skipWhitespaceBackward(end)
        while (index > 0 && (this[index - 1] == '!' || this[index - 1] in closingBrackets)) {
            index = if (this[index - 1] == '!') index - 1 else openingBracketIndex(index - 1)
            index = skipWhitespaceBackward(index)
        }
        return index
    }

    private fun String.openingBracketIndex(closeIndex: Int): Int {
        val close = this[closeIndex]
        val open = closingBrackets.getValue(close)
        var depth = 0
        var index = closeIndex
        while (index >= 0) {
            when (this[index]) {
                close -> depth++
                open -> if (--depth == 0) return index
            }
            index--
        }
        return 0
    }

    private fun String.identifierStart(end: Int): Int {
        var index = end
        while (index > 0 && (this[index - 1].isLetterOrDigit() || this[index - 1] == '_')) index--
        return index
    }

    /** The length of the `.` or `?.` that ends at [end], or 0. */
    private fun String.memberAccessLength(end: Int): Int =
        when {
            end >= 2 && substring(end - 2, end) == "?." -> 2
            end >= 1 && this[end - 1] == '.' -> 1
            else -> 0
        }

    private fun String.lineAt(index: Int): Int = 1 + (0 until index).count { this[it] == '\n' }

    /**
     * [source] with every comment, string literal, char literal and backtick identifier blanked to spaces; line breaks
     * stay, so offsets and lines match.
     */
    private fun stripLiterals(source: String): String {
        val code = StringBuilder(source)
        var index = 0
        while (index < source.length) {
            val end = source.literalEnd(index)
            for (blanked in index until end) {
                if (code[blanked] != '\n' && code[blanked] != '\r') code[blanked] = ' '
            }
            index = maxOf(end, index + 1)
        }
        return code.toString()
    }

    /** The end of the comment, literal or backtick identifier that starts at [start], or [start] if none starts there. */
    private fun String.literalEnd(start: Int): Int =
        when {
            startsWith("//", start) -> indexOf('\n', start).takeIf { it >= 0 } ?: length
            startsWith("/*", start) -> blockCommentEnd(start)
            startsWith(RAW_QUOTE, start) -> stringEnd(start + RAW_QUOTE.length, raw = true)
            this[start] == '"' -> stringEnd(start + 1, raw = false)
            this[start] == '\'' -> charEnd(start + 1)
            this[start] == '`' -> indexOf('`', start + 1).takeIf { it >= 0 }?.plus(1) ?: length
            else -> start
        }

    private fun String.blockCommentEnd(start: Int): Int {
        var depth = 0
        var index = start
        while (index < length) {
            val delimiter =
                when {
                    startsWith("/*", index) -> 1
                    startsWith("*/", index) -> -1
                    else -> 0
                }
            depth += delimiter
            index += if (delimiter == 0) 1 else 2
            if (depth == 0) return index
        }
        return length
    }

    private fun String.stringEnd(
        start: Int,
        raw: Boolean,
    ): Int {
        var index = start
        while (index < length && !isStringClose(index, raw)) {
            index =
                when {
                    !raw && this[index] == '\\' -> index + 2
                    startsWith(TEMPLATE_START, index) -> templateEnd(index + TEMPLATE_START.length)
                    else -> index + 1
                }
        }
        return when {
            index >= length -> length
            raw -> skipQuotes(index)
            else -> index + 1
        }
    }

    private fun String.isStringClose(
        index: Int,
        raw: Boolean,
    ): Boolean = if (raw) startsWith(RAW_QUOTE, index) else this[index] == '"'

    /** A raw string may end in more than three quotes; the extra ones belong to its content. */
    private fun String.skipQuotes(start: Int): Int {
        var index = start
        while (index < length && this[index] == '"') index++
        return index
    }

    private fun String.templateEnd(start: Int): Int {
        var depth = 1
        var index = start
        while (index < length && depth > 0) {
            val literalEnd = literalEnd(index)
            when {
                literalEnd > index -> index = literalEnd - 1
                this[index] == '{' -> depth++
                this[index] == '}' -> depth--
            }
            index++
        }
        return index
    }

    private fun String.charEnd(start: Int): Int {
        val contentEnd = if (getOrNull(start) == '\\') start + 2 else start + 1
        return indexOf('\'', contentEnd).takeIf { it >= 0 }?.plus(1) ?: length
    }

    private data class Segment(
        val name: String,
        val end: Int,
    )

    private data class Usage(
        val receiver: List<Segment>,
        val isReference: Boolean,
    ) {
        fun referenceText(name: String): String = receiver.joinToString(".", postfix = "$REFERENCE$name") { it.name }
    }
}

/**
 * Fails with one message that lists every violation in this scope's files as `path:line: match`, one per line.
 */
fun KoScope.assertLaunchLatchConventions(extraShowReceivers: Set<String> = emptySet()) {
    val hits =
        files
            .sortedBy { it.path }
            .flatMap { file ->
                LaunchLatchConventions.violations(file.text, extraShowReceivers).map { "${file.path}:${it.line}: ${it.match}" }
            }
    if (hits.isNotEmpty()) {
        val header = if (hits.size == 1) "1 call bypasses the launch latch:" else "${hits.size} calls bypass the launch latch:"
        throw AssertionError(hits.joinToString("\n", prefix = "$header\n"))
    }
}
