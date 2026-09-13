# Convocami — passaggio di consegne

Documento di riferimento per chi prende in mano l'implementazione. Contiene lo
stato reale del progetto, cosa è verificato e cosa no, le convenzioni da
rispettare e il lavoro da fare con i criteri per dirlo finito.

Ultimo aggiornamento: 13 settembre 2026.

---

## 1. Cos'è

App per organizzare il calcetto a 7 e a 8 dentro una comitiva di zona. Si apre
la partita, ci si iscrive, a quota raggiunta le convocazioni si chiudono da
sole, l'app propone le squadre, si gioca, e a fine partita i giocatori si votano
a vicenda. Da quei voti l'app impara chi gioca dove e quanto vale, senza che
nessuno debba dichiararlo.

Due client sullo stesso database: web app (già viva) e app Android nativa
(ancora un guscio). Backend Supabase, piano gratuito, costo di esercizio zero.

---

## 2. Stato reale

### Fatto e verificato

| Cosa | Dove | Come è stato verificato |
|---|---|---|
| Motore di sorteggio squadre | `core/teamdraw/` | 72 test, compilati ed eseguiti |
| Propensione ai ruoli, voto per fasi | `core/teamdraw/` | Convergenza simulata su 40 partite |
| Schema database | `db/migrations/001` | Eseguito su Postgres 16: 15 tabelle, 4 viste, 33 policy, zero errori |
| Trigger iscrizioni | `db/migrations/001` | Provati: 16 iscritti su 14 posti → 14 convocati, 2 riserve, 56 notifiche in coda, promozione riserve al ritiro |
| Ingresso gruppo, realtime | `db/migrations/002` | Eseguito; funzioni provate una per una |
| Email privata | `db/migrations/003` | Eseguito; il client legge `profili_pubblici` |
| Regole della partita: tetto riserve, contatore ritiri, referto | `db/migrations/004` | 33 prove eseguite su Postgres 16 (`db/locale/01_prova_regole.sql`) |
| Secondo ruolo dichiarabile | `db/migrations/005` + `core/teamdraw/` | Vincoli provati sul database e nel motore |
| Web app: accesso, gruppo, partite, iscrizioni | `web/` | Sintassi e resa grafica verificate |
| Identità visiva della web app | `web/style.css` | Schermate renderizzate e guardate (senza i font veri: vedi sotto) |

### Fatto ma NON verificato

La web app **non è mai stata eseguita contro un database vero**: l'ambiente in
cui è stata scritta non raggiunge npm, i CDN né Supabase. Sintassi, struttura e
impaginato sono controllati; il comportamento runtime no. Chi prende in mano il
progetto **può compilare ed eseguire davvero**, e la prima cosa da fare è
proprio quello.

Nemmeno **i caratteri** sono stati visti: `fonts.googleapis.com` era chiuso, e
le schermate renderizzate mostrano i ripieghi (Arial Black, Arial Narrow).
Colori, spaziature e impaginato sono quelli buoni; Archivo Black e Barlow
vanno guardati al primo avvio con la rete aperta.

### Non fatto

App Android oltre il guscio; il client che *mostra* quota, presenze e ritiri —
il database ormai li tiene, ma nessuna schermata li legge; voto e statistiche
lato client; riepilogo partita; classifiche; zone; notifiche push ed email.

---

## 3. Architettura e convenzioni — da rispettare

**Il codice è in italiano.** Nomi di funzioni, variabili, tabelle, commenti.
`iscriviti`, `convocati`, `propensione`, `turniInPorta`. Non è vezzo: il dominio
è italiano e tradurlo introduce ambiguità (*pitch* è il campo o il tiro?). I
termini tecnici restano in inglese dove non hanno un equivalente sensato.

**Il motore di sorteggio è Kotlin puro, senza dipendenze.** Vive in
`core/teamdraw/`, non importa nulla di Android né di Supabase, e si testa con una
JVM in un secondo. Non introdurre dipendenze lì dentro. Se serve al web, va
esposto come Edge Function, **non riscritto in JavaScript**: due implementazioni
dello stesso algoritmo divergono sempre.

**La web app non ha passaggio di build.** HTML, CSS e un modulo ES, con Supabase
dalla CDN. Niente npm, niente bundler, niente framework. È una scelta: si
pubblica copiando file e si corregge ricaricando la pagina. Mantenerla, finché
regge.

