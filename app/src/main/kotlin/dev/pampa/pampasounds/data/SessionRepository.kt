package dev.pampa.pampasounds.data

import dev.pampa.pampasounds.dsp.DoseCriterion
import dev.pampa.pampasounds.dsp.FrequencyWeighting
import dev.pampa.pampasounds.dsp.LevelHistogram
import dev.pampa.pampasounds.dsp.LevelSecond
import dev.pampa.pampasounds.dsp.LevelSummary
import dev.pampa.pampasounds.dsp.NoiseDose
import dev.pampa.pampasounds.dsp.SILENCE_DBFS
import dev.pampa.pampasounds.dsp.TimeWeighting
import dev.pampa.pampasounds.dsp.dbToPower
import dev.pampa.pampasounds.dsp.powerToDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Una sessione aperta nel dettaglio: la riga e le statistiche per pesatura. */
data class SessionDetail(
  val session: SessionEntity,
  val stats: Map<FrequencyWeighting, SessionStatsEntity>,
)

/** Il riassunto di fine misura, come lo consegna il motore. */
class SessionResult(
  val endedAt: Long,
  val offsetDb: Double,
  val summary: LevelSummary,
  val doseNiosh: Double,
  val doseOsha: Double,
  val bandsDbfs: DoubleArray?,
)

class SessionRepository(private val dao: SessionDao) {

  fun observeFinished(): Flow<List<SessionListItem>> = dao.observeFinished()

  fun observeDetail(id: Long): Flow<SessionDetail?> =
    combine(dao.observe(id), dao.observeStats(id)) { session, stats ->
      session?.let { found ->
        SessionDetail(
          session = found,
          stats = stats.associateBy { FrequencyWeighting.entries[it.weighting] },
        )
      }
    }

  suspend fun samples(id: Long): List<SampleEntity> = dao.samples(id)

  suspend fun rename(id: Long, name: String?) = dao.rename(id, name?.trim()?.takeIf { it.isNotEmpty() })

  suspend fun delete(id: Long) = dao.delete(id)

  // --- Scrittura, dal motore di misura ---

  suspend fun create(session: SessionEntity) = dao.upsert(session)

  suspend fun appendSeconds(sessionId: Long, seconds: List<LevelSecond>) {
    if (seconds.isEmpty()) return
    dao.insertSamples(seconds.map { it.toEntity(sessionId) })
  }

  suspend fun finish(session: SessionEntity, result: SessionResult) {
    val summary = result.summary
    dao.upsert(
      session.copy(
        endedAt = result.endedAt,
        offsetDb = result.offsetDb,
        durationSeconds = summary.durationSeconds,
        peakDbfs = summary.peakDb.orNull(),
        doseNiosh = result.doseNiosh,
        doseOsha = result.doseOsha,
        bandsDbfs = result.bandsDbfs?.joinToString(",") { "%.2f".format(java.util.Locale.ROOT, it) },
      ),
    )
    dao.upsertStats(
      FrequencyWeighting.entries.map { w ->
        val i = w.ordinal
        SessionStatsEntity(
          sessionId = session.id,
          weighting = i,
          leq = summary.leqDb[i].orNull(),
          maxFast = summary.maxDb[i][TimeWeighting.Fast.ordinal].orNull(),
          minFast = summary.minDb[i][TimeWeighting.Fast.ordinal].orNull(),
          maxSlow = summary.maxDb[i][TimeWeighting.Slow.ordinal].orNull(),
          minSlow = summary.minDb[i][TimeWeighting.Slow.ordinal].orNull(),
          l10 = summary.l10Db[i].orNull(),
          l50 = summary.l50Db[i].orNull(),
          l90 = summary.l90Db[i].orNull(),
        )
      },
    )
  }

