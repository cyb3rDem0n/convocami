// Convocami — web app.
//
// Volutamente SENZA passaggio di build: ES module, Supabase dalla CDN, CSS a
// mano. Non c'e niente da installare e niente da compilare, quindi si pubblica
// copiando dei file e si corregge ricaricando la pagina. Per una manciata di
// schermate e la scelta giusta; il giorno che diventasse stretta, le query
// Supabase si portano in Next.js senza toccarle.

import { createClient } from "https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2/+esm";
import { SUPABASE_URL, SUPABASE_ANON_KEY } from "./config.js";

const db = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: { detectSessionInUrl: true, persistSession: true, autoRefreshToken: true },
});

const vista = document.getElementById("vista");
const barra = document.getElementById("barra");

let utente = null;      // riga di auth
let profilo = null;     // riga di profiles
let gruppo = null;      // { ruolo, ...groups }
let canale = null;      // sottoscrizione realtime attiva

// ---------------------------------------------------------------------------
// Utilita
// ---------------------------------------------------------------------------

const esc = (s) =>
  String(s ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

let timerAvviso;
function avvisa(testo, male = false) {
  const a = document.getElementById("avviso");
  a.textContent = testo;
  a.classList.toggle("male", male);
  a.hidden = false;
  clearTimeout(timerAvviso);
  timerAvviso = setTimeout(() => (a.hidden = true), male ? 6000 : 3500);
}

/** Gli errori di Postgres arrivano con prefissi che non interessano a nessuno. */
function spiega(e) {
  const m = e?.message || String(e);
  return m.replace(/^.*?violates row-level security.*$/i,
      "Non hai i permessi per farlo.")
    .replace(/^(new row |duplicate key).*/i, "Questa operazione non e consentita.")
    .replace(/\s*\(SQLSTATE.*\)$/i, "");
}

const GIORNI = ["domenica", "lunedi", "martedi", "mercoledi", "giovedi", "venerdi", "sabato"];
const MESI = ["gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
  "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"];

function quando(iso) {
  const d = new Date(iso);
  const ora = String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0");
  return `${GIORNI[d.getDay()]} ${d.getDate()} ${MESI[d.getMonth()]} · ${ora}`;
}

function fraQuanto(iso) {
  const giorni = Math.round((new Date(iso) - Date.now()) / 86400000);
  if (giorni < 0) return "gia giocata";
  if (giorni === 0) return "oggi";
  if (giorni === 1) return "domani";
  if (giorni < 7) return `fra ${giorni} giorni`;
  return `fra ${Math.round(giorni / 7)} settimane`;
}

/** Per gli input datetime-local, che vogliono l'ora locale senza fuso. */
function perInput(d) {
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

function mostra(html) {
  vista.innerHTML = html;
  vista.querySelectorAll("[data-vai]").forEach((b) =>
    b.addEventListener("click", () => (location.hash = b.dataset.vai)));
}

/** Disattiva il bottone mentre l'operazione e in corso: due tocchi = due iscrizioni. */
async function conAttesa(bottone, testoAttesa, fn) {
  const prima = bottone.textContent;
  bottone.disabled = true;
  bottone.textContent = testoAttesa;
  try {
    await fn();
  } catch (e) {
    avvisa(spiega(e), true);
  } finally {
    bottone.disabled = false;
    bottone.textContent = prima;
  }
}

function chiudiCanale() {
  if (canale) { db.removeChannel(canale); canale = null; }
}

// ---------------------------------------------------------------------------
// Accesso
// ---------------------------------------------------------------------------

function schermataAccesso(modo = "entra") {
  barra.hidden = true;
  const registrazione = modo === "registrati";

  mostra(`
    <h1>Convocami</h1>
    <p class="sotto">Organizza il calcetto senza rincorrere nessuno su WhatsApp.</p>

    <form id="form-accesso">
      <label for="email">Email</label>
      <input id="email" type="email" required autocomplete="email"
             inputmode="email" placeholder="nome@esempio.it">

      <label for="password">Password</label>
      <input id="password" type="password" required minlength="8"
             autocomplete="${registrazione ? "new-password" : "current-password"}"
             placeholder="${registrazione ? "almeno 8 caratteri" : ""}">

      ${registrazione ? `
        <label for="nome">Come ti chiamano</label>
        <input id="nome" required placeholder="Giuseppe">` : ""}

      <div class="azioni">
        <button class="primario" type="submit">${registrazione ? "Crea il mio accesso" : "Entra"}</button>
        <button class="testo" type="button" id="cambia-modo">
          ${registrazione ? "Ho gia un accesso" : "E la prima volta che entro"}
        </button>
      </div>
    </form>
  `);

  document.getElementById("cambia-modo").addEventListener("click", () =>
    schermataAccesso(registrazione ? "entra" : "registrati"));

  document.getElementById("form-accesso").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const email = document.getElementById("email").value.trim();
    const password = document.getElementById("password").value;
    const nome = document.getElementById("nome")?.value.trim();

    await conAttesa(ev.target.querySelector("button"), "Un attimo…", async () => {
      if (registrazione) {
        const { error } = await db.auth.signUp({
          email, password, options: { data: { nome } },
        });
        if (error) throw error;
        // con la conferma via email disattivata la sessione arriva subito;
        // se invece e attiva, signUp non autentica e bisogna aprire il link
        const { data } = await db.auth.getSession();
        if (!data?.session) {
          mostra(`
            <h1>Controlla la posta</h1>
            <p class="sotto">Ti ho mandato un link di conferma a
            <strong>${esc(email)}</strong>. Aprilo e poi torna qui.</p>
          `);
        }
      } else {
        const { error } = await db.auth.signInWithPassword({ email, password });
        if (error) {
          throw /invalid login credentials/i.test(error.message)
            ? new Error("Email o password non corrispondono. Se e la prima volta, registrati.")
            : error;
        }
      }
    });
  });
}

