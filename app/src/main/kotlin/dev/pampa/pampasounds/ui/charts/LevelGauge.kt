package dev.pampa.pampasounds.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.pampa.pampasounds.ui.format.formatDb
import dev.pampa.pampasounds.ui.theme.CautionDb
import dev.pampa.pampasounds.ui.theme.DangerDb
import dev.pampa.pampasounds.ui.theme.LevelPalette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val GaugeMin = 20f
private const val GaugeMax = 120f
private const val StartAngle = 150f
private const val SweepAngle = 240f

/**
 * Il misuratore: un arco da 20 a 120 dB con le tre zone, il livello di adesso e il massimo.
 *
 * Le zone stanno sempre sulla pista, appena accennate, perche' la domanda vera davanti a un numero
 * e' "e' tanto?": l'arco risponde prima che si legga la cifra. Il segno del massimo resta li' mentre
 * il livello scende, come la lancetta trascinata di un fonometro analogico.
 */
@Composable
fun LevelGauge(
  levelDb: Double,
  maxDb: Double,
  unit: String,
  caption: String,
  palette: LevelPalette,
  modifier: Modifier = Modifier,
) {
  val target = if (levelDb.isNaN()) GaugeMin else levelDb.toFloat().coerceIn(GaugeMin, GaugeMax)
  val animated by animateFloatAsState(target, animationSpec = FluidMotion.snappy(), label = "gauge")
  val trackAlpha = 0.22f
  val markerColor = MaterialTheme.colorScheme.onSurface
  val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
  val measurer = rememberTextMeasurer()
  val description = "${formatDb(levelDb)} $unit"

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(1.3f)
      .semantics { contentDescription = description },
  ) {
    val strokeDp = 16.dp
    Canvas(Modifier.fillMaxSize()) {
      val stroke = strokeDp.toPx()
      val radius = min((size.width - stroke) / 2f, (size.height - stroke) / 1.5f)
      val center = Offset(size.width / 2f, stroke / 2f + radius)
      val arcSize = Size(radius * 2, radius * 2)
      val topLeft = Offset(center.x - radius, center.y - radius)

      fun arc(from: Float, to: Float, color: androidx.compose.ui.graphics.Color, cap: StrokeCap) {
        val a = angleFor(from)
        drawArc(color, a, angleFor(to) - a, false, topLeft, arcSize, style = Stroke(stroke, cap = cap))
      }

      arc(GaugeMin, CautionDb.toFloat(), palette.safe.copy(alpha = trackAlpha), StrokeCap.Butt)
      arc(CautionDb.toFloat(), DangerDb.toFloat(), palette.caution.copy(alpha = trackAlpha), StrokeCap.Butt)
      arc(DangerDb.toFloat(), GaugeMax, palette.danger.copy(alpha = trackAlpha), StrokeCap.Butt)
      if (!levelDb.isNaN()) arc(GaugeMin, animated.coerceAtLeast(GaugeMin + 0.5f), palette.colorFor(animated.toDouble()), StrokeCap.Round)

      if (!maxDb.isNaN()) {
        val angle = Math.toRadians(angleFor(maxDb.toFloat().coerceIn(GaugeMin, GaugeMax)).toDouble())
        val inner = radius - stroke * 0.9f
        val outer = radius + stroke * 0.9f
        drawLine(
          color = markerColor,
          start = center + Offset((cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat()),
          end = center + Offset((cos(angle) * outer).toFloat(), (sin(angle) * outer).toFloat()),
          strokeWidth = 2.5.dp.toPx(),
          cap = StrokeCap.Round,
        )
      }

      drawScaleLabels(center, radius - stroke * 1.9f, measurer, labelStyle)
    }

    // Il centro dell'arco sta sotto il centro del riquadro di (h − tratto) / 6: il testo ci va sopra.
    val shift = (maxHeight - strokeDp) / 6
    Column(
      modifier = Modifier
        .align(Alignment.Center)
        .offset(y = shift),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = formatDb(levelDb),
        style = FluidTextStyles.largeNumeric,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = unit,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = caption,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun angleFor(db: Float): Float = StartAngle + (db - GaugeMin) / (GaugeMax - GaugeMin) * SweepAngle

private fun DrawScope.drawScaleLabels(
  center: Offset,
  radius: Float,
  measurer: androidx.compose.ui.text.TextMeasurer,
  style: androidx.compose.ui.text.TextStyle,
) {
  var db = GaugeMin.toInt()
  while (db <= GaugeMax.toInt()) {
    val angle = Math.toRadians(angleFor(db.toFloat()).toDouble())
    val layout = measurer.measure(db.toString(), style)
    val x = center.x + (cos(angle) * radius).toFloat() - layout.size.width / 2f
    val y = center.y + (sin(angle) * radius).toFloat() - layout.size.height / 2f
    drawText(layout, topLeft = Offset(x, y))
    db += 20
  }
}
