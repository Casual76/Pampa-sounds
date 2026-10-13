package dev.pampa.pampasounds.ui.session

import android.content.Intent
import dev.pampa.pampasounds.dsp.dbfsToSpl
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidLoadingBlock
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone
import dev.antigravity.fluidengine.ui.fluid.FluidRowValue
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidMetricTile
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.pampa.pampasounds.AppContainer
import dev.pampa.pampasounds.data.SampleEntity
import dev.pampa.pampasounds.data.SessionDetail
import dev.pampa.pampasounds.data.bands
import dev.pampa.pampasounds.data.levels
import dev.pampa.pampasounds.dsp.CaptureSource
import dev.pampa.pampasounds.dsp.DoseCriterion
import dev.pampa.pampasounds.dsp.FrequencyWeighting
import dev.pampa.pampasounds.dsp.NoiseDose
import dev.pampa.pampasounds.dsp.ThirdOctave
import dev.pampa.pampasounds.ui.charts.BandChart
import dev.pampa.pampasounds.ui.charts.LevelLineChart
import dev.pampa.pampasounds.ui.charts.TimeLabel
import dev.pampa.pampasounds.ui.charts.spectrumTop
import dev.pampa.pampasounds.ui.format.formatClock
import dev.pampa.pampasounds.ui.format.formatDb
import dev.pampa.pampasounds.ui.format.formatDuration
import dev.pampa.pampasounds.ui.format.formatHourMinute
import dev.pampa.pampasounds.ui.format.formatPercent
import dev.pampa.pampasounds.ui.format.formatSessionStart
import dev.pampa.pampasounds.ui.format.label
import dev.pampa.pampasounds.ui.format.shortLabel
import dev.pampa.pampasounds.ui.format.unitLabel
import dev.pampa.pampasounds.ui.theme.LevelPalette
import dev.pampa.pampasounds.ui.theme.rememberLevelPalette
import kotlinx.coroutines.launch