// ---------------------------------------------------------------------------
// Primo ingresso: creare un gruppo o entrare con un codice
// ---------------------------------------------------------------------------

function schermataBenvenuto() {
  barra.hidden = false;
  mostra(`
    <h1>Ci siamo quasi</h1>
    <p class="sotto">Un gruppo e la comitiva con cui giochi. Creane uno e invita gli altri,
    oppure entra in quello di qualcun altro col codice che ti ha dato.</p>

    <div class="pannello">
      <h3 style="margin-top:0">Entra con un codice</h3>
      <form id="form-entra">
        <input id="codice" required placeholder="ABC12345" autocapitalize="characters"
               style="font-family:var(--mono); letter-spacing:.1em; text-transform:uppercase">
        <div class="azioni">
          <button class="primario" type="submit">Entra</button>
        </div>
      </form>
    </div>

    <div class="pannello">
      <h3 style="margin-top:0">Oppure creane uno tuo</h3>
      <form id="form-crea">
        <label for="nome-gruppo">Come si chiama</label>
        <input id="nome-gruppo" required placeholder="I soliti del giovedi">
        <label for="campo-gruppo">Campo abituale <span style="text-transform:none">(facoltativo)</span></label>
        <input id="campo-gruppo" placeholder="Centro sportivo Milano Nord">
        <div class="azioni">
          <button class="secondario" type="submit">Crea il gruppo</button>
        </div>
      </form>
    </div>
  `);

  document.getElementById("form-entra").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const codice = document.getElementById("codice").value.trim().toUpperCase();
    await conAttesa(ev.target.querySelector("button"), "Entro…", async () => {
      const { error } = await db.rpc("entra_con_codice", { codice });
      if (error) throw error;
      await caricaGruppo();
      avvisa(`Sei dentro: ${gruppo.nome}`);
      location.hash = "#/gruppo";
      await instrada();
    });
  });

  document.getElementById("form-crea").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    await conAttesa(ev.target.querySelector("button"), "Creo…", async () => {
      const { error } = await db.rpc("crea_gruppo", {
        nome: document.getElementById("nome-gruppo").value.trim(),
        campo: document.getElementById("campo-gruppo").value.trim() || null,
      });
      if (error) throw error;
      await caricaGruppo();
      location.hash = "#/gruppo";
      await instrada();
    });
  });
}

