package dev.pampa.pampasounds.ui.live

import dev.pampa.pampasounds.dsp.dbfsToSpl
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidRowValue
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidMetricTile
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.pampa.pampasounds.AppContainer
import dev.pampa.pampasounds.audio.CaptureFailure
import dev.pampa.pampasounds.audio.MeterState
import dev.pampa.pampasounds.audio.MeterStatus
import dev.pampa.pampasounds.dsp.DoseCriterion
import dev.pampa.pampasounds.dsp.FrequencyWeighting
import dev.pampa.pampasounds.dsp.NoiseDose
import dev.pampa.pampasounds.dsp.TICKS_PER_SECOND
import dev.pampa.pampasounds.dsp.TimeWeighting
import dev.pampa.pampasounds.settings.MeterPreferences
import dev.pampa.pampasounds.ui.MeterController
import dev.pampa.pampasounds.ui.MicPermissionProblem
import dev.pampa.pampasounds.ui.SoundsTab
import dev.pampa.pampasounds.ui.charts.LevelGauge
import dev.pampa.pampasounds.ui.charts.LevelLineChart
import dev.pampa.pampasounds.ui.charts.TimeLabel
import dev.pampa.pampasounds.ui.format.ReferenceLevels
import dev.pampa.pampasounds.ui.format.formatDb
import dev.pampa.pampasounds.ui.format.formatDuration
import dev.pampa.pampasounds.ui.format.formatPercent
import dev.pampa.pampasounds.ui.format.label
import dev.pampa.pampasounds.ui.format.referenceFor
import dev.pampa.pampasounds.ui.format.shortLabel
import dev.pampa.pampasounds.ui.format.unitLabel
import dev.pampa.pampasounds.ui.theme.LevelPalette
import dev.pampa.pampasounds.ui.theme.rememberLevelPalette
import kotlinx.coroutines.launch

/**
 * La scheda principale: quanto rumore c'e' adesso, e com'e' andata da quando si misura.
 *
 * Tutto quello che si legge e' in dB SPL: il motore da' dBFS e questa schermata somma l'offset di
 * calibrazione, che puo' cambiare anche a misura in corso.
 */
