# CallMeUp

App Android per organizzare le partite di calcetto a 7 e a 8. Si apre la
partita, ci si iscrive, al quattordicesimo le iscrizioni si chiudono da sole e
arriva la conferma con data, ora e campo. Le squadre le sorteggia l'app:
equilibrate, ma diverse ogni volta.

Il nome è *call-up*, la convocazione.

## Stato

**Fase 0 — impianto.** Il motore di sorteggio è scritto, coperto da 63 test e funzionante;
lo schema del database è pronto da eseguire; l'app Android si compila e si
installa, ma mostra ancora una schermata segnaposto.

| | Fase | |
|---|---|---|
| ✅ | **0 · Impianto** | Schema SQL, motore di sorteggio, progetto compilabile |
| | 1 · Identità e gruppo | Registrazione, profilo con foto, codice d'invito |
| | 2 · Partite e convocazioni | Iscrizioni in tempo reale, chiusura automatica, push ed email |
| | 3 · Squadre | Taratura skill, schermata del campo, notifica formazione |
| | 4 · Rendimento | Risultato, marcatori, evoluzione skill, classifiche |
| | 5 · Web app | Next.js sullo stesso database |
| | 6 · Pubblicazione | Play Store |

## Com'è fatto

```
app/                 app Android — Kotlin, Jetpack Compose, Material 3
core/teamdraw/       motore di sorteggio — Kotlin puro, zero dipendenze
db/migrations/       schema Postgres, con trigger e Row Level Security
docs/                impianto tecnico e guida al build
tools/proto/         prototipo Python usato per validare l'algoritmo
```

Due scelte meritano una spiegazione.

**Il motore di sorteggio è un modulo Kotlin senza dipendenze da Android.** Gira
sul telefono di chi organizza, quindi non costa un centesimo di server, si testa
con una JVM in un secondo, e se un domani serve spostarlo dentro un backend si
ricompila senza modifiche.

**Tutto l'accesso ai dati passa da interfacce**, non direttamente dall'SDK di
Supabase. Sostituire Supabase con un backend proprio significa scrivere una
nuova implementazione, non riscrivere l'app.

## Partire

```bash
./gradlew :core:teamdraw:test   # 63 test del motore
./gradlew assembleDebug         # APK installabile
```

Per l'ambiente, la firma, il Play Store e la pubblicazione della web app:
**[docs/build-e-deploy.md](docs/build-e-deploy.md)**.

Il database va creato una volta sola su Supabase eseguendo
`db/migrations/001_schema.sql`. Chiavi e percorso dell'SDK vanno in
`local.properties`, che non è versionato.

## Il sorteggio

Il problema è che due requisiti si contraddicono: le squadre devono essere
**eque** e devono essere **diverse ogni volta**. Un ottimizzatore che cerca la
divisione perfetta, con gli stessi quattordici convocati, dà sempre lo stesso
risultato.

La soluzione: si generano 60 partizioni indipendenti, ciascuna raffinata con una
ricerca locale, e poi si **estrae a sorte fra tutte quelle entro l'8% dalla
migliore**. Il seme viene salvato, quindi un sorteggio è sempre riproducibile.

Una funzione di costo somma ciò che rende sgradevole una divisione: scarto di
forza complessivo, squilibrio per reparto, differenza fra i due più forti,
coppie che finiscono sempre insieme, e una penalità leggera per chi gioca fuori
ruolo.

### Nessuno ha un ruolo fisso

Il ruolo non si dichiara all'iscrizione — se si dichiarasse, si dichiarerebbero
tutti attaccanti — ma si deduce da come è andata in campo. Ogni giocatore ha una
**propensione**: quattro valori che dicono dove tende a rendere meglio, più una
**confidenza** che cresce con le partite. Chi è nuovo ha confidenza zero e il
ruolo non lo influenza affatto, così l'app non finge di sapere dove metterlo.

Chi vuole può indicare un **profilo di partenza** all'iscrizione. Serve a non
sorteggiare del tutto alla cieca la prima domenica, compare come *"Attaccante ·
di partenza"*, e i fatti lo scavalcano in poche partite.

L'etichetta compare solo con abbastanza partite e un distacco netto. Altrimenti
resta **Jolly**, che non è l'assenza di un dato ma un'etichetta positiva, e nel
calcetto è anche la più frequente.

### Il portiere invece si dichiara, ed è un vincolo

