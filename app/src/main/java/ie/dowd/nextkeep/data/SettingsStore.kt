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

/** Independent scale for markdown headings, applied on top of [FontSize]. */
enum class HeadingSize(val scale: Float) { SMALL(0.85f), MEDIUM(1.0f), LARGE(1.2f) }

/** How much of a note's body shows on a list card: block count + lines per block. */
enum class PreviewLength(val maxBlocks: Int, val lines: Int) {
    SHORT(2, 2), MEDIUM(4, 3), LONG(8, 5)
}

/**
 * Bounds for the pinch-to-zoom note reading scale. This is *relative* to the
 * app-wide [FontSize]: 1.0 means "same size as the rest of the app", and the two
 * multiply (so global Large × a zoomed note compound). Kept modest so the product
 * stays readable rather than absurd.
 */
const val MIN_NOTE_FONT_SCALE = 0.7f
const val MAX_NOTE_FONT_SCALE = 2.2f

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: FontSize = FontSize.MEDIUM,
    val headingSize: HeadingSize = HeadingSize.MEDIUM,
    val previewLength: PreviewLength = PreviewLength.MEDIUM,
    val noteFontScale: Float = 1f,
    val gridColumns: Int = 2,
    val sortOrder: SortOrder = SortOrder.MODIFIED,
    val appLock: Boolean = false,
)

class SettingsStore(private val context: Context) {

    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyFont = stringPreferencesKey("font_size")
    private val keyHeading = stringPreferencesKey("heading_size")
    private val keyPreview = stringPreferencesKey("preview_length")
    private val keyNoteFontScale = floatPreferencesKey("note_font_scale")
    private val keyColumns = intPreferencesKey("grid_columns")
    private val keySort = stringPreferencesKey("sort_order")
    private val keyAppLock = booleanPreferencesKey("app_lock")

    val settings: Flow<Settings> = context.settingsDataStore.data.map { p ->
        Settings(
            themeMode = p[keyTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            fontSize = p[keyFont]?.let { runCatching { FontSize.valueOf(it) }.getOrNull() } ?: FontSize.MEDIUM,
            headingSize = p[keyHeading]?.let { runCatching { HeadingSize.valueOf(it) }.getOrNull() } ?: HeadingSize.MEDIUM,
            previewLength = p[keyPreview]?.let { runCatching { PreviewLength.valueOf(it) }.getOrNull() } ?: PreviewLength.MEDIUM,
            noteFontScale = (p[keyNoteFontScale] ?: 1f).coerceIn(MIN_NOTE_FONT_SCALE, MAX_NOTE_FONT_SCALE),
            gridColumns = p[keyColumns] ?: 2,
            sortOrder = p[keySort]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() } ?: SortOrder.MODIFIED,
            appLock = p[keyAppLock] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.settingsDataStore.edit { it[keyTheme] = mode.name }

    suspend fun setFontSize(size: FontSize) =
        context.settingsDataStore.edit { it[keyFont] = size.name }

    suspend fun setHeadingSize(size: HeadingSize) =
        context.settingsDataStore.edit { it[keyHeading] = size.name }

    suspend fun setPreviewLength(length: PreviewLength) =
        context.settingsDataStore.edit { it[keyPreview] = length.name }

    suspend fun setNoteFontScale(scale: Float) =
        context.settingsDataStore.edit {
            it[keyNoteFontScale] = scale.coerceIn(MIN_NOTE_FONT_SCALE, MAX_NOTE_FONT_SCALE)
        }

    suspend fun setGridColumns(columns: Int) =
        context.settingsDataStore.edit { it[keyColumns] = columns }

    suspend fun setSortOrder(order: SortOrder) =
        context.settingsDataStore.edit { it[keySort] = order.name }

    suspend fun setAppLock(enabled: Boolean) =
        context.settingsDataStore.edit { it[keyAppLock] = enabled }
}
