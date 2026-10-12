package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
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

        if (!vm.hudHidden) Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            HudLeft(vm, Modifier.align(Alignment.TopStart))
            if (hud.phase == GamePhase.PLAYING) {
                SquareIconButton({ vm.togglePause() }, Modifier.align(Alignment.TopEnd)) { PauseGlyph() }
                if (!hud.paused) {
                    Joystick(vm, Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp))
                }
            }
            PowerUpPanel(hud.powerups, Modifier.align(Alignment.CenterEnd))
        }

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
    Panel(Modifier.width(196.dp), edge = color) {
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
 * On-screen joystick, bottom-left. Touch anywhere on the base and drag: the knob follows the
 * finger (clamped to the rim) and its offset is turned into the engine's digital directions, one
 * per axis past [JOY_THRESHOLD] of the radius - so diagonals come for free as an 8-way stick.
 * Multi-touch safe (it tracks the finger that went down on it), and while it's held its input
 * takes priority over the slide-to-steer surface, same as the old pad.
 */
@Composable
private fun Joystick(vm: GameViewModel, modifier: Modifier = Modifier) {
    val base = 150.dp
    val knob = 58.dp
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(base)
            .pointerInput(Unit) {
                val radius = (base - knob).toPx() / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                var held = emptySet<PadDir>()
                fun apply(dirs: Set<PadDir>) {
                    (held - dirs).forEach { vm.setPad(it, false) }
                    (dirs - held).forEach { vm.setPad(it, true) }
                    held = dirs
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    active = true
                    var pos = down.position
                    while (true) {
                        val raw = pos - center
                        val len = raw.getDistance()
                        val clamped = if (len > radius && len > 0f) raw * (radius / len) else raw
                        knobOffset = clamped
                        val nx = clamped.x / radius
                        val ny = clamped.y / radius
                        apply(buildSet {
                            if (nx < -JOY_THRESHOLD) add(PadDir.LEFT)
                            if (nx > JOY_THRESHOLD) add(PadDir.RIGHT)
                            if (ny < -JOY_THRESHOLD) add(PadDir.UP)
                            if (ny > JOY_THRESHOLD) add(PadDir.DOWN)
                        })
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        change.consume()
                        pos = change.position
                    }
                    apply(emptySet())
                    knobOffset = Offset.Zero
                    active = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Base: dark disc, hairline rim and a faint cross marking the four axes.
        Canvas(Modifier.size(base)) {
            val r = size.minDimension / 2f
            drawCircle(Color(0x8C0D0D12), r)
            drawCircle(
                if (active) NDColors.Accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.22f),
                r - 0.5.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
            )
            val tick = 7.dp.toPx(); val inset = 9.dp.toPx()
            val tc = Color.White.copy(alpha = 0.3f); val sw = 1.5.dp.toPx()
            drawLine(tc, Offset(r, inset), Offset(r, inset + tick), sw)
            drawLine(tc, Offset(r, size.height - inset), Offset(r, size.height - inset - tick), sw)
            drawLine(tc, Offset(inset, r), Offset(inset + tick, r), sw)
            drawLine(tc, Offset(size.width - inset, r), Offset(size.width - inset - tick, r), sw)
        }
        // Knob
        Box(
            Modifier
                .graphicsLayer { translationX = knobOffset.x; translationY = knobOffset.y }
                .size(knob)
                .clip(CircleShape)
                .background(if (active) NDColors.Accent else Color(0xCC1B1B23))
                .border(BorderStroke(1.dp, if (active) NDColors.Accent else Color.White.copy(alpha = 0.35f)), CircleShape),
        )
    }
}

private const val JOY_THRESHOLD = 0.38f
