# Impianto tecnico

Documento di riferimento del progetto. Versione 2.0 — aggiornata al modello dei
ruoli dedotti e del voto per fasi di gioco.

## Decisioni

| Ambito | Scelta | Perché |
|---|---|---|
| Nome | **Convocami**, tranne il package `it.cyb3rdm0n.callmeup` | La parola *è* la funzione, e gli utenti sono italiani: un gioco di parole che va spiegato non sta funzionando. Il package resta perché dopo la pubblicazione non si cambia più — è l'unico pezzo davvero irreversibile — e perché un identificatore neutro viaggia meglio se l'app cambia mani. Scartato *CallMeApp*: «call me up» in inglese vuol dire *telefonami*, e attaccato sembrava un'app per chiamate |
| Client | Android nativo, Kotlin + Jetpack Compose | L'APK firmato è l'oggetto che si può cedere o licenziare |
| Backend | Supabase, piano gratuito | Postgres, auth, storage e realtime pronti, a costo zero |
| Sorteggio | Modulo Kotlin puro, sul dispositivo | Testabile con una JVM, portabile, nessun costo di server |
| Notifiche | Firebase Cloud Messaging + Resend/Brevo | Gratuiti entro margini larghissimi |
| Multi-gruppo | Fin dal primo giorno | Aggiungerlo dopo vorrebbe dire migrare tutto |
| Budget | €0 di esercizio | Unica spesa futura e facoltativa: $25 per il Play Console |

Il vincolo con il fornitore si neutralizza in due modi: sotto c'è Postgres
standard, quindi il database si porta via con un dump; e nell'app tutto
l'accesso ai dati passa da interfacce `MatchRepository`, `PlayerRepository`,
`AuthRepository`.

---

## I ruoli

Questa è la parte che è cambiata di più, e sempre nella stessa direzione:
avvicinarsi a come si gioca davvero invece che a come sarebbe comodo modellarlo.

### I ruoli di movimento non sono fissi

Nel calcetto un difensore si ritrova in area avversaria e un attaccante rientra
a coprire, dentro la stessa partita. E domenica prossima uno che ha fatto
l'attaccante può giocare dietro. Quindi il ruolo non è un dato anagrafico.

Non si dichiara nemmeno all'iscrizione, per una ragione pratica: **si
dichiarerebbero tutti attaccanti**. Si deduce da come è andata in campo.

Ogni giocatore ha una **propensione** — quattro valori che dicono dove tende a
rendere meglio — e una **confidenza** che cresce con le partite valutate e
satura dopo otto. La confidenza non è una diagnostica interna: è la manopola che
fa degradare tutto in modo morbido. A confidenza zero il ruolo non influenza il
sorteggio in alcun modo, così l'app non finge di sapere dove mettere chi è nuovo.

### Il profilo di partenza

Chi si iscrive può indicare dove parte: *«parto attaccante»*. Serve a non
sorteggiare del tutto alla cieca la prima domenica e a dare subito qualcosa da
mostrare accanto al nome.

Non è una dichiarazione di identità, e l'app lo dice: compare come **«Attaccante
· di partenza»** finché le partite non lo confermano. Pesa 0,30 di confidenza —
abbastanza da orientare, non abbastanza da fissare nessuno — e dopo quattro
partite valutate il suffisso cade. Se i fatti dicono un'altra cosa, l'etichetta
cambia. È testato esplicitamente, perché senza quella garanzia il profilo di
partenza sarebbe solo l'autodichiarazione con un altro nome.

### L'etichetta, e il jolly

L'etichetta compare solo con abbastanza partite **e** un distacco netto fra il
primo ruolo e il secondo. Altrimenti resta **Jolly**, che non è l'assenza di un
dato ma un'etichetta positiva — e nel calcetto è anche la più frequente.

### Il portiere è l'eccezione: si dichiara

Fare il portiere è un'identità, non una cosa in cui si scivola dopo dieci minuti
buoni fra i pali: chi para, para tutte le domeniche. Il flag `portiere` lo mette
la persona iscrivendosi o l'organizzatore, e **nessuna votazione lo accende o lo
spegne**. Lato database un trigger riserva quel campo a chi organizza, perché è
un vincolo sul sorteggio e non una preferenza personale.

