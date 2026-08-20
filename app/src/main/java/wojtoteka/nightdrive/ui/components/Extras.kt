package wojtoteka.nightdrive.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.Radius

/** Standard screen header: back button + title, with optional trailing content. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = NDColors.Cyan,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, onBack, tint = NDColors.TextPrimary, contentDescription = "Back")
        Spacer(Modifier.width(14.dp))
        GlowText(title.uppercase(), NDType.ScreenTitle, color = NDColors.TextPrimary, glow = accent, glowRadius = 14f)
        Spacer(Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

/** A row for a setting: label + description on the left, control on the right. */
@Composable
fun SettingRow(
    label: String,
    description: String?,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    NDCard(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = NDType.Label, color = NDColors.TextPrimary)
                if (description != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(description, style = NDType.BodyMuted, color = NDColors.TextMuted)
                }
            }
            Spacer(Modifier.width(12.dp))
            control()
        }
    }
}

/** Pill segmented control (used for language selection). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = NDColors.Cyan,
) {
    Row(
        modifier
            .clip(Radius.pill)
            .background(Color(0x330D0F22))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), Radius.pill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val navFocused = isNavFocus(focused)
            Box(
                Modifier
                    .clip(Radius.pill)
                    .background(
                        when {
                            selected -> accent.copy(alpha = 0.22f)
                            navFocused -> Color.White.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable(interaction, ripple(color = accent)) { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    style = NDType.Label,
                    color = when {
                        selected -> accent
                        navFocused -> NDColors.TextPrimary
                        else -> NDColors.TextSecondary
                    },
                )
            }
        }
    }
}

/** Confirmation dialog with a title, message and yes/no actions. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NDCard(borderColor = NDColors.Red.copy(alpha = 0.4f)) {
            Column(
                Modifier.padding(22.dp).width(320.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlowText(title, NDType.Heading, color = NDColors.Red, glow = NDColors.Red, glowRadius = 12f)
                Spacer(Modifier.height(12.dp))
                Text(message, style = NDType.Body, color = NDColors.TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton(dismissText, onDismiss, color = NDColors.TextSecondary)
                    NeonButton(confirmText, onConfirm, color = NDColors.Red)
                }
            }
        }
    }
}
