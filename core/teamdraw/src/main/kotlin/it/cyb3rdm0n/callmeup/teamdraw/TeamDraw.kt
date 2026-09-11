package it.cyb3rdm0n.callmeup.teamdraw

import kotlin.math.abs
import kotlin.random.Random

/**
 * Motore di sorteggio squadre di CallMeUp.
 *
 * Kotlin puro, nessuna dipendenza da Android e nessuna dal backend: gira sul
 * telefono di chi organizza, si testa con una JVM e domani si ricompila dentro
 * un server senza toccare una riga.
 *
 * Due cose sono deliberatamente asimmetriche, perche il calcetto e asimmetrico:
 *
 *  - i ruoli di movimento sono FLUIDI. Nel calcetto un difensore si ritrova in
 *    area avversaria e un attaccante rientra a coprire, dentro la stessa
 *    partita. Il ruolo e quindi una preferenza leggera, non un vincolo, e
 *    giocare altrove costa pochissimo;
 *  - il portiere invece e un VINCOLO vero. Chi para, para.
 */

// ---------------------------------------------------------------------------
// Modello
// ---------------------------------------------------------------------------

enum class Ruolo { POR, DIF, CEN, ATT }

enum class Squadra { A, B }

/** Attributi 1..99. Tutti partono da 50 e si muovono con i rendimenti. */
data class Skill(
    val velocita: Double = 50.0,
    val tiro: Double = 50.0,
    val passaggio: Double = 50.0,
    val tecnica: Double = 50.0,
    val difesa: Double = 50.0,
    val fisico: Double = 50.0,
    val parate: Double = 50.0,
)

/**
 * Quanto conta il ruolo, per questo gruppo.
 *
 * I valori di default descrivono un calcetto normale, dove ci si scambia di
 * posizione in continuazione. Un gruppo che gioca in modo piu ordinato puo
 * alzare [pesoFuoriRuolo]; uno che gioca a ruoli completamente liberi puo
 * portare [proficiencyFuoriRuolo] a 1.0 e [pesoFuoriRuolo] a 0.0, e il ruolo
 * diventa solo un'indicazione di dove partire.
 */
data class ParametriRuolo(
    /** Moltiplicatore del valore in un ruolo secondario dichiarato. */
    val proficiencySecondario: Double = 0.97,
    /** Moltiplicatore del valore in un ruolo mai dichiarato. */
    val proficiencyFuoriRuolo: Double = 0.90,
    /** Quanto dispiace, nella funzione di costo, schierare uno fuori ruolo. */
    val pesoFuoriRuolo: Double = 3.0,
    /** Quanto dispiace schierare uno nel suo ruolo secondario. */
    val pesoSecondario: Double = 0.5,
) {
    init {
        require(proficiencyFuoriRuolo in 0.5..1.0) { "proficiencyFuoriRuolo fuori scala" }
        require(proficiencySecondario in proficiencyFuoriRuolo..1.0) {
            "Il ruolo secondario non puo valere meno del fuori ruolo"
        }
    }

    companion object {
        /** Ruoli del tutto liberi: conta solo la forza dei giocatori. */
        val LIBERI = ParametriRuolo(1.0, 1.0, 0.0, 0.0)

        /** Gruppo ordinato, che tiene le posizioni. */
        val RIGIDI = ParametriRuolo(0.92, 0.80, 20.0, 3.0)
    }
}

data class Giocatore(
    val id: String,
    val nome: String,
    val skill: Skill,
    val ruoloPrimario: Ruolo,
    val ruoliSecondari: Set<Ruolo> = emptySet(),
) {
    fun proficiency(ruolo: Ruolo, p: ParametriRuolo = ParametriRuolo()): Double = when {
        ruolo == ruoloPrimario -> 1.0
        ruolo in ruoliSecondari -> p.proficiencySecondario
        else -> p.proficiencyFuoriRuolo
    }

    /** Quanto vale questo giocatore schierato in questo ruolo, scala 1..99. */
    fun ovr(ruolo: Ruolo, p: ParametriRuolo = ParametriRuolo()): Double {
        val w = PESI.getValue(ruolo)
        val base = w.velocita * skill.velocita +
            w.tiro * skill.tiro +
            w.passaggio * skill.passaggio +
            w.tecnica * skill.tecnica +
            w.difesa * skill.difesa +
            w.fisico * skill.fisico +
            w.parate * skill.parate
        return base * proficiency(ruolo, p)
    }

    companion object {
        /** Pesi per ruolo; ogni riga somma 1.0, cosi l'OVR resta sulla scala 1..99. */
        val PESI: Map<Ruolo, Skill> = mapOf(
            Ruolo.POR to Skill(.05, .00, .05, .00, .10, .10, .70),
            Ruolo.DIF to Skill(.15, .05, .15, .05, .40, .20, .00),
            Ruolo.CEN to Skill(.15, .15, .30, .20, .10, .10, .00),
            Ruolo.ATT to Skill(.25, .35, .10, .20, .05, .05, .00),
        )
    }
}

