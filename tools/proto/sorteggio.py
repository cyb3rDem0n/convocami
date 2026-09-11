"""
Prototipo eseguibile del motore di sorteggio squadre.
Serve a validare le scelte di progetto PRIMA di scriverle in Kotlin:
- le squadre risultano davvero equilibrate?
- i ruoli vengono rispettati?
- le formazioni cambiano di partita in partita o si ripetono?

Eseguire:  python3 sorteggio.py
"""

from __future__ import annotations

import random
import statistics
from dataclasses import dataclass, field
from itertools import combinations

# ---------------------------------------------------------------------------
# Sistema skill
# ---------------------------------------------------------------------------

ATTRIBUTI = ["velocita", "tiro", "passaggio", "tecnica", "difesa", "fisico", "parate"]

# Quanto pesa ogni attributo nel rendere un giocatore forte IN QUEL RUOLO.
# Ogni riga somma 1.0: l'OVR resta cosi sulla stessa scala 1-99 degli attributi.
PESI_RUOLO = {
    "POR": {"velocita": .05, "tiro": .00, "passaggio": .05, "tecnica": .00, "difesa": .10, "fisico": .10, "parate": .70},
    "DIF": {"velocita": .15, "tiro": .05, "passaggio": .15, "tecnica": .05, "difesa": .40, "fisico": .20, "parate": .00},
    "CEN": {"velocita": .15, "tiro": .15, "passaggio": .30, "tecnica": .20, "difesa": .10, "fisico": .10, "parate": .00},
    "ATT": {"velocita": .25, "tiro": .35, "passaggio": .10, "tecnica": .20, "difesa": .05, "fisico": .05, "parate": .00},
}

# Quanti uomini per reparto, portiere escluso dal conteggio di movimento
FORMAZIONI = {
    7: {"POR": 1, "DIF": 2, "CEN": 3, "ATT": 1},
    8: {"POR": 1, "DIF": 3, "CEN": 3, "ATT": 1},
}

FUORI_RUOLO = 0.80  # moltiplicatore se schierato in un ruolo che non sa fare


@dataclass
class Giocatore:
    nome: str
    skill: dict[str, float]
    primario: str
    secondari: list[str] = field(default_factory=list)

    def proficiency(self, ruolo: str) -> float:
        if ruolo == self.primario:
            return 1.00
        if ruolo in self.secondari:
            return 0.92
        return FUORI_RUOLO

    def ovr(self, ruolo: str) -> float:
        base = sum(self.skill[a] * p for a, p in PESI_RUOLO[ruolo].items())
        return base * self.proficiency(ruolo)


# ---------------------------------------------------------------------------
# Funzione di costo: piu e bassa, piu le due squadre sono eque
# ---------------------------------------------------------------------------

W_TOTALE     = 1.00   # scarto di forza complessivo
W_REPARTO    = 0.45   # scarto reparto per reparto (evita "tutti i difensori di qua")
W_TOP        = 0.60   # scarto tra i due giocatori piu forti per squadra
W_RIPETUTI   = 0.35   # penalita per coppie che giocano sempre insieme
W_FUORIRUOLO = 6.00   # penalita per ogni giocatore schierato dove non sa giocare
W_SECONDARIO = 1.00   # penalita lieve per il ruolo secondario


