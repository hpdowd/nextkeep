package ie.dowd.nextkeep.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ie.dowd.nextkeep.NextKeepApp
import ie.dowd.nextkeep.data.MAX_NOTE_FONT_SCALE
import ie.dowd.nextkeep.data.MIN_NOTE_FONT_SCALE
import ie.dowd.nextkeep.data.NotesRepository
import ie.dowd.nextkeep.data.SettingsStore
import ie.dowd.nextkeep.markdown.MarkdownEditing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EditorViewModel(
    private val repository: NotesRepository,
    private val settingsStore: SettingsStore,
    private val appScope: CoroutineScope,
    localId: Long,
) : ViewModel() {

    var title by mutableStateOf("")
        private set
    var body by mutableStateOf(TextFieldValue(""))
        private set
    var category by mutableStateOf("")
        private set
    var favorite by mutableStateOf(false)
        private set
    var modified by mutableStateOf(0L)
        private set

    /** Pinch-to-zoom reading scale for note text, relative to the app font size. */
    var noteFontScale by mutableStateOf(1f)
        private set

    // Undo/redo over the note's text (title + body); favorite/category are excluded.
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val history = EditHistory()
    private var lastEditAt = 0L
    private var lastEditTag = ""

    private var currentLocalId: Long? = localId.takeIf { it > 0 }
    private var loaded = false
    private var deleted = false
    private var saveJob: Job? = null
    private var zoomJob: Job? = null

    init {
        // The zoom level is an app-wide preference; seed it from the stored value.
        viewModelScope.launch { noteFontScale = settingsStore.settings.first().noteFontScale }
        viewModelScope.launch {
            currentLocalId?.let { id ->
                repository.getNote(id)?.let { note ->
                    title = note.title
                    // Place the caret at the end so editing (and shortcut-seeded
                    // checklists) continue from the existing text rather than its start.
                    body = TextFieldValue(note.body, TextRange(note.body.length))
                    category = note.category
                    favorite = note.favorite
                    modified = note.modified
                }
            }
            loaded = true
            history.reset(snapshot())
            refreshHistoryFlags()
        }
    }

    /** Multiply the reading scale by a pinch [factor] (1.0 = no change), clamped. */
    fun onZoom(factor: Float) {
        val next = (noteFontScale * factor).coerceIn(MIN_NOTE_FONT_SCALE, MAX_NOTE_FONT_SCALE)
        if (next == noteFontScale) return
        noteFontScale = next
        // Live value updates every gesture frame; throttle the DataStore write.
        zoomJob?.cancel()
        zoomJob = viewModelScope.launch {
            delay(300)
            settingsStore.setNoteFontScale(next)
        }
    }

    fun onTitleChange(value: String) {
        title = value
        recordHistory(coalesce = typingCoalesce("title"))
        scheduleSave()
    }

    fun onBodyChange(value: TextFieldValue) {
        // Detect a single '\n' just inserted (by diffing, not by trusting the
        // reported selection) and auto-continue the list.
        val caret = newlineInsertedAt(body.text, value.text)
        if (caret != null) {
            MarkdownEditing.onNewline(value.text, caret)?.let { edit ->
                body = TextFieldValue(edit.text, TextRange(edit.selStart, edit.selEnd))
                recordHistory(coalesce = false) // a list continuation is its own step
                scheduleSave()
                return
            }
        }
        body = value
        recordHistory(coalesce = typingCoalesce("body"))
        scheduleSave()
    }

    /** If [new] is [old] with a single '\n' inserted, return the caret after it. */
    private fun newlineInsertedAt(old: String, new: String): Int? {
        if (new.length != old.length + 1) return null
        var i = 0
        while (i < old.length && old[i] == new[i]) i++
        return if (new[i] == '\n' && old.substring(i) == new.substring(i + 1)) i + 1 else null
    }

    /** Toggle the [taskIndex]-th checkbox in the body (tapped in the preview). */
    fun toggleTask(taskIndex: Int) {
        val newText = MarkdownEditing.toggleTaskAt(body.text, taskIndex)
        if (newText == body.text) return
        body = body.copy(text = newText)
        recordHistory(coalesce = false)
        scheduleSave()
    }

    // Formatting-toolbar actions; each rewrites the body's text + selection.
    fun cycleHeading() = applyMarkdown(MarkdownEditing::cycleHeading)
    fun toggleBullet() = applyMarkdown(MarkdownEditing::toggleBullet)
    fun toggleNumbered() = applyMarkdown(MarkdownEditing::toggleNumberedList)
    fun toggleCheckbox() = applyMarkdown(MarkdownEditing::toggleCheckbox)
    fun toggleQuote() = applyMarkdown(MarkdownEditing::toggleQuote)
    fun indent() = applyMarkdown(MarkdownEditing::indent)
    fun outdent() = applyMarkdown(MarkdownEditing::outdent)
    fun bold() = applyMarkdown(MarkdownEditing::bold)
    fun italic() = applyMarkdown(MarkdownEditing::italic)

    private fun applyMarkdown(op: (String, Int, Int) -> MarkdownEditing.Edit) {
        val current = body
        val result = op(current.text, current.selection.start, current.selection.end)
        body = TextFieldValue(result.text, TextRange(result.selStart, result.selEnd))
        recordHistory(coalesce = false) // each formatting action is a discrete step
        scheduleSave()
    }

    /** Revert to the previous text state (no-op if there is nothing to undo). */
    fun undo() = history.undo()?.let(::applySnapshot)

    /** Re-apply a previously undone text state (no-op if there is nothing to redo). */
    fun redo() = history.redo()?.let(::applySnapshot)

    private fun applySnapshot(s: EditSnapshot) {
        title = s.title
        val len = s.body.length
        body = TextFieldValue(
            s.body,
            TextRange(s.selStart.coerceIn(0, len), s.selEnd.coerceIn(0, len)),
        )
        lastEditTag = "" // a following keystroke shouldn't merge into the restored state
        refreshHistoryFlags()
        scheduleSave()
    }

    private fun snapshot() =
        EditSnapshot(title, body.text, body.selection.start, body.selection.end)

    private fun recordHistory(coalesce: Boolean) {
        if (!loaded) return // ignore programmatic state set before the baseline exists
        history.record(snapshot(), coalesce)
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    /** Whether an edit tagged [tag] continues a recent run of the same kind. */
    private fun typingCoalesce(tag: String): Boolean {
        val nowMs = System.currentTimeMillis()
        val coalesce = tag == lastEditTag && nowMs - lastEditAt < COALESCE_WINDOW_MS
        lastEditAt = nowMs
        lastEditTag = tag
        return coalesce
    }

    fun onCategoryChange(value: String) {
        category = value.trim()
        scheduleSave()
    }

    fun toggleFavorite() {
        favorite = !favorite
        scheduleSave()
    }

    fun delete(onDeleted: (Long?) -> Unit) {
        deleted = true
        saveJob?.cancel()
        val id = currentLocalId
        // Tombstone only; the list shows an Undo snackbar and the delete is
        // pushed to the server on the next sync.
        appScope.launch { if (id != null) repository.deleteNote(id) }
        onDeleted(id)
    }

    /** Called when the editor leaves the screen: save immediately and push. */
    fun flushAndSync() {
        saveJob?.cancel()
        // Persist the zoom regardless of the note's fate (it's a global setting).
        zoomJob?.cancel()
        val scale = noteFontScale
        appScope.launch { settingsStore.setNoteFontScale(scale) }
        if (deleted) return
        appScope.launch {
            if (title.isBlank() && body.text.isBlank()) {
                // Discard empty notes, Keep-style, instead of saving a blank one.
                currentLocalId?.let { repository.deleteNote(it) }
            } else {
                persist()
            }
            repository.sync()
        }
    }

    private fun scheduleSave() {
        modified = System.currentTimeMillis() / 1000
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            persist()
        }
    }

    private suspend fun persist() {
        if (!loaded || deleted) return
        val id = currentLocalId
        if (id == null) {
            if (title.isBlank() && body.text.isBlank()) return
            currentLocalId = repository.createNote(title, body.text, category, favorite)
        } else {
            repository.updateNote(id, title, body.text, category, favorite)
        }
    }

    companion object {
        /** Keystrokes of the same kind within this window collapse into one undo step. */
        private const val COALESCE_WINDOW_MS = 700L

        fun factory(localId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NextKeepApp
                EditorViewModel(
                    app.container.repository,
                    app.container.settingsStore,
                    app.container.appScope,
                    localId,
                )
            }
        }
    }
}