L'esclusione vale in entrambe le direzioni, e la seconda è quella che conta di
più: **POR è fuori dallo spazio delle deduzioni**. Un centrocampista che se la
cava durante una turnazione raccoglie nomine per le parate e, senza questa
regola, si ritroverebbe etichettato portiere e poi spedito fra i pali dal
vincolo, per sempre. La fase «porta» continua ad alimentare l'attributo *parate*
— serve a scegliere chi improvvisa meglio quando serve — ma non tocca la
propensione.

Questa esclusione ha avuto un effetto collaterale che non era previsto: nella
simulazione da zero dati l'accuratezza complessiva è salita da 17 etichette
giuste su 18 a **20 su 20**. Le nomine per le parate stavano rumorosamente
inquinando anche la deduzione dei ruoli di movimento — chi faceva turni in porta
accumulava un quarto valore che competeva con gli altri tre e rallentava il
distacco.

### Tarabile per gruppo

`ParametriRuolo` decide quanto conta il ruolo. Il default descrive un calcetto
normale: il fuori ruolo vale 0,90 e il secondario 0,97, cioè un calo percepibile
ma non punitivo. `LIBERI` azzera la differenza, `RIGIDI` la porta a 0,80 con una
penalità forte, per un gruppo che tiene le posizioni.

Questa taratura non è estetica. La prima versione penalizzava il fuori ruolo del
20% fisso, e quel divario dava all'ottimizzatore una leva: spostare un forte
fuori ruolo ne abbassava il valore, e i conti tornavano. Il 41% dei giocatori
finiva schierato altrove. Abbassando il calo, la leva non conviene più e il
problema si dissolve.

---

## Il voto di fine partita

### Si vota per fase, non per ruolo

Un difensore può essere il miglior regista in campo. Chiedere «chi è stato il
miglior centrocampista» quel dato lo perderebbe.

| Fase | Domanda | Alimenta | Sposta la propensione verso |
|---|---|---|---|
| **Difesa** | Chi ha difeso meglio? | *difesa* | DIF |
| **Attacco** | Chi ha fatto più male davanti? | *tiro* | ATT |
| **Regia** | Chi ha fatto giocare meglio la squadra? | *passaggio* (0,65), *tecnica* (0,35) | CEN |
| **Porta** | Chi ha parato meglio? | *parate* | nessuna — il portiere si dichiara |

Le fasi si mappano una a una sui ruoli **e** sugli attributi, ed è questo che
rende il sistema onesto: un voto generico «ha giocato bene» non saprebbe quale
delle sette voci alzare, e spalmarlo su tutte le farebbe muovere sempre insieme
fino a renderle decorative.

Il movimento avviene **indipendentemente da dove la persona era schierata**. Un
difensore nominato per la regia guadagna propensione da centrocampista, e il
sorteggio successivo lo sa.

### Si nomina, non si dà un punteggio

Un voto da 1 a 10 per tredici persone su quattro fasi sono cinquantadue caselle.
Nessuno le compila dopo la partita, sudato, mentre gli altri si stanno
cambiando — e chi ci prova mette numeri a caso pur di finire. Quattro nomi sono
quattro tocchi.

Regole:

- nessuno vota sé stesso;
- per la fase «porta» si possono nominare solo quelli che ci hanno passato dei
  minuti;
- **«nessuno si è distinto» è sempre disponibile**: obbligare a scegliere
  produce nomi a caso, che è esattamente il rumore che si vuole evitare.

### Come una nomina diventa un numero

Non ricevere nomine non vuol dire aver giocato male: in ogni fase può vincerne
uno solo. Lo zero quindi non è il fondo della scala. Il riferimento è la quota
che toccherebbe a ciascuno se i voti fossero distribuiti a caso — con quattordici
in campo, circa il 7% — e il massimo si raggiunge al 40% delle nomine. Chi non ne
prende nessuna scende di poco; chi le prende quasi tutte sale al massimo.

---

## Il sorteggio

### Eque e sempre diverse

I due requisiti si contraddicono. Un ottimizzatore che cerca la divisione
perfetta, con gli stessi quattordici convocati, dà sempre lo stesso risultato.

