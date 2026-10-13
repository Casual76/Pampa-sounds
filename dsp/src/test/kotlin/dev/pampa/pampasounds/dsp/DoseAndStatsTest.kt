package dev.pampa.pampasounds.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoseAndStatsTest {

  @Test
  fun `otto ore a 85 dBA sono il 100 percento NIOSH`() {
    assertEquals(100.0, NoiseDose.percentFor(85.0, 8 * 3600.0, DoseCriterion.Niosh), 1e-9)
    assertEquals(100.0, NoiseDose.percentFor(88.0, 4 * 3600.0, DoseCriterion.Niosh), 1e-9)
    assertEquals(0.0, NoiseDose.percentFor(79.9, 8 * 3600.0, DoseCriterion.Niosh), 1e-9)
  }

  @Test
  fun `otto ore a 90 dBA sono il 100 percento OSHA`() {
    assertEquals(100.0, NoiseDose.percentFor(90.0, 8 * 3600.0, DoseCriterion.Osha), 1e-9)
    assertEquals(100.0, NoiseDose.percentFor(95.0, 4 * 3600.0, DoseCriterion.Osha), 1e-9)
  }

  @Test
  fun `la dose dall'istogramma applica la calibrazione`() {
    val histogram = LevelHistogram()
    repeat(3600) { histogram.add(-45.0) } // un'ora a −45 dBFS
    // Con +130 dB di offset sono 85 dB(A): un'ora e' un ottavo della dose giornaliera.
    assertEquals(12.5, NoiseDose.percent(histogram, 130.0, DoseCriterion.Niosh), 0.01)
    assertEquals(100.0, NoiseDose.projected(12.5, 3600.0), 1e-9)
  }

  @Test
  fun `il TWA inverte la dose`() {
    assertEquals(85.0, NoiseDose.timeWeightedAverage(100.0, DoseCriterion.Niosh)!!, 1e-9)
    assertEquals(88.0, NoiseDose.timeWeightedAverage(200.0, DoseCriterion.Niosh)!!, 0.02)
    assertEquals(95.0, NoiseDose.timeWeightedAverage(200.0, DoseCriterion.Osha)!!, 0.02)
    assertNull(NoiseDose.timeWeightedAverage(0.0, DoseCriterion.Niosh))
  }

  @Test
  fun `i percentili dell'istogramma`() {
    val histogram = LevelHistogram()
    // Cento livelli in dBFS, da −100 a −1.
    for (level in 1..100) histogram.add(level - 101.0)
    assertEquals(-10.0, histogram.exceeded(10.0)!!, 0.05)
    assertEquals(-50.0, histogram.exceeded(50.0)!!, 0.05)
    assertEquals(-90.0, histogram.exceeded(90.0)!!, 0.05)
    assertNull(LevelHistogram().exceeded(50.0))
  }

  @Test
  fun `la media energetica non e' la media dei numeri`() {
    assertEquals(77.04, energeticMean(doubleArrayOf(60.0, 80.0)), 0.01)
  }

  @Test
  fun `la calibrazione segue la CDD`() {
    assertEquals(130.0, Calibration.baseOffsetDb(CaptureSource.Unprocessed), 1e-9)
    assertEquals(112.35, Calibration.baseOffsetDb(CaptureSource.VoiceRecognition), 0.01)
    // Il telefono legge 60, il fonometro 63: il ritocco sale di 3.
    assertEquals(4.0, Calibration.trimForReference(1.0, measuredSplDb = 60.0, referenceSplDb = 63.0), 1e-9)
    assertEquals(Calibration.TRIM_RANGE_DB, Calibration.trimForReference(0.0, 0.0, 100.0), 1e-9)
  }
}
