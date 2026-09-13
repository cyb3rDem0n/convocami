package it.cyb3rdm0n.callmeup.teamdraw

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * I test che contano di piu sono quelli che difendono i difetti trovati in
 * simulazione, perche sono difetti che non fanno fallire niente: producono
 * squadre plausibili e sbagliate.
 *
 *  1. il fuori ruolo come leva per pareggiare le squadre;
 *  2. il portiere trattato come costo invece che come vincolo;
 *  3. la varieta che senza storico si affloscia;
 *  4. l'app che finge di sapere dove mettere gente di cui non sa niente.
 */
class TeamDrawTest {

    // ---------------------------------------------------------------------
    // Costruttori di comodo
    // ---------------------------------------------------------------------

    private fun giocatore(
        n: Int,
        ruolo: Ruolo? = null,
        livello: Double = 50.0,
        parate: Double = 25.0,
        partite: Int = Propensione.PARTITE_PER_CONFIDENZA_PIENA.toInt(),
    ) = Giocatore(
        id = "p$n",
        nome = "Giocatore$n",
        skill = Skill(livello, livello, livello, livello, livello, livello, parate),
        propensione = if (ruolo == null) Propensione.NUOVA else Propensione.dichiarata(ruolo, partite),
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

    // =====================================================================
    // Nessuno ha un ruolo fisso
    // =====================================================================

    @Test
    fun `un giocatore nuovo non ha etichetta e nessun ruolo lo penalizza`() {
        val g = giocatore(0)
        assertNull(g.propensione.etichetta)
        assertEquals("Nuovo", g.etichetta)
        assertEquals(0.0, g.propensione.confidenza)

        // il punto: senza dati, schierarlo ovunque costa esattamente uguale
        for (r in Ruolo.entries) {
            assertEquals(1.0, g.propensione.affinita(r), "Affinita alterata per $r senza dati")
        }
        assertEquals(g.ovrBase(Ruolo.ATT), g.ovr(Ruolo.ATT))
    }

    @Test
    fun `l'etichetta non compare finche non ci sono abbastanza partite`() {
        var p = Propensione.NUOVA
        repeat(Propensione.PARTITE_MINIME_PER_ETICHETTA - 1) {
            p = p.dopoLaPartita(Ruolo.DIF, 1.0)
            assertNull(p.etichetta, "Etichetta assegnata dopo sole ${p.partite} partite")
            assertEquals("Nuovo", p.etichettaTesto)
        }
        p = p.dopoLaPartita(Ruolo.DIF, 1.0)
        assertEquals(Ruolo.DIF, p.etichetta)
        assertEquals("Difensore", p.etichettaTesto)
    }

    @Test
    fun `chi rende uguale ovunque e un jolly, non un dato mancante`() {
        var p = Propensione.NUOVA
        repeat(3) {
            p = p.dopoLaPartita(Ruolo.DIF, 0.6)
                .dopoLaPartita(Ruolo.CEN, 0.6)
                .dopoLaPartita(Ruolo.ATT, 0.6)
        }
        assertTrue(p.partite >= Propensione.PARTITE_MINIME_PER_ETICHETTA)
        assertNull(p.etichetta, "Con rendimento uguale ovunque non deve uscire un ruolo")
        assertEquals("Jolly", p.etichettaTesto)
    }

    @Test
    fun `la confidenza cresce con le partite e satura`() {
        var p = Propensione.NUOVA
        var precedente = 0.0
        repeat(Propensione.PARTITE_PER_CONFIDENZA_PIENA.toInt()) {
            p = p.dopoLaPartita(Ruolo.CEN, 0.5)
            assertTrue(p.confidenza >= precedente)
            precedente = p.confidenza
        }
        assertEquals(1.0, p.confidenza)
        p = p.dopoLaPartita(Ruolo.CEN, 0.5)
        assertEquals(1.0, p.confidenza, "La confidenza non deve superare 1")
    }

    /**
     * Il rischio della profezia che si autoavvera: se l'etichetta si cementasse,
     * chi parte difensore resterebbe difensore per sempre. Il decadimento verso
     * il centro deve consentire di cambiare.
     */
    @Test
    fun `chi cambia modo di giocare puo cambiare etichetta`() {
        var p = Propensione.NUOVA
        repeat(8) { p = p.dopoLaPartita(Ruolo.DIF, 0.8) }
        assertEquals(Ruolo.DIF, p.etichetta)

        repeat(12) { p = p.dopoLaPartita(Ruolo.ATT, 0.9) }
        assertEquals(Ruolo.ATT, p.etichetta, "L'etichetta si e cementata e non segue piu i fatti")
    }

    // =====================================================================
    // Quando non si sa, lo si dice
    // =====================================================================

    @Test
    fun `con tutti giocatori nuovi e uguali si dichiara il sorteggio puro`() {
        val rosa = (0 until 14).map { giocatore(it) }
        val f = SorteggioSquadre.sorteggia(rosa, 7, seed = 1)

        assertEquals(LivelloInformazione.SORTEGGIO_PURO, f.livello)
        assertTrue(f.spiegazione().any { "sorteggio puro" in it.lowercase() })
    }

    /**
     * Il sorteggio puro dev'essere CASUALE, non un ordine fisso: l'alfabetico
     * darebbe sempre le stesse due squadre agli stessi quattordici, settimana
     * dopo settimana, fingendo per giunta di essere un criterio.
     */
    @Test
    fun `il sorteggio puro non e sempre lo stesso`() {
        val rosa = (0 until 14).map { giocatore(it) }
        val partizioni = (0 until 20).map { i ->
            SorteggioSquadre.sorteggia(rosa, 7, seed = i.toLong())
                .squadraA.map { it.giocatore.id }.toSet()
        }.toSet()

        assertTrue(partizioni.size >= 15, "Solo ${partizioni.size} divisioni diverse su 20")
    }

    @Test
    fun `senza etichette ma con forze diverse si pareggia sui rendimenti`() {
        val rng = Random(5)
        val rosa = (0 until 14).map {
            giocatore(it, ruolo = null, livello = 35.0 + rng.nextDouble() * 40)
        }
        val f = SorteggioSquadre.sorteggia(rosa, 7, seed = 3)

        assertEquals(LivelloInformazione.RENDIMENTI, f.livello)
        val scarto = f.deltaOvr / ((f.forzaA + f.forzaB) / 2)
        assertTrue(scarto < 0.04, "Scarto ${"%.1f".format(scarto * 100)}% con forze note")
    }

    @Test
    fun `con i ruoli noti si dichiara il livello pieno`() {
        val f = SorteggioSquadre.sorteggia(rosaEsatta(), 7, seed = 2)
        assertEquals(LivelloInformazione.ETICHETTE, f.livello)
    }

    @Test
    fun `la formazione avvisa sempre che i ruoli sono indicativi`() {
        val f = SorteggioSquadre.sorteggia(rosaEsatta(), 7, seed = 2)
        assertTrue(f.spiegazione().any { "indicativi" in it })
    }

    // =====================================================================
    // Il portiere e un vincolo
    // =====================================================================

    @Test
    fun `con due portieri riconosciuti entrambi stanno in porta`() {
        repeat(30) { i ->
            val f = SorteggioSquadre.sorteggia(rosaEsatta(), 7, seed = i.toLong())
            val inPorta = f.portieri()
            assertTrue(inPorta.all { it.ruolo == Ruolo.POR })
            assertTrue(
                inPorta.all { it.giocatore.propensione.etichetta == Ruolo.POR },
                "Seme $i: un portiere riconosciuto e stato schierato in campo",
            )
            assertEquals(ModalitaPorta.REGOLARE, f.modalitaPorta)
        }
    }

    /**
     * Regressione del difetto originale: due portieri MOLTO diversi fra loro.
     * La versione sbagliata li teneva entrambi fuori dalla porta, perche due
     * ripieghi scarsi avevano fra loro uno scarto minore.
     */
    @Test
    fun `portieri di forza molto diversa restano comunque in porta`() {
        val rosa = buildList {
            add(giocatore(0, Ruolo.POR, 80.0, parate = 90.0))
            add(giocatore(1, Ruolo.POR, 40.0, parate = 45.0))
            var i = 2
            repeat(4) { add(giocatore(i++, Ruolo.DIF, 55.0)) }
            repeat(6) { add(giocatore(i++, Ruolo.CEN, 55.0)) }
            repeat(2) { add(giocatore(i++, Ruolo.ATT, 55.0)) }
        }
        repeat(30) { i ->
            val f = SorteggioSquadre.sorteggia(rosa, 7, seed = i.toLong())
            assertEquals(
                setOf("p0", "p1"), f.portieri().map { it.giocatore.id }.toSet(),
                "Seme $i: i portieri devono stare fra i pali anche se impari",
            )
        }
    }

    @Test
    fun `con un solo portiere l'altra squadra si organizza a turni`() {
        val rosa = rosaEsatta().filterNot { it.id == "p1" } + giocatore(99, Ruolo.CEN, 50.0)
        val f = SorteggioSquadre.sorteggia(rosa, 7, seed = 42)

        assertEquals(ModalitaPorta.UNO_SOLO, f.modalitaPorta)
        assertEquals(1, f.portieri().count { it.giocatore.propensione.etichetta == Ruolo.POR })
        assertEquals(1, f.pianoPorta.map { it.squadra }.toSet().size, "Solo una squadra deve ruotare")
    }

    @Test
    fun `senza nessun portiere ruotano entrambe le squadre`() {
        val rosa = rosaEsatta().filterNot { it.propensione.etichetta == Ruolo.POR } +
            listOf(giocatore(90, Ruolo.CEN, 50.0), giocatore(91, Ruolo.DIF, 50.0))
        val f = SorteggioSquadre.sorteggia(rosa, 7, seed = 7)

        assertEquals(ModalitaPorta.A_TURNO, f.modalitaPorta)
        assertEquals(setOf(Squadra.A, Squadra.B), f.pianoPorta.map { it.squadra }.toSet())
    }

    @Test
    fun `anche a sorteggio puro qualcuno finisce in porta, e a turni`() {
        val rosa = (0 until 14).map { giocatore(it) }
        val f = SorteggioSquadre.sorteggia(rosa, 7, seed = 9)

        assertEquals(ModalitaPorta.A_TURNO, f.modalitaPorta)
        assertEquals(setOf(Squadra.A, Squadra.B), f.pianoPorta.map { it.squadra }.toSet())
        assertTrue(f.portieri().all { it.ruolo == Ruolo.POR })
    }

    // =====================================================================
    // La turnazione
    // =====================================================================

    @Test
    fun `la turnazione copre la partita senza buchi ne doppioni`() {
        val rosa = rosaEsatta().filterNot { it.propensione.etichetta == Ruolo.POR } +
            listOf(giocatore(90, Ruolo.CEN, 50.0), giocatore(91, Ruolo.DIF, 50.0))
        val f = SorteggioSquadre.sorteggia(rosa, 7, durataMin = 60, seed = 3)

        for (lato in listOf(Squadra.A, Squadra.B)) {
            val turni = f.pianoPorta.filter { it.squadra == lato }.sortedBy { it.dalMinuto }
            val rosaSquadra = if (lato == Squadra.A) f.squadraA else f.squadraB

            assertEquals(0, turni.first().dalMinuto)
            assertEquals(60, turni.last().alMinuto)
            assertEquals(60, turni.sumOf { it.minuti })
            assertEquals(turni.size, turni.map { it.giocatoreId }.toSet().size)
            for (i in 1 until turni.size) {
                assertEquals(turni[i - 1].alMinuto, turni[i].dalMinuto, "Buco fra i turni")
            }
            assertEquals(rosaSquadra.first().giocatore.id, turni.first().giocatoreId)
            assertTrue(turni.all { t -> rosaSquadra.any { it.giocatore.id == t.giocatoreId } })
        }
    }

    @Test
    fun `con due portieri non si perde tempo a fare turnazioni`() {
        assertTrue(SorteggioSquadre.sorteggia(rosaEsatta(), 7, seed = 11).pianoPorta.isEmpty())
    }

    /**
     * Il punto non e che qualcuno vada in porta, ma che non sia sempre lo
     * stesso: senza il conteggio dei turni passati, chi para un po' meglio
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
            val f = SorteggioSquadre.sorteggia(
                rosa.shuffled(rng).take(14), 7, turniInPorta = turni, durataMin = 60,
                seed = rng.nextLong(),
            )
            for (t in f.pianoPorta) {
                turni.merge(t.giocatoreId, 1, Int::plus)
                minuti.merge(t.giocatoreId, t.minuti, Int::plus)
            }
        }

        assertEquals(rosa.size, minuti.size, "Nessuno deve restare fuori dalla rotazione")
        val rapporto = minuti.values.max().toDouble() / minuti.values.min()
        assertTrue(rapporto < 1.5, "Squilibrio in porta: ${"%.2f".format(rapporto)}x")
    }

    // =====================================================================
    // Ruoli in formazione
    // =====================================================================

    @Test
    fun `con ruoli fluidi lo spostamento resta contenuto`() {
        var lontani = 0
        var totale = 0
        repeat(20) { i ->
            val f = SorteggioSquadre.sorteggia(rosaEsatta(), 7, seed = i.toLong())
            lontani += f.tutti().count { it.lontanoDalSuoRuolo }
            totale += f.tutti().size
        }
        val quota = lontani.toDouble() / totale
        assertTrue(quota < 0.30, "Spostati nel ${"%.0f".format(quota * 100)}% dei casi")
    }

    /**
     * Con ruoli rigidi e dotazione esatta nessuno deve giocare fuori ruolo: se
     * succede, l'ottimizzatore sta di nuovo usando lo spostamento come leva per
     * pareggiare, che e il difetto originale.
     */
    @Test
    fun `con ruoli rigidi e dotazione esatta nessuno viene spostato`() {
        repeat(20) { i ->
            val f = SorteggioSquadre.sorteggia(
                rosaEsatta(), 7, parametriRuolo = ParametriRuolo.RIGIDI, seed = i.toLong(),
            )
            val fuori = f.tutti().filter { it.lontanoDalSuoRuolo }
            assertTrue(
                fuori.isEmpty(),
                "Seme $i: ${fuori.map { "${it.giocatore.nome}->${it.ruolo}" }} spostati senza motivo",
            )
        }
    }

    @Test
    fun `a ruoli liberi la posizione non cambia il valore`() {
        val g = giocatore(0, Ruolo.DIF, 60.0)
        assertEquals(1.0, g.propensione.affinita(Ruolo.ATT, ParametriRuolo.LIBERI))
        assertEquals(g.ovrBase(Ruolo.ATT), g.ovr(Ruolo.ATT, ParametriRuolo.LIBERI))
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
                    assertEquals(quanti, squadra.count { it.ruolo == ruolo }, "$formato: servono $quanti $ruolo")
                }
            }
        }
    }

    /** Sei attaccanti non sono un problema: i posti si riempiono comunque. */
    @Test
    fun `una rosa tutta sbilanciata su un ruolo non rompe niente`() {
        val rosa = (0 until 14).map { giocatore(it, Ruolo.ATT, 45.0 + it) }
        val f = SorteggioSquadre.sorteggia(rosa, 7, seed = 4)

        assertEquals(7, f.squadraA.size)
        assertEquals(7, f.squadraB.size)
        assertEquals(setOf(Squadra.A, Squadra.B), f.pianoPorta.map { it.squadra }.toSet())
    }

    // =====================================================================
    // Equilibrio e varieta: i due requisiti in conflitto
    // =====================================================================

    @Test
    fun `le squadre sono equilibrate`() {
        val rng = Random(99)
        val scarti = (0 until 25).map {
            val f = SorteggioSquadre.sorteggia(rosaEsatta(rng), 7, seed = rng.nextLong())
            f.deltaOvr / ((f.forzaA + f.forzaB) / 2)
        }
        assertTrue(scarti.average() < 0.02, "Scarto medio ${"%.2f".format(scarti.average() * 100)}%")
        assertTrue(scarti.max() < 0.06, "Un sorteggio ha sbilanciato oltre il 6%")
    }

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

        assertTrue(partizioni.size >= 22, "Solo ${partizioni.size} formazioni diverse su 25")
    }

    /**
     * Partenza a freddo: primo sorteggio, storico vuoto. Qui la varieta e
     * strutturalmente piu bassa, e va misurata invece che mascherata.
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
        val storico = mapOf(("p2" to "p3") to 50)
        val insieme = (0 until 20).count { i ->
            val idsA = SorteggioSquadre.sorteggia(rosa, 7, storico, seed = i.toLong())
                .squadraA.map { it.giocatore.id }.toSet()
            ("p2" in idsA) == ("p3" in idsA)
        }
        assertTrue(insieme < 6, "p2 e p3 insieme $insieme volte su 20 nonostante la penalita")
    }

    // =====================================================================
    // L'organizzatore rimaneggia
    // =====================================================================

    @Test
    fun `lo scarto si ricalcola su una formazione rimaneggiata a mano`() {
        val f = SorteggioSquadre.sorteggia(rosaEsatta(), 7, seed = 8)
        assertEquals(f.deltaOvr, SorteggioSquadre.scarto(f.squadraA, f.squadraB), 1e-9)

        // l'organizzatore scambia due giocatori di movimento fra le squadre
        val a = f.squadraA.toMutableList()
        val b = f.squadraB.toMutableList()
        val tmp = a[3]
        a[3] = b[3]
        b[3] = tmp

        val nuovo = SorteggioSquadre.scarto(a, b)
        assertTrue(nuovo >= 0.0)
        assertTrue(
            abs(nuovo - f.deltaOvr) > 1e-9 || quasiUgualiForze(a, b),
            "Lo scarto deve seguire le modifiche dell'organizzatore",
        )
    }

    private fun quasiUgualiForze(a: List<Slot>, b: List<Slot>) =
        abs(a.sumOf { it.ovr } - b.sumOf { it.ovr }) < 1e-9

    // =====================================================================
    // Ingressi sbagliati
    // =====================================================================

    @Test
    fun `rifiuta un numero di convocati che non torna`() {
        assertFailsWith<IllegalArgumentException> { SorteggioSquadre.sorteggia(rosaEsatta().take(13), 7) }
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
        assertFailsWith<IllegalArgumentException> { SorteggioSquadre.sorteggia(rosaEsatta().take(10), 5) }
    }

    @Test
    fun `rifiuta un rendimento fuori scala`() {
        assertFailsWith<IllegalArgumentException> { Propensione.NUOVA.dopoLaPartita(Ruolo.CEN, 1.5) }
    }

    // =====================================================================
    // Il sistema di valutazione
    // =====================================================================

    @Test
    fun `i pesi di ogni ruolo sommano a uno`() {
        for ((ruolo, p) in Giocatore.PESI) {
            val somma = p.velocita + p.tiro + p.passaggio + p.tecnica + p.difesa + p.fisico + p.parate
            assertTrue(abs(somma - 1.0) < 1e-9, "I pesi di $ruolo sommano a $somma: l'OVR esce da scala")
        }
    }

    @Test
    fun `tutti partono uguali`() {
        val a = Giocatore("a", "A")
        val b = Giocatore("b", "B")
        assertEquals(a.ovr(Ruolo.CEN), b.ovr(Ruolo.CEN))
        assertEquals(50.0, a.ovr(Ruolo.CEN), "Con tutti gli attributi a 50 l'OVR deve essere 50")
    }

    @Test
    fun `il ruolo conta, ma poco`() {
        var p = Propensione.NUOVA
        repeat(10) { p = p.dopoLaPartita(Ruolo.DIF, 0.9) }
        val g = Giocatore("g", "G", Skill(), p)

        assertNotNull(g.propensione.etichetta)
        val calo = 1 - g.propensione.affinita(Ruolo.ATT)
        assertTrue(calo in 0.01..0.15, "Il calo fuori ruolo e ${"%.2f".format(calo)}: non dev'essere una punizione")
    }
}

/**
 * Le fasi di gioco e il voto di fine partita. Si vota per fase e non per ruolo
 * perche un difensore puo essere il miglior regista in campo, e chiedere "chi e
 * stato il miglior centrocampista" quel dato lo perderebbe.
 */
