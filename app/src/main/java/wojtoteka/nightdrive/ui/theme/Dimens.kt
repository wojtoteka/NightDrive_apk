package wojtoteka.nightdrive.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Consistent spacing + radius scale so screens share one visual rhythm. */
object Dp {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val card = RoundedCornerShape(20.dp)
    val chip = RoundedCornerShape(14.dp)
    val button = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(100.dp)
    val small = RoundedCornerShape(10.dp)
}
