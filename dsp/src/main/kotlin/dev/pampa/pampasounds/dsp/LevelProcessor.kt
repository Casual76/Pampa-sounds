package dev.pampa.pampasounds.dsp

import kotlin.math.abs
import kotlin.math.exp

/** Quante letture al secondo arrivano alla UI: otto, il passo di una costante Fast. */
const val TICKS_PER_SECOND: Int = 8

private val WEIGHTINGS = FrequencyWeighting.entries
private val TIME_WEIGHTINGS = TimeWeighting.entries

/**
 * Un ottavo di secondo di misura. Gli array sono indicizzati per `FrequencyWeighting.ordinal`
 * (A, C, Z): tutte e tre le pesature si calcolano sempre, cosi' cambiarla non azzera niente.
 */
class LevelTick(
  val elapsedSeconds: Double,
  val fastDb: DoubleArray,
  val slowDb: DoubleArray,
  /** Leq di questo ottavo di secondo. */
  val eqDb: DoubleArray,
  /** Picco non pesato (Z) del segnale, in dB rispetto al fondo scala. */
  val peakDb: Double,
)

/** Un secondo intero: e' la riga che finisce nel database e nel grafico dello storico. */
class LevelSecond(
  val index: Int,
  val eqDb: DoubleArray,
  /** Massimo e minimo Fast nel secondo, NaN finche' gli integratori si stanno assestando. */
  val fastMaxDb: DoubleArray,
  val fastMinDb: DoubleArray,
  val peakDb: Double,
)

/** Il riassunto di tutta la misura fino a qui. Tutto in dBFS, NaN dove non c'e' ancora un dato. */
class LevelSummary(
  val durationSeconds: Double,
  val leqDb: DoubleArray,
  /** `[pesatura][costante di tempo]` */
  val maxDb: Array<DoubleArray>,
  val minDb: Array<DoubleArray>,
  val l10Db: DoubleArray,
  val l50Db: DoubleArray,
  val l90Db: DoubleArray,
  val peakDb: Double,
)

/**
 * Il cuore del fonometro: riceve campioni grezzi e restituisce livelli.
 *
 * Per ogni campione: le tre pesature (A e C filtrate, Z cosi' com'e'), i due integratori Fast e Slow
 * per ciascuna, la somma dei quadrati per i Leq, il picco. Ogni ottavo di secondo una [LevelTick],
 * ogni secondo una [LevelSecond]. Tutto in dBFS: la calibrazione e' una somma che fa chi legge.
 *
 * Il primo [warmupSeconds] non entra in minimi, massimi e percentili: gli integratori partono da
 * zero (−160 dB) e Slow impiega un secondo ad arrivare al livello vero; contarlo darebbe a ogni
 * sessione un minimo assurdo. Non e' thread-safe: vive tutto sul thread audio.
 */