**La logica delicata sta nel database.** Chiusura iscrizioni, conferme,
promozione riserve sono trigger Postgres, non codice applicativo: se due persone
si iscrivono nello stesso istante al quattordicesimo posto, solo il database può
arbitrare. Le operazioni che devono essere atomiche o che scavalcano le policy
sono funzioni `security definer`. Continuare così.

**Row Level Security su tutto.** Ogni nuova tabella nasce con le sue policy.

**Ogni tabella porta `group_id`.** L'app è multi-gruppo dal primo giorno.

---

## 4. Il caso d'uso completo

Questo è il flusso che il prodotto deve coprire. Le parti già fatte sono
marcate.

### 4.1 Zone e gruppi

Un gruppo è una comitiva di zona: *Bicocca Region*. Chi vive dall'altra parte
della città non vi gioca, e la separazione è quella. Il gruppo ha quindi una
**zona** (testo libero: quartiere, paese, nome che si danno loro).

Una persona può stare in più gruppi. Il suo profilo mostra la zona o le zone di
appartenenza.

*Stato: gruppi fatti. Zona da aggiungere.*

### 4.2 Il profilo — figurina

Foto o avatar, nomignolo, zona, ruoli possibili, skill, punti affiatamento,
etichette guadagnate. **L'email non si mostra mai**: serve solo alla
registrazione.

Ogni giocatore può indicare **due ruoli** che si sente di ricoprire. Per chi è
nuovo sono l'unica informazione disponibile e servono al primo sorteggio; poi i
voti li scavalcano. I ruoli dichiarati restano visibili nel profilo, ma
l'etichetta mostrata è quella dedotta.

*Stato: due ruoli dichiarabili fatti — `dichiara_ruoli()` in `005` e
`Propensione.diPartenza(principale, secondo)` nel motore. Restano le due
tendine nel profilo e la foto.*

### 4.3 Organizzare

L'organizzatore apre la partita: data, ora, campo, formato e **quota a testa**.
Tutti i membri ricevono la notifica.

Sulla quota l'app **mostra un importo e basta**: niente incassi, niente IBAN,
niente saldi da spuntare. È una decisione di prodotto, non una mancanza —
toccare denaro vero significa entrare nella normativa sui pagamenti, e per una
comitiva è sproporzionato. I soldi viaggiano come sono sempre viaggiati.

*Stato: il campo `quota_eur` esiste su `matches` e non può essere negativo.
Manca solo mostrarlo.*

### 4.4 Iscriversi

Iscrizione in ordine di arrivo. A quota raggiunta le convocazioni **si chiudono
da sole** e compare la quota a testa.

Oltre la capienza si va in lista d'attesa, **massimo due persone**. Il
tredicesimo che ci prova viene informato che la lista è piena.

C'è il tasto **mi ritiro**. Chi si ritira libera il posto e la prima riserva
entra, avvisata.

*Stato: fatto, tetto di due riserve compreso (`004`, provato anche sul
rientro di chi si era ritirato). Manca la quota da mostrare, e usare
`posti_liberi()` per dirlo prima che uno prema il pulsante.*

### 4.5 Ritiri

Ritirarsi **non comporta alcuna penalità**. Nessun punteggio, nessuna classifica
di affidabilità, nessun meccanismo da tarare.

Resta solo un dato nel profilo: **quante volte quella persona si è ritirata**.
Va però mostrato sempre **in coppia con le presenze** — *«38 presenze, 4
ritiri»* — e mai come numero solo. Un conteggio nudo penalizza chi gioca da
anni rispetto a chi è arrivato il mese scorso, e finirebbe per dire il
contrario di quello che vuole dire.

È il dato a fare il lavoro, non una regola: in una comitiva che si conosce,
vedere i numeri di tutti basta e avanza.

*Stato: i dati ci sono — vista `contatori_giocatore` in `004`. Manca la riga
nel profilo che li mostra in coppia.*

### 4.6 Le squadre

Sorteggio pseudo-casuale che tiene conto della propensione ai ruoli e della
forza; per chi è nuovo valgono i due ruoli dichiarati; se non c'è abbastanza
informazione, sorteggio puro dichiarato come tale. Il motore c'è ed è testato.

L'organizzatore può **sistemare la formazione** e indicare il campo. Le squadre
si chiamano **bianca** e **nera** — per le maglie — e la formazione finale viene
mostrata a tutti.

