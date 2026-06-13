package ie.dowd.nextkeep

import ie.dowd.nextkeep.data.NotesRepository.Companion.joinContent
import ie.dowd.nextkeep.data.NotesRepository.Companion.splitContent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Notes API stores a single content blob and derives the title from its
 * first line, while the UI keeps a Keep-style title + body. These tests pin down
 * that mapping, since a bad round-trip silently mutates users' notes.
 */
class ContentMappingTest {

    @Test
    fun join_titleAndBody_separatedByNewline() {
        assertEquals("Shopping\nmilk\neggs", joinContent("Shopping", "milk\neggs"))
    }

    @Test
    fun join_titleOnly_isJustTitle() {
        assertEquals("Shopping", joinContent("Shopping", ""))
    }

    @Test
    fun join_bodyOnly_isJustBody() {
        assertEquals("milk\neggs", joinContent("", "milk\neggs"))
    }

    @Test
    fun split_firstLineBecomesTitle_remainderBody() {
        assertEquals("Shopping" to "milk\neggs", splitContent("Shopping\nmilk\neggs"))
    }

    @Test
    fun split_singleLine_isTitleOnly() {
        assertEquals("Shopping" to "", splitContent("Shopping"))
    }

    @Test
    fun split_empty_isBlankPair() {
        assertEquals("" to "", splitContent(""))
    }

    @Test
    fun split_preservesBlankLinesInBody() {
        assertEquals("Title" to "\nbody after gap", splitContent("Title\n\nbody after gap"))
    }

    @Test
    fun roundTrip_titleAndBody_isStable() {
        val (title, body) = "Recipe" to "step 1\nstep 2"
        val (t2, b2) = splitContent(joinContent(title, body))
        assertEquals(title, t2)
        assertEquals(body, b2)
    }

    @Test
    fun roundTrip_rawContent_isExactlyPreserved() {
        // Joining the split of any server content must reproduce it byte-for-byte,
        // so syncing a note never rewrites it (e.g. markdown headings stay intact).
        for (content in listOf("# Heading\ntext", "single", "a\nb\nc", "", "line\n\n")) {
            val (title, body) = splitContent(content)
            assertEquals(content, joinContent(title, body))
        }
    }
}
