# I file dei caratteri

La cartella `app/src/main/res/font/` contiene i font scaricati da
[fonts.google.com](https://fonts.google.com):

| File | Da scaricare |
|---|---|
| `archivo_black.ttf` | Archivo Black, Regular |
| `barlow_condensed_semibold.ttf` | Barlow Condensed, SemiBold 600 |
| `barlow_condensed_bold.ttf` | Barlow Condensed, Bold 700 |
| `barlow_regular.ttf` | Barlow, Regular 400 |
| `barlow_medium.ttf` | Barlow, Medium 500 |
| `barlow_semibold.ttf` | Barlow, SemiBold 600 |

I sei file sono versionati nel repository. **Senza di loro il progetto non
compila**: `Tipografia.kt` li referenzia per nome. Le licenze sono riportate in
[`LICENZE-CARATTERI.txt`](../LICENZE-CARATTERI.txt).

Su web gli stessi caratteri arrivano da Google Fonts. Su Android non esiste una
CDN di font: o li imbarchi nell'APK, o il sistema ripiega su Roboto e le due app si
somigliano soltanto nei colori.

I nomi dei file devono essere in minuscolo con trattini bassi: è una regola di
Android, non una preferenza.

In `res/font/` sono ammessi solo file `.xml`, `.ttf`, `.ttc` e `.otf`.
La documentazione deve restare fuori da `res/`, altrimenti il merge delle
risorse Android fallisce.
