package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.BuildConfig
import wojtoteka.nightdrive.domain.Language
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.components.ConfirmDialog
import wojtoteka.nightdrive.ui.components.Overline
import wojtoteka.nightdrive.ui.components.Panel
import wojtoteka.nightdrive.ui.components.Rule
import wojtoteka.nightdrive.ui.components.ScreenHeader
import wojtoteka.nightdrive.ui.components.SecondaryButton
import wojtoteka.nightdrive.ui.components.SegmentedControl
import wojtoteka.nightdrive.ui.components.SettingRow
import wojtoteka.nightdrive.ui.components.Toggle
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.Space

@Composable
fun SettingsScreen(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState
    var showReset by remember { mutableStateOf(false) }
    BackHandler { vm.goTo(Destination.MENU) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.gutter, vertical = 14.dp),
    ) {
        ScreenHeader(title = s["settings"], onBack = { vm.goTo(Destination.MENU) }, backLabel = s["back"])
        Spacer(Modifier.height(Space.section))

        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState()),
        ) {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    SettingRow(s["music"], null) { Toggle(p.musicEnabled) { vm.setMusicEnabled(it) } }
                    Rule()
                    SettingRow(s["sound_effects"], null) { Toggle(p.sfxEnabled) { vm.setSfxEnabled(it) } }
                    Rule()
                    SettingRow(s["haptics"], null) { Toggle(p.hapticsEnabled) { vm.setHapticsEnabled(it) } }
                    Rule()
                    SettingRow(s["language"], null) {
                        val langs = listOf(Language.SYSTEM, Language.EN, Language.PL)
                        SegmentedControl(
                            options = listOf(s["lang_system"], "EN", "PL"),
                            selectedIndex = langs.indexOf(p.language).coerceAtLeast(0),
                            onSelect = { vm.changeLanguage(langs[it]) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                SettingRow(s["reset_progress"], s["reset_progress_desc"]) {
                    SecondaryButton(s["reset_progress"], { showReset = true }, tone = NDColors.Danger)
                }
            }
            Spacer(Modifier.height(Space.section))
            Overline("Night Drive ${BuildConfig.VERSION_NAME}", Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(4.dp))
            Text(
                s["credits"],
                style = NDType.Caption,
                color = NDColors.TextFaint,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showReset) {
        ConfirmDialog(
            title = s["reset_confirm_title"],
            message = s["reset_confirm_msg"],
            confirmText = s["yes"],
            dismissText = s["cancel"],
            onConfirm = { vm.resetProgress(); showReset = false },
            onDismiss = { showReset = false },
        )
    }
}
