package ie.dowd.nextkeep.markdown

/** Column alignment for a [MdBlock.Table], from the `:---:`-style separator row. */
enum class TableAlign { LEFT, CENTER, RIGHT }

/** Block-level markdown elements supported by the renderer. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val indent: Int, val text: String) : MdBlock
    data class Numbered(val indent: Int, val number: Int, val text: String) : MdBlock
    data class Task(val indent: Int, val checked: Boolean, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lines: List<String>) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TableAlign>,
        val rows: List<List<String>>,
    ) : MdBlock
    data object Divider : MdBlock
    data object Blank : MdBlock
}

private val headingRe = Regex("^(#{1,6}) +(.*)$")
// The brackets accept an optional leading backslash (`\[ \]`) since some sources
// (e.g. text pasted from elsewhere) backslash-escape them defensively; CommonMark
// treats `\[`/`\]` as a literal bracket anyway, so this is still a task marker.
private val taskRe = Regex("^( *)[-*+] \\\\?\\[([ xX])\\\\?] +(.*)$")
private val bulletRe = Regex("^( *)[-*+] +(.*)$")
private val numberedRe = Regex("^( *)(\\d+)\\. +(.*)$")
private val dividerRe = Regex("^ {0,3}(-{3,}|\\*{3,}|_{3,})$")
private val tableSeparatorCellRe = Regex("^:?-+:?$")

/** CommonMark's escapable ASCII punctuation: `\` + one of these is the literal char. */
internal val escapablePunctuation = setOf(
    '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
    ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '^', '_', '`', '{', '|', '}', '~',
)

/** Drops a backslash before an escapable punctuation char, leaving other backslashes alone. */
internal fun unescapeMarkdown(text: String): String {
    if ('\\' !in text) return text
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (ch == '\\' && i + 1 < text.length && text[i + 1] in escapablePunctuation) {
            sb.append(text[i + 1])
            i += 2
        } else {
            sb.append(ch)
            i++
        }
    }
    return sb.toString()
}

/**
 * Splits a pipe-delimited table row into trimmed cells. Leading/trailing pipes
 * are optional and dropped; `\|` is an escaped literal pipe.
 */
private fun splitTableCells(line: String): List<String> {
    val trimmed = line.trim()
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var i = 0
    while (i < trimmed.length) {
        val ch = trimmed[i]
        when {
            ch == '\\' && i + 1 < trimmed.length && trimmed[i + 1] == '|' -> {
                cell.append('|')
                i += 2
            }
            ch == '|' -> {
                cells += cell.toString()
                cell.clear()
                i++
            }
            else -> {
                cell.append(ch)
                i++
            }
        }
    }
    cells += cell.toString()
    if (cells.size > 1 && cells.first().isBlank() && trimmed.startsWith("|")) cells.removeAt(0)
    if (cells.size > 1 && cells.last().isBlank() && trimmed.endsWith("|")) cells.removeAt(cells.size - 1)
    return cells.map { it.trim() }
}

/** Parses a table separator row (e.g. `| --- | :---: |`) into per-column alignment, or null if it isn't one. */
private fun parseTableSeparator(line: String): List<TableAlign>? {
    if ('|' !in line) return null
    val cells = splitTableCells(line)
    if (cells.isEmpty()) return null
    return cells.map { raw ->
        if (!tableSeparatorCellRe.matches(raw)) return null
        when {
            raw.startsWith(":") && raw.endsWith(":") -> TableAlign.CENTER
            raw.endsWith(":") -> TableAlign.RIGHT
            else -> TableAlign.LEFT
        }
    }
}

/**
 * Parses the common markdown subset used in notes into block elements. Pure and
 * free of Compose types so it can be unit-tested; [MarkdownText] turns the
 * result into composables.
 */
fun parseMarkdownBlocks(markdown: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = markdown.split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val fence = line.trimStart().startsWith("```")
        if (fence) {
            val code = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code += lines[i]
                i++
            }
            if (i < lines.size) i++ // consume closing fence
            out += MdBlock.Code(code)
            continue
        }

        if ('|' in line && i + 1 < lines.size) {
            val alignments = parseTableSeparator(lines[i + 1])
            val headers = if (alignments != null) splitTableCells(line) else null
            if (alignments != null && headers != null && headers.isNotEmpty()) {
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && '|' in lines[i] && lines[i].isNotBlank()) {
                    rows += splitTableCells(lines[i]).let { row ->
                        when {
                            row.size == headers.size -> row
                            row.size > headers.size -> row.take(headers.size)
                            else -> row + List(headers.size - row.size) { "" }
                        }
                    }
                    i++
                }
                val colAlignments = List(headers.size) { idx -> alignments.getOrElse(idx) { TableAlign.LEFT } }
                out += MdBlock.Table(headers, colAlignments, rows)
                continue
            }
        }

        out += classifyLine(line)
        i++
    }
    return out
}

private fun classifyLine(line: String): MdBlock {
    if (line.isBlank()) return MdBlock.Blank
    dividerRe.find(line)?.let { return MdBlock.Divider }
    taskRe.find(line)?.let {
        return MdBlock.Task(
            indent = it.groupValues[1].length / 2,
            checked = it.groupValues[2].lowercase() == "x",
            text = it.groupValues[3],
        )
    }
    headingRe.find(line)?.let {
        return MdBlock.Heading(level = it.groupValues[1].length, text = it.groupValues[2])
    }
    numberedRe.find(line)?.let {
        return MdBlock.Numbered(
            indent = it.groupValues[1].length / 2,
            number = it.groupValues[2].toIntOrNull() ?: 1,
            text = it.groupValues[3],
        )
    }
    bulletRe.find(line)?.let {
        return MdBlock.Bullet(indent = it.groupValues[1].length / 2, text = it.groupValues[2])
    }
    if (line.trimStart().startsWith(">")) {
        return MdBlock.Quote(line.trimStart().removePrefix(">").trim())
    }
    return MdBlock.Paragraph(line)
}

private val inlineMarkers = Regex("(\\*\\*|__|\\*|_|`|~~)")
private val linkRe = Regex("\\[([^]]+)]\\(([^)]+)\\)")

/**
 * Strips markdown syntax to readable plain text for compact previews (note
 * cards), e.g. turns "- [ ] **Buy** milk" into "Buy milk".
 */
fun markdownToPlainText(markdown: String): String =
    markdown.split('\n').joinToString("\n") { raw ->
        var line = raw
        if (line.trimStart().startsWith("```")) return@joinToString ""
        if ('|' in line) {
            if (parseTableSeparator(line) != null) return@joinToString ""
            line = splitTableCells(line).joinToString(" ")
        }
        line = headingRe.replace(line) { it.groupValues[2] }
        line = taskRe.replace(line) { (if (it.groupValues[2].lowercase() == "x") "✓ " else "") + it.groupValues[3] }
        line = numberedRe.replace(line) { it.groupValues[3] }
        line = bulletRe.replace(line) { it.groupValues[2] }
        line = line.trimStart().removePrefix(">").let { if (it != line.trimStart()) it.trim() else line }
        line = linkRe.replace(line) { it.groupValues[1] }
        line = inlineMarkers.replace(line, "")
        line = unescapeMarkdown(line)
        line
    }.trim()
