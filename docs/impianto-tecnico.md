# Impianto tecnico

Documento di riferimento del progetto. La versione illustrata, con il campo
disegnato e i grafici, sta nell'artifact collegato in fondo.

## Decisioni

| Ambito | Scelta | Perché |
|---|---|---|
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

## Skill e ruoli

Sette attributi su scala 1–99, tutti a 50 di partenza. Il valore in campo (OVR)
si calcola rispetto al ruolo, con pesi che sommano a 1 per riga.

| Ruolo | Velocità | Tiro | Passaggio | Tecnica | Difesa | Fisico | Parate |
|---|---|---|---|---|---|---|---|
| POR | 0.05 | — | 0.05 | — | 0.10 | 0.10 | 0.70 |
| DIF | 0.15 | 0.05 | 0.15 | 0.05 | 0.40 | 0.20 | — |
| CEN | 0.15 | 0.15 | 0.30 | 0.20 | 0.10 | 0.10 | — |
| ATT | 0.25 | 0.35 | 0.10 | 0.20 | 0.05 | 0.05 | — |

### I ruoli di movimento sono fluidi

Nel calcetto un difensore si ritrova in area avversaria e un attaccante rientra
a coprire, dentro la stessa partita. Il ruolo è quindi una preferenza leggera:
di default il fuori ruolo vale 0.90 e il secondario 0.97, cioè un calo
percepibile ma non punitivo.

Questa non è una scelta estetica. La prima versione penalizzava il fuori ruolo
del 20%, e quel divario dava all'ottimizzatore una leva: spostare un forte fuori
ruolo ne abbassava il valore, e i conti tornavano. Il 41% dei giocatori finiva
schierato altrove. Abbassando il calo, la leva sparisce da sola.

`ParametriRuolo` è tarabile per gruppo: `LIBERI` (il ruolo è solo un'indicazione
di dove partire) e `RIGIDI` (gruppo ordinato che tiene le posizioni).

### Evoluzione

Le skill crescono sullo scarto fra rendimento e media mobile personale, non sul
numero assoluto: due gol sono un risultato notevole per un difensore e una
serata normale per un attaccante.

- **Tiro** — gol contro gol attesi
- **Passaggio e tecnica** — assist e voti dei compagni
- **Difesa e parate** — gol subiti rispetto alla media, porta inviolata
- **Fisico** — continuità di presenza, con decadimento dopo tre assenze

Due freni contro l'inflazione: la crescita rallenta al salire del valore, e dopo
ogni partita la media del gruppo torna dolcemente verso 50. Ogni variazione
finisce in `rating_history`, così si può sempre rispondere a «perché sono
sceso?».

## Sorteggio

60 partizioni indipendenti, ciascuna raffinata con ricerca locale a due mosse
(scambio incrociato fra squadre, scambio di ruolo dentro la squadra). Poi si
estrae a sorte fra tutte quelle entro l'8% dal costo minimo: è così che le
squadre restano eque senza mai ripetersi. Il seme viene salvato.

Costo = 1.00 × scarto totale + 0.45 × squilibrio per reparto + 0.60 × scarto fra
i due migliori + 0.35 × coppie ricorrenti + penalità di ruolo.

### Il portiere è un vincolo

Trattarlo come termine di costo produceva un difetto sottile: due portieri
improvvisati hanno fra loro uno scarto minore di due bravi, quindi l'algoritmo
schierava i portieri veri in campo per far tornare i conti. Ora la porta si
assegna prima del sorteggio e nessuna mossa può toccarla.

### Quando i portieri mancano

Su 40 partite: 24 con due portieri di ruolo, 14 con uno solo, 2 con nessuno. Per
le squadre senza portiere i 60 minuti si dividono fra tre giocatori, scelti
dando la precedenza a chi ci è finito meno volte. Turni in
`draw_keeper_shifts`, totali nella vista `turni_porta`.

## Verifica

22 test in `TeamDrawTest`, più il banco di prova `Simulazione`.

| | 7v7 | 8v8 |
|---|---|---|
| Scarto di forza medio | 0,50% | 0,26% |
| Formazioni distinte | 40/40 | 40/40 |
| Fuori ruolo | 13,8% | 13,2% |
| Violazioni vincolo portiere | 0/40 | 0/40 |
| Tempo per sorteggio | 61 ms | 70 ms |

Scenario peggiore della porta (rosa di 16, nessun portiere, 40 partite): tutti
coinvolti, da 280 a 320 minuti a testa, squilibrio 1,14×.

## Database

13 tabelle, 2 viste, 3 trigger, Row Level Security su tutto. Chiusura
iscrizioni, invio conferme e promozione delle riserve stanno nel database e non
nell'app: se due persone si iscrivono nello stesso istante al quattordicesimo
posto, solo il database può decidere senza ambiguità chi entra.

Schema completo: `db/migrations/001_schema.sql`.

## Flusso

1. L'organizzatore apre la partita
2. Push al gruppo, contatore in tempo reale
3. Iscrizioni in ordine di arrivo, oltre capienza si va in lista d'attesa
4. Al quattordicesimo chiusura automatica, conferma push ed email con data, ora,
   campo e mappa, più i promemoria a 24 e 2 ore in coda
5. Sorteggio squadre, con i turni in porta se servono
6. Forfait: la prima riserva viene promossa; se non ce ne sono, la partita
   riapre e il sorteggio viene invalidato
7. Dopo la partita: risultato, marcatori, skill aggiornate

## Roadmap

Vedi il README. La guida operativa per compilare e pubblicare è in
`build-e-deploy.md`.
