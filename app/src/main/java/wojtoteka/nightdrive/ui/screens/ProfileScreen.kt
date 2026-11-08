package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.net.ClaimResult
import wojtoteka.nightdrive.net.PlayerRank
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.components.NickField
import wojtoteka.nightdrive.ui.components.Overline
import wojtoteka.nightdrive.ui.components.Panel
import wojtoteka.nightdrive.ui.components.PrimaryButton
import wojtoteka.nightdrive.ui.components.Readout
import wojtoteka.nightdrive.ui.components.ScreenHeader
import wojtoteka.nightdrive.ui.components.VRule
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.Space

/** The nickname that scores go to the rankings under, and where that nickname stands. */
@Composable
fun ProfileScreen(vm: GameViewModel) {
    val s = LocalStrings.current
    val p = vm.profileState
    BackHandler { vm.goTo(Destination.MENU) }
    LaunchedEffect(Unit) {
        vm.clearClaimResult()
        vm.refreshProfileRanks()
    }
    var name by remember { mutableStateOf(p.profileNick.ifEmpty { vm.lastNickname }) }
    val canSave = name.trim().isNotEmpty() && name.trim() != p.profileNick && !vm.claimInProgress
    // The answer shows under the field, where an open keyboard would hide it.
    val keyboard = LocalSoftwareKeyboardController.current
    val save = {
        if (canSave) {
            keyboard?.hide()
            vm.saveProfile(name)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = Space.gutter, vertical = 14.dp),
    ) {
        ScreenHeader(title = s["profile"], onBack = { vm.goTo(Destination.MENU) }, backLabel = s["back"])
        Spacer(Modifier.height(Space.section))
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState()),
        ) {
            Panel(Modifier.fillMaxWidth(), edge = if (p.profileNick.isNotEmpty()) NDColors.Accent else null) {
                Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)) {
                    Overline(s["profile_nick"])
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NickField(name, { name = it; vm.clearClaimResult() }, { save() }, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton(s["profile_save"], { save() }, enabled = canSave)
                    }
                    claimMessage(vm)?.let { (key, color) ->
                        Spacer(Modifier.height(8.dp))
                        Overline(s[key], color = color)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(s["profile_about"], style = NDType.Caption, color = NDColors.TextDim)
                }
            }
            if (p.profileNick.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp).height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Readout(s["high_score_label"], p.highScore.toString(), Modifier.weight(1f), small = true)
                        VRule(30.dp)
                        Spacer(Modifier.width(14.dp))
                        Readout(s["leaderboard"], place(vm.profileRanks.first), Modifier.weight(1f), small = true)
                        VRule(30.dp)
                        Spacer(Modifier.width(14.dp))
                        Readout(s["board_weekly"], place(vm.profileRanks.second), Modifier.weight(1f), small = true)
                    }
                }
            }
        }
    }
}

private fun place(r: PlayerRank?): String = r?.let { "#${it.rank}${if (it.capped) "+" else ""}" } ?: "-"

@Composable
private fun claimMessage(vm: GameViewModel) = when {
    vm.claimInProgress -> "submitting" to NDColors.TextDim
    else -> when (vm.claimResult) {
        is ClaimResult.Ok -> "profile_saved" to NDColors.Positive
        ClaimResult.Taken -> "name_taken" to NDColors.Danger
        ClaimResult.Invalid -> "profile_invalid" to NDColors.Danger
        ClaimResult.Unverified -> "submit_unverified" to NDColors.Danger
        ClaimResult.UpdateRequired -> "update_required" to NDColors.Danger
        ClaimResult.Failed -> "profile_failed" to NDColors.Danger
        null -> null
    }
}
