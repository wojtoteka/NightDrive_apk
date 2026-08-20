package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.domain.Achievement
import wojtoteka.nightdrive.domain.AchievementKind
import wojtoteka.nightdrive.domain.Achievements
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.ProfileSnapshot
import wojtoteka.nightdrive.ui.components.CoinIcon
import wojtoteka.nightdrive.ui.components.NDCard
import wojtoteka.nightdrive.ui.components.NeonProgress
import wojtoteka.nightdrive.ui.components.ScreenHeader
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import kotlin.math.min

@Composable
fun AchievementsScreen(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState
    BackHandler { vm.goTo(Destination.MENU) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        ScreenHeader(
            title = s["achievements"],
            onBack = { vm.goTo(Destination.MENU) },
            accent = NDColors.Violet,
            trailing = {
                Text("${p.unlockedAchievements.size}/${Achievements.all.size}", style = NDType.Heading, color = NDColors.Green)
            },
        )
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(Achievements.all, key = { it.id }) { a ->
                AchievementCard(a, p, unlocked = p.unlockedAchievements.contains(a.id))
            }
        }
    }
}

@Composable
private fun AchievementCard(a: Achievement, p: ProfileSnapshot, unlocked: Boolean) {
    val s = LocalStrings.current
    val accent = if (unlocked) NDColors.Green else NDColors.Violet
    val progress = achievementProgress(a, p)
    val fraction = if (a.goal > 0) progress.toFloat() / a.goal else 0f
    val suffix = when (a.kind) {
        AchievementKind.TIME -> s["seconds_word"]
        else -> ""
    }
    NDCard(borderColor = accent.copy(alpha = if (unlocked) 0.55f else 0.22f)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                val icon = when {
                    unlocked -> Icons.Filled.Check
                    fraction > 0f -> Icons.Filled.Star
                    else -> Icons.Filled.Lock
                }
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(s[a.nameKey], style = NDType.Label, color = NDColors.TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("+${a.reward}", style = NDType.LabelSmall, color = NDColors.Gold)
                        Spacer(Modifier.width(4.dp))
                        CoinIcon(13)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(s[a.descKey], style = NDType.BodyMuted, color = NDColors.TextMuted)
                Spacer(Modifier.height(8.dp))
                NeonProgress(fraction, accent, height = 5)
                Spacer(Modifier.height(4.dp))
                Text("${min(progress, a.goal)}$suffix / ${a.goal}$suffix", style = NDType.Caption, color = NDColors.TextMuted)
            }
        }
    }
}

private fun achievementProgress(a: Achievement, p: ProfileSnapshot): Int = when (a.kind) {
    AchievementKind.TOTAL_COINS -> p.lifetimeCoins
    AchievementKind.SCORE -> p.highScore
    AchievementKind.STREAK -> p.longestStreak
    AchievementKind.TIME -> p.bestTimeSec
    AchievementKind.DAILY_STREAK -> p.bestDailyStreak
    AchievementKind.SKIN_COUNT -> p.ownedSkins.size
    AchievementKind.SHIELD_BLOCK -> p.lifetimeShieldBlocks
    AchievementKind.BOOST_USED -> p.lifetimeBoostsUsed
    AchievementKind.POWERUP_COLLECT -> p.lifetimePowerups
}
