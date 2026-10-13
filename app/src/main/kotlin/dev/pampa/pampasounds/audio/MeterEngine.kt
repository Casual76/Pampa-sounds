package dev.pampa.pampasounds.audio

import android.content.Context
import dev.pampa.pampasounds.data.SessionEntity
import dev.pampa.pampasounds.data.SessionRepository
import dev.pampa.pampasounds.data.SessionResult
import dev.pampa.pampasounds.dsp.Calibration
import dev.pampa.pampasounds.dsp.CaptureSource
import dev.pampa.pampasounds.dsp.DIGITAL_SILENCE_DBFS
import dev.pampa.pampasounds.dsp.DoseCriterion
import dev.pampa.pampasounds.dsp.FrequencyWeighting
import dev.pampa.pampasounds.dsp.LevelProcessor
import dev.pampa.pampasounds.dsp.LevelSecond
import dev.pampa.pampasounds.dsp.LevelSummary
import dev.pampa.pampasounds.dsp.LevelTick
import dev.pampa.pampasounds.dsp.NoiseDose
import dev.pampa.pampasounds.dsp.SpectrumAnalyzer
import dev.pampa.pampasounds.dsp.SpectrumFrame
import dev.pampa.pampasounds.dsp.TICKS_PER_SECOND
import dev.pampa.pampasounds.dsp.ThirdOctave
import dev.pampa.pampasounds.dsp.powerToDb
import dev.pampa.pampasounds.settings.MeterSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MeterStatus {
  /** Mai partito in questo processo. */
  Idle,
  Measuring,

  /** Fermato: i numeri restano a schermo finche' non si riparte. */
  Stopped,
  Failed,
}

/**
 * Tutto quello che la UI e la notifica leggono della misura. I livelli sono in dBFS: la UI somma
 * [offsetDb], che segue la calibrazione anche a misura in corso.
 */
data class MeterState(
  val status: MeterStatus = MeterStatus.Idle,
  val failure: CaptureFailure? = null,
  val sessionId: Long? = null,
  val startedAt: Long = 0L,
  val source: CaptureSource? = null,
  val sampleRate: Int = 0,
  val offsetDb: Double = 0.0,
  val tick: LevelTick? = null,
  val summary: LevelSummary? = null,
  val doseNiosh: Double = 0.0,
  val doseOsha: Double = 0.0,
  /** Gli ultimi trenta secondi, otto letture al secondo. */
  val recent: List<LevelTick> = emptyList(),
  /** Quanti secondi ha la traccia: cambia una volta al secondo. */
  val traceSeconds: Int = 0,
  /** Un'altra app si e' presa il microfono (Android 10+): il segnale e' zero, non silenzio. */
  val silenced: Boolean = false,
  /** Da almeno due secondi arrivano solo zeri: un emulatore, o un microfono negato dal sistema. */
  val digitalSilence: Boolean = false,
) {
  val isMeasuring: Boolean get() = status == MeterStatus.Measuring
}

/**
 * Il fonometro: microfono → [LevelProcessor] e [SpectrumAnalyzer] → stato osservabile e database.
 *
 * Un'istanza per processo (vive nell'AppContainer), comandata dal [MeterService]: e' il servizio a
 * tenere vivo il processo a schermo spento, il motore non sa niente di notifiche. Il lavoro vero
 * sta sul thread audio; il database riceve i secondi a lotti, in ordine, da una coda sola.
 */
