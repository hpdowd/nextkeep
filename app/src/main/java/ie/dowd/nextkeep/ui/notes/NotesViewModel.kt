package ie.dowd.nextkeep.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ie.dowd.nextkeep.NextKeepApp
import ie.dowd.nextkeep.data.AccountStore
import ie.dowd.nextkeep.data.NotesRepository
import ie.dowd.nextkeep.data.local.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
    val syncing: Boolean = false,
    val syncError: String? = null,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && pinned.isEmpty() && others.isEmpty()
}

class NotesViewModel(
    private val repository: NotesRepository,
    private val accountStore: AccountStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val syncing = MutableStateFlow(false)
    private val syncError = MutableStateFlow<String?>(null)

    val accountName = accountStore.account
        .map { it?.username.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val uiState = combine(
        repository.notes, query, selectedCategory, syncing, syncError,
    ) { notes, query, category, syncing, syncError ->
        val filtered = notes.filter { note ->
            val matchesQuery = query.isBlank() ||
                note.title.contains(query, ignoreCase = true) ||
                note.body.contains(query, ignoreCase = true)
            val matchesCategory = category == null || note.category == category
            matchesQuery && matchesCategory
        }
        NotesUiState(
            pinned = filtered.filter { it.favorite },
            others = filtered.filterNot { it.favorite },
            categories = notes.map { it.category }.filter { it.isNotBlank() }.distinct().sorted(),
            selectedCategory = category,
            query = query,
            syncing = syncing,
            syncError = syncError,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

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
                )
            }
        }
    }
}
