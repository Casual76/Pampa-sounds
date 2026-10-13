package dev.pampa.pampasounds.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.theme.FluidTone

/** Sotto i 70 dB(A) un ambiente e' tranquillo; da 85, otto ore fanno danni (NIOSH). */
const val CautionDb: Double = 70.0
const val DangerDb: Double = 85.0

/**
 * I colori dei livelli: sicuro, attenzione, rischio — e la scala dello spettrogramma.
 *
 * L'arco del misuratore e i grafici hanno bisogno del colore crudo di una zona, ma i colori dei
 * toni dell'engine (`toneColors`) sono privati. Quindi la coppia chiaro/scuro di Success e Warning
 * si dichiara qui, **identica** a quella di `FluidTone` (FluidComponents.kt), come chiede
 * regole.md per i ruoli che Material non ha; il rischio e' `error`, che un ruolo ce l'ha.
 */
@Immutable
class LevelPalette(
  val safe: Color,
  val caution: Color,
  val danger: Color,
  /** Duecentocinquantasei colori ARGB dal fondo (silenzio) al rosso (forte). */
  val spectrogram: IntArray,
) {
  fun colorFor(db: Double): Color = when {
    db.isNaN() || db < CautionDb -> safe
    db < DangerDb -> caution
    else -> danger
  }

  fun toneFor(db: Double): FluidTone = when {
    db.isNaN() || db < CautionDb -> FluidTone.Success
    db < DangerDb -> FluidTone.Warning
    else -> FluidTone.Danger
  }
}

@Composable
fun rememberLevelPalette(): LevelPalette {
  val scheme = MaterialTheme.colorScheme
  val dark = GlassDefaults.isDarkSurface()
  return remember(scheme, dark) {
    val safe = if (dark) Color(0xFF81C784) else Color(0xFF1B5E20)
    val caution = if (dark) Color(0xFFFFB74D) else Color(0xFFE65100)
    val danger = scheme.error
    val stops = listOf(
      scheme.surfaceContainerLowest,
      scheme.primaryContainer,
      scheme.primary,
      caution,
      danger,
    )
    val ramp = IntArray(256) { i ->
      val position = i / 255f * (stops.size - 1)
      val index = position.toInt().coerceAtMost(stops.size - 2)
      lerp(stops[index], stops[index + 1], position - index).toArgb()
    }
    LevelPalette(safe = safe, caution = caution, danger = danger, spectrogram = ramp)
  }
}
