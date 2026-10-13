package dev.pampa.pampasounds.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/**
 * Il colore di Pampa Sounds: un ottanio, fra il verde acqua e il blu.
 *
 * L'unico posto dell'app dove un colore si scrive a mano: da questa coppia l'engine ricava tutta la
 * palette (superfici, contenitori, ruoli secondari), e il resto dell'app chiede ruoli, mai valori.
 */
val PampaSoundsBrand = AccentPreset(
  name = "pampasounds",
  label = "Pampa Sounds",
  light = Color(0xFF0B7A8A),
  dark = Color(0xFF5CD3E2),
)

@Composable
fun PampaSoundsTheme(settings: EngineSettings, content: @Composable () -> Unit) {
  FluidTheme(settings = settings, brand = PampaSoundsBrand, content = content)
}
