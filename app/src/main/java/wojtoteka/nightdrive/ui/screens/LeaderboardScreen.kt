package wojtoteka.nightdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.net.LeaderboardEntry
import wojtoteka.nightdrive.net.LeaderboardState
import wojtoteka.nightdrive.ui.Destination
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.components.GhostButton
import wojtoteka.nightdrive.ui.components.GlowText
import wojtoteka.nightdrive.ui.components.NDCard
import wojtoteka.nightdrive.ui.components.ScreenHeader
import wojtoteka.nightdrive.ui.theme.LocalStrings
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDType

@Composable
fun LeaderboardScreen(vm: GameViewModel) {
    val s = LocalStrings.current
    BackHandler { vm.goTo(Destination.MENU) }
    LaunchedEffect(Unit) { vm.fetchLeaderboard() }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        ScreenHeader(
            title = s["leaderboard"],
            onBack = { vm.goTo(Destination.MENU) },
            accent = NDColors.Gold,
        )
        Spacer(Modifier.height(14.dp))

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val st = vm.leaderboardState) {
                is LeaderboardState.Loading -> Text(s["submitting"], style = NDType.Heading, color = NDColors.TextMuted)
                is LeaderboardState.Offline -> Message(s["leaderboard_offline"], s["retry"]) { vm.fetchLeaderboard() }
                is LeaderboardState.Error -> Message(s["leaderboard_error"], s["retry"]) { vm.fetchLeaderboard() }
                is LeaderboardState.Success ->
                    if (st.entries.isEmpty()) Text(s["leaderboard_empty"], style = NDType.Heading, color = NDColors.TextMuted)
                    else Board(st.entries)
            }
        }
    }
}

@Composable
private fun Message(text: String, retry: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = NDType.Heading, color = NDColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        GhostButton(retry, onRetry, color = NDColors.Cyan)
    }
}

@Composable
private fun Board(entries: List<LeaderboardEntry>) {
    val left = entries.take(5)
    val right = entries.drop(5).take(5)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            left.forEach { RankRow(it) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            right.forEach { RankRow(it) }
        }
    }
}

@Composable
private fun RankRow(e: LeaderboardEntry) {
    val color = when (e.rank) {
        1 -> NDColors.Gold
        2 -> Color(0xFFC7CEE6)
        3 -> Color(0xFFD08A4A)
        else -> NDColors.TextMuted
    }
    val podium = e.rank <= 3
    NDCard(borderColor = if (podium) color.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${e.rank}", style = NDType.Label, color = color)
            }
            Spacer(Modifier.width(12.dp))
            Text(e.name, style = NDType.Label, color = NDColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            GlowText(e.score.toString(), NDType.ScoreSmall, color = color, glow = color, glowRadius = 10f)
        }
    }
}
