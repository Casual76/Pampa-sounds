package dev.pampa.pampasounds.ui.settings

import dev.pampa.pampasounds.dsp.dbfsToSpl
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidPressGrip
import dev.antigravity.fluidengine.ui.fluid.FluidPromptAlert
import dev.antigravity.fluidengine.ui.fluid.FluidRowValue
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.fluid.fluidUpdateSection
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.pampa.pampasounds.AppContainer
import dev.pampa.pampasounds.BuildConfig
import dev.pampa.pampasounds.dsp.Calibration
import dev.pampa.pampasounds.dsp.CaptureSource
import dev.pampa.pampasounds.dsp.FrequencyWeighting
import dev.pampa.pampasounds.dsp.TimeWeighting
import dev.pampa.pampasounds.settings.MeterPreferences
import dev.pampa.pampasounds.ui.format.formatDb
import dev.pampa.pampasounds.ui.format.label
import dev.pampa.pampasounds.ui.format.shortLabel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Il tema come lo sceglie una persona: tre voci, non le quattro dell'engine (AMOLED e' un dettaglio). */
private enum class ThemeChoice(val label: String, val mode: ThemeMode) {
  System("Sistema", ThemeMode.SYSTEM),
  Light("Chiaro", ThemeMode.LIGHT),
  Dark("Scuro", ThemeMode.DARK),
}

