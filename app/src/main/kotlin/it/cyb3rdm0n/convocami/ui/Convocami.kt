package it.cyb3rdm0n.convocami.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.cyb3rdm0n.convocami.ui.theme.Ambra
import it.cyb3rdm0n.convocami.ui.theme.Erba
import it.cyb3rdm0n.convocami.ui.theme.Erba2
import it.cyb3rdm0n.convocami.ui.theme.Fumo
import it.cyb3rdm0n.convocami.ui.theme.Fumo2
import it.cyb3rdm0n.convocami.ui.theme.Gesso
import it.cyb3rdm0n.convocami.ui.theme.Linea
import it.cyb3rdm0n.convocami.ui.theme.Notte
import it.cyb3rdm0n.convocami.ui.theme.Rosso
import it.cyb3rdm0n.convocami.ui.theme.Verde

enum class Destinazione { Accesso, Gruppi, Partite }

enum class StatoContenuto { Contenuto, Caricamento, Errore, Vuoto }

private val FormaScheda = RoundedCornerShape(12.dp)
private val SfondoStadio = Brush.radialGradient(
    colors = listOf(Color(0xFF183024), Notte),
    center = Offset(450f, -80f),
    radius = 950f,
)

@Composable
fun Convocami(destinazione: Destinazione, vaiA: (Destinazione) -> Unit) {
    Box(Modifier.fillMaxSize().background(SfondoStadio)) {
        when (destinazione) {
            Destinazione.Accesso -> Accesso { vaiA(Destinazione.Gruppi) }
            Destinazione.Gruppi -> SelezioneGruppo(
                stato = StatoContenuto.Contenuto,
                apriGruppo = { vaiA(Destinazione.Partite) },
                indietro = { vaiA(Destinazione.Accesso) },
            )
            Destinazione.Partite -> ListaPartite(
                stato = StatoContenuto.Contenuto,
                indietro = { vaiA(Destinazione.Gruppi) },
            )
        }
    }
}

@Composable
private fun Accesso(continua: () -> Unit) {
    SchermataScorrevole {
        Spacer(Modifier.height(44.dp))
        Marchio()
        Spacer(Modifier.height(46.dp))
        Occhiello("IL TUO CALCETTO. IN CAMPO.")
        Text("CONVOCA.\nGIOCA.\nRICORDA.", style = MaterialTheme.typography.displayMedium)
        Text(
            "Partite, presenze e squadre: tutta la comitiva in un solo posto.",
            color = Fumo,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 14.dp, bottom = 22.dp),
        )
        Campo(titolo = "EMAIL", segreto = false, tipo = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        Campo(titolo = "PASSWORD", segreto = true)
        Spacer(Modifier.height(22.dp))
        AzionePrimaria("ENTRA IN SPOGLIATOIO", continua)
        TextButton(onClick = {}, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("È LA PRIMA VOLTA CHE ENTRO", style = MaterialTheme.typography.labelMedium, color = Fumo)
        }
        Spacer(Modifier.weight(1f))
        Text("CONVOCAMI  /  STAGIONE 26", style = MaterialTheme.typography.labelSmall, color = Fumo2)
    }
}

