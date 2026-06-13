package ie.dowd.nextkeep

import ie.dowd.nextkeep.markdown.MarkdownEditing
import ie.dowd.nextkeep.markdown.MdBlock
import ie.dowd.nextkeep.markdown.markdownToPlainText
import ie.dowd.nextkeep.markdown.parseMarkdownBlocks
import org.junit.Assert.assertEquals
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
        assertEquals(MdBlock.Task(false, "todo"), parseMarkdownBlocks("- [ ] todo").single())
        assertEquals(MdBlock.Task(true, "done"), parseMarkdownBlocks("- [x] done").single())
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
    fun strips_link_to_its_text() {
        assertEquals("Nextcloud", markdownToPlainText("[Nextcloud](https://nextcloud.com)"))
    }
}
