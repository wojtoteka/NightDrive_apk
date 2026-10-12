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
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.components.GlowText
import wojtoteka.nightdrive.ui.components.Overline
import wojtoteka.nightdrive.ui.components.Panel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.NightDriveTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember

/** Root composable: the animated world with the current screen layered on top. */
@Composable
fun NightDriveApp(vm: GameViewModel) {
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

        Box(Modifier.fillMaxSize().background(NDColors.Ink)) {
            WorldBackground(vm, Modifier.fillMaxSize())

            if (!vm.isReady) {
                LoadingOverlay()
            } else {
                Crossfade(targetState = vm.destination, animationSpec = tween(280), label = "nav") { dest ->
                    when (dest) {
                        Destination.MENU -> MenuScreen(vm)
                        Destination.GAME -> GameScreen(vm)
                        Destination.SHOP -> ShopScreen(vm)
                        Destination.ACHIEVEMENTS -> AchievementsScreen(vm)
                        Destination.SETTINGS -> SettingsScreen(vm)
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
        GlowText("NIGHT DRIVE", NDType.Brand, color = NDColors.LogoWhite, glow = NDColors.LogoMagenta)
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
        Panel(fill = NDColors.PanelSolid, edge = NDColors.Accent) {
            Column(Modifier.padding(start = 20.dp, end = 22.dp, top = 10.dp, bottom = 11.dp)) {
                Overline(strings["achievement_unlocked"], color = NDColors.Accent)
                Spacer(Modifier.height(3.dp))
                Text(key?.let { strings[it] } ?: "", style = NDType.Heading, color = NDColors.Text)
            }
        }
    }
}
