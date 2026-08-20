package wojtoteka.nightdrive.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.domain.Achievements
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.components.CoinPill
import wojtoteka.nightdrive.ui.components.CircleIconButton
import wojtoteka.nightdrive.ui.components.GhostButton
import wojtoteka.nightdrive.ui.components.GlowText
import wojtoteka.nightdrive.ui.components.NDCard
import wojtoteka.nightdrive.ui.components.NeonButton
import wojtoteka.nightdrive.ui.components.NeonProgress
import wojtoteka.nightdrive.ui.components.StatChip
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType

@Composable
fun MenuScreen(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        // Top bar: coins + settings
        Row(
            Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoinPill(p.totalCoins)
            Spacer(Modifier.width(10.dp))
            CircleIconButton(Icons.Filled.Settings, { vm.goTo(Destination.SETTINGS) }, tint = NDColors.TextSecondary, contentDescription = s["settings"])
        }

        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: brand + stats + daily
            Column(Modifier.weight(1f)) {
                GlowText("NIGHT", NDType.Brand, color = NDColors.TextPrimary, glow = NDColors.Cyan)
                GlowText("DRIVE", NDType.Brand, color = NDColors.Magenta, glow = NDColors.Magenta)
                Spacer(Modifier.height(6.dp))
                Text(s["app_tagline"], style = NDType.Label, color = NDColors.TextSecondary)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip(s["high_score_label"], p.highScore.toString(), NDColors.Magenta)
                    StatChip(s["best_time"], formatTime(p.bestTimeSec), NDColors.Cyan)
                }
                Spacer(Modifier.height(12.dp))
                DailyCard(vm)
            }

            Spacer(Modifier.width(26.dp))

            // Right: play + secondary actions
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NeonButton(
                    text = s["play"],
                    onClick = { vm.startGame() },
                    color = NDColors.Cyan,
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier.fillMaxWidth(0.82f).height(64.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton(s["shop"], { vm.goTo(Destination.SHOP) }, color = NDColors.Gold, icon = Icons.Filled.ShoppingCart)
                    GhostButton(s["leaderboard"], { vm.goTo(Destination.LEADERBOARD) }, color = NDColors.Cyan, icon = Icons.AutoMirrored.Filled.List)
                }
                Spacer(Modifier.height(10.dp))
                run {
                    val unlocked = p.unlockedAchievements.size
                    GhostButton("${s["achievements"]}  $unlocked/${Achievements.all.size}", { vm.goTo(Destination.ACHIEVEMENTS) }, color = NDColors.Violet, icon = Icons.Filled.Star)
                }
                Spacer(Modifier.height(10.dp))
                Text(s["tip_controls"], style = NDType.Caption, color = NDColors.TextMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DailyCard(vm: GameViewModel) {
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
    NDCard(borderColor = NDColors.Cyan.copy(alpha = 0.25f)) {
        Column(Modifier.padding(16.dp).width(260.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(s["daily_challenge"].uppercase(), style = NDType.LabelSmall, color = NDColors.Cyan)
                Text("${s["daily_streak"]}: ${p.dailyStreak}", style = NDType.LabelSmall, color = NDColors.TextMuted)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (p.dailyCompleted) "${title} — ${s["daily_done"]}" else "$title  ${p.dailyProgress}/${p.dailyGoal}$unitSuffix",
                style = NDType.Label,
                color = if (p.dailyCompleted) NDColors.Green else NDColors.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            NeonProgress(fraction, if (p.dailyCompleted) NDColors.Green else NDColors.Cyan)
        }
    }
}

/**
 * "45s" below a minute, then "2:16". The colon form is deliberate — with the pixel display font
 * an "m" suffix reads like an "h", which made "2m 16s" look like two hours.
 */
fun formatTime(seconds: Int): String {
    if (seconds < 60) return "${seconds}s"
    val m = seconds / 60
    val ssec = seconds % 60
    return "$m:${ssec.toString().padStart(2, '0')}"
}
