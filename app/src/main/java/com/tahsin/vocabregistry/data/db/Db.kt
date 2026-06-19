package com.tahsin.vocabregistry.data.db

import androidx.room.*
import com.tahsin.vocabregistry.data.model.*
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun axisToString(a: Axis) = a.name
    @TypeConverter fun stringToAxis(s: String) = Axis.valueOf(s)
    @TypeConverter fun statusToString(s: AxisStatus) = s.name
    @TypeConverter fun stringToStatus(s: String) = AxisStatus.valueOf(s)
}

@Dao
interface WordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(words: List<Word>)
    @Query("SELECT COUNT(*) FROM words") suspend fun count(): Int
    @Query("SELECT * FROM words") suspend fun all(): List<Word>
    @Query("SELECT * FROM words WHERE id = :id") suspend fun byId(id: Int): Word?
    @Query("SELECT * FROM words WHERE tier = :tier") suspend fun byTier(tier: Int): List<Word>
    @Query("""SELECT * FROM words WHERE (:tier = 0 OR tier = :tier)
              AND (word LIKE '%'||:q||'%' OR theme LIKE '%'||:q||'%' OR definition LIKE '%'||:q||'%')
              ORDER BY id LIMIT 100""")
    suspend fun search(q: String, tier: Int): List<Word>
}

@Dao
interface AxisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(state: AxisState)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(states: List<AxisState>)
    @Query("SELECT * FROM axis_states") suspend fun all(): List<AxisState>
    @Query("SELECT * FROM axis_states WHERE wordId = :wordId") suspend fun forWord(wordId: Int): List<AxisState>
    @Query("SELECT * FROM axis_states WHERE dueEpoch > 0 AND dueEpoch <= :now AND status != 'NEW'")
    suspend fun due(now: Long): List<AxisState>
    @Query("SELECT COUNT(DISTINCT wordId) FROM axis_states") suspend fun wordsInCirculation(): Int
    @Query("DELETE FROM axis_states") suspend fun clearAll()
    @Query("SELECT * FROM axis_states ORDER BY wordId") fun watchAll(): Flow<List<AxisState>>
}

@Dao
interface LogDao {
    @Insert suspend fun insert(log: ReviewLog)
    @Query("SELECT * FROM review_log ORDER BY epoch DESC LIMIT :n") suspend fun recent(n: Int): List<ReviewLog>
    @Query("SELECT * FROM review_log WHERE axis = :axis ORDER BY epoch DESC LIMIT :n")
    suspend fun recentForAxis(axis: Axis, n: Int): List<ReviewLog>
    @Query("DELETE FROM review_log WHERE id NOT IN (SELECT id FROM review_log ORDER BY epoch DESC LIMIT 2000)")
    suspend fun trim()
    @Query("DELETE FROM review_log") suspend fun clearAll()
}

@Dao
interface PendingDao {
    @Insert suspend fun insert(p: PendingGrade)
    @Query("SELECT * FROM pending_grades ORDER BY epoch") suspend fun all(): List<PendingGrade>
    @Query("DELETE FROM pending_grades WHERE id = :id") suspend fun delete(id: Long)
}

@Database(
    entities = [Word::class, AxisState::class, ReviewLog::class, PendingGrade::class],
    version = 1, exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun words(): WordDao
    abstract fun axes(): AxisDao
    abstract fun logs(): LogDao
    abstract fun pending(): PendingDao
}
