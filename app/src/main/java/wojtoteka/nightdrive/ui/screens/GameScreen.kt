package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.ads.AdsController
import wojtoteka.nightdrive.engine.GamePhase
import wojtoteka.nightdrive.net.SubmitState
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.HudPowerUp
import wojtoteka.nightdrive.ui.PadDir
import wojtoteka.nightdrive.ui.components.CoinIcon
import wojtoteka.nightdrive.ui.components.GlowText
import wojtoteka.nightdrive.ui.components.NDCard
import wojtoteka.nightdrive.ui.components.NeonButton
import wojtoteka.nightdrive.ui.components.NeonProgress
import wojtoteka.nightdrive.ui.components.PowerUpGlyph
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.Radius
import kotlin.math.min

@Composable
fun GameScreen(vm: GameViewModel, ads: AdsController) {
    val hud = vm.hud
    val playing = hud.phase == GamePhase.PLAYING && !hud.paused

    BackHandler {
        when {
            hud.phase == GamePhase.GAME_OVER -> ads.maybeShowInterstitial { vm.quitToMenu() }
            hud.paused -> vm.togglePause()
            hud.phase == GamePhase.PLAYING -> vm.togglePause()
            else -> vm.quitToMenu()
        }
    }

    Box(Modifier.fillMaxSize()) {
        SteerSurface(vm, enabled = playing, modifier = Modifier.fillMaxSize())

        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            HudLeft(vm, Modifier.align(Alignment.TopStart))
            if (hud.phase == GamePhase.PLAYING) {
                PauseButton(onClick = { vm.togglePause() }, modifier = Modifier.align(Alignment.TopEnd))
                if (!hud.paused) {
                    DirectionalPad(vm, Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 4.dp))
                }
            }
            PowerUpPanel(hud.powerups, Modifier.align(Alignment.CenterEnd))
        }

        PowerUpToastView(vm, Modifier.align(Alignment.Center))

        if (hud.paused) PauseOverlay(vm, ads)
        if (hud.phase == GamePhase.GAME_OVER) GameOverOverlay(vm, ads)
    }
}

/** Full-area drag-to-steer surface: the car drives toward your finger (same movement model as the
 * original d-pad, just a friendlier input). */
@Composable
private fun SteerSurface(vm: GameViewModel, enabled: Boolean, modifier: Modifier) {
    Box(
        modifier.pointerInput(enabled) {
            if (!enabled) { vm.clearInput(); return@pointerInput }
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.pressed } ?: event.changes.firstOrNull()
                    if (change != null && change.pressed) {
                        val pos = change.position
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        val unit = min(w, h) / 200f
                        val px = w / 2f + vm.engine.player.x * unit
                        val py = h / 2f - vm.engine.player.y * unit
                        val dz = unit * 3.5f
                        vm.setInput(
                            left = pos.x < px - dz,
                            right = pos.x > px + dz,
                            up = pos.y < py - dz,
                            down = pos.y > py + dz,
                        )
                    } else {
                        vm.clearInput()
                    }
                }
            }
        },
    )
}

@Composable
private fun HudLeft(vm: GameViewModel, modifier: Modifier) {
    val s = LocalStrings.current
    val hud = vm.hud
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            GlowText(hud.score.toString(), NDType.Score, color = NDColors.TextPrimary, glow = NDColors.Magenta)
            if (hud.score > 0 && hud.score >= hud.highScore) {
                Spacer(Modifier.width(8.dp))
                Text(s["new_record"], style = NDType.LabelSmall, color = NDColors.Gold, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hud.coinsInSession > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(14)
                    Spacer(Modifier.width(5.dp))
                    Text(hud.coinsInSession.toString(), style = NDType.Label, color = NDColors.Gold)
                }
            }
            if (hud.comboMultiplier > 1f) ComboBadge(hud.comboMultiplier)
        }
        // Live daily progress for every goal type that advances mid-run (coins, combo, power-ups);
        // the survival type only updates at crash, so it never renders here - same as before.
        if (!hud.dailyCompleted && hud.dailyProgress > 0) {
            Spacer(Modifier.height(6.dp))
            Text("${s["daily_challenge"]}: ${hud.dailyProgress}/${hud.dailyGoal}", style = NDType.LabelSmall, color = NDColors.Cyan)
        }
    }
}

