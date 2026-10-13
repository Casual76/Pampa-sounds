package dev.pampa.pampasounds.update

import android.content.Context
import dev.antigravity.fluidengine.foundation.AppUpdater
import dev.antigravity.fluidengine.net.EngineHttp
import dev.antigravity.fluidengine.update.AndroidAppUpdateInstaller
import dev.antigravity.fluidengine.update.EngineAppUpdater
import dev.antigravity.fluidengine.update.UpdateSource
import dev.pampa.pampasounds.BuildConfig

/**
 * L'aggiornamento in-app, sullo stesso manifest.json che il Pampa Store legge per mostrare l'app:
 * una sola fonte, quindi lo store e l'app non possono offrire due versioni diverse.
 *
 * L'id e' quello della release anche nelle build di debug (che hanno il suffisso `.debug`): il
 * manifest conosce solo l'app pubblicata.
 */
fun soundsUpdater(context: Context): AppUpdater {
  val http = EngineHttp(userAgent = "PampaSoundsUpdater/${BuildConfig.VERSION_NAME}")
  return EngineAppUpdater(
    http = http,
    source = UpdateSource(
      manifestUrl = BuildConfig.MANIFEST_URL,
      applicationId = RELEASE_APPLICATION_ID,
    ),
    installer = AndroidAppUpdateInstaller(context.applicationContext, http),
  )
}

private const val RELEASE_APPLICATION_ID = "dev.pampa.pampasounds"
