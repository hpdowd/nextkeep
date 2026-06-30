package ie.dowd.nextkeep

import ie.dowd.nextkeep.markdown.MarkdownEditing
import ie.dowd.nextkeep.markdown.MdBlock
import ie.dowd.nextkeep.markdown.TableAlign
import ie.dowd.nextkeep.markdown.markdownToPlainText
import ie.dowd.nextkeep.markdown.parseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditingTest {

    @Test
    fun bullet_toggles_on_and_off() {
        val on = MarkdownEditing.toggleBullet("milk", 0, 0)
        assertEquals("- milk", on.text)
        val off = MarkdownEditing.toggleBullet(on.text, on.selStart, on.selEnd)
        assertEquals("milk", off.text)
    }

    @Test
    fun bullet_applies_to_every_selected_line() {
        val result = MarkdownEditing.toggleBullet("a\nb\nc", 0, 5)
        assertEquals("- a\n- b\n- c", result.text)
    }

    @Test
    fun heading_cycles_through_levels() {
        var e = MarkdownEditing.cycleHeading("Title", 0, 0)
        assertEquals("# Title", e.text)
        e = MarkdownEditing.cycleHeading(e.text, e.selStart, e.selEnd)
        assertEquals("## Title", e.text)
        e = MarkdownEditing.cycleHeading(e.text, e.selStart, e.selEnd)
        assertEquals("### Title", e.text)
        e = MarkdownEditing.cycleHeading(e.text, e.selStart, e.selEnd)
        assertEquals("Title", e.text)
    }

    @Test
    fun numbered_list_numbers_sequentially() {
        val result = MarkdownEditing.toggleNumberedList("a\nb\nc", 0, 5)
        assertEquals("1. a\n2. b\n3. c", result.text)
    }

    @Test
    fun indent_and_outdent_are_inverses() {
        val indented = MarkdownEditing.indent("hello", 0, 0)
        assertEquals("  hello", indented.text)
        val out = MarkdownEditing.outdent(indented.text, 0, 0)
        assertEquals("hello", out.text)
    }

    @Test
    fun checkbox_prefixes_line() {
        assertEquals("- [ ] task", MarkdownEditing.toggleCheckbox("task", 0, 0).text)
    }

    @Test
    fun formatting_an_empty_line_starts_the_construct() {
        // Regression: on a blank line the toggles must ADD the marker (a fresh
        // note is the most common place to tap these buttons).
        assertEquals("- ", MarkdownEditing.toggleBullet("", 0, 0).text)
        assertEquals("# ", MarkdownEditing.cycleHeading("", 0, 0).text)
        assertEquals("1. ", MarkdownEditing.toggleNumberedList("", 0, 0).text)
        assertEquals("- [ ] ", MarkdownEditing.toggleCheckbox("", 0, 0).text)
    }

    @Test
    fun caret_collapses_to_end_after_formatting_empty_line() {
        val e = MarkdownEditing.toggleBullet("", 0, 0)
        assertEquals(2, e.selStart) // caret after "- ", ready to type
        assertEquals(2, e.selEnd)
    }

    @Test
    fun bold_wraps_selection_and_moves_caret_inside() {
        val sel = MarkdownEditing.bold("hello", 0, 5)
        assertEquals("**hello**", sel.text)
        assertEquals(2, sel.selStart)
        assertEquals(7, sel.selEnd)

        val empty = MarkdownEditing.bold("ab", 1, 1)
        assertEquals("a****b", empty.text)
        assertEquals(3, empty.selStart) // caret between the markers
    }

    @Test
    fun bold_toggles_off_when_already_wrapped() {
        // selection covers the inner text of "**hello**"
        val off = MarkdownEditing.bold("**hello**", 2, 7)
        assertEquals("hello", off.text)
    }
}

class MarkdownParseTest {

    @Test
    fun parses_each_block_type() {
        assertEquals(MdBlock.Heading(2, "Notes"), parseMarkdownBlocks("## Notes").single())
        assertEquals(MdBlock.Bullet(0, "milk"), parseMarkdownBlocks("- milk").single())
        assertEquals(MdBlock.Numbered(0, 1, "first"), parseMarkdownBlocks("1. first").single())
        assertEquals(MdBlock.Task(0, false, "todo"), parseMarkdownBlocks("- [ ] todo").single())
        assertEquals(MdBlock.Task(0, true, "done"), parseMarkdownBlocks("- [x] done").single())
        assertEquals(MdBlock.Quote("hmm"), parseMarkdownBlocks("> hmm").single())
        assertEquals(MdBlock.Divider, parseMarkdownBlocks("---").single())
        assertEquals(MdBlock.Paragraph("just text"), parseMarkdownBlocks("just text").single())
    }

    @Test
    fun parses_fenced_code_block() {
        val blocks = parseMarkdownBlocks("```\nval x = 1\nval y = 2\n```")
        assertEquals(listOf(MdBlock.Code(listOf("val x = 1", "val y = 2"))), blocks)
    }

    @Test
    fun nested_bullet_indent_is_counted() {
        assertEquals(MdBlock.Bullet(1, "child"), parseMarkdownBlocks("  - child").single())
    }

    @Test
    fun nested_task_indent_is_counted() {
        // Regression: a checklist item nested under a bullet (e.g. via the
        // indent toolbar button) must still render as a checkbox, not fall
        // through to a plain bullet showing the literal "[ ] text".
        assertEquals(MdBlock.Task(1, false, "sub-task"), parseMarkdownBlocks("  - [ ] sub-task").single())
        assertEquals(MdBlock.Task(2, true, "done"), parseMarkdownBlocks("    * [x] done").single())
    }

