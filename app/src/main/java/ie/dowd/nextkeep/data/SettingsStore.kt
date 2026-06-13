package ie.dowd.nextkeep.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

enum class SortOrder { MODIFIED, TITLE }

/** Font scale presets applied to the whole app's typography. */
enum class FontSize(val scale: Float) { XSMALL(0.72f), SMALL(0.85f), MEDIUM(1.0f), LARGE(1.18f) }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: FontSize = FontSize.MEDIUM,
    val gridColumns: Int = 2,
    val sortOrder: SortOrder = SortOrder.MODIFIED,
    val appLock: Boolean = false,
)

class SettingsStore(private val context: Context) {

    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyFont = stringPreferencesKey("font_size")
    private val keyColumns = intPreferencesKey("grid_columns")
    private val keySort = stringPreferencesKey("sort_order")
    private val keyAppLock = booleanPreferencesKey("app_lock")

    val settings: Flow<Settings> = context.settingsDataStore.data.map { p ->
        Settings(
            themeMode = p[keyTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            fontSize = p[keyFont]?.let { runCatching { FontSize.valueOf(it) }.getOrNull() } ?: FontSize.MEDIUM,
            gridColumns = p[keyColumns] ?: 2,
            sortOrder = p[keySort]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.MODIFIED,
            appLock = p[keyAppLock] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.settingsDataStore.edit { it[keyTheme] = mode.name }

    suspend fun setFontSize(size: FontSize) =
        context.settingsDataStore.edit { it[keyFont] = size.name }

    suspend fun setGridColumns(columns: Int) =
        context.settingsDataStore.edit { it[keyColumns] = columns }

    suspend fun setSortOrder(order: SortOrder) =
        context.settingsDataStore.edit { it[keySort] = order.name }

    suspend fun setAppLock(enabled: Boolean) =
        context.settingsDataStore.edit { it[keyAppLock] = enabled }
}
