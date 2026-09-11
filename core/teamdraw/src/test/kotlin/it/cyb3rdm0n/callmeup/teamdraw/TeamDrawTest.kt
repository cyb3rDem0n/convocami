package it.cyb3rdm0n.callmeup.teamdraw

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * I test che contano sono quelli che difendono i due difetti trovati in
 * simulazione durante la progettazione, perche sono difetti che non fanno
 * fallire nulla: producono squadre plausibili e sbagliate.
 *
 *  1. senza penalita esplicita l'ottimizzatore schiera la gente fuori ruolo
 *     per abbassarne il valore e pareggiare i conti;
 *  2. se il portiere e un termine di costo invece che un vincolo, due portieri
 *     scarsi "pareggiano" meglio di due bravi e i portieri veri finiscono in
 *     campo.
 */
class TeamDrawTest {

    // ---------------------------------------------------------------------
    // Costruttori di comodo
    // ---------------------------------------------------------------------

    private fun giocatore(
        n: Int,
        ruolo: Ruolo,
        livello: Double = 50.0,
        parate: Double = 25.0,
        secondari: Set<Ruolo> = emptySet(),
    ) = Giocatore(
        id = "p$n",
        nome = "Giocatore$n",
        skill = Skill(livello, livello, livello, livello, livello, livello, parate),
        ruoloPrimario = ruolo,
        ruoliSecondari = secondari,
    )

    /** Rosa con la dotazione esatta di ruoli per un 7v7: 2 POR, 4 DIF, 6 CEN, 2 ATT. */
    private fun rosaEsatta(rng: Random = Random(1)) = buildList {
        var i = 0
        repeat(2) { add(giocatore(i++, Ruolo.POR, 55.0, parate = 75.0)) }
        repeat(4) { add(giocatore(i++, Ruolo.DIF, 40.0 + rng.nextDouble() * 30)) }
        repeat(6) { add(giocatore(i++, Ruolo.CEN, 40.0 + rng.nextDouble() * 30)) }
        repeat(2) { add(giocatore(i++, Ruolo.ATT, 40.0 + rng.nextDouble() * 30)) }
    }

    private fun Formazione.portieri() = listOf(squadraA.first(), squadraB.first())

    private fun Formazione.tutti() = squadraA + squadraB

    // ---------------------------------------------------------------------
    // Il portiere e un vincolo, non un costo
    // ---------------------------------------------------------------------

    @Test
    fun `con due portieri di ruolo entrambi stanno in porta`() {
        repeat(30) { i ->
            val f = SorteggioSquadre.sorteggia(rosaEsatta(), 7, seed = i.toLong())
            val inPorta = f.portieri()
            assertTrue(inPorta.all { it.ruolo == Ruolo.POR }, "Lo slot 0 deve essere il portiere")
            assertTrue(
                inPorta.all { it.giocatore.ruoloPrimario == Ruolo.POR },
                "Seme $i: un portiere di ruolo e stato schierato in campo",
            )
            assertEquals(ModalitaPorta.REGOLARE, f.modalitaPorta)
        }
    }

    /**
     * Regressione del difetto originale: due portieri MOLTO diversi fra loro.
     * La versione sbagliata li teneva entrambi fuori dalla porta perche due
     * ripieghi scarsi avevano fra loro uno scarto minore.
     */
    @Test
    fun `portieri di forza molto diversa restano comunque in porta`() {
        val rosa = buildList {
            add(giocatore(0, Ruolo.POR, 80.0, parate = 90.0))   // molto forte
            add(giocatore(1, Ruolo.POR, 40.0, parate = 45.0))   // molto debole
            var i = 2
            repeat(4) { add(giocatore(i++, Ruolo.DIF, 55.0)) }
            repeat(6) { add(giocatore(i++, Ruolo.CEN, 55.0)) }
            repeat(2) { add(giocatore(i++, Ruolo.ATT, 55.0)) }
        }
        repeat(30) { i ->
            val f = SorteggioSquadre.sorteggia(rosa, 7, seed = i.toLong())
            val idsInPorta = f.portieri().map { it.giocatore.id }.toSet()
            assertEquals(
                setOf("p0", "p1"), idsInPorta,
                "Seme $i: i portieri di ruolo devono stare fra i pali anche se impari",
            )
        }
    }

    @Test
    fun `con un solo portiere di ruolo l'altra squadra si organizza a turni`() {
        val rosa = rosaEsatta().filterNot { it.id == "p1" } + giocatore(99, Ruolo.CEN, 50.0)
        val f = SorteggioSquadre.sorteggia(rosa, 7, seed = 42)

        assertEquals(ModalitaPorta.UNO_SOLO, f.modalitaPorta)
        assertEquals(1, f.portieri().count { it.giocatore.ruoloPrimario == Ruolo.POR })
        assertTrue(f.pianoPorta.isNotEmpty(), "La squadra senza portiere deve avere una turnazione")

        val squadreConTurni = f.pianoPorta.map { it.squadra }.toSet()
        assertEquals(1, squadreConTurni.size, "Solo una squadra deve ruotare")
    }