class VotazioneTest {

    private val convocati = (0 until 14).map { "p$it" }
    private val inPorta = setOf("p0", "p7")

    private fun tutti(fase: Fase, votato: String, tranne: Set<String> = emptySet()) =
        convocati.filter { it != votato && it !in tranne }.map { Nomina(it, fase, votato) }

    @Test
    fun `ogni fase punta al ruolo che le corrisponde`() {
        assertEquals(Ruolo.DIF, Fase.DIFESA.ruolo)
        assertEquals(Ruolo.ATT, Fase.ATTACCO.ruolo)
        assertEquals(Ruolo.CEN, Fase.REGIA.ruolo)
        assertEquals(Ruolo.POR, Fase.PORTA.ruolo)
        assertEquals(4, Fase.entries.map { it.ruolo }.toSet().size, "Le fasi devono coprire i quattro ruoli")
    }

    @Test
    fun `non si vota se stessi`() {
        assertTrue(!Votazione.validaNomina(Nomina("p1", Fase.DIFESA, "p1"), convocati.toSet(), inPorta))
    }

    @Test
    fun `per la porta si nomina solo chi ci e stato`() {
        assertTrue(Votazione.validaNomina(Nomina("p1", Fase.PORTA, "p0"), convocati.toSet(), inPorta))
        assertTrue(!Votazione.validaNomina(Nomina("p1", Fase.PORTA, "p5"), convocati.toSet(), inPorta))
    }

