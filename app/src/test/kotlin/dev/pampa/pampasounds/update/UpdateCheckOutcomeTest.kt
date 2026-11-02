package dev.pampa.pampasounds.update

import dev.antigravity.fluidengine.foundation.AvailableAppUpdate
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateUiState
import dev.antigravity.fluidengine.ui.fluid.release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckOutcomeTest {

  private val found = AvailableAppUpdate(
    version = "1.1.0",
    changelog = "Fluid Engine 3.0.",
    releaseTag = "stable-pampa-sounds-v1.1.0",
    apkAsset = "pampa-sounds-1.1.0.apk",
    downloadUrl = "https://example.invalid/pampa-sounds-1.1.0.apk",
    sizeBytes = 3_200_000L,
  )

  @Test
  fun `nessuna versione piu' nuova non e' un errore`() {
    val outcome = updateCheckOutcome(Result.success(null))
    assertEquals(FluidUpdateUiState.UpToDate, outcome.state)
    assertNull(outcome.update)
  }

  @Test
  fun `una versione trovata si tiene da parte e si offre con peso e note`() {
    val outcome = updateCheckOutcome(Result.success(found))
    assertSame(found, outcome.update)
    val state = outcome.state as FluidUpdateUiState.Available
    assertEquals("1.1.0", state.release.version)
    assertEquals(3_200_000L, state.release.sizeBytes)
    assertEquals("Fluid Engine 3.0.", state.release.notes)
  }

  @Test
  fun `un controllo che non riesce dice di riprovare e non lascia una versione vecchia da installare`() {
    val outcome = updateCheckOutcome(Result.failure(java.io.IOException("offline")))
    val state = outcome.state as FluidUpdateUiState.CheckFailed
    assertEquals("offline", state.message)
    assertNull(outcome.update)
    assertNull(outcome.state.release)
    assertTrue(outcome.state !is FluidUpdateUiState.UpToDate)
  }
}
