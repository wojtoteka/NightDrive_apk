package wojtoteka.nightdrive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.domain.Achievements
import wojtoteka.nightdrive.domain.Missions
import wojtoteka.nightdrive.domain.Strings
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.MissionSnapshot
import wojtoteka.nightdrive.ui.components.CheckGlyph
import wojtoteka.nightdrive.ui.components.CoinIcon
import wojtoteka.nightdrive.ui.components.CoinReadout
import wojtoteka.nightdrive.ui.components.GlowText
import wojtoteka.nightdrive.ui.components.Meter
import wojtoteka.nightdrive.ui.components.NavRow
import wojtoteka.nightdrive.ui.components.Overline
import wojtoteka.nightdrive.ui.components.Panel
import wojtoteka.nightdrive.ui.components.PrimaryButton
import wojtoteka.nightdrive.ui.components.Readout
import wojtoteka.nightdrive.ui.components.Rule
import wojtoteka.nightdrive.ui.components.SlidersGlyph
import wojtoteka.nightdrive.ui.components.SquareIconButton
import wojtoteka.nightdrive.ui.components.VRule
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.Space

@Composable
fun MenuScreen(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.gutter, vertical = 14.dp),
    ) {
        // Top right: balance + settings
        Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            Panel {
                CoinReadout(p.totalCoins, Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
            }
            Spacer(Modifier.width(8.dp))
            SquareIconButton({ vm.goTo(Destination.SETTINGS) }, contentDescription = s["settings"]) { SlidersGlyph() }
        }

        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // Left: logo, records, missions. Scrolls if a short screen can't fit all three missions.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                GlowText("NIGHT", NDType.Brand, color = NDColors.LogoWhite, glow = NDColors.LogoCyan)
                GlowText("DRIVE", NDType.Brand, color = NDColors.LogoMagenta, glow = NDColors.LogoMagenta)
                Spacer(Modifier.height(6.dp))
                Overline(s["app_tagline"], color = NDColors.TextDim)
                Spacer(Modifier.height(Space.section))
                Column(Modifier.widthIn(max = 340.dp)) {
                    RecordsPanel(vm)
                    Spacer(Modifier.height(8.dp))
                    MissionsPanel(vm)
                }
            }

            Spacer(Modifier.width(28.dp))

            // Right: the one big action, then the rest as a list
            Column(Modifier.width(360.dp)) {
                Spacer(Modifier.height(40.dp))
                PrimaryButton(s["play"], { vm.startGame() }, Modifier.fillMaxWidth(), large = true)
                Spacer(Modifier.height(10.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        NavRow(s["shop"], { vm.goTo(Destination.SHOP) })
                        Rule()
                        NavRow(s["leaderboard"], { vm.goTo(Destination.LEADERBOARD) })
                        Rule()
                        NavRow(
                            s["achievements"], { vm.goTo(Destination.ACHIEVEMENTS) },
                            detail = "${p.unlockedAchievements.size}/${Achievements.all.size}",
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(s["tip_controls"], style = NDType.Caption, color = NDColors.TextFaint)
            }
        }
    }
}

@Composable
private fun RecordsPanel(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState
    Panel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Readout(s["high_score_label"], p.highScore.toString(), Modifier.weight(1f))
            VRule(40.dp)
            Spacer(Modifier.width(18.dp))
            Readout(s["best_time"], formatTime(p.bestTimeSec), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MissionsPanel(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState
    val allDone = p.missions.isNotEmpty() && p.missions.all { it.completed }
    Panel(Modifier.fillMaxWidth(), edge = if (allDone) NDColors.Positive else null) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Overline(s["daily_missions"], Modifier.weight(1f))
                Overline("${s["daily_streak"]} ${p.dailyStreak}", color = NDColors.TextDim)
            }
            for (m in p.missions) {
                Spacer(Modifier.height(9.dp))
                MissionRow(m)
            }
            Spacer(Modifier.height(10.dp))
            Overline(
                if (allDone) "${s["daily_bonus"]} +${Missions.ALL_DONE_BONUS} · ${s["daily_done"]}"
                else "${s["missions_bonus_hint"]} +${Missions.ALL_DONE_BONUS}",
                color = if (allDone) NDColors.Positive else NDColors.TextFaint,
            )
        }
    }
}

@Composable
private fun MissionRow(m: MissionSnapshot) {
    val s = LocalStrings.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                missionTitle(s, m),
                style = NDType.Label,
                color = if (m.completed) NDColors.TextDim else NDColors.Text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
            if (m.completed) CheckGlyph(NDColors.Positive, 12.dp)
            else Text("${m.progress}/${m.goal}", style = NDType.Caption, color = NDColors.TextDim)
            Spacer(Modifier.width(10.dp))
            Text("+${m.reward}", style = NDType.Caption, color = if (m.completed) NDColors.Positive else NDColors.TextFaint)
            Spacer(Modifier.width(4.dp))
            CoinIcon(12)
        }
        Spacer(Modifier.height(5.dp))
        Meter(
            if (m.completed) 1f else m.progress.toFloat() / m.goal,
            color = if (m.completed) NDColors.Positive else NDColors.Accent,
            height = 4.dp,
        )
    }
}

/** "Collect 45 coins": the mission's title with its goal filled in. */
fun missionTitle(s: Strings, m: MissionSnapshot): String = s[m.type.titleKey].replace("%d", m.goal.toString())

/**
 * "45s" below a minute, then "2:16". The colon form is deliberate - with the pixel display font
 * an "m" suffix reads like an "h", which made "2m 16s" look like two hours.
 */
fun formatTime(seconds: Int): String {
    if (seconds < 60) return "${seconds}s"
    val m = seconds / 60
    val ssec = seconds % 60
    return "$m:${ssec.toString().padStart(2, '0')}"
}
