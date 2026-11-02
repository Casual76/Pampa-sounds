package dev.pampa.pampasounds.ui.history

import dev.pampa.pampasounds.dsp.dbfsToSpl
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.fluidGlassGroups
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.pampa.pampasounds.AppContainer
import dev.pampa.pampasounds.data.SessionListItem
import dev.pampa.pampasounds.settings.MeterPreferences
import dev.pampa.pampasounds.ui.MeterController
import dev.pampa.pampasounds.ui.charts.LevelLineChart
import dev.pampa.pampasounds.ui.charts.TimeLabel
import dev.pampa.pampasounds.ui.common.DeleteSessionAlert
import dev.pampa.pampasounds.ui.format.formatDb
import dev.pampa.pampasounds.ui.format.formatDurationWords
import dev.pampa.pampasounds.ui.format.formatHourMinute
import dev.pampa.pampasounds.ui.format.formatSessionStart
import dev.pampa.pampasounds.ui.format.unitLabel
import dev.pampa.pampasounds.ui.theme.rememberLevelPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Quanta parte della misura in corso si guarda. */
enum class TraceRange(val label: String, val seconds: Int?) {
  Minute("1 min", 60),
  FiveMinutes("5 min", 300),
  Quarter("15 min", 900),
  Hour("1 h", 3600),
  All("Tutto", null),
}

/** Lo storico: la misura in corso (o l'ultima) e tutte le sessioni salvate, giorno per giorno. */
@Composable
fun HistoryScreen(
  container: AppContainer,
  meter: MeterController,
  bottomInset: Dp,
  onOpenSession: (Long) -> Unit,
) {
  val engine = container.meterEngine
  val state by engine.state.collectAsStateWithLifecycle()
  val prefs by container.meterSettings.preferences.collectAsStateWithLifecycle(initialValue = MeterPreferences())
  val sessions by container.sessions.observeFinished().collectAsStateWithLifecycle(initialValue = null)
  val palette = rememberLevelPalette()
  val scope = rememberCoroutineScope()
  var range by rememberSaveable { mutableStateOf(TraceRange.FiveMinutes) }
  var pendingDelete by remember { mutableStateOf<SessionListItem?>(null) }

  val trace = remember(state.traceSeconds, prefs.weighting, state.sessionId) { engine.trace.snapshot(prefs.weighting) }

  FluidScreen(
    title = "Storico",
    subtitle = sessions?.let { if (it.isEmpty()) "Le misure salvate" else "${it.size} ${if (it.size == 1) "sessione salvata" else "sessioni salvate"}" },
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.SecondaryToTertiary, motif = FluidHeroMotif.Ticks) },
  ) {
    if (trace.size >= 2) {
      item(key = "live-header") {
        FluidSectionHeader(
          title = if (state.isMeasuring) "Misura in corso" else "Ultima misura",
          detail = "Leq al secondo, ${prefs.weighting.unitLabel()}",
        )
      }
      item(key = "live") {
        FluidCard(glass = true) {
          FluidSegmentedControl(
            options = TraceRange.entries,
            selected = range,
            onSelect = { range = it },
            modifier = Modifier.fillMaxWidth(),
            label = { it.label },
          )
          val count = range.seconds?.coerceAtMost(trace.size) ?: trace.size
          val from = trace.size - count
          val offset = state.offsetDb
          fun slice(source: FloatArray) = FloatArray(count) { dbfsToSpl(source[from + it], offset) }
          val startMillis = state.startedAt + from * 1000L
          val endMillis = state.startedAt + trace.size * 1000L
          LevelLineChart(
            eq = slice(trace.eq),
            max = slice(trace.max),
            min = slice(trace.min),
            palette = palette,
            timeLabels = listOf(
              TimeLabel(0f, formatHourMinute(startMillis)),
              TimeLabel(1f, if (state.isMeasuring) "adesso" else formatHourMinute(endMillis)),
            ),
          )
          Text(
            text = "La linea è il Leq di ogni secondo, la fascia va dal minimo al massimo Fast. Tratteggi a 70 e 85 dB.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    val list = sessions
    when {
      list == null -> Unit
      list.isEmpty() -> item(key = "empty") {
        FluidCard(glass = true) {
          FluidEmptyState(
            title = "Nessuna sessione",
            detail = "Ogni misura che fermi finisce qui, con il grafico, le statistiche e il CSV da esportare.",
          )
          // L'avvio ha il suo colpo (`ListenStart`, da `MeterController`): il tasto non ne suona altri tre.
          if (!state.isMeasuring) FluidButton(text = "Avvia la misura", onClick = meter.start, fillWidth = true, haptic = null)
        }
      }
      else -> {
        // Un gruppo per giorno. `fluidGlassGroups` tiene il vetro sul pannello e non sulla riga, e spezza
        // un giorno lungo in pannelli da otto righe senza che il taglio si veda.
        val byDay = list.groupBy { dayOf(it.session.startedAt) }
        byDay.forEach { (day, items) ->
          item(key = "day-$day") { FluidSectionHeader(title = dayTitle(day)) }
          fluidGlassGroups(items, key = "group-$day") { entry ->
            val offset = entry.session.offsetDb
            val leq = entry.leqA?.let { dbfsToSpl(it, offset) }?.takeUnless { it.isNaN() }
            val max = entry.maxA?.let { dbfsToSpl(it, offset) }?.takeUnless { it.isNaN() }
            FluidListRow(
              title = entry.session.name ?: formatSessionStart(entry.session.startedAt),
              subtitle = "${formatDurationWords(entry.session.durationSeconds)} · Leq ${formatDb(leq)} dB(A)",
              meta = "Massimo ${formatDb(max)} dB(A)" + (entry.session.doseNiosh?.takeIf { it >= 1 }?.let { " · dose ${it.toInt()} %" } ?: ""),
              tone = leq?.let { palette.toneFor(it) } ?: FluidTone.Neutral,
              leading = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
              onClick = { onOpenSession(entry.session.id) },
              contextActions = {
                listOf(
                  FluidContextAction("Elimina", Icons.Rounded.Delete, destructive = true) { pendingDelete = entry },
                )
              },
            )
          }
        }
      }
    }
  }

  pendingDelete?.let { item ->
    DeleteSessionAlert(
      message = "${item.session.name ?: formatSessionStart(item.session.startedAt)}: il grafico e i dati spariscono per sempre.",
      onDismiss = { pendingDelete = null },
      onConfirm = {
        pendingDelete = null
        scope.launch { container.sessions.delete(item.session.id) }
      },
    )
  }
}

private fun dayOf(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

private val DayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALY)

private fun dayTitle(day: LocalDate): String {
  val today = LocalDate.now()
  return when (day) {
    today -> "Oggi"
    today.minusDays(1) -> "Ieri"
    else -> DayTitle.format(day).replaceFirstChar { it.titlecase(Locale.ITALY) }
  }
}
