package dev.pampa.pampasounds.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.pampa.pampasounds.AppContainer
import dev.pampa.pampasounds.audio.MeterService

/** Che cosa impedisce di misurare, quando qualcosa lo impedisce. */
enum class MicPermissionProblem {
  /** Negato una volta: si puo' richiedere. */
  Denied,

  /** Negato per sempre: solo le impostazioni di sistema lo riaprono. */
  Blocked,
}

/** Avvia e ferma la misura, chiedendo i permessi che servono al momento giusto. */
@Stable
class MeterController internal constructor(
  val start: () -> Unit,
  val stop: () -> Unit,
  problemState: () -> MicPermissionProblem?,
  val openAppSettings: () -> Unit,
) {
  private val readProblem = problemState
  val problem: MicPermissionProblem? get() = readProblem()
}

@Composable
fun rememberMeterController(container: AppContainer): MeterController {
  val context = LocalContext.current
  val activity = LocalActivity.current
  val haptics = rememberFluidHaptics()
  var problem by remember { mutableStateOf<MicPermissionProblem?>(null) }

  fun launchService() {
    problem = null
    haptics.play(FluidHapticEvent.ListenStart)
    MeterService.start(context)
  }

  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
    if (context.has(Manifest.permission.RECORD_AUDIO)) {
      launchService()
    } else {
      val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
      problem = if (canAskAgain) MicPermissionProblem.Denied else MicPermissionProblem.Blocked
      haptics.play(FluidHapticEvent.Warning)
    }
  }

  return remember(container, launcher, haptics, context, activity) {
    MeterController(
      start = {
        val missing = buildList {
          if (!context.has(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
          // Le notifiche non sono indispensabili (la misura gira lo stesso), ma senza la notifica
          // non si vede che il microfono e' acceso: si chiedono insieme, una volta.
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.has(Manifest.permission.POST_NOTIFICATIONS)) {
            add(Manifest.permission.POST_NOTIFICATIONS)
          }
        }
        if (missing.isEmpty() || (context.has(Manifest.permission.RECORD_AUDIO) && problem == null && askedNotifications)) {
          launchService()
        } else {
          askedNotifications = true
          launcher.launch(missing.toTypedArray())
        }
      },
      stop = {
        haptics.play(FluidHapticEvent.Stop)
        // Il servizio chiude la misura; se per qualche ragione non c'e', la chiude il motore.
        runCatching { MeterService.stop(context) }.onFailure { container.meterEngine.stop() }
      },
      problemState = { problem },
      openAppSettings = {
        context.startActivity(
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
      },
    )
  }
}

/** Le notifiche si chiedono una volta per processo: un "no" non va richiesto a ogni avvio. */
private var askedNotifications = false

private fun Context.has(permission: String): Boolean =
  ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
