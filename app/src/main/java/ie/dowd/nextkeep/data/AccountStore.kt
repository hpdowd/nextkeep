package ie.dowd.nextkeep.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "account")

data class Account(
    val baseUrl: String,
    val username: String,
    val appPassword: String,
)

class AccountStore(private val context: Context) {

    private val keyUrl = stringPreferencesKey("base_url")
    private val keyUser = stringPreferencesKey("username")
    private val keyPassword = stringPreferencesKey("app_password")

    val account: Flow<Account?> = context.dataStore.data.map { prefs ->
        val url = prefs[keyUrl]
        val user = prefs[keyUser]
        val pass = prefs[keyPassword]
        if (url.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank()) null
        else Account(url, user, pass)
    }

    suspend fun save(account: Account) {
        context.dataStore.edit { prefs ->
            prefs[keyUrl] = account.baseUrl
            prefs[keyUser] = account.username
            prefs[keyPassword] = account.appPassword
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