def costo(schieramento, storico_coppie) -> tuple[float, float]:
    """schieramento: dict squadra -> list[(Giocatore, ruolo)]"""
    tot = {}
    per_reparto = {}
    top = {}
    disagio = 0.0

    for squadra, slots in schieramento.items():
        ovrs = [(g.ovr(r), r) for g, r in slots]
        # il portiere entra nel totale: una squadra con un portiere piu forte
        # viene compensata sui giocatori di movimento
        tot[squadra] = sum(o for o, _ in ovrs)
        top[squadra] = sum(sorted((o for o, r in ovrs if r != "POR"), reverse=True)[:2])
        for o, r in ovrs:
            if r == "POR":
                continue
            per_reparto.setdefault(r, {}).setdefault(squadra, 0.0)
            per_reparto[r][squadra] += o
        for g, r in slots:
            if r == "POR":
                continue  # la porta e assegnata a monte, non e negoziabile qui
            p = g.proficiency(r)
            if p == FUORI_RUOLO:
                disagio += W_FUORIRUOLO
            elif p < 1.0:
                disagio += W_SECONDARIO

    delta_tot = abs(tot["A"] - tot["B"])
    delta_top = abs(top["A"] - top["B"])
    delta_reparti = sum(abs(v.get("A", 0) - v.get("B", 0)) for v in per_reparto.values())

    ripetizioni = 0
    for slots in schieramento.values():
        for (g1, _), (g2, _) in combinations(slots, 2):
            ripetizioni += storico_coppie.get(frozenset((g1.nome, g2.nome)), 0)

    c = (W_TOTALE * delta_tot
         + W_REPARTO * delta_reparti
         + W_TOP * delta_top
         + W_RIPETUTI * ripetizioni
         + disagio)
    return c, delta_tot


# ---------------------------------------------------------------------------
# Generazione di una partizione valida rispetto ai ruoli
# ---------------------------------------------------------------------------

def scegli_portieri(convocati, rng):
    """Decide chi va in porta. E un VINCOLO, non un termine di costo: se ci sono
    portieri di ruolo fra i convocati, giocano in porta, punto.

    Ritorna (portiere_A, portiere_B, modalita) dove modalita vale:
      'regolare'   due portieri di ruolo
      'uno_solo'   un portiere di ruolo, l'altra squadra improvvisa
      'a_turno'    nessun portiere di ruolo: si suggerisce la rotazione in porta
    """
    di_ruolo = [g for g in convocati if g.primario == "POR"]
    rng.shuffle(di_ruolo)

    if len(di_ruolo) >= 2:
        # se ce ne sono piu di due, i due migliori, ma alternando fra pari livello
        di_ruolo.sort(key=lambda g: g.ovr("POR"), reverse=True)
        return di_ruolo[0], di_ruolo[1], "regolare"

    # nessuno o uno solo: si improvvisa con chi ha piu 'parate'
    ripieghi = sorted((g for g in convocati if g.primario != "POR"),
                      key=lambda g: g.ovr("POR"), reverse=True)[:4]
    rng.shuffle(ripieghi)

    if len(di_ruolo) == 1:
        return di_ruolo[0], ripieghi[0], "uno_solo"
    return ripieghi[0], ripieghi[1], "a_turno"


def schieramento_casuale(rosa, formato, rng, portieri):
    """Assegna ogni giocatore a una squadra e a un ruolo rispettando la formazione.

    I portieri arrivano gia decisi e occupano sempre la posizione 0 della lista:
    nessuna mossa successiva puo toglierli dalla porta.
    """
    form = FORMAZIONI[formato]
    slot_liberi = {"A": dict(form), "B": dict(form)}
    assegnati = {"A": [], "B": []}

    por_a, por_b = portieri
    assegnati["A"].append((por_a, "POR"))
    assegnati["B"].append((por_b, "POR"))
    slot_liberi["A"]["POR"] -= 1
    slot_liberi["B"]["POR"] -= 1

    rimasti = [g for g in rosa if g is not por_a and g is not por_b]
    rng.shuffle(rimasti)

    # giocatori di movimento: ordine casuale, ciascuno al posto libero che
    # sa fare meglio, nella squadra con meno forza accumulata
    for g in rimasti:
        opzioni = []
        for squadra in ("A", "B"):
            for ruolo, n in slot_liberi[squadra].items():
                if n > 0 and ruolo != "POR":
                    opzioni.append((squadra, ruolo))
        if not opzioni:
            break
        carico = {s: sum(x.ovr(r) for x, r in assegnati[s]) for s in ("A", "B")}
        # preferenza: ruolo che sa fare + squadra piu debole, con rumore casuale
        squadra, ruolo = max(
            opzioni,
            key=lambda o: g.proficiency(o[1]) * 100
                          - carico[o[0]] * 0.02
                          + rng.random() * 8,
        )
        assegnati[squadra].append((g, ruolo))
        slot_liberi[squadra][ruolo] -= 1

    return assegnati


