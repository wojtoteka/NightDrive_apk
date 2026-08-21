package wojtoteka.nightdrive.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.Radius

/**
 * True only when [focused] came from D-pad/keyboard navigation, not a touch tap. Android hands
 * default keyboard focus to the first focusable element on screen even in touch mode, which would
 * otherwise draw a focus ring nobody asked for on a plain phone tap.
 */
@Composable
fun isNavFocus(focused: Boolean): Boolean =
    focused && LocalInputModeManager.current.inputMode != InputMode.Touch

/** Text with a soft neon glow, achieved with a coloured blur shadow. */
@Composable
fun GlowText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    glow: Color = color,
    glowRadius: Float = 24f,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = align,
        style = style.copy(shadow = Shadow(glow.copy(alpha = 0.55f), Offset.Zero, glowRadius)),
    )
}

/** Frosted, rounded panel that reads clearly over the animated world. */
@Composable
fun NDCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White.copy(alpha = 0.08f),
    fill: Color = Color(0xF70D0F22),
    shape: RoundedCornerShape = Radius.card,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(BorderStroke(1.dp, borderColor), shape),
    ) { content() }
}

/** Primary action button: filled neon gradient with a press-scale. */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NDColors.Cyan,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Focus ring for D-pad / keyboard menu navigation only - never on a plain touch tap.
    val focused by interaction.collectIsFocusedAsState()
    val navFocused = isNavFocus(focused)
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
            .clip(Radius.button)
            // Solid dark base first, so the animated world never shows through the button.
            .background(Color(0xF20B0E20))
            .background(
                Brush.horizontalGradient(
                    listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.10f))
                )
            )
            .border(
                BorderStroke(if (navFocused) 2.5.dp else 1.5.dp, if (navFocused) Color.White else color.copy(alpha = 0.85f)),
                Radius.button,
            )
            .clickable(interaction, ripple(color = color), enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            GlowText(text, NDType.Button, color = Color.White, glow = color, glowRadius = 18f)
        }
    }
}

/** Secondary/ghost button: subtle outline, no fill. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = NDColors.TextSecondary,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val navFocused = isNavFocus(focused)
    Box(
        modifier = modifier
            .clip(Radius.button)
            .border(
                BorderStroke(if (navFocused) 1.5.dp else 1.dp, if (navFocused) Color.White else color.copy(alpha = 0.35f)),
                Radius.button,
            )
            .clickable(interaction, ripple(color = color), onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = NDType.Label, color = color)
        }
    }
}

/** Circular icon button used for back / pause / settings. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NDColors.TextPrimary,
    size: Int = 44,
    contentDescription: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val navFocused = isNavFocus(focused)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(Radius.pill)
            .background(Color(0xB30D0F22))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = if (navFocused) 0.9f else 0.10f)), Radius.pill)
            .clickable(interaction, ripple(color = tint), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

/** Small labelled value chip (coin count, best time, etc.). */
@Composable
fun StatChip(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    NDCard(modifier = modifier, shape = Radius.chip) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
            Column {
                Text(label.uppercase(), style = NDType.LabelSmall, color = NDColors.TextMuted)
                Spacer(Modifier.height(2.dp))
                GlowText(value, NDType.ScoreSmall, color = valueColor, glow = valueColor, glowRadius = 12f)
            }
        }
    }
}

/** Neon toggle switch. */
@Composable
fun NeonSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, color: Color = NDColors.Cyan) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val navFocused = isNavFocus(focused)
    val thumbX by animateFloatAsState(if (checked) 1f else 0f, label = "thumb")
    val track = if (checked) color.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.10f)
    val border = when {
        navFocused -> Color.White
        checked -> color
        else -> Color.White.copy(alpha = 0.20f)
    }
    Box(
        Modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(Radius.pill)
            .background(track)
            .border(BorderStroke(1.5.dp, border), Radius.pill)
            .clickable(interaction, ripple(color = color)) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer { translationX = thumbX * 22.dp.toPx() }
                .size(24.dp)
                .clip(Radius.pill)
                .background(if (checked) color else NDColors.TextMuted),
        )
    }
}

/** Slim neon progress bar (daily challenge / achievement progress). */
@Composable
fun NeonProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Int = 6) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(Radius.pill)
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(Radius.pill)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))),
        )
    }
}
