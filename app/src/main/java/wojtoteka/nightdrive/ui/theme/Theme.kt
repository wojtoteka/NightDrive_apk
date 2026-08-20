package wojtoteka.nightdrive.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import wojtoteka.nightdrive.domain.Language
import wojtoteka.nightdrive.domain.Strings

val LocalStrings = staticCompositionLocalOf { Strings(Language.EN) }
val LocalPalette = staticCompositionLocalOf { NDPalette() }

private val NDDarkScheme = darkColorScheme(
    primary = NDColors.Cyan,
    onPrimary = NDColors.Background,
    secondary = NDColors.Magenta,
    onSecondary = NDColors.TextPrimary,
    tertiary = NDColors.Violet,
    background = NDColors.Background,
    onBackground = NDColors.TextPrimary,
    surface = NDColors.Surface,
    onSurface = NDColors.TextPrimary,
    surfaceVariant = NDColors.SurfaceHi,
    onSurfaceVariant = NDColors.TextSecondary,
    error = NDColors.Red,
    outline = NDColors.Outline,
)

@Composable
fun NightDriveTheme(
    language: Language = Language.SYSTEM,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStrings provides Strings(language),
        LocalPalette provides NDPalette(),
    ) {
        MaterialTheme(colorScheme = NDDarkScheme, content = content)
    }
}