*Stato: motore fatto. Schermata, ritocchi e assegnazione colori da fare.*

### 4.7 Dopo la partita

**90 minuti dopo il fischio finale** arriva a tutti la notifica per votare:

- valutare le prestazioni degli altri, per fase di gioco (difesa, attacco,
  regia, porta) — obbligatorio per chiudere;
- **reclamare i propri gol e assist**;
- gol più bello e migliore in campo — facoltativi.

L'organizzatore inserisce il risultato finale e segna eventuali assenti.

**I gol reclamati devono combaciare con il risultato.** Se i claim di una
squadra non tornano con i gol che ha segnato, l'evento **non si chiude**: l'app
mostra lo scarto e l'organizzatore arbitra. Il vincolo va imposto dal database,
non dal client, altrimenti basta una chiamata diretta per aggirarlo.

Gli autogol vanno contati nel totale della squadra avversaria, o i conti non
torneranno mai e nessuno capirà perché.

*Stato: modello dati del voto fatto. Tutto il resto da fare.*

### 4.8 Etichette della settimana

Chi fa qualcosa di notevole si prende un'etichetta:

| Etichetta | Quando |
|---|---|
| Bomber | 2 o più gol |
| Saracinesca | rigore parato, o porta inviolata da portiere |
| Muro | difensore in una squadra che subisce 1 gol o meno |
| Regista | 3 o più assist |
| Uomo ovunque | nominato in 3 fasi diverse su 4 |

Le soglie sono una proposta e vanno tarate sulle prime partite vere.

*Stato: da fare.*

### 4.9 Chiusura e storico

A votazioni concluse l'evento si chiude e viene generato il **riepilogo**:
risultato, marcatori, assist, formazioni, migliore in campo, gol più bello,
etichette assegnate, variazioni di skill.

Nel tempo: classifica cannonieri, miglior giocatore per ruolo, presenze,
affidabilità.

*Stato: da fare.*

---

## 5. Aspetto

Riferimento dichiarato: **FIFA 26 / PES 26**. Interfaccia scura, schede
giocatore in stile figurina, numeri grandi, transizioni.

**Vale per entrambi i client.** Sono lo stesso prodotto e le stesse persone
passano dall'uno all'altro: se non si somigliano, sembrano due app diverse dello
stesso gruppo. Ma l'identità è condivisa, il codice no — CSS sul web, Compose su
Android. Quello che si condivide è `design/tokens.json`, che è la fonte unica
dei valori. Non c'è generazione automatica di proposito: introdurla vorrebbe
dire aggiungere un passaggio di build alla web app, che oggi non ne ha.

Due trappole di Android, entrambe già chiuse nel repo ma facili da riaprire:

- **I colori dinamici di Material 3 sono spenti.** Erano accesi: su Android 12+
  avrebbero preso le tinte dallo sfondo del telefono, e con uno sfondo viola
  Convocami sarebbe diventata viola. Per un'app di sistema è una funzione, per
  un'identità dichiarata è la sua fine.
- **I caratteri vanno imbarcati nell'APK** (`app/src/main/res/font/`). Su web
  arrivano da Google Fonts, su Android non esiste una CDN di font: senza i file,
  il sistema ripiega su Roboto e le due app si somigliano soltanto nei colori.
  È il modo più comune in cui un'identità condivisa si perde per strada.

Il tema chiaro non esiste in nessuno dei due, ed è una scelta: un'app che vive
di schede lucide e numeri grandi su fondo scuro non ha una versione chiara che
regga, e averne una a metà sarebbe peggio che non averla.

Va detto che è un cambio di direzione rispetto a quello che c'è ora, che è
sobrio e documentale. La direzione è legittima e per un'app di calcio funziona,
ma tre avvertenze da chi la implementerà:

1. **La schermata che si guarda di più è la lista iscritti.** Deve restare
   leggibile con il telefono in mano fuori dal campo, di sera. Il vetro scuro e
   i riflessi lì fanno danni.
2. **Le animazioni costano batteria e frame.** Vanno dove c'è un momento — la
   rivelazione delle squadre, l'etichetta guadagnata — non su ogni lista.
3. **La figurina è l'idea buona.** Schede giocatore con foto, valori per ruolo,
   etichette: è esattamente il linguaggio giusto, e si può fare bene con CSS
   puro senza appesantire.

