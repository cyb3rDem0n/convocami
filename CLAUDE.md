# CallMeUp — istruzioni per chi lavora su questo repo

App per organizzare il calcetto a 7 e a 8 in una comitiva di zona. Iscrizioni
che si chiudono da sole, squadre sorteggiate in modo equo e mai uguale, e un
sistema che impara chi gioca dove dai voti di fine partita.

**Prima di iniziare, leggi [`docs/passaggio-a-code.md`](docs/passaggio-a-code.md).**
Contiene lo stato reale, il caso d'uso completo e il lavoro da fare diviso in
blocchi, ciascuno con il criterio per dirlo finito. Questo file contiene solo le
regole che non devono mai uscire dal contesto.

---

## Regole

**Si scrive in italiano.** Nomi di funzioni, variabili, tabelle, commenti,
messaggi d'errore. `iscriviti`, `convocati`, `propensione`, `turniInPorta`. Non
è un vezzo: il dominio è italiano e tradurlo crea ambiguità — *pitch* è il campo
o il tiro? I termini tecnici senza equivalente restano in inglese.

**`core/teamdraw/` non ha dipendenze e non deve averne.** È Kotlin puro: niente
Android, niente Supabase, niente librerie. Si compila con una JVM e si testa in
un secondo. Serve al web? Si espone come Edge Function, **non si riscrive in
JavaScript**: due implementazioni dello stesso algoritmo divergono sempre, e
questo algoritmo ha già prodotto due difetti sottili che non facevano fallire
niente.

**`web/` non ha passaggio di build.** HTML, CSS e un modulo ES, con Supabase
dalla CDN. Niente npm, niente bundler, niente framework. È una scelta: si
pubblica copiando file, si corregge ricaricando la pagina, e non può rompersi in
compilazione. Non introdurre un build senza una ragione forte.

**La logica che deve essere atomica sta nel database.** Chiusura iscrizioni,
conferme, promozione delle riserve sono trigger Postgres. Se due persone si
iscrivono nello stesso istante al quattordicesimo posto, solo il database può
arbitrare senza ambiguità. Ciò che scavalca le policy è una funzione
`security definer`.

**I vincoli si impongono lato database, mai solo lato client.** Un controllo che
vive nella pagina si aggira con una chiamata diretta a PostgREST.

**Row Level Security su ogni tabella nuova**, con le sue policy scritte nella
stessa migrazione.

**Ogni tabella porta `group_id`.** L'app è multi-gruppo dal primo giorno.

**I client leggono `profili_pubblici`, mai `profiles`.** L'email non deve
uscire, e Postgres non sa filtrare per colonna dentro una policy: o leggi la
riga o non la leggi.

---

## Prima di dire che una cosa funziona

Questo progetto ha una regola che vale più di tutte le altre: **si dice
verificato solo ciò che è stato eseguito.**

Lo schema del database è stato eseguito su un Postgres vero prima di essere
dichiarato buono, e sono usciti due difetti. Il motore di sorteggio ha 63 test
compilati ed eseguiti, e la simulazione ha trovato quattro difetti che non
facevano fallire niente — producevano squadre plausibili e sbagliate. La web app
è l'unica parte mai eseguita contro un database vero, ed è scritto a chiare
lettere nella documentazione.

Quando qualcosa non è stato provato, dillo. Vale più di un'affermazione
ottimistica.

---

## Comandi

```bash
./gradlew :core:teamdraw:test        # 63 test del motore
./gradlew assembleDebug              # APK di debug
cd web && python3 -m http.server 8000
```

Migrazioni in `db/migrations/`, da eseguire in ordine nel SQL Editor di
Supabase.

---

## Trappole

- **Non toccare i pesi della funzione di costo** senza rieseguire
  `Simulazione`: è lì che sono stati trovati i difetti seri.
- **Il flag `portiere` è dell'organizzatore**, protetto da trigger. Chi para,
  para: il ruolo di portiere è fuori dallo spazio delle deduzioni, in entrambe
  le direzioni.
- **`Confirm email` resta spento** finché non c'è un SMTP proprio: quello
  integrato di Supabase manda 2 messaggi all'ora e solo a indirizzi del team.
- **Le tabelle nuove vanno aggiunte alla publication realtime** se il client
  deve vederne i cambiamenti da solo.
- **`web/config.js` è versionato di proposito.** La chiave anon è pubblica per
  definizione; ignorarla faceva restare bianca la pagina su Vercel.
- **Il progetto Supabase gratuito va in pausa dopo 7 giorni senza traffico.**

---

## Commit

Messaggi in italiano. Il titolo dice cosa cambia, il corpo dice **perché** —
soprattutto quando la scelta fatta non è quella ovvia. La storia di questo repo
è documentazione: serve a capire fra sei mesi perché una cosa è fatta così e non
nel modo che verrebbe in mente per primo.
