package dev.pampa.pampasounds.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import dev.pampa.pampasounds.dsp.CaptureSource
import java.util.concurrent.atomic.AtomicBoolean

/** Perche' il microfono non si e' aperto, o si e' chiuso da solo. */
enum class CaptureFailure { PermissionMissing, Unavailable, Lost }

/**
 * Il microfono, nel modo piu' adatto a misurare.
 *
 * Float a 48 kHz (44,1 se il telefono non regge), mono. La sorgente e' scelta per *non* essere
 * elaborata: UNPROCESSED se il telefono la dichiara (niente AGC, niente filtri, risposta piatta per
 * specifica), altrimenti VOICE_RECOGNITION, che la CDD vuole senza controllo del guadagno. MIC e'
 * l'ultima spiaggia, perche' su molti telefoni passa per l'AGC — e un AGC che alza il silenzio e
 * abbassa il rumore e' l'esatto contrario di un fonometro. Gli effetti che il sistema attacca da solo
 * (AGC, soppressione del rumore, cancellazione dell'eco) si spengono comunque, uno per uno.
 */
class AudioCapture(private val context: Context) {

  class Opened internal constructor(
    val source: CaptureSource,
    val sampleRate: Int,
    internal val record: AudioRecord,
    internal val effects: List<AudioEffect>,
  )

  interface Callback {
    fun onSamples(samples: FloatArray, count: Int)
    fun onFailure(failure: CaptureFailure)

    /** Android 10+: un'altra app (una chiamata, un registratore) si e' presa il microfono. */
    fun onSilenced(silenced: Boolean)
  }

  private val audioManager = context.getSystemService(AudioManager::class.java)
  private val running = AtomicBoolean(false)
  private var thread: Thread? = null
  private var recordingCallback: AudioManager.AudioRecordingCallback? = null

  @SuppressLint("MissingPermission") // controllato a mano subito sotto
  fun open(): Result<Opened> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      return Result.failure(CaptureException(CaptureFailure.PermissionMissing))
    }
    val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
    val sources = buildList {
      if (unprocessed) add(CaptureSource.Unprocessed)
      add(CaptureSource.VoiceRecognition)
      add(CaptureSource.Mic)
    }
    for (source in sources) {
      for (rate in intArrayOf(48_000, 44_100)) {
        val record = tryOpen(source, rate) ?: continue
        return Result.success(Opened(source, rate, record, disableEffects(record.audioSessionId)))
      }
    }
    return Result.failure(CaptureException(CaptureFailure.Unavailable))
  }

  fun start(opened: Opened, callback: Callback) {
    if (!running.compareAndSet(false, true)) return
    val record = opened.record
    registerSilenceWatch(record, callback)
    thread = Thread({ loop(opened, callback) }, "PampaSounds-capture").apply { start() }
  }

  fun stop() {
    running.set(false)
    // Chi si ferma dal thread di cattura stesso (un errore) non puo' aspettare se stesso.
    thread?.takeIf { it !== Thread.currentThread() }?.join(1_000)
    thread = null
    recordingCallback?.let { audioManager.unregisterAudioRecordingCallback(it) }
    recordingCallback = null
  }

  private fun loop(opened: Opened, callback: Callback) {
    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
    val record = opened.record
    // Blocchi da ~21 ms: abbastanza piccoli per un Fast reattivo, abbastanza grandi da non
    // svegliare il thread mille volte al secondo.
    val buffer = FloatArray(1024)
    // Il primo decimo di secondo e' il clic d'accensione del microfono, non la stanza.
    var toDiscard = opened.sampleRate / 10
    var emptyReads = 0
    try {
      record.startRecording()
      if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
        callback.onFailure(CaptureFailure.Unavailable)
        return
      }
      while (running.get()) {
        val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
        when {
          read > 0 -> {
            emptyReads = 0
            if (toDiscard > 0) {
              toDiscard -= read
              continue
            }
            callback.onSamples(buffer, read)
          }
          read == AudioRecord.ERROR_DEAD_OBJECT || read == AudioRecord.ERROR_INVALID_OPERATION -> {
            callback.onFailure(CaptureFailure.Lost)
            return
          }
          else -> if (++emptyReads > 50) {
            callback.onFailure(CaptureFailure.Lost)
            return
          }
        }
      }
    } catch (error: IllegalStateException) {
      callback.onFailure(CaptureFailure.Lost)
    } finally {
      runCatching { record.stop() }
      record.release()
      opened.effects.forEach { runCatching { it.release() } }
    }
  }

  @SuppressLint("MissingPermission")
  private fun tryOpen(source: CaptureSource, rate: Int): AudioRecord? {
    val audioSource = when (source) {
      CaptureSource.Unprocessed -> MediaRecorder.AudioSource.UNPROCESSED
      CaptureSource.VoiceRecognition -> MediaRecorder.AudioSource.VOICE_RECOGNITION
      CaptureSource.Mic -> MediaRecorder.AudioSource.MIC
    }
    val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
    if (minBuffer <= 0) return null
    // Mezzo secondo di margine: se il thread perde un giro (GC, schermo che si accende) non si
    // perdono campioni, e una misura con dei buchi sarebbe una misura sbagliata.
    val bufferBytes = maxOf(minBuffer * 4, rate / 2 * Float.SIZE_BYTES)
    val record = runCatching {
      AudioRecord.Builder()
        .setAudioSource(audioSource)
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build(),
        )
        .setBufferSizeInBytes(bufferBytes)
        .build()
    }.getOrNull() ?: return null
    if (record.state != AudioRecord.STATE_INITIALIZED) {
      record.release()
      return null
    }
    return record
  }

  /** Spegne gli effetti attaccati alla sessione; li restituisce perche' vanno rilasciati alla fine. */
  private fun disableEffects(sessionId: Int): List<AudioEffect> = buildList {
    if (AutomaticGainControl.isAvailable()) runCatching { AutomaticGainControl.create(sessionId) }.getOrNull()?.let { add(it) }
    if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()?.let { add(it) }
    if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()?.let { add(it) }
    forEach { effect -> runCatching { effect.enabled = false } }
  }

  private fun registerSilenceWatch(record: AudioRecord, callback: Callback) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val session = record.audioSessionId
    val watch = object : AudioManager.AudioRecordingCallback() {
      override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
        val mine = configs.firstOrNull { it.clientAudioSessionId == session } ?: return
        callback.onSilenced(mine.isClientSilenced)
      }
    }
    recordingCallback = watch
    audioManager.registerAudioRecordingCallback(watch, Handler(Looper.getMainLooper()))
  }
}

class CaptureException(val failure: CaptureFailure) : Exception(failure.name)