Si generano quindi **60 partizioni indipendenti**, ciascuna raffinata con una
ricerca locale a due mosse — scambio incrociato fra le squadre, e scambio di
ruolo dentro la squadra — e poi si **estrae a sorte fra tutte quelle entro l'8%
dal costo minimo**. Il seme viene salvato, quindi un sorteggio è sempre
riproducibile.

Costo = 1,00 × scarto totale + 0,45 × squilibrio per reparto + 0,60 × scarto fra
i due migliori + 0,35 × coppie ricorrenti + penalità di ruolo.

**La varietà ha bisogno dello storico.** Con una rosa fissa e storico vuoto si
ottengono solo 9 formazioni distinte su 25; con lo storico che si accumula, 25
su 25. È la penalità anti-ripetizione a fare il lavoro, e va detto perché
riguarda il primo sorteggio di ogni gruppo nuovo.

### La cascata dell'informazione

Il sorteggio dichiara sempre con quanta informazione ha lavorato. Non è una
diagnostica: un sorteggio fatto al buio va annunciato come tale invece di
sembrare un verdetto.

| Livello | Quando | Cosa dice l'app |
|---|---|---|
| `ETICHETTE` | Metà dei convocati ha un'etichetta e la confidenza media supera 0,35 | «Squadre fatte con ruoli e rendimenti.» |
| `RENDIMENTI` | Ruoli incerti, ma le forze differiscono | «Ruoli ancora incerti: squadre pareggiate sui rendimenti.» |
| `SORTEGGIO_PURO` | Troppi giocatori nuovi, niente da distinguere | «Troppi giocatori nuovi per sapere come dividervi: sorteggio puro.» |

Sotto, però, la degradazione è **continua**: ogni giocatore pesa per la sua
confidenza. A gradini, un solo giocatore nuovo farebbe precipitare tutta la
partita al livello più basso, buttando via le informazioni sugli altri tredici. I
tre gradini servono solo al messaggio, perché «confidenza media 0,41» non
significa niente per nessuno.

Il sorteggio puro è **casuale e non alfabetico**, di proposito: l'ordine
alfabetico darebbe sempre le stesse due squadre agli stessi quattordici,
settimana dopo settimana, e farebbe finta di essere un criterio pur non
essendolo. Il caso dice la stessa cosa — *non so niente di voi* — senza mentire.

### Il portiere è un vincolo, non un costo

Se fra i convocati c'è chi para di mestiere, gioca in porta, e nessuna mossa
successiva può toglierlo di lì.

Trattarlo come termine di costo produceva un difetto sottile: due portieri
improvvisati hanno fra loro uno scarto minore di due bravi, quindi l'algoritmo
schierava i portieri veri in campo per far tornare i conti. La differenza fra i
due portieri si compensa invece sui giocatori di movimento.

### Quando i portieri mancano

Su 40 partite simulate: 24 con due portieri, 15 con uno solo, 1 con nessuno. Non
è un caso limite, è il calcetto.

Per le squadre scoperte i 60 minuti si dividono fra tre giocatori, scelti dando
la precedenza a **chi in porta ci è finito meno volte** e solo a parità di turni
a chi para meglio. Senza questo criterio, chi ha qualche riflesso in più diventa
il portiere fisso della comitiva: è il modo in cui queste app smettono di essere
usate.

Turni in `draw_keeper_shifts`, totali nella vista `turni_porta`.

### Le squadre sono una proposta

L'organizzatore le rimaneggia prima di chiudere la partita, e vede lo **scarto di
forza aggiornarsi mentre sposta** (`SorteggioSquadre.scarto`). L'app avvisa se lo
squilibrio supera una soglia ma non blocca mai: chi organizza sa cose che l'app
non sa.

I ruoli mostrati descrivono come si parte, non come si deve giocare — la
formazione lo dice esplicitamente: *«I ruoli sono indicativi: in campo
mescolatevi pure.»*

Formazione sorteggiata (`draw_slots`) e formazione finale (`match_lineup`) si
conservano **separate**. Altrimenti l'evoluzione attribuirebbe i rendimenti alla
casella sbagliata, e uno spostato in difesa dall'organizzatore risulterebbe un
attaccante che non ha segnato.

---

## Le skill