class MeterEngine(
  context: Context,
  private val repository: SessionRepository,
  settings: MeterSettings,
  private val scope: CoroutineScope,
) {
  private val capture = AudioCapture(context.applicationContext)

  private val _state = MutableStateFlow(MeterState())
  val state: StateFlow<MeterState> = _state.asStateFlow()

  private val _spectrum = MutableStateFlow<SpectrumFrame?>(null)

  /** L'ultimo spettro, a ~23 fotogrammi al secondo mentre si misura. */
  val spectrum: StateFlow<SpectrumFrame?> = _spectrum.asStateFlow()

  /** La cascata dell'ultima misura. Null prima della prima. */
  @Volatile
  var spectrogram: SpectrogramHistory? = null
    private set

  val trace = LiveTrace()

  @Volatile
  private var trimDb = 0.0

  // --- Stato della misura in corso: toccato solo dal thread audio fra start e stop. ---
  private var processor: LevelProcessor? = null
  private var analyzer: SpectrumAnalyzer? = null
  private var session: SessionEntity? = null
  private val recent = ArrayDeque<LevelTick>()
  private val pendingSeconds = ArrayList<LevelSecond>()
  private var bandSum = DoubleArray(ThirdOctave.COUNT)
  private var bandFrames = 0
  private var silentTicks = 0

  /** Le scritture sul database, in fila: la sessione nasce prima dei suoi secondi, sempre. */
  private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

  init {
    scope.launch(Dispatchers.IO) {
      repository.recoverUnfinished()
      for (write in writes) runCatching { write() }
    }
    scope.launch {
      settings.preferences.collect { prefs ->
        trimDb = prefs.trimDb
        _state.update { current ->
          current.source?.let { current.copy(offsetDb = Calibration.offsetDb(it, prefs.trimDb)) } ?: current
        }
      }
    }
  }

  /** Apre il microfono e comincia una sessione. Dal thread principale, con il servizio gia' in primo piano. */
  fun start(): Result<Unit> {
    if (_state.value.isMeasuring) return Result.success(Unit)
    val opened = capture.open().getOrElse { error ->
      val failure = (error as? CaptureException)?.failure ?: CaptureFailure.Unavailable
      _state.update { it.copy(status = MeterStatus.Failed, failure = failure) }
      return Result.failure(error)
    }
    val startedAt = System.currentTimeMillis()
    val offset = Calibration.offsetDb(opened.source, trimDb)
    processor = LevelProcessor(opened.sampleRate)
    analyzer = SpectrumAnalyzer(opened.sampleRate)
    spectrogram = SpectrogramHistory(opened.sampleRate, analyzer!!.fftSize)
    recent.clear()
    pendingSeconds.clear()
    bandSum = DoubleArray(ThirdOctave.COUNT)
    bandFrames = 0
    silentTicks = 0
    trace.clear()
    _spectrum.value = null
    val entity = SessionEntity(
      id = startedAt,
      startedAt = startedAt,
      endedAt = null,
      name = null,
      source = opened.source.name,
      sampleRate = opened.sampleRate,
      offsetDb = offset,
      durationSeconds = 0.0,
      peakDbfs = null,
      doseNiosh = null,
      doseOsha = null,
      bandsDbfs = null,
    )
    session = entity
    enqueue { repository.create(entity) }
    _state.value = MeterState(
      status = MeterStatus.Measuring,
      sessionId = entity.id,
      startedAt = startedAt,
      source = opened.source,
      sampleRate = opened.sampleRate,
      offsetDb = offset,
    )
    capture.start(opened, captureCallback)
    return Result.success(Unit)
  }

  /** Ferma il microfono e chiude la sessione. Idempotente. */
  fun stop() {
    if (!_state.value.isMeasuring) return
    capture.stop()
    finishSession(MeterStatus.Stopped, failure = null)
  }

  private fun finishSession(status: MeterStatus, failure: CaptureFailure?) {
    val processor = processor ?: return
    val entity = session ?: return
    this.processor = null
    analyzer = null
    session = null

    val summary = processor.summary()
    val offset = Calibration.offsetDb(CaptureSource.valueOf(entity.source), trimDb)
    val doseHistogram = processor.secondHistograms[FrequencyWeighting.A.ordinal]
    val remaining = pendingSeconds.toList()
    pendingSeconds.clear()
    val bands = if (bandFrames > 0) DoubleArray(ThirdOctave.COUNT) { powerToDb(bandSum[it] / bandFrames) } else null
    val result = SessionResult(
      endedAt = System.currentTimeMillis(),
      offsetDb = offset,
      summary = summary,
      doseNiosh = NoiseDose.percent(doseHistogram, offset, DoseCriterion.Niosh),
      doseOsha = NoiseDose.percent(doseHistogram, offset, DoseCriterion.Osha),
      bandsDbfs = bands,
    )
    enqueue {
      repository.appendSeconds(entity.id, remaining)
      if (summary.durationSeconds < SessionRepository.MIN_SECONDS_TO_KEEP) {
        repository.delete(entity.id)
      } else {
        repository.finish(entity, result)
      }
    }
    _state.update {
      it.copy(status = status, failure = failure, summary = summary, silenced = false, digitalSilence = false, offsetDb = offset)
    }
  }

  private fun enqueue(write: suspend () -> Unit) {
    writes.trySend(write)
  }

  private val levelListener = object : LevelProcessor.Listener {
    override fun onTick(tick: LevelTick) {
      recent.addLast(tick)
      while (recent.size > RECENT_TICKS) recent.removeFirst()
      val summary = processor?.summary()
      val snapshot = recent.toList()
      silentTicks = if (tick.peakDb <= DIGITAL_SILENCE_DBFS) silentTicks + 1 else 0
      val digitalSilence = silentTicks >= 2 * TICKS_PER_SECOND
      _state.update { it.copy(tick = tick, summary = summary, recent = snapshot, digitalSilence = digitalSilence) }
    }

    override fun onSecond(second: LevelSecond) {
      trace.append(second)
      pendingSeconds += second
      if (pendingSeconds.size >= FLUSH_EVERY_SECONDS) {
        val batch = pendingSeconds.toList()
        pendingSeconds.clear()
        val id = session?.id
        if (id != null) enqueue { repository.appendSeconds(id, batch) }
      }
      val processor = processor ?: return
      val doseHistogram = processor.secondHistograms[FrequencyWeighting.A.ordinal]
      _state.update { current ->
        current.copy(
          traceSeconds = second.index + 1,
          doseNiosh = NoiseDose.percent(doseHistogram, current.offsetDb, DoseCriterion.Niosh),
          doseOsha = NoiseDose.percent(doseHistogram, current.offsetDb, DoseCriterion.Osha),
        )
      }
    }
  }

  private val onFrame: (SpectrumFrame) -> Unit = { frame ->
    for (i in 0 until ThirdOctave.COUNT) bandSum[i] += frame.bandPower[i]
    bandFrames++
    spectrogram?.push(frame)
    _spectrum.value = frame
  }

  private val captureCallback = object : AudioCapture.Callback {
    override fun onSamples(samples: FloatArray, count: Int) {
      processor?.process(samples, count, levelListener)
      analyzer?.push(samples, count, onFrame)
    }

    override fun onFailure(failure: CaptureFailure) {
      // Dal thread di cattura, che sta per uscire: la chiusura si fa altrove, mai su se stessi.
      scope.launch {
        if (_state.value.isMeasuring) {
          capture.stop()
          finishSession(MeterStatus.Failed, failure)
        }
      }
    }

    override fun onSilenced(silenced: Boolean) {
      _state.update { it.copy(silenced = silenced) }
    }
  }

  private companion object {
    const val RECENT_TICKS = 30 * TICKS_PER_SECOND
    const val FLUSH_EVERY_SECONDS = 10
  }
}
