package dev.pampa.pampasounds.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import dev.pampa.pampasounds.audio.SpectrogramHistory
import dev.pampa.pampasounds.dsp.FrequencyWeighting
import dev.pampa.pampasounds.ui.theme.LevelPalette
import kotlin.math.roundToInt

/**
 * La cascata: il tempo scorre da destra a sinistra, la frequenza sale dal basso, il colore e' il
 * livello. E' il grafico che dice *da dove* viene un rumore: il ronzio di rete e' una riga fissa a
 * 50 Hz, una voce e' una scala di armoniche che si muove, il traffico e' una nuvola nei bassi.
 *
 * La bitmap si ricompone tutta a ogni fotogramma (512 × 160, pochi decimi di millisecondo), perche'
 * pesatura, calibrazione e colori sono di chi guarda e possono cambiare in qualsiasi momento.
 * [frameCount] e' letto nel disegno: cambia, e si ridisegna solo il Canvas.
 */
@Composable
fun SpectrogramView(
  history: SpectrogramHistory,
  frameCount: () -> Long,
  weighting: FrequencyWeighting,
  offsetDb: Double,
  topDb: () -> Float,
  palette: LevelPalette,
  modifier: Modifier = Modifier,
  height: Dp = 200.dp,
  spanDb: Float = 70f,
) {
  val columns = history.columns
  val rows = history.rows
  val bitmap = remember(history) { createBitmap(columns, rows) }
  val image = remember(bitmap) { bitmap.asImageBitmap() }
  val values = remember(history) { FloatArray(columns * rows) }
  val pixels = remember(history) { IntArray(columns * rows) }
  val rowGain = remember(history, weighting) {
    FloatArray(rows) { weighting.gainDb(history.rowFrequencies[it]).toFloat() }
  }
  val measurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)

  Canvas(modifier.fillMaxWidth().height(height)) {
    frameCount() // la dipendenza che fa ridisegnare
    history.copyChronological(values)
    val top = topDb()
    val floor = top - spanDb
    val ramp = palette.spectrogram
    val offset = offsetDb.toFloat()
    for (column in 0 until columns) {
      val base = column * rows
      for (row in 0 until rows) {
        val raw = values[base + row]
        val index = if (raw <= SpectrogramHistory.SILENT + 1f) 0 else {
          val level = raw + rowGain[row] + offset
          (((level - floor) / spanDb) * 255f).roundToInt().coerceIn(0, 255)
        }
        pixels[(rows - 1 - row) * columns + column] = ramp[index]
      }
    }
    bitmap.setPixels(pixels, 0, columns, 0, 0, columns, rows)
    drawImage(
      image = image,
      srcOffset = IntOffset.Zero,
      srcSize = IntSize(columns, rows),
      dstOffset = IntOffset.Zero,
      dstSize = IntSize(size.width.toInt(), size.height.toInt()),
      filterQuality = FilterQuality.Low,
    )
    // Tre frequenze di riferimento sul bordo sinistro.
    for ((hz, label) in listOf(100.0 to "100", 1000.0 to "1k", 10_000.0 to "10k")) {
      if (hz > history.maxHz) continue
      val fraction = fractionAt(hz, history.maxHz)
      val y = size.height * (1f - fraction)
      val text = measurer.measure(label, labelStyle)
      drawText(text, topLeft = Offset(4.dp.toPx(), (y - text.size.height / 2f).coerceIn(0f, size.height - text.size.height)))
    }
  }
}
