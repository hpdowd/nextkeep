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
                    body = TextFieldValue(note.body)
                    category = note.category
                    favorite = note.favorite
                    modified = note.modified
                }
            }
            loaded = true
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
        scheduleSave()
    }

    fun onBodyChange(value: TextFieldValue) {
        // Detect a single '\n' just inserted (by diffing, not by trusting the
        // reported selection) and auto-continue the list.
        val caret = newlineInsertedAt(body.text, value.text)
        if (caret != null) {
            MarkdownEditing.onNewline(value.text, caret)?.let { edit ->
                body = TextFieldValue(edit.text, TextRange(edit.selStart, edit.selEnd))
                scheduleSave()
                return
            }
        }
        body = value
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
        scheduleSave()
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