def migliora(schieramento, storico, rng, passi=500):
    """Ricerca locale con due mosse:
      1. scambio incrociato: un giocatore dell'A e uno del B si scambiano di
         squadra prendendo ciascuno il ruolo dell'altro;
      2. scambio interno: due giocatori della stessa squadra si scambiano il
         ruolo (serve a rimettere ognuno dove sa giocare).
    Uno scambio si tiene solo se abbassa il costo."""
    c_att, _ = costo(schieramento, storico)

    # l'indice 0 e il portiere di ciascuna squadra e resta intoccabile
    for _ in range(passi):
        if rng.random() < 0.65:
            # mossa 1 — scambio tra le due squadre
            i = rng.randrange(1, len(schieramento["A"]))
            j = rng.randrange(1, len(schieramento["B"]))
            ga, ra = schieramento["A"][i]
            gb, rb = schieramento["B"][j]
            schieramento["A"][i] = (gb, ra)
            schieramento["B"][j] = (ga, rb)
            c_new, _ = costo(schieramento, storico)
            if c_new < c_att:
                c_att = c_new
            else:
                schieramento["A"][i] = (ga, ra)
                schieramento["B"][j] = (gb, rb)
        else:
            # mossa 2 — riassegnazione dei ruoli dentro la stessa squadra
            squadra = rng.choice(("A", "B"))
            slots = schieramento[squadra]
            i, j = rng.sample(range(1, len(slots)), 2)
            gi, ri = slots[i]
            gj, rj = slots[j]
            if ri == rj:
                continue
            slots[i] = (gi, rj)
            slots[j] = (gj, ri)
            c_new, _ = costo(schieramento, storico)
            if c_new < c_att:
                c_att = c_new
            else:
                slots[i] = (gi, ri)
                slots[j] = (gj, rj)

    return c_att


def sorteggia(rosa, formato, storico_coppie, seed=None, restart=60, tolleranza=0.08):
    """Genera molte partizioni, tiene solo quelle quasi ottime ed estrae fra loro.

    E qui che nasce la 'pseudo-casualita': non si sceglie il minimo assoluto
    (che darebbe sempre le stesse squadre), ma si pesca a caso nel gruppo delle
    soluzioni entro `tolleranza` dal migliore.
    """
    seed = seed if seed is not None else random.randrange(2**62)
    rng = random.Random(seed)

    candidati = []
    modalita = None
    for _ in range(restart):
        pa, pb, modalita = scegli_portieri(rosa, rng)
        s = schieramento_casuale(rosa, formato, rng, (pa, pb))
        c = migliora(s, storico_coppie, rng)
        candidati.append((c, s))

    migliore = min(c for c, _ in candidati)
    soglia = migliore + abs(migliore) * tolleranza + 1.0
    buoni = [(c, s) for c, s in candidati if c <= soglia]
    c, scelto = rng.choice(buoni)
    _, delta = costo(scelto, storico_coppie)
    return scelto, c, delta, seed, modalita


# ---------------------------------------------------------------------------
# Simulazione su una rosa realistica
# ---------------------------------------------------------------------------