// ---------------------------------------------------------------------------
// Il gruppo: le prossime partite
// ---------------------------------------------------------------------------

async function schermataGruppo() {
  barra.hidden = false;
  chiudiCanale();

  const da = new Date(Date.now() - 6 * 3600 * 1000).toISOString(); // le partite di oggi restano
  const { data: partite, error } = await db
    .from("matches")
    .select("*")
    .eq("group_id", gruppo.id)
    .gte("inizio_at", da)
    .not("stato", "eq", "annullata")
    .order("inizio_at", { ascending: true });

  if (error) { avvisa(spiega(error), true); return; }

  const { data: mie } = await db
    .from("match_signups")
    .select("match_id, stato")
    .eq("profile_id", utente.id)
    .in("match_id", (partite ?? []).map((p) => p.id).concat("00000000-0000-0000-0000-000000000000"));

  const statoMio = Object.fromEntries((mie ?? []).map((r) => [r.match_id, r.stato]));

  const elenco = (partite ?? []).length
    ? partite.map((p) => {
        const s = statoMio[p.id];
        const pill = s === "convocato" ? '<span class="pill ok">ci sei</span>'
          : s === "riserva" ? '<span class="pill attesa">in lista</span>'
          : p.stato === "al_completo" ? '<span class="pill">al completo</span>' : "";
        return `
          <button class="scheda" data-vai="#/partita/${p.id}">
            <span class="quando">${esc(quando(p.inizio_at))} · ${esc(fraQuanto(p.inizio_at))}</span>
            <div class="riga">
              <div>
                <div class="dove">${esc(p.campo)}</div>
                <div class="mini">${p.formato} contro ${p.formato}${p.quota_eur ? ` · ${p.quota_eur}€ a testa` : ""}</div>
              </div>
              ${pill}
            </div>
          </button>`;
      }).join("")
    : `<div class="piatto"><p>Nessuna partita in programma.
       ${gruppo.ruolo === "admin" ? "Aprine una e avvisa gli altri." : "Quando l'organizzatore ne apre una la trovi qui."}</p></div>`;

  mostra(`
    <h1>${esc(gruppo.nome)}</h1>
    <p class="sotto">${esc(gruppo.campo_default || "Prossime partite")}</p>
    ${elenco}
    ${gruppo.ruolo === "admin" ? `<div class="azioni">
      <button class="primario" data-vai="#/nuova">Apri una partita</button></div>` : ""}

    <h2>Invita gli altri</h2>
    <p class="mini">Chi ha questo codice puo entrare nel gruppo.</p>
    <div class="codice">${esc(gruppo.codice_invito)}</div>
    <div class="azioni"><button class="secondario" id="condividi">Condividi il codice</button></div>
  `);

  document.getElementById("condividi").addEventListener("click", async () => {
    const testo = `Entra nel gruppo "${gruppo.nome}" su Convocami col codice ${gruppo.codice_invito}\n${location.origin + location.pathname}`;
    try {
      if (navigator.share) await navigator.share({ text: testo });
      else { await navigator.clipboard.writeText(testo); avvisa("Copiato: incollalo dove vuoi"); }
    } catch { /* l'utente ha annullato: non e un errore */ }
  });
}

// ---------------------------------------------------------------------------
// Aprire una partita
// ---------------------------------------------------------------------------