/** Quanti uomini per reparto. Il portiere e sempre uno. */
val FORMAZIONI: Map<Int, Map<Ruolo, Int>> = mapOf(
    7 to mapOf(Ruolo.POR to 1, Ruolo.DIF to 2, Ruolo.CEN to 3, Ruolo.ATT to 1),
    8 to mapOf(Ruolo.POR to 1, Ruolo.DIF to 3, Ruolo.CEN to 3, Ruolo.ATT to 1),
)

data class Slot(
    val giocatore: Giocatore,
    val ruolo: Ruolo,
    /** Valore del giocatore in questo ruolo, gia calcolato con i parametri del gruppo. */
    val ovr: Double,
    /** Vero se il ruolo non e ne il primario ne uno dei secondari dichiarati. */
    val fuoriRuolo: Boolean,
)

/** Come si e risolta la questione portiere: serve a spiegarlo nella UI. */
enum class ModalitaPorta {
    /** Due portieri di ruolo fra i convocati: ciascuno nella sua porta. */
    REGOLARE,

    /** Un solo portiere di ruolo: l'altra squadra si organizza a turni. */
    UNO_SOLO,

    /** Nessun portiere di ruolo: entrambe le squadre ruotano in porta. */
    A_TURNO,
}

/** Uno spezzone di partita in cui un giocatore sta fra i pali. */
data class TurnoPorta(
    val squadra: Squadra,
    val giocatoreId: String,
    val nome: String,
    val dalMinuto: Int,
    val alMinuto: Int,
) {
    val minuti: Int get() = alMinuto - dalMinuto
}

data class Formazione(
    val squadraA: List<Slot>,
    val squadraB: List<Slot>,
    val modalitaPorta: ModalitaPorta,
    /**
     * Turnazione fra i pali. Vuota quando ci sono due portieri di ruolo.
     * Quando non ce ne sono, o ce n'e uno solo, nessuno deve farsi tutta la
     * partita in porta per caso: la squadra senza portiere ruota fra tre
     * giocatori, scelti dando la precedenza a chi in porta ci e finito meno
     * volte nelle partite passate.
     */
    val pianoPorta: List<TurnoPorta>,
    val seed: Long,
    val costo: Double,
    /** Scarto di forza fra le due squadre, in punti OVR sommati. */
    val deltaOvr: Double,
) {
    val forzaA: Double get() = squadraA.sumOf { it.ovr }
    val forzaB: Double get() = squadraB.sumOf { it.ovr }

    /** Frase pronta per la UI e per la notifica. */
    fun spiegazionePorta(): String = when (modalitaPorta) {
        ModalitaPorta.REGOLARE -> "Portieri di ruolo in entrambe le porte."
        ModalitaPorta.UNO_SOLO -> "Un solo portiere di ruolo: l'altra squadra si alterna fra i pali."
        ModalitaPorta.A_TURNO -> "Nessun portiere di ruolo fra i convocati: si va a turni in porta."
    }
}

// ---------------------------------------------------------------------------
// Pesi della funzione di costo
// ---------------------------------------------------------------------------

private const val W_TOTALE = 1.00   // scarto di forza complessivo
private const val W_REPARTO = 0.45  // scarto reparto per reparto
private const val W_TOP = 0.60      // scarto fra i due migliori di movimento
private const val W_RIPETUTI = 0.35 // coppie che giocano sempre insieme

/**
 * Quante volte due giocatori sono gia finiti nella stessa squadra di recente.
 * Chiave: coppia di id ordinata. Alimenta la penalita anti-ripetizione, ed e
 * cio che rende le squadre diverse a ogni partita: senza storico il sorteggio
 * ha molte meno ragioni per cambiare.
 */