Fare il portiere è un'identità, non una cosa in cui si scivola dopo dieci minuti
buoni fra i pali: chi para, para tutte le domeniche. Quindi quel ruolo non si
deduce dai voti — lo imposta la persona iscrivendosi o l'organizzatore — e in
porta ci va, senza che nessuna ottimizzazione possa spostarlo. Se per una volta
deve giocare in campo, è l'organizzatore a farlo a mano.

L'esclusione vale in entrambe le direzioni, ed è la seconda che sorprende: un
centrocampista che se la cava durante una turnazione raccoglie nomine per le
parate e, senza questa regola, si ritroverebbe etichettato portiere e poi
spedito fra i pali dal vincolo, per sempre.

Prima ancora, la versione che trattava la porta come un costo da bilanciare
schierava i portieri veri in campo, perché due improvvisati sono più *simili fra
loro* di due bravi e così il conto tornava prima.

Quando i portieri mancano — che è la norma: su 40 partite simulate solo 24 ne
avevano due — l'app divide i minuti fra tre giocatori e dà la precedenza a chi
in porta ci è finito meno volte.

### Quando non si sa, lo si dice

Il sorteggio dichiara sempre con quanta informazione ha lavorato: *squadre fatte
con ruoli e rendimenti*, *ruoli ancora incerti*, oppure *troppi giocatori nuovi
per sapere come dividervi: sorteggio puro*. Quest'ultimo è casuale e non
alfabetico di proposito — l'alfabetico darebbe sempre le stesse due squadre agli
stessi quattordici, fingendo per giunta di essere un criterio.

### Il voto di fine partita

Si vota per **fase di gioco**, non per ruolo: difesa, attacco, regia, porta. Un
difensore può essere il miglior regista in campo, e votando il ruolo quel dato
andrebbe perso. Ed è una **nomina**, non un punteggio — quattro nomi, quattro
tocchi — perché un voto da 1 a 10 per tredici persone su quattro fasi sono
cinquantadue caselle che nessuno compila dopo la partita.

Ogni fase alimenta ciò che le compete: difesa → *difesa*, attacco → *tiro*,
regia → *passaggio* e *tecnica*, porta → *parate*. È questo che tiene in piedi
le sette voci: un voto generico "ha giocato bene" le muoverebbe tutte insieme
fino a renderle decorative.

### Le squadre sono una proposta

L'organizzatore le rimaneggia prima di chiudere la partita, e vede lo scarto di
forza aggiornarsi mentre sposta. I ruoli mostrati descrivono come si parte, non
come si deve giocare. Formazione sorteggiata e formazione finale si conservano
separate, altrimenti l'evoluzione attribuirebbe i rendimenti alla casella
sbagliata.

### Verificato, non solo scritto

I numeri qui sotto escono dal motore compilato, non da una stima.

| | 7v7 | 8v8 |
|---|---|---|
| Scarto di forza fra le squadre | 0,28% | 0,35% |
| Formazioni distinte su 40 partite | 40/40 | 40/40 |
| Violazioni del vincolo portiere | 0/40 | 0/40 |
| Tempo per sorteggio | ~87 ms | ~90 ms |

Partendo da **zero dati** sui ruoli di movimento, con i soli voti dei compagni:
dopo 10 partite 18 giocatori su 20 hanno un'etichetta e sono tutte corrette;
dopo una stagione, 20 su 20.

Nello scenario peggiore della porta — rosa di 16 senza nemmeno un portiere di
ruolo, 40 partite — tutti e 16 hanno fatto fra 280 e 320 minuti fra i pali.

## Le skill

Sette attributi su scala 1–99: velocità, tiro, passaggio, tecnica, difesa,
fisico, parate. Tutti partono da 50, e chi organizza alza i valori iniziali di
chi già si sa che gioca bene.

Il valore in campo si calcola sempre rispetto al ruolo, perché un ottimo
difensore schierato in attacco non è più un ottimo giocatore. Le skill crescono
sullo scarto fra rendimento e media personale, non sul numero assoluto di gol:
due gol sono un risultato notevole per un difensore e una serata normale per un
attaccante.

Dettagli in [docs/impianto-tecnico.md](docs/impianto-tecnico.md).

## Licenza

Tutti i diritti riservati. Il codice è pubblicato per consultazione; non è
concesso alcun permesso di uso, copia, modifica o distribuzione.
