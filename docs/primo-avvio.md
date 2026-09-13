# Primo avvio

Cosa serve davvero per avere la web app in mano e farla provare agli altri.

**Non serve** l'ambiente Android: quello entra in gioco più avanti, e comunque
si risolve installando Android Studio una volta sola.

**Non serve** un server virtuale, né Docker, né nginx. La web app è fatta di tre
file statici: il "server" per provarla in locale è un comando, e per metterla
online non serve nemmeno quello.

---

## 1. Il database — 5 minuti

Su Supabase, *SQL Editor*:

1. `db/migrations/001_schema.sql` — se l'hai già eseguito, salta.
2. `db/migrations/002_ingresso_e_realtime.sql` — **questo serve**. Senza, chi
   riceve un codice d'invito non riesce a entrare nel gruppo, e il contatore
   delle iscrizioni non si aggiorna da solo.
3. `db/migrations/003_email_privata.sql` — chiude la lettura dell'email altrui.
   Va eseguito insieme al resto: il client legge già dalla vista che crea.

Poi in *Storage* crea un bucket `avatars`, pubblico in lettura. Serve dalla
prossima fase, ma tanto vale farlo adesso.

## 2. L'accesso — 2 minuti, ed è il passaggio che si sbaglia

In *Authentication → Providers → Email*:

- **Enable email provider**: acceso.
- **Confirm email**: **spento**.

Quel secondo interruttore è tutto. L'email integrata di Supabase manda **2
messaggi all'ora e soltanto a indirizzi che fanno parte del tuo team**: ai tuoi
amici risponderebbe *"Email address not authorized"* e il test finirebbe prima
di cominciare. Con la conferma spenta non parte nessuna email e chi si registra
entra subito.

Va benissimo per una comitiva chiusa che si conosce. Prima di aprire l'app a
gente che non conosci, configuri un SMTP vero (Brevo o Resend, gratuiti) e
riaccendi la conferma — il momento giusto è la fase 6, insieme alle notifiche.

In *Authentication → URL Configuration*:

- **Site URL**: `http://localhost:8000` per ora.
- **Redirect URLs**: aggiungi sia `http://localhost:8000` sia l'indirizzo
  Vercel, appena ce l'hai.

## 3. Le chiavi — 1 minuto

In *Settings → API* copia `URL` e `anon key`, poi:

```bash
cd web
cp config.example.js config.js
```

e incollale dentro. La chiave `anon` è pubblica per definizione e vive nel
browser: a proteggere i dati sono le policy di Row Level Security. La
`service_role` non deve mai finire lì.

## 4. Provala in locale — 1 comando

```bash
cd web
python3 -m http.server 8000
```

Apri `http://localhost:8000`.

**Non aprire il file con doppio clic**: i moduli ES non funzionano da `file://`,
serve un server — ma "server" qui vuol dire quella riga, niente di più. Se
python non ce l'hai: `npx serve` oppure `php -S localhost:8000` fanno lo stesso.

Il giro completo da provare: registrati → crea un gruppo → apri una partita →
iscriviti. Se vuoi vedere la lista d'attesa senza tredici amici, apri una
seconda finestra in incognito e registrati con un'altra email.

## 5. Mettila online — 5 minuti

Solo quando in locale funziona.

1. Crea il repo su GitHub e fai push.
2. Su vercel.com, accedi con GitHub, *Add New → Project*, scegli `Convocami`.
3. **Root Directory**: `web` — è il passaggio che si dimentica sempre.
4. **Framework Preset**: *Other*. Nessun comando di build: non ce n'è.
5. Deploy.

Ottieni un indirizzo tipo `convocami.vercel.app`. Torna in Supabase e mettilo
come **Site URL** e fra i **Redirect URLs**. Da quel momento mandi il link agli
amici e ogni push ripubblica da solo.

`web/config.js` è versionato apposta: se fosse ignorato, Vercel non lo
troverebbe e la pagina resterebbe bianca.

---

## Quando servirà l'ambiente Android

Non adesso. Quando ci arriveremo basta **Android Studio** — installa da solo
SDK, platform-tools e build-tools, e il pulsante *Run* mette l'app sul telefono
collegato. Da riga di comando è `./gradlew assembleDebug`.

I dettagli, inclusa la firma per il Play Store, sono in
[`build-e-deploy.md`](build-e-deploy.md).

## Se qualcosa non va

Apri la console del browser (F12) e guarda l'errore: con un'app senza passaggio
di build, quello che vedi lì è esattamente il codice che ho scritto, riga per
riga. Mandamelo così com'è.

I due errori più probabili al primo giro:

- **"Email address not authorized"** → *Confirm email* è ancora acceso (punto 2).
- **pagina bianca, console piena di 404** → `config.js` non c'è, o su Vercel la
  Root Directory non è `web`.
