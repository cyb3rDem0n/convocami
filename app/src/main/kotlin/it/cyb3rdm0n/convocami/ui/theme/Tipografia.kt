package it.cyb3rdm0n.convocami.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.cyb3rdm0n.convocami.R

/**
 * I caratteri sono gli stessi della web app, ma qui sono IMBARCATI nell'APK:
 * Android non ha una CDN di font. Se sparissero da res/font/ il sistema
 * ripiegherebbe su Roboto e le due app si somiglierebbero soltanto nei colori —
 * che e il modo piu comune in cui un'identita condivisa si perde per strada.
 *
 * I sei .ttf stanno in app/src/main/res/font/ e sono nel repo: sono sotto SIL
 * Open Font License (vedi LICENZE-CARATTERI.txt alla radice), che permette di
 * imbarcarli. In res/ i nomi vanno in minuscolo con trattini bassi, e nella
 * cartella possono vivere SOLO file .ttf/.otf/.ttc/.xml: un LEGGIMI.md li
 * dentro ha gia fermato un build.
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

val TipografiaConvocami = Typography(
    // I numeri grandi: OVR, contatore iscrizioni, forza delle squadre
    displayLarge = TextStyle(fontFamily = Display, fontSize = 46.sp, letterSpacing = (-1.4).sp),
    displayMedium = TextStyle(fontFamily = Display, fontSize = 34.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = Display, fontSize = 28.sp, letterSpacing = (-0.6).sp),
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
