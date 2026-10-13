package dev.pampa.pampasounds.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelProcessorTest {

  private val a = FrequencyWeighting.A.ordinal
  private val z = FrequencyWeighting.Z.ordinal
  private val fast = TimeWeighting.Fast.ordinal
  private val slow = TimeWeighting.Slow.ordinal

  @Test
  fun `un seno a 1 kHz legge il suo RMS in dBFS su tutte le pesature`() {
    val rate = 48_000
    val processor = LevelProcessor(rate)
    val listener = RecordingListener()
    val signal = sine(1000.0, rms = 0.1, sampleRate = rate, seconds = 3.0)
    processor.process(signal, signal.size, listener)

    val summary = processor.summary()
    for (w in FrequencyWeighting.entries) {
      assertEquals("Leq ${w.name}", -20.0, summary.leqDb[w.ordinal], 0.05)
    }
    val last = listener.ticks.last()
    assertEquals(-20.0, last.fastDb[a], 0.1)
    assertEquals(-20.0, last.slowDb[a], 0.1)
    // Il picco di un seno e' 3 dB sopra il suo RMS.
    assertEquals(-16.99, summary.peakDb, 0.05)
  }

  @Test
  fun `otto tick e un secondo per ogni secondo di segnale, anche a 44,1 kHz`() {
    val rate = 44_100
    val processor = LevelProcessor(rate)
    val listener = RecordingListener()
    val signal = sine(500.0, rms = 0.05, sampleRate = rate, seconds = 5.0)
    // A blocchi di dimensione strana, come arrivano davvero da AudioRecord.
    var offset = 0
    val block = FloatArray(1031)
    while (offset < signal.size) {
      val n = minOf(block.size, signal.size - offset)
      System.arraycopy(signal, offset, block, 0, n)
      processor.process(block, n, listener)
      offset += n
    }
    assertEquals(5, listener.seconds.size)
    assertEquals(40, listener.ticks.size)
    assertEquals(4, listener.seconds.last().index)
    for (second in listener.seconds) {
      assertEquals(powerToDb(0.0025), second.eqDb[z], 0.05)
    }
  }

  @Test
  fun `il primo secondo non entra nel minimo`() {
    val rate = 48_000
    val processor = LevelProcessor(rate)
    val signal = sine(1000.0, rms = 0.01, sampleRate = rate, seconds = 4.0)
    processor.process(signal, signal.size, RecordingListener())
    val summary = processor.summary()
    // Senza riscaldamento e pre-carica il minimo Slow sarebbe vicino a −160 dB.
    assertEquals(-40.0, summary.minDb[a][slow], 0.3)
    assertEquals(-40.0, summary.maxDb[a][fast], 0.2)
  }

  @Test
  fun `un rumore che raddoppia l'energia alza il Leq di 3 dB`() {
    val rate = 48_000
    val processor = LevelProcessor(rate)
    val listener = RecordingListener()
    val quiet = sine(1000.0, rms = 0.01, sampleRate = rate, seconds = 2.0)
    val loud = sine(1000.0, rms = 0.1, sampleRate = rate, seconds = 2.0)
    processor.process(quiet, quiet.size, listener)
    processor.process(loud, loud.size, listener)
    // Meta' tempo a −40, meta' a −20: Leq = 10·log10((1e-4 + 1e-2)/2) = −22,97 dB.
    assertEquals(-22.97, processor.summary().leqDb[z], 0.1)
  }

  @Test
  fun `L10 sta sopra L90 quando il livello varia`() {
    val rate = 48_000
    val processor = LevelProcessor(rate)
    val listener = RecordingListener()
    repeat(5) {
      val quiet = sine(1000.0, rms = 0.01, sampleRate = rate, seconds = 1.0)
      val loud = sine(1000.0, rms = 0.1, sampleRate = rate, seconds = 1.0)
      processor.process(quiet, quiet.size, listener)
      processor.process(loud, loud.size, listener)
    }
    val summary = processor.summary()
    assertTrue(summary.l10Db[a] > summary.l50Db[a])
    assertTrue(summary.l50Db[a] > summary.l90Db[a])
    assertEquals(-20.0, summary.l10Db[a], 1.0)
    assertEquals(-40.0, summary.l90Db[a], 1.5)
  }
}
