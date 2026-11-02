package dev.pampa.pampasounds.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class TabBarMotionTest {

  @Test
  fun `a riposo la barra c'e' o non c'e', secondo la pagina davanti`() {
    assertEquals(0f, tabBarHidden(moving = false, fromHidden = false, toHidden = true, progress = 0.7f, restHidden = false), 0f)
    assertEquals(1f, tabBarHidden(moving = false, fromHidden = false, toHidden = false, progress = 0f, restHidden = true), 0f)
  }

  @Test
  fun `aprendo il dettaglio la barra esce con la pagina`() {
    // scheda -> sessione: da visibile (0) a via (1), di quanto l'host dice che il movimento e' fatto
    assertEquals(0f, tabBarHidden(moving = true, fromHidden = false, toHidden = true, progress = 0f, restHidden = true), 0f)
    assertEquals(0.4f, tabBarHidden(moving = true, fromHidden = false, toHidden = true, progress = 0.4f, restHidden = true), 1e-6f)
    assertEquals(1f, tabBarHidden(moving = true, fromHidden = false, toHidden = true, progress = 1f, restHidden = true), 0f)
  }

  @Test
  fun `chiudendo il dettaglio col dito la barra rientra e un back annullato la riporta via`() {
    // sessione -> scheda: da via (1) a visibile (0); l'avanzamento e' quello dell'uscita della sessione
    assertEquals(1f, tabBarHidden(moving = true, fromHidden = true, toHidden = false, progress = 0f, restHidden = false), 0f)
    assertEquals(0.75f, tabBarHidden(moving = true, fromHidden = true, toHidden = false, progress = 0.25f, restHidden = false), 1e-6f)
    // il dito torna indietro: l'avanzamento scende, la barra con lui
    assertEquals(0.9f, tabBarHidden(moving = true, fromHidden = true, toHidden = false, progress = 0.1f, restHidden = false), 1e-6f)
  }

  @Test
  fun `fra due schede la barra non si muove`() {
    for (progress in listOf(0f, 0.3f, 1f)) {
      assertEquals(0f, tabBarHidden(moving = true, fromHidden = false, toHidden = false, progress = progress, restHidden = false), 0f)
    }
  }

  @Test
  fun `un avanzamento fuori da 0-1 non porta la barra fuori misura`() {
    assertEquals(1f, tabBarHidden(moving = true, fromHidden = false, toHidden = true, progress = 1.4f, restHidden = true), 0f)
    assertEquals(0f, tabBarHidden(moving = true, fromHidden = false, toHidden = true, progress = -0.2f, restHidden = true), 0f)
  }
}