Sette attributi su scala 1–99 — velocità, tiro, passaggio, tecnica, difesa,
fisico, parate — tutti a 50 di partenza. L'organizzatore alza i valori iniziali
di chi già si sa che gioca bene.

Il valore in campo (OVR) si calcola sempre rispetto al ruolo, con pesi che
sommano a 1 per riga, moltiplicato per l'affinità al ruolo:

| Ruolo | Velocità | Tiro | Passaggio | Tecnica | Difesa | Fisico | Parate |
|---|---|---|---|---|---|---|---|
| POR | 0,05 | — | 0,05 | — | 0,10 | 0,10 | 0,70 |
| DIF | 0,15 | 0,05 | 0,15 | 0,05 | 0,40 | 0,20 | — |
| CEN | 0,15 | 0,15 | 0,30 | 0,20 | 0,10 | 0,10 | — |
| ATT | 0,25 | 0,35 | 0,10 | 0,20 | 0,05 | 0,05 | — |

Le skill si muovono con le nomine ricevute, ognuna sulla voce che le compete. Il
freno contro l'inflazione è nei rendimenti decrescenti: salire da 92 costa molto
più che da 50, e scendere da 30 è altrettanto difficile. Senza quel freno, dopo
un anno la comitiva è tutta a 99 e i numeri non dicono più niente.

Ogni variazione finisce in `rating_history`, così si può sempre rispondere a
«perché sono sceso?».

---

## Il database

15 tabelle, 4 viste, Row Level Security su tutto.

| Tabella | Cosa tiene |
|---|---|
| `profiles` | Email, nome, foto o avatar, token push |
| `groups` | La comitiva, con codice d'invito e campo abituale |
| `group_members` | Chi sta in quale gruppo, e chi organizza |
| `player_ratings` | I sette attributi, la forma, le medie personali |
| `player_roles` | Propensione ai quattro ruoli, partite valutate, profilo di partenza, flag portiere |
| `matches` | Data, ora, campo, formato, capienza, stato, risultato |
| `match_signups` | Iscritti con numero d'ordine, convocati e riserve |
| `match_draws` | Ogni sorteggio con seme, costo, modalità porta e livello di informazione |
| `draw_slots` | La formazione sorteggiata |
| `draw_keeper_shifts` | I turni in porta, minuto per minuto |
| `match_lineup` | La formazione finale, dopo i ritocchi dell'organizzatore |
| `match_votes` | Le nomine per fase di gioco |
| `match_events` | Gol, assist, autogol |
| `rating_history` | Ogni variazione di skill, con il motivo |
| `notifications_outbox` | Push ed email in attesa di partire |

