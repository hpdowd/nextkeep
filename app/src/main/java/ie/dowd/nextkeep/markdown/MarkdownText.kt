package ie.dowd.nextkeep.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders the common markdown subset (headings, lists, task lists, quotes,
 * fenced code, dividers, and inline bold/italic/code/strikethrough/links) as
 * Material 3 composables. Intentionally dependency-free — see
 * [parseMarkdownBlocks] for the parser it builds on.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier, maxBlocks: Int = Int.MAX_VALUE) {
    val parsed = remember(markdown) { parseMarkdownBlocks(markdown) }
    // For previews (maxBlocks set), drop blank lines and cap the count so a long
    // note can't produce a giant card.
    val source = remember(parsed, maxBlocks) {
        if (maxBlocks == Int.MAX_VALUE) parsed else parsed.filter { it != MdBlock.Blank }
    }
    val blocks = source.take(maxBlocks)
    val overflow = source.size > blocks.size
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    buildInline(block.text, linkColor, codeBg),
                    style = headingStyle(block.level),
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )

                is MdBlock.Paragraph -> Text(
                    buildInline(block.text, linkColor, codeBg),
                    style = MaterialTheme.typography.bodyLarge,
                )

                is MdBlock.Bullet -> MarkerRow(indent = block.indent, marker = "•") {
                    Text(buildInline(block.text, linkColor, codeBg), style = MaterialTheme.typography.bodyLarge)
                }

                is MdBlock.Numbered -> MarkerRow(indent = block.indent, marker = "${block.number}.") {
                    Text(buildInline(block.text, linkColor, codeBg), style = MaterialTheme.typography.bodyLarge)
                }

                is MdBlock.Task -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (block.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (block.checked) MaterialTheme.colorScheme.primary else onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildInline(block.text, linkColor, codeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (block.checked) onSurfaceVariant else Color.Unspecified,
                        textDecoration = if (block.checked) TextDecoration.LineThrough else null,
                    )
                }

                is MdBlock.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        buildInline(block.text, linkColor, codeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                    )
                }

                is MdBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(codeBg, RoundedCornerShape(8.dp))
                        .padding(PaddingValues(horizontal = 12.dp, vertical = 8.dp))
                ) {
                    Text(
                        block.lines.joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }

                MdBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
                MdBlock.Blank -> Spacer(Modifier.height(6.dp))
            }
        }
        if (overflow) {
            Text("…", style = MaterialTheme.typography.bodyLarge, color = onSurfaceVariant)
        }
    }
}

@Composable
private fun MarkerRow(indent: Int, marker: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.padding(start = (indent * 16).dp)) {
        Text(
            marker,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        content()
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.headlineMedium
    2 -> MaterialTheme.typography.headlineSmall
    3 -> MaterialTheme.typography.titleLarge
    else -> MaterialTheme.typography.titleMedium
}.copy(fontWeight = FontWeight.SemiBold)

/** Inline scanner: bold, italic, code, strikethrough, and links. Non-nested. */
private fun buildInline(text: String, linkColor: Color, codeBg: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val marker = text.substring(i, i + 2)
                    val close = text.indexOf(marker, i + 2)
                    if (close > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, close))
                        }
                        i = close + 2
                    } else {
                        append(text[i]); i++
                    }
                }

                text.startsWith("~~", i) -> {
                    val close = text.indexOf("~~", i + 2)
                    if (close > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(i + 2, close))
                        }
                        i = close + 2
                    } else {
                        append(text[i]); i++
                    }
                }

                text[i] == '`' -> {
                    val close = text.indexOf('`', i + 1)
                    if (close > i) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                            append(text.substring(i + 1, close))
                        }
                        i = close + 1
                    } else {
                        append(text[i]); i++
                    }
                }

                text[i] == '*' || text[i] == '_' -> {
                    val ch = text[i]
                    val close = text.indexOf(ch, i + 1)
                    if (close > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, close))
                        }
                        i = close + 1
                    } else {
                        append(text[i]); i++
                    }
                }

                text[i] == '[' -> {
                    val match = linkRe.find(text, i)
                    if (match != null && match.range.first == i) {
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            append(match.groupValues[1])
                        }
                        i = match.range.last + 1
                    } else {
                        append(text[i]); i++
                    }
                }

                else -> {
                    append(text[i]); i++
                }
            }
        }
    }

private val linkRe = Regex("\\[([^]]+)]\\(([^)]+)\\)")
