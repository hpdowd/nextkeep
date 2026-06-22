package ie.dowd.nextkeep.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import ie.dowd.nextkeep.data.ThemeMode

/** Multiplier for markdown heading sizes, set from the Heading size setting and
 *  read by the markdown renderer so headings scale independently of body text. */
val LocalHeadingScale = staticCompositionLocalOf { 1f }

// Fallback palette for pre-Android 12 devices, modeled on Google Keep:
// white surfaces, Google blue accents, subtle grey outlines.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondaryContainer = Color(0xFFFEEFC3),
    onSecondaryContainer = Color(0xFF574500),
    background = Color.White,
    onBackground = Color(0xFF1F1F1F),
    surface = Color.White,
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFF747775),
    outlineVariant = Color(0xFFE0E0E0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondaryContainer = Color(0xFF41331C),
    onSecondaryContainer = Color(0xFFFEEFC3),
    background = Color(0xFF202124),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF202124),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF303134),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = Color(0xFF8E918F),
    outlineVariant = Color(0xFF444746),
)

@Composable
fun NextKeepTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontScale: Float = 1f,
    headingScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    val context = LocalContext.current

    var colorScheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkColors
        else -> LightColors
    }

    if (themeMode == ThemeMode.BLACK) {
        // AMOLED: true-black backgrounds, near-black surfaces.
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1A1A1A),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography().scaledBy(fontScale),
    ) {
        CompositionLocalProvider(LocalHeadingScale provides headingScale, content = content)
    }
}

/**
 * Scales every text style's size and line height. Drives the app-wide Font size
 * setting, and is reused by the editor's pinch-to-zoom (nested over the already
 * scaled typography, so the two compound).
 */
internal fun Typography.scaledBy(factor: Float): Typography {
    if (factor == 1f) return this
    fun TextStyle.scaled(): TextStyle = copy(
        fontSize = (fontSize.value * factor).sp,
        lineHeight = if (lineHeight.isSpecified) (lineHeight.value * factor).sp else lineHeight,
    )
    return copy(
        displayLarge = displayLarge.scaled(),
        displayMedium = displayMedium.scaled(),
        displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(),
        headlineMedium = headlineMedium.scaled(),
        headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(),
        titleMedium = titleMedium.scaled(),
        titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(),
        bodyMedium = bodyMedium.scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(),
        labelMedium = labelMedium.scaled(),
        labelSmall = labelSmall.scaled(),
    )
}