@Composable
fun LiveScreen(
  container: AppContainer,
  meter: MeterController,
  bottomInset: Dp,
  onOpenTab: (SoundsTab) -> Unit,
) {
  val state by container.meterEngine.state.collectAsStateWithLifecycle()
  val prefs by container.meterSettings.preferences.collectAsStateWithLifecycle(initialValue = MeterPreferences())
  val scope = rememberCoroutineScope()
  val palette = rememberLevelPalette()
  val w = prefs.weighting.ordinal
  val t = prefs.timeWeighting.ordinal
  val offset = state.offsetDb

  val now = state.tick?.let { dbfsToSpl(if (prefs.timeWeighting == TimeWeighting.Fast) it.fastDb[w] else it.slowDb[w], offset) } ?: Double.NaN
  val summary = state.summary
  val leq = summary?.leqDb?.get(w)?.let { dbfsToSpl(it, offset) } ?: Double.NaN
  val max = summary?.maxDb?.get(w)?.get(t)?.let { dbfsToSpl(it, offset) } ?: Double.NaN
  val min = summary?.minDb?.get(w)?.get(t)?.let { dbfsToSpl(it, offset) } ?: Double.NaN
  val peak = summary?.peakDb?.let { dbfsToSpl(it, offset) } ?: Double.NaN

  FluidScreen(
    title = "Misura",
    subtitle = subtitleFor(state),
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Ripples) },
  ) {
    item(key = "gauge") {
      FluidCard(glass = true) {
        LevelGauge(
          levelDb = now,
          maxDb = max,
          unit = prefs.weighting.unitLabel(),
          caption = when {
            !now.isNaN() -> referenceFor(now).label
            state.isMeasuring -> "Nessun segnale"
            else -> "Tocca Avvia per misurare"
          },
          palette = palette,
        )
        FluidSegmentedControl(
          options = FrequencyWeighting.entries,
          selected = prefs.weighting,
          onSelect = { scope.launch { container.meterSettings.setWeighting(it) } },
          modifier = Modifier.fillMaxWidth(),
          label = { "Pesatura ${it.shortLabel()}" },
        )
        FluidSegmentedControl(
          options = TimeWeighting.entries,
          selected = prefs.timeWeighting,
          onSelect = { scope.launch { container.meterSettings.setTimeWeighting(it) } },
          modifier = Modifier.fillMaxWidth(),
          label = { it.label() },
        )
      }
    }

    item(key = "control") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isMeasuring) {
          FluidButton(
            text = "Ferma e salva",
            onClick = meter.stop,
            style = FluidButtonStyle.Destructive,
            size = FluidButtonSize.Large,
            fillWidth = true,
            leading = { Icon(Icons.Rounded.Stop, contentDescription = null) },
          )
        } else {
          FluidButton(
            text = if (state.status == MeterStatus.Idle) "Avvia la misura" else "Nuova misura",
            onClick = meter.start,
            size = FluidButtonSize.Large,
            fillWidth = true,
            leading = { Icon(Icons.Rounded.Mic, contentDescription = null) },
          )
        }
        Text(
          text = controlCaption(state),
          modifier = Modifier.fillMaxWidth(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }

    noticeItem(state, meter)

    item(key = "stats-header") {
      FluidSectionHeader(title = "Statistiche", detail = if (summary == null) null else "Pesatura ${prefs.weighting.shortLabel()}, ${prefs.timeWeighting.label()}")
    }
    item(key = "stats") {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          FluidMetricTile(label = "Leq", value = formatDb(leq), detail = "media energetica", modifier = Modifier.weight(1f), tone = tileTone(palette, leq), glass = true)
          FluidMetricTile(label = "Massimo", value = formatDb(max), detail = prefs.timeWeighting.label(), modifier = Modifier.weight(1f), tone = tileTone(palette, max), glass = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          FluidMetricTile(label = "Minimo", value = formatDb(min), detail = prefs.timeWeighting.label(), modifier = Modifier.weight(1f), glass = true)
          FluidMetricTile(label = "Picco", value = formatDb(peak), detail = "non pesato", modifier = Modifier.weight(1f), glass = true)
        }
      }
    }

    item(key = "recent-header") { FluidSectionHeader(title = "Ultimi 30 secondi", detail = prefs.timeWeighting.label()) }
    item(key = "recent") {
      FluidCard(glass = true, onClick = { onOpenTab(SoundsTab.History) }, animatePress = false) {
        val recent = state.recent
        if (recent.size < 2) {
          Text(
            text = "Il grafico si riempie mentre misuri.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          val values = FloatArray(recent.size) { i ->
            val tick = recent[i]
            dbfsToSpl(if (prefs.timeWeighting == TimeWeighting.Fast) tick.fastDb[w] else tick.slowDb[w], offset).toFloat()
          }
          val seconds = recent.size / TICKS_PER_SECOND
          LevelLineChart(
            eq = values,
            palette = palette,
            height = 140.dp,
            timeLabels = listOf(TimeLabel(0f, "−${seconds} s"), TimeLabel(1f, "adesso")),
          )
        }
      }
    }

    item(key = "exposure-header") { FluidSectionHeader(title = "Esposizione", detail = "su 8 ore, pesatura A") }
    item(key = "exposure") { ExposureGroup(state, palette) }

    item(key = "percentiles-header") { FluidSectionHeader(title = "Livelli statistici", detail = "Fast, ${prefs.weighting.shortLabel()}") }
    item(key = "percentiles") {
      FluidListGroup(glass = true) {
        StatRow("L10", "Superato il 10 % del tempo: i rumori ricorrenti", summary?.l10Db?.get(w)?.let { dbfsToSpl(it, offset) })
        FluidListDivider()
        StatRow("L50", "La mediana: il livello tipico", summary?.l50Db?.get(w)?.let { dbfsToSpl(it, offset) })
        FluidListDivider()
        StatRow("L90", "Superato il 90 % del tempo: il rumore di fondo", summary?.l90Db?.get(w)?.let { dbfsToSpl(it, offset) })
      }
    }

    item(key = "reference-header") { FluidSectionHeader(title = "Livelli di riferimento", detail = "dB(A), indicativi") }
    item(key = "reference") { ReferenceGroup(current = if (prefs.weighting == FrequencyWeighting.A) now else Double.NaN) }
    item(key = "footnote") {
      FluidSectionFootnote(
        "Il microfono di un telefono non è un fonometro certificato: i valori sono stime, di solito " +
          "entro qualche decibel. Per letture più fedeli taralo in Impostazioni contro un fonometro vero. " +
          "L'OMS consiglia meno di 30 dB(A) in camera da letto, di notte.",
      )
    }
  }
}

