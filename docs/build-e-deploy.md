# Come si compila l'app e come va online la web app

Due catene separate, che condividono solo il database. L'app Android si compila
sul tuo computer e produce un APK; la web app si pubblica da GitHub e va online
da sola a ogni push.

---

## Parte 1 — Ottenere l'APK

### Cosa serve installato

| | Cosa | Perché |
|---|---|---|
| 1 | **JDK 17** | Gradle e il compilatore Kotlin girano qui sopra. `java -version` deve dire 17. |
| 2 | **Android SDK** | Contiene le librerie di Android e gli strumenti di firma. |

Per l'SDK hai due strade.

**Android Studio** (consigliato la prima volta). Installalo, apri la cartella
del progetto, e lui scarica da solo SDK, platform-tools e build-tools. Il
pulsante *Run* installa l'app sul telefono collegato.

**Solo riga di comando**, se preferisci non installare l'IDE:

```bash
# scarica i "command line tools" da developer.android.com/studio#command-tools
mkdir -p ~/Android/sdk/cmdline-tools
unzip commandlinetools-*.zip -d ~/Android/sdk/cmdline-tools
mv ~/Android/sdk/cmdline-tools/cmdline-tools ~/Android/sdk/cmdline-tools/latest

export ANDROID_HOME=~/Android/sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

sdkmanager --licenses            # accetta tutto
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Poi, nella cartella del progetto, crea `local.properties`:

```properties
sdk.dir=/home/tuoutente/Android/sdk
```

Quel file è già in `.gitignore` e non va mai committato.

### Il wrapper di Gradle

Il repo non contiene il binario del wrapper (`gradle-wrapper.jar`), perché è un
eseguibile e nei repo si preferisce non metterceli. Lo generi una volta sola:

```bash
# se hai Gradle installato
gradle wrapper --gradle-version 8.12

# se non ce l'hai, Android Studio lo crea da solo alla prima apertura
```

Da quel momento usi sempre `./gradlew`, che scarica la versione giusta per conto
suo e rende il build identico sul tuo portatile e sulla CI.

### Build di sviluppo

```bash
./gradlew :core:teamdraw:test     # i 72 test del motore di sorteggio
./gradlew assembleDebug           # l'APK
```

L'APK esce qui:

```
app/build/outputs/apk/debug/app-debug.apk
```

Installalo sul telefono collegato via USB con il debug ADB attivo:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Questo APK è già distribuibile agli amici: mandalo via Telegram o mettilo su
Drive. Chi lo riceve deve autorizzare una volta l'installazione da origini
sconosciute. È firmato con una chiave di debug automatica: va benissimo per
provare, **non** per il Play Store.

### Build di release firmato

Serve una tua chiave. **Generala una volta sola e non perderla mai**: se la
perdi non puoi più pubblicare aggiornamenti della stessa app sul Play Store, e
l'unica via è ripubblicarla da zero con un altro package name, perdendo gli
utenti.

```bash
keytool -genkeypair -v \
  -keystore callmeup-release.jks \
  -alias callmeup \
  -keyalg RSA -keysize 2048 -validity 10000