    @Test
    fun parses_a_table_with_alignment_and_rows() {
        val md = """
            | Name | Qty | Price |
            | :--- | :---: | ---: |
            | Milk | 2 | 1.50 |
            | Eggs | 12 | 3.00 |
        """.trimIndent()
        val table = parseMarkdownBlocks(md).single() as MdBlock.Table
        assertEquals(listOf("Name", "Qty", "Price"), table.headers)
        assertEquals(listOf(TableAlign.LEFT, TableAlign.CENTER, TableAlign.RIGHT), table.alignments)
        assertEquals(
            listOf(listOf("Milk", "2", "1.50"), listOf("Eggs", "12", "3.00")),
            table.rows,
        )
    }

    @Test
    fun table_without_outer_pipes_is_still_parsed() {
        val md = "Name | Qty\n--- | ---\nMilk | 2"
        val table = parseMarkdownBlocks(md).single() as MdBlock.Table
        assertEquals(listOf("Name", "Qty"), table.headers)
        assertEquals(listOf(listOf("Milk", "2")), table.rows)
    }

    @Test
    fun table_rows_are_padded_or_truncated_to_header_width() {
        val md = "| A | B |\n| --- | --- |\n| short |\n| too | many | cells |"
        val table = parseMarkdownBlocks(md).single() as MdBlock.Table
        assertEquals(listOf(listOf("short", ""), listOf("too", "many")), table.rows)
    }

    @Test
    fun table_ends_at_a_blank_or_non_row_line() {
        val md = "| A |\n| --- |\n| 1 |\n\nnot a table row"
        val blocks = parseMarkdownBlocks(md)
        assertEquals(MdBlock.Table(listOf("A"), listOf(TableAlign.LEFT), listOf(listOf("1"))), blocks[0])
        assertEquals(MdBlock.Blank, blocks[1])
        assertEquals(MdBlock.Paragraph("not a table row"), blocks[2])
    }

    @Test
    fun a_lone_pipe_line_without_a_separator_is_just_a_paragraph() {
        assertEquals(MdBlock.Paragraph("a | b"), parseMarkdownBlocks("a | b").single())
    }

    @Test
    fun a_malformed_separator_row_falls_back_to_paragraphs() {
        // ":--:--:" has a colon in the middle, so it isn't a valid alignment cell -
        // this must not be misdetected as a table (and must not crash).
        val md = "| a | b |\n|:--:--:|\nmore text"
        val blocks = parseMarkdownBlocks(md)
        assertTrue(blocks.none { it is MdBlock.Table })
        assertEquals(MdBlock.Paragraph("| a | b |"), blocks[0])
    }

    @Test
    fun cell_text_keeps_raw_markdown_for_the_renderer_to_style() {
        val md = "| **Bold** | [link](http://x) |\n| --- | --- |"
        val table = parseMarkdownBlocks(md).single() as MdBlock.Table
        assertEquals(listOf("**Bold**", "[link](http://x)"), table.headers)
    }
}

class MarkdownStripTest {

    @Test
    fun strips_heading_marker() {
        assertEquals("Shopping", markdownToPlainText("# Shopping"))
    }

    @Test
    fun strips_task_and_inline_emphasis() {
        assertEquals("Buy milk", markdownToPlainText("- [ ] Buy **milk**"))
    }

    @Test
    fun keeps_checked_marker_and_strips_rest() {
        assertTrue(markdownToPlainText("- [x] done").contains("done"))
    }

    @Test
    fun strips_an_indented_task_too() {
        assertEquals("sub-task", markdownToPlainText("  - [ ] sub-task"))
    }

    @Test
    fun strips_link_to_its_text() {
        assertEquals("Nextcloud", markdownToPlainText("[Nextcloud](https://nextcloud.com)"))
    }

    @Test
    fun flattens_a_table_row_and_drops_the_separator() {
        assertEquals("Name Qty", markdownToPlainText("| Name | Qty |"))
        assertEquals("", markdownToPlainText("| --- | --- |"))
    }
}

class MarkdownTaskToggleTest {

    @Test
    fun toggles_unchecked_to_checked() {
        assertEquals("- [x] milk", MarkdownEditing.toggleTaskAt("- [ ] milk", 0))
    }

    @Test
    fun toggles_checked_to_unchecked() {
        assertEquals("- [ ] eggs", MarkdownEditing.toggleTaskAt("- [x] eggs", 0))
    }

    @Test
    fun toggles_only_the_indexed_task() {
        val content = "- [ ] a\n- [ ] b\n- [ ] c"
        assertEquals("- [ ] a\n- [x] b\n- [ ] c", MarkdownEditing.toggleTaskAt(content, 1))
    }

    @Test
    fun task_index_counts_only_task_lines() {
        val content = "# Title\n- [ ] a\nplain\n- [ ] b"
        assertEquals("# Title\n- [ ] a\nplain\n- [x] b", MarkdownEditing.toggleTaskAt(content, 1))
    }
}

class MarkdownNewlineTest {

    @Test
    fun continues_a_bullet() {
        val e = MarkdownEditing.onNewline("- a\n", 4)!!
        assertEquals("- a\n- ", e.text)
        assertEquals(6, e.selStart)
    }

    @Test
    fun continues_a_numbered_list_incrementing() {
        assertEquals("1. a\n2. ", MarkdownEditing.onNewline("1. a\n", 5)!!.text)
    }

    @Test
    fun continues_a_checkbox_unchecked() {
        assertEquals("- [x] done\n- [ ] ", MarkdownEditing.onNewline("- [x] done\n", 11)!!.text)
    }

    @Test
    fun empty_item_exits_the_list() {
        assertEquals("", MarkdownEditing.onNewline("- \n", 3)!!.text)
    }

    @Test
    fun returns_null_for_a_non_list_line() {
        assertNull(MarkdownEditing.onNewline("hello\n", 6))
    }
}