Viste: `teammate_history` (coppie ricorrenti, alimenta l'anti-ripetizione),
`turni_porta` (turni e minuti già fatti fra i pali), `chi_ha_parato` (chi è
nominabile nella fase porta), `nomine_ricevute` (l'ingresso dell'aggiornamento
di skill e propensione).

**La logica delicata sta nel database, non nell'app.** Chiusura iscrizioni,
invio conferme e promozione delle riserve sono tre trigger Postgres. Il motivo è
concreto: se due persone si iscrivono nello stesso istante al quattordicesimo
posto, solo il database può decidere chi entra senza ambiguità. Lasciarlo fare
all'app significa accettare che prima o poi si presentino in quindici.

Un quarto trigger riserva il flag `portiere` a chi organizza.

La migrazione `004` aggiunge le regole della serata, e sono nel database per lo
stesso motivo: un controllo che vive nella pagina si aggira con una chiamata
diretta a PostgREST.

**La lista d'attesa si ferma a due**, e il muro sta su due porte — la INSERT di
chi si iscrive e la UPDATE di chi si era ritirato e rientra. Chiuderne una sola
sembra sufficiente finché non arriva il primo ripensamento.

**Il ritiro si conta ma non si paga.** Solo chi molla un posto da convocato:
una riserva che si sfila non lascia nessuno a piedi. E una volta per partita,
non una per ripensamento — da qui la colonna `ritiro_contato`. Le presenze
invece non hanno un contatore: sono già scritte nelle iscrizioni, e un
contatore che duplica un dato prima o poi diverge da quel dato.

**I gol dichiarati devono fare il risultato.** È una funzione e non un `check`
perché guarda tre tabelle insieme, e perché deve valere alla chiusura: durante
la serata il conto è per forza sbagliato, il primo che dichiara un gol lo
dichiara quando il risultato ancora non c'è. L'autogol conta per l'altra
squadra — l'unico punto in cui il conteggio non è «somma i gol dei miei» — e a
referto chiuso i gol si congelano, altrimenti il vincolo durerebbe il tempo di
una chiamata in più. Riaprire si può: senza, il primo errore è per sempre e la
gente impara a non chiudere niente.

---

## Il flusso

1. L'organizzatore apre la partita — data, ora, campo, formato, quota.
2. Push al gruppo, contatore iscrizioni in tempo reale.
3. Iscrizioni in ordine di arrivo; oltre capienza si va in lista d'attesa.
4. Al quattordicesimo **chiusura automatica**: conferma push ed email con data,
   ora, campo e mappa, più i promemoria a 24 e 2 ore messi in coda nella stessa
   transazione.
5. Sorteggio squadre, con i turni in porta se servono. L'organizzatore ritocca.
6. **Forfait**: la prima riserva viene promossa e avvisata; se non ce ne sono, la
   partita riapre e il sorteggio viene invalidato, perché con tredici non vale
   più.
7. Dopo la partita: risultato, marcatori, e le quattro nomine. Skill e profili si
   aggiornano, e ognuno riceve la notifica di cosa è cambiato.

---

## Verifica

72 test in `TeamDrawTest`, `VotazioneTest`, `ProfiloDiPartenzaTest`,
`DueRuoliDiPartenzaTest` e `PortiereTest`, più il banco di prova `Simulazione`
per le misure su stagioni intere.

Sul database, 33 prove in `db/locale/01_prova_regole.sql`: non test unitari, ma
la serata tipo fatta succedere davvero su un Postgres vero — venti iscritti su
quattordici posti, uno che molla, uno che ci ripensa, e un referto in cui i
conti non tornano.

I test che contano di più sono quelli che presidiano i difetti trovati in
simulazione, perché sono difetti che non fanno fallire niente: producono squadre
plausibili e sbagliate.

| | 7v7 | 8v8 |
|---|---|---|
| Scarto di forza medio | 0,28% | 0,22% |
| Scarto peggiore | 1,12% | 0,99% |
| Formazioni distinte su 40 | 40/40 | 40/40 |
| Violazioni vincolo portiere | 0/40 | 0/40 |
| Tempo per sorteggio (JVM) | ~120 ms | ~140 ms |

**Convergenza da zero dati** — venti persone, nessuna informazione sui ruoli di
movimento, solo le nomine dei compagni con il rumore che hanno nella realtà:

| Dopo | Con etichetta | Di cui giuste |
|---|---|---|
| 5 partite | 9 su 20 | 9 su 9 |
| 10 partite | 18 su 20 | 18 su 18 |
| 40 partite | 20 su 20 | 20 su 20 |

Dalla decima partita in poi il sorteggio dichiara il livello pieno; prima dice
onestamente che sta andando sui rendimenti.

**Scenario peggiore della porta** — rosa di 16, nessun portiere dichiarato, 40
partite: tutti e 16 coinvolti, 300 minuti a testa esatti.

---

## Roadmap

- **Fase 0** — impianto: repo, schema, motore di sorteggio *(fatto)*
- **Fase 1** — identità e gruppo: Supabase, registrazione email, profilo con
  foto, profilo di partenza, codice d'invito
- **Fase 2** — partite e convocazioni: creazione, iscrizione in tempo reale,
  chiusura automatica, lista d'attesa, conferma push ed email
- **Fase 3** — squadre: taratura iniziale, schermata del campo, ritocchi
  dell'organizzatore con scarto in tempo reale, notifica formazione e turni
- **Fase 4** — rendimento: risultato, marcatori, le quattro nomine, evoluzione,
  storico e classifiche
- **Fase 5** — web app Next.js su Vercel, stesso database
- **Fase 6** — pubblicazione: 12 tester per 14 giorni, Play Store

La guida operativa per compilare, firmare e pubblicare è in
[`build-e-deploy.md`](build-e-deploy.md).
