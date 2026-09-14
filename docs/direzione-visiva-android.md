# Direzione visiva Android — settembre 2026

Ricerca e decisioni per accesso, selezione gruppo e lista partite. La data di
consultazione è **14 settembre 2026**. In questo ambiente le pagine esterne
restituivano HTTP 403 dal proxy: le fonti sono quindi registrate per una
successiva verifica visuale, mentre le decisioni non dipendono da asset copiati.

## Riferimenti recenti

| Fonte | Data del riferimento | Cosa osservare, non cosa copiare |
|---|---:|---|
| [EA Sports FC 26 — sito ufficiale](https://www.ea.com/games/ea-sports-fc/fc-26) | 2025 | Tipografia display molto assertiva, grandi blocchi numerici, contrasto netto e accento ad alta energia. |
| [EA Sports FC 26 — pitch notes](https://www.ea.com/games/ea-sports-fc/fc-26/news) | 2025–2026 | Informazioni dense organizzate per moduli e stati; il contenuto operativo resta più piano dei momenti promozionali. |
| [eFootball — informazioni versione](https://www.konami.com/efootball/en/page/v5/versioninfo_v5-0) | 14 agosto 2025 | Fondi blu molto scuri, giallo ad alta visibilità, schede squadra e indicatori compatti. |
| [Material Design 3 — accessibility](https://m3.material.io/foundations/accessible-design/overview) | pagina viva, consultazione 2026 | Gerarchia non affidata al solo colore, target comodi e contrasto come requisito. |
| [Android — animation in Compose](https://developer.android.com/develop/ui/compose/animation/quick-guide) | aggiornamenti continui, consultazione 2026 | Preferire trasformazioni economiche e animazioni brevi, preservando semantica e layout. |

### Tendenze osservate

- sfondi profondi, con un alone o una luce ambientale che separa il primo piano;
- display face condensate o molto pesanti per punteggi, titoli e CTA;
- colore acceso concentrato su selezione, progresso e azione, non distribuito
  uniformemente;
- card modulari con badge testuale, numeri tabulari e metadati molto compatti;
- spettacolarità riservata a reveal e ricompense, non alle liste consultate spesso.

### Proposte originali Convocami

- l'alone verde in alto richiama i fari su un campo senza usare fotografie;
- il piccolo quadrato verde accanto al wordmark e il linguaggio «spogliatoio»
  creano riconoscibilità senza stemmi, loghi o geometrie proprietarie;
- le card partita hanno una barra convocati, un numero dominante e un badge
  testuale: disponibilità e stato non sono mai comunicati soltanto dal colore;
- gli stati vuoto ed errore usano pittogrammi geometrici disegnati in Compose,
  titolo, spiegazione e azione. Non derivano da iconografie dei videogiochi;
- squadra bianca `#F1F4F0` e squadra nera `#10150F` restano token di dominio;
  quando entreranno nelle formazioni avranno sempre anche il nome scritto.

## Sistema coerente

La palette esistente è confermata. Il verde `#2BE07A` ha più personalità del
ciano comune nelle dashboard e conserva un contrasto forte sul fondo
`#070B08`. Il nero con deriva verde lega accento e superfici senza tingere il
testo. L'oro resta escluso da navigazione, CTA e stato: compare solo per premi,
figurine ed esiti guadagnati. Rosso e ambra restano segnali semantici separati.
Non servono nuovi token in questa fase, quindi web e Android rimangono allineati
senza interventi cosmetici gratuiti.

- **Tipografia:** Archivo Black per headline e numeri; Barlow Condensed per
  occhielli, badge e controlli; Barlow per testi. I file OFL sono nell'APK.
- **Superfici:** fondo radiale, card opache `erba`/`erba2`, bordo sempre
  visibile. Niente vetro o blur sulle liste da usare all'aperto.
- **Componenti:** raggio 12 dp, CTA alte 52 dp, campi ad alto contrasto, avatar
  iniziali da 48 dp, badge con testo e bordo.
- **Icone:** primitive Canvas per i pochi simboli identitari; nessuna nuova
  libreria e nessun pacchetto generico necessario.
- **Movimento:** nessuna animazione decorativa nelle tre schermate operative.
  In seguito, reveal e premi useranno transizioni Compose brevi e saltabili; lo
  stato deve essere comprensibile anche a movimento disattivato.

## Dipendenze

Non è stata aggiunta alcuna dipendenza. Compose UI e Material 3 erano già nel
progetto (licenza Apache 2.0) e bastano per layout, semantica, Canvas e
indicatori. Questo evita peso APK, inizializzazioni e rischio di incompatibilità.
I controlli Material sono usati come base accessibile ma colori, forme,
tipografia e gerarchia sono personalizzati.

## Stati e verifica

`StatoContenuto` descrive contenuto, caricamento, errore e vuoto per selezione
gruppo e lista partite. I dati mostrati sono dimostrativi: questo intervento
definisce il livello visuale e la navigazione locale, non dichiara una nuova
integrazione Supabase.

La compilazione da sola non valida l'interfaccia. Va eseguita una verifica su
telefono o emulatore almeno a 360×800 dp, con font scale 1,0 e 1,3, TalkBack e
animazioni di sistema disattivate. In questo ambiente non era disponibile un
emulatore Android: non sono quindi inclusi screenshot e la resa non è dichiarata
visivamente verificata.