function schermataNuovaPartita() {
  if (gruppo.ruolo !== "admin") { location.hash = "#/gruppo"; return; }
  barra.hidden = false;

  const fra3 = new Date(Date.now() + 3 * 86400000);
  fra3.setHours(21, 0, 0, 0);

  mostra(`
    <h1>Nuova partita</h1>
    <form id="form-partita">
      <label for="inizio">Quando</label>
      <input id="inizio" type="datetime-local" required value="${perInput(fra3)}">

      <label for="campo">Dove</label>
      <input id="campo" required value="${esc(gruppo.campo_default || "")}" placeholder="Nome del campo">

      <label for="formato">Formato</label>
      <select id="formato">
        <option value="7" ${gruppo.formato_default === 7 ? "selected" : ""}>7 contro 7 — 14 posti</option>
        <option value="8" ${gruppo.formato_default === 8 ? "selected" : ""}>8 contro 8 — 16 posti</option>
      </select>

      <label for="quota">Quota a testa <span style="text-transform:none">(facoltativa)</span></label>
      <input id="quota" type="number" step="0.5" min="0" placeholder="7">

      <div class="azioni">
        <button class="primario" type="submit">Apri le iscrizioni</button>
        <button class="testo" type="button" data-vai="#/gruppo">Annulla</button>
      </div>
    </form>
  `);

  document.getElementById("form-partita").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const formato = Number(document.getElementById("formato").value);
    const quota = document.getElementById("quota").value;
    await conAttesa(ev.target.querySelector("button"), "Apro…", async () => {
      const { data, error } = await db.from("matches").insert({
        group_id: gruppo.id,
        inizio_at: new Date(document.getElementById("inizio").value).toISOString(),
        campo: document.getElementById("campo").value.trim(),
        formato,
        capienza: formato * 2,
        quota_eur: quota === "" ? null : Number(quota),
        created_by: utente.id,
      }).select("id").single();
      if (error) throw error;
      avvisa("Partita aperta");
      location.hash = `#/partita/${data.id}`;
    });
  });
}

// ---------------------------------------------------------------------------
// La partita
// ---------------------------------------------------------------------------

async function schermataPartita(id) {
  barra.hidden = false;
  chiudiCanale();

  const { data: p, error } = await db.from("matches").select("*").eq("id", id).single();
  if (error) { avvisa(spiega(error), true); location.hash = "#/gruppo"; return; }

  await disegnaPartita(p);

  // il contatore deve muoversi da solo: in quattordici sullo stesso gruppo
  // WhatsApp, ricaricare a mano e esattamente cio che rende l'app inutile
  canale = db.channel(`partita-${id}`)
    .on("postgres_changes",
      { event: "*", schema: "public", table: "match_signups", filter: `match_id=eq.${id}` },
      async () => {
        const { data: fresca } = await db.from("matches").select("*").eq("id", id).single();
        if (fresca) await disegnaPartita(fresca);
      })
    .subscribe();
}

