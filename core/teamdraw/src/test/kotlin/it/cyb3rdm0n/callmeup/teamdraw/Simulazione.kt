package it.cyb3rdm0n.callmeup.teamdraw

import kotlin.random.Random
import kotlin.system.measureNanoTime

/**
 * Banco di prova del motore. Non e un test: e lo strumento con cui si misurano
 * equilibrio, varieta, tempi e convergenza delle etichette su stagioni intere, e
 * con cui sono stati trovati i difetti che [TeamDrawTest] ora presidia.
 */
object Simulazione {

    private fun gauss(rng: Random, mu: Double, sigma: Double): Double {
        val u1 = rng.nextDouble().coerceAtLeast(1e-9)
        val u2 = rng.nextDouble()
        return mu + sigma * Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2 * Math.PI * u2)
    }

    private val NOMI = listOf(
        "Giuseppe", "Marco", "Luca", "Andrea", "Fabio", "Stefano", "Davide",
        "Matteo", "Simone", "Alessio", "Riccardo", "Paolo", "Antonio", "Gianni",
        "Emanuele", "Roberto", "Nicola", "Dario", "Michele", "Federico",
    )

    /**
     * Un giocatore come lo vede l'universo, non come lo vede l'app: ha un ruolo
     * in cui rende davvero meglio, che l'app NON conosce e deve dedurre.
     */
    data class Vero(val giocatore: Giocatore, val ruoloVero: Ruolo, val bravura: Double)

    fun rosaVera(rng: Random, n: Int = 20, conPortieri: Int = 2): List<Vero> {
        val ruoli = buildList {
            repeat(conPortieri) { add(Ruolo.POR) }
            while (size < n) {
                add(Ruolo.DIF); if (size < n) add(Ruolo.CEN)
                if (size < n) add(Ruolo.CEN); if (size < n) add(Ruolo.ATT)
            }
        }
        return (0 until n).map { i ->
            val vero = ruoli[i]
            val bravura = gauss(rng, 52.0, 11.0).coerceIn(32.0, 88.0)
            fun a() = (bravura + gauss(rng, 0.0, 5.0)).coerceIn(15.0, 95.0)
            val parate = if (vero == Ruolo.POR) (bravura + 18).coerceAtMost(95.0)
            else gauss(rng, 25.0, 6.0).coerceIn(15.0, 95.0)
            Vero(
                Giocatore(
                    "p$i", NOMI[i % NOMI.size], Skill(a(), a(), a(), a(), a(), a(), parate),
                    // chi para lo si sa dal primo giorno: il portiere si dichiara,
                    // non si deduce. Tutti gli altri partono senza ruolo.
                    if (vero == Ruolo.POR) Propensione.portiere() else Propensione.NUOVA,
                ),
                vero, bravura,
            )
        }
    }

    /**
     * Simula la votazione di fine partita: ognuno nomina, per ciascuna fase,
     * chi secondo lui si e distinto. Chi e davvero portato per quella fase
     * viene notato piu spesso, ma con parecchio rumore, perche una partita sola
     * dice poco e la gente guarda il pallone, non le statistiche.
     */
    private fun votazione(
        inCampo: List<Vero>, chiHaParato: Set<String>, rng: Random,
    ): List<Nomina> = buildList {
        for (votante in inCampo) {
            for (fase in Fase.entries) {
                val candidati = inCampo.filter {
                    it.giocatore.id != votante.giocatore.id &&
                        (fase != Fase.PORTA || it.giocatore.id in chiHaParato)
                }
                if (candidati.isEmpty()) { add(Nomina(votante.giocatore.id, fase, null)); continue }

                val scelto = candidati.maxBy { v ->
                    val portato = if (v.ruoloVero == fase.ruolo) 30.0 else 0.0
                    portato + v.bravura + gauss(rng, 0.0, 15.0)
                }
                // ogni tanto nessuno si e distinto davvero
                add(Nomina(votante.giocatore.id, fase, if (rng.nextDouble() < 0.15) null else scelto.giocatore.id))
            }
        }
    }

    /**
     * La prova piu importante del modello: si parte da ZERO, con tutti senza
     * ruolo, e si guarda se dopo una stagione l'app ha capito da sola chi gioca
     * dove — usando solo le nomine dei compagni, come nella realta.
     */
    fun convergenza(partite: Int = 40, seme: Long = 11) {
        // I portieri sono gia dichiarati: quello che si deve dedurre sono i
        // ruoli di movimento, che e l'unica cosa deducibile.
        val rng = Random(seme)
        var rosa = rosaVera(rng)
        val turni = HashMap<String, Int>()
        val storico = HashMap<Pair<String, String>, Int>()
        val livelli = HashMap<LivelloInformazione, Int>()
        val tappe = listOf(5, 10, 20, 40)

        println("Convergenza delle etichette - si parte senza sapere niente di nessuno")
        println("")
        println("  partita   con etichetta   di cui giuste   livello dichiarato")

        repeat(partite) { n ->
            val convocati = rosa.shuffled(rng).take(14)
            val f = SorteggioSquadre.sorteggia(
                convocati.map { it.giocatore }, 7, storico, turni, seed = rng.nextLong(),
            )
            livelli.merge(f.livello, 1, Int::plus)

            val chiHaParato = (f.pianoPorta.map { it.giocatoreId } +
                listOf(f.squadraA[0].giocatore.id, f.squadraB[0].giocatore.id)).toSet()

            val nomine = votazione(convocati, chiHaParato, rng)
            val rendimenti = Votazione.rendimenti(
                nomine, convocati.map { it.giocatore.id }, chiHaParato,
            ).associateBy { it.giocatoreId }

            rosa = rosa.map { v ->
                rendimenti[v.giocatore.id]
                    ?.let { v.copy(giocatore = Crescita.applica(v.giocatore, it)) } ?: v
            }

            for (lista in listOf(f.squadraA, f.squadraB)) {
                for (i in lista.indices) for (j in i + 1 until lista.size) {
                    val x = lista[i].giocatore.id
                    val y = lista[j].giocatore.id
                    storico.merge(if (x < y) x to y else y to x, 1, Int::plus)
                }
            }
            for (t in f.pianoPorta) turni.merge(t.giocatoreId, 1, Int::plus)

            if (n + 1 in tappe) {
                val con = rosa.filter { it.giocatore.propensione.confermataDaiFatti &&
                    it.giocatore.propensione.etichetta != null }
                val giuste = con.count { it.giocatore.propensione.etichetta == it.ruoloVero }
                println("  %7d   %5d/%-8d   %5d/%-9d   %s".format(
                    n + 1, con.size, rosa.size, giuste, con.size, f.livello))
            }
        }

        println("")
        println("  livelli dichiarati: " +
            livelli.entries.sortedBy { it.key.ordinal }.joinToString { "${it.key}=${it.value}" })
        println("")
        println("  etichette finali (i portieri erano dichiarati in partenza):")
        for (v in rosa.sortedBy { it.ruoloVero.ordinal }) {
            val segno = when {
                v.giocatore.propensione.etichetta == v.ruoloVero -> "ok"
                v.giocatore.propensione.etichetta == null -> "--"
                else -> "NO"
            }
            println("    %-11s vero %-3s  dedotto %-16s %s"
                .format(v.giocatore.nome, v.ruoloVero, v.giocatore.propensione.etichettaTesto, segno))
        }
        println("")
    }

    fun stagione(formato: Int, partite: Int = 40, seme: Long = 7) {
        val rng = Random(seme)
        val rosa = rosaVera(rng, n = 18).map { v ->
            // qui le etichette si danno per gia acquisite: interessa misurare
            // equilibrio e varieta, non la convergenza
            v.copy(giocatore = v.giocatore.copy(propensione = Propensione.dichiarata(v.ruoloVero, 10)))
        }
        val storico = HashMap<Pair<String, String>, Int>()
        val turni = HashMap<String, Int>()

        val scarti = ArrayList<Double>()
        val firme = HashSet<Set<Set<String>>>()
        val tempi = ArrayList<Long>()
        val modalita = HashMap<ModalitaPorta, Int>()
        var spostati = 0
        var slot = 0
        var violazioni = 0

        repeat(partite) {
            val convocati = rosa.shuffled(rng).take(formato * 2)
            lateinit var f: Formazione
            tempi += measureNanoTime {
                f = SorteggioSquadre.sorteggia(
                    convocati.map { it.giocatore }, formato, storico, turni, seed = rng.nextLong(),
                )
            }

            scarti += f.deltaOvr / ((f.forzaA + f.forzaB) / 2)
            modalita.merge(f.modalitaPorta, 1, Int::plus)
            firme += setOf(
                f.squadraA.map { it.giocatore.nome }.toSet(),
                f.squadraB.map { it.giocatore.nome }.toSet(),
            )

            val portieri = convocati.filter { it.giocatore.propensione.etichetta == Ruolo.POR }
                .map { it.giocatore.id }.toSet()
            val inPorta = setOf(f.squadraA[0].giocatore.id, f.squadraB[0].giocatore.id)
            if ((portieri intersect inPorta).size < minOf(2, portieri.size)) violazioni++

            for (lista in listOf(f.squadraA, f.squadraB)) {
                for (s in lista.drop(1)) { slot++; if (s.lontanoDalSuoRuolo) spostati++ }
                for (i in lista.indices) for (j in i + 1 until lista.size) {
                    val x = lista[i].giocatore.id
                    val y = lista[j].giocatore.id
                    storico.merge(if (x < y) x to y else y to x, 1, Int::plus)
                }
            }
            for (t in f.pianoPorta) turni.merge(t.giocatoreId, 1, Int::plus)
        }

        println("$formato contro $formato — $partite partite, rosa di ${rosa.size}\n")
        println("  scarto di forza      medio %.2f%%   massimo %.2f%%"
            .format(scarti.average() * 100, scarti.max() * 100))
        println("  formazioni distinte  ${firme.size}/$partite")
        println("  spostati di ruolo    $spostati/$slot (%.1f%%)".format(100.0 * spostati / slot))
        println("  vincolo portiere     $violazioni violazioni")
        println("  porta                " + modalita.entries.joinToString { "${it.key}=${it.value}" })
        println("  tempo per sorteggio  mediano %d ms, massimo %d ms\n"
            .format(tempi.sorted()[tempi.size / 2] / 1_000_000, tempi.max() / 1_000_000))
    }

    /** Il caso che nel calcetto capita davvero: nessuno vuole andare in porta. */
    fun senzaPortieri(partite: Int = 40) {
        val rng = Random(2024)
        val rosa = rosaVera(rng, n = 16, conPortieri = 0).map { v ->
            v.copy(giocatore = v.giocatore.copy(propensione = Propensione.dichiarata(v.ruoloVero, 10)))
        }
        val turni = HashMap<String, Int>()
        val minuti = HashMap<String, Int>()

        repeat(partite) {
            val f = SorteggioSquadre.sorteggia(
                rosa.shuffled(rng).take(14).map { it.giocatore }, 7,
                turniInPorta = turni, durataMin = 60, seed = rng.nextLong(),
            )
            for (t in f.pianoPorta) {
                turni.merge(t.giocatoreId, 1, Int::plus)
                minuti.merge(t.giocatoreId, t.minuti, Int::plus)
            }
        }

        val m = minuti.values
        println("nessun portiere — $partite partite, rosa di ${rosa.size}\n")
        println("  coinvolti in porta   ${minuti.size}/${rosa.size}")
        println("  minuti in porta      da ${m.min()} a ${m.max()}")
        println("  squilibrio           %.2f volte\n".format(m.max().toDouble() / m.min()))
    }
}

fun main() {
    Simulazione.convergenza()
    Simulazione.stagione(7)
    Simulazione.stagione(8, seme = 99)
    Simulazione.senzaPortieri()
}
