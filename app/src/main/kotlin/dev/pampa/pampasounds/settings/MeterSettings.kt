package dev.pampa.pampasounds.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.pampa.pampasounds.dsp.Calibration
import dev.pampa.pampasounds.dsp.FrequencyWeighting
import dev.pampa.pampasounds.dsp.TimeWeighting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Come si mostra lo spettro: la curva fine o le trenta bande. */
enum class SpectrumMode { Lines, Bands }

/**
 * Le preferenze del fonometro.
 *
 * Stanno qui e non in `EngineSettings`: quello e' piccolo apposta e parla di tema e aptica, cose che
 * ogni app ha. Una calibrazione e' di quest'app sola.
 */
data class MeterPreferences(
  /** Il ritocco dell'utente sopra la base della CDD, in dB. */
  val trimDb: Double = 0.0,
  val weighting: FrequencyWeighting = FrequencyWeighting.A,
  val timeWeighting: TimeWeighting = TimeWeighting.Fast,
  val keepScreenOn: Boolean = true,
  val spectrumMode: SpectrumMode = SpectrumMode.Lines,
  val peakHold: Boolean = true,
)

private val Context.meterDataStore: DataStore<Preferences> by preferencesDataStore(name = "meter")

class MeterSettings(private val context: Context) {

  private object Keys {
    val trim = doublePreferencesKey("trim_db")
    val weighting = stringPreferencesKey("weighting")
    val timeWeighting = stringPreferencesKey("time_weighting")
    val keepScreenOn = booleanPreferencesKey("keep_screen_on")
    val spectrumMode = stringPreferencesKey("spectrum_mode")
    val peakHold = booleanPreferencesKey("peak_hold")
  }

  val preferences: Flow<MeterPreferences> = context.meterDataStore.data.map { p ->
    val defaults = MeterPreferences()
    MeterPreferences(
      trimDb = (p[Keys.trim] ?: defaults.trimDb).coerceIn(-Calibration.TRIM_RANGE_DB, Calibration.TRIM_RANGE_DB),
      weighting = enumOr(p[Keys.weighting], defaults.weighting),
      timeWeighting = enumOr(p[Keys.timeWeighting], defaults.timeWeighting),
      keepScreenOn = p[Keys.keepScreenOn] ?: defaults.keepScreenOn,
      spectrumMode = enumOr(p[Keys.spectrumMode], defaults.spectrumMode),
      peakHold = p[Keys.peakHold] ?: defaults.peakHold,
    )
  }

  suspend fun setTrim(db: Double) = edit {
    it[Keys.trim] = db.coerceIn(-Calibration.TRIM_RANGE_DB, Calibration.TRIM_RANGE_DB)
  }

  suspend fun setWeighting(value: FrequencyWeighting) = edit { it[Keys.weighting] = value.name }
  suspend fun setTimeWeighting(value: TimeWeighting) = edit { it[Keys.timeWeighting] = value.name }
  suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.keepScreenOn] = value }
  suspend fun setSpectrumMode(value: SpectrumMode) = edit { it[Keys.spectrumMode] = value.name }
  suspend fun setPeakHold(value: Boolean) = edit { it[Keys.peakHold] = value }

  private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
    context.meterDataStore.edit { block(it) }
  }

  private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
}