  /**
   * Chiude le sessioni rimaste aperte da un processo morto a meta' misura (il sistema che uccide
   * l'app di notte, un riavvio). I secondi scritti ci sono: le statistiche si ricostruiscono da
   * quelli, a grana di secondo invece che di ottavo, e la misura non va persa.
   */
  suspend fun recoverUnfinished() {
    for (session in dao.unfinished()) {
      val samples = dao.samples(session.id)
      if (samples.size < MIN_SECONDS_TO_KEEP) {
        dao.delete(session.id)
        continue
      }
      val weightings = FrequencyWeighting.entries
      val leq = DoubleArray(weightings.size)
      val histograms = Array(weightings.size) { LevelHistogram() }
      val max = DoubleArray(weightings.size) { Double.NEGATIVE_INFINITY }
      val min = DoubleArray(weightings.size) { Double.POSITIVE_INFINITY }
      var peak = SILENCE_DBFS
      for (sample in samples) {
        val eq = doubleArrayOf(sample.eqA.toDouble(), sample.eqC.toDouble(), sample.eqZ.toDouble())
        val mx = arrayOf(sample.maxA, sample.maxC, sample.maxZ)
        val mn = arrayOf(sample.minA, sample.minC, sample.minZ)
        for (i in weightings.indices) {
          leq[i] += dbToPower(eq[i])
          histograms[i].add(eq[i])
          mx[i]?.let { if (it > max[i]) max[i] = it.toDouble() }
          mn[i]?.let { if (it < min[i]) min[i] = it.toDouble() }
        }
        if (sample.peak > peak) peak = sample.peak.toDouble()
      }
      fun finite(v: Double) = if (v.isInfinite()) Double.NaN else v
      val summary = LevelSummary(
        durationSeconds = samples.size.toDouble(),
        leqDb = DoubleArray(weightings.size) { powerToDb(leq[it] / samples.size) },
        maxDb = Array(weightings.size) { doubleArrayOf(finite(max[it]), Double.NaN) },
        minDb = Array(weightings.size) { doubleArrayOf(finite(min[it]), Double.NaN) },
        l10Db = DoubleArray(weightings.size) { histograms[it].exceeded(10.0) ?: Double.NaN },
        l50Db = DoubleArray(weightings.size) { histograms[it].exceeded(50.0) ?: Double.NaN },
        l90Db = DoubleArray(weightings.size) { histograms[it].exceeded(90.0) ?: Double.NaN },
        peakDb = peak,
      )
      val doseHistogram = histograms[FrequencyWeighting.A.ordinal]
      finish(
        session,
        SessionResult(
          endedAt = session.startedAt + samples.size * 1000L,
          offsetDb = session.offsetDb,
          summary = summary,
          doseNiosh = NoiseDose.percent(doseHistogram, session.offsetDb, DoseCriterion.Niosh),
          doseOsha = NoiseDose.percent(doseHistogram, session.offsetDb, DoseCriterion.Osha),
          bandsDbfs = null,
        ),
      )
    }
  }

  companion object {
    /** Sotto i tre secondi una misura e' un tocco per sbaglio, non una misura. */
    const val MIN_SECONDS_TO_KEEP = 3
  }
}

private fun Double.orNull(): Double? = if (isNaN() || isInfinite()) null else this

private fun Double.orNullFloat(): Float? = if (isNaN() || isInfinite()) null else toFloat()

private fun LevelSecond.toEntity(sessionId: Long) = SampleEntity(
  sessionId = sessionId,
  second = index,
  eqA = eqDb[0].toFloat(),
  eqC = eqDb[1].toFloat(),
  eqZ = eqDb[2].toFloat(),
  maxA = fastMaxDb[0].orNullFloat(),
  maxC = fastMaxDb[1].orNullFloat(),
  maxZ = fastMaxDb[2].orNullFloat(),
  minA = fastMinDb[0].orNullFloat(),
  minC = fastMinDb[1].orNullFloat(),
  minZ = fastMinDb[2].orNullFloat(),
  peak = peakDb.toFloat(),
)

/** I livelli di un campione per una pesatura: (Leq, max Fast, min Fast), in dBFS. */
fun SampleEntity.levels(weighting: FrequencyWeighting): Triple<Float, Float?, Float?> = when (weighting) {
  FrequencyWeighting.A -> Triple(eqA, maxA, minA)
  FrequencyWeighting.C -> Triple(eqC, maxC, minC)
  FrequencyWeighting.Z -> Triple(eqZ, maxZ, minZ)
}

/** Lo spettro medio salvato, o null. */
fun SessionEntity.bands(): DoubleArray? =
  bandsDbfs?.split(',')?.mapNotNull { it.toDoubleOrNull() }?.toDoubleArray()?.takeIf { it.size == 30 }