### Le quattro schermate di riferimento

Mockup costruiti con i dati veri del progetto:
https://claude.ai/code/artifact/a84f03cc-c62c-4084-9cc4-8a01424474af

Figurina, rivelazione delle squadre, schermata partita e schermata voto. Sono
CSS puro, quindi il codice si può leggere e riusare direttamente.

### Palette e caratteri

| Token | Valore | Dove |
|---|---|---|
| `--notte` | `#070B08` | sfondo, nero con deriva verde |
| `--erba` | `#0E1712` | superfici |
| `--linea` | `#1E2C24` | bordi |
| `--verde` | `#2BE07A` | accento — contatori, stati, azione primaria |
| `--oro` | `#E9C46A` | solo figurina ed etichette guadagnate |
| `--gesso` | `#EDF2EC` | testo |

Squadre: bianca `#F1F4F0`, nera `#10150F`. Il bianco e nero non è decorazione,
è il sistema di colore che distingue le due squadre ovunque compaiano.

Caratteri: **Archivo Black** per numeri e titoli, **Barlow Condensed** per le
etichette da tabellone, **Barlow** per il testo.

### Librerie

| Libreria | Peso | Verdetto |
|---|---|---|
| CSS + Web Animations API | 0 kB | Il 90% di tutto. Figurine, barre, gradienti, stati. |
| Motion (`motion.dev`) | ~18 kB | Solo per la rivelazione delle squadre. Si importa come modulo, niente build. |
| `canvas-confetti` | ~6 kB | Facoltativa, per l'etichetta guadagnata. |
| GSAP | ~23 kB | Gratuito da fine 2024, ma qui è più potente del necessario. |
| React + libreria UI | ~140 kB | No: rompe la scelta di non avere un passaggio di build, per una trentina di componenti in tutto. |

L'aspetto da videogioco non viene dai pacchetti: viene da gradienti stratificati,
caratteri giusti e numeri grandi.

---

## 6. Lavoro da fare

Ordinato per dipendenze. Ogni voce ha il criterio per dirla finita.

### Blocco A — far funzionare quello che c'è *(prima di tutto)*

1. **Eseguire la web app contro Supabase vero.**
   *Finito quando:* registrazione, creazione gruppo, apertura partita,
   iscrizione e ritiro funzionano da due browser diversi, e il contatore si
   muove da solo.

2. ~~Chiudere la falla sull'email.~~ **Fatto** in `003_email_privata.sql`:
   i compagni leggono la vista `profili_pubblici` (id, nome, avatar) e da
   `profiles` ciascuno vede solo la propria riga. Verificato su Postgres.
   Il client è già allineato: legge da lì, non più con una join su `profiles`.

### Blocco B — completare il giro della partita

**Il lato database di questo blocco è fatto e provato** (004 e 005). Quel che
resta è farlo vedere: le regole esistono, ma nessuna schermata le legge.

3. ~~Tetto di due riserve.~~ **Fatto** in `004`: vale sia per chi si iscrive
   sia per chi rientra dopo essersi ritirato, e regge anche a una insert
   diretta su PostgREST. Il numero è `matches.max_riserve`, non una costante.
   *Da fare nel client:* usare `posti_liberi(partita)` per scrivere «2 posti,
   poi lista d'attesa» **prima** che qualcuno prema il pulsante, e mostrare il
   messaggio del database quando la lista è piena.
4. ~~Quota, lato dati.~~ Il vincolo c'è (`quota_eur >= 0`), il numero pure.
   *Da fare:* mostrarlo nella scheda partita e nella conferma di iscrizione.
   Nient'altro — nessuna tabella di saldi, nessun riferimento per pagare.
   *Finito quando:* a iscrizioni chiuse ogni convocato vede quanto costa.
5. ~~Contatori presenze e ritiri.~~ **Fatto** in `004`: vista
   `contatori_giocatore`. Le presenze si contano dalle iscrizioni, i ritiri
   stanno in `group_members.ritiri`. Si conta solo chi molla un posto da
   convocato, e una volta sola per partita.
   *Finito quando:* il profilo mostra «N presenze, M ritiri», sempre in coppia.
