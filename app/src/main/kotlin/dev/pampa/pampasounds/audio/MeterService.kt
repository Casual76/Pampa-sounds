package dev.pampa.pampasounds.audio

import dev.pampa.pampasounds.dsp.dbfsToSpl
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.pampa.pampasounds.MainActivity
import dev.pampa.pampasounds.PampaSoundsApp
import dev.pampa.pampasounds.R
import dev.pampa.pampasounds.dsp.FrequencyWeighting
import dev.pampa.pampasounds.dsp.TimeWeighting
import dev.pampa.pampasounds.ui.format.formatDb
import dev.pampa.pampasounds.ui.format.formatDuration
import dev.pampa.pampasounds.ui.format.unitLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Tiene il microfono aperto a schermo spento.
 *
 * Un servizio in primo piano di tipo `microphone`: e' l'unico modo, da Android 9 in poi, per
 * registrare con l'app in background. Da Android 14 si puo' avviare solo mentre l'app e' visibile,
 * quindi parte sempre da un tocco su "Avvia". La notifica e' la sua faccia: il livello di adesso, il
 * Leq della misura, la durata, e un tasto per fermare.
 */
class MeterService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var observer: Job? = null
  private var wakeLock: PowerManager.WakeLock? = null

  private val container get() = (application as PampaSoundsApp).container

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        container.meterEngine.stop()
        shutDown()
      }
      else -> {
        // startForeground per primo, sempre: il sistema concede pochi secondi da
        // startForegroundService, e un microfono che non si apre deve comunque rispettarli.
        startInForeground()
        val started = container.meterEngine.start()
        if (started.isFailure) {
          shutDown()
        } else {
          acquireWakeLock()
          observe()
        }
      }
    }
    // Ucciso dal sistema, non ripartire da solo: una misura che riprende senza che nessuno lo
    // chieda e' un microfono acceso a sorpresa. La sessione interrotta si recupera dal database.
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    container.meterEngine.stop()
    releaseWakeLock()
    scope.cancel()
    super.onDestroy()
  }

  private fun observe() {
    observer?.cancel()
    val engine = container.meterEngine
    observer = scope.launch {
      combine(engine.state, container.meterSettings.preferences) { state, prefs -> state to prefs }
        // Una volta al secondo basta: la notifica non e' un indicatore, e aggiornarla otto volte al
        // secondo farebbe lavorare la status bar per niente.
        .distinctUntilChanged { old, new ->
          old.first.status == new.first.status &&
            old.first.traceSeconds == new.first.traceSeconds &&
            old.second.weighting == new.second.weighting &&
            old.first.offsetDb == new.first.offsetDb
        }
        .collect { (state, prefs) ->
          if (!state.isMeasuring) {
            shutDown()
            return@collect
          }
          notificationManager().notify(NOTIFICATION_ID, buildNotification(state, prefs.weighting, prefs.timeWeighting))
        }
    }
  }

  private fun startInForeground() {
    ensureChannel()
    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      buildNotification(null, FrequencyWeighting.A, TimeWeighting.Fast),
      // Il tipo "microphone" esiste da Android 11; prima vale quello del manifest.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
    )
  }

  private fun shutDown() {
    observer?.cancel()
    releaseWakeLock()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun buildNotification(
    state: MeterState?,
    weighting: FrequencyWeighting,
    timeWeighting: TimeWeighting,
  ): Notification {
    val open = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val stop = PendingIntent.getService(
      this,
      1,
      Intent(this, MeterService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val w = weighting.ordinal
    val title: String
    val text: String
    if (state?.tick == null) {
      title = "Misura in avvio…"
      text = "Il microfono si sta aprendo"
    } else {
      val tick = state.tick
      val now = dbfsToSpl(if (timeWeighting == TimeWeighting.Fast) tick.fastDb[w] else tick.slowDb[w], state.offsetDb)
      val leq = state.summary?.leqDb?.get(w)?.let { dbfsToSpl(it, state.offsetDb) }
      title = "${formatDb(now)} ${weighting.unitLabel()}"
      text = buildString {
        if (leq != null && !leq.isNaN()) append("Leq ${formatDb(leq)} · ")
        append(formatDuration(state.traceSeconds.toLong()))
      }
    }
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_meter)
      .setContentTitle(title)
      .setContentText(text)
      .setContentIntent(open)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setShowWhen(false)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .addAction(0, getString(R.string.meter_notification_stop), stop)
      .build()
  }

  private fun ensureChannel() {
    val channel = NotificationChannel(
      CHANNEL_ID,
      getString(R.string.meter_channel_name),
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = getString(R.string.meter_channel_description)
      setShowBadge(false)
    }
    notificationManager().createNotificationChannel(channel)
  }

  private fun notificationManager() = getSystemService(NotificationManager::class.java)

  private fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) return
    val power = getSystemService(PowerManager::class.java)
    wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PampaSounds:measure").apply {
      setReferenceCounted(false)
      // Un tetto, per sicurezza: una misura dimenticata non tiene sveglio il telefono per sempre.
      acquire(MAX_MEASURE_MILLIS)
    }
  }

  private fun releaseWakeLock() {
    wakeLock?.takeIf { it.isHeld }?.release()
    wakeLock = null
  }

  companion object {
    private const val CHANNEL_ID = "meter"
    private const val NOTIFICATION_ID = 7
    private const val ACTION_START = "dev.pampa.pampasounds.action.START"
    private const val ACTION_STOP = "dev.pampa.pampasounds.action.STOP"
    private const val MAX_MEASURE_MILLIS = 24L * 60 * 60 * 1000

    fun start(context: Context) {
      ContextCompat.startForegroundService(
        context,
        Intent(context, MeterService::class.java).setAction(ACTION_START),
      )
    }

    fun stop(context: Context) {
      context.startService(Intent(context, MeterService::class.java).setAction(ACTION_STOP))
    }
  }
}
