package dev.pampa.pampasounds.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pampa.pampasounds.dsp.dbToPower
import dev.pampa.pampasounds.dsp.powerToDb
import dev.pampa.pampasounds.ui.theme.CautionDb
import dev.pampa.pampasounds.ui.theme.DangerDb
import dev.pampa.pampasounds.ui.theme.LevelPalette
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Un'etichetta dell'asse del tempo: dove sta (0…1) e cosa dice. */
data class TimeLabel(val fraction: Float, val text: String)

/**
 * Il livello nel tempo: la linea del Leq e, dietro, la fascia fra minimo e massimo.
 *
 * Quando i punti sono piu' dei pixel si raggruppano: il Leq del gruppo e' la media *energetica*
 * (non quella dei numeri: un colpo forte deve restare visibile anche a un'ora per schermo), il
 * massimo e il minimo sono quelli del gruppo. Le soglie di 70 e 85 dB sono tratteggiate nei colori
 * delle zone, cosi' la domanda "quanto e' stato sopra il rischio" ha una risposta a colpo d'occhio.
 *
 * Valori in dB SPL, NaN dove manca il dato.
 */
@Composable
fun LevelLineChart(
  eq: FloatArray,
  palette: LevelPalette,
  modifier: Modifier = Modifier,
  max: FloatArray? = null,
  min: FloatArray? = null,
  height: Dp = 180.dp,
  timeLabels: List<TimeLabel> = emptyList(),
) {
  val lineColor = MaterialTheme.colorScheme.primary
  val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
  val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
  val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
  val measurer = rememberTextMeasurer()

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(height),
  ) {
    val gutterLeft = 30.dp.toPx()
    val gutterBottom = if (timeLabels.isEmpty()) 4.dp.toPx() else 18.dp.toPx()
    val plotWidth = size.width - gutterLeft
    val plotHeight = size.height - gutterBottom
    if (eq.isEmpty() || plotWidth <= 0f) return@Canvas

    // Raggruppamento: al massimo un punto ogni due pixel.
    val buckets = max(1, min(eq.size, (plotWidth / 2f).toInt()))
    val bEq = FloatArray(buckets) { Float.NaN }
    val bMax = FloatArray(buckets) { Float.NaN }
    val bMin = FloatArray(buckets) { Float.NaN }
    for (b in 0 until buckets) {
      val from = (b.toLong() * eq.size / buckets).toInt()
      val to = max(from + 1, ((b + 1).toLong() * eq.size / buckets).toInt())
      var power = 0.0
      var count = 0
      var hi = Float.NaN
      var lo = Float.NaN
      for (i in from until min(to, eq.size)) {
        val v = eq[i]
        if (!v.isNaN()) {
          power += dbToPower(v.toDouble())
          count++
        }
        val top = max?.getOrNull(i)?.takeUnless { it.isNaN() } ?: v
        val bottom = min?.getOrNull(i)?.takeUnless { it.isNaN() } ?: v
        if (!top.isNaN() && (hi.isNaN() || top > hi)) hi = top
        if (!bottom.isNaN() && (lo.isNaN() || bottom < lo)) lo = bottom
      }
      if (count > 0) bEq[b] = powerToDb(power / count).toFloat()
      bMax[b] = hi
      bMin[b] = lo
    }

    var dataLo = Float.POSITIVE_INFINITY
    var dataHi = Float.NEGATIVE_INFINITY
    for (b in 0 until buckets) {
      if (!bMin[b].isNaN()) dataLo = min(dataLo, bMin[b])
      if (!bMax[b].isNaN()) dataHi = max(dataHi, bMax[b])
    }
    if (dataLo.isInfinite() || dataHi.isInfinite()) return@Canvas
    var lo = floor((dataLo - 3f) / 10f) * 10f
    var hi = ceil((dataHi + 3f) / 10f) * 10f
    if (hi - lo < 30f) {
      val mid = (hi + lo) / 2f
      lo = floor((mid - 15f) / 10f) * 10f
      hi = lo + 30f
    }
    fun y(db: Float) = plotHeight - (db - lo) / (hi - lo) * plotHeight
    fun x(index: Int) = gutterLeft + if (buckets == 1) plotWidth / 2f else index * plotWidth / (buckets - 1)

    // Griglia e scala.
    val step = if (hi - lo > 60f) 20 else 10
    var level = (ceil(lo / step) * step).toInt()
    while (level <= hi) {
      val yy = y(level.toFloat())
      drawLine(gridColor, Offset(gutterLeft, yy), Offset(size.width, yy), 1.dp.toPx())
      val text = measurer.measure(level.toString(), labelStyle)
      drawText(text, topLeft = Offset(0f, (yy - text.size.height / 2f).coerceIn(0f, plotHeight - text.size.height)))
      level += step
    }
    for ((threshold, color) in listOf(CautionDb to palette.caution, DangerDb to palette.danger)) {
      val t = threshold.toFloat()
      if (t in lo..hi) {
        drawLine(
          color = color.copy(alpha = 0.7f),
          start = Offset(gutterLeft, y(t)),
          end = Offset(size.width, y(t)),
          strokeWidth = 1.5.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
      }
    }

    // Fascia min–max.
    if (max != null || min != null) {
      val band = Path()
      var open = false
      for (b in 0 until buckets) {
        if (bMax[b].isNaN()) continue
        if (!open) band.moveTo(x(b), y(bMax[b])) else band.lineTo(x(b), y(bMax[b]))
        open = true
      }
      for (b in buckets - 1 downTo 0) {
        if (bMin[b].isNaN()) continue
        band.lineTo(x(b), y(bMin[b]))
      }
      if (open) {
        band.close()
        drawPath(band, bandColor)
      }
    }

    // Linea del Leq, con un'ombra di riempimento verso il basso.
    val line = Path()
    val fill = Path()
    var started = false
    var lastX = 0f
    var firstX = 0f
    for (b in 0 until buckets) {
      val v = bEq[b]
      if (v.isNaN()) continue
      val px = x(b)
      val py = y(v)
      if (!started) {
        line.moveTo(px, py)
        fill.moveTo(px, plotHeight)
        fill.lineTo(px, py)
        firstX = px
        started = true
      } else {
        line.lineTo(px, py)
        fill.lineTo(px, py)
      }
      lastX = px
    }
    if (started) {
      fill.lineTo(lastX, plotHeight)
      fill.lineTo(firstX, plotHeight)
      fill.close()
      drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.18f), lineColor.copy(alpha = 0f)), endY = plotHeight))
      drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    for (label in timeLabels) {
      val text = measurer.measure(label.text, labelStyle)
      val cx = gutterLeft + label.fraction * plotWidth
      val left = (cx - text.size.width / 2f).coerceIn(gutterLeft, size.width - text.size.width)
      drawText(text, topLeft = Offset(left, size.height - text.size.height))
    }
  }
}
