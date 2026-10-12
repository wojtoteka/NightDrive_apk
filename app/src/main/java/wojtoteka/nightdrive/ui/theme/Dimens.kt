package wojtoteka.nightdrive.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corners are barely there on purpose: machined, not bubbly. */
object Corner {
    val panel = RoundedCornerShape(4.dp)
    val control = RoundedCornerShape(3.dp)
    val tag = RoundedCornerShape(2.dp)
}

object Space {
    val gutter = 22.dp
    val section = 18.dp
    val item = 12.dp
}