    @Test
    fun `lasciare in bianco e una risposta legittima`() {
        assertTrue(Votazione.validaNomina(Nomina("p1", Fase.REGIA, null), convocati.toSet(), inPorta))
    }

    @Test
    fun `chi prende tutte le nomine di una fase sfonda in quella fase`() {
        val r = Votazione.rendimenti(tutti(Fase.DIFESA, "p3"), convocati, inPorta)
        val p3 = r.first { it.giocatoreId == "p3" }
        assertEquals(1.0, p3.perFase.getValue(Fase.DIFESA))
        assertTrue(p3.perFase.getValue(Fase.ATTACCO) <= 0.0, "Una fase non deve contagiare le altre")
    }

    /** Non essere nominati non e giocare male: in ogni fase ne vince uno solo. */
    @Test
    fun `chi non prende nomine scende poco`() {
        val r = Votazione.rendimenti(tutti(Fase.DIFESA, "p3"), convocati, inPorta)
        val altro = r.first { it.giocatoreId == "p9" }
        val voto = altro.perFase.getValue(Fase.DIFESA)
        assertTrue(voto < 0.0, "Zero nomine deve pesare un po'")
        assertTrue(voto > -0.5, "Zero nomine non deve essere una condanna: era $voto")
    }

    @Test
    fun `senza nomine nessuno si muove`() {
        val r = Votazione.rendimenti(emptyList(), convocati, inPorta)
        assertTrue(r.all { it.perFase.values.all { v -> v == 0.0 } })
    }

