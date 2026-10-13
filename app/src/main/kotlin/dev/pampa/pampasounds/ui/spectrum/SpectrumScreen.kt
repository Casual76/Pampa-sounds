package dev.pampa.pampasounds.ui.spectrum

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidRowValue
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.pampa.pampasounds.AppContainer
import dev.pampa.pampasounds.dsp.Pitch
import dev.pampa.pampasounds.dsp.ThirdOctave
import dev.pampa.pampasounds.settings.MeterPreferences
import dev.pampa.pampasounds.settings.SpectrumMode
import dev.pampa.pampasounds.ui.MeterController
import dev.pampa.pampasounds.ui.charts.BandChart
import dev.pampa.pampasounds.ui.charts.SpectrogramView
import dev.pampa.pampasounds.ui.charts.SpectrumChart
import dev.pampa.pampasounds.ui.charts.binWeights
import dev.pampa.pampasounds.ui.charts.spectrumTop
import dev.pampa.pampasounds.ui.format.formatDb
import dev.pampa.pampasounds.ui.format.formatFrequency
import dev.pampa.pampasounds.ui.format.label
import dev.pampa.pampasounds.ui.format.unitLabel
import dev.pampa.pampasounds.ui.theme.rememberLevelPalette
import kotlinx.coroutines.launch
import kotlin.math.max

/** Quanto scende il picco trattenuto: 12 dB al secondo, abbastanza lento da leggerlo. */
private const val PeakDecayDbPerSecond = 12f

/**
 * Lo spettro: di che cosa e' fatto il rumore.
 *
 * Curva fine o bande a terzi d'ottava, con il picco trattenuto; sotto, la frequenza dominante con la
 * sua nota (un ronzio a 50 Hz e' la rete elettrica, un fischio a 15,6 kHz un vecchio televisore) e
 * la cascata degli ultimi venti secondi.
 */
