# CallMeUp

App Android per organizzare le partite di calcetto a 7 e a 8. Si apre la
partita, ci si iscrive, al quattordicesimo le iscrizioni si chiudono da sole e
arriva la conferma con data, ora e campo. Le squadre le sorteggia l'app:
equilibrate, ma diverse ogni volta.

Il nome è *call-up*, la convocazione.

## Stato

**Fase 0 — impianto.** Il motore di sorteggio è scritto, testato e funzionante;
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
./gradlew :core:teamdraw:test   # 22 test del motore di sorteggio
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

### Due cose che il calcetto impone

**I ruoli di movimento sono fluidi.** Un difensore si ritrova in area avversaria
e un attaccante rientra a coprire, dentro la stessa partita. Il ruolo è quindi
una preferenza leggera e giocare altrove costa poco (`ParametriRuolo`, che ogni
gruppo può tarare: da `LIBERI` a `RIGIDI`).

**Il portiere invece è un vincolo.** Se fra i convocati c'è un portiere di
ruolo, gioca in porta, e nessuna ottimizzazione successiva può spostarlo. La
versione che lo trattava come un costo da bilanciare schierava i portieri veri
in campo, perché due portieri improvvisati sono più *simili fra loro* di due
bravi, e così il conto tornava prima.

Quando i portieri mancano — che è la norma, non l'eccezione: su 40 partite
simulate solo 24 ne avevano due — l'app divide i minuti fra tre giocatori e
sceglie dando la precedenza a chi in porta ci è finito meno volte. Senza questo
criterio, chi ha qualche riflesso in più diventa il portiere fisso della
comitiva.

### Verificato, non solo scritto

I numeri qui sotto escono dal motore compilato, non da una stima.

| | 7v7 | 8v8 |
|---|---|---|
| Scarto di forza fra le squadre | 0,50% | 0,26% |
| Formazioni distinte su 40 partite | 40/40 | 40/40 |
| Violazioni del vincolo portiere | 0/40 | 0/40 |
| Tempo per sorteggio | ~61 ms | ~70 ms |

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
