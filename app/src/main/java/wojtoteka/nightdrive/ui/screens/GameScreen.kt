package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.engine.GamePhase
import wojtoteka.nightdrive.net.SubmitState
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.HudBanner
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
import kotlin.math.max
import kotlin.math.min

/** Same cyan as the near-miss sparks on the road, so the tag reads as their label. */
private val NearMissColor = Color(0xFF37E7FF)

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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        SteerSurface(vm, enabled = playing, modifier = Modifier.fillMaxSize())

        // At rest the joystick sits just above the road's near neon line instead of straddling it.
        val restBottom = (roadEdgeFromBottom(maxWidth.value, maxHeight.value) + 12f).coerceAtLeast(14f).dp
        val restStart = with(LocalDensity.current) {
            WindowInsets.safeDrawing.getLeft(this, LocalLayoutDirection.current).toDp()
        } + 24.dp

        if (!vm.hudHidden) Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            HudLeft(vm, Modifier.align(Alignment.TopStart))
            if (hud.phase == GamePhase.PLAYING) {
                SquareIconButton({ vm.togglePause() }, Modifier.align(Alignment.TopEnd)) { PauseGlyph() }
            }
            // Up in the sky, left of the pause button, so it never covers the road.
            PowerUpPanel(hud.powerups, Modifier.align(Alignment.TopEnd).padding(end = 44.dp + 10.dp))
            // Below the score row, over the sun: announcements never sit on the lanes.
            Banner(hud.banner, hud.bannerVisible, Modifier.align(Alignment.TopCenter).padding(top = 76.dp))
        }

        if (!vm.hudHidden && playing) {
            // The whole left half is the joystick's touch zone.
            Joystick(vm, restStart, restBottom, Modifier.fillMaxHeight().fillMaxWidth(0.5f))
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
            if (hud.nearMissFlash > 0) Tag("${s["near_miss"]} +${hud.nearMissFlash}", color = NearMissColor, filled = true)
        }
        // The unfinished daily mission closest to done, live as it moves.
        hud.mission?.let { m ->
            Spacer(Modifier.height(8.dp))
            Text(
                "${s["mission"].uppercase()} · ${s[m.type.shortKey].uppercase()}  ${m.progress}/${m.goal}",
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
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (pu in powerups) PowerUpChip(pu)
    }
}

/** Compact active power-up: its glyph (same art as the pickup on the road), time or charges left
 * and a bar - no name, so all five can sit side by side in the sky strip. */
@Composable
private fun PowerUpChip(pu: HudPowerUp) {
    val color = Color(pu.type.color)
    Panel(Modifier.width(92.dp), edge = color) {
        Column(Modifier.padding(start = 10.dp, end = 9.dp, top = 7.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PowerUpGlyph(pu.type, 18)
                Spacer(Modifier.weight(1f))
                Text(
                    if (pu.isCharge) "×${pu.chargeCount}" else "${pu.remainingSeconds}s",
                    style = NDType.Overline,
                    color = color,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
            if (pu.isCharge) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(pu.chargeCount.coerceAtMost(6)) {
                        Box(Modifier.size(width = 8.dp, height = 3.dp).background(color))
                    }
                }
            } else {
                Bar(pu.fraction, color)
            }
        }
    }
}

@Composable
private fun Banner(banner: HudBanner?, visible: Boolean, modifier: Modifier) {
    val s = LocalStrings.current
    AnimatedVisibility(
        visible = visible && banner != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut(),
    ) {
        val b = banner ?: return@AnimatedVisibility
        val color = Color(b.color)
        Panel(fill = NDColors.PanelSolid, edge = color) {
            Column(
                Modifier.padding(start = 20.dp, end = 22.dp, top = 9.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val title = if (b.titleArg.isEmpty()) s[b.titleKey] else "${s[b.titleKey]} ${b.titleArg}"
                Text(title.uppercase(), style = NDType.Title, color = color)
                Spacer(Modifier.height(2.dp))
                Text(s[b.subtitleKey], style = NDType.Caption, color = NDColors.TextDim)
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
 * Floating joystick, Brawl Stars style. It rests bottom-left, but a touch anywhere in its zone (the
 * left half of the screen) moves the base under the finger and the knob follows the drag (clamped
 * to the rim); the offset turns into the engine's digital directions, one per axis past
 * [JOY_THRESHOLD] of the radius - so diagonals come for free as an 8-way stick. Multi-touch safe
 * (it tracks the finger that went down on it), and while it's held its input takes priority over
 * the slide-to-steer surface, same as the old pad.
 */
@Composable
private fun Joystick(vm: GameViewModel, restStart: Dp, restBottom: Dp, modifier: Modifier = Modifier) {
    val base = 150.dp
    val knob = 58.dp
    var center by remember { mutableStateOf<Offset?>(null) }   // null = resting
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier.pointerInput(Unit) {
            val baseR = base.toPx() / 2f
            val radius = (base - knob).toPx() / 2f
            var held = emptySet<PadDir>()
            fun apply(dirs: Set<PadDir>) {
                (held - dirs).forEach { vm.setPad(it, false) }
                (dirs - held).forEach { vm.setPad(it, true) }
                held = dirs
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                // Keep the whole base on screen when the touch lands near an edge.
                val c = Offset(
                    down.position.x.coerceIn(baseR, max(baseR, size.width - baseR)),
                    down.position.y.coerceIn(baseR, max(baseR, size.height - baseR)),
                )
                center = c
                var pos = down.position
                while (true) {
                    val raw = pos - c
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
                center = null
            }
        },
    ) {
        val active = center != null
        val r = base.toPx() / 2f
        val c = center ?: Offset(restStart.toPx() + r, size.height - restBottom.toPx() - r)
        // Base: translucent white disc, white rim and a cross of ticks marking the four axes.
        drawCircle(Color.White.copy(alpha = if (active) 0.16f else 0.10f), r, c)
        drawCircle(
            Color.White.copy(alpha = if (active) 0.85f else 0.6f),
            r - 0.75.dp.toPx(), c,
            style = Stroke(1.5.dp.toPx()),
        )
        val tick = 7.dp.toPx(); val inset = 9.dp.toPx()
        val tc = Color.White.copy(alpha = 0.55f); val sw = 1.5.dp.toPx()
        drawLine(tc, Offset(c.x, c.y - r + inset), Offset(c.x, c.y - r + inset + tick), sw)
        drawLine(tc, Offset(c.x, c.y + r - inset), Offset(c.x, c.y + r - inset - tick), sw)
        drawLine(tc, Offset(c.x - r + inset, c.y), Offset(c.x - r + inset + tick, c.y), sw)
        drawLine(tc, Offset(c.x + r - inset, c.y), Offset(c.x + r - inset - tick, c.y), sw)
        // Knob: solid white with a dark hairline so it still reads over the sun.
        val k = c + knobOffset
        val kr = knob.toPx() / 2f
        drawCircle(Color.White.copy(alpha = if (active) 0.95f else 0.8f), kr, k)
        drawCircle(Color.Black.copy(alpha = 0.3f), kr - 0.5.dp.toPx(), k, style = Stroke(1.dp.toPx()))
    }
}

private const val JOY_THRESHOLD = 0.38f