    @Test
    fun `chi non e stato in porta non viene giudicato sulle parate`() {
        val r = Votazione.rendimenti(tutti(Fase.PORTA, "p0"), convocati, inPorta)
        assertEquals(0.0, r.first { it.giocatoreId == "p5" }.perFase.getValue(Fase.PORTA))
        assertTrue(r.first { it.giocatoreId == "p0" }.perFase.getValue(Fase.PORTA) > 0.5)
    }

    @Test
    fun `le nomine non valide vengono scartate e non contano`() {
        val sporche = listOf(
            Nomina("p1", Fase.DIFESA, "p1"),        // vota se stesso
            Nomina("p1", Fase.PORTA, "p5"),          // p5 non ha parato
            Nomina("estraneo", Fase.DIFESA, "p3"),   // non era convocato
        )
        val r = Votazione.rendimenti(sporche, convocati, inPorta)
        assertTrue(r.all { it.perFase.values.all { v -> v == 0.0 } })
    }

    // --------------------------------------------------------------
    // Dal voto al profilo
    // --------------------------------------------------------------

    /**
     * Il punto di votare le fasi: un difensore nominato per la regia diventa un
     * centrocampista agli occhi dell'app, anche se ha sempre giocato dietro.
     */
    @Test
    fun `le nomine per la regia spostano il profilo verso il centrocampo`() {
        var g = Giocatore("p3", "Terzo", propensione = Propensione.diPartenza(Ruolo.DIF))
        repeat(10) {
            val r = Votazione.rendimenti(tutti(Fase.REGIA, "p3"), convocati, inPorta)
            g = Crescita.applica(g, r.first { it.giocatoreId == "p3" })
        }
        assertEquals(Ruolo.CEN, g.propensione.etichetta, "Il profilo non ha seguito i voti")
        assertEquals("Centrocampista", g.etichetta)
    }