class LevelProcessor(
  val sampleRate: Int,
  warmupSeconds: Double = 1.0,
) {
  interface Listener {
    fun onTick(tick: LevelTick)
    fun onSecond(second: LevelSecond)
  }

  private val weightingCount = WEIGHTINGS.size
  private val filters = Array(weightingCount) { WeightingFilter.create(WEIGHTINGS[it], sampleRate) }
  private val alpha = DoubleArray(TIME_WEIGHTINGS.size) {
    1.0 - exp(-1.0 / (TIME_WEIGHTINGS[it].tauSeconds * sampleRate))
  }
  private val integrators = Array(weightingCount) { DoubleArray(TIME_WEIGHTINGS.size) }
  private val warmupSamples = (warmupSeconds * sampleRate).toLong()

  private val tickSum = DoubleArray(weightingCount)
  private var tickSamples = 0
  private var tickPeak = 0.0

  private val secondSum = DoubleArray(weightingCount)
  private val secondFastMax = DoubleArray(weightingCount) { Double.NEGATIVE_INFINITY }
  private val secondFastMin = DoubleArray(weightingCount) { Double.POSITIVE_INFINITY }
  private var secondPeak = 0.0
  private var samplesInSecond = 0
  private var nextTick = 1
  private var secondIndex = 0

  private val sessionSum = DoubleArray(weightingCount)
  private var sessionSamples = 0L
  private var sessionPeak = 0.0
  private val maxLevel = Array(weightingCount) { DoubleArray(TIME_WEIGHTINGS.size) { Double.NEGATIVE_INFINITY } }
  private val minLevel = Array(weightingCount) { DoubleArray(TIME_WEIGHTINGS.size) { Double.POSITIVE_INFINITY } }

  /** Livelli Fast campionati a ogni tick: da qui L10, L50, L90. */
  private val fastHistograms = Array(weightingCount) { LevelHistogram() }

  /** Leq di ogni secondo: da qui la dose (sulla A). */
  val secondHistograms: Array<LevelHistogram> = Array(weightingCount) { LevelHistogram() }

  val elapsedSeconds: Double get() = sessionSamples.toDouble() / sampleRate

  fun process(samples: FloatArray, count: Int, listener: Listener) {
    for (i in 0 until count) {
      val x = samples[i].toDouble()
      val magnitude = abs(x)
      if (magnitude > tickPeak) tickPeak = magnitude
      for (w in 0 until weightingCount) {
        val y = filters[w]?.process(x) ?: x
        val y2 = y * y
        val integrator = integrators[w]
        integrator[0] += (y2 - integrator[0]) * alpha[0]
        integrator[1] += (y2 - integrator[1]) * alpha[1]
        tickSum[w] += y2
      }
      tickSamples++
      samplesInSecond++
      sessionSamples++
      if (samplesInSecond >= tickBoundary(nextTick)) closeTick(listener)
    }
  }

  fun summary(): LevelSummary {
    fun finite(value: Double) = if (value.isInfinite()) Double.NaN else value
    return LevelSummary(
      durationSeconds = elapsedSeconds,
      leqDb = DoubleArray(weightingCount) {
        if (sessionSamples == 0L) Double.NaN else powerToDb(sessionSum[it] / sessionSamples)
      },
      maxDb = Array(weightingCount) { w -> DoubleArray(TIME_WEIGHTINGS.size) { finite(maxLevel[w][it]) } },
      minDb = Array(weightingCount) { w -> DoubleArray(TIME_WEIGHTINGS.size) { finite(minLevel[w][it]) } },
      l10Db = DoubleArray(weightingCount) { fastHistograms[it].exceeded(10.0) ?: Double.NaN },
      l50Db = DoubleArray(weightingCount) { fastHistograms[it].exceeded(50.0) ?: Double.NaN },
      l90Db = DoubleArray(weightingCount) { fastHistograms[it].exceeded(90.0) ?: Double.NaN },
      peakDb = if (sessionPeak > 0.0) amplitudeToDb(sessionPeak) else Double.NaN,
    )
  }

  private fun tickBoundary(tick: Int): Int = (tick.toLong() * sampleRate / TICKS_PER_SECOND).toInt()

  private fun closeTick(listener: Listener) {
    val warmedUp = sessionSamples > warmupSamples
    if (sessionSamples == tickSamples.toLong()) {
      // Il primo tick pre-carica gli integratori con il livello che ha appena misurato: partendo da
      // zero, Slow impiegherebbe tre secondi a salire da −160 dB al livello vero (dopo 3τ e' ancora
      // a −0,2 dB), e chi apre l'app vedrebbe la cifra arrampicarsi invece di leggere la stanza.
      for (w in 0 until weightingCount) integrators[w].fill(tickSum[w] / tickSamples)
    }
    val fast = DoubleArray(weightingCount) { powerToDb(integrators[it][0]) }
    val slow = DoubleArray(weightingCount) { powerToDb(integrators[it][1]) }
    val eq = DoubleArray(weightingCount) { powerToDb(tickSum[it] / tickSamples) }
    for (w in 0 until weightingCount) {
      secondSum[w] += tickSum[w]
      sessionSum[w] += tickSum[w]
      tickSum[w] = 0.0
      if (warmedUp) {
        val levels = doubleArrayOf(fast[w], slow[w])
        for (t in levels.indices) {
          if (levels[t] > maxLevel[w][t]) maxLevel[w][t] = levels[t]
          if (levels[t] < minLevel[w][t]) minLevel[w][t] = levels[t]
        }
        if (fast[w] > secondFastMax[w]) secondFastMax[w] = fast[w]
        if (fast[w] < secondFastMin[w]) secondFastMin[w] = fast[w]
        fastHistograms[w].add(fast[w])
      }
    }
    if (tickPeak > secondPeak) secondPeak = tickPeak
    // Il clic d'apertura del microfono non e' il picco della stanza.
    if (warmedUp && tickPeak > sessionPeak) sessionPeak = tickPeak
    listener.onTick(LevelTick(elapsedSeconds, fast, slow, eq, amplitudeToDb(tickPeak)))
    tickPeak = 0.0
    tickSamples = 0
    if (nextTick == TICKS_PER_SECOND) {
      closeSecond(listener)
      samplesInSecond = 0
      nextTick = 1
    } else {
      nextTick++
    }
  }

  private fun closeSecond(listener: Listener) {
    val eq = DoubleArray(weightingCount) { powerToDb(secondSum[it] / sampleRate) }
    for (w in 0 until weightingCount) secondHistograms[w].add(eq[w])
    val second = LevelSecond(
      index = secondIndex++,
      eqDb = eq,
      fastMaxDb = DoubleArray(weightingCount) { if (secondFastMax[it].isInfinite()) Double.NaN else secondFastMax[it] },
      fastMinDb = DoubleArray(weightingCount) { if (secondFastMin[it].isInfinite()) Double.NaN else secondFastMin[it] },
      peakDb = amplitudeToDb(secondPeak),
    )
    secondSum.fill(0.0)
    secondFastMax.fill(Double.NEGATIVE_INFINITY)
    secondFastMin.fill(Double.POSITIVE_INFINITY)
    secondPeak = 0.0
    listener.onSecond(second)
  }
}
