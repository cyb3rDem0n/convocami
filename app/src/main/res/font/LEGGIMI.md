# I file dei caratteri

Questa cartella deve contenere i font veri, scaricati da
[fonts.google.com](https://fonts.google.com):

| File | Da scaricare |
|---|---|
| `archivo_black.ttf` | Archivo Black, Regular |
| `barlow_condensed_semibold.ttf` | Barlow Condensed, SemiBold 600 |
| `barlow_condensed_bold.ttf` | Barlow Condensed, Bold 700 |
| `barlow_regular.ttf` | Barlow, Regular 400 |
| `barlow_medium.ttf` | Barlow, Medium 500 |
| `barlow_semibold.ttf` | Barlow, SemiBold 600 |

Non sono nel repo perché sono binari e si scaricano in trenta secondi. **Senza
di loro il progetto non compila**: `Tipografia.kt` li referenzia per nome.

Su web gli stessi caratteri arrivano da Google Fonts. Su Android non esiste una
CDN di font: o li imbarchi qui, o il sistema ripiega su Roboto e le due app si
somigliano soltanto nei colori.

I nomi dei file devono essere in minuscolo con trattini bassi: è una regola di
Android, non una preferenza.
