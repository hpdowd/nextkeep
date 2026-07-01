package ie.dowd.nextkeep.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.dowd.nextkeep.ui.theme.LocalHeadingScale

/**
 * Renders the common markdown subset (headings, lists, task lists, quotes,
 * fenced code, dividers, and inline bold/italic/code/strikethrough/links) as
 * Material 3 composables. Intentionally dependency-free — see
 * [parseMarkdownBlocks] for the parser it builds on.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    maxBlocks: Int = Int.MAX_VALUE,
    previewLines: Int = 4,
    onToggleTask: ((Int) -> Unit)? = null,
) {
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
    // In preview mode (maxBlocks capped) also cap each block's lines, so a single
    // long paragraph or code block can't make a giant card.
    val bodyLines = if (maxBlocks == Int.MAX_VALUE) Int.MAX_VALUE else previewLines
    val headingLines = if (maxBlocks == Int.MAX_VALUE) Int.MAX_VALUE else 2
    // Size the checkbox and the gap after it off the body text so they scale with
    // it — the editor's pinch-zoom and the app-wide Font size setting.
    val bodyDp = with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.fontSize.toDp() }
    val checkboxSize = bodyDp * 1.25f
    val checkboxGap = bodyDp * 0.5f

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var taskOrdinal = 0
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    buildInline(block.text, linkColor, codeBg),
                    style = headingStyle(block.level),
                    maxLines = headingLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )

                is MdBlock.Paragraph -> Text(
                    buildInline(block.text, linkColor, codeBg),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = bodyLines,
                    overflow = TextOverflow.Ellipsis,
                )

                is MdBlock.Bullet -> MarkerRow(indent = block.indent, marker = "•") {
                    Text(
                        buildInline(block.text, linkColor, codeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = bodyLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is MdBlock.Numbered -> MarkerRow(indent = block.indent, marker = "${block.number}.") {
                    Text(
                        buildInline(block.text, linkColor, codeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = bodyLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is MdBlock.Task -> {
                    val ordinal = taskOrdinal++
                    val indentModifier = Modifier.padding(start = bodyDp * block.indent)
                    val rowModifier = if (onToggleTask != null) {
                        indentModifier.fillMaxWidth().clickable { onToggleTask(ordinal) }
                    } else {
                        indentModifier
                    }
                    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (block.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (block.checked) MaterialTheme.colorScheme.primary else onSurfaceVariant,
                        modifier = Modifier.size(checkboxSize),
                    )
                    Spacer(Modifier.width(checkboxGap))
                    Text(
                        buildInline(block.text, linkColor, codeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (block.checked) onSurfaceVariant else Color.Unspecified,
                        textDecoration = if (block.checked) TextDecoration.LineThrough else null,
                        maxLines = bodyLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    }
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
                        maxLines = bodyLines,
                        overflow = TextOverflow.Ellipsis,
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
                        maxLines = bodyLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is MdBlock.Table -> MarkdownTable(
                    headers = block.headers,
                    alignments = block.alignments,
                    rows = block.rows,
                    rowLimit = bodyLines,
                    cellMaxLines = if (maxBlocks == Int.MAX_VALUE) Int.MAX_VALUE else 2,
                    linkColor = linkColor,
                    codeBg = codeBg,
                )

                MdBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
                MdBlock.Blank -> Spacer(Modifier.height(6.dp))
            }
        }
        if (overflow) {
            Text("…", style = MaterialTheme.typography.bodyLarge, color = onSurfaceVariant)
        }
    }
}

/**
 * Renders a GFM table as a bordered grid. Column widths and row heights are
 * measured from the cells themselves (capped per cell so one long cell can't
 * blow out the whole table) then shared between the header and body rows via
 * [colWidths]/[rowHeights], which a [drawBehind] on the same layout uses to
 * paint the grid lines. Wrapped in horizontal scroll since a wide table
 * otherwise wouldn't fit a note card or the editor.
 */
