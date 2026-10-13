package dev.pampa.pampasounds.dsp

import kotlin.math.roundToInt

/**
 * Quante volte e' comparso ogni livello, a passi di un decimo di decibel.
 *
 * E' il modo di avere L10/L50/L90 e la dose di una registrazione di otto ore in memoria costante:
 * millesettecento contatori invece di centinaia di migliaia di valori. Un decimo di decibel e' gia'
 * sotto quello che un fonometro di classe 1 sa distinguere.
 */
class LevelHistogram(
  val minDb: Double = SILENCE_DBFS,
  val maxDb: Double = 20.0,
  val stepDb: Double = 0.1,
) {
  private val counts = LongArray(((maxDb - minDb) / stepDb).roundToInt() + 1)

  /** Quanti valori sono entrati. */
  var total: Long = 0L
    private set

  fun add(levelDb: Double) {
    if (levelDb.isNaN()) return
    counts[indexOf(levelDb)]++
    total++
  }

  fun reset() {
    counts.fill(0L)
    total = 0L
  }

  /**
   * Il livello superato per [percent] % del tempo: `exceeded(10.0)` e' L10 (il rumore dei picchi
   * ricorrenti), `exceeded(90.0)` e' L90 (il fondo, quello che resta quando non succede niente).
   */
  fun exceeded(percent: Double): Double? {
    if (total == 0L) return null
    val target = total * (percent / 100.0)
    var cumulative = 0L
    for (index in counts.indices.reversed()) {
      cumulative += counts[index]
      if (cumulative >= target && cumulative > 0) return levelAt(index)
    }
    return levelAt(0)
  }

  /** Visita ogni livello presente, con quante volte e' comparso. */
  inline fun forEachLevel(action: (levelDb: Double, count: Long) -> Unit) {
    for (index in 0 until binCount) {
      val count = countAt(index)
      if (count > 0) action(levelAt(index), count)
    }
  }

  val binCount: Int get() = counts.size

  fun countAt(index: Int): Long = counts[index]

  fun levelAt(index: Int): Double = minDb + index * stepDb

  private fun indexOf(levelDb: Double): Int =
    ((levelDb.coerceIn(minDb, maxDb) - minDb) / stepDb).roundToInt().coerceIn(0, counts.lastIndex)
}