@Composable
private fun ComboBadge(multiplier: Float) {
    val s = LocalStrings.current
    val color = when {
        multiplier >= 4f -> NDColors.Magenta
        multiplier >= 3f -> NDColors.Violet
        multiplier >= 2f -> NDColors.Cyan
        else -> NDColors.Green
    }
    val scale by animateFloatAsState(1f, label = "combo")
    NDCard(shape = Radius.pill, borderColor = color.copy(alpha = 0.5f), modifier = Modifier.scale(scale)) {
        Text(
            "${s["combo"]} ×${fmtMult(multiplier)}",
            style = NDType.LabelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun PowerUpPanel(powerups: List<HudPowerUp>, modifier: Modifier) {
    if (powerups.isEmpty()) return
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (pu in powerups) PowerUpRow(pu)
    }
}

@Composable
private fun PowerUpRow(pu: HudPowerUp) {
    val s = LocalStrings.current
    val color = Color(pu.type.color)
    NDCard(borderColor = color.copy(alpha = 0.35f), shape = Radius.chip, modifier = Modifier.width(148.dp)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PowerUpGlyph(pu.type, 18)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(s[pu.type.labelKey], style = NDType.LabelSmall, color = NDColors.TextPrimary, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                if (pu.isCharge) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(pu.chargeCount.coerceAtMost(5)) {
                            Box(Modifier.size(width = 12.dp, height = 3.dp).clip(Radius.pill).background(color))
                        }
                    }
                } else {
                    NeonProgress(pu.fraction, color, height = 3)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (pu.isCharge) "×${pu.chargeCount}" else "${pu.remainingSeconds}s",
                style = NDType.LabelSmall,
                color = color,
            )
        }
    }
}

@Composable
private fun PowerUpToastView(vm: GameViewModel, modifier: Modifier) {
    val s = LocalStrings.current
    val type = vm.powerUpToast
    AnimatedVisibility(
        visible = type != null,
        enter = scaleIn(initialScale = 0.7f) + fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val t = type
        if (t != null) {
            NDCard(borderColor = Color(t.color).copy(alpha = 0.6f)) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PowerUpGlyph(t, 24)
                    Spacer(Modifier.width(12.dp))
                    GlowText(s[t.labelKey], NDType.Heading, color = Color(t.color), glow = Color(t.color), glowRadius = 14f)
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(vm: GameViewModel, ads: AdsController) {
    val s = LocalStrings.current
    Box(
        Modifier.fillMaxSize().background(Color(0xCC05060F)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlowText(s["paused"], NDType.Brand, color = NDColors.Cyan, glow = NDColors.Cyan)
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NeonButton(s["resume"], { vm.togglePause() }, color = NDColors.Green, icon = Icons.Filled.PlayArrow)
                NeonButton(
                    s["quit_to_menu"],
                    { ads.maybeShowInterstitial { vm.quitToMenu() } },
                    color = NDColors.Magenta,
                    icon = Icons.Filled.Close,
                )
            }
        }
    }
}

@Composable
private fun GameOverOverlay(vm: GameViewModel, ads: AdsController) {
    val s = LocalStrings.current
    val hud = vm.hud
    val isRecord = hud.score > 0 && hud.score >= hud.highScore
    AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = scaleOut() + fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xE605060F))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                // Keep the content (especially the nickname field) above the soft keyboard.
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlowText(s["game_over"], NDType.Brand, color = NDColors.Red, glow = NDColors.Red)
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    ResultStat(s["score"], hud.score.toString(), NDColors.Cyan)
                    ResultStat(s["high_score"], hud.highScore.toString(), NDColors.Magenta)
                }
                if (isRecord) {
                    Text("★ ${s["new_record"]} ★", style = NDType.Heading, color = NDColors.Gold, textAlign = TextAlign.Center)
                }
                AdOfferSection(vm, ads)
                LeaderboardEntrySection(vm)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeonButton(s["play_again"], { ads.maybeShowInterstitial { vm.restartGame() } }, color = NDColors.Green, icon = Icons.Filled.Refresh)
                    NeonButton(s["main_menu"], { ads.maybeShowInterstitial { vm.quitToMenu() } }, color = NDColors.TextSecondary, icon = Icons.Filled.Close)
                    // Also gated by the interstitial: leaderboard-then-back used to be a free
                    // detour to the menu that dodged the ad every other exit path shows.
                    NeonButton(s["leaderboard"], { ads.maybeShowInterstitial { vm.goTo(Destination.LEADERBOARD) } }, color = NDColors.Cyan, icon = Icons.AutoMirrored.Filled.List)
                }
            }
        }
    }
}