private fun androidx.compose.foundation.lazy.LazyListScope.noticeItem(state: MeterState, meter: MeterController) {
  val problem = meter.problem
  val message: Triple<String, String, FluidTone>? = when {
    problem == MicPermissionProblem.Blocked -> Triple(
      "Microfono bloccato",
      "Il permesso del microfono è stato negato. Riattivalo dalle impostazioni di sistema dell'app.",
      FluidTone.Danger,
    )
    problem == MicPermissionProblem.Denied -> Triple(
      "Serve il microfono",
      "Senza il permesso del microfono l'app non può misurare niente. Tocca di nuovo Avvia per concederlo.",
      FluidTone.Warning,
    )
    state.silenced -> Triple(
      "Microfono occupato",
      "Un'altra app (una chiamata, un registratore) sta usando il microfono: per ora arriva solo silenzio.",
      FluidTone.Warning,
    )
    state.isMeasuring && state.digitalSilence -> Triple(
      "Nessun segnale dal microfono",
      "Arrivano solo zeri digitali: succede nell'emulatore, o quando il sistema non concede davvero il microfono. Quello che si misura adesso non è la stanza.",
      FluidTone.Warning,
    )
    state.status == MeterStatus.Failed -> Triple(
      "Misura interrotta",
      when (state.failure) {
        CaptureFailure.PermissionMissing -> "Manca il permesso del microfono."
        CaptureFailure.Lost -> "Il microfono si è chiuso a metà misura. Quello che era stato misurato è salvato."
        else -> "Non è stato possibile aprire il microfono. Riprova tra poco."
      },
      FluidTone.Danger,
    )
    else -> null
  }
  if (message != null) {
    item(key = "notice") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidInlineMessage(title = message.first, message = message.second, tone = message.third)
        if (problem == MicPermissionProblem.Blocked) {
          FluidButton(text = "Apri le impostazioni", onClick = meter.openAppSettings, style = FluidButtonStyle.Tinted, fillWidth = true)
        }
      }
    }
  }
}

@Composable
private fun ExposureGroup(state: MeterState, palette: LevelPalette) {
  val elapsed = state.traceSeconds.toDouble()
  val projected = NoiseDose.projected(state.doseNiosh, elapsed)
  val twa = NoiseDose.timeWeightedAverage(state.doseNiosh, DoseCriterion.Niosh)
  FluidListGroup(glass = true) {
    FluidListRow(
      title = "Dose NIOSH",
      subtitle = "85 dB(A) per 8 ore, ogni 3 dB in più il tempo si dimezza",
      badge = { FluidStatusBadge(label = formatPercent(state.doseNiosh), tone = doseTone(state.doseNiosh)) },
    )
    FluidListDivider()
    FluidListRow(
      title = "Dose OSHA",
      subtitle = "90 dB(A) per 8 ore, scambio 5 dB: il limite di legge USA",
      badge = { FluidStatusBadge(label = formatPercent(state.doseOsha), tone = doseTone(state.doseOsha)) },
    )
    FluidListDivider()
    FluidListRow(
      title = "Se continui così per 8 ore",
      subtitle = if (elapsed < 60) "Serve almeno un minuto di misura" else "Dose NIOSH proiettata${twa?.let { ", TWA ${formatDb(it)} dB(A)" } ?: ""}",
      badge = { FluidRowValue(if (elapsed < 60) "—" else formatPercent(projected)) },
    )
  }
}

@Composable
private fun StatRow(title: String, subtitle: String, value: Double?) {
  FluidListRow(title = title, subtitle = subtitle, badge = { FluidRowValue(formatDb(value)) })
}

@Composable
private fun ReferenceGroup(current: Double) {
  val active = if (current.isNaN()) null else referenceFor(current)
  FluidListGroup(glass = true) {
    ReferenceLevels.forEachIndexed { index, reference ->
      if (index > 0) FluidListDivider()
      FluidListRow(
        title = reference.label,
        subtitle = reference.example,
        selected = reference == active,
        badge = { FluidRowValue("${reference.db} dB") },
      )
    }
  }
}

private fun subtitleFor(state: MeterState): String = when (state.status) {
  MeterStatus.Measuring -> "In misura da ${formatDuration(state.traceSeconds.toLong())}"
  MeterStatus.Stopped -> "Misura ferma: i numeri sono quelli dell'ultima sessione"
  MeterStatus.Failed -> "La misura si è interrotta"
  MeterStatus.Idle -> "Il livello sonoro della stanza, in decibel"
}

private fun controlCaption(state: MeterState): String = when {
  state.isMeasuring -> "Continua anche a schermo spento. La sessione si salva nello Storico."
  else -> "La misura continua in background, con una notifica per fermarla."
}

private fun tileTone(palette: LevelPalette, db: Double): FluidTone =
  if (db.isNaN()) FluidTone.Neutral else palette.toneFor(db)

private fun doseTone(percent: Double): FluidTone = when {
  percent >= 100 -> FluidTone.Danger
  percent >= 50 -> FluidTone.Warning
  percent > 0 -> FluidTone.Info
  else -> FluidTone.Neutral
}
