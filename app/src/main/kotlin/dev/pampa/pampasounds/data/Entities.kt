package dev.pampa.pampasounds.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una misura.
 *
 * L'id e' l'istante d'inizio in millisecondi: si conosce prima di toccare il database, quindi i
 * secondi possono essere scritti mentre la riga della sessione e' ancora in viaggio, e due sessioni
 * nello stesso millisecondo non esistono.
 *
 * **I livelli stanno in dBFS, la calibrazione a parte ([offsetDb]).** Chi legge somma: cosi' una
 * sessione ricalibrata ha tutti i suoi numeri spostati insieme, e nessuno e' mai mezzo vecchio.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
  @PrimaryKey val id: Long,
  val startedAt: Long,
  /** Null mentre la misura e' in corso (o se l'app e' morta prima di chiuderla). */
  val endedAt: Long?,
  val name: String?,
  /** `CaptureSource.name` */
  val source: String,
  val sampleRate: Int,
  val offsetDb: Double,
  val durationSeconds: Double,
  /** Picco non pesato, dBFS. */
  val peakDbfs: Double?,
  /** Dose a fine misura, in percentuale, gia' calcolata con [offsetDb]. */
  val doseNiosh: Double?,
  val doseOsha: Double?,
  /** Spettro medio a terzi d'ottava (Z, dBFS), trenta valori separati da virgola. */
  val bandsDbfs: String?,
)

/** Le statistiche di una sessione per una pesatura. Tre righe per sessione: A, C, Z. */
@Entity(
  tableName = "session_stats",
  primaryKeys = ["sessionId", "weighting"],
  foreignKeys = [
    ForeignKey(
      entity = SessionEntity::class,
      parentColumns = ["id"],
      childColumns = ["sessionId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class SessionStatsEntity(
  val sessionId: Long,
  /** `FrequencyWeighting.ordinal` */
  val weighting: Int,
  val leq: Double?,
  val maxFast: Double?,
  val minFast: Double?,
  val maxSlow: Double?,
  val minSlow: Double?,
  val l10: Double?,
  val l50: Double?,
  val l90: Double?,
)

/** Un secondo di misura: la riga dello storico. Le colonne sono per pesatura (A, C, Z). */
@Entity(
  tableName = "samples",
  primaryKeys = ["sessionId", "second"],
  foreignKeys = [
    ForeignKey(
      entity = SessionEntity::class,
      parentColumns = ["id"],
      childColumns = ["sessionId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("sessionId")],
)
data class SampleEntity(
  val sessionId: Long,
  val second: Int,
  val eqA: Float,
  val eqC: Float,
  val eqZ: Float,
  val maxA: Float?,
  val maxC: Float?,
  val maxZ: Float?,
  val minA: Float?,
  val minC: Float?,
  val minZ: Float?,
  val peak: Float,
)

/** Una riga dell'elenco: la sessione e i due numeri che si leggono a colpo d'occhio, in A. */
data class SessionListItem(
  @Embedded val session: SessionEntity,
  val leqA: Double?,
  val maxA: Double?,
)
