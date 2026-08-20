package wojtoteka.nightdrive.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import wojtoteka.nightdrive.render.RenderCache
import wojtoteka.nightdrive.render.RetroCanvas
import wojtoteka.nightdrive.render.WorldRenderer
import wojtoteka.nightdrive.ui.GameViewModel
import wojtoteka.nightdrive.ui.theme.NDFonts
import kotlin.math.min

/**
 * Full-bleed Canvas that renders the live game world every frame. Reading [GameViewModel.frameTick]
 * inside the draw scope invalidates only the draw pass (not the composition), so it stays cheap.
 */
@Composable
fun WorldBackground(vm: GameViewModel, modifier: Modifier = Modifier) {
    // Deep layout cache: coin/power-up glyphs re-measure at many quantized sizes every frame,
    // and the default cache of 8 would thrash straight through them.
    val textMeasurer = rememberTextMeasurer(cacheSize = 160)
    val renderer = remember { WorldRenderer(NDFonts.Ui, NDFonts.Ui) }
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
        val rc = RetroCanvas(this, textMeasurer, NDFonts.Ui, renderCache)
        renderer.render(rc, vm.engine, vm.worldAnimMs)
    }
}