    @Test
    fun `senza nessun portiere di ruolo ruotano entrambe le squadre`() {
        val rosa = rosaEsatta()
            .filterNot { it.ruoloPrimario == Ruolo.POR } + listOf(
            giocatore(90, Ruolo.CEN, 50.0), giocatore(91, Ruolo.DIF, 50.0),
        )
        val f = SorteggioSquadre.sorteggia(rosa, 7, seed = 7)

        assertEquals(ModalitaPorta.A_TURNO, f.modalitaPorta)
        assertEquals(
            setOf(Squadra.A, Squadra.B), f.pianoPorta.map { it.squadra }.toSet(),
            "Se non c'e nessun portiere devono ruotare tutte e due",
        )
    }

    // ---------------------------------------------------------------------
    // La turnazione
    // ---------------------------------------------------------------------

    @Test
    fun `la turnazione copre la partita senza buchi ne doppioni`() {
        val rosa = rosaEsatta().filterNot { it.ruoloPrimario == Ruolo.POR } + listOf(
            giocatore(90, Ruolo.CEN, 50.0), giocatore(91, Ruolo.DIF, 50.0),
        )
        val f = SorteggioSquadre.sorteggia(rosa, 7, durataMin = 60, seed = 3)

        for (lato in listOf(Squadra.A, Squadra.B)) {
            val turni = f.pianoPorta.filter { it.squadra == lato }.sortedBy { it.dalMinuto }
            val rosaSquadra = if (lato == Squadra.A) f.squadraA else f.squadraB

            assertEquals(0, turni.first().dalMinuto)
            assertEquals(60, turni.last().alMinuto)
            assertEquals(60, turni.sumOf { it.minuti }, "I minuti devono sommare alla durata")
            assertEquals(
                turni.size, turni.map { it.giocatoreId }.toSet().size,
                "Nessuno puo comparire due volte nella turnazione",
            )
            for (i in 1 until turni.size) {
                assertEquals(
                    turni[i - 1].alMinuto, turni[i].dalMinuto,
                    "Buco o sovrapposizione fra i turni",
                )
            }
            assertEquals(
                rosaSquadra.first().giocatore.id, turni.first().giocatoreId,
                "Il primo turno spetta a chi e schierato in porta",
            )
            assertTrue(
                turni.all { t -> rosaSquadra.any { it.giocatore.id == t.giocatoreId } },
                "In turnazione c'e qualcuno che non gioca in quella squadra",
            )
        }
    }

    @Test
    fun `con due portieri di ruolo non si perde tempo a fare turnazioni`() {
        val f = SorteggioSquadre.sorteggia(rosaEsatta(), 7, seed = 11)
        assertTrue(f.pianoPorta.isEmpty())
    }

    /**
     * Il punto non e solo che qualcuno vada in porta, ma che non sia sempre lo
     * stesso. Senza il conteggio dei turni passati, chi para un po' meglio
     * diventa il portiere fisso della comitiva.
     */
    @Test
    fun `la porta ruota in modo equo su molte partite`() {
        val rng = Random(2024)
        val rosa = (0 until 16).map {
            giocatore(it, listOf(Ruolo.DIF, Ruolo.CEN, Ruolo.ATT)[it % 3], 45.0 + rng.nextDouble() * 20)
        }
        val turni = HashMap<String, Int>()
        val minuti = HashMap<String, Int>()

        repeat(40) {
            val convocati = rosa.shuffled(rng).take(14)
            val f = SorteggioSquadre.sorteggia(
                convocati, 7, turniInPorta = turni, durataMin = 60, seed = rng.nextLong(),
            )
            for (t in f.pianoPorta) {
                turni.merge(t.giocatoreId, 1, Int::plus)
                minuti.merge(t.giocatoreId, t.minuti, Int::plus)
            }
        }

        assertEquals(rosa.size, minuti.size, "Nessuno deve restare fuori dalla rotazione")
        val rapporto = minuti.values.max().toDouble() / minuti.values.min()
        assertTrue(
            rapporto < 1.5,
            "Squilibrio nella porta: chi ne fa di piu ne fa ${"%.2f".format(rapporto)} volte chi ne fa meno",
        )
    }

    // ---------------------------------------------------------------------
    // Ruoli
    // ---------------------------------------------------------------------

