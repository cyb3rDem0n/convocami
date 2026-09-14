package it.cyb3rdm0n.convocami

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import it.cyb3rdm0n.convocami.ui.Convocami
import it.cyb3rdm0n.convocami.ui.Destinazione
import it.cyb3rdm0n.convocami.ui.theme.ConvocamiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConvocamiTheme {
                var destinazione by rememberSaveable { mutableStateOf(Destinazione.Accesso) }
                Convocami(destinazione = destinazione, vaiA = { destinazione = it })
            }
        }
    }
}
