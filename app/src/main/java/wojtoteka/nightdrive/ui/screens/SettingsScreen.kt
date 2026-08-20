package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.BuildConfig
import wojtoteka.nightdrive.domain.Language
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.components.ConfirmDialog
import wojtoteka.nightdrive.ui.components.GhostButton
import wojtoteka.nightdrive.ui.components.NeonSwitch
import wojtoteka.nightdrive.ui.components.ScreenHeader
import wojtoteka.nightdrive.ui.components.SegmentedControl
import wojtoteka.nightdrive.ui.components.SettingRow
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.Radius

@Composable
fun SettingsScreen(vm: GameViewModel, ads: wojtoteka.nightdrive.ads.AdsController) {
    val s = LocalStrings.current
    val p = vm.profileState
    var showReset by remember { mutableStateOf(false) }
    BackHandler { vm.goTo(Destination.MENU) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        ScreenHeader(title = s["settings"], onBack = { vm.goTo(Destination.MENU) }, accent = NDColors.Cyan)
        Spacer(Modifier.height(14.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingRow(s["music"], null) {
                NeonSwitch(p.musicEnabled, { vm.setMusicEnabled(it) }, NDColors.Cyan)
            }
            SettingRow(s["sound_effects"], null) {
                NeonSwitch(p.sfxEnabled, { vm.setSfxEnabled(it) }, NDColors.Magenta)
            }
            SettingRow(s["haptics"], null) {
                NeonSwitch(p.hapticsEnabled, { vm.setHapticsEnabled(it) }, NDColors.Violet)
            }
            SettingRow(s["language"], null) {
                val langs = listOf(Language.SYSTEM, Language.EN, Language.PL)
                val labels = listOf(s["lang_system"], "EN", "PL")
                SegmentedControl(
                    options = labels,
                    selectedIndex = langs.indexOf(p.language).coerceAtLeast(0),
                    onSelect = { vm.changeLanguage(langs[it]) },
                )
            }
            if (ads.privacyOptionsRequired) {
                SettingRow(s["ad_privacy"], s["ad_privacy_desc"]) {
                    GhostButton(s["ad_privacy"], { ads.showPrivacyOptions() }, color = NDColors.TextSecondary)
                }
            }
            SettingRow(s["reset_progress"], s["reset_progress_desc"]) {
                GhostButton(s["reset_progress"], { showReset = true }, color = NDColors.Red, icon = Icons.Filled.Refresh)
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(Radius.pill)
                    .background(Color(0xF70D0F22))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    "${s["version"]} ${BuildConfig.VERSION_NAME}  •  ${s["credits"]}",
                    style = NDType.Caption,
                    color = NDColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
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
