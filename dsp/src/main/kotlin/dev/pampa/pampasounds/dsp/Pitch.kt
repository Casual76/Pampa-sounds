package dev.pampa.pampasounds.dsp

import kotlin.math.ln
import kotlin.math.roundToInt

/** Il tono che domina lo spettro: dove sta e quanto e' forte. */
data class DominantTone(val frequencyHz: Double, val levelDb: Double)

/** Una nota temperata (La4 = 440 Hz) e di quanti cent la frequenza se ne discosta. */
data class MusicalNote(val midi: Int, val cents: Int) {
  /** 0 = Do, 1 = Do♯, … 11 = Si. */
  val pitchClass: Int get() = Math.floorMod(midi, 12)

  /** Ottava in notazione scientifica: il Do centrale e' Do4. */
  val octave: Int get() = Math.floorDiv(midi, 12) - 1
}

object Pitch {

  /**
   * Il picco piu' alto fra [minHz] e [maxHz] in uno spettro in dB, con interpolazione parabolica:
   * fra due bin la frequenza vera si ricava dalla curvatura dei tre valori attorno al massimo, e la
   * risoluzione passa da un bin intero a qualche centesimo di bin.
   */
  fun dominant(
    lineDb: FloatArray,
    binHz: Double,
    minHz: Double = 20.0,
    maxHz: Double = 20_000.0,
  ): DominantTone? {
    val first = (minHz / binHz).toInt().coerceAtLeast(1)
    val last = (maxHz / binHz).toInt().coerceAtMost(lineDb.size - 2)
    if (first >= last) return null
    var best = first
    for (k in first..last) if (lineDb[k] > lineDb[best]) best = k
    val alpha = lineDb[best - 1].toDouble()
    val beta = lineDb[best].toDouble()
    val gamma = lineDb[best + 1].toDouble()
    val denominator = alpha - 2 * beta + gamma
    val delta = if (denominator == 0.0) 0.0 else (0.5 * (alpha - gamma) / denominator).coerceIn(-0.5, 0.5)
    val level = beta - 0.25 * (alpha - gamma) * delta
    return DominantTone(frequencyHz = (best + delta) * binHz, levelDb = level)
  }

  fun note(frequencyHz: Double): MusicalNote? {
    if (frequencyHz <= 0.0) return null
    val exact = 69.0 + 12.0 * ln(frequencyHz / 440.0) / ln(2.0)
    val midi = exact.roundToInt()
    return MusicalNote(midi = midi, cents = ((exact - midi) * 100).roundToInt())
  }
}
