package ie.dowd.nextkeep.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ie.dowd.nextkeep.NextKeepApp
import ie.dowd.nextkeep.data.AccountStore
import ie.dowd.nextkeep.data.FontSize
import ie.dowd.nextkeep.data.NotesRepository
import ie.dowd.nextkeep.data.Settings
import ie.dowd.nextkeep.data.SettingsStore
import ie.dowd.nextkeep.data.SortOrder
import ie.dowd.nextkeep.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val accountStore: AccountStore,
    private val repository: NotesRepository,
) : ViewModel() {

    val settings = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val accountName = accountStore.account
        .map { it?.username.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val serverUrl = accountStore.account
        .map { it?.baseUrl.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setFontSize(size: FontSize) = viewModelScope.launch { settingsStore.setFontSize(size) }
    fun setColumns(columns: Int) = viewModelScope.launch { settingsStore.setGridColumns(columns) }
    fun setSortOrder(order: SortOrder) = viewModelScope.launch { settingsStore.setSortOrder(order) }
    fun setAppLock(enabled: Boolean) = viewModelScope.launch { settingsStore.setAppLock(enabled) }

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
                SettingsViewModel(
                    app.container.settingsStore,
                    app.container.accountStore,
                    app.container.repository,
                )
            }
        }
    }
}
