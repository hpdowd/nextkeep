package ie.dowd.nextkeep.ui.editor

/** A snapshot of the editor's text state (title + body + selection) for undo/redo. */
data class EditSnapshot(
    val title: String,
    val body: String,
    val selStart: Int,
    val selEnd: Int,
)

/**
 * A linear undo/redo history of editor [EditSnapshot]s. Pure — no Android/Compose
 * types — so the branching/coalescing logic is unit-testable (see EditHistoryTest).
 *
 * The caller decides when consecutive edits should collapse into one undo step via
 * the [record] `coalesce` flag (e.g. a run of keystrokes is one step, a toolbar
 * action is its own step). A committed step is never coalesced into, so the state
 * before a typing run is always recoverable.
 */
class EditHistory(private val limit: Int = 200) {
    private val stack = ArrayDeque<EditSnapshot>()
    private var index = -1
    // Whether the current top entry is an open typing run that may be merged into.
    private var coalescing = false

    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index < stack.size - 1

    /** Seed the baseline state, clearing any existing history. */
    fun reset(initial: EditSnapshot) {
        stack.clear()
        stack.addLast(initial)
        index = 0
        coalescing = false
    }

    /**
     * Record [snapshot] as the new current state. A pure selection move (same text)
     * updates the current entry in place without adding a step. When [coalesce] is
     * true and the current entry is an open typing run, it is replaced in place;
     * otherwise the snapshot becomes a new step, discarding any redo branch.
     */
    fun record(snapshot: EditSnapshot, coalesce: Boolean) {
        if (index < 0) {
            reset(snapshot)
            return
        }
        val current = stack[index]
        if (current.title == snapshot.title && current.body == snapshot.body) {
            stack[index] = snapshot // selection-only move; not a separate undo step
            return
        }
        if (coalesce && coalescing) {
            stack[index] = snapshot
            truncateRedo()
            return
        }
        truncateRedo()
        stack.addLast(snapshot)
        index++
        coalescing = coalesce
        while (stack.size > limit) {
            stack.removeFirst()
            index--
        }
    }

    fun undo(): EditSnapshot? {
        if (!canUndo) return null
        index--
        coalescing = false
        return stack[index]
    }

    fun redo(): EditSnapshot? {
        if (!canRedo) return null
        index++
        coalescing = false
        return stack[index]
    }

    private fun truncateRedo() {
        while (stack.size > index + 1) stack.removeLast()
    }
}
