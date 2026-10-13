package dev.pampa.pampasounds.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pampa.pampasounds.dsp.FrequencyWeighting
import dev.pampa.pampasounds.dsp.ThirdOctave
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.exp

/** Le frequenze segnate sull'asse: le ottave da 31,5 Hz a 16 kHz. */
private val FrequencyTicks = listOf(
  31.5 to "31", 63.0 to "63", 125.0 to "125", 250.0 to "250", 500.0 to "500",
  1000.0 to "1k", 2000.0 to "2k", 4000.0 to "4k", 8000.0 to "8k", 16000.0 to "16k",
)

/** Lo span verticale degli spettri: novanta decibel bastano dal fruscio al fischio. */
const val SpectrumSpanDb = 90f

/** La cima della scala per un picco: arrotondata ai 10 dB sopra, mai sotto i 40. */
fun spectrumTop(peakDb: Float): Float = max(40f, ceil((peakDb + 5f) / 10f) * 10f)

/** Il guadagno della pesatura per ogni bin di una FFT, da sommare in dB. */
fun binWeights(weighting: FrequencyWeighting, sampleRate: Int, fftSize: Int): FloatArray =
  FloatArray(fftSize / 2 + 1) { k ->
    if (k == 0) -200f else weighting.gainDb(k * sampleRate.toDouble() / fftSize).toFloat()
  }

/**
 * Lo spettro fine: la curva per bin su un asse logaritmico da 20 Hz a 20 kHz.
 *
 * Ogni colonna di due pixel prende il massimo dei bin che copre: negli acuti una colonna copre
 * decine di bin, e un fischio stretto deve restare un picco invece di sparire nella media. Nei
 * bassi, dove un bin e' piu' largo di una colonna, si interpola. [peakHold] e' la linea sottile dei
 * massimi che scendono piano.
 *
 * [levels] e [peakHold] sono dB SPL gia' pesati e calibrati, uno per bin.
 */
@Composable
fun SpectrumChart(
  levels: FloatArray,
  binHz: Double,
  topDb: Float,
  modifier: Modifier = Modifier,
  peakHold: FloatArray? = null,
  markerHz: Double? = null,
  height: Dp = 220.dp,
) {
  val lineColor = MaterialTheme.colorScheme.primary
  val peakColor = MaterialTheme.colorScheme.tertiary
  val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
  val markerColor = MaterialTheme.colorScheme.onSurfaceVariant
  val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
  val measurer = rememberTextMeasurer()
  val maxHz = min(20_000.0, (levels.size - 1) * binHz)

  Canvas(modifier.fillMaxWidth().height(height)) {
    val plot = plotArea()
    val lo = topDb - SpectrumSpanDb
    drawDbGrid(plot, lo, topDb, gridColor, measurer, labelStyle)
    drawFrequencyGrid(plot, maxHz, gridColor, measurer, labelStyle)
    if (levels.isEmpty()) return@Canvas

    fun columns(values: FloatArray): FloatArray {
      val step = 2.dp.toPx()
      val count = max(2, (plot.width / step).toInt())
      return FloatArray(count) { c ->
        val f0 = frequencyAt(c / count.toFloat(), maxHz)
        val f1 = frequencyAt((c + 1) / count.toFloat(), maxHz)
        val k0 = f0 / binHz
        val k1 = f1 / binHz
        if (k1 - k0 < 1.0) {
          val k = k0.toInt().coerceIn(1, values.size - 2)
          val t = (k0 - k).toFloat()
          values[k] * (1 - t) + values[k + 1] * t
        } else {
          var best = Float.NEGATIVE_INFINITY
          for (k in k0.toInt().coerceAtLeast(1)..k1.toInt().coerceAtMost(values.size - 1)) best = max(best, values[k])
          best
        }
      }
    }

    fun path(values: FloatArray, closed: Boolean): Path {
      val path = Path()
      val count = values.size
      for (c in 0 until count) {
        val x = plot.left + plot.width * (c + 0.5f) / count
        val y = plot.top + plot.height * (1f - ((values[c] - lo) / SpectrumSpanDb).coerceIn(0f, 1f))
        if (c == 0) {
          if (closed) {
            path.moveTo(x, plot.bottom)
            path.lineTo(x, y)
          } else {
            path.moveTo(x, y)
          }
        } else {
          path.lineTo(x, y)
        }
      }
      if (closed) {
        path.lineTo(plot.right, plot.bottom)
        path.close()
      }
      return path
    }

    val current = columns(levels)
    drawPath(path(current, closed = true), Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.32f), lineColor.copy(alpha = 0.02f)), startY = plot.top, endY = plot.bottom))
    drawPath(path(current, closed = false), lineColor, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
    if (peakHold != null) {
      drawPath(path(columns(peakHold), closed = false), peakColor.copy(alpha = 0.8f), style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
    }
    if (markerHz != null && markerHz in 20.0..maxHz) {
      val x = plot.left + plot.width * fractionAt(markerHz, maxHz)
      drawLine(
        markerColor,
        Offset(x, plot.top),
        Offset(x, plot.bottom),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
      )
    }
  }
}

