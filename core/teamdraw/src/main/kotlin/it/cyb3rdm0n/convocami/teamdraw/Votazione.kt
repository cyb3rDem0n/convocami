package it.cyb3rdm0n.convocami.teamdraw

/**
 * Il voto di fine partita.
 *
 * Si vota per FASE DI GIOCO, non per ruolo, e la differenza non e accademica:
 * un difensore puo essere il miglior regista in campo, e chiedere "chi e stato
 * il miglior centrocampista" quel dato lo perderebbe. Le fasi descrivono quello
 * che la gente ha visto, non la casella in cui era schierata.
 *
 * E si vota per NOMINA, non con un punteggio. Dare un voto da 1 a 10 a tredici
 * persone per quattro fasi sono cinquantadue caselle: nessuno le compila dopo
 * la partita, e chi ci prova mette numeri a caso pur di finire. Quattro nomi
 * sono quattro tocchi, e con quattordici votanti bastano e avanzano.
 */
enum class Fase {
    /** Recuperare palla, coprire, chiudere sull'uomo. */
    DIFESA,

    /** Incidere sotto porta: concludere, smarcarsi, segnare. */
    ATTACCO,

    /** Far girare la palla, vedere il passaggio, dettare i tempi. */
    REGIA,

    /** Parare. Votabile solo per chi in porta ci e stato davvero. */
    PORTA;

    /** La domanda che compare nella schermata di voto. */
    val domanda: String
        get() = when (this) {
            DIFESA -> "Chi ha difeso meglio?"
            ATTACCO -> "Chi ha fatto piu male davanti?"
            REGIA -> "Chi ha fatto giocare meglio la squadra?"
            PORTA -> "Chi ha parato meglio?"
        }

    val etichetta: String
        get() = when (this) {
            DIFESA -> "Difesa"
            ATTACCO -> "Attacco"
            REGIA -> "Regia"
            PORTA -> "Porta"
        }

    /** Il ruolo verso cui questa fase sposta la propensione. */
    val ruolo: Ruolo
        get() = when (this) {
            DIFESA -> Ruolo.DIF
            ATTACCO -> Ruolo.ATT
            REGIA -> Ruolo.CEN
            PORTA -> Ruolo.POR
        }
}

/**
 * Una nomina. [votatoId] a null significa "nessuno si e distinto", che dev'essere
 * una risposta legittima: obbligare a scegliere qualcuno produce nomi a caso.
 */
data class Nomina(
    val votanteId: String,
    val fase: Fase,
    val votatoId: String?,
)

/** Quanto ha reso un giocatore in ciascuna fase, da -1 a +1. */
data class RendimentoPartita(
    val giocatoreId: String,
    val perFase: Map<Fase, Double>,
)

object Votazione {

    /**
     * Nessuno vota se stesso, e per la porta si possono nominare solo quelli
     * che ci hanno passato dei minuti.
     */
    fun validaNomina(
        n: Nomina, convocati: Set<String>, chiHaParato: Set<String>,
    ): Boolean {
        val votato = n.votatoId ?: return n.votanteId in convocati
        if (n.votanteId !in convocati || votato !in convocati) return false
        if (votato == n.votanteId) return false
        if (n.fase == Fase.PORTA && votato !in chiHaParato) return false
        return true
    }

    /**
     * Trasforma le nomine in rendimenti.
     *
     * Non ricevere nomine non vuol dire aver giocato male: in ogni fase puo
     * vincerne uno solo. Quindi lo zero non e il fondo della scala, e il
     * riferimento e la quota che toccherebbe a ciascuno se i voti fossero
     * distribuiti a caso.
     */
    fun rendimenti(
        nomine: List<Nomina>,
        convocati: List<String>,
        chiHaParato: Set<String> = emptySet(),
    ): List<RendimentoPartita> {
        require(convocati.isNotEmpty()) { "Nessun convocato" }

        val valide = nomine.filter { validaNomina(it, convocati.toSet(), chiHaParato) }

        return convocati.map { id ->
            val perFase = Fase.entries.associateWith { fase ->
                val votanti = valide.count { it.fase == fase && it.votanteId != id }
                if (votanti == 0) return@associateWith 0.0

                // chi non poteva essere votato in questa fase non viene giudicato
                val candidati = if (fase == Fase.PORTA) chiHaParato.size else convocati.size - 1
                if (fase == Fase.PORTA && id !in chiHaParato) return@associateWith 0.0
                if (candidati <= 1) return@associateWith 0.0

                val ricevute = valide.count { it.fase == fase && it.votatoId == id }
                val quota = ricevute.toDouble() / votanti
                val attesa = 1.0 / candidati

                // quota == attesa -> 0 ; quota == SOGLIA_ECCELLENZA -> +1
                val scala = (SOGLIA_ECCELLENZA - attesa).coerceAtLeast(1e-6)
                ((quota - attesa) / scala).coerceIn(-1.0, 1.0)
            }
            RendimentoPartita(id, perFase)
        }
    }

    /** Quota di nomine oltre la quale il rendimento e al massimo. */
    private const val SOGLIA_ECCELLENZA = 0.40
}

/**
 * Come i voti muovono le sette caratteristiche.
 *
 * Il voto e un segnale per FASE, e ogni fase sa quale attributo alimentare:
 * e questo che rende il sistema onesto. Un voto generico "ha giocato bene" non
 * saprebbe dove andare, e spalmarlo su tutti gli attributi li farebbe muovere
 * sempre insieme fino a renderli decorativi.
 */
object Crescita {

    /**
     * Rendimenti decrescenti: passare da 80 a 81 costa molto piu che da 50 a 51,
     * e scendere da 30 e altrettanto difficile. Senza questo freno, dopo un anno
     * la comitiva e tutta a 99 e i numeri non dicono piu niente.
     */
    private fun muovi(valore: Double, rendimento: Double, peso: Double = 1.0): Double {
        val delta = PASSO * rendimento * peso
        val margine = if (delta >= 0) (99.0 - valore) / 49.0 else (valore - 1.0) / 49.0
        return (valore + delta * margine.coerceIn(0.0, 1.2)).coerceIn(1.0, 99.0)
    }

    private const val PASSO = 1.4

    /**
     * Applica a un giocatore l'esito della sua partita: caratteristiche e profilo.
     *
     * Ogni fase tocca solo cio che le compete. E questo che tiene in piedi le
     * sette voci: se un voto generico le muovesse tutte insieme, dopo una
     * stagione sarebbero sette copie dello stesso numero.
     */
    fun applica(g: Giocatore, r: RendimentoPartita): Giocatore {
        var s = g.skill
        for (fase in Fase.entries) {
            val rend = r.perFase[fase] ?: 0.0
            if (rend == 0.0) continue
            s = when (fase) {
                Fase.DIFESA -> s.copy(difesa = muovi(s.difesa, rend))
                Fase.ATTACCO -> s.copy(tiro = muovi(s.tiro, rend))
                Fase.REGIA -> s.copy(
                    passaggio = muovi(s.passaggio, rend, 0.65),
                    tecnica = muovi(s.tecnica, rend, 0.35),
                )
                Fase.PORTA -> s.copy(parate = muovi(s.parate, rend))
            }
        }
        return g.copy(skill = s, propensione = g.propensione.dopoLaVotazione(r.perFase))
    }
}