@Composable
fun SpectrumScreen(container: AppContainer, meter: MeterController, bottomInset: Dp) {
  val engine = container.meterEngine
  val state by engine.state.collectAsStateWithLifecycle()
  val frame by engine.spectrum.collectAsStateWithLifecycle()
  val prefs by container.meterSettings.preferences.collectAsStateWithLifecycle(initialValue = MeterPreferences())
  val scope = rememberCoroutineScope()
  val palette = rememberLevelPalette()
  val weighting = prefs.weighting
  val offset = state.offsetDb.toFloat()

  // Il picco trattenuto e la cima della scala vivono qui: sono un modo di guardare, non una misura.
  var linePeak by remember { mutableStateOf<FloatArray?>(null) }
  var bandPeak by remember { mutableStateOf<FloatArray?>(null) }
  var top by remember { mutableFloatStateOf(80f) }
  var frames by remember { mutableLongStateOf(0L) }

  val current = frame
  val weights = remember(current?.fftSize, current?.sampleRate, weighting) {
    current?.let { binWeights(weighting, it.sampleRate, it.fftSize) }
  }
  val bandWeights = remember(weighting) {
    FloatArray(ThirdOctave.COUNT) { weighting.gainDb(ThirdOctave.centers[it]).toFloat() }
  }
  val lineLevels = remember(current, weights, offset) {
    if (current == null || weights == null) null
    else FloatArray(current.lineDb.size) { current.lineDb[it] + weights[it] + offset }
  }
  val bandLevels = remember(current, bandWeights, offset) {
    current?.let { f -> FloatArray(ThirdOctave.COUNT) { f.bandDb[it] + bandWeights[it] + offset } }
  }

  LaunchedEffect(lineLevels, bandLevels) {
    val decay = PeakDecayDbPerSecond * (current?.let { 2048f / it.sampleRate } ?: 0f)
    linePeak = hold(linePeak, lineLevels, decay)
    bandPeak = hold(bandPeak, bandLevels, decay)
    // La cima segue il picco trattenuto, non il fotogramma: la scala non salta a ogni colpo.
    val reference = if (prefs.spectrumMode == SpectrumMode.Bands) bandPeak else linePeak
    reference?.let { peaks -> top = spectrumTop(peaks.maxOrNull() ?: top) }
    frames++
  }
  // Pesatura o calibrazione cambiate: i massimi trattenuti appartengono a un'altra scala.
  LaunchedEffect(weighting, offset) {
    linePeak = null
    bandPeak = null
  }

  val dominant = remember(current) {
    current?.let { f -> Pitch.dominant(f.lineDb, f.binHz) }
  }
  val dominantNote = dominant?.let { Pitch.note(it.frequencyHz) }

  FluidScreen(
    title = "Spettro",
    subtitle = dominant?.let { "Domina ${formatFrequency(it.frequencyHz)}" } ?: "Di che frequenze è fatto il rumore",
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.PrimaryToSecondary, motif = FluidHeroMotif.Bars) },
  ) {
    if (current == null) {
      item(key = "empty") {
        FluidCard(glass = true) {
          FluidEmptyState(
            title = if (state.isMeasuring) "Un attimo…" else "Nessuno spettro",
            detail = if (state.isMeasuring) "Il primo spettro arriva dopo un quinto di secondo." else "Avvia una misura per vedere le frequenze della stanza in tempo reale.",
          )
          if (!state.isMeasuring) FluidButton(text = "Avvia la misura", onClick = meter.start, fillWidth = true)
        }
      }
      return@FluidScreen
    }

    item(key = "chart") {
      FluidCard(glass = true) {
        FluidSegmentedControl(
          options = SpectrumMode.entries,
          selected = prefs.spectrumMode,
          onSelect = { scope.launch { container.meterSettings.setSpectrumMode(it) } },
          modifier = Modifier.fillMaxWidth(),
          label = { if (it == SpectrumMode.Lines) "Curva" else "Bande 1/3 d'ottava" },
        )
        if (prefs.spectrumMode == SpectrumMode.Lines && lineLevels != null) {
          SpectrumChart(
            levels = lineLevels,
            binHz = current.binHz,
            topDb = top,
            peakHold = if (prefs.peakHold) linePeak else null,
            markerHz = dominant?.frequencyHz,
          )
        } else if (bandLevels != null) {
          BandChart(levels = bandLevels, topDb = top, peakHold = if (prefs.peakHold) bandPeak else null)
        }
        Text(
          text = "${weighting.unitLabel()} · ${if (state.isMeasuring) "dal vivo" else "ultimo spettro misurato"}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item(key = "facts") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Frequenza dominante",
          subtitle = "Il picco più alto dello spettro",
          badge = { FluidRowValue(dominant?.let { formatFrequency(it.frequencyHz) } ?: "—") },
        )
        FluidListDivider()
        FluidListRow(
          title = "Nota",
          subtitle = "La nota temperata più vicina (La4 = 440 Hz)",
          badge = { FluidRowValue(dominantNote?.label() ?: "—") },
        )
        FluidListDivider()
        FluidListRow(
          title = "Livello del picco",
          subtitle = "Nel suo bin, ${weighting.unitLabel()}",
          badge = {
            val level = dominant?.let { it.levelDb + weighting.gainDb(it.frequencyHz) + offset }
            FluidRowValue(formatDb(level))
          },
        )
        FluidListDivider()
        FluidListRow(
          title = "Trattieni i picchi",
          subtitle = "Una linea sottile segna i massimi recenti",
          onClick = { scope.launch { container.meterSettings.setPeakHold(!prefs.peakHold) } },
          disclosure = false,
          badge = { FluidSwitch(checked = prefs.peakHold, onCheckedChange = null) },
        )
      }
    }

    val history = engine.spectrogram
    if (history != null) {
      item(key = "spectrogram-header") { FluidSectionHeader(title = "Spettrogramma", detail = "gli ultimi 20 secondi") }
      item(key = "spectrogram") {
        FluidCard(glass = true) {
          SpectrogramView(
            history = history,
            frameCount = { frames },
            weighting = weighting,
            offsetDb = state.offsetDb,
            topDb = { top },
            palette = palette,
          )
          Text(
            text = "Il tempo scorre verso sinistra, le frequenze salgono dal basso. Righe fisse sono ronzii, scale che si muovono sono voci e musica.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    item(key = "footnote") {
      FluidSectionFootnote(
        "Bande a terzi d'ottava secondo IEC 61260, FFT da ${current.fftSize} punti a ${current.sampleRate / 1000} kHz " +
          "(risoluzione ${formatFrequency(current.binHz)}). Sotto gli 80 Hz il microfono di un telefono sente poco: " +
          "i bassi profondi sono sottostimati.",
      )
    }
  }
}

/** Il nuovo massimo trattenuto: il piu' alto fra il valore di adesso e il vecchio che scende. */
private fun hold(previous: FloatArray?, current: FloatArray?, decay: Float): FloatArray? {
  if (current == null) return previous
  if (previous == null || previous.size != current.size) return current.copyOf()
  return FloatArray(current.size) { max(current[it], previous[it] - decay) }
}
