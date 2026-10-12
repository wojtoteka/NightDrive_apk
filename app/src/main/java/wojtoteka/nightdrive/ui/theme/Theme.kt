package wojtoteka.nightdrive.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import wojtoteka.nightdrive.domain.Language
import wojtoteka.nightdrive.domain.Strings

val LocalStrings = staticCompositionLocalOf { Strings(Language.EN) }

private val NDDarkScheme = darkColorScheme(
    primary = NDColors.Accent,
    onPrimary = NDColors.OnAccent,
    secondary = NDColors.Text,
    onSecondary = NDColors.Ink,
    background = NDColors.Ink,
    onBackground = NDColors.Text,
    surface = NDColors.PanelSolid,
    onSurface = NDColors.Text,
    surfaceVariant = NDColors.PanelRaised,
    onSurfaceVariant = NDColors.TextDim,
    error = NDColors.Danger,
    outline = NDColors.LineStrong,
)

@Composable
fun NightDriveTheme(
    language: Language = Language.SYSTEM,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStrings provides Strings(language)) {
        MaterialTheme(colorScheme = NDDarkScheme, content = content)
    }
}
