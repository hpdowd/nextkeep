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
import ie.dowd.nextkeep.data.NotesRepository
import ie.dowd.nextkeep.markdown.MarkdownEditing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EditorViewModel(
    private val repository: NotesRepository,
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

    private var currentLocalId: Long? = localId.takeIf { it > 0 }
    private var loaded = false
    private var deleted = false
    private var saveJob: Job? = null

    init {
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

    fun onTitleChange(value: String) {
        title = value
        scheduleSave()
    }

    fun onBodyChange(value: TextFieldValue) {
        body = value
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

    fun delete(onDeleted: () -> Unit) {
        deleted = true
        saveJob?.cancel()
        val id = currentLocalId
        appScope.launch {
            if (id != null) {
                repository.deleteNote(id)
                repository.sync()
            }
        }
        onDeleted()
    }

    /** Called when the editor leaves the screen: save immediately and push. */
    fun flushAndSync() {
        saveJob?.cancel()
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
                EditorViewModel(app.container.repository, app.container.appScope, localId)
            }
        }
    }
}