async function disegnaPartita(p) {
  const { data: iscritti, error } = await db
    .from("match_signups")
    .select("stato, posto, profile_id")
    .eq("match_id", p.id)
    .neq("stato", "ritirato")
    .order("posto", { ascending: true });

  if (error) { avvisa(spiega(error), true); return; }

  // I nomi si leggono da profili_pubblici e non da profiles: l'email non deve
  // uscire, e Postgres non sa filtrare per colonna dentro una policy — o leggi
  // la riga o non la leggi. Quindi i compagni vedono una vista, e qui si
  // ricuciono i due risultati a mano.
  const { data: persone } = await db
    .from("profili_pubblici")
    .select("id, nome, avatar_url")
    .in("id", (iscritti ?? []).map((r) => r.profile_id));

  const nomi = Object.fromEntries((persone ?? []).map((x) => [x.id, x]));

  const convocati = (iscritti ?? []).filter((r) => r.stato === "convocato");
  const riserve = (iscritti ?? []).filter((r) => r.stato === "riserva");
  const mio = (iscritti ?? []).find((r) => r.profile_id === utente.id);
  const pieno = convocati.length >= p.capienza;
  const percentuale = Math.min(100, Math.round((convocati.length / p.capienza) * 100));

  const riga = (r, i) => `
    <li>
      <span class="numero">${i + 1}</span>
      <span class="chi">
        <span class="nome ${r.profile_id === utente.id ? "io" : ""}">${esc(nomi[r.profile_id]?.nome ?? "—")}</span>
      </span>
    </li>`;

  const azione = mio
    ? `<button class="pericolo" id="agisci">${mio.stato === "riserva" ? "Esci dalla lista d'attesa" : "Non vengo piu"}</button>`
    : pieno
      ? `<button class="secondario" id="agisci">Mettimi in lista d'attesa</button>`
      : `<button class="primario" id="agisci">Ci sono</button>`;

  mostra(`
    <button class="testo" data-vai="#/gruppo">‹ Tutte le partite</button>
    <h1>${esc(p.campo)}</h1>
    <p class="sotto">${esc(quando(p.inizio_at))} · ${esc(fraQuanto(p.inizio_at))}
      ${p.quota_eur ? ` · ${p.quota_eur}€ a testa` : ""}</p>

    <div class="contatore">
      <b>${convocati.length}</b><span>su ${p.capienza} posti${pieno ? " — al completo" : ""}</span>
    </div>
    <div class="barra-posti ${pieno ? "pieno" : ""}"><i style="width:${percentuale}%"></i></div>

    <div class="azioni">${azione}</div>
    ${mio?.stato === "riserva" ? `<p class="mini">Sei il numero ${riserve.findIndex((r) => r.profile_id === utente.id) + 1}
      della lista: se qualcuno si sfila entri tu, e ti avvisiamo.</p>` : ""}

    <h2>Convocati</h2>
    ${convocati.length
      ? `<ul class="persone">${convocati.map(riga).join("")}</ul>`
      : `<p class="mini">Ancora nessuno. Rompi il ghiaccio.</p>`}

    ${riserve.length ? `<h2>Lista d'attesa</h2><ul class="persone">${riserve.map(riga).join("")}</ul>` : ""}

    ${p.stato === "al_completo"
      ? `<div class="piatto"><p><strong>Iscrizioni chiuse.</strong> A tutti i convocati e
         partita una conferma per email con data, ora e campo.</p></div>` : ""}
  `);

  document.getElementById("agisci").addEventListener("click", async (ev) => {
    await conAttesa(ev.target, "Un attimo…", async () => {
      if (mio) {
        const { error: e } = await db.rpc("ritirati", { partita: p.id });
        if (e) throw e;
        avvisa("Va bene, ti abbiamo tolto");
      } else {
        const { data: esito, error: e } = await db.rpc("iscriviti", { partita: p.id });
        if (e) throw e;
        avvisa(esito === "riserva" ? "Sei in lista d'attesa" : "Ci sei");
      }
      const { data: fresca } = await db.from("matches").select("*").eq("id", p.id).single();
      if (fresca) await disegnaPartita(fresca);
    });
  });
}

// ---------------------------------------------------------------------------
// Profilo
// ---------------------------------------------------------------------------

async function schermataProfilo() {
  barra.hidden = false;
  chiudiCanale();

  const { data: ruoli } = await db
    .from("player_roles")
    .select("*")
    .eq("group_id", gruppo.id)
    .eq("profile_id", utente.id)
    .maybeSingle();

  const iniziale = ruoli?.profilo_iniziale ?? "";
  const opzione = (v, t) => `<option value="${v}" ${iniziale === v ? "selected" : ""}>${t}</option>`;

  mostra(`
    <h1>Il tuo profilo</h1>
    <form id="form-profilo">
      <label for="nome">Come ti chiamano</label>
      <input id="nome" required value="${esc(profilo?.nome ?? "")}">

      <label for="ruolo">Dove parti</label>
      <select id="ruolo">
        ${opzione("", "Non saprei, decidete voi")}
        ${opzione("DIF", "Difensore")}
        ${opzione("CEN", "Centrocampista")}
        ${opzione("ATT", "Attaccante")}
        ${opzione("POR", "Porto io i guanti")}
      </select>
      <p class="mini">E solo un punto di partenza: dopo qualche partita l'app capisce da sola
      dove rendi meglio, e i fatti battono le intenzioni. Il portiere invece resta tale
      finche non lo cambia chi organizza.</p>

      <div class="azioni">
        <button class="primario" type="submit">Salva</button>
        <button class="testo" type="button" data-vai="#/gruppo">Torna indietro</button>
      </div>
    </form>
  `);

  document.getElementById("form-profilo").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const ruolo = document.getElementById("ruolo").value || null;
    await conAttesa(ev.target.querySelector("button"), "Salvo…", async () => {
      const { error: e1 } = await db.from("profiles")
        .update({ nome: document.getElementById("nome").value.trim() })
        .eq("id", utente.id);
      if (e1) throw e1;

      // il flag portiere e dell'organizzatore: da qui si tocca solo il profilo
      // di partenza, e il database lo fa rispettare comunque
      const { error: e2 } = await db.from("player_roles")
        .update({ profilo_iniziale: ruolo })
        .eq("group_id", gruppo.id)
        .eq("profile_id", utente.id);
      if (e2) throw e2;

      await caricaProfilo();
      avvisa("Salvato");
      location.hash = "#/gruppo";
    });
  });
}

// ---------------------------------------------------------------------------
// Caricamento dati e instradamento
// ---------------------------------------------------------------------------

async function caricaProfilo() {
  const { data } = await db.from("profiles").select("*").eq("id", utente.id).maybeSingle();
  profilo = data;

  // il trigger crea il profilo con la parte dell'email prima della chiocciola:
  // se in registrazione e stato indicato un nome vero, vince quello
  const scelto = utente.user_metadata?.nome;
  if (scelto && profilo && profilo.nome !== scelto && profilo.nome === utente.email?.split("@")[0]) {
    await db.from("profiles").update({ nome: scelto }).eq("id", utente.id);
    profilo.nome = scelto;
  }
}

async function caricaGruppo() {
  const { data, error } = await db
    .from("group_members")
    .select("ruolo, groups(*)")
    .eq("profile_id", utente.id)
    .eq("attivo", true)
    .order("joined_at", { ascending: true })
    .limit(1);

  if (error) { avvisa(spiega(error), true); gruppo = null; return; }
  const riga = (data ?? [])[0];
  gruppo = riga?.groups ? { ...riga.groups, ruolo: riga.ruolo } : null;
}

async function instrada() {
  if (!utente) { schermataAccesso(); return; }
  if (!gruppo) { schermataBenvenuto(); return; }

  const rotta = location.hash || "#/gruppo";
  const partita = rotta.match(/^#\/partita\/([0-9a-f-]{36})$/i);

  if (partita) await schermataPartita(partita[1]);
  else if (rotta.startsWith("#/nuova")) schermataNuovaPartita();
  else if (rotta.startsWith("#/profilo")) await schermataProfilo();
  else await schermataGruppo();
}

document.getElementById("esci").addEventListener("click", async () => {
  chiudiCanale();
  await db.auth.signOut();
});

document.querySelectorAll("[data-vai]").forEach((b) =>
  b.addEventListener("click", () => (location.hash = b.dataset.vai)));

// Il link magico torna con i token nel frammento: non e una rotta, va ignorato
// e ripulito, altrimenti l'instradamento ci inciampa e resta l'URL sporco.
addEventListener("hashchange", () => {
  if (location.hash.includes("access_token")) return;
  instrada();
});

db.auth.onAuthStateChange(async (_evento, sessione) => {
  utente = sessione?.user ?? null;
  if (utente) {
    if (location.hash.includes("access_token")) {
      history.replaceState(null, "", location.pathname + "#/gruppo");
    }
    await caricaProfilo();
    await caricaGruppo();
  } else {
    profilo = null;
    gruppo = null;
    chiudiCanale();
  }
  await instrada();
});

// Primo avvio: onAuthStateChange scatta comunque, ma se la sessione non c'e
// puo tardare abbastanza da lasciare "Un attimo..." sullo schermo.
(async () => {
  const { data } = await db.auth.getSession();
  if (!data?.session) schermataAccesso();
})();