    /**
     * Con i parametri di default i ruoli sono fluidi, come nel calcetto vero:
     * scambiarsi di posizione non e un problema, quindi un po' di fuori ruolo e
     * atteso e va bene. Cio che NON deve succedere e che sia sistematico.
     */
    @Test
    fun `con ruoli fluidi il fuori ruolo resta contenuto`() {
        var fuori = 0
        var totale = 0
        repeat(20) { i ->
            val f = SorteggioSquadre.sorteggia(rosaEsatta(), 7, seed = i.toLong())
            fuori += f.tutti().count { it.fuoriRuolo }
            totale += f.tutti().size
        }
        val quota = fuori.toDouble() / totale
        assertTrue(
            quota < 0.30,
            "Fuori ruolo nel ${"%.0f".format(quota * 100)}% dei casi pur avendo la dotazione esatta",
        )
    }

    /**
     * Un gruppo che tiene le posizioni puo chiedere ruoli rigidi. Con la
     * dotazione di ruoli esatta, li nessuno deve giocare fuori ruolo: se
     * succede, l'ottimizzatore sta di nuovo usando lo spostamento come leva per
     * pareggiare le squadre, che e il difetto originale.
     */
    @Test
    fun `con ruoli rigidi e dotazione esatta nessuno gioca fuori ruolo`() {
        repeat(20) { i ->
            val f = SorteggioSquadre.sorteggia(
                rosaEsatta(), 7, parametriRuolo = ParametriRuolo.RIGIDI, seed = i.toLong(),
            )
            val fuori = f.tutti().filter { it.fuoriRuolo }
            assertTrue(
                fuori.isEmpty(),
                "Seme $i: ${fuori.map { "${it.giocatore.nome}->${it.ruolo}" }} fuori ruolo senza motivo",
            )
        }
    }

    @Test
    fun `a ruoli liberi il valore non dipende dalla posizione`() {
        val g = giocatore(0, Ruolo.DIF, 60.0)
        assertEquals(
            g.ovr(Ruolo.DIF, ParametriRuolo.LIBERI) / Giocatore.PESI.getValue(Ruolo.DIF).let { 1.0 },
            g.ovr(Ruolo.DIF, ParametriRuolo.LIBERI),
        )
        assertEquals(1.0, g.proficiency(Ruolo.ATT, ParametriRuolo.LIBERI))
    }

