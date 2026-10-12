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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.domain.Achievements
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
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
            // Left: logo, records, daily
            Column(Modifier.weight(1f)) {
                GlowText("NIGHT", NDType.Brand, color = NDColors.LogoWhite, glow = NDColors.LogoCyan)
                GlowText("DRIVE", NDType.Brand, color = NDColors.LogoMagenta, glow = NDColors.LogoMagenta)
                Spacer(Modifier.height(6.dp))
                Overline(s["app_tagline"], color = NDColors.TextDim)
                Spacer(Modifier.height(Space.section))
                Column(Modifier.widthIn(max = 340.dp)) {
                    RecordsPanel(vm)
                    Spacer(Modifier.height(8.dp))
                    DailyPanel(vm)
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
private fun DailyPanel(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState
    // 0 = collect coins, 1 = survive seconds, 2 = hit a combo in one run, 3 = collect power-ups
    val title = when (p.dailyType) {
        0 -> s["daily_collect"]
        1 -> s["daily_survive"]
        2 -> s["daily_combo"]
        else -> s["daily_powerups"]
    }
    val unitSuffix = when (p.dailyType) {
        0 -> " ${s["coins_word"]}"
        1 -> " ${s["seconds_word"]}"
        else -> ""
    }
    val fraction = if (p.dailyGoal > 0) p.dailyProgress.toFloat() / p.dailyGoal else 0f
    val done = p.dailyCompleted
    Panel(Modifier.fillMaxWidth(), edge = if (done) NDColors.Positive else null) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Overline(s["daily_challenge"], Modifier.weight(1f))
                Overline("${s["daily_streak"]} ${p.dailyStreak}", color = NDColors.TextDim)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = NDType.Heading, color = NDColors.Text, modifier = Modifier.weight(1f))
                Text(
                    if (done) s["daily_done"].uppercase() else "${p.dailyProgress}/${p.dailyGoal}$unitSuffix",
                    style = if (done) NDType.Overline else NDType.ReadoutSmall,
                    color = if (done) NDColors.Positive else NDColors.TextDim,
                )
            }
            Spacer(Modifier.height(10.dp))
            Meter(if (done) 1f else fraction, color = if (done) NDColors.Positive else NDColors.Accent)
        }
    }
}

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