/**
 * Both rewarded-ad offers on game over, side by side so neither pushes the other off screen:
 * "double coins" on the left, "keep driving" (revive) on the right, with the revive hint below.
 */
@Composable
private fun AdOfferSection(vm: GameViewModel, ads: AdsController) {
    val s = LocalStrings.current
    // A submitted (or in-flight) score already consumed this run's anti-cheat session - the
    // continued run couldn't be submitted again, so don't offer a continue that would mislead.
    val reviveVisible = !vm.adReviveUsed && ads.rewardedReady &&
        vm.submitState !is SubmitState.Done && vm.submitState !is SubmitState.Submitting
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdRewardSection(vm, ads)
            if (reviveVisible) {
                NeonButton(
                    text = s["revive"],
                    onClick = { ads.showRewarded { vm.grantAdRevive() } },
                    color = NDColors.Green,
                    icon = Icons.Filled.PlayArrow,
                )
            }
        }
        if (reviveVisible) {
            Spacer(Modifier.height(4.dp))
            Text(s["revive_hint"], style = NDType.Caption, color = NDColors.TextMuted, textAlign = TextAlign.Center)
        }
    }
}

/** Rewarded ad on game over: double the coins collected this run (once per run). */
@Composable
private fun AdRewardSection(vm: GameViewModel, ads: AdsController) {
    val s = LocalStrings.current
    val coins = vm.hud.coinsInSession
    if (coins <= 0) return
    when {
        vm.adRewardClaimed -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(16)
                Spacer(Modifier.width(6.dp))
                Text("+$coins ✓", style = NDType.Heading, color = NDColors.Gold)
            }
        }
        ads.rewardedReady -> {
            NeonButton(
                "${s["double_coins"]}  +$coins",
                { ads.showRewarded { vm.grantAdCoinDouble() } },
                color = NDColors.Gold,
                icon = Icons.Filled.PlayArrow,
            )
        }
    }
}

/** Game-over → offer to save the score to the online Top 10 when it qualifies. */
@Composable
private fun LeaderboardEntrySection(vm: GameViewModel) {
    val s = LocalStrings.current
    when (val st = vm.submitState) {
        is SubmitState.Done ->
            Text("${s["saved_rank"]} #${st.rank ?: "?"}", style = NDType.Heading, color = NDColors.Green)
        SubmitState.Submitting ->
            Text(s["submitting"], style = NDType.Label, color = NDColors.TextSecondary)
        SubmitState.Failed ->
            Text(s["submit_failed"], style = NDType.Label, color = NDColors.Red)
        SubmitState.Offline ->
            Text(s["submit_offline"], style = NDType.Label, color = NDColors.TextMuted)
        SubmitState.Idle -> if (vm.scoreQualifies) Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var name by remember { mutableStateOf(vm.lastNickname) }
            Text("★ ${s["you_qualified"]}", style = NDType.Heading, color = NDColors.Gold)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { input ->
                        name = input.filter { it.isLetterOrDigit() || it in " _.-" }.take(16)
                    },
                    singleLine = true,
                    placeholder = { Text(s["enter_name"], color = NDColors.TextMuted) },
                    textStyle = NDType.Label.copy(color = NDColors.TextPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (name.trim().isNotEmpty()) vm.submitNickname(name)
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = NDColors.Surface,
                        unfocusedContainerColor = NDColors.Surface,
                        focusedIndicatorColor = NDColors.Cyan,
                        unfocusedIndicatorColor = NDColors.Outline,
                        cursorColor = NDColors.Cyan,
                    ),
                    modifier = Modifier.width(220.dp),
                )
                NeonButton(
                    s["submit_score"],
                    { vm.submitNickname(name) },
                    color = NDColors.Gold,
                    enabled = name.trim().isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = NDType.LabelSmall, color = NDColors.TextMuted)
        Spacer(Modifier.height(4.dp))
        GlowText(value, NDType.Score, color = color, glow = color, glowRadius = 16f)
    }
}

private fun fmtMult(m: Float): String = if (m == m.toInt().toFloat()) m.toInt().toString() else m.toString()

/**
 * On-screen d-pad, bottom-left like the original game. The whole 3x3 area is one drag surface:
 * sliding a held finger from one arrow to another switches the active direction immediately,
 * without needing to lift and re-tap (corners blend the two neighbouring directions, e.g.
 * top-left presses up+left together). Still multi-touch friendly (hold up + right with two
 * fingers). While any pad button is held, pad input takes priority over the slide-to-steer
 * surface.
 */
