package wojtoteka.nightdrive.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.components.GlowText
import wojtoteka.nightdrive.ui.components.NDCard
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.NightDriveTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember

/** Root composable: the animated world with the current screen layered on top. */
@Composable
fun NightDriveApp(vm: GameViewModel, ads: wojtoteka.nightdrive.ads.AdsController) {
    NightDriveTheme(language = vm.language) {
        // Drive the fixed-timestep simulation from the Compose frame clock.
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { now ->
                    if (last != 0L) vm.onFrame(now - last)
                    last = now
                }
            }
        }

        Box(Modifier.fillMaxSize().background(NDColors.Background)) {
            WorldBackground(vm, Modifier.fillMaxSize())

            if (!vm.isReady) {
                LoadingOverlay()
            } else {
                Crossfade(targetState = vm.destination, animationSpec = tween(280), label = "nav") { dest ->
                    when (dest) {
                        Destination.MENU -> MenuScreen(vm)
                        Destination.GAME -> GameScreen(vm, ads)
                        Destination.SHOP -> ShopScreen(vm, ads)
                        Destination.ACHIEVEMENTS -> AchievementsScreen(vm)
                        Destination.SETTINGS -> SettingsScreen(vm, ads)
                        Destination.LEADERBOARD -> LeaderboardScreen(vm)
                    }
                }
            }

            AchievementToast(vm, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlowText("NIGHT DRIVE", NDType.Brand, color = NDColors.TextPrimary, glow = NDColors.Magenta)
    }
}

@Composable
private fun AchievementToast(vm: GameViewModel, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val key by remember { androidx.compose.runtime.derivedStateOf { vm.achievementToastKey } }
    AnimatedVisibility(
        visible = key != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(top = 14.dp),
    ) {
        NDCard(borderColor = NDColors.Gold.copy(alpha = 0.5f)) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                val name = key?.let { strings[it] } ?: ""
                Text(
                    "★  ${strings["achievement_unlocked"]}: $name",
                    style = NDType.Label,
                    color = NDColors.Gold,
                )
            }
        }
    }
}