/** Una sessione salvata, per intero: grafico, numeri, dose, spettro medio, e il CSV. */
@Composable
fun SessionScreen(
  container: AppContainer,
  sessionId: Long,
  bottomInset: Dp,
  onBack: () -> Unit,
) {
  val detail by remember(sessionId) { container.sessions.observeDetail(sessionId) }
    .collectAsStateWithLifecycle(initialValue = null)
  val samples by produceState<List<SampleEntity>?>(initialValue = null, sessionId) {
    value = container.sessions.samples(sessionId)
  }
  val palette = rememberLevelPalette()
  val context = LocalContext.current
  val notifications = LocalFluidNotificationHostState.current
  val scope = rememberCoroutineScope()
  var weighting by rememberSaveable { mutableStateOf(FrequencyWeighting.A) }
  var renaming by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }

  val current = detail
  val title = current?.session?.let { it.name ?: formatSessionStart(it.startedAt) } ?: "Sessione"

  fun export() {
    val session = current?.session ?: return
    scope.launch {
      runCatching { container.csvExporter.export(session) }
        .onSuccess { uri ->
          val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Pampa Sounds — $title")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          context.startActivity(Intent.createChooser(send, "Esporta la sessione"))
        }
        .onFailure {
          notifications?.show(
            FluidNotification(id = "export-failed", title = "Esportazione non riuscita", message = it.message ?: "Errore sconosciuto", tone = FluidNotificationTone.Error),
          )
        }
    }
  }

  FluidScreen(
    title = title,
    subtitle = current?.session?.let { "${formatSessionStart(it.startedAt)} · ${formatDuration(it.durationSeconds.toLong())}" },
    onBack = onBack,
    extraBottomPadding = bottomInset,
    actions = {
      FluidBarAction(icon = Icons.Rounded.Share, contentDescription = "Esporta CSV", onClick = ::export, enabled = current != null)
      FluidBarAction(
        icon = Icons.Rounded.MoreHoriz,
        contentDescription = "Altro",
        onClick = {},
        enabled = current != null,
        actions = {
          listOf(
            FluidContextAction("Rinomina", Icons.Rounded.Edit) { renaming = true },
            FluidContextAction("Esporta CSV", Icons.Rounded.Share) { export() },
            FluidContextAction("Elimina", Icons.Rounded.Delete, destructive = true) { confirmDelete = true },
          )
        },
      )
    },
  ) {
    if (current == null) {
      item(key = "loading") { FluidLoadingBlock() }
      return@FluidScreen
    }
    val offset = current.session.offsetDb

    item(key = "weighting") {
      FluidSegmentedControl(
        options = FrequencyWeighting.entries,
        selected = weighting,
        onSelect = { weighting = it },
        modifier = Modifier.fillMaxWidth(),
        label = { "Pesatura ${it.shortLabel()}" },
      )
    }

    item(key = "chart") {
      FluidCard {
        val rows = samples
        if (rows == null || rows.size < 2) {
          FluidLoadingBlock(minHeight = 180.dp)
        } else {
          SessionChart(rows, weighting, offset, current.session.startedAt, palette)
        }
      }
    }

    item(key = "tiles") { StatTiles(current, weighting, offset, palette) }

    item(key = "stats-header") { FluidSectionHeader(title = "Livelli", detail = weighting.unitLabel()) }
    item(key = "stats") {
      val stats = current.stats[weighting]
      FluidListGroup {
        ValueRow("L10", "Superato il 10 % del tempo", stats?.l10?.let { dbfsToSpl(it, offset) }?.takeUnless { it.isNaN() })
        FluidListDivider()
        ValueRow("L50", "La mediana", stats?.l50?.let { dbfsToSpl(it, offset) }?.takeUnless { it.isNaN() })
        FluidListDivider()
        ValueRow("L90", "Il rumore di fondo", stats?.l90?.let { dbfsToSpl(it, offset) }?.takeUnless { it.isNaN() })
        FluidListDivider()
        ValueRow("Massimo Slow", "Costante di 1 secondo", stats?.maxSlow?.let { dbfsToSpl(it, offset) }?.takeUnless { it.isNaN() })
        FluidListDivider()
        ValueRow("Minimo Slow", "Costante di 1 secondo", stats?.minSlow?.let { dbfsToSpl(it, offset) }?.takeUnless { it.isNaN() })
      }
    }

    item(key = "dose-header") { FluidSectionHeader(title = "Esposizione", detail = "pesatura A") }
    item(key = "dose") { DoseGroup(current) }

    current.session.bands()?.let { bands ->
      item(key = "bands-header") { FluidSectionHeader(title = "Spettro medio", detail = "terzi d'ottava, ${weighting.unitLabel()}") }
      item(key = "bands") {
        FluidCard {
          val levels = FloatArray(ThirdOctave.COUNT) {
            (bands[it] + weighting.gainDb(ThirdOctave.centers[it]) + offset).toFloat()
          }
          BandChart(levels = levels, topDb = spectrumTop(levels.maxOrNull() ?: 60f))
        }
      }
    }

    item(key = "info-header") { FluidSectionHeader(title = "Dettagli") }
    item(key = "info") {
      val session = current.session
      FluidListGroup {
        ValueRow("Inizio", formatSessionStart(session.startedAt), null, formatClock(session.startedAt))
        FluidListDivider()
        ValueRow("Fine", session.endedAt?.let { formatSessionStart(it) } ?: "—", null, session.endedAt?.let { formatClock(it) } ?: "—")
        FluidListDivider()
        ValueRow(
          "Microfono",
          runCatching { CaptureSource.valueOf(session.source).label() }.getOrDefault(session.source),
          null,
          "${session.sampleRate / 1000.0} kHz".replace('.', ','),
        )
        FluidListDivider()
        ValueRow("Calibrazione", "Somma tra dBFS e dB SPL", null, "${formatDb(session.offsetDb)} dB")
      }
    }
    item(key = "footnote") {
      FluidSectionFootnote("Il CSV contiene un secondo per riga: Leq A, C e Z, massimo e minimo Fast, picco. Valori in dB SPL calibrati.")
    }
  }

  if (renaming && current != null) {
    var name by remember { mutableStateOf(current.session.name.orEmpty()) }
    FluidAlert(
      onDismissRequest = { renaming = false },
      title = "Rinomina",
      message = "Un nome aiuta a ritrovarla: «Camera, notte», «Ufficio con il condizionatore».",
      actions = listOf(
        FluidAlertAction("Annulla", onClick = { renaming = false }),
        FluidAlertAction(
          "Salva",
          onClick = {
            renaming = false
            scope.launch { container.sessions.rename(sessionId, name) }
          },
          emphasis = FluidAlertAction.Emphasis.Preferred,
        ),
      ),
    ) {
      FluidTextField(value = name, onValueChange = { name = it }, placeholder = formatSessionStart(current.session.startedAt))
    }
  }

  if (confirmDelete) {
    FluidAlert(
      onDismissRequest = { confirmDelete = false },
      title = "Eliminare la sessione?",
      message = "Il grafico e i dati di questa misura spariscono per sempre.",
      actions = listOf(
        FluidAlertAction("Annulla", onClick = { confirmDelete = false }),
        FluidAlertAction(
          "Elimina",
          onClick = {
            confirmDelete = false
            onBack()
            // Lo scope dell'app, non quello della schermata: la schermata sta per andarsene.
            container.appScope.launch { container.sessions.delete(sessionId) }
          },
          emphasis = FluidAlertAction.Emphasis.Destructive,
        ),
      ),
    )
  }
}

