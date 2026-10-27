package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.domain.Achievement
import wojtoteka.nightdrive.domain.AchievementKind
import wojtoteka.nightdrive.domain.Achievements
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.ProfileSnapshot
import wojtoteka.nightdrive.ui.components.CheckGlyph
import wojtoteka.nightdrive.ui.components.CoinIcon
import wojtoteka.nightdrive.ui.components.Meter
import wojtoteka.nightdrive.ui.components.Overline
import wojtoteka.nightdrive.ui.components.Panel
import wojtoteka.nightdrive.ui.components.ScreenHeader
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.Space
import kotlin.math.min

@Composable
fun AchievementsScreen(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState
    BackHandler { vm.goTo(Destination.MENU) }
    // Done ones sink to the bottom; among the rest, the closest to completion come first.
    val ordered = Achievements.all.sortedWith(
        compareBy<Achievement> { p.unlockedAchievements.contains(it.id) }
            .thenByDescending { achievementProgress(it, p).toFloat() / it.goal }
    )

    Column(
        Modifier
            .fillMaxSize()
            // The list runs right down to the road's near line and disappears into it.
            .aboveRoadEdge(0.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .padding(start = Space.gutter, end = Space.gutter, top = 14.dp),
    ) {
        ScreenHeader(
            title = s["achievements"],
            onBack = { vm.goTo(Destination.MENU) },
            backLabel = s["back"],
            trailing = {
                Text(
                    "${p.unlockedAchievements.size}/${Achievements.all.size}",
                    style = NDType.Readout,
                    color = NDColors.Text,
                )
            },
        )
        Spacer(Modifier.height(Space.section))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(ordered, key = { it.id }) { a ->
                AchievementCard(a, p, unlocked = p.unlockedAchievements.contains(a.id))
            }
        }
    }
}

@Composable
private fun AchievementCard(a: Achievement, p: ProfileSnapshot, unlocked: Boolean) {
    val s = LocalStrings.current
    val progress = achievementProgress(a, p)
    val fraction = if (a.goal > 0) progress.toFloat() / a.goal else 0f
    val suffix = if (a.kind == AchievementKind.TIME) s["seconds_word"] else ""
    Panel(Modifier.fillMaxWidth(), edge = if (unlocked) NDColors.Accent else null) {
        Column(Modifier.padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s[a.nameKey].uppercase(),
                    style = NDType.Heading,
                    color = if (unlocked) NDColors.Text else NDColors.TextDim,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (unlocked) {
                    CheckGlyph(NDColors.Accent)
                } else {
                    Text("+${a.reward}", style = NDType.ReadoutSmall, color = NDColors.TextDim)
                    Spacer(Modifier.width(6.dp))
                    CoinIcon(14)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(s[a.descKey], style = NDType.Body, color = NDColors.TextFaint, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            if (unlocked) {
                Overline("${s["completed"]} · +${a.reward} ${s["coins_word"]}", color = NDColors.Accent)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Meter(fraction, Modifier.weight(1f), segments = 20, height = 5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${min(progress, a.goal)}$suffix/${a.goal}$suffix",
                        style = NDType.Caption,
                        color = NDColors.TextDim,
                    )
                }
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
    AchievementKind.NEAR_MISS -> p.lifetimeNearMisses
    AchievementKind.MISSIONS -> p.lifetimeMissions
}
