package it.cyb3rdm0n.callmeup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.cyb3rdm0n.callmeup.teamdraw.FORMAZIONI
import it.cyb3rdm0n.callmeup.ui.theme.ConvocamiTheme

/**
 * Schermata segnaposto della fase 0.
 *
 * Serve a una cosa sola ma importante: rendere il progetto compilabile e
 * installabile da subito, cosi la catena `gradlew -> APK -> telefono` si
 * verifica prima di scriverci dentro l'app vera.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ConvocamiTheme { Scaffold { p -> Benvenuto(Modifier.padding(p)) } } }
    }
}

@Composable
private fun Benvenuto(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Convocami", style = MaterialTheme.typography.displaySmall)
        Text(
            "Motore di sorteggio pronto: formati " +
                FORMAZIONI.keys.sorted().joinToString(" e ") { "${it}v$it" },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Fase 0 — l'app vera arriva con le prossime fasi.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
