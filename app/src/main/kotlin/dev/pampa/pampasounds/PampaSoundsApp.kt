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
import dev.pampa.pampasounds.update.UpdateController
import dev.pampa.pampasounds.update.soundsUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PampaSoundsApp : Application() {
  val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * Gli oggetti che vivono quanto il processo. Niente DI: sono sette, e si leggono tutti da qui.
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

  /** L'aggiornamento in-app, con il suo stato: sopravvive al cambio di scheda, e un download non si ferma. */
  val updateController by lazy {
    // Il manifest conosce solo la release: la build di debug si confronta con la versione senza suffisso.
    UpdateController(updater, appScope, currentVersion = BuildConfig.VERSION_NAME.removeSuffix("-debug"))
  }
}

val Context.container: AppContainer get() = (applicationContext as PampaSoundsApp).container
