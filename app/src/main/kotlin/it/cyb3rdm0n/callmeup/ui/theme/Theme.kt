package it.cyb3rdm0n.callmeup.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Verde campo desaturato: la stessa identita del documento di progetto.
private val VerdeCampo = Color(0xFF2F6B47)
private val VerdeChiaro = Color(0xFF64B383)
private val Gesso = Color(0xFFF1F4F0)

private val Chiaro = lightColorScheme(
    primary = VerdeCampo,
    secondary = VerdeChiaro,
    background = Gesso,
)

private val Scuro = darkColorScheme(
    primary = VerdeChiaro,
    secondary = VerdeCampo,
    background = Color(0xFF10140F),
)

@Composable
fun CallMeUpTheme(
    scuro: Boolean = isSystemInDarkTheme(),
    coloriDinamici: Boolean = true,
    content: @Composable () -> Unit,
) {
    val schema = when {
        coloriDinamici && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (scuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        scuro -> Scuro
        else -> Chiaro
    }
    MaterialTheme(colorScheme = schema, content = content)
}