@Composable
fun SettingsScreen(container: AppContainer, bottomInset: Dp) {
  val prefs by container.meterSettings.preferences.collectAsStateWithLifecycle(initialValue = MeterPreferences())
  val engineSettings by container.engineSettings.settings.collectAsStateWithLifecycle(initialValue = EngineSettings())
  val meter by container.meterEngine.state.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  var calibrating by remember { mutableStateOf(false) }

  // Il cursore si muove fluido sotto il dito e si salva quando lo si lascia: scrivere il DataStore a
  // ogni pixel sarebbe una scrittura su disco a ogni fotogramma.
  var draftTrim by remember { mutableStateOf<Float?>(null) }
  val trim = draftTrim?.toDouble() ?: prefs.trimDb
  val source = meter.source
  val trimLabel = "${if (trim > 0) "+" else ""}${formatDb(trim)} dB"

  FluidScreen(
    title = "Impostazioni",
    subtitle = "Calibrazione, misura e aspetto",
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Tertiary, motif = FluidHeroMotif.Glow) },
  ) {
    item(key = "calibration-header") { FluidSectionHeader(title = "Calibrazione", detail = "ritocco sopra la taratura di fabbrica") }
    item(key = "calibration") {
      FluidCard(glass = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = trimLabel,
            style = FluidTextStyles.numeric,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = if (trim == 0.0) "nessun ritocco" else if (trim > 0) "l'app legge più alto" else "l'app legge più basso",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        FluidSlider(
          value = trim.toFloat(),
          onValueChange = { draftTrim = (it * 10f).roundToInt() / 10f },
          valueRange = -20f..20f,
          // «Nessun ritocco» e' una tacca (e un colpo aptico): si ritrova senza guardare. Piccola, 0,4 dB, perche'
          // i ritocchi fini attorno allo zero restano della pista e dei tasti.
          snapValues = listOf(0f),
          snapThreshold = 0.01f,
          contentDescription = "Ritocco di calibrazione",
          stateDescription = if (trim == 0.0) "nessun ritocco" else trimLabel,
          onValueChangeFinished = {
            val value = draftTrim ?: return@FluidSlider
            scope.launch {
              container.meterSettings.setTrim(value.toDouble())
              draftTrim = null
            }
          },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          for (step in listOf(-1.0, -0.1, 0.1, 1.0)) {
            FluidButton(
              text = (if (step > 0) "+" else "−") + formatDb(kotlin.math.abs(step)),
              onClick = { scope.launch { container.meterSettings.setTrim(prefs.trimDb + step) } },
              style = FluidButtonStyle.Tinted,
              size = FluidButtonSize.Small,
              modifier = Modifier.weight(1f),
              // Quattro tasti affiancati sotto lo slider, in una pagina che scorre: la pagina non deve restare senza dito.
              grip = FluidPressGrip.YieldsToScroll,
            )
          }
        }
      }
    }
    item(key = "calibration-actions") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Calibra con un fonometro",
          subtitle = if (meter.isMeasuring) "Scrivi quello che legge il fonometro adesso" else "Avvia una misura, poi torna qui",
          onClick = if (meter.isMeasuring) ({ calibrating = true }) else null,
        )
        FluidListDivider()
        FluidListRow(
          title = "Azzera il ritocco",
          subtitle = "Torna alla taratura prevista dalle specifiche Android",
          onClick = { scope.launch { container.meterSettings.setTrim(0.0) } },
          disclosure = false,
        )
        FluidListDivider()
        FluidListRow(
          title = "Microfono in uso",
          subtitle = source?.label() ?: "Si vede alla prima misura",
          badge = source?.let { { FluidRowValue("${formatDb(Calibration.baseOffsetDb(it))} dB") } },
        )
      }
    }
    item(key = "calibration-note") {
      FluidSectionFootnote(
        "La taratura di base viene dalle specifiche di compatibilità Android: con il microfono senza " +
          "elaborazioni 94 dB SPL leggono −36 dBFS (base ${formatDb(Calibration.UNPROCESSED_BASE_DB)} dB), con il " +
          "percorso vocale 90 dB SPL leggono −22,4 dBFS (base ${formatDb(Calibration.baseOffsetDb(CaptureSource.VoiceRecognition))} dB). " +
          "Ogni telefono sbaglia di qualche decibel: il ritocco lo corregge una volta per tutte.",
      )
    }

    item(key = "meter-header") { FluidSectionHeader(title = "Misura") }
    item(key = "meter") {
      FluidCard(glass = true) {
        Text("Pesatura in frequenza", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        FluidSegmentedControl(
          options = FrequencyWeighting.entries,
          selected = prefs.weighting,
          onSelect = { scope.launch { container.meterSettings.setWeighting(it) } },
          modifier = Modifier.fillMaxWidth(),
          label = { it.shortLabel() },
        )
        Text(
          "A come l'orecchio (leggi e dose), C per i bassi, Z senza filtri.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Costante di tempo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        FluidSegmentedControl(
          options = TimeWeighting.entries,
          selected = prefs.timeWeighting,
          onSelect = { scope.launch { container.meterSettings.setTimeWeighting(it) } },
          modifier = Modifier.fillMaxWidth(),
          label = { it.label() },
        )
        Text(
          "Fast (125 ms) segue voci e colpi, Slow (1 s) legge un rumore che oscilla senza far ballare la cifra.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    item(key = "meter-switches") {
      FluidListGroup(glass = true) {
        FluidToggleRow(
          title = "Schermo acceso durante la misura",
          subtitle = "Con lo schermo spento la misura continua comunque",
          checked = prefs.keepScreenOn,
          onCheckedChange = { scope.launch { container.meterSettings.setKeepScreenOn(it) } },
        )
      }
    }

    item(key = "appearance-header") { FluidSectionHeader(title = "Aspetto") }
    item(key = "appearance") {
      FluidCard(glass = true) {
        val choice = ThemeChoice.entries.firstOrNull { it.mode == engineSettings.themeMode } ?: ThemeChoice.Dark
        FluidSegmentedControl(
          options = ThemeChoice.entries,
          selected = choice,
          onSelect = { scope.launch { container.engineSettings.setThemeMode(it.mode) } },
          modifier = Modifier.fillMaxWidth(),
          label = { it.label },
        )
      }
    }
    item(key = "appearance-switches") {
      FluidListGroup(glass = true) {
        FluidToggleRow(
          title = "Vibrazioni",
          subtitle = "Un tocco sotto il dito all'avvio e allo stop",
          checked = engineSettings.hapticsEnabled,
          onCheckedChange = { scope.launch { container.engineSettings.setHapticsEnabled(it) } },
        )
      }
    }

    val update = container.updateController
    fluidUpdateSection(
      state = update.state,
      onCheck = update::check,
      onInstall = update::install,
      currentVersion = BuildConfig.VERSION_NAME,
      lastCheckedAtMillis = update.lastCheckedAtMillis,
      glass = true,
    )

    item(key = "about-header") { FluidSectionHeader(title = "Informazioni") }
    item(key = "about") {
      FluidListGroup(glass = true) {
        FluidListRow(title = "Pacchetto", subtitle = LocalContext.current.packageName)
      }
    }
    fluidLicensesSection()
  }

  if (calibrating) {
    CalibrationDialog(
      currentReading = meter.tick?.let { tick ->
        val w = prefs.weighting.ordinal
        dbfsToSpl(if (prefs.timeWeighting == TimeWeighting.Fast) tick.fastDb[w] else tick.slowDb[w], meter.offsetDb)
      },
      unit = "dB(${prefs.weighting.shortLabel()})",
      onDismiss = { calibrating = false },
      onConfirm = { measured, reference ->
        calibrating = false
        scope.launch {
          container.meterSettings.setTrim(Calibration.trimForReference(prefs.trimDb, measured, reference))
        }
      },
    )
  }
}

/**
 * La taratura contro un fonometro: si scrive quello che legge lui, e l'app sposta il ritocco della
 * differenza. La lettura dell'app si congela quando si apre la finestra, perche' e' quella che si
 * stava confrontando: aggiornarla mentre si scrive renderebbe il confronto una gara.
 */
@Composable
private fun CalibrationDialog(
  currentReading: Double?,
  unit: String,
  onDismiss: () -> Unit,
  onConfirm: (measured: Double, reference: Double) -> Unit,
) {
  val frozen = remember { currentReading }
  FluidPromptAlert(
    title = "Calibra con un fonometro",
    message = "Metti il telefono accanto al fonometro, con la stessa pesatura ($unit). L'app legge adesso ${formatDb(frozen)} $unit.",
    onDismissRequest = onDismiss,
    onConfirm = { text ->
      val reference = parseReference(text)
      if (frozen != null && reference != null) onConfirm(frozen, reference)
    },
    placeholder = "Valore del fonometro, es. 54,3",
    confirmLabel = "Calibra",
    validate = { text -> calibrationValidation(text, frozen) },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
  )
}