    @Test
    fun `ogni fase alza la caratteristica che le compete`() {
        val base = Giocatore("p3", "Terzo")
        val r = Votazione.rendimenti(tutti(Fase.DIFESA, "p3"), convocati, inPorta)
            .first { it.giocatoreId == "p3" }
        val dopo = Crescita.applica(base, r)

        assertTrue(dopo.skill.difesa > base.skill.difesa, "La difesa deve salire")
        assertTrue(dopo.skill.tiro <= base.skill.tiro, "Il tiro non c'entra con la fase difensiva")
        assertTrue(dopo.skill.parate <= base.skill.parate, "Le parate non c'entrano")
    }

    @Test
    fun `la crescita rallenta ai valori alti`() {
        val forte = Giocatore("a", "A", Skill(difesa = 92.0))
        val medio = Giocatore("b", "B", Skill(difesa = 50.0))
        val r = { id: String ->
            Votazione.rendimenti(tutti(Fase.DIFESA, "p3"), convocati, inPorta)
                .first { it.giocatoreId == "p3" }.copy(giocatoreId = id)
        }
        val dForte = Crescita.applica(forte, r("a")).skill.difesa - 92.0
        val dMedio = Crescita.applica(medio, r("b")).skill.difesa - 50.0

        assertTrue(dForte in 0.0..dMedio, "Salire da 92 deve costare piu che da 50: $dForte vs $dMedio")
    }

