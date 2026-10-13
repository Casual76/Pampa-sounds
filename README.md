# Pampa Sounds

Un fonometro Android per misurare il rumore di una stanza, costruito sul
[Fluid Engine](https://github.com/Casual76/fluid-engine) (2.11.0).

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
