package dev.pampa.pampasounds.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightingTest {

  @Test
  fun `la curva analitica A rispetta la tabella della norma`() {
    // IEC 61672-1, tabella 3: i valori sono arrotondati e le frequenze nominali (31,5 sta per
    // 31,62), quindi la tolleranza e' quella dell'arrotondamento.
    assertEquals(-39.4, aWeightingDb(31.5), 0.15)
    assertEquals(-19.1, aWeightingDb(100.0), 0.1)
    assertEquals(0.0, aWeightingDb(1000.0), 0.05)
    assertEquals(1.0, aWeightingDb(4000.0), 0.1)
    assertEquals(-2.5, aWeightingDb(10_000.0), 0.1)
  }

  @Test
  fun `la curva analitica C rispetta la tabella della norma`() {
    assertEquals(-3.0, cWeightingDb(31.5), 0.1)
    assertEquals(-0.3, cWeightingDb(100.0), 0.1)
    assertEquals(0.0, cWeightingDb(1000.0), 0.05)
    assertEquals(-4.4, cWeightingDb(10_000.0), 0.1)
  }

  @Test
  fun `il filtro A digitale segue la curva fino a qualche kHz`() {
    for (rate in intArrayOf(44_100, 48_000)) {
      val filter = WeightingFilter.create(FrequencyWeighting.A, rate)!!
      assertEquals(0.0, filter.responseDb(1000.0), 0.01)
      for (f in doubleArrayOf(63.0, 100.0, 250.0, 500.0, 2000.0, 4000.0)) {
        assertEquals("A a $f Hz, fs=$rate", aWeightingDb(f), filter.responseDb(f), 0.5)
      }
    }
  }

  @Test
  fun `il filtro C digitale segue la curva`() {
    val filter = WeightingFilter.create(FrequencyWeighting.C, 48_000)!!
    assertEquals(0.0, filter.responseDb(1000.0), 0.01)
    for (f in doubleArrayOf(31.5, 63.0, 100.0, 500.0, 2000.0, 4000.0)) {
      assertEquals("C a $f Hz", cWeightingDb(f), filter.responseDb(f), 0.5)
    }
  }

  @Test
  fun `la pesatura Z non ha filtro`() {
    assertNull(WeightingFilter.create(FrequencyWeighting.Z, 48_000))
  }

  @Test
  fun `un seno filtrato nel tempo perde quanto dice la risposta`() {
    val rate = 48_000
    val filter = WeightingFilter.create(FrequencyWeighting.A, rate)!!
    val signal = sine(100.0, rms = 0.1, sampleRate = rate, seconds = 2.0)
    var sum = 0.0
    var n = 0
    for ((i, s) in signal.withIndex()) {
      val y = filter.process(s.toDouble())
      if (i >= rate) { // un secondo per far assestare il filtro
        sum += y * y
        n++
      }
    }
    assertEquals(powerToDb(0.01) + aWeightingDb(100.0), powerToDb(sum / n), 0.3)
  }
}
