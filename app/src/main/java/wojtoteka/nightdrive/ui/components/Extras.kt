package wojtoteka.nightdrive.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import wojtoteka.nightdrive.ui.theme.Corner
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType

/**
 * Screen header: back button, condensed caps title with a short accent underline, optional
 * trailing content (usually the coin balance).
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = "Back",
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SquareIconButton(onBack, contentDescription = backLabel) { BackGlyph() }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title.uppercase(), style = NDType.Title, color = NDColors.Text, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Box(Modifier.width(22.dp).height(3.dp).background(NDColors.Accent))
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

/**
 * Navigation row inside a list panel: label, optional right-hand detail, arrow. Pressed and
 * focused states lift the row; there is no ripple.
 */
@Composable
fun NavRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val nav = isNavFocus(focused)
    Row(
        modifier
            .fillMaxWidth()
            .background(if (pressed || nav) NDColors.PanelRaised else Color.Transparent)
            .then(
                if (nav) Modifier.drawBehind {
                    drawRect(NDColors.Accent, Offset.Zero, Size(3.dp.toPx(), size.height))
                } else Modifier
            )
            .clickable(interaction, null, onClick = onClick)
            .heightIn(min = 50.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = NDType.Button, color = NDColors.Text, modifier = Modifier.weight(1f), maxLines = 1)
        if (detail != null) {
            Text(detail, style = NDType.ReadoutSmall, color = NDColors.TextDim, maxLines = 1)
            Spacer(Modifier.width(14.dp))
        }
        ArrowGlyph(NDColors.TextDim, 12.dp)
    }
}

/** A setting: label (+ optional description) on the left, control on the right. Lives in a list panel. */
@Composable
fun SettingRow(
    label: String,
    description: String?,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = NDType.Label, color = NDColors.Text)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, style = NDType.Caption, color = NDColors.TextFaint)
            }
        }
        Spacer(Modifier.width(16.dp))
        control()
    }
}

/** Square-cornered segmented control; the selected segment is filled with the accent. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(Corner.control)
            .border(BorderStroke(1.dp, NDColors.LineStrong), Corner.control),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val nav = isNavFocus(focused)
            if (i > 0) Box(Modifier.width(1.dp).height(34.dp).background(NDColors.LineStrong))
            Box(
                Modifier
                    .background(
                        when {
                            selected -> NDColors.Accent
                            nav -> NDColors.PanelRaised
                            else -> Color.Transparent
                        }
                    )
                    .clickable(interaction, null) { onSelect(i) }
                    .heightIn(min = 34.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(),
                    style = NDType.Overline.copy(fontSize = 12.sp),
                    color = if (selected) NDColors.OnAccent else NDColors.TextDim,
                    modifier = Modifier.padding(vertical = 9.dp),
                )
            }
        }
    }
}

/** Confirmation dialog: flat panel, danger action on the right. */
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
        Panel(fill = NDColors.PanelSolid, edge = NDColors.Danger) {
            Column(Modifier.padding(24.dp).width(340.dp)) {
                Text(title.uppercase(), style = NDType.Title, color = NDColors.Text)
                Spacer(Modifier.height(10.dp))
                Text(message, style = NDType.Body, color = NDColors.TextDim)
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    SecondaryButton(dismissText, onDismiss)
                    SecondaryButton(confirmText, onConfirm, tone = NDColors.Danger)
                }
            }
        }
    }
}
