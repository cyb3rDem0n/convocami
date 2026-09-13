package it.cyb3rdm0n.convocami.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * L'identita di Convocami, la stessa della web app.
 *
 * DUE COSE SONO SPENTE DI PROPOSITO, ed entrambe sono comportamenti che Material
 * 3 accende da solo.
 *
 * 1. I COLORI DINAMICI. Su Android 12+ Material prende i colori dallo sfondo del
 *    telefono: con uno sfondo viola l'app diventa viola. Per un'app di sistema e
 *    una funzione, per un'app con un'identita dichiarata e la fine
 *    dell'identita. Le due squadre si chiamano bianca e nera, la figurina e
 *    dorata: non sono tinte negoziabili con la tappezzeria di chi installa.
 *
 * 2. IL TEMA CHIARO. Non esiste. Non e una dimenticanza: un'app che vive di
 *    schede lucide e numeri grandi su fondo scuro non ha una versione chiara che
 *    regga, e averne una a meta sarebbe peggio che non averla. Stessa scelta
 *    della web app.
 *
 * I valori vengono da design/tokens.json, che e la fonte unica per i due client.
 * Se cambi un colore qui, cambialo anche li e in web/style.css.
 */

// Fondali
private val Notte = Color(0xFF070B08)   // nero con deriva verde
private val Erba = Color(0xFF0E1712)    // superfici
private val Erba2 = Color(0xFF14201A)   // superfici sollevate
private val Linea = Color(0xFF1E2C24)   // bordi

// Accenti
private val Verde = Color(0xFF2BE07A)      // contatori, stati, azione primaria
private val VerdeCupo = Color(0xFF1A8F4F)
private val Oro = Color(0xFFE9C46A)        // solo figurina ed etichette guadagnate

// Testo
private val Gesso = Color(0xFFEDF2EC)
private val Fumo = Color(0xFF8FA396)

// Semantici: separati dall'accento, perche "buono" e "il colore del marchio"
// non sono la stessa informazione
private val Rosso = Color(0xFFD9705F)
private val Ambra = Color(0xFFD79A4A)

/** Le due squadre. Non sono decorazione: sono il sistema di colore della partita. */
val SquadraBianca = Color(0xFFF1F4F0)
val SquadraNera = Color(0xFF10150F)

private val Schema = darkColorScheme(
    primary = Verde,
    onPrimary = Color(0xFF04210F),
    primaryContainer = VerdeCupo,
    onPrimaryContainer = Gesso,

    secondary = Oro,
    onSecondary = Color(0xFF1A1405),

    background = Notte,
    onBackground = Gesso,

    surface = Erba,
    onSurface = Gesso,
    surfaceVariant = Erba2,
    onSurfaceVariant = Fumo,

    outline = Linea,
    outlineVariant = Color(0xFF16221C),

    error = Rosso,
    onError = Color(0xFF2A0B06),
    tertiary = Ambra,
)

@Composable
fun ConvocamiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Schema,
        typography = TipografiaConvocami,
        content = content,
    )
}
