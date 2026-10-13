package dev.pampa.pampasounds.audio

import dev.pampa.pampasounds.dsp.FrequencyWeighting
import dev.pampa.pampasounds.dsp.LevelSecond

/** Una serie di secondi per una pesatura, in dBFS. NaN dove il dato non c'e'. */
class TraceSeries(val eq: FloatArray, val max: FloatArray, val min: FloatArray) {
  val size: Int get() = eq.size
}

/**
 * I secondi della misura in corso, in memoria, per il grafico dello storico dal vivo.
 *
 * Nove serie di float (Leq, max, min per A, C, Z): otto ore sono un megabyte. Il thread audio
 * aggiunge, la UI copia: le copie costano meno di qualsiasi schema senza lock, e succedono una volta
 * al secondo.
 */
class LiveTrace {
  private val lock = Any()
  private val weightings = FrequencyWeighting.entries.size
  private var eq = Array(weightings) { FloatArray(INITIAL_CAPACITY) }
  private var max = Array(weightings) { FloatArray(INITIAL_CAPACITY) }
  private var min = Array(weightings) { FloatArray(INITIAL_CAPACITY) }
  private var size = 0

  fun append(second: LevelSecond) = synchronized(lock) {
    if (size == eq[0].size) grow()
    for (w in 0 until weightings) {
      eq[w][size] = second.eqDb[w].toFloat()
      max[w][size] = second.fastMaxDb[w].toFloat()
      min[w][size] = second.fastMinDb[w].toFloat()
    }
    size++
  }

  fun clear() = synchronized(lock) { size = 0 }

  fun snapshot(weighting: FrequencyWeighting): TraceSeries = synchronized(lock) {
    val w = weighting.ordinal
    TraceSeries(eq[w].copyOf(size), max[w].copyOf(size), min[w].copyOf(size))
  }

  private fun grow() {
    val capacity = eq[0].size * 2
    eq = Array(weightings) { eq[it].copyOf(capacity) }
    max = Array(weightings) { max[it].copyOf(capacity) }
    min = Array(weightings) { min[it].copyOf(capacity) }
  }

  private companion object {
    const val INITIAL_CAPACITY = 3_600
  }
}
