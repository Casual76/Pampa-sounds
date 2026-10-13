package dev.pampa.pampasounds

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.pampa.pampasounds.settings.MeterPreferences
import dev.pampa.pampasounds.ui.AppRoot
import dev.pampa.pampasounds.ui.theme.PampaSoundsTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val container = container
    setContent {
      // Niente tema finche' le impostazioni non sono lette: un primo fotogramma chiaro su un'app
      // scelta scura e' il lampo che si vede a ogni apertura.
      val settings by container.engineSettings.settings.collectAsStateWithLifecycle(initialValue = lastSettings)
      val resolved = settings ?: return@setContent
      SideEffect { lastSettings = resolved }

      val meter by container.meterEngine.state.collectAsStateWithLifecycle()
      val prefs by container.meterSettings.preferences.collectAsStateWithLifecycle(initialValue = MeterPreferences())
      val keepOn = meter.isMeasuring && prefs.keepScreenOn
      LaunchedEffect(keepOn) {
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }

      PampaSoundsTheme(settings = resolved) {
        AppRoot(container)
      }
    }
  }

  private companion object {
    // Per processo: una rotazione non deve rileggere il DataStore e lasciare un fotogramma vuoto.
    var lastSettings: EngineSettings? = null
  }
}
