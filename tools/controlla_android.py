#!/usr/bin/env python3
"""Controlli statici sul modulo Android, senza bisogno di Gradle ne dell'SDK.

Esiste perche' questo progetto e' stato scritto in un ambiente dove il modulo
Android non si puo' compilare, e ogni errore banale veniva scoperto dalla CI
un push alla volta: il "--" in un commento XML, un LEGGIMI.md dentro res/,
un'icona referenziata dal manifest ma inesistente, i font referenziati da
Tipografia.kt e assenti. Tutte cose che si vedono guardando i file, se
qualcuno guarda con le stesse regole di aapt.

Non sostituisce il build: e' la rete sotto il trapezio, per chi lavora dove il
build non c'e'. Uso:

    python3 tools/controlla_android.py        # esce 0 se pulito, 1 se no
"""

import re
import sys
import xml.parsers.expat
from pathlib import Path

RADICE = Path(__file__).resolve().parent.parent
SRC = RADICE / "app" / "src" / "main"
RES = SRC / "res"

errori: list[str] = []
def errore(m: str) -> None: errori.append(m)

# ---------------------------------------------------------------------------
# 1. Ogni XML deve essere ben formato. Expat applica le stesse regole di aapt,
#    compreso il divieto di "--" dentro i commenti.
# ---------------------------------------------------------------------------
for f in sorted(SRC.rglob("*.xml")):
    p = xml.parsers.expat.ParserCreate()
    try:
        p.Parse(f.read_bytes(), True)
    except xml.parsers.expat.ExpatError as e:
        errore(f"{f.relative_to(RADICE)}: XML non valido — {e}")

# ---------------------------------------------------------------------------
# 2. Dentro res/ valgono le regole di aapt: solo certi tipi di file per
#    cartella, e nomi in minuscolo con trattini bassi. Un file di appunti
#    lasciato li' dentro ferma il build di chiunque.
# ---------------------------------------------------------------------------
AMMESSI = {
    "values":   {".xml"},
    "font":     {".ttf", ".otf", ".ttc", ".xml"},
    "mipmap":   {".png", ".webp", ".xml"},
    "drawable": {".png", ".webp", ".xml", ".jpg", ".gif"},
    "raw":      None,          # raw accetta qualunque cosa
}
if RES.is_dir():
    for f in sorted(RES.rglob("*")):
        if not f.is_file():
            continue
        tipo = f.parent.name.split("-")[0]
        ammessi = AMMESSI.get(tipo)
        if ammessi is None and tipo in AMMESSI:
            pass
        elif ammessi is None:
            errore(f"{f.relative_to(RADICE)}: cartella res/{f.parent.name} di tipo sconosciuto")
        elif f.suffix.lower() not in ammessi:
            errore(f"{f.relative_to(RADICE)}: in res/{tipo}*/ sono ammessi solo {sorted(ammessi)}")
        if not re.fullmatch(r"[a-z0-9_.]+", f.name):
            errore(f"{f.relative_to(RADICE)}: nome non valido per res/ (solo minuscole, cifre, _ e .)")

# ---------------------------------------------------------------------------
# 3. Le risorse referenziate devono esistere. E' il controllo che avrebbe
#    fermato @mipmap/ic_launcher e R.font.archivo_black prima della CI.
# ---------------------------------------------------------------------------
definite: dict[str, set[str]] = {"color": set(), "string": set(), "style": set(),
                                 "font": set(), "mipmap": set(), "drawable": set()}
for f in RES.glob("values*/*.xml"):
    testo = f.read_text(encoding="utf-8")
    for tipo in ("color", "string", "style"):
        for m in re.finditer(rf'<{tipo}[^>]*\sname="([^"]+)"', testo):
            definite[tipo].add(m.group(1))
for tipo in ("font", "mipmap", "drawable"):
    for cart in RES.glob(f"{tipo}*"):
        for f in cart.iterdir():
            if f.is_file():
                definite[tipo].add(f.stem)

usi: list[tuple[str, str, str]] = []      # (tipo, nome, dove)
for f in list(SRC.rglob("*.xml")):
    for m in re.finditer(r'@(color|string|style|font|mipmap|drawable)/([\w.]+)', f.read_text(encoding="utf-8")):
        usi.append((m.group(1), m.group(2), str(f.relative_to(RADICE))))
for f in SRC.rglob("*.kt"):
    for m in re.finditer(r'\bR\.(color|string|style|font|mipmap|drawable)\.(\w+)', f.read_text(encoding="utf-8")):
        usi.append((m.group(1), m.group(2), str(f.relative_to(RADICE))))

for tipo, nome, dove in usi:
    if nome not in definite[tipo]:
        errore(f"{dove}: riferimento a @{tipo}/{nome} che non esiste in res/")

# Gli stili con il punto ereditano: "Theme.Convocami" e' definito cosi com'e'.
# (Gia' coperto: il name in values e' letterale.)

# ---------------------------------------------------------------------------
# 4. Le classi nominate dal manifest devono esistere come file Kotlin.
# ---------------------------------------------------------------------------
manifest = SRC / "AndroidManifest.xml"
if manifest.exists():
    testo = manifest.read_text(encoding="utf-8")
    sorgenti = {f.stem for f in SRC.rglob("*.kt")}
    for m in re.finditer(r'android:name="\.(\w+)"', testo):
        if m.group(1) not in sorgenti:
            errore(f"AndroidManifest.xml: la classe .{m.group(1)} non ha un file sorgente")

# ---------------------------------------------------------------------------
# 5. Contratto minimo del prototipo visuale provato su dispositivo.
#
# Un Box, a differenza di Surface, non imposta LocalContentColor: togliere il
# provider fa tornare neri tutti i Text senza un colore esplicito. Anche logo e
# CTA sono gia' regrediti in azioni mute durante le prime prove dell'APK. Questi
# controlli non giudicano il layout, ma impediscono di perdere di nuovo le tre
# correzioni osservabili prima ancora di costruire l'app.
# ---------------------------------------------------------------------------
interfaccia = SRC / "kotlin" / "it" / "cyb3rdm0n" / "convocami" / "ui" / "Convocami.kt"
if interfaccia.exists():
    testo = interfaccia.read_text(encoding="utf-8")
    contratti = {
        "CompositionLocalProvider(LocalContentColor provides Gesso)":
            "manca il colore chiaro predefinito: i testi sul fondo scuro tornano neri",
        "painterResource(R.mipmap.ic_launcher_foreground)":
            "il marchio dell'app non viene mostrato nella schermata di accesso",
        'AzionePrimaria("APRI UNA PARTITA", nuovaPartita)':
            "la CTA Apri una partita non e' collegata alla navigazione",
        "Destinazione.NuovaPartita -> NuovaPartita(":
            "manca la destinazione del modulo nuova partita",
    }
    for frammento, messaggio in contratti.items():
        if frammento not in testo:
            errore(f"{interfaccia.relative_to(RADICE)}: {messaggio}")
else:
    errore(f"{interfaccia.relative_to(RADICE)}: interfaccia Compose assente")

# ---------------------------------------------------------------------------
# Esito
# ---------------------------------------------------------------------------
if errori:
    for e in errori:
        print(f"NO  {e}")
    print(f"\n{len(errori)} problemi: il build di Android si fermera' li'.")
    sys.exit(1)
print("Tutti i controlli statici del modulo Android passano.")