6. ~~Due ruoli dichiarabili.~~ **Fatto** in `005` e nel motore:
   `dichiara_ruoli(gruppo, principale, secondario)`, con POR escluso da
   entrambe le caselle.
   *Da fare nel client:* le due tendine nel profilo, e l'etichetta doppia
   «Attaccante o centrocampista · di partenza» nell'elenco dei convocati.

### Blocco C — squadre

7. **Sorteggio dal client.** Il motore è Kotlin: esporlo come Edge Function
   Supabase (Deno) che lo richiama, oppure — se troppo scomodo — portarlo in
   TypeScript **una volta sola**, lato server, mai nei client.
8. **Schermata formazione**: campo disegnato, squadra bianca e nera, ritocchi
   dell'organizzatore con scarto di forza aggiornato mentre sposta.
   *Finito quando:* l'organizzatore sposta un giocatore e vede il numero
   cambiare, e i convocati vedono la formazione finale.

### Blocco D — dopo la partita

9. **Notifica a 90 minuti**: riga in `notifications_outbox` con `invia_dopo`, e
   una funzione schedulata che svuota la coda.
10. **Schermata voto**: quattro nomine, claim gol e assist, extra facoltativi.
11. ~~Arbitraggio dei claim.~~ **Fatto** in `004`: `chiudi_referto(partita,
    gol_bianca, gol_nera)` rifiuta il referto se i gol dichiarati non fanno il
    risultato, autogol compresi, e a referto chiuso i gol si congelano;
    `riapri_referto` serve a correggere. Squadra A = bianca, B = nera.
    *Da fare nel client:* la schermata dove l'organizzatore scrive il
    risultato, e il messaggio del database mostrato com'è quando i conti non
    tornano — dice già chi dichiara quanto.
12. **Etichette**, **riepilogo**, **classifiche**.

### Blocco E — Android e pubblicazione

13. App Android che rifà il giro completo, riusando il motore direttamente.
14. Push FCM, SMTP vero, 12 tester per 14 giorni, Play Store.

---

## 7. Trappole note

- **`Confirm email` deve restare spento** finché non c'è un SMTP proprio:
  l'email integrata di Supabase manda 2 messaggi all'ora e solo a indirizzi del
  team del progetto. Con la conferma accesa, nessun tester riesce a registrarsi.
- **Le tabelle nuove vanno aggiunte alla publication realtime** se il client
  deve vederne i cambiamenti da solo.
- **Il progetto Supabase gratuito va in pausa dopo 7 giorni senza traffico.**
- **`web/config.js` è versionato di proposito.** La chiave anon è pubblica per
  definizione; ignorarla faceva solo restare bianca la pagina su Vercel.
- **Il flag `portiere` è dell'organizzatore**, protetto da trigger. Chi para,
  para: quel ruolo non si deduce dai voti, e l'esclusione vale in entrambe le
  direzioni.
- **Non toccare i pesi della funzione di costo** senza rieseguire
  `Simulazione`: due difetti seri sono già stati trovati lì, e non facevano
  fallire nulla — producevano squadre plausibili e sbagliate.

---

## 8. Comandi

```bash
# motore di sorteggio
./gradlew :core:teamdraw:test        # 72 test
./gradlew assembleDebug              # APK di debug

# web app
cd web && python3 -m http.server 8000

# database: su Supabase, SQL Editor, in ordine
# db/migrations/001_schema.sql
# db/migrations/002_ingresso_e_realtime.sql
# db/migrations/003_email_privata.sql
# db/migrations/004_regole_di_partita.sql
# db/migrations/005_secondo_ruolo.sql

# ... oppure in locale, su un Postgres qualunque, per provare davvero:
createdb callmeup
psql -d callmeup -f db/locale/00_finta_supabase.sql   # SOLO in locale
for f in db/migrations/0*.sql; do psql -d callmeup -f "$f"; done
psql -d callmeup -f db/locale/01_prova_regole.sql     # 33 prove
```

`db/locale/00_finta_supabase.sql` crea le tre cose che su Supabase ci sono già
— lo schema `auth`, `auth.uid()`, i ruoli `anon` e `authenticated` — e senza le
quali `001` fallisce alla prima foreign key. Su Supabase non va eseguito mai.

Documenti: [`impianto-tecnico.md`](impianto-tecnico.md) per il perché delle
scelte, [`primo-avvio.md`](primo-avvio.md) per la configurazione,
[`build-e-deploy.md`](build-e-deploy.md) per firma e pubblicazione.