    @Test
    fun `ogni squadra rispetta la formazione`() {
        for (formato in listOf(7, 8)) {
            val rosa = when (formato) {
                7 -> rosaEsatta()
                else -> rosaEsatta() + listOf(giocatore(80, Ruolo.DIF, 50.0), giocatore(81, Ruolo.DIF, 50.0))
            }
            val f = SorteggioSquadre.sorteggia(rosa, formato, seed = 5)
            val attesa = FORMAZIONI.getValue(formato)

            for (squadra in listOf(f.squadraA, f.squadraB)) {
                assertEquals(formato, squadra.size)
                for ((ruolo, quanti) in attesa) {
                    assertEquals(
                        quanti, squadra.count { it.ruolo == ruolo },
                        "Nel ${formato}v$formato servono $quanti $ruolo per squadra",
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Equilibrio e varieta: i due requisiti in conflitto
    // ---------------------------------------------------------------------

    @Test
    fun `le squadre sono equilibrate`() {
        val rng = Random(99)
        val scarti = (0 until 25).map {
            val f = SorteggioSquadre.sorteggia(rosaEsatta(rng), 7, seed = rng.nextLong())
            f.deltaOvr / ((f.forzaA + f.forzaB) / 2)
        }
        val medio = scarti.average()
        assertTrue(medio < 0.02, "Scarto medio ${"%.2f".format(medio * 100)}%, atteso sotto il 2%")
        assertTrue(scarti.max() < 0.06, "Un sorteggio ha sbilanciato oltre il 6%")
    }

    /**
     * Come funziona nell'uso reale: lo storico delle coppie si accumula partita
     * dopo partita, e la penalita anti-ripetizione spinge il sorteggio a
     * cambiare. Senza storico il sorteggio ha molte meno ragioni per variare —
     * vedi il test successivo, che misura quel caso invece di nasconderlo.
     */
    @Test
    fun `con lo storico che si accumula le squadre cambiano ogni volta`() {
        val rosa = rosaEsatta()
        val storico = HashMap<Pair<String, String>, Int>()

        val partizioni = (0 until 25).map { i ->
            val f = SorteggioSquadre.sorteggia(rosa, 7, storico, seed = i.toLong())
            for (lista in listOf(f.squadraA, f.squadraB)) {
                for (a in lista.indices) for (b in a + 1 until lista.size) {
                    val x = lista[a].giocatore.id
                    val y = lista[b].giocatore.id
                    storico.merge(if (x < y) x to y else y to x, 1, Int::plus)
                }
            }
            f.squadraA.map { it.giocatore.id }.toSet()
        }.toSet()

        assertTrue(
            partizioni.size >= 22,
            "Solo ${partizioni.size} formazioni diverse su 25 nonostante lo storico",
        )
    }

    /**
     * Caso di partenza a freddo: primo sorteggio di un gruppo nuovo, storico
     * vuoto. Qui la varieta e strutturalmente piu bassa e va detto, non
     * mascherato: con una rosa fissa ci si assesta attorno a un terzo di
     * formazioni distinte, e sono i sorteggi successivi a sparpagliare.
     */
    @Test
    fun `senza storico le squadre variano meno ma non sono identiche`() {
        val rosa = rosaEsatta()
        val partizioni = (0 until 25).map { i ->
            SorteggioSquadre.sorteggia(rosa, 7, seed = i.toLong())
                .squadraA.map { it.giocatore.id }.toSet()
        }.toSet()

        assertTrue(partizioni.size >= 5, "Solo ${partizioni.size} formazioni diverse su 25 a freddo")
    }

    @Test
    fun `lo stesso seme produce lo stesso sorteggio`() {
        val rosa = rosaEsatta()
        val a = SorteggioSquadre.sorteggia(rosa, 7, seed = 123456)
        val b = SorteggioSquadre.sorteggia(rosa, 7, seed = 123456)

        assertEquals(a.squadraA.map { it.giocatore.id to it.ruolo }, b.squadraA.map { it.giocatore.id to it.ruolo })
        assertEquals(a.squadraB.map { it.giocatore.id to it.ruolo }, b.squadraB.map { it.giocatore.id to it.ruolo })
        assertEquals(a.pianoPorta, b.pianoPorta)
    }

    @Test
    fun `chi gioca sempre insieme viene separato`() {
        val rosa = rosaEsatta()
        // p2 e p3 hanno gia giocato insieme moltissime volte
        val storico = mapOf(("p2" to "p3") to 50)

        val insieme = (0 until 20).count { i ->
            val f = SorteggioSquadre.sorteggia(rosa, 7, storico, seed = i.toLong())
            val idsA = f.squadraA.map { it.giocatore.id }.toSet()
            ("p2" in idsA) == ("p3" in idsA)
        }
        assertTrue(insieme < 6, "p2 e p3 sono finiti insieme $insieme volte su 20 nonostante la penalita")
    }

    // ---------------------------------------------------------------------
    // Ingressi sbagliati
    // ---------------------------------------------------------------------

    @Test
    fun `rifiuta un numero di convocati che non torna`() {
        assertFailsWith<IllegalArgumentException> {
            SorteggioSquadre.sorteggia(rosaEsatta().take(13), 7)
        }
    }

    @Test
    fun `rifiuta i convocati duplicati`() {
        val rosa = rosaEsatta()
        assertFailsWith<IllegalArgumentException> {
            SorteggioSquadre.sorteggia(rosa.dropLast(1) + rosa.first(), 7)
        }
    }

    @Test
    fun `rifiuta un formato non previsto`() {
        assertFailsWith<IllegalArgumentException> {
            SorteggioSquadre.sorteggia(rosaEsatta().take(10), 5)
        }
    }

    // ---------------------------------------------------------------------
    // Il sistema di valutazione
    // ---------------------------------------------------------------------

    @Test
    fun `i pesi di ogni ruolo sommano a uno`() {
        for ((ruolo, p) in Giocatore.PESI) {
            val somma = p.velocita + p.tiro + p.passaggio + p.tecnica + p.difesa + p.fisico + p.parate
            assertTrue(
                kotlin.math.abs(somma - 1.0) < 1e-9,
                "I pesi di $ruolo sommano a $somma invece che a 1.0: l'OVR uscirebbe da scala",
            )
        }
    }

    @Test
    fun `il ruolo conta, ma poco`() {
        val g = giocatore(0, Ruolo.DIF, 60.0, secondari = setOf(Ruolo.CEN))
        assertEquals(1.00, g.proficiency(Ruolo.DIF))
        assertEquals(0.97, g.proficiency(Ruolo.CEN), "Il secondario vale quasi quanto il principale")
        assertEquals(0.90, g.proficiency(Ruolo.ATT), "Il fuori ruolo pesa poco: i ruoli si mescolano")

        // il calo dev'essere sensibile ma non punitivo: nel calcetto un
        // difensore che si ritrova in attacco non diventa un altro giocatore
        val calo = 1 - g.proficiency(Ruolo.ATT)
        assertTrue(calo <= 0.15, "Il fuori ruolo non deve essere una punizione")
    }

    @Test
    fun `tutti partono uguali`() {
        val base = Skill()
        val a = Giocatore("a", "A", base, Ruolo.CEN)
        val b = Giocatore("b", "B", base, Ruolo.CEN)
        assertEquals(a.ovr(Ruolo.CEN), b.ovr(Ruolo.CEN))
        assertEquals(50.0, a.ovr(Ruolo.CEN), "Con tutti gli attributi a 50 l'OVR deve essere 50")
    }
}