@Composable
private fun MarkdownTable(
    headers: List<String>,
    alignments: List<TableAlign>,
    rows: List<List<String>>,
    rowLimit: Int,
    cellMaxLines: Int,
    linkColor: Color,
    codeBg: Color,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val bodyStyle = MaterialTheme.typography.bodyLarge
    val headerStyle = bodyStyle.copy(fontWeight = FontWeight.Bold)
    val shownRows = rows.take(rowLimit)
    val truncated = rows.size > shownRows.size
    val columns = headers.size
    val totalRows = shownRows.size + 1
    val density = LocalDensity.current
    val cellPadding = with(density) { 8.dp.roundToPx() }
    val cellMaxWidth = with(density) { 200.dp.roundToPx() }
    val colWidths = remember(columns) { IntArray(columns) }
    val rowHeights = remember(totalRows) { IntArray(totalRows) }

    Column(Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Layout(
                content = {
                    headers.forEach { cell ->
                        Text(
                            buildInline(cell, linkColor, codeBg),
                            style = headerStyle,
                            maxLines = cellMaxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    shownRows.forEach { row ->
                        row.forEach { cell ->
                            Text(
                                buildInline(cell, linkColor, codeBg),
                                style = bodyStyle,
                                maxLines = cellMaxLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                modifier = Modifier
                    .border(1.dp, borderColor)
                    .drawBehind {
                        var x = 0f
                        for (c in 0 until columns - 1) {
                            x += colWidths[c]
                            drawLine(borderColor, Offset(x, 0f), Offset(x, size.height))
                        }
                        var y = 0f
                        for (r in 0 until totalRows - 1) {
                            y += rowHeights[r]
                            drawLine(borderColor, Offset(0f, y), Offset(size.width, y))
                        }
                    },
            ) { measurables, _ ->
                val placeables = measurables.map { it.measure(Constraints(maxWidth = cellMaxWidth)) }
                for (c in 0 until columns) colWidths[c] = 0
                for (r in 0 until totalRows) rowHeights[r] = 0
                for (r in 0 until totalRows) {
                    for (c in 0 until columns) {
                        val p = placeables[r * columns + c]
                        colWidths[c] = maxOf(colWidths[c], p.width + 2 * cellPadding)
                        rowHeights[r] = maxOf(rowHeights[r], p.height + 2 * cellPadding)
                    }
                }
                layout(colWidths.sum(), rowHeights.sum()) {
                    var y = 0
                    for (r in 0 until totalRows) {
                        var x = 0
                        for (c in 0 until columns) {
                            val p = placeables[r * columns + c]
                            val innerWidth = colWidths[c] - 2 * cellPadding
                            val offsetX = when (alignments.getOrElse(c) { TableAlign.LEFT }) {
                                TableAlign.CENTER -> (innerWidth - p.width) / 2
                                TableAlign.RIGHT -> innerWidth - p.width
                                TableAlign.LEFT -> 0
                            }.coerceAtLeast(0)
                            p.placeRelative(x + cellPadding + offsetX, y + cellPadding)
                            x += colWidths[c]
                        }
                        y += rowHeights[r]
                    }
                }
            }
        }
        if (truncated) {
            Text("…", style = bodyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MarkerRow(indent: Int, marker: String, content: @Composable () -> Unit) {
    // Indent step and marker column track the body text size so nested lists and
    // numbered markers stay aligned as the text scales.
    val bodyDp = with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.fontSize.toDp() }
    Row(modifier = Modifier.padding(start = bodyDp * indent)) {
        Text(
            marker,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(bodyDp * 1.5f),
        )
        content()
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle {
    // Sized relative to body text (so headings follow the Font size setting) and
    // scaled by the Heading size setting. Deliberately smaller than the M3
    // headline styles, which looked oversized on note cards and in the editor.
    val bodySize = MaterialTheme.typography.bodyLarge.fontSize.value
    val multiplier = when (level) {
        1 -> 1.35f
        2 -> 1.2f
        3 -> 1.08f
        else -> 1f
    }
    val size = bodySize * multiplier * LocalHeadingScale.current
    return MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = size.sp,
        lineHeight = (size * 1.3f).sp,
    )
}

/** Inline scanner: bold, italic, code, strikethrough, and links. Non-nested. */
private fun buildInline(text: String, linkColor: Color, codeBg: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text[i] == '\\' && i + 1 < text.length && text[i + 1] in escapablePunctuation -> {
                    append(text[i + 1])
                    i += 2
                }

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
