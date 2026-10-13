package dev.pampa.pampasounds.dsp

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Un seno di valore efficace [rms] (non di ampiezza: e' l'RMS che il fonometro misura). */
fun sine(frequencyHz: Double, rms: Double, sampleRate: Int, seconds: Double): FloatArray {
  val amplitude = rms * sqrt(2.0)
  val count = (seconds * sampleRate).toInt()
  return FloatArray(count) { (amplitude * sin(2.0 * PI * frequencyHz * it / sampleRate)).toFloat() }
}

/** Raccoglie tutto quello che il processore emette. */
class RecordingListener : LevelProcessor.Listener {
  val ticks = ArrayList<LevelTick>()
  val seconds = ArrayList<LevelSecond>()
  override fun onTick(tick: LevelTick) {
    ticks += tick
  }

  override fun onSecond(second: LevelSecond) {
    seconds += second
  }
}