    @Test
    fun `nessuna caratteristica esce mai dalla scala`() {
        var g = Giocatore("p3", "Terzo", Skill(difesa = 98.5))
        repeat(50) {
            val r = Votazione.rendimenti(tutti(Fase.DIFESA, "p3"), convocati, inPorta)
            g = Crescita.applica(g, r.first { it.giocatoreId == "p3" })
        }
        assertTrue(g.skill.difesa <= 99.0, "Sforata la scala: ${g.skill.difesa}")
        assertTrue(g.skill.difesa > 90.0)
    }
}

/** Il profilo di partenza scelto all'iscrizione. */
class ProfiloDiPartenzaTest {

    @Test
    fun `chi dichiara un profilo ha subito un'etichetta, marcata come tale`() {
        val p = Propensione.diPartenza(Ruolo.ATT)
        assertEquals(Ruolo.ATT, p.etichetta)
        assertEquals("Attaccante · di partenza", p.etichettaTesto)
        assertTrue(!p.confermataDaiFatti)
    }

    @Test
    fun `un profilo di partenza orienta il primo sorteggio senza fissare nessuno`() {
        val p = Propensione.diPartenza(Ruolo.ATT)
        assertTrue(p.confidenza > 0.0, "Deve contare qualcosa, altrimenti e inutile dichiararlo")
        assertTrue(p.confidenza < 0.5, "Non deve contare quanto le partite vere")
        assertTrue(p.affinita(Ruolo.ATT) > p.affinita(Ruolo.DIF))
    }

    @Test
    fun `dopo qualche partita l'etichetta smette di essere di partenza`() {
        var p = Propensione.diPartenza(Ruolo.ATT)
        repeat(Propensione.PARTITE_MINIME_PER_ETICHETTA) { p = p.dopoLaPartita(Ruolo.ATT, 0.8) }
        assertTrue(p.confermataDaiFatti)
        assertEquals("Attaccante", p.etichettaTesto)
    }

    /** I fatti battono le intenzioni: altrimenti si dichiarerebbero tutti attaccanti. */
    @Test
    fun `chi si dichiara attaccante ma difende viene riclassificato`() {
        var p = Propensione.diPartenza(Ruolo.ATT)
        repeat(10) { p = p.dopoLaPartita(Ruolo.DIF, 0.9) }
        assertEquals(Ruolo.DIF, p.etichetta, "Il profilo dichiarato ha prevalso sui fatti")
        assertEquals("Difensore", p.etichettaTesto)
    }

