package dev.pampa.pampasounds.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

class SpectrumTest {

  private fun lastFrame(signal: FloatArray, rate: Int): SpectrumFrame {
    val analyzer = SpectrumAnalyzer(rate)
    var frame: SpectrumFrame? = null
    analyzer.push(signal, signal.size) { frame = it }
    return frame ?: error("nessun fotogramma: il segnale e' piu' corto della finestra")
  }

  @Test
  fun `la FFT di un impulso e' piatta`() {
    val fft = Fft(64)
    val re = DoubleArray(64).also { it[0] = 1.0 }
    val im = DoubleArray(64)
    fft.transform(re, im)
    for (k in 0 until 64) {
      assertEquals(1.0, re[k], 1e-12)
      assertEquals(0.0, im[k], 1e-12)
    }
  }

  @Test
  fun `un tono legge il suo livello nel suo bin`() {
    val rate = 48_000
    val frame = lastFrame(sine(1000.0, rms = 0.1, sampleRate = rate, seconds = 1.0), rate)
    val tone = Pitch.dominant(frame.lineDb, frame.binHz)!!
    assertEquals(1000.0, tone.frequencyHz, 1.0)
    // Un tono fra due bin perde fino a 1,4 dB con Hann; l'interpolazione ne recupera gran parte.
    assertEquals(-20.0, tone.levelDb, 0.6)
  }

  @Test
  fun `l'energia delle bande torna quella del segnale`() {
    val rate = 48_000
    val frame = lastFrame(sine(1000.0, rms = 0.1, sampleRate = rate, seconds = 1.0), rate)
    val band1k = ThirdOctave.labels.indexOf("1k")
    assertEquals(-20.0, frame.bandDb[band1k].toDouble(), 0.3)
    val total = frame.bandPower.sum()
    assertEquals(-20.0, 10 * log10(total), 0.3)
    // Le bande lontane dal tono restano giu' di almeno 60 dB.
    val band100 = ThirdOctave.labels.indexOf("100")
    assertTrue(frame.bandDb[band100] < -80f)
  }

  @Test
  fun `le bande sono trenta, contigue, da 25 Hz a 20 kHz`() {
    assertEquals(ThirdOctave.COUNT, ThirdOctave.labels.size)
    assertEquals(25.0, ThirdOctave.centers.first(), 0.5)
    assertEquals(20_000.0, ThirdOctave.centers.last(), 200.0)
    for (i in 1 until ThirdOctave.COUNT) {
      assertTrue(abs(ThirdOctave.upperEdge(i - 1) - ThirdOctave.lowerEdge(i)) < 1e-9)
    }
  }

  @Test
  fun `440 Hz e' il La4`() {
    val note = Pitch.note(440.0)!!
    assertEquals(69, note.midi)
    assertEquals(9, note.pitchClass)
    assertEquals(4, note.octave)
    assertEquals(0, note.cents)
    val c4 = Pitch.note(261.63)!!
    assertEquals(0, c4.pitchClass)
    assertEquals(4, c4.octave)
  }
}
