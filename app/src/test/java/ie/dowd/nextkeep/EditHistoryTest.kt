package ie.dowd.nextkeep

import ie.dowd.nextkeep.ui.editor.EditHistory
import ie.dowd.nextkeep.ui.editor.EditSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    private fun snap(body: String, sel: Int = body.length) =
        EditSnapshot(title = "", body = body, selStart = sel, selEnd = sel)

    @Test
    fun freshHistoryHasNothingToUndoOrRedo() {
        val h = EditHistory()
        h.reset(snap(""))
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertNull(h.undo())
        assertNull(h.redo())
    }

    @Test
    fun discreteStepsUndoAndRedoInOrder() {
        val h = EditHistory()
        h.reset(snap(""))
        h.record(snap("a"), coalesce = false)
        h.record(snap("ab"), coalesce = false)

        assertTrue(h.canUndo)
        assertFalse(h.canRedo)
        assertEquals("a", h.undo()?.body)
        assertEquals("", h.undo()?.body)
        assertFalse(h.canUndo)
        assertEquals("a", h.redo()?.body)
        assertEquals("ab", h.redo()?.body)
        assertFalse(h.canRedo)
    }

    @Test
    fun coalescedTypingCollapsesToOneStepButKeepsTheBaseline() {
        val h = EditHistory()
        h.reset(snap("hello"))
        // A run of keystrokes, all coalesced.
        h.record(snap("hello "), coalesce = true)
        h.record(snap("hello w"), coalesce = true)
        h.record(snap("hello world"), coalesce = true)

        // One undo returns to the pre-typing baseline, not an intermediate char.
        assertEquals("hello", h.undo()?.body)
        assertFalse(h.canUndo)
    }

    @Test
    fun firstCoalescedEditDoesNotOverwriteBaseline() {
        // Regression: a coalesced edit must open a NEW step over a committed entry,
        // so the loaded note's text stays undoable.
        val h = EditHistory()
        h.reset(snap("seed"))
        h.record(snap("seedX"), coalesce = true)
        assertTrue(h.canUndo)
        assertEquals("seed", h.undo()?.body)
    }

    @Test
    fun recordingAfterUndoDiscardsTheRedoBranch() {
        val h = EditHistory()
        h.reset(snap(""))
        h.record(snap("a"), coalesce = false)
        h.record(snap("ab"), coalesce = false)
        h.undo() // back to "a"
        assertTrue(h.canRedo)

        h.record(snap("aZ"), coalesce = false) // new branch
        assertFalse(h.canRedo)
        assertEquals("a", h.undo()?.body)
    }

    @Test
    fun selectionOnlyMoveIsNotASeparateUndoStep() {
        val h = EditHistory()
        h.reset(snap("abc", sel = 3))
        h.record(snap("abc", sel = 0), coalesce = false)
        assertFalse(h.canUndo) // text unchanged -> no new step
    }

    @Test
    fun typingRunAfterADiscreteStepStartsAFreshStep() {
        val h = EditHistory()
        h.reset(snap(""))
        h.record(snap("- "), coalesce = false) // e.g. a toolbar action
        h.record(snap("- a"), coalesce = true) // then typing
        h.record(snap("- ab"), coalesce = true)

        assertEquals("- ", h.undo()?.body)
        assertEquals("", h.undo()?.body)
    }

    @Test
    fun historyIsCappedAtTheLimitDroppingOldest() {
        val h = EditHistory(limit = 3)
        h.reset(snap("0"))
        h.record(snap("1"), coalesce = false)
        h.record(snap("2"), coalesce = false)
        h.record(snap("3"), coalesce = false) // drops "0"

        assertEquals("2", h.undo()?.body)
        assertEquals("1", h.undo()?.body)
        assertFalse(h.canUndo) // "0" was dropped
    }
}
