package wojtoteka.nightdrive.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import wojtoteka.nightdrive.R

/**
 * Typography pairs the game's original retro display faces (used for the wordmark, big numbers and
 * headings - they carry the synthwave identity) with the system sans for dense body copy, keeping
 * long text crisp and readable.
 */
object NDFonts {
    val Brand = FontFamily(Font(R.font.edunline))       // wordmark + overlay titles
    val Display = FontFamily(Font(R.font.zepto))         // big numbers, primary actions
    val Ui = FontFamily(Font(R.font.squarewave))         // headings + labels
    val Body = FontFamily.SansSerif                      // descriptions, dense text
}

object NDType {
    val Brand = TextStyle(fontFamily = NDFonts.Brand, fontSize = 44.sp, letterSpacing = 1.sp)
    val ScreenTitle = TextStyle(fontFamily = NDFonts.Ui, fontSize = 26.sp, letterSpacing = 0.5.sp)
    val Score = TextStyle(fontFamily = NDFonts.Display, fontSize = 40.sp)
    val ScoreSmall = TextStyle(fontFamily = NDFonts.Display, fontSize = 22.sp)
    val Heading = TextStyle(fontFamily = NDFonts.Ui, fontSize = 18.sp, letterSpacing = 0.4.sp)
    val Label = TextStyle(fontFamily = NDFonts.Ui, fontSize = 14.sp, letterSpacing = 0.3.sp)
    val LabelSmall = TextStyle(fontFamily = NDFonts.Ui, fontSize = 11.sp, letterSpacing = 0.3.sp)
    val Button = TextStyle(fontFamily = NDFonts.Display, fontSize = 18.sp, letterSpacing = 1.sp)
    val Body = TextStyle(fontFamily = NDFonts.Body, fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val BodyMuted = TextStyle(fontFamily = NDFonts.Body, fontSize = 12.sp)
    val Caption = TextStyle(fontFamily = NDFonts.Body, fontSize = 11.sp, textAlign = TextAlign.Center)
}
