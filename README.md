# Convocami

**Organizza la partita. Convoca il gruppo. Scendi in campo.**

Convocami è una piattaforma per gestire partite di calcetto a 7 e a 8: riunisce
convocazioni, lista d'attesa, composizione delle squadre e rendimento dei
giocatori in un unico flusso progettato per web e Android.

L'obiettivo è semplice: togliere lavoro a chi organizza e rendere ogni partita
più equilibrata, senza trasformare la chat del gruppo in un gestionale.

## Cosa fa

- **Gestisce le convocazioni** in ordine di iscrizione, chiudendole
  automaticamente al raggiungimento della capienza.
- **Organizza la lista d'attesa** e promuove la prima riserva quando si libera
  un posto.
- **Propone squadre equilibrate e sempre diverse**, considerando livello,
  ruoli e storico delle formazioni.
- **Impara dalle partite** attraverso nomine rapide per fase di gioco: difesa,
  attacco, regia e porta.
- **Tutela i dati del gruppo** con Row Level Security e profili pubblici privi
  dell'indirizzo email.
- **Mantiene un'esperienza coerente** tra web e Android grazie a un'identità
  visiva condivisa.

## Come funziona

1. L'organizzatore crea una partita con data, campo, formato e quota.
2. I giocatori si iscrivono; raggiunta la capienza, le iscrizioni successive
   entrano in lista d'attesa.
3. Il motore genera squadre bilanciate. L'organizzatore può rifinire la
   formazione prima di pubblicarla.
4. Dopo la partita, il gruppo segnala le prestazioni migliori con pochi tocchi.
5. Ruoli e valori evolvono nel tempo, migliorando i sorteggi successivi.

Il sorteggio non cerca soltanto la divisione con lo scarto minimo: seleziona
una soluzione tra le migliori disponibili e penalizza gli abbinamenti già
visti. Il risultato resta riproducibile grazie al salvataggio del seme casuale.
Quando i dati non sono ancora sufficienti, il sistema applica un sorteggio
puro invece di attribuire ai giocatori caratteristiche non osservate.

## Architettura

```text
app/                 client Android — Kotlin e Jetpack Compose
core/teamdraw/       motore di sorteggio — Kotlin puro, senza dipendenze Android
db/migrations/       schema PostgreSQL, trigger e policy Row Level Security
db/locale/           ambiente e verifiche locali del database
web/                 client web statico — HTML, CSS e JavaScript
design/              token visivi condivisi e schermate di riferimento
docs/                documentazione tecnica, avvio e distribuzione
tools/proto/         prototipo usato per validare l'algoritmo
```

Le responsabilità sono separate in modo netto:

- il **database** garantisce atomicità e vincoli su iscrizioni, ritiri e
  promozione delle riserve;
- `core/teamdraw` contiene l'algoritmo di composizione delle squadre ed è
  indipendente dai client;
- la **web app** non richiede build, package manager o framework;
- il client **Android** usa Compose e condivide con il web i valori definiti in
  `design/tokens.json`.

## Stato del progetto

| Area | Stato |
|---|---|
| Schema dati, trigger e policy di accesso | **Completato e verificato** |
| Motore di sorteggio e modello dei ruoli | **Completato e coperto da test** |
| Web: accesso, gruppi, partite e iscrizioni | **Implementato, da validare end-to-end su Supabase** |
| Android: fondazioni UI e primo flusso | **In sviluppo** |
| Formazioni, referto e statistiche nei client | **Pianificato** |
| Notifiche e distribuzione sugli store | **Pianificato** |

Lo stato operativo dettagliato, incluse le attività successive e i relativi
criteri di completamento, è disponibile in
[`docs/passaggio-a-code.md`](docs/passaggio-a-code.md).

## Avvio rapido

### Motore di sorteggio

Requisito: JDK 17. Se il wrapper Gradle non è presente, può essere generato con
`gradle wrapper --gradle-version 8.12`.

```bash
./gradlew :core:teamdraw:test
```

### Web app

Inserisci URL e chiave anon del progetto Supabase in `web/config.js`, quindi:

```bash
cd web
python3 -m http.server 8000
```

Apri [http://localhost:8000](http://localhost:8000). La configurazione completa
di database, autenticazione e deploy è descritta in
[`docs/primo-avvio.md`](docs/primo-avvio.md).

### App Android

Con Android SDK configurato:

```bash
./gradlew assembleDebug
```

L'APK viene generato in `app/build/outputs/apk/debug/app-debug.apk`. In
alternativa, la pipeline GitHub Actions produce automaticamente un APK di debug
scaricabile dagli artifact della run. Per ambiente locale, firma e
pubblicazione consulta [`docs/build-e-deploy.md`](docs/build-e-deploy.md).

## Qualità e sicurezza

- Il motore è verificato da **72 test** e da simulazioni su stagioni complete.
- Le regole del database sono coperte da **33 prove** eseguite su PostgreSQL.
- Ogni tabella applicativa usa Row Level Security e appartiene a un gruppo.
- I client leggono esclusivamente i profili pubblici; l'email resta privata.
- Le operazioni concorrenti critiche sono risolte nel database, non nel client.

Per eseguire le prove locali del database:

```bash
createdb convocami
psql -d convocami -f db/locale/00_finta_supabase.sql
for migration in db/migrations/0*.sql; do
  psql -d convocami -f "$migration"
done
psql -d convocami -f db/locale/01_prova_regole.sql
```

## Documentazione

- [Primo avvio](docs/primo-avvio.md) — dalla configurazione Supabase alla web
  app online.
- [Impianto tecnico](docs/impianto-tecnico.md) — modello dati, algoritmo e
  decisioni architetturali.
- [Build e deploy](docs/build-e-deploy.md) — APK, firma e pubblicazione.
- [Direzione visiva Android](docs/direzione-visiva-android.md) — principi e
  regole dell'interfaccia.
- [Passaggio di consegne](docs/passaggio-a-code.md) — stato puntuale e roadmap
  di implementazione.

## Licenza

Copyright © 2026 Giuseppe D'Agostino. Tutti i diritti riservati.

Il codice è pubblicato esclusivamente per consultazione. Non è concesso alcun
permesso di utilizzo, copia, modifica o distribuzione. Consulta
[`LICENSE`](LICENSE) per i termini completi.
