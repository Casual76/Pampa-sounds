package dev.pampa.pampasounds.ui.settings

import dev.antigravity.fluidengine.ui.fluid.FluidPromptValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationInputTest {

  @Test
  fun `il campo vuoto non ha sbagliato niente`() {
    assertEquals(FluidPromptValidation.Incomplete, calibrationValidation("", appReading = 60.0))
    assertEquals(FluidPromptValidation.Incomplete, calibrationValidation("   ", appReading = 60.0))
  }

  @Test
  fun `un valore del fonometro con la virgola o con il punto e' valido`() {
    assertEquals(FluidPromptValidation.Valid, calibrationValidation("54,3", appReading = 52.0))
    assertEquals(FluidPromptValidation.Valid, calibrationValidation("54.3", appReading = 52.0))
    assertEquals(FluidPromptValidation.Valid, calibrationValidation("54,", appReading = 52.0))
  }

  @Test
  fun `i bordi dell'intervallo 20-140 dB sono validi, fuori no`() {
    assertEquals(FluidPromptValidation.Valid, calibrationValidation("20", appReading = 30.0))
    assertEquals(FluidPromptValidation.Valid, calibrationValidation("140", appReading = 30.0))
    assertTrue(calibrationValidation("19,9", appReading = 30.0) is FluidPromptValidation.Invalid)
    assertTrue(calibrationValidation("140,1", appReading = 30.0) is FluidPromptValidation.Invalid)
  }

  @Test
  fun `quello che non e' un numero lo dice`() {
    val verdict = calibrationValidation("abc", appReading = 30.0)
    assertTrue(verdict is FluidPromptValidation.Invalid)
    assertEquals("Scrivi un numero, per esempio 54,3", (verdict as FluidPromptValidation.Invalid).message)
  }

  @Test
  fun `senza una lettura dell'app non si calibra niente`() {
    assertTrue(calibrationValidation("54,3", appReading = null) is FluidPromptValidation.Invalid)
    assertTrue(calibrationValidation("54,3", appReading = Double.NaN) is FluidPromptValidation.Invalid)
  }

  @Test
  fun `parseReference accetta virgola e punto e scarta il resto`() {
    assertEquals(54.3, parseReference("54,3")!!, 1e-9)
    assertEquals(54.3, parseReference(" 54.3 ")!!, 1e-9)
    assertNull(parseReference("cinquanta"))
    assertNull(parseReference(""))
  }
}
