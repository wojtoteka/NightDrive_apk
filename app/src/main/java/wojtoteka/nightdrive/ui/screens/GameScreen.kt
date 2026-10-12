package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.engine.GamePhase
import wojtoteka.nightdrive.net.SubmitState
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.HudPowerUp
import wojtoteka.nightdrive.ui.PadDir
import wojtoteka.nightdrive.ui.components.Bar
import wojtoteka.nightdrive.ui.components.CoinIcon
import wojtoteka.nightdrive.ui.components.Overline
import wojtoteka.nightdrive.ui.components.Panel
import wojtoteka.nightdrive.ui.components.PauseGlyph
import wojtoteka.nightdrive.ui.components.PowerUpGlyph
import wojtoteka.nightdrive.ui.components.PrimaryButton
import wojtoteka.nightdrive.ui.components.Readout
import wojtoteka.nightdrive.ui.components.SecondaryButton
import wojtoteka.nightdrive.ui.components.SquareIconButton
import wojtoteka.nightdrive.ui.components.Tag
import wojtoteka.nightdrive.ui.components.VRule
import wojtoteka.nightdrive.ui.theme.Corner
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import kotlin.math.min

/** Dark drop shadow so HUD text stays legible over the bright sun and neon grid, without a glow. */
private val hudShadow = Shadow(Color(0xCC000000), Offset(0f, 2f), 6f)

@Composable
fun GameScreen(vm: GameViewModel) {
    val hud = vm.hud
    val playing = hud.phase == GamePhase.PLAYING && !hud.paused

    BackHandler {
        when {
            hud.phase == GamePhase.GAME_OVER -> vm.quitToMenu()
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
                SquareIconButton({ vm.togglePause() }, Modifier.align(Alignment.TopEnd)) { PauseGlyph() }
                if (!hud.paused) {
                    DirectionalPad(vm, Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 4.dp))
                }
            }
            PowerUpPanel(hud.powerups, Modifier.align(Alignment.CenterEnd))
        }

        PowerUpToastView(vm, Modifier.align(Alignment.Center))

        if (hud.paused) PauseOverlay(vm)
        if (hud.phase == GamePhase.GAME_OVER) GameOverOverlay(vm)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hud.score.toString(), style = NDType.Score.copy(shadow = hudShadow), color = NDColors.Text)
            if (hud.score > 0 && hud.score >= hud.highScore) {
                Spacer(Modifier.width(10.dp))
                Tag(s["new_record"].trimEnd('!'), filled = true)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (hud.coinsInSession > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(16)
                    Spacer(Modifier.width(6.dp))
                    Text(hud.coinsInSession.toString(), style = NDType.ReadoutSmall.copy(shadow = hudShadow), color = NDColors.Text)
                }
            }
            if (hud.comboMultiplier > 1f) ComboTag(hud.comboMultiplier)
        }
        // Live daily progress for every goal type that advances mid-run (coins, combo, power-ups);
        // the survival type only updates at crash, so it never renders here.
        if (!hud.dailyCompleted && hud.dailyProgress > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${s["daily_challenge"].uppercase()}  ${hud.dailyProgress}/${hud.dailyGoal}",
                style = NDType.Overline.copy(shadow = hudShadow),
                color = NDColors.TextDim,
            )
        }
    }
}

/** Combo as a split tag: accent block with the multiplier, dark block with the word. */
@Composable
private fun ComboTag(multiplier: Float) {
    val s = LocalStrings.current
    Row(Modifier.clip(Corner.tag)) {
        Box(Modifier.background(NDColors.Accent).padding(horizontal = 7.dp, vertical = 3.dp)) {
            Text("×${fmtMult(multiplier)}", style = NDType.Overline, color = NDColors.OnAccent)
        }
        Box(Modifier.background(NDColors.PanelSolid).padding(horizontal = 7.dp, vertical = 3.dp)) {
            Text(s["combo"].uppercase(), style = NDType.Overline, color = NDColors.Text)
        }
    }
}

@Composable
private fun PowerUpPanel(powerups: List<HudPowerUp>, modifier: Modifier) {
    if (powerups.isEmpty()) return
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (pu in powerups) PowerUpRow(pu)
    }
}

@Composable
private fun PowerUpRow(pu: HudPowerUp) {
    val s = LocalStrings.current
    val color = Color(pu.type.color)
    Panel(Modifier.width(164.dp), edge = color) {
        Row(
            Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PowerUpGlyph(pu.type, 22)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s[pu.type.labelKey].uppercase(), style = NDType.Overline, color = NDColors.Text,
                        maxLines = 1, modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (pu.isCharge) "×${pu.chargeCount}" else "${pu.remainingSeconds}s",
                        style = NDType.Overline,
                        color = color,
                    )
                }
                Spacer(Modifier.height(5.dp))
                if (pu.isCharge) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(pu.chargeCount.coerceAtMost(8)) {
                            Box(Modifier.size(width = 10.dp, height = 3.dp).background(color))
                        }
                    }
                } else {
                    Bar(pu.fraction, color)
                }
            }
        }
    }
}