```

Tieni il `.jks` **fuori** dal repo (è in `.gitignore`, ma mettilo proprio in
un'altra cartella) e fanne una copia in un posto sicuro. Poi crea
`keystore.properties` nella radice del progetto:

```properties
storeFile=/percorso/assoluto/callmeup-release.jks
storePassword=...
keyAlias=callmeup
keyPassword=...
```

e aggiungi in `app/build.gradle.kts`, dentro il blocco `android { }`:

```kotlin
signingConfigs {
    create("release") {
        val ks = rootProject.file("keystore.properties")
        if (ks.exists()) {
            val p = java.util.Properties().apply { ks.inputStream().use { load(it) } }
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }
}
```

Poi:

```bash
./gradlew assembleRelease    # APK firmato, per distribuzione diretta
./gradlew bundleRelease      # AAB, il formato che vuole il Play Store
```

### Pubblicare sul Play Store

Solo quando vorrai. Tre cose da sapere in anticipo:

- **$25 una tantum** per aprire l'account Play Console, non ricorrenti.
- Un account **personale** aperto dopo il 2023 deve far installare e usare
  l'app da **12 tester per 14 giorni consecutivi** prima di poter passare in
  produzione. I tester puoi reclutarli fra chi già gioca con te.
- Serve un'informativa sulla privacy raggiungibile da URL pubblico, perché
  l'app raccoglie email e foto profilo.

Fra il pagamento e l'app pubblica passano quindi 2–4 settimane, quasi tutte di
attesa. Vale la pena far partire il periodo di test appena l'app è usabile,
mentre continui a svilupparla.

---

## Parte 2 — La web app online

La web app non ha **nessun passaggio di build**: è HTML, CSS e un modulo
JavaScript, con il client Supabase preso dalla CDN. Non c'è niente da
installare e niente da compilare, quindi si pubblica copiando dei file e si
corregge ricaricando la pagina.

È una revisione consapevole del piano iniziale, che diceva Next.js. Per una
manciata di schermate un progetto senza build si mette online in cinque minuti
e non può rompersi in fase di compilazione; il giorno che diventasse stretto, le
query Supabase si portano in Next.js senza toccarle.

### Provarla in locale

```bash
cd web
cp config.example.js config.js     # e riempilo con URL e chiave anon
python3 -m http.server 8000
```

Poi apri `http://localhost:8000`. **Non aprire il file con doppio clic**: i
moduli ES non funzionano da `file://`, serve un server, anche banale come
quello qui sopra.

### Le chiavi

`web/config.js` non è versionato. La chiave `anon` è pubblica per definizione e
vive nel browser: a proteggere i dati sono le policy di Row Level Security, non
la segretezza della chiave. La `service_role` invece non deve mai finire lì.

### Andare online su Vercel

Gratuito, e si aggiorna da solo a ogni push.

1. Vai su vercel.com e accedi con GitHub.
2. *Add New → Project*, scegli il repo `Convocami`.
3. **Root Directory**: `web` — è il passaggio che si dimentica sempre.
4. **Framework Preset**: *Other*. Lascia vuoti i comandi di build: non ce ne sono.
5. Deploy.

Un dettaglio: `config.js` è in `.gitignore`, quindi Vercel non lo trova e la
pagina resta bianca. Due modi per risolverlo, entrambi legittimi visto che la
chiave anon è pubblica:

- togliere `web/config.js` dal `.gitignore` e committarlo (il più semplice);
- oppure caricarlo a mano una volta con `vercel` da riga di comando.

### Due cose da configurare in Supabase

In *Authentication → URL Configuration* aggiungi l'indirizzo Vercel fra i
**Redirect URLs** e come **Site URL**.

In *Authentication → Providers → Email*, tieni **Confirm email spento** finché
non configuri un SMTP tuo: l'email integrata di Supabase manda 2 messaggi
all'ora e solo a indirizzi del tuo team, quindi con la conferma accesa i tuoi
amici non riuscirebbero a registrarsi. Il dettaglio è in
[`primo-avvio.md`](primo-avvio.md).

---

## Il database, che serve a entrambi

Da fare una volta sola, prima di tutto il resto.

1. Su supabase.com crea un progetto (piano gratuito, niente carta).
2. Apri *SQL Editor*, incolla `db/migrations/001_schema.sql`, esegui.
3. Sempre lì, esegui anche `db/migrations/002_ingresso_e_realtime.sql`: contiene
   le funzioni per entrare in un gruppo col codice — senza, chi non è ancora
   membro non riesce nemmeno a trovare il gruppo — e attiva il realtime sulle
   iscrizioni.
4. In *Storage* crea un bucket `avatars`, pubblico in lettura.
5. In *Settings → API* copia `URL` e `anon key`: vanno in `local.properties`
   per Android e in `web/config.js` per il web.

Poi in `local.properties`, accanto a `sdk.dir`:

```properties
supabase.url=https://xxxxx.supabase.co
supabase.anonKey=eyJ...
```

**Attenzione a una cosa**: un progetto Supabase gratuito va in pausa dopo 7
giorni senza traffico e va riattivato a mano dalla dashboard. Giocando ogni
settimana non succede, ma d'estate può capitare di trovare l'app ferma senza
capire perché.

---

## Riepilogo dei comandi

```bash
./gradlew :core:teamdraw:test    # test del motore
./gradlew assembleDebug          # APK per provare
./gradlew assembleRelease        # APK firmato
./gradlew bundleRelease          # AAB per il Play Store
adb install -r <apk>             # installa sul telefono

cd web && npm run dev            # web app in locale
git push                         # e Vercel ripubblica da solo
```