typealias StoricoCoppie = Map<Pair<String, String>, Int>

/**
 * Quante volte ciascuno e gia finito in porta senza esserne il ruolo.
 * Serve a non far pagare sempre allo stesso il fatto che nessuno vuole andarci.
 */
typealias TurniInPorta = Map<String, Int>

private fun chiave(a: String, b: String) = if (a < b) a to b else b to a

// ---------------------------------------------------------------------------
// Funzione di costo
// ---------------------------------------------------------------------------

private class Schieramento(val a: MutableList<Slot>, val b: MutableList<Slot>)

private fun costo(
    s: Schieramento, storico: StoricoCoppie, p: ParametriRuolo,
): Pair<Double, Double> {
    val totA = s.a.sumOf { it.ovr }
    val totB = s.b.sumOf { it.ovr }
    val deltaTot = abs(totA - totB)

    // Il portiere pesa nel totale — una porta piu forte si compensa in campo —
    // ma non entra nel confronto per reparto ne fra i migliori.
    val movA = s.a.drop(1)
    val movB = s.b.drop(1)

    var deltaReparti = 0.0
    for (r in listOf(Ruolo.DIF, Ruolo.CEN, Ruolo.ATT)) {
        deltaReparti += abs(
            movA.filter { it.ruolo == r }.sumOf { it.ovr } -
                movB.filter { it.ruolo == r }.sumOf { it.ovr }
        )
    }

    val topA = movA.map { it.ovr }.sortedDescending().take(2).sum()
    val topB = movB.map { it.ovr }.sortedDescending().take(2).sum()

    var disagio = 0.0
    for (slot in movA + movB) {
        val prof = slot.giocatore.proficiency(slot.ruolo, p)
        disagio += when {
            prof == 1.0 -> 0.0
            slot.fuoriRuolo -> p.pesoFuoriRuolo
            else -> p.pesoSecondario
        }
    }

    var ripetizioni = 0
    for (squadra in listOf(s.a, s.b)) {
        for (i in squadra.indices) {
            for (j in i + 1 until squadra.size) {
                ripetizioni += storico[chiave(squadra[i].giocatore.id, squadra[j].giocatore.id)] ?: 0
            }
        }
    }

    val c = W_TOTALE * deltaTot +
        W_REPARTO * deltaReparti +
        W_TOP * abs(topA - topB) +
        W_RIPETUTI * ripetizioni +
        disagio
    return c to deltaTot
}

// ---------------------------------------------------------------------------
// Portieri — vincolo, non costo
// ---------------------------------------------------------------------------

private data class SceltaPorta(
    val a: Giocatore, val b: Giocatore, val modalita: ModalitaPorta,
)

/**
 * Se fra i convocati ci sono portieri di ruolo, vanno in porta. Non e
 * negoziabile: lasciarlo decidere alla funzione di costo porta l'ottimizzatore
 * a schierare due scarsi fra i pali, perche due scarsi sono piu simili fra loro
 * di due bravi e cosi il conto torna prima.
 *
 * Quando i portieri sono uno o zero — che nel calcetto e la norma piu che
 * l'eccezione — si improvvisa, ma con un criterio: prima chi in porta ci e
 * andato meno volte, e a parita di turni chi para meglio.
 */
private fun scegliPortieri(
    convocati: List<Giocatore>, rng: Random, turni: TurniInPorta, p: ParametriRuolo,
): SceltaPorta {
    val diRuolo = convocati.filter { it.ruoloPrimario == Ruolo.POR }.shuffled(rng)

    if (diRuolo.size >= 2) {
        val due = diRuolo.sortedByDescending { it.ovr(Ruolo.POR, p) }
        return SceltaPorta(due[0], due[1], ModalitaPorta.REGOLARE)
    }

    val ripieghi = ordinaRipieghi(convocati, turni, rng, p)

    return if (diRuolo.size == 1) {
        SceltaPorta(diRuolo[0], ripieghi[0], ModalitaPorta.UNO_SOLO)
    } else {
        SceltaPorta(ripieghi[0], ripieghi[1], ModalitaPorta.A_TURNO)
    }
}

