package it.cyb3rdm0n.callmeup.teamdraw

/**
 * I ruoli di movimento non sono fissi. Il portiere si.
 *
 * Nel calcetto una persona fa l'attaccante una domenica e il difensore quella
 * dopo, e va benissimo cosi. Quindi il ruolo non e un dato anagrafico: e una
 * PROPENSIONE, quattro numeri che dicono quanto quella persona tende a rendere
 * in ciascuna zona del campo, piu una CONFIDENZA che dice quanto ci possiamo
 * fidare di quei numeri.
 *
 * All'iscrizione si puo indicare un PROFILO DI PARTENZA — "parto attaccante" —
 * che serve a non sorteggiare del tutto alla cieca la prima domenica. Non e una
 * dichiarazione di identita e l'app lo mostra come tale, "di partenza", finche
 * le partite non lo confermano o lo smentiscono. Se lo smentiscono, l'etichetta
 * cambia: i fatti battono le intenzioni, altrimenti si dichiarerebbero tutti
 * attaccanti e il sistema non servirebbe a niente.
 */
data class Propensione(
    /** Peso per ruolo. Non serve che sommino a 1: conta il rapporto fra loro. */
    private val valori: Map<Ruolo, Double> = Ruolo.entries.associateWith { 1.0 },
    /** Quante partite di evidenza vera ci sono dietro questi numeri. */
    val partite: Int = 0,
    /** Dove ha detto di partire, se l'ha detto. Non e un verdetto. */
    val profiloIniziale: Ruolo? = null,
    /**
     * Il portiere non si deduce: si dichiara.
     *
     * I ruoli di movimento si mescolano e vanno dedotti dai fatti, ma fare il
     * portiere e un'identita, non una cosa in cui si scivola dopo dieci minuti
     * buoni fra i pali. Chi para, para tutte le domeniche; chi non para non
     * diventa portiere perche una volta ha fatto una parata.
     *
     * Quindi questo flag lo mette la persona iscrivendosi o l'organizzatore, e
     * nessuna votazione puo accenderlo o spegnerlo. Se un portiere gioca in
     * campo per una partita, e l'organizzatore a spostarlo a mano.
     */
    val portiere: Boolean = false,
) {
    init {
        require(partite >= 0) { "Le partite non possono essere negative" }
        require(valori.values.all { it >= 0.0 }) { "Le propensioni non possono essere negative" }
        require(!portiere || profiloIniziale == null || profiloIniziale == Ruolo.POR) {
            "Un portiere non puo avere come profilo iniziale un ruolo di movimento"
        }
    }

    /**
     * Da 0 a 1. Cresce con le partite e satura dopo
     * [PARTITE_PER_CONFIDENZA_PIENA]. E la manopola che fa degradare tutto in
     * modo morbido: a confidenza zero il ruolo non influisce per niente.
     *
     * Un profilo di partenza vale un pizzico di confidenza — quanto basta a
     * orientare il primo sorteggio, non abbastanza da fissare nessuno.
     */
    val confidenza: Double
        get() {
            if (portiere) return 1.0
            val daPartite = partite.toDouble() / PARTITE_PER_CONFIDENZA_PIENA
            val minima = if (profiloIniziale != null) CONFIDENZA_PROFILO else 0.0
            return maxOf(daPartite, minima).coerceAtMost(1.0)
        }

    /** Vero quando l'etichetta poggia su partite giocate e non sulle intenzioni. */
    val confermataDaiFatti: Boolean get() = portiere || partite >= PARTITE_MINIME_PER_ETICHETTA

    private val massimo: Double get() = valori.values.maxOrNull() ?: 1.0

    /**
     * Il ruolo da mostrare accanto al nome, o null se non c'e niente di sensato
     * da dire. Con abbastanza partite serve anche un distacco netto: chi rende
     * uguale ovunque non e un dato mancante, e un jolly.
     */
    val etichetta: Ruolo?
        get() {
            // Chi e portiere lo e e basta, e non smette di esserlo per via dei voti.
            if (portiere) return Ruolo.POR
            if (!confermataDaiFatti) return profiloIniziale

            // POR resta fuori dalla deduzione: un centrocampista che se l'e
            // cavata in porta durante una turnazione raccoglie nomine per le
            // parate, e senza questa esclusione si ritroverebbe etichettato
            // portiere e poi spedito fra i pali dal vincolo, per sempre.
            val diMovimento = valori.entries.filter { it.key != Ruolo.POR }
                .sortedByDescending { it.value }
            val primo = diMovimento[0].value
            val secondo = diMovimento.getOrNull(1)?.value ?: 0.0
            if (primo <= 0.0) return null
            return if ((primo - secondo) / primo >= DISTACCO_MINIMO) diMovimento[0].key else null
        }

    /** Quel che compare sotto il nome: "Difensore", "Attaccante · di partenza", "Jolly". */
    val etichettaTesto: String
        get() {
            val r = etichetta
            return when {
                r == null && !confermataDaiFatti -> "Nuovo"
                r == null -> "Jolly"
                confermataDaiFatti -> nome(r)
                else -> "${nome(r)} · di partenza"
            }
        }

    private fun nome(r: Ruolo) = when (r) {
        Ruolo.POR -> "Portiere"
        Ruolo.DIF -> "Difensore"
        Ruolo.CEN -> "Centrocampista"
        Ruolo.ATT -> "Attaccante"
    }

    /**
     * Quanto rende schierato in [ruolo], da [ParametriRuolo.affinitaMinima] a 1.0.
     *
     * La confidenza entra come sfumatura, non come interruttore: chi non ha dati
     * ottiene 1.0 ovunque, cioe il ruolo non lo penalizza affatto, e man mano
     * che le partite si accumulano l'affinita si differenzia.
     */
    fun affinita(ruolo: Ruolo, p: ParametriRuolo = ParametriRuolo()): Double {
        val max = massimo
        val quota = if (max <= 0.0) 1.0 else (valori[ruolo] ?: 0.0) / max
        val piena = p.affinitaMinima + (1.0 - p.affinitaMinima) * quota
        return 1.0 - confidenza * (1.0 - piena)
    }

    /** Il valore grezzo, per la UI che mostra le quattro barrette del profilo. */
    fun peso(ruolo: Ruolo): Double = valori[ruolo] ?: 0.0

    /** Le quattro barrette normalizzate a 0..1, per disegnarle. */
    fun profilo(): Map<Ruolo, Double> {
        val max = massimo
        return Ruolo.entries.associateWith { if (max <= 0.0) 0.0 else (valori[it] ?: 0.0) / max }
    }

    /**
     * Aggiorna il profilo con l'esito della votazione di fine partita.
     *
     * Ogni fase di gioco sposta il ruolo corrispondente, indipendentemente da
     * dove la persona era schierata: e il senso di votare le fasi invece dei
     * ruoli. Un difensore nominato per la regia guadagna propensione da
     * centrocampista, e la prossima volta il sorteggio lo sapra.
     */
    fun dopoLaVotazione(rendimentoPerFase: Map<Fase, Double>): Propensione {
        require(rendimentoPerFase.values.all { it in -1.0..1.0 }) { "Rendimento fuori scala" }
        val aggiornati = valori.mapValues { (r, v) ->
            // La fase 'porta' alimenta l'attributo parate — serve a capire chi
            // se la cava meglio quando bisogna improvvisare — ma non muove la
            // propensione: quella casella la decide solo chi organizza.
            if (r == Ruolo.POR) return@mapValues v
            val versoIlCentro = v + (1.0 - v) * DECADIMENTO
            val fase = Fase.entries.firstOrNull { it.ruolo == r }
            val delta = PASSO * (rendimentoPerFase[fase] ?: 0.0)
            (versoIlCentro + delta).coerceIn(0.05, 3.0)
        }
        return copy(valori = aggiornati, partite = partite + 1)
    }

    /** Scorciatoia per i test e per le simulazioni: una sola fase, un solo esito. */
    fun dopoLaPartita(ruolo: Ruolo, rendimento: Double): Propensione {
        require(rendimento in -1.0..1.0) { "Rendimento fuori scala: $rendimento" }
        val fase = Fase.entries.first { it.ruolo == ruolo }
        return dopoLaVotazione(mapOf(fase to rendimento))
    }

    companion object {
        /** Sotto questa soglia l'etichetta resta quella di partenza. */
        const val PARTITE_MINIME_PER_ETICHETTA = 4

        /** Da qui in poi ci fidiamo pienamente dei numeri. */
        const val PARTITE_PER_CONFIDENZA_PIENA = 8.0

        /** Quanto il primo ruolo deve staccare il secondo per meritare l'etichetta. */
        const val DISTACCO_MINIMO = 0.15

        /** Quanto pesa un profilo dichiarato e non ancora confermato da nessuna partita. */
        const val CONFIDENZA_PROFILO = 0.30

        private const val PASSO = 0.22
        private const val DECADIMENTO = 0.04

        /** Chi si iscrive e non dice niente. Nessun ruolo, nessun pregiudizio. */
        val NUOVA = Propensione()

        /**
         * Il profilo di partenza scelto all'iscrizione: "parto attaccante".
         *
         * Orienta il primo sorteggio e da alla persona un'etichetta da mostrare
         * subito, ma pesa poco e si lascia smentire in poche partite.
         */
        fun diPartenza(ruolo: Ruolo): Propensione =
            if (ruolo == Ruolo.POR) portiere() else Propensione(
                valori = Ruolo.entries.associateWith { if (it == ruolo) 1.8 else 1.0 },
                partite = 0,
                profiloIniziale = ruolo,
            )

        /**
         * Chi para. Si dichiara iscrivendosi o lo imposta l'organizzatore, e
         * resta finche uno dei due non cambia idea: nessun voto lo scalfisce.
         */
        fun portiere(): Propensione = Propensione(
            valori = Ruolo.entries.associateWith { if (it == Ruolo.POR) 3.0 else 0.6 },
            partite = 0,
            profiloIniziale = Ruolo.POR,
            portiere = true,
        )

        /**
         * La taratura dell'organizzatore al momento di far partire il gruppo.
         *
         * E una dichiarazione ferma, non un suggerimento: chi organizza ha visto
         * giocare queste persone per anni e ne sa piu dell'app. Vale quindi come
         * una stagione di evidenza, e resta comunque scalzabile dai fatti.
         */
        fun dichiarata(ruolo: Ruolo, partite: Int = PARTITE_PER_CONFIDENZA_PIENA.toInt()): Propensione =
            if (ruolo == Ruolo.POR) portiere() else Propensione(
                valori = Ruolo.entries.associateWith { if (it == ruolo) 3.0 else 0.6 },
                partite = partite,
                profiloIniziale = ruolo,
            )
    }
}

