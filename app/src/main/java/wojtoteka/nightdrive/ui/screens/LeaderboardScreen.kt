package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.net.LeaderboardEntry
import wojtoteka.nightdrive.net.LeaderboardState
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.components.Overline
import wojtoteka.nightdrive.ui.components.Panel
import wojtoteka.nightdrive.ui.components.Rule
import wojtoteka.nightdrive.ui.components.ScreenHeader
import wojtoteka.nightdrive.ui.components.SecondaryButton
import wojtoteka.nightdrive.ui.components.StateMessage
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType
import wojtoteka.nightdrive.ui.theme.Space

@Composable
fun LeaderboardScreen(vm: GameViewModel) {
    val s = LocalStrings.current
    BackHandler { vm.goTo(Destination.MENU) }
    LaunchedEffect(Unit) { vm.fetchLeaderboard() }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.gutter, vertical = 14.dp),
    ) {
        ScreenHeader(
            title = s["leaderboard"],
            onBack = { vm.goTo(Destination.MENU) },
            backLabel = s["back"],
            trailing = { Overline(s["top10_title"], color = NDColors.TextDim) },
        )
        Spacer(Modifier.height(Space.section))

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val st = vm.leaderboardState) {
                is LeaderboardState.Loading -> Overline(s["submitting"], color = NDColors.TextDim)
                is LeaderboardState.Offline -> StateMessage(s["leaderboard_offline"]) {
                    SecondaryButton(s["retry"], { vm.fetchLeaderboard() })
                }
                is LeaderboardState.Error -> StateMessage(s["leaderboard_error"]) {
                    SecondaryButton(s["retry"], { vm.fetchLeaderboard() })
                }
                is LeaderboardState.Success ->
                    if (st.entries.isEmpty()) StateMessage(s["leaderboard_empty"])
                    else Board(st.entries)
            }
        }
    }
}

/** Timing-tower layout: two columns of five, one panel each, rows split by hairlines. */
@Composable
private fun Board(entries: List<LeaderboardEntry>) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (col in listOf(entries.take(5), entries.drop(5).take(5))) {
            Panel(Modifier.weight(1f)) {
                Column {
                    col.forEachIndexed { i, e ->
                        if (i > 0) Rule()
                        RankRow(e)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankRow(e: LeaderboardEntry) {
    val leader = e.rank == 1
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            e.rank.toString().padStart(2, '0'),
            style = NDType.ReadoutSmall,
            color = if (leader) NDColors.Accent else NDColors.TextFaint,
            modifier = Modifier.width(34.dp),
        )
        Text(
            e.name,
            style = NDType.Label,
            color = NDColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(e.score.toString(), style = NDType.ReadoutSmall, color = if (leader) NDColors.Accent else NDColors.Text)
    }
}
