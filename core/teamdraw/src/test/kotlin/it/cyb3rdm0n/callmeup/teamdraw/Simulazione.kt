package it.cyb3rdm0n.callmeup.teamdraw

import kotlin.random.Random
import kotlin.system.measureNanoTime

/**
 * Banco di prova del motore di sorteggio. Non e un test: e lo strumento con cui
 * si misurano equilibrio, varieta e tempi su stagioni intere simulate, e con cui
 * sono stati trovati i difetti che i test in [TeamDrawTest] ora presidiano.
 *
 * Si esegue a mano quando si toccano i pesi della funzione di costo:
 *
 *   ./gradlew :core:teamdraw:test --tests '*' -i     # i test
 *   kotlinc ... && java -cp ... SimulazioneKt        # questa simulazione
 */
object Simulazione {

    private fun gauss(rng: Random, mu: Double, sigma: Double): Double {
        val u1 = rng.nextDouble().coerceAtLeast(1e-9)
        val u2 = rng.nextDouble()
        return mu + sigma * Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2 * Math.PI * u2)
    }

    /** Rosa verosimile: due portieri, qualche forte, molti nella media. */
    fun rosa(rng: Random, n: Int = 18, conPortieri: Boolean = true): List<Giocatore> {
        val nomi = listOf(
            "Giuseppe", "Marco", "Luca", "Andrea", "Fabio", "Stefano", "Davide",
            "Matteo", "Simone", "Alessio", "Riccardo", "Paolo", "Antonio", "Gianni",
            "Emanuele", "Roberto", "Nicola", "Dario",
        )
        val ruoli = buildList {
            if (conPortieri) repeat(2) { add(Ruolo.POR) }
            repeat(5) { add(Ruolo.DIF) }
            repeat(7) { add(Ruolo.CEN) }
            repeat(4) { add(Ruolo.ATT) }
        }
        return (0 until n).map { i ->
            val primario = ruoli[i % ruoli.size]
            val livello = gauss(rng, 52.0, 11.0).coerceIn(32.0, 88.0)
            fun a() = (livello + gauss(rng, 0.0, 5.0)).coerceIn(15.0, 95.0)
            val parate = if (primario == Ruolo.POR) (livello + 18).coerceAtMost(95.0)
            else gauss(rng, 25.0, 6.0).coerceIn(15.0, 95.0)
            Giocatore(
                id = "p$i",
                nome = nomi[i % nomi.size],
                skill = Skill(a(), a(), a(), a(), a(), a(), parate),
                ruoloPrimario = primario,
                ruoliSecondari = listOf(Ruolo.DIF, Ruolo.CEN, Ruolo.ATT)
                    .filter { it != primario && rng.nextDouble() < 0.45 }.toSet(),
            )
        }
    }

    fun stagione(formato: Int, partite: Int = 40, seme: Long = 7) {
        val rng = Random(seme)
        val rosa = rosa(rng)
        val storico = HashMap<Pair<String, String>, Int>()
        val turni = HashMap<String, Int>()

        val scarti = ArrayList<Double>()
        val firme = HashSet<Set<Set<String>>>()
        val tempi = ArrayList<Long>()
        val modalita = HashMap<ModalitaPorta, Int>()
        var fuori = 0
        var slot = 0
        var violazioni = 0

        repeat(partite) {
            val convocati = rosa.shuffled(rng).take(formato * 2)
            lateinit var f: Formazione
            tempi += measureNanoTime {
                f = SorteggioSquadre.sorteggia(
                    convocati, formato, storico, turni, seed = rng.nextLong(),
                )
            }

            scarti += f.deltaOvr / ((f.forzaA + f.forzaB) / 2)
            modalita.merge(f.modalitaPorta, 1, Int::plus)
            firme += setOf(
                f.squadraA.map { it.giocatore.nome }.toSet(),
                f.squadraB.map { it.giocatore.nome }.toSet(),
            )

            // il vincolo: un portiere di ruolo convocato deve stare fra i pali
            val diRuolo = convocati.filter { it.ruoloPrimario == Ruolo.POR }.map { it.id }.toSet()
            val inPorta = setOf(f.squadraA[0].giocatore.id, f.squadraB[0].giocatore.id)
            if ((diRuolo intersect inPorta).size < minOf(2, diRuolo.size)) violazioni++

            for (lista in listOf(f.squadraA, f.squadraB)) {
                for (s in lista.drop(1)) { slot++; if (s.fuoriRuolo) fuori++ }
                for (i in lista.indices) for (j in i + 1 until lista.size) {
                    val x = lista[i].giocatore.id
                    val y = lista[j].giocatore.id
                    storico.merge(if (x < y) x to y else y to x, 1, Int::plus)
                }
            }
            for (t in f.pianoPorta) turni.merge(t.giocatoreId, 1, Int::plus)
        }

        println("$formato contro $formato — $partite partite, rosa di ${rosa.size}\n")
        println("  scarto di forza    medio %.2f%%   massimo %.2f%%"
            .format(scarti.average() * 100, scarti.max() * 100))
        println("  formazioni distinte  ${firme.size}/$partite")
        println("  fuori ruolo          $fuori/$slot (%.1f%%)".format(100.0 * fuori / slot))
        println("  vincolo portiere     $violazioni violazioni")
        println("  porta                " + modalita.entries.joinToString { "${it.key}=${it.value}" })
        println("  tempo per sorteggio  mediano %d ms, massimo %d ms\n"
            .format(tempi.sorted()[tempi.size / 2] / 1_000_000, tempi.max() / 1_000_000))
    }

    /** Il caso che nel calcetto capita davvero: nessuno vuole andare in porta. */
    fun senzaPortieri(partite: Int = 40) {
        val rng = Random(2024)
        val rosa = rosa(rng, n = 16, conPortieri = false)
        val turni = HashMap<String, Int>()
        val minuti = HashMap<String, Int>()

        repeat(partite) {
            val f = SorteggioSquadre.sorteggia(
                rosa.shuffled(rng).take(14), 7, turniInPorta = turni,
                durataMin = 60, seed = rng.nextLong(),
            )
            for (t in f.pianoPorta) {
                turni.merge(t.giocatoreId, 1, Int::plus)
                minuti.merge(t.giocatoreId, t.minuti, Int::plus)
            }
        }

        val m = minuti.values
        println("nessun portiere di ruolo — $partite partite, rosa di ${rosa.size}\n")
        println("  coinvolti in porta   ${minuti.size}/${rosa.size}")
        println("  minuti in porta      da ${m.min()} a ${m.max()}")
        println("  squilibrio           %.2f volte\n".format(m.max().toDouble() / m.min()))
    }
}

fun main() {
    Simulazione.stagione(7)
    Simulazione.stagione(8, seme = 99)
    Simulazione.senzaPortieri()
}
