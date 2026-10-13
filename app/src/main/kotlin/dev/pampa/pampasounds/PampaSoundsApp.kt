package dev.pampa.pampasounds

import android.app.Application
import android.content.Context
import dev.antigravity.fluidengine.foundation.AppUpdater
import dev.antigravity.fluidengine.storage.EngineSettingsStore
import dev.pampa.pampasounds.audio.MeterEngine
import dev.pampa.pampasounds.data.CsvExporter
import dev.pampa.pampasounds.data.SessionRepository
import dev.pampa.pampasounds.data.SoundsDatabase
import dev.pampa.pampasounds.settings.MeterSettings
import dev.pampa.pampasounds.update.soundsUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PampaSoundsApp : Application() {
  val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * Gli oggetti che vivono quanto il processo. Niente DI: sono sei, e si leggono tutti da qui.
 *
 * Il motore di misura sta qui e non nel servizio perche' la UI lo legge anche quando il servizio non
 * c'e' (i numeri dell'ultima misura restano a schermo), e il servizio lo comanda soltanto.
 */
class AppContainer(context: Context) {
  private val app = context.applicationContext

  /** Il processo intero: le scritture di fine misura non devono morire con una schermata. */
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  val engineSettings by lazy { EngineSettingsStore(app) }
  val meterSettings by lazy { MeterSettings(app) }
  private val database by lazy { SoundsDatabase.create(app) }
  val sessions by lazy { SessionRepository(database.sessions()) }
  val csvExporter by lazy { CsvExporter(app, sessions) }
  val meterEngine by lazy { MeterEngine(app, sessions, meterSettings, appScope) }
  val updater: AppUpdater by lazy { soundsUpdater(app) }
}

val Context.container: AppContainer get() = (applicationContext as PampaSoundsApp).container