@Composable
private fun DirectionalPad(vm: GameViewModel, modifier: Modifier = Modifier) {
    val cell = 58.dp
    var activeDirs by remember { mutableStateOf(emptySet<PadDir>()) }
    Box(
        modifier
            .size(cell * 3)
            .pointerInput(Unit) {
                val cellPx = cell.toPx()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val newDirs = event.changes
                            .filter { it.pressed }
                            .flatMap { padDirsAt(it.position, cellPx) }
                            .toSet()
                        if (newDirs != activeDirs) {
                            (activeDirs - newDirs).forEach { vm.setPad(it, false) }
                            (newDirs - activeDirs).forEach { vm.setPad(it, true) }
                            activeDirs = newDirs
                        }
                    }
                }
            },
    ) {
        PadButton(PadDir.UP, PadDir.UP in activeDirs, Modifier.align(Alignment.TopCenter).size(cell))
        PadButton(PadDir.LEFT, PadDir.LEFT in activeDirs, Modifier.align(Alignment.CenterStart).size(cell))
        PadButton(PadDir.RIGHT, PadDir.RIGHT in activeDirs, Modifier.align(Alignment.CenterEnd).size(cell))
        PadButton(PadDir.DOWN, PadDir.DOWN in activeDirs, Modifier.align(Alignment.BottomCenter).size(cell))
    }
}

/** Maps a touch position within the pad's 3x3 cell grid to the direction(s) it represents. */
private fun padDirsAt(pos: androidx.compose.ui.geometry.Offset, cellPx: Float): Set<PadDir> {
    val col = (pos.x / cellPx).toInt().coerceIn(0, 2)
    val row = (pos.y / cellPx).toInt().coerceIn(0, 2)
    return when {
        row == 0 && col == 1 -> setOf(PadDir.UP)
        row == 2 && col == 1 -> setOf(PadDir.DOWN)
        row == 1 && col == 0 -> setOf(PadDir.LEFT)
        row == 1 && col == 2 -> setOf(PadDir.RIGHT)
        row == 0 && col == 0 -> setOf(PadDir.UP, PadDir.LEFT)
        row == 0 && col == 2 -> setOf(PadDir.UP, PadDir.RIGHT)
        row == 2 && col == 0 -> setOf(PadDir.DOWN, PadDir.LEFT)
        row == 2 && col == 2 -> setOf(PadDir.DOWN, PadDir.RIGHT)
        else -> emptySet() // dead zone in the very centre
    }
}

@Composable
private fun PadButton(dir: PadDir, pressed: Boolean, modifier: Modifier) {
    Box(
        modifier
            .clip(Radius.pill)
            .background(if (pressed) Color(0x8822E9FF) else Color(0x5A0D0F22))
            .border(
                androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (pressed) NDColors.Cyan else Color.White.copy(alpha = 0.35f),
                ),
                Radius.pill,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
            val w = size.width; val h = size.height
            val p = androidx.compose.ui.graphics.Path()
            when (dir) {
                PadDir.UP -> { p.moveTo(w / 2, 0f); p.lineTo(w, h); p.lineTo(0f, h) }
                PadDir.DOWN -> { p.moveTo(0f, 0f); p.lineTo(w, 0f); p.lineTo(w / 2, h) }
                PadDir.LEFT -> { p.moveTo(w, 0f); p.lineTo(w, h); p.lineTo(0f, h / 2) }
                PadDir.RIGHT -> { p.moveTo(0f, 0f); p.lineTo(w, h / 2); p.lineTo(0f, h) }
            }
            p.close()
            drawPath(p, if (pressed) Color.White else Color.White.copy(alpha = 0.85f))
        }
    }
}

/** Round pause button drawn with two neon bars (Pause isn't in the core icon set). */
@Composable
private fun PauseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(Radius.pill)
            .background(Color(0xB30D0F22))
            .androidxBorder()
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
            val barW = size.width * 0.28f
            val h = size.height
            drawRect(NDColors.TextPrimary, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.10f, 0f), size = androidx.compose.ui.geometry.Size(barW, h))
            drawRect(NDColors.TextPrimary, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.62f, 0f), size = androidx.compose.ui.geometry.Size(barW, h))
        }
    }
}

private fun Modifier.androidxBorder(): Modifier =
    border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), Radius.pill)
