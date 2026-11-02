# Pampa Sounds

Un fonometro Android per misurare il rumore di una stanza, costruito sul
[Fluid Engine](https://github.com/Casual76/fluid-engine) (3.0.0-rc.2).

## Cosa fa

- **Misura**: livello in dB(A), dB(C) o dB(Z), costanti Fast (125 ms) e Slow (1 s), Leq, minimo,
  massimo, picco, L10/L50/L90, confronto con livelli di riferimento.
- **In background**: la misura continua a schermo spento (servizio in primo piano di tipo
  `microphone`) con il livello in tempo reale nella notifica.
- **Spettro**: curva FFT a 8192 punti su asse logaritmico o bande a terzi d'ottava (IEC 61260),
  picco trattenuto, frequenza dominante con la nota musicale, spettrogramma a cascata.
- **Storico**: ogni misura diventa una sessione (Room) con grafico al secondo, statistiche per
  pesatura, spettro medio, dose NIOSH/OSHA e TWA, export CSV.
- **Calibrazione**: taratura di base dalla CDD Android (UNPROCESSED: 94 dB SPL = −36 dBFS;
  VOICE_RECOGNITION: 90 dB SPL = RMS 2500/32768) più un ritocco, anche contro un fonometro vero.

## Struttura

```
dsp/      Kotlin/JVM puro: FFT, pesature A/C (IEC 61672), Fast/Slow, statistiche, dose,
          bande, calibrazione. Tutto testato sul computer.
app/      dev.pampa.pampasounds: cattura (AudioRecord float), servizio, Room, UI Fluid.
engine/   submodule fluid-engine, agganciato al tag in engine.properties.
```

I livelli viaggiano e si salvano in **dBFS**; la calibrazione è una somma che fa chi legge
(`SessionEntity.offsetDb`), così ricalibrare non lascia mai numeri mezzi vecchi.

## Compilare

Serve `local.properties` con `sdk.dir` (e le chiavi `pampa.*` per una release firmata).
Le cartelle di build stanno in `%LOCALAPPDATA%\PampaSounds\build`, fuori dalla cartella
sincronizzata da Google Drive.

```powershell
powershell -ExecutionPolicy Bypass -File engine\tools\engine-doctor.ps1 -AppRoot .
.\gradlew.bat --no-daemon :dsp:test :app:assembleDebug :app:testDebugUnitTest
```

## Pubblicare

`manifest.json` alla radice è quello che leggono il Pampa Store e l'aggiornamento in-app
(`BuildConfig.MANIFEST_URL` → `Casual76/Pampa-sounds`). La prima release la scrive il publisher
(skill `pampa-store-publish-direct`); il repository GitHub va creato prima.

## Licenza

Pampa Sounds è software libero: si distribuisce sotto la **GNU General Public License, versione 3**
(testo completo in [`LICENSE`](LICENSE)). Chi riceve l'APK ha diritto ai sorgenti, che sono questo
repository: <https://github.com/Casual76/Pampa-sounds>. Si possono leggere, modificare e ridistribuire
alle condizioni della GPL-3.0, con la stessa licenza.

**Perché GPL-3.0.** Dalla 3.0 il Fluid Engine contiene codice di
[LibrePods](https://github.com/librepods-org/librepods) (GPL-3.0): il comportamento dei pulsanti e delle
pagine non è più riscritto, è portato. Un'app che distribuisce l'engine è un'opera derivata, e va
distribuita con la stessa licenza. È la regola di tutte le app Pampa che usano l'engine.

Le opere di altri che l'APK porta con sé (il vetro di Kyant, Apache-2.0; i caratteri Inter, SIL OFL 1.1;
LibrePods) sono elencate nella pagina **Impostazioni › Informazioni** dell'app, con gli avvisi che le
loro licenze chiedono; i testi sono in [`engine/LICENSES/`](engine/LICENSES).