@Composable
private fun Campo(titolo: String, segreto: Boolean, tipo: KeyboardType = KeyboardType.Text) {
    var testo by rememberSaveable { mutableStateOf("") }
    Column {
        Text(titolo, style = MaterialTheme.typography.labelMedium, color = Fumo2)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = testo,
            onValueChange = { testo = it },
            singleLine = true,
            placeholder = { Text(if (segreto) "Almeno 8 caratteri" else "nome@esempio.it") },
            visualTransformation = if (segreto) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (segreto) KeyboardType.Password else tipo),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Erba2, unfocusedContainerColor = Erba2,
                focusedBorderColor = Verde, unfocusedBorderColor = Linea,
                cursorColor = Verde, focusedTextColor = Gesso, unfocusedTextColor = Gesso,
                focusedPlaceholderColor = Fumo2, unfocusedPlaceholderColor = Fumo2,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SelezioneGruppo(stato: StatoContenuto, apriGruppo: () -> Unit, indietro: () -> Unit) {
    SchermataScorrevole {
        Testata("I TUOI GRUPPI", "Scegli lo spogliatoio", indietro)
        when (stato) {
            StatoContenuto.Caricamento -> StatoCaricamento("PREPARIAMO GLI SPOGLIATOI")
            StatoContenuto.Errore -> StatoMessaggio(true, "CONNESSIONE PERSA", "Non riusciamo a recuperare i tuoi gruppi.", "RIPROVA")
            StatoContenuto.Vuoto -> StatoMessaggio(false, "NESSUN GRUPPO", "Crea una comitiva o entra con il codice invito.", "CREA UN GRUPPO")
            StatoContenuto.Contenuto -> {
                SchedaGruppo("BICOCCA REGION", "Milano Nord", "8 giocatori attivi", "BR", apriGruppo)
                SchedaGruppo("I SOLITI DEL GIOVEDÌ", "Centro sportivo Aurora", "12 giocatori attivi", "SG", apriGruppo)
                Spacer(Modifier.height(10.dp))
                AzioneSecondaria("+  ENTRA CON UN CODICE") {}
            }
        }
    }
}

@Composable
private fun SchedaGruppo(nome: String, zona: String, dettaglio: String, iniziali: String, apri: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(FormaScheda)
            .background(Brush.linearGradient(listOf(Erba2, Erba))).border(1.dp, Linea, FormaScheda)
            .clickable(role = Role.Button, onClick = apri).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(iniziali)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(nome, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(zona.uppercase(), style = MaterialTheme.typography.labelSmall, color = Verde)
            Text(dettaglio, style = MaterialTheme.typography.bodySmall, color = Fumo)
        }
        Text("›", style = MaterialTheme.typography.headlineMedium, color = Fumo)
    }
}

@Composable
fun ListaPartite(stato: StatoContenuto, indietro: () -> Unit) {
    SchermataScorrevole {
        Testata("BICOCCA REGION", "Prossime partite", indietro)
        when (stato) {
            StatoContenuto.Caricamento -> StatoCaricamento("AGGIORNIAMO IL CALENDARIO")
            StatoContenuto.Errore -> StatoMessaggio(true, "FUORI GIOCO", "Le partite non sono disponibili in questo momento.", "RIPROVA")
            StatoContenuto.Vuoto -> StatoMessaggio(false, "CAMPO LIBERO", "Nessuna partita in programma. Aprine una e chiama la squadra.", "APRI UNA PARTITA")
            StatoContenuto.Contenuto -> {
                SchedaPartita("DOMANI · 21:00", "CENTRO SPORTIVO AURORA", "8 VS 8", 13, 16, "2 POSTI, POI RISERVA", "CI SEI", true)
                SchedaPartita("GIO 24 SET · 20:30", "SPORTING BICOCCA", "7 VS 7", 14, 14, "LISTA CONVOCATI CHIUSA", "AL COMPLETO", false)
                Spacer(Modifier.height(10.dp))
                AzionePrimaria("APRI UNA PARTITA") {}
            }
        }
    }
}

@Composable
private fun SchedaPartita(quando: String, campo: String, formato: String, presenti: Int, posti: Int, nota: String, badge: String, aperta: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(FormaScheda)
            .background(Brush.linearGradient(listOf(Erba2, Erba))).border(1.dp, Linea, FormaScheda)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Occhiello(quando)
            Spacer(Modifier.weight(1f))
            Badge(badge, if (aperta) Verde else Fumo)
        }
        Text(campo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        Text("$formato  ·  € 7,50 A TESTA", style = MaterialTheme.typography.labelSmall, color = Fumo)
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.Bottom) {
            Text("$presenti", style = MaterialTheme.typography.displayMedium, color = if (aperta) Verde else Gesso)
            Text(" / $posti", style = MaterialTheme.typography.titleLarge, color = Fumo, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            Text("CONVOCATI", style = MaterialTheme.typography.labelSmall, color = Fumo2, modifier = Modifier.padding(bottom = 5.dp))
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFF1A2620))) {
            Box(Modifier.fillMaxWidth(presenti.toFloat() / posti).height(6.dp).background(if (aperta) Verde else Fumo2))
        }
        Text(nota, style = MaterialTheme.typography.labelSmall, color = if (aperta) Verde else Fumo, modifier = Modifier.padding(top = 9.dp))
    }
}