    @Test
    fun `il profilo si disegna come quattro barrette normalizzate`() {
        val profilo = Propensione.diPartenza(Ruolo.ATT).profilo()
        assertEquals(4, profilo.size)
        assertEquals(1.0, profilo.getValue(Ruolo.ATT))
        assertTrue(profilo.values.all { it in 0.0..1.0 })
    }
}

/**
 * Il portiere e il caso speciale: si dichiara e non si deduce.
 *
 * Se uno e indicato portiere nel suo profilo, in porta ci va praticamente
 * sempre; e se per una volta deve giocare in campo, e l'organizzatore a
 * spostarlo. Il contrario dev'essere altrettanto vero: chi portiere non e non
 * deve poterlo diventare per via dei voti.
 */
class PortiereTest {

    private fun campo(n: Int, parate: Double = 25.0) =
        Giocatore("p$n", "Giocatore$n", Skill(parate = parate), Propensione.diPartenza(Ruolo.CEN))

    private fun tra_i_pali(n: Int) =
        Giocatore("p$n", "Portiere$n", Skill(parate = 80.0), Propensione.portiere())

    @Test
    fun `chi e dichiarato portiere ha subito l'etichetta, senza aspettare partite`() {
        val p = Propensione.portiere()
        assertEquals(Ruolo.POR, p.etichetta)
        assertEquals("Portiere", p.etichettaTesto)
        assertTrue(p.portiere)
        assertEquals(1.0, p.confidenza, "Su chi para non c'e incertezza da accumulare")
    }

    /** Il caso Fabio: un centrocampista che se la cava durante una turnazione. */
    @Test
    fun `chi para bene per una stagione intera non diventa portiere`() {
        var g = campo(1)
        repeat(40) {
            g = Crescita.applica(g, RendimentoPartita("p1", mapOf(Fase.PORTA to 1.0)))
        }
        assertTrue(!g.propensione.portiere, "Un voto non puo promuovere nessuno a portiere")
        assertTrue(
            g.propensione.etichetta != Ruolo.POR,
            "Etichettato portiere dai soli voti: ${g.etichetta}",
        )
        // il merito pero si vede: parera meglio degli altri quando serve improvvisare
        assertTrue(g.skill.parate > 25.0, "Le parate devono comunque essere salite")
    }

    @Test
    fun `un portiere resta portiere anche se lo votano per l'attacco`() {
        var g = tra_i_pali(1)
        repeat(40) {
            g = Crescita.applica(g, RendimentoPartita("p1", mapOf(Fase.ATTACCO to 1.0)))
        }
        assertTrue(g.propensione.portiere)
        assertEquals(Ruolo.POR, g.propensione.etichetta)
        assertEquals("Portiere", g.etichetta)
    }

    @Test
    fun `la propensione al ruolo di portiere non si muove con i voti`() {
        val prima = Propensione.diPartenza(Ruolo.CEN)
        val dopo = prima.dopoLaVotazione(mapOf(Fase.PORTA to 1.0, Fase.REGIA to 1.0))
        assertEquals(prima.peso(Ruolo.POR), dopo.peso(Ruolo.POR))
        assertTrue(dopo.peso(Ruolo.CEN) > prima.peso(Ruolo.CEN), "La regia invece deve muoversi")
    }

    @Test
    fun `i portieri dichiarati vanno in porta, gli altri no`() {
        val rosa = listOf(tra_i_pali(0), tra_i_pali(1)) + (2 until 14).map { campo(it, parate = 70.0) }
        repeat(25) { i ->
            val f = SorteggioSquadre.sorteggia(rosa, 7, seed = i.toLong())
            assertEquals(
                setOf("p0", "p1"),
                setOf(f.squadraA[0].giocatore.id, f.squadraB[0].giocatore.id),
                "Seme $i: in porta e finito qualcuno che non e portiere",
            )
            assertEquals(ModalitaPorta.REGOLARE, f.modalitaPorta)
        }
    }

    /**
     * Con un portiere solo, nessun giocatore di movimento deve poter finire
     * nell'altra porta al posto suo: l'unico portiere resta sempre fra i pali.
     */
    @Test
    fun `l'unico portiere non viene mai messo in campo`() {
        val rosa = listOf(tra_i_pali(0)) + (1 until 14).map { campo(it, parate = 70.0) }
        repeat(25) { i ->
            val f = SorteggioSquadre.sorteggia(rosa, 7, seed = i.toLong())
            val inPorta = setOf(f.squadraA[0].giocatore.id, f.squadraB[0].giocatore.id)
            assertTrue("p0" in inPorta, "Seme $i: il portiere e stato schierato in campo")
            assertEquals(ModalitaPorta.UNO_SOLO, f.modalitaPorta)
        }
    }

    @Test
    fun `il portiere non entra nella turnazione dell'altra squadra`() {
        val rosa = listOf(tra_i_pali(0)) + (1 until 14).map { campo(it) }
        val f = SorteggioSquadre.sorteggia(rosa, 7, seed = 3)
        assertTrue(
            f.pianoPorta.none { it.giocatoreId == "p0" },
            "Il portiere di ruolo non va messo a turni: sta in porta e basta",
        )
    }