/**
 * Le trenta bande a terzi d'ottava, a barre. [levels] in dB SPL gia' pesati, [peakHold] opzionale.
 */
@Composable
fun BandChart(
  levels: FloatArray,
  topDb: Float,
  modifier: Modifier = Modifier,
  peakHold: FloatArray? = null,
  height: Dp = 220.dp,
) {
  val barColor = MaterialTheme.colorScheme.primary
  val peakColor = MaterialTheme.colorScheme.tertiary
  val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
  val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
  val measurer = rememberTextMeasurer()

  Canvas(modifier.fillMaxWidth().height(height)) {
    val plot = plotArea()
    val lo = topDb - SpectrumSpanDb
    drawDbGrid(plot, lo, topDb, gridColor, measurer, labelStyle)
    val slot = plot.width / ThirdOctave.COUNT
    val gap = min(2.dp.toPx(), slot * 0.25f)
    for (band in 0 until min(ThirdOctave.COUNT, levels.size)) {
      val left = plot.left + band * slot + gap / 2
      val fraction = ((levels[band] - lo) / SpectrumSpanDb).coerceIn(0f, 1f)
      val top = plot.bottom - plot.height * fraction
      drawRect(
        brush = Brush.verticalGradient(listOf(barColor, barColor.copy(alpha = 0.55f)), startY = top, endY = plot.bottom),
        topLeft = Offset(left, top),
        size = Size(slot - gap, plot.bottom - top),
      )
      peakHold?.getOrNull(band)?.let { peak ->
        val py = plot.bottom - plot.height * ((peak - lo) / SpectrumSpanDb).coerceIn(0f, 1f)
        drawLine(peakColor, Offset(left, py), Offset(left + slot - gap, py), strokeWidth = 2.dp.toPx())
      }
      // Un'etichetta ogni ottava: 31, 63, 125 … 16k.
      if (band % 3 == 1) {
        val text = measurer.measure(ThirdOctave.labels[band].replace(",5", ""), labelStyle)
        val cx = left + (slot - gap) / 2
        drawText(text, topLeft = Offset((cx - text.size.width / 2f).coerceIn(plot.left, size.width - text.size.width), size.height - text.size.height))
      }
    }
  }
}

/** L'area del grafico dentro i margini per le etichette. */
internal class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  val width: Float get() = right - left
  val height: Float get() = bottom - top
}

internal fun DrawScope.plotArea(): PlotArea =
  PlotArea(left = 30.dp.toPx(), top = 4.dp.toPx(), right = size.width, bottom = size.height - 18.dp.toPx())

internal fun DrawScope.drawDbGrid(
  plot: PlotArea,
  lo: Float,
  hi: Float,
  color: androidx.compose.ui.graphics.Color,
  measurer: TextMeasurer,
  style: TextStyle,
) {
  var level = (ceil(lo / 20f) * 20f).toInt()
  while (level <= hi) {
    val y = plot.bottom - plot.height * (level - lo) / (hi - lo)
    drawLine(color, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
    val text = measurer.measure(level.toString(), style)
    drawText(text, topLeft = Offset(0f, (y - text.size.height / 2f).coerceIn(0f, plot.bottom - text.size.height)))
    level += 20
  }
}

private fun DrawScope.drawFrequencyGrid(
  plot: PlotArea,
  maxHz: Double,
  color: androidx.compose.ui.graphics.Color,
  measurer: TextMeasurer,
  style: TextStyle,
) {
  for ((hz, label) in FrequencyTicks) {
    if (hz > maxHz) continue
    val x = plot.left + plot.width * fractionAt(hz, maxHz)
    drawLine(color, Offset(x, plot.top), Offset(x, plot.bottom), 1.dp.toPx())
    val text = measurer.measure(label, style)
    drawText(text, topLeft = Offset((x - text.size.width / 2f).coerceIn(plot.left, size.width - text.size.width), size.height - text.size.height))
  }
}

private const val MinHz = 20.0

internal fun fractionAt(hz: Double, maxHz: Double): Float =
  ((ln(hz) - ln(MinHz)) / (ln(maxHz) - ln(MinHz))).toFloat()

internal fun frequencyAt(fraction: Float, maxHz: Double): Double =
  exp(ln(MinHz) + (ln(maxHz) - ln(MinHz)) * fraction)
