package ie.dowd.nextkeep.markdown

/** Block-level markdown elements supported by the renderer. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val indent: Int, val text: String) : MdBlock
    data class Numbered(val indent: Int, val number: Int, val text: String) : MdBlock
    data class Task(val checked: Boolean, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lines: List<String>) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data object Divider : MdBlock
    data object Blank : MdBlock
}

private val headingRe = Regex("^(#{1,6}) +(.*)$")
private val taskRe = Regex("^[-*] \\[([ xX])] +(.*)$")
private val bulletRe = Regex("^( *)[-*+] +(.*)$")
private val numberedRe = Regex("^( *)(\\d+)\\. +(.*)$")
private val dividerRe = Regex("^ {0,3}(-{3,}|\\*{3,}|_{3,})$")

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

        out += classifyLine(line)
        i++
    }
    return out
}

private fun classifyLine(line: String): MdBlock {
    if (line.isBlank()) return MdBlock.Blank
    dividerRe.find(line)?.let { return MdBlock.Divider }
    taskRe.find(line)?.let {
        return MdBlock.Task(checked = it.groupValues[1].lowercase() == "x", text = it.groupValues[2])
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
        line = headingRe.replace(line) { it.groupValues[2] }
        line = taskRe.replace(line) { (if (it.groupValues[1].lowercase() == "x") "✓ " else "") + it.groupValues[2] }
        line = numberedRe.replace(line) { it.groupValues[3] }
        line = bulletRe.replace(line) { it.groupValues[2] }
        line = line.trimStart().removePrefix(">").let { if (it != line.trimStart()) it.trim() else line }
        line = linkRe.replace(line) { it.groupValues[1] }
        line = inlineMarkers.replace(line, "")
        line
    }.trim()
