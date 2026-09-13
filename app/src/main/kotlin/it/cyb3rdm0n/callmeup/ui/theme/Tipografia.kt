package it.cyb3rdm0n.callmeup.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.cyb3rdm0n.callmeup.R

/**
 * I caratteri sono gli stessi della web app, ma qui vanno IMBARCATI nell'APK:
 * Android non ha una CDN di font. Senza i file in res/font/ il sistema ripiega
 * su Roboto e le due app si somigliano soltanto nei colori — che e il modo piu
 * comune in cui un'identita condivisa si perde per strada.
 *
 * File attesi in app/src/main/res/font/ (scaricabili da fonts.google.com):
 *   archivo_black.ttf
 *   barlow_condensed_semibold.ttf, barlow_condensed_bold.ttf
 *   barlow_regular.ttf, barlow_medium.ttf, barlow_semibold.ttf
 */

val Display = FontFamily(Font(R.font.archivo_black, FontWeight.Black))

val Condensato = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

val Testo = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

val TipografiaCallMeUp = Typography(
    // I numeri grandi: OVR, contatore iscrizioni, forza delle squadre
    displayLarge = TextStyle(fontFamily = Display, fontSize = 46.sp, letterSpacing = (-1.4).sp),
    displayMedium = TextStyle(fontFamily = Display, fontSize = 34.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontSize = 24.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Display, fontSize = 19.sp),

    // Le etichette da tabellone: sigle dei ruoli, maiuscoletto spaziato
    labelLarge = TextStyle(fontFamily = Condensato, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, letterSpacing = 1.6.sp),
    labelMedium = TextStyle(fontFamily = Condensato, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, letterSpacing = 1.4.sp),
    labelSmall = TextStyle(fontFamily = Condensato, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, letterSpacing = 1.2.sp),

    // Tutto il resto
    bodyLarge = TextStyle(fontFamily = Testo, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Testo, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Testo, fontSize = 13.sp, lineHeight = 18.sp),
)
