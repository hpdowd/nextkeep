package ie.dowd.nextkeep.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ie.dowd.nextkeep.NextKeepApp
import ie.dowd.nextkeep.data.Account
import ie.dowd.nextkeep.data.AccountStore
import ie.dowd.nextkeep.data.NotesRepository
import ie.dowd.nextkeep.data.remote.ApiClient
import ie.dowd.nextkeep.qr.NextcloudLoginUri
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class LoginViewModel(
    private val accountStore: AccountStore,
    private val repository: NotesRepository,
) : ViewModel() {

    var serverUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var appPassword by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * Applies a scanned QR code. A full Nextcloud login code fills every field
     * and connects immediately; a bare server URL just fills the address.
     */
    fun applyScannedCredential(raw: String, onSuccess: () -> Unit) {
        val parsed = NextcloudLoginUri.parse(raw)
        if (parsed != null) {
            serverUrl = parsed.server
            username = parsed.user
            appPassword = parsed.password
            error = null
            login(onSuccess)
            return
        }
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            serverUrl = trimmed
            error = "Scanned a server address — now enter your username and app password."
        } else {
            error = "That QR code isn't a Nextcloud login code."
        }
    }

    fun login(onSuccess: () -> Unit) {
        if (loading) return
        if (serverUrl.isBlank() || username.isBlank() || appPassword.isBlank()) {
            error = "Fill in all fields"
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val url = ApiClient.normalizeBaseUrl(serverUrl)
                // Validates the server URL, credentials, and that the Notes app
                // is installed in one call.
                val response = ApiClient.create(url, username.trim(), appPassword).getNotes()
                if (!response.isSuccessful) {
                    error = when (response.code()) {
                        401 -> "Wrong username or app password"
                        404 -> "Notes app not found on this server — is it installed?"
                        else -> "Server error (HTTP ${response.code()})"
                    }
                    return@launch
                }
                accountStore.save(Account(url, username.trim(), appPassword))
                repository.sync()
                onSuccess()
            } catch (e: HttpException) {
                error = when (e.code()) {
                    401 -> "Wrong username or app password"
                    404 -> "Notes app not found on this server — is it installed?"
                    else -> "Server error (HTTP ${e.code()})"
                }
            } catch (e: IOException) {
                error = "Can't reach the server. Check the address and your connection."
            } catch (e: Exception) {
                error = "Unexpected error: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as NextKeepApp
                LoginViewModel(app.container.accountStore, app.container.repository)
            }
        }
    }
}