def rosa_demo(rng, n=18):
    """Rosa verosimile: qualche fuoriclasse, molti nella media, due portieri."""
    nomi = ["Giuseppe", "Marco", "Luca", "Andrea", "Fabio", "Stefano", "Davide",
            "Matteo", "Simone", "Alessio", "Riccardo", "Paolo", "Antonio", "Gianni",
            "Emanuele", "Roberto", "Nicola", "Dario"]
    ruoli_pool = ["POR", "POR", "DIF", "DIF", "DIF", "DIF", "DIF",
                  "CEN", "CEN", "CEN", "CEN", "CEN", "CEN", "CEN",
                  "ATT", "ATT", "ATT", "ATT"]
    rosa = []
    for i in range(n):
        primario = ruoli_pool[i]
        # forza generale del giocatore: la maggior parte attorno a 50, code lunghe
        livello = min(88, max(32, rng.gauss(52, 11)))
        skill = {}
        for a in ATTRIBUTI:
            base = livello + rng.gauss(0, 5)
            if a == "parate":
                base = livello + 18 if primario == "POR" else 25 + rng.gauss(0, 6)
            skill[a] = round(min(95, max(15, base)), 1)
        secondari = [r for r in ("DIF", "CEN", "ATT")
                     if r != primario and rng.random() < 0.45]
        rosa.append(Giocatore(nomi[i], skill, primario, secondari))
    return rosa


def simula(partite=40, formato=7, seed=7):
    rng = random.Random(seed)
    rosa = rosa_demo(rng)
    storico: dict[frozenset, int] = {}

    deltas, ovr_medi, formazioni_viste, fuori_ruolo = [], [], set(), 0

    for _ in range(partite):
        convocati = rng.sample(rosa, formato * 2)
        schieramento, c, delta, s, quanti_buoni = sorteggia(
            convocati, formato, storico, seed=rng.randrange(2**40))

        deltas.append(delta)
        tot_a = sum(g.ovr(r) for g, r in schieramento["A"])
        tot_b = sum(g.ovr(r) for g, r in schieramento["B"])
        ovr_medi.append((tot_a + tot_b) / (formato * 2))

        firma = frozenset(frozenset(g.nome for g, _ in slots)
                          for slots in schieramento.values())
        formazioni_viste.add(firma)

        for slots in schieramento.values():
            for g, r in slots:
                if g.proficiency(r) == FUORI_RUOLO:
                    fuori_ruolo += 1
            for (g1, _), (g2, _) in combinations(slots, 2):
                k = frozenset((g1.nome, g2.nome))
                storico[k] = storico.get(k, 0) + 1

    tot_slot = partite * formato * 2
    print(f"Simulazione: {partite} partite {formato}v{formato}, rosa di {len(rosa)}\n")
    print(f"  Scarto di forza tra le squadre (somma OVR)")
    print(f"    medio    : {statistics.mean(deltas):5.2f} punti")
    print(f"    mediano  : {statistics.median(deltas):5.2f} punti")
    print(f"    peggiore : {max(deltas):5.2f} punti")
    print(f"    in % della forza di una squadra: "
          f"{100 * statistics.mean(deltas) / (statistics.mean(ovr_medi) * formato):4.2f}%\n")
    print(f"  Varieta")
    print(f"    formazioni distinte: {len(formazioni_viste)}/{partite}")
    print(f"    coppia piu ricorrente: {max(storico.values())} volte su {partite}")
    print(f"    media volte insieme per coppia: {statistics.mean(storico.values()):.2f}\n")
    print(f"  Rispetto dei ruoli")
    print(f"    schierati fuori ruolo: {fuori_ruolo}/{tot_slot} "
          f"({100*fuori_ruolo/tot_slot:.1f}%)\n")

    # esempio concreto dell'ultimo sorteggio
    print("  Ultimo sorteggio:")
    for squadra in ("A", "B"):
        slots = sorted(schieramento[squadra], key=lambda x: ["POR","DIF","CEN","ATT"].index(x[1]))
        tot = sum(g.ovr(r) for g, r in slots)
        print(f"    Squadra {squadra}  (forza totale {tot:.1f})")
        for g, r in slots:
            marchio = "" if g.proficiency(r) == 1.0 else ("*" if g.proficiency(r) == 0.92 else "!")
            print(f"       {r}  {g.nome:<11} {g.ovr(r):5.1f} {marchio}")
    print("\n    * ruolo secondario   ! fuori ruolo")


if __name__ == "__main__":
    simula(partite=40, formato=7)
    print("\n" + "=" * 60 + "\n")
    simula(partite=40, formato=8, seed=99)
