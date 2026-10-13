package dev.pampa.pampasounds.dsp

import kotlin.math.log10
import kotlin.math.pow

/**
 * Il pavimento di ogni livello calcolato qui, in dBFS.
 *
 * Il silenzio digitale vero e' meno infinito, e un meno infinito che entra in un istogramma, in una
 * media o in un grafico rompe tutto quello che tocca. Centosessanta decibel sotto il fondo scala
 * sono gia' ben sotto il rumore di qualsiasi microfono esistente.
 */
const val SILENCE_DBFS: Double = -160.0

/**
 * Sotto questa soglia il segnale non e' il silenzio di una stanza: e' un microfono che manda zeri
 * (un emulatore, un'altra app che se l'e' preso). Anche il miglior microfono MEMS ha un rumore
 * proprio attorno a −100 dBFS; −150 non lo raggiunge niente di vero.
 */
const val DIGITAL_SILENCE_DBFS: Double = -150.0

/** dBFS → dB SPL con l'offset di calibrazione, o NaN per il silenzio digitale (non e' una lettura). */
fun dbfsToSpl(dbfs: Double, offsetDb: Double): Double =
  if (dbfs.isNaN() || dbfs <= DIGITAL_SILENCE_DBFS) Double.NaN else dbfs + offsetDb

fun dbfsToSpl(dbfs: Float, offsetDb: Double): Float =
  if (dbfs.isNaN() || dbfs <= DIGITAL_SILENCE_DBFS) Float.NaN else (dbfs + offsetDb).toFloat()

/** Valore quadratico medio → dB (10·log10), con il pavimento [SILENCE_DBFS]. */
fun powerToDb(meanSquare: Double): Double =
  if (meanSquare <= 1e-16) SILENCE_DBFS else 10.0 * log10(meanSquare)

/** dB → valore quadratico medio. */
fun dbToPower(db: Double): Double = 10.0.pow(db / 10.0)

/** Ampiezza (picco o RMS) → dB (20·log10), con lo stesso pavimento. */
fun amplitudeToDb(amplitude: Double): Double =
  if (amplitude <= 1e-8) SILENCE_DBFS else 20.0 * log10(amplitude)

/**
 * La media che ha senso per il suono: quella dell'energia, non dei numeri.
 *
 * 60 dB e 80 dB non fanno 70 di media: fanno 77, perche' 80 dB porta cento volte l'energia di 60.
 * E' la definizione del Leq, ed e' la ragione per cui un rumore breve e forte pesa cosi' tanto.
 */
fun energeticMean(levelsDb: DoubleArray): Double {
  if (levelsDb.isEmpty()) return SILENCE_DBFS
  var sum = 0.0
  for (level in levelsDb) sum += dbToPower(level)
  return powerToDb(sum / levelsDb.size)
}

/**
 * Le due costanti di tempo di un fonometro (IEC 61672).
 *
 * Fast segue la voce e i colpi, Slow legge un rumore che oscilla senza far ballare la cifra. Sono
 * integratori esponenziali sul quadrato del segnale: [tauSeconds] e' il tempo in cui la memoria di
 * un campione scende a 1/e.
 */
enum class TimeWeighting(val tauSeconds: Double) {
  Fast(0.125),
  Slow(1.0),
}
