package wojtoteka.nightdrive.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wojtoteka.nightdrive.ui.theme.Corner
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType

/**
 * True only when [focused] came from D-pad/keyboard navigation, not a touch tap. Android hands
 * default keyboard focus to the first focusable element on screen even in touch mode, which would
 * otherwise draw a focus ring nobody asked for on a plain phone tap.
 */
@Composable
fun isNavFocus(focused: Boolean): Boolean =
    focused && LocalInputModeManager.current.inputMode != InputMode.Touch

/** White outline for D-pad / keyboard focus - the one focus treatment used everywhere. */
fun Modifier.focusRing(show: Boolean, shape: androidx.compose.ui.graphics.Shape = Corner.control): Modifier =
    if (show) border(BorderStroke(2.dp, Color.White), shape) else this

/** Logo text with its original soft glow. Used for the wordmark only. */
@Composable
fun GlowText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    glow: Color = color,
    glowRadius: Float = 24f,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.copy(shadow = Shadow(glow.copy(alpha = 0.55f), Offset.Zero, glowRadius)),
    )
}

/** Small uppercase eyebrow label. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = NDColors.TextFaint) {
    Text(text.uppercase(), style = NDType.Overline, color = color, modifier = modifier, maxLines = 1)
}

/**
 * Flat panel - the basic surface. [edge] paints a 3dp bar down the left side, which is how a panel
 * says "this one is active / selected / done" instead of glowing borders.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    edge: Color? = null,
    fill: Color = NDColors.Panel,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(Corner.panel)
            .background(fill)
            .then(
                if (edge != null) Modifier.drawBehind {
                    drawRect(edge, Offset.Zero, Size(3.dp.toPx(), size.height))
                } else Modifier
            ),
        content = content,
    )
}

/** Hairline rule. */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = NDColors.Line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Vertical hairline for separating readouts in a row. */
@Composable
fun VRule(height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).height(height).background(NDColors.Line))
}

/** The one primary action per screen: solid accent block, dark condensed caps, trailing arrow. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val nav = isNavFocus(focused)
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(Corner.control)
            .background(if (pressed) NDColors.AccentPressed else NDColors.Accent)
            .focusRing(nav)
            .clickable(interaction, null, enabled = enabled, onClick = onClick)
            .heightIn(min = if (large) 60.dp else 46.dp)
            .padding(horizontal = if (large) 24.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text.uppercase(),
            style = if (large) NDType.Button.copy(fontSize = 22.sp) else NDType.Button,
            color = NDColors.OnAccent,
            maxLines = 1,
        )
        Spacer(Modifier.width(14.dp))
        ArrowGlyph(NDColors.OnAccent, if (large) 18.dp else 13.dp)
    }
}

/** Secondary action: dark block with a hairline, text in [tone]. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: Color = NDColors.Text,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val nav = isNavFocus(focused)
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clip(Corner.control)
            .background(if (pressed) NDColors.PanelRaised else NDColors.PanelSolid)
            .border(BorderStroke(1.dp, if (nav) Color.White else NDColors.LineStrong), Corner.control)
            .clickable(interaction, null, enabled = enabled, onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = NDType.Button, color = tone, maxLines = 1)
    }
}

/** Price action used across the garage: coin + amount on a dark block, accent when affordable. */
@Composable
fun BuyButton(
    price: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prefix: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val nav = isNavFocus(focused)
    Row(
        modifier
            .clip(Corner.control)
            .background(
                when {
                    !enabled -> NDColors.PanelSolid
                    pressed -> NDColors.AccentPressed
                    else -> NDColors.Accent
                }
            )
            .then(if (enabled) Modifier.focusRing(nav) else Modifier.border(BorderStroke(1.dp, NDColors.LineStrong), Corner.control))
            .clickable(interaction, null, enabled = enabled, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) NDColors.OnAccent else NDColors.TextFaint
        if (prefix != null) {
            Text(prefix, style = NDType.Button, color = fg, maxLines = 1)
            Spacer(Modifier.width(10.dp))
        }
        CoinIcon(15, dark = enabled)
        Spacer(Modifier.width(6.dp))
        Text(price.toString(), style = NDType.ReadoutSmall, color = fg, maxLines = 1)
    }
}

/** Square icon button (back, pause, settings) with a drawn glyph. */
@Composable
fun SquareIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = null,
    glyph: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val nav = isNavFocus(focused)
    Box(
        modifier
            .size(size)
            .clip(Corner.control)
            .background(if (pressed) NDColors.PanelRaised else NDColors.Panel)
            .border(BorderStroke(1.dp, if (nav) Color.White else NDColors.Line), Corner.control)
            .clickable(interaction, null, onClick = onClick)
            .then(
                if (contentDescription != null) Modifier.semanticsLabel(contentDescription) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

private fun Modifier.semanticsLabel(label: String): Modifier =
    semantics { contentDescription = label }

/** Label over a big odometer number. */
@Composable
fun Readout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = NDColors.Text,
    small: Boolean = false,
) {
    Column(modifier) {
        Overline(label)
        Spacer(Modifier.height(6.dp))
        Text(value, style = if (small) NDType.ReadoutSmall else NDType.Readout, color = valueColor, maxLines = 1)
    }
}

/**
 * Segmented meter, like the LED bar of a rev counter: [segments] cells, filled up to [fraction].
 * Reads progress at a glance and doesn't look like every other rounded progress bar.
 */
@Composable
fun Meter(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = NDColors.Accent,
    segments: Int = 24,
    height: Dp = 6.dp,
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(400), label = "meter")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = 2.dp.toPx()
        val cell = (size.width - gap * (segments - 1)) / segments
        val lit = f * segments
        for (i in 0 until segments) {
            val x = i * (cell + gap)
            val fill = (lit - i).coerceIn(0f, 1f)
            drawRect(NDColors.LineStrong.copy(alpha = 0.18f), Offset(x, 0f), Size(cell, size.height))
            if (fill > 0f) drawRect(color, Offset(x, 0f), Size(cell * fill, size.height))
        }
    }
}

/** Thin continuous bar - for timers that drain smoothly (power-ups in the HUD). */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        drawRect(Color.White.copy(alpha = 0.1f))
        drawRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
    }
}

/** Small rectangular status tag ("Equipped", "Max", "Record"). */
@Composable
fun Tag(text: String, color: Color = NDColors.Accent, filled: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(Corner.tag)
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text.uppercase(), style = NDType.Overline, color = if (filled) NDColors.OnAccent else color, maxLines = 1)
    }
}

/** Rectangular on/off switch: accent track with a dark knob when on. */
@Composable
fun Toggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val nav = isNavFocus(focused)
    val x by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "toggle")
    Box(
        Modifier
            .size(width = 46.dp, height = 24.dp)
            .clip(Corner.control)
            .background(if (checked) NDColors.Accent else NDColors.PanelRaised)
            .border(BorderStroke(1.dp, if (nav) Color.White else NDColors.LineStrong), Corner.control)
            .clickable(interaction, null) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = x * 22.dp.toPx() }
                .size(18.dp)
                .clip(Corner.tag)
                .background(if (checked) NDColors.OnAccent else NDColors.TextDim),
        )
    }
}

/** Centered message with optional action (empty / offline states). */
@Composable
fun StateMessage(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = NDType.Heading, color = NDColors.TextDim, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