@Composable
private fun SessionChart(
  rows: List<SampleEntity>,
  weighting: FrequencyWeighting,
  offset: Double,
  startedAt: Long,
  palette: LevelPalette,
) {
  val series = remember(rows, weighting, offset) {
    val eq = FloatArray(rows.size)
    val max = FloatArray(rows.size)
    val min = FloatArray(rows.size)
    rows.forEachIndexed { i, sample ->
      val (e, hi, lo) = sample.levels(weighting)
      eq[i] = dbfsToSpl(e, offset)
      max[i] = hi?.let { dbfsToSpl(it, offset) } ?: Float.NaN
      min[i] = lo?.let { dbfsToSpl(it, offset) } ?: Float.NaN
    }
    Triple(eq, max, min)
  }
  val last = rows.last().second
  LevelLineChart(
    eq = series.first,
    max = series.second,
    min = series.third,
    palette = palette,
    height = 200.dp,
    timeLabels = listOf(
      TimeLabel(0f, formatHourMinute(startedAt)),
      TimeLabel(0.5f, formatHourMinute(startedAt + last * 500L)),
      TimeLabel(1f, formatHourMinute(startedAt + last * 1000L)),
    ),
  )
}

@Composable
private fun StatTiles(detail: SessionDetail, weighting: FrequencyWeighting, offset: Double, palette: LevelPalette) {
  val stats = detail.stats[weighting]
  val leq = stats?.leq?.let { dbfsToSpl(it, offset) }?.takeUnless { it.isNaN() }
  val max = stats?.maxFast?.let { dbfsToSpl(it, offset) }?.takeUnless { it.isNaN() }
  val min = stats?.minFast?.let { dbfsToSpl(it, offset) }?.takeUnless { it.isNaN() }
  val peak = detail.session.peakDbfs?.let { dbfsToSpl(it, offset) }?.takeUnless { it.isNaN() }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FluidMetricTile(label = "Leq", value = formatDb(leq), detail = weighting.unitLabel(), modifier = Modifier.weight(1f), tone = leq?.let { palette.toneFor(it) } ?: FluidTone.Neutral)
      FluidMetricTile(label = "Massimo", value = formatDb(max), detail = "Fast", modifier = Modifier.weight(1f), tone = max?.let { palette.toneFor(it) } ?: FluidTone.Neutral)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FluidMetricTile(label = "Minimo", value = formatDb(min), detail = "Fast", modifier = Modifier.weight(1f))
      FluidMetricTile(label = "Picco", value = formatDb(peak), detail = "non pesato", modifier = Modifier.weight(1f))
    }
  }
}

@Composable
private fun DoseGroup(detail: SessionDetail) {
  val niosh = detail.session.doseNiosh ?: 0.0
  val osha = detail.session.doseOsha ?: 0.0
  val duration = detail.session.durationSeconds
  FluidListGroup {
    FluidListRow(
      title = "Dose NIOSH",
      subtitle = "85 dB(A) per 8 ore, scambio 3 dB",
      badge = { FluidStatusBadge(label = formatPercent(niosh), tone = doseTone(niosh)) },
    )
    FluidListDivider()
    FluidListRow(
      title = "Dose OSHA",
      subtitle = "90 dB(A) per 8 ore, scambio 5 dB",
      badge = { FluidStatusBadge(label = formatPercent(osha), tone = doseTone(osha)) },
    )
    FluidListDivider()
    FluidListRow(
      title = "TWA su 8 ore (NIOSH)",
      subtitle = "Il livello costante che darebbe la stessa dose",
      badge = { FluidRowValue(NoiseDose.timeWeightedAverage(niosh, DoseCriterion.Niosh)?.let { "${formatDb(it)} dB(A)" } ?: "—") },
    )
    FluidListDivider()
    FluidListRow(
      title = "Proiezione su 8 ore",
      subtitle = "Se l'ambiente restasse com'era durante la misura",
      badge = { FluidRowValue(if (duration < 60) "—" else formatPercent(NoiseDose.projected(niosh, duration))) },
    )
  }
}

@Composable
private fun ValueRow(title: String, subtitle: String, value: Double?, text: String? = null) {
  FluidListRow(title = title, subtitle = subtitle, badge = { FluidRowValue(text ?: formatDb(value)) })
}

private fun doseTone(percent: Double): FluidTone = when {
  percent >= 100 -> FluidTone.Danger
  percent >= 50 -> FluidTone.Warning
  percent > 0 -> FluidTone.Info
  else -> FluidTone.Neutral
}