@Composable
private fun PowerUpToastView(vm: GameViewModel, modifier: Modifier) {
    val s = LocalStrings.current
    val type = vm.powerUpToast
    AnimatedVisibility(
        visible = type != null,
        enter = slideInHorizontally { it / 4 } + fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val t = type
        if (t != null) {
            Panel(fill = NDColors.PanelSolid, edge = Color(t.color)) {
                Row(Modifier.padding(start = 18.dp, end = 22.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PowerUpGlyph(t, 30)
                    Spacer(Modifier.width(14.dp))
                    Text(s[t.labelKey].uppercase(), style = NDType.Title, color = NDColors.Text)
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(vm: GameViewModel) {
    val s = LocalStrings.current
    Box(
        Modifier.fillMaxSize().background(NDColors.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(320.dp)) {
            Overline("${s["score"]} ${vm.hud.score}", color = NDColors.TextDim)
            Spacer(Modifier.height(4.dp))
            Text(s["paused"].uppercase(), style = NDType.Display, color = NDColors.Text)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(s["resume"], { vm.togglePause() }, Modifier.fillMaxWidth(), large = true)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(s["quit_to_menu"], { vm.quitToMenu() }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GameOverOverlay(vm: GameViewModel) {
    val s = LocalStrings.current
    val hud = vm.hud
    val isRecord = hud.score > 0 && hud.score >= hud.highScore
    Box(
        Modifier
            .fillMaxSize()
            .background(NDColors.Scrim)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Keep the content (especially the nickname field) above the soft keyboard.
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp, horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s["game_over"].uppercase(), style = NDType.Display, color = NDColors.Text)
                if (isRecord) {
                    Spacer(Modifier.width(14.dp))
                    Tag(s["new_record"].trimEnd('!'), filled = true)
                }
            }
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 14.dp).height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Readout(s["score"], hud.score.toString(), Modifier.weight(1f), valueColor = if (isRecord) NDColors.Accent else NDColors.Text)
                    VRule(40.dp)
                    Spacer(Modifier.width(18.dp))
                    Readout(s["high_score"], hud.highScore.toString(), Modifier.weight(1f))
                    VRule(40.dp)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Overline(s["total_coins"])
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoinIcon(20)
                            Spacer(Modifier.width(8.dp))
                            Text("+${hud.coinsInSession}", style = NDType.Readout, color = NDColors.Text)
                        }
                    }
                }
            }
            LeaderboardEntrySection(vm)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(s["play_again"], { vm.restartGame() }, Modifier.weight(1.4f))
                SecondaryButton(s["leaderboard"], { vm.goTo(Destination.LEADERBOARD) }, Modifier.weight(1f))
                SecondaryButton(s["main_menu"], { vm.quitToMenu() }, Modifier.weight(0.8f))
            }
        }
    }
}

/** Game-over → offer to save the score to the online Top 10 when it qualifies. */
@Composable
private fun LeaderboardEntrySection(vm: GameViewModel) {
    val s = LocalStrings.current
    val line: Pair<String, Color>? = when (val st = vm.submitState) {
        is SubmitState.Done -> "${s["saved_rank"]} #${st.rank ?: "?"}" to NDColors.Positive
        SubmitState.Submitting -> s["submitting"] to NDColors.TextDim
        SubmitState.Failed -> s["submit_failed"] to NDColors.Danger
        SubmitState.Offline -> s["submit_offline"] to NDColors.TextFaint
        SubmitState.Idle -> null
    }
    if (line != null) {
        Spacer(Modifier.height(10.dp))
        Overline(line.first, color = line.second)
        return
    }
    if (!vm.scoreQualifies) return

    var name by remember { mutableStateOf(vm.lastNickname) }
    val submit = { if (name.trim().isNotEmpty()) vm.submitNickname(name) }
    Spacer(Modifier.height(8.dp))
    Panel(Modifier.fillMaxWidth(), edge = NDColors.Accent) {
        Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Overline(s["you_qualified"], color = NDColors.Accent)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(Corner.control)
                        .background(NDColors.Inset)
                        .border(BorderStroke(1.dp, NDColors.LineStrong), Corner.control)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (name.isEmpty()) Text(s["enter_name"], style = NDType.Label, color = NDColors.TextFaint)
                    BasicTextField(
                        value = name,
                        onValueChange = { input -> name = input.filter { it.isLetterOrDigit() || it in " _.-" }.take(16) },
                        singleLine = true,
                        textStyle = NDType.Label.copy(color = NDColors.Text),
                        cursorBrush = SolidColor(NDColors.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(s["submit_score"], { submit() }, enabled = name.trim().isNotEmpty())
            }
        }
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
private fun padDirsAt(pos: Offset, cellPx: Float): Set<PadDir> {
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
            .padding(3.dp)
            .clip(Corner.panel)
            .background(if (pressed) NDColors.Accent else Color(0x8C0D0D12))
            .border(BorderStroke(1.dp, if (pressed) NDColors.Accent else Color.White.copy(alpha = 0.22f)), Corner.panel),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = size.width; val h = size.height
            val p = Path()
            when (dir) {
                PadDir.UP -> { p.moveTo(w / 2, h * 0.1f); p.lineTo(w, h * 0.85f); p.lineTo(0f, h * 0.85f) }
                PadDir.DOWN -> { p.moveTo(0f, h * 0.15f); p.lineTo(w, h * 0.15f); p.lineTo(w / 2, h * 0.9f) }
                PadDir.LEFT -> { p.moveTo(w * 0.85f, 0f); p.lineTo(w * 0.85f, h); p.lineTo(w * 0.1f, h / 2) }
                PadDir.RIGHT -> { p.moveTo(w * 0.15f, 0f); p.lineTo(w * 0.9f, h / 2); p.lineTo(w * 0.15f, h) }
            }
            p.close()
            drawPath(p, if (pressed) NDColors.OnAccent else Color.White.copy(alpha = 0.85f))
        }
    }
}
