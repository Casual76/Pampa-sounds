package dev.pampa.pampasounds.dsp

import kotlin.math.log10

/** Da quale percorso del telefono arriva il segnale. Cambia quanto e' "alto" il fondo scala. */
enum class CaptureSource {
  /** Microfono senza elaborazioni (Android 7+, se il telefono lo dichiara): il percorso giusto. */
  Unprocessed,

  /** Il percorso del riconoscimento vocale: per specifica senza controllo automatico del guadagno. */
  VoiceRecognition,

  /** Il microfono generico: puo' avere AGC e filtri, ultima scelta. */
  Mic,
}

/**
 * Da dBFS a dB SPL.
 *
 * Un microfono digitale non sa quanti pascal arrivano: sa solo che frazione del fondo scala vede. La
 * conversione e' un numero che si somma, e la sua base viene dalla Compatibility Definition di
 * Android, che fissa la sensibilita' dei percorsi audio:
 *
 * - **UNPROCESSED** (CDD 5.11): un tono a 1 kHz e 94 dB SPL deve leggere −36 dBFS → base 130 dB.
 * - **VOICE_RECOGNITION** (CDD 5.4): 90 dB SPL devono leggere un RMS di 2500 su 16 bit
 *   (−22,35 dBFS) → base 112,35 dB. Per MIC la norma non dice niente, e si usa la stessa.
 *
 * Sono requisiti "SHOULD": ogni telefono sbaglia di qualche decibel, ed e' per questo che sopra la
 * base c'e' il ritocco dell'utente ([trimDb]), che si tara una volta contro un fonometro vero.
 */
object Calibration {
  const val UNPROCESSED_BASE_DB: Double = 94.0 + 36.0
  val VOICE_RECOGNITION_BASE_DB: Double = 90.0 - 20.0 * log10(2500.0 / 32768.0)

  /** Il ritocco massimo accettato, in entrambe le direzioni. */
  const val TRIM_RANGE_DB: Double = 30.0

  fun baseOffsetDb(source: CaptureSource): Double = when (source) {
    CaptureSource.Unprocessed -> UNPROCESSED_BASE_DB
    CaptureSource.VoiceRecognition, CaptureSource.Mic -> VOICE_RECOGNITION_BASE_DB
  }

  fun offsetDb(source: CaptureSource, trimDb: Double): Double = baseOffsetDb(source) + trimDb

  /**
   * Il ritocco che fa leggere [referenceSplDb] (il valore di un fonometro vero, nello stesso istante)
   * a un'app che sta leggendo [measuredSplDb] con il ritocco [currentTrimDb].
   */
  fun trimForReference(currentTrimDb: Double, measuredSplDb: Double, referenceSplDb: Double): Double =
    (currentTrimDb + referenceSplDb - measuredSplDb).coerceIn(-TRIM_RANGE_DB, TRIM_RANGE_DB)
}
