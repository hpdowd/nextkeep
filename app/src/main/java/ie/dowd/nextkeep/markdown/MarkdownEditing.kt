package ie.dowd.nextkeep.markdown

/**
 * Pure text transforms behind the editor's formatting toolbar. Each takes the
 * current text and selection (start/end offsets) and returns the new text plus
 * the selection to apply afterwards. Kept free of Compose types so the logic is
 * unit-testable on a plain JVM.
 */
object MarkdownEditing {

    data class Edit(val text: String, val selStart: Int, val selEnd: Int)

    private val numberedRe = Regex("^\\d+\\. ")
    private val headingRe = Regex("^(#{1,6}) ")

    fun cycleHeading(text: String, selStart: Int, selEnd: Int): Edit =
        transformLines(text, selStart, selEnd) { lines ->
            val first = lines.firstOrNull { it.isNotBlank() }.orEmpty()
            val current = headingRe.find(first)?.groupValues?.get(1)?.length ?: 0
            val next = (current + 1) % 4 // none -> H1 -> H2 -> H3 -> none
            val single = lines.size == 1
            lines.map { line ->
                if (line.isBlank() && !single) line
                else {
                    val stripped = line.replaceFirst(headingRe, "")
                    if (next == 0) stripped else "#".repeat(next) + " " + stripped
                }
            }
        }

    fun toggleBullet(text: String, selStart: Int, selEnd: Int): Edit =
        toggleLinePrefix(text, selStart, selEnd, "- ")

    fun toggleQuote(text: String, selStart: Int, selEnd: Int): Edit =
        toggleLinePrefix(text, selStart, selEnd, "> ")

    fun toggleCheckbox(text: String, selStart: Int, selEnd: Int): Edit =
        toggleLinePrefix(text, selStart, selEnd, "- [ ] ")

    fun toggleNumberedList(text: String, selStart: Int, selEnd: Int): Edit =
        transformLines(text, selStart, selEnd) { lines ->
            val nonBlank = lines.filter { it.isNotBlank() }
            val allNumbered = nonBlank.isNotEmpty() && nonBlank.all { numberedRe.containsMatchIn(it) }
            val single = lines.size == 1
            if (allNumbered) {
                lines.map { if (it.isBlank() && !single) it else it.replaceFirst(numberedRe, "") }
            } else {
                var n = 1
                lines.map { line ->
                    if (line.isBlank() && !single) line
                    else "${n++}. " + line.replaceFirst(numberedRe, "")
                }
            }
        }

    fun indent(text: String, selStart: Int, selEnd: Int): Edit =
        transformLines(text, selStart, selEnd) { lines ->
            val single = lines.size == 1
            lines.map { if (it.isBlank() && !single) it else "  $it" }
        }

    fun outdent(text: String, selStart: Int, selEnd: Int): Edit =
        transformLines(text, selStart, selEnd) { lines ->
            lines.map { line ->
                when {
                    line.startsWith("  ") -> line.substring(2)
                    line.startsWith(" ") || line.startsWith("\t") -> line.substring(1)
                    else -> line
                }
            }
        }

    fun bold(text: String, selStart: Int, selEnd: Int): Edit =
        wrapInline(text, selStart, selEnd, "**")

    fun italic(text: String, selStart: Int, selEnd: Int): Edit =
        wrapInline(text, selStart, selEnd, "*")

    fun wrapInline(text: String, selStart: Int, selEnd: Int, marker: String): Edit {
        val s = minOf(selStart, selEnd).coerceIn(0, text.length)
        val e = maxOf(selStart, selEnd).coerceIn(0, text.length)
        val len = marker.length

        // Already wrapped in this marker -> unwrap (toggle off).
        val before = text.substring((s - len).coerceAtLeast(0), s)
        val after = text.substring(e, (e + len).coerceAtMost(text.length))
        if (s != e && before == marker && after == marker) {
            val unwrapped = text.substring(0, s - len) +
                text.substring(s, e) +
                text.substring(e + len)
            return Edit(unwrapped, s - len, e - len)
        }

        val wrapped = text.substring(0, s) + marker + text.substring(s, e) + marker + text.substring(e)
        return if (s == e) {
            val caret = s + len
            Edit(wrapped, caret, caret)
        } else {
            Edit(wrapped, s + len, e + len)
        }
    }

    private fun toggleLinePrefix(text: String, selStart: Int, selEnd: Int, prefix: String): Edit =
        transformLines(text, selStart, selEnd) { lines ->
            val nonBlank = lines.filter { it.isNotBlank() }
            val allHave = nonBlank.isNotEmpty() && nonBlank.all { it.startsWith(prefix) }
            val single = lines.size == 1
            lines.map { line ->
                when {
                    line.isBlank() && !single -> line
                    allHave -> line.removePrefix(prefix)
                    else -> prefix + line
                }
            }
        }

    private fun transformLines(
        text: String,
        selStart: Int,
        selEnd: Int,
        transform: (List<String>) -> List<String>,
    ): Edit {
        val s = minOf(selStart, selEnd).coerceIn(0, text.length)
        val e = maxOf(selStart, selEnd).coerceIn(0, text.length)
        val wasCursor = s == e
        val start = blockStart(text, s)
        val end = blockEnd(text, e)
        val newBlock = transform(text.substring(start, end).split('\n')).joinToString("\n")
        val newText = text.substring(0, start) + newBlock + text.substring(end)
        val blockEndPos = start + newBlock.length
        // A plain caret collapses to the end of the line (keep typing after the
        // marker); a real selection keeps the block selected for easy re-toggle.
        return if (wasCursor) Edit(newText, blockEndPos, blockEndPos)
        else Edit(newText, start, blockEndPos)
    }

    private fun blockStart(text: String, pos: Int): Int =
        text.lastIndexOf('\n', (pos - 1).coerceAtLeast(-1)) + 1

    private fun blockEnd(text: String, pos: Int): Int =
        text.indexOf('\n', pos).let { if (it < 0) text.length else it }
}