/** Chi puo improvvisare in porta, dal piu indicato al meno. */
private fun ordinaRipieghi(
    candidati: List<Giocatore>, turni: TurniInPorta, rng: Random, p: ParametriRuolo,
): List<Giocatore> = candidati
    .filter { it.ruoloPrimario != Ruolo.POR }
    .map { g ->
        // meno turni fatti = molto meglio; a parita, chi para di piu.
        // il rumore evita che con i contatori a zero esca sempre lo stesso.
        g to (-(turni[g.id] ?: 0) * 40.0 + g.ovr(Ruolo.POR, p) + rng.nextDouble() * 6)
    }
    .sortedByDescending { it.second }
    .map { it.first }

/**
 * Turnazione fra i pali per una squadra senza portiere di ruolo: spezzoni di
 * durata pari, il primo a chi e schierato in porta.
 */
private fun pianoPerSquadra(
    squadra: Squadra,
    rosaSquadra: List<Slot>,
    portierePartenza: Giocatore,
    durataMin: Int,
    turni: TurniInPorta,
    rng: Random,
    p: ParametriRuolo,
    quanti: Int = 3,
): List<TurnoPorta> {
    val altri = ordinaRipieghi(
        rosaSquadra.map { it.giocatore }.filter { it.id != portierePartenza.id }, turni, rng, p,
    )
    val rotazione = (listOf(portierePartenza) + altri).take(quanti.coerceAtMost(rosaSquadra.size))

    val base = durataMin / rotazione.size
    val resto = durataMin % rotazione.size
    var cursore = 0
    return rotazione.mapIndexed { i, g ->
        // il resto dei minuti va ai primi turni, cosi la somma torna esatta
        val durata = base + if (i < resto) 1 else 0
        TurnoPorta(squadra, g.id, g.nome, cursore, cursore + durata)
            .also { cursore += durata }
    }
}

// ---------------------------------------------------------------------------
// Costruzione e ricerca locale
// ---------------------------------------------------------------------------

private fun slot(g: Giocatore, r: Ruolo, p: ParametriRuolo) = Slot(
    giocatore = g,
    ruolo = r,
    ovr = g.ovr(r, p),
    fuoriRuolo = r != g.ruoloPrimario && r !in g.ruoliSecondari,
)

private fun schieramentoIniziale(
    convocati: List<Giocatore>, formato: Int, rng: Random, porta: SceltaPorta, p: ParametriRuolo,
): Schieramento {
    val forma = FORMAZIONI.getValue(formato)
    val liberi = mapOf(
        Squadra.A to forma.toMutableMap(),
        Squadra.B to forma.toMutableMap(),
    )
    val s = Schieramento(
        mutableListOf(slot(porta.a, Ruolo.POR, p)),
        mutableListOf(slot(porta.b, Ruolo.POR, p)),
    )
    liberi.getValue(Squadra.A)[Ruolo.POR] = 0
    liberi.getValue(Squadra.B)[Ruolo.POR] = 0

    val rimasti = convocati
        .filter { it.id != porta.a.id && it.id != porta.b.id }
        .shuffled(rng)

    for (g in rimasti) {
        val opzioni = buildList {
            for (sq in Squadra.entries) {
                for ((r, n) in liberi.getValue(sq)) {
                    if (n > 0 && r != Ruolo.POR) add(sq to r)
                }
            }
        }
        if (opzioni.isEmpty()) break

        val carico = mapOf(
            Squadra.A to s.a.sumOf { it.ovr },
            Squadra.B to s.b.sumOf { it.ovr },
        )
        val (sq, r) = opzioni.maxBy { (squadra, ruolo) ->
            g.proficiency(ruolo, p) * 100 - carico.getValue(squadra) * 0.02 + rng.nextDouble() * 8
        }
        (if (sq == Squadra.A) s.a else s.b).add(slot(g, r, p))
        liberi.getValue(sq)[r] = liberi.getValue(sq).getValue(r) - 1
    }
    return s
}

/**
 * Ricerca locale con due mosse, entrambe cieche ai portieri (indice 0):
 *  1. scambio incrociato fra le squadre, ciascuno prende il ruolo dell'altro;
 *  2. scambio di ruolo dentro la stessa squadra.
 */