/**
 * Quanta informazione aveva davvero il sorteggio. Non e una diagnostica interna:
 * e cio che l'app dice a chi guarda le squadre, perche un sorteggio fatto al
 * buio va annunciato come tale invece di sembrare un verdetto.
 */
enum class LivelloInformazione {
    /** Ruoli noti per buona parte dei convocati: squadre costruite per reparto. */
    ETICHETTE,

    /** Ruoli incerti ma le forze sono note: squadre pareggiate sui rendimenti. */
    RENDIMENTI,

    /** Troppi giocatori nuovi: sorteggio puro, e l'app lo dichiara. */
    SORTEGGIO_PURO;

    val testo: String
        get() = when (this) {
            ETICHETTE -> "Squadre fatte con ruoli e rendimenti."
            RENDIMENTI -> "Ruoli ancora incerti: squadre pareggiate sui rendimenti."
            SORTEGGIO_PURO -> "Troppi giocatori nuovi per sapere come dividervi: sorteggio puro."
        }
}

/**
 * Decide quanta informazione c'e in questi convocati.
 *
 * Il sorteggio degrada in modo continuo — ogni giocatore pesa per la sua
 * confidenza — ma il messaggio all'utente resta a tre gradini, perche
 * "confidenza media 0,41" non significa niente per nessuno.
 */
internal fun livelloInformazione(convocati: List<Giocatore>): LivelloInformazione {
    val conEtichetta = convocati.count { it.propensione.etichetta != null }
    val confidenzaMedia = convocati.map { it.propensione.confidenza }.average()

    // Le skill portano informazione solo se differiscono davvero fra loro: una
    // rosa tutta a 50 e indistinguibile e non c'e niente da pareggiare.
    val forze = convocati.map { g -> Ruolo.entries.maxOf { g.ovrBase(it) } }
    val media = forze.average()
    val dispersione = kotlin.math.sqrt(forze.sumOf { (it - media) * (it - media) } / forze.size)

    return when {
        conEtichetta >= convocati.size / 2 && confidenzaMedia >= 0.35 -> LivelloInformazione.ETICHETTE
        dispersione > 2.0 -> LivelloInformazione.RENDIMENTI
        else -> LivelloInformazione.SORTEGGIO_PURO
    }
}