    @Test
    fun `l'organizzatore puo togliere o dare il ruolo di portiere`() {
        val era = Propensione.portiere()
        val ora = era.copy(portiere = false, profiloIniziale = null)
        assertTrue(ora.etichetta != Ruolo.POR, "Deve poter smettere, ma solo per mano dell'admin")

        val promosso = Propensione.diPartenza(Ruolo.ATT).copy(portiere = true, profiloIniziale = null)
        assertEquals(Ruolo.POR, promosso.etichetta)
    }

    @Test
    fun `un portiere non puo avere un profilo iniziale di movimento`() {
        assertFailsWith<IllegalArgumentException> {
            Propensione.portiere().copy(profiloIniziale = Ruolo.ATT)
        }
    }
}

/**
 * I ruoli dichiarabili sono due.
 *
 * Con una casella sola, di fronte a quattordici persone, si dichiarano tutti
 * attaccanti e non resta niente da usare. Con due, chi si iscrive dice di fatto
 * dove NON gioca, ed e quella l'informazione utile.
 */
class DueRuoliDiPartenzaTest {

    @Test
    fun `finche sono intenzioni si mostrano tutti e due`() {
        val p = Propensione.diPartenza(Ruolo.ATT, Ruolo.CEN)
        assertEquals(Ruolo.ATT, p.etichetta, "Il primo resta il primo")
        assertEquals("Attaccante o centrocampista · di partenza", p.etichettaTesto)
    }

    @Test
    fun `il secondo ruolo sta in mezzo, non in concorrenza col primo`() {
        val p = Propensione.diPartenza(Ruolo.ATT, Ruolo.CEN)
        assertTrue(p.affinita(Ruolo.ATT) > p.affinita(Ruolo.CEN), "Il primo deve restare il primo")
        assertTrue(p.affinita(Ruolo.CEN) > p.affinita(Ruolo.DIF), "Il secondo deve contare qualcosa")
    }

    /** Dichiararne due non deve valere piu di dichiararne uno. */
    @Test
    fun `dichiararne due non da piu credito di dichiararne uno`() {
        val uno = Propensione.diPartenza(Ruolo.ATT)
        val due = Propensione.diPartenza(Ruolo.ATT, Ruolo.CEN)
        assertEquals(uno.confidenza, due.confidenza)
        assertTrue(!due.confermataDaiFatti)
    }

    /** I fatti battono le intenzioni, tutte e due. */
    @Test
    fun `chi si dichiara attaccante o centrocampista ma difende diventa difensore`() {
        var p = Propensione.diPartenza(Ruolo.ATT, Ruolo.CEN)
        repeat(10) { p = p.dopoLaPartita(Ruolo.DIF, 0.9) }
        assertEquals(Ruolo.DIF, p.etichetta)
        assertEquals("Difensore", p.etichettaTesto, "Coi fatti in mano si mostra un ruolo solo")
    }

    @Test
    fun `il secondo ruolo si vede nel sorteggio`() {
        val jolly = Giocatore(
            id = "j", nome = "Jolly",
            skill = Skill(60.0, 60.0, 60.0, 60.0, 60.0, 60.0),
            propensione = Propensione.diPartenza(Ruolo.ATT, Ruolo.CEN),
        )
        val puro = jolly.copy(
            id = "p", nome = "Puro",
            propensione = Propensione.diPartenza(Ruolo.ATT),
        )
        assertTrue(
            jolly.ovr(Ruolo.CEN) > puro.ovr(Ruolo.CEN),
            "Chi si e dichiarato anche centrocampista deve valere di piu a centrocampo",
        )
        assertEquals(jolly.ovr(Ruolo.DIF), puro.ovr(Ruolo.DIF), 0.001, "Altrove nessuna differenza")
    }

    @Test
    fun `i due ruoli devono essere diversi`() {
        assertFailsWith<IllegalArgumentException> { Propensione.diPartenza(Ruolo.ATT, Ruolo.ATT) }
    }

    /** Altrimenti il vincolo del portiere si scavalca dichiarandosi secondo portiere. */
    @Test
    fun `il portiere non si dichiara come secondo ruolo`() {
        assertFailsWith<IllegalArgumentException> { Propensione.diPartenza(Ruolo.DIF, Ruolo.POR) }
    }

    @Test
    fun `chi para non ha un secondo ruolo`() {
        assertFailsWith<IllegalArgumentException> {
            Propensione.portiere().copy(profiloSecondario = Ruolo.DIF)
        }
    }

    @Test
    fun `il secondo ruolo da solo non esiste`() {
        assertFailsWith<IllegalArgumentException> {
            Propensione.NUOVA.copy(profiloSecondario = Ruolo.CEN)
        }
    }
}
