package dev.pampa.pampasounds.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

  @Query(
    """
    SELECT s.*, st.leq AS leqA, st.maxFast AS maxA
    FROM sessions s
    LEFT JOIN session_stats st ON st.sessionId = s.id AND st.weighting = 0
    WHERE s.endedAt IS NOT NULL
    ORDER BY s.startedAt DESC
    """,
  )
  fun observeFinished(): Flow<List<SessionListItem>>

  @Query("SELECT * FROM sessions WHERE id = :id")
  fun observe(id: Long): Flow<SessionEntity?>

  @Query("SELECT * FROM session_stats WHERE sessionId = :id ORDER BY weighting")
  fun observeStats(id: Long): Flow<List<SessionStatsEntity>>

  @Query("SELECT * FROM samples WHERE sessionId = :id ORDER BY second")
  suspend fun samples(id: Long): List<SampleEntity>

  @Upsert
  suspend fun upsert(session: SessionEntity)

  @Upsert
  suspend fun upsertStats(stats: List<SessionStatsEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSamples(samples: List<SampleEntity>)

  @Query("UPDATE sessions SET name = :name WHERE id = :id")
  suspend fun rename(id: Long, name: String?)

  @Query("DELETE FROM sessions WHERE id = :id")
  suspend fun delete(id: Long)

  /** Le sessioni rimaste aperte da un processo morto a meta' misura. */
  @Query("SELECT * FROM sessions WHERE endedAt IS NULL")
  suspend fun unfinished(): List<SessionEntity>

  @Query("SELECT COUNT(*) FROM samples WHERE sessionId = :id")
  suspend fun sampleCount(id: Long): Int
}

@Database(
  entities = [SessionEntity::class, SessionStatsEntity::class, SampleEntity::class],
  version = 1,
  exportSchema = true,
)
abstract class SoundsDatabase : RoomDatabase() {
  abstract fun sessions(): SessionDao

  companion object {
    fun create(context: Context): SoundsDatabase =
      Room.databaseBuilder(context, SoundsDatabase::class.java, "sounds.db").build()
  }
}
