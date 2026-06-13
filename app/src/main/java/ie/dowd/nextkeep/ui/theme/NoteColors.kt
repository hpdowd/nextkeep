package ie.dowd.nextkeep.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Google Keep's note pastels (light) and their dark-theme counterparts.
private val LightPastels = listOf(
    Color(0xFFFAAFA8), Color(0xFFF39F76), Color(0xFFFFF8B8), Color(0xFFE2F6D3),
    Color(0xFFB4DDD3), Color(0xFFD4E4ED), Color(0xFFAECCDC), Color(0xFFD3BFDB),
    Color(0xFFF6E2DD), Color(0xFFE9E3D4),
)

private val DarkPastels = listOf(
    Color(0xFF77172E), Color(0xFF692B17), Color(0xFF7C4A03), Color(0xFF264D3B),
    Color(0xFF0C625D), Color(0xFF256377), Color(0xFF284255), Color(0xFF472E5B),
    Color(0xFF6C394F), Color(0xFF4B443A),
)

/**
 * Nextcloud notes have no color attribute, so tint cards by category instead —
 * stable per category, Keep-style. Uncategorized notes return null (plain card).
 */
fun categoryColor(category: String, darkTheme: Boolean): Color? {
    if (category.isBlank()) return null
    val palette = if (darkTheme) DarkPastels else LightPastels
    return palette[abs(category.hashCode()) % palette.size]
}