@Composable
private fun StatoCaricamento(testo: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Verde, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        Text(testo, style = MaterialTheme.typography.labelSmall, color = Fumo, modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
private fun StatoMessaggio(errore: Boolean, titolo: String, testo: String, azione: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp).background(Erba, FormaScheda)
            .border(1.dp, if (errore) Rosso.copy(alpha = .6f) else Linea, FormaScheda).padding(22.dp),
    ) {
        IconaTattica(errore)
        Text(titolo, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp))
        Text(testo, style = MaterialTheme.typography.bodyLarge, color = Fumo, modifier = Modifier.padding(vertical = 8.dp, horizontal = 0.dp))
        if (errore) Text("ERRORE DI RETE", style = MaterialTheme.typography.labelSmall, color = Rosso)
        else Text("0 PARTITE", style = MaterialTheme.typography.labelSmall, color = Ambra)
        Spacer(Modifier.height(22.dp))
        AzioneSecondaria(azione) {}
    }
}

@Composable
private fun IconaTattica(errore: Boolean) {
    Canvas(Modifier.size(42.dp).semantics { contentDescription = if (errore) "Errore" else "Calendario vuoto" }) {
        val colore = if (errore) Rosso else Verde
        drawCircle(colore.copy(alpha = .12f))
        drawCircle(colore, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        if (errore) {
            drawLine(colore, Offset(size.width * .35f, size.height * .35f), Offset(size.width * .65f, size.height * .65f), 2.dp.toPx())
            drawLine(colore, Offset(size.width * .65f, size.height * .35f), Offset(size.width * .35f, size.height * .65f), 2.dp.toPx())
        } else {
            val p = Path().apply { moveTo(size.width*.28f,size.height*.55f); lineTo(size.width*.44f,size.height*.7f); lineTo(size.width*.74f,size.height*.34f) }
            drawPath(p, colore, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun Testata(occhiello: String, titolo: String, indietro: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = indietro, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text("‹", style = MaterialTheme.typography.displaySmall, color = Gesso) }
        Column {
            Occhiello(occhiello)
            Text(titolo.uppercase(), style = MaterialTheme.typography.displaySmall)
        }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable private fun Marchio() = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(11.dp).background(Verde, RoundedCornerShape(2.dp)))
    Spacer(Modifier.width(9.dp))
    Text("CONVOCAMI", style = MaterialTheme.typography.titleLarge)
}

@Composable private fun Occhiello(testo: String) = Text(testo, style = MaterialTheme.typography.labelMedium, color = Verde)

@Composable private fun Avatar(testo: String) = Box(
    Modifier.size(48.dp).background(Color(0xFF182B21), CircleShape).border(1.dp, Color(0xFF2A4435), CircleShape),
    contentAlignment = Alignment.Center,
) { Text(testo, style = MaterialTheme.typography.labelLarge, color = Verde) }

@Composable private fun Badge(testo: String, colore: Color) = Text(
    testo, style = MaterialTheme.typography.labelSmall, color = colore,
    modifier = Modifier.border(1.dp, colore.copy(alpha = .45f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
)

@Composable private fun AzionePrimaria(testo: String, azione: () -> Unit) = Button(
    onClick = azione,
    shape = RoundedCornerShape(12.dp),
    colors = ButtonDefaults.buttonColors(containerColor = Verde, contentColor = Color(0xFF04210F)),
    modifier = Modifier.fillMaxWidth().height(52.dp),
) { Text(testo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }

@Composable private fun AzioneSecondaria(testo: String, azione: () -> Unit) = Button(
    onClick = azione,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, Linea),
    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Gesso),
    modifier = Modifier.fillMaxWidth().height(50.dp),
) { Text(testo, style = MaterialTheme.typography.labelLarge) }

@Composable private fun SchermataScorrevole(contenuto: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = contenuto,
    )
}
