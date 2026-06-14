package ie.dowd.nextkeep.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ie.dowd.nextkeep.NextKeepApp
import ie.dowd.nextkeep.data.AccountStore
import ie.dowd.nextkeep.data.NotesRepository
import ie.dowd.nextkeep.data.PreviewLength
import ie.dowd.nextkeep.data.SettingsStore
import ie.dowd.nextkeep.data.SortOrder
import ie.dowd.nextkeep.data.local.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NotesUiState(
    val pinned: List<NoteEntity> = emptyList(),
    val others: List<NoteEntity> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val query: String = "",
    val columns: Int = 2,
    val previewLength: PreviewLength = PreviewLength.MEDIUM,
    val syncing: Boolean = false,
    val syncError: String? = null,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && pinned.isEmpty() && others.isEmpty()
}

private data class Filters(
    val query: String,
    val category: String?,
    val syncing: Boolean,
    val syncError: String?,
)

class NotesViewModel(
    private val repository: NotesRepository,
    private val accountStore: AccountStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val syncing = MutableStateFlow(false)
    private val syncError = MutableStateFlow<String?>(null)

    val accountName = accountStore.account
        .map { it?.username.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val filters = combine(query, selectedCategory, syncing, syncError, ::Filters)

    val uiState = combine(repository.notes, filters, settingsStore.settings) { notes, f, settings ->
        val filtered = notes.filter { note ->
            val matchesQuery = f.query.isBlank() ||
                note.title.contains(f.query, ignoreCase = true) ||
                note.body.contains(f.query, ignoreCase = true)
            val matchesCategory = f.category == null || note.category == f.category
            matchesQuery && matchesCategory
        }
        val comparator: Comparator<NoteEntity> = when (settings.sortOrder) {
            SortOrder.MODIFIED -> compareByDescending { it.modified }
            SortOrder.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        }
        NotesUiState(
            pinned = filtered.filter { it.favorite }.sortedWith(comparator),
            others = filtered.filterNot { it.favorite }.sortedWith(comparator),
            categories = notes.map { it.category }.filter { it.isNotBlank() }.distinct().sorted(),
            selectedCategory = f.category,
            query = f.query,
            columns = settings.gridColumns,
            previewLength = settings.previewLength,
            syncing = f.syncing,
            syncError = f.syncError,
            loaded = true,
        )
    }
        .flowOn(Dispatchers.Default) // filter + sort off the main thread
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            syncError.value = null
            val result = repository.sync()
            result.exceptionOrNull()?.let {
                syncError.value = "Sync failed — working offline"
            }
            syncing.value = false
        }
    }

    fun dismissSyncError() {
        syncError.value = null
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCategorySelect(category: String?) {
        selectedCategory.value = if (selectedCategory.value == category) null else category
    }

    /** Restore a note that was just deleted (Undo). */
    fun undoDelete(localId: Long) {
        viewModelScope.launch { repository.restoreNote(localId) }
    }

    /** Push the pending delete (after the Undo window elapses). */
    fun confirmDelete() {
        viewModelScope.launch { repository.sync() }
    }

    /** Toggle a checkbox tapped directly on a note card. */
    fun toggleTask(localId: Long, taskIndex: Int) {
        viewModelScope.launch {
            repository.toggleTask(localId, taskIndex)
            repository.sync()
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            accountStore.clear()
            withContext(Dispatchers.IO) { repository.clearLocalData() }
            onLoggedOut()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NextKeepApp
                NotesViewModel(
                    app.container.repository,
                    app.container.accountStore,
                    app.container.settingsStore,
                )
            }
        }
    }
}
