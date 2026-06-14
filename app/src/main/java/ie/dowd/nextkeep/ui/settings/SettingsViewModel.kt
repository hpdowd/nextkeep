package ie.dowd.nextkeep.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ie.dowd.nextkeep.NextKeepApp
import ie.dowd.nextkeep.data.AccountStore
import ie.dowd.nextkeep.data.FontSize
import ie.dowd.nextkeep.data.HeadingSize
import ie.dowd.nextkeep.data.NotesRepository
import ie.dowd.nextkeep.data.PreviewLength
import ie.dowd.nextkeep.data.Settings
import ie.dowd.nextkeep.data.SettingsStore
import ie.dowd.nextkeep.data.SortOrder
import ie.dowd.nextkeep.data.ThemeMode
import ie.dowd.nextkeep.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** UI state for the "Check for updates" flow in Settings. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Updater.Release) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Downloaded(val release: Updater.Release, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val accountStore: AccountStore,
    private val repository: NotesRepository,
    private val updater: Updater,
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
    fun setHeadingSize(size: HeadingSize) = viewModelScope.launch { settingsStore.setHeadingSize(size) }
    fun setPreviewLength(length: PreviewLength) = viewModelScope.launch { settingsStore.setPreviewLength(length) }
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

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    fun checkForUpdate() {
        // Ignore taps while a check or download is already running.
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = runCatching { updater.fetchLatest() }.fold(
                onSuccess = { release ->
                    when {
                        release == null -> UpdateState.Failed("Couldn't reach the update server")
                        updater.isUpdateAvailable(release) -> UpdateState.Available(release)
                        else -> UpdateState.UpToDate
                    }
                },
                onFailure = { UpdateState.Failed(it.message ?: "Update check failed") },
            )
        }
    }

    fun downloadUpdate() {
        val release = (_update.value as? UpdateState.Available)?.release ?: return
        _update.value = UpdateState.Downloading(0f)
        viewModelScope.launch {
            _update.value = runCatching {
                updater.download(release) { p -> _update.value = UpdateState.Downloading(p) }
            }.fold(
                onSuccess = { UpdateState.Downloaded(release, it) },
                onFailure = { UpdateState.Failed(it.message ?: "Download failed") },
            )
        }
    }

    fun canInstall(): Boolean = updater.canInstall()

    fun unknownSourcesIntent() = updater.unknownSourcesSettingsIntent()

    /** Install the downloaded APK; no-ops unless a download has finished. */
    fun install() {
        val apk = (_update.value as? UpdateState.Downloaded)?.apk ?: return
        updater.install(apk)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NextKeepApp
                SettingsViewModel(
                    app.container.settingsStore,
                    app.container.accountStore,
                    app.container.repository,
                    app.container.updater,
                )
            }
        }
    }
}
