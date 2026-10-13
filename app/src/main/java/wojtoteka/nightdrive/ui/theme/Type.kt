package wojtoteka.nightdrive.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import wojtoteka.nightdrive.R

/**
 * Three voices, each with one job:
 *  - Edunline for the logo only,
 *  - BitCell for numbers (score, coins, prices) - reads like a digital odometer, and unlike the
 *    earlier Zepto its 8 has open counters, so it can't blur into a solid block at small sizes,
 *  - the system condensed sans for headings and actions, regular sans for everything else.
 */
object NDFonts {
    val Brand = FontFamily(Font(R.font.edunline))
    val Display = FontFamily(Font(R.font.bitcell))
    val Condensed = FontFamily(
        Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold),
        Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Medium),
    )
    val Body = FontFamily.SansSerif
}

object NDType {
    val Brand = TextStyle(fontFamily = NDFonts.Brand, fontSize = 44.sp, letterSpacing = 1.sp)

    // BitCell's digits are ~70% the size of Zepto's at the same sp, hence the larger numbers.
    val Score = TextStyle(fontFamily = NDFonts.Display, fontSize = 56.sp)
    val Readout = TextStyle(fontFamily = NDFonts.Display, fontSize = 34.sp)
    val ReadoutSmall = TextStyle(fontFamily = NDFonts.Display, fontSize = 22.sp)

    val Display = TextStyle(fontFamily = NDFonts.Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = 0.5.sp)
    val Title = TextStyle(fontFamily = NDFonts.Condensed, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 0.6.sp)
    val Heading = TextStyle(fontFamily = NDFonts.Condensed, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.3.sp)
    val Button = TextStyle(fontFamily = NDFonts.Condensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.4.sp)
    val Overline = TextStyle(fontFamily = NDFonts.Condensed, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.8.sp)

    val Label = TextStyle(fontFamily = NDFonts.Body, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    val Body = TextStyle(fontFamily = NDFonts.Body, fontSize = 13.sp, lineHeight = 17.sp)
    val Caption = TextStyle(fontFamily = NDFonts.Body, fontSize = 11.sp, lineHeight = 14.sp)
}
