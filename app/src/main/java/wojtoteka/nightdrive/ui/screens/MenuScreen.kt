package wojtoteka.nightdrive.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import wojtoteka.nightdrive.ui.components.focusRing
import wojtoteka.nightdrive.ui.components.isNavFocus
import wojtoteka.nightdrive.ui.theme.Corner
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
                Spacer(Modifier.height(4.dp))
                Overline(s["app_tagline"], color = NDColors.TextDim)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.widthIn(max = 340.dp)) {
                    RecordsPanel(vm)
                    Spacer(Modifier.height(6.dp))
                    MissionsPanel(vm)
                }
            }

            Spacer(Modifier.width(28.dp))

            // Right: the one big action, then the rest as a list
            Column(Modifier.width(360.dp)) {
                Spacer(Modifier.height(40.dp))
                // The daily route sits beside PLAY: both start a run, and a fourth list row
                // would push the list off the bottom of a phone in landscape.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(s["play"], { vm.startGame() }, Modifier.weight(1f).fillMaxHeight(), large = true)
                    DailyRouteButton(
                        detail = if (p.dailyRunAttemptsToday > 0) "${s["best_short"]} ${p.dailyRunBestToday}" else s["daily_run_new"],
                        onClick = { vm.startDailyRun() },
                        modifier = Modifier.width(140.dp).fillMaxHeight(),
                    )
                }
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

/** Secondary start button: the route's name over today's best (or "new" before the first try). */
@Composable
private fun DailyRouteButton(detail: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val nav = isNavFocus(focused)
    Column(
        modifier
            .clip(Corner.control)
            .background(if (pressed) NDColors.PanelRaised else NDColors.PanelSolid)
            .border(BorderStroke(1.dp, NDColors.LineStrong), Corner.control)
            .focusRing(nav)
            .clickable(interaction, null, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(s["daily_run"].uppercase(), style = NDType.Button, color = NDColors.Text, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Overline(detail, color = NDColors.Accent)
    }
}

@Composable
private fun RecordsPanel(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState
    Panel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 11.dp).height(IntrinsicSize.Min),
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
        Column(Modifier.padding(horizontal = 18.dp, vertical = 11.dp)) {
            // The all-three bonus rides in the header, so the panel stays three rows tall.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Overline(s["daily_missions"])
                Overline(
                    if (allDone) " · ${s["daily_bonus"]} +${Missions.ALL_DONE_BONUS} ✓"
                    else " · ${s["missions_bonus_hint"]} +${Missions.ALL_DONE_BONUS}",
                    Modifier.weight(1f),
                    color = if (allDone) NDColors.Positive else NDColors.TextFaint,
                )
                Overline("${s["daily_streak_short"]} ${p.dailyStreak}", color = NDColors.TextDim)
            }
            for (m in p.missions) {
                Spacer(Modifier.height(7.dp))
                MissionRow(m)
            }
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
        Spacer(Modifier.height(4.dp))
        Meter(
            if (m.completed) 1f else m.progress.toFloat() / m.goal,
            color = if (m.completed) NDColors.Positive else NDColors.Accent,
            height = 3.dp,
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