private fun migliora(
    s: Schieramento, storico: StoricoCoppie, rng: Random, p: ParametriRuolo, passi: Int = 500,
): Double {
    var corrente = costo(s, storico, p).first

    repeat(passi) {
        if (rng.nextDouble() < 0.65) {
            val i = 1 + rng.nextInt(s.a.size - 1)
            val j = 1 + rng.nextInt(s.b.size - 1)
            val sa = s.a[i]
            val sb = s.b[j]
            s.a[i] = slot(sb.giocatore, sa.ruolo, p)
            s.b[j] = slot(sa.giocatore, sb.ruolo, p)
            val nuovo = costo(s, storico, p).first
            if (nuovo < corrente) corrente = nuovo else { s.a[i] = sa; s.b[j] = sb }
        } else {
            val lista = if (rng.nextBoolean()) s.a else s.b
            if (lista.size < 3) return@repeat
            val i = 1 + rng.nextInt(lista.size - 1)
            val j = 1 + rng.nextInt(lista.size - 1)
            if (i == j) return@repeat
            val si = lista[i]
            val sj = lista[j]
            if (si.ruolo == sj.ruolo) return@repeat
            lista[i] = slot(si.giocatore, sj.ruolo, p)
            lista[j] = slot(sj.giocatore, si.ruolo, p)
            val nuovo = costo(s, storico, p).first
            if (nuovo < corrente) corrente = nuovo else { lista[i] = si; lista[j] = sj }
        }
    }
    return corrente
}

// ---------------------------------------------------------------------------
// API pubblica
// ---------------------------------------------------------------------------

object SorteggioSquadre {

    /**
     * Divide i convocati in due squadre eque.
     *
     * La pseudo-casualita chiesta dal progetto nasce qui: non si sceglie mai la
     * soluzione di costo minimo — darebbe sempre le stesse squadre con gli
     * stessi convocati — ma si pesca a caso fra tutte quelle entro [tolleranza]
     * dalla migliore. Passare [storico] e cio che spinge davvero la varieta:
     * senza, con una rosa fissa, le formazioni tornano a somigliarsi.
     *
     * @param seed conservarlo sul database permette di rigenerare lo stesso
     *             sorteggio, per esempio per mostrare com'era prima di un forfait.
     */
    fun sorteggia(
        convocati: List<Giocatore>,
        formato: Int,
        storico: StoricoCoppie = emptyMap(),
        turniInPorta: TurniInPorta = emptyMap(),
        durataMin: Int = 60,
        parametriRuolo: ParametriRuolo = ParametriRuolo(),
        seed: Long = Random.nextLong(),
        restart: Int = 60,
        tolleranza: Double = 0.08,
    ): Formazione {
        require(formato in FORMAZIONI) { "Formato non supportato: $formato" }
        require(convocati.size == formato * 2) {
            "Servono ${formato * 2} convocati, ricevuti ${convocati.size}"
        }
        require(convocati.map { it.id }.toSet().size == convocati.size) {
            "Ci sono convocati duplicati"
        }

        val p = parametriRuolo
        val rng = Random(seed)
        val candidati = ArrayList<Pair<Double, Schieramento>>(restart)
        var modalita = ModalitaPorta.A_TURNO

        repeat(restart) {
            val porta = scegliPortieri(convocati, rng, turniInPorta, p)
            modalita = porta.modalita
            val s = schieramentoIniziale(convocati, formato, rng, porta, p)
            candidati += migliora(s, storico, rng, p) to s
        }

        val migliore = candidati.minOf { it.first }
        val soglia = migliore + abs(migliore) * tolleranza + 1.0
        val buoni = candidati.filter { it.first <= soglia }
        val (c, scelto) = buoni[rng.nextInt(buoni.size)]
        val (_, delta) = costo(scelto, storico, p)

        // La turnazione si calcola sulle rose gia formate: ruota solo la
        // squadra (o le squadre) senza portiere di ruolo.
        val piano = buildList {
            for ((lato, rosa) in listOf(Squadra.A to scelto.a, Squadra.B to scelto.b)) {
                val portiere = rosa[0].giocatore
                if (portiere.ruoloPrimario == Ruolo.POR) continue
                addAll(pianoPerSquadra(lato, rosa, portiere, durataMin, turniInPorta, rng, p))
            }
        }

        val ordine = listOf(Ruolo.POR, Ruolo.DIF, Ruolo.CEN, Ruolo.ATT)
        return Formazione(
            squadraA = scelto.a.sortedBy { ordine.indexOf(it.ruolo) },
            squadraB = scelto.b.sortedBy { ordine.indexOf(it.ruolo) },
            modalitaPorta = modalita,
            pianoPorta = piano,
            seed = seed,
            costo = c,
            deltaOvr = delta,
        )
    }
}
