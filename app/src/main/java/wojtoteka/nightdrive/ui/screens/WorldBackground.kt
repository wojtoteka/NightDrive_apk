package wojtoteka.nightdrive.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import wojtoteka.nightdrive.render.ROAD_NEAR_EDGE_Y
import androidx.compose.ui.text.rememberTextMeasurer
import wojtoteka.nightdrive.render.RenderCache
import wojtoteka.nightdrive.render.RetroCanvas
import wojtoteka.nightdrive.render.WorldRenderer
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.theme.NDFonts
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-bleed Canvas that renders the live game world every frame. Reading [GameViewModel.frameTick]
 * inside the draw scope invalidates only the draw pass (not the composition), so it stays cheap.
 */
@Composable
fun WorldBackground(vm: GameViewModel, modifier: Modifier = Modifier) {
    // Deep layout cache: coin/power-up glyphs re-measure at many quantized sizes every frame,
    // and the default cache of 8 would thrash straight through them.
    val textMeasurer = rememberTextMeasurer(cacheSize = 160)
    val renderer = remember { WorldRenderer() }
    val renderCache = remember { RenderCache() }
    Canvas(
        modifier = modifier.onSizeChanged { size ->
            if (size.width > 0 && size.height > 0) {
                val unit = min(size.width, size.height) / 200f
                vm.setViewport((size.width / unit) / 2f)
            }
        }
    ) {
        // Subscribe the draw pass to the frame clock.
        @Suppress("UNUSED_EXPRESSION") vm.frameTick.intValue
        val rc = RetroCanvas(this, textMeasurer, NDFonts.Brand, renderCache)
        renderer.render(rc, vm.engine, vm.worldAnimMs)
    }
}

/**
 * How far the road's near neon line sits above the bottom of a full-screen [width] x [height] box
 * (any unit, as long as both match). Same mapping as the world canvas: 200 world units span the
 * shorter side, centred.
 */
fun roadEdgeFromBottom(width: Float, height: Float): Float =
    height / 2f + ROAD_NEAR_EDGE_Y * (min(width, height) / 200f)

/**
 * Ends the content [gap] above the road's near neon line instead of at the screen's bottom edge,
 * so the line reads as the bottom of the screen with the empty strip of world below it. Apply
 * right after fillMaxSize(), while the constraints are still the whole screen.
 */
fun Modifier.aboveRoadEdge(gap: Dp): Modifier = layout { measurable, constraints ->
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val bottom = (roadEdgeFromBottom(w.toFloat(), h.toFloat()) + gap.toPx()).roundToInt().coerceIn(0, h)
    val placeable = measurable.measure(constraints.offset(vertical = -bottom))
    layout(w, h) { placeable.place(0, 0) }
}
