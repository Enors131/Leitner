package com.example.leitner

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * One flashcard. `box` is 1..5, `dueAt` is the epoch-millis time from which
 * the card is due for review. The word is unique (case-insensitive).
 */
@Entity(tableName = "cards", indices = [Index(value = ["word"], unique = true)])
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val word: String,
    val meaning: String,
    val box: Int = 1,
    val dueAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface CardDao {
    // IGNORE: a duplicate word is skipped and -1 is returned for it
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(card: Card): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cards: List<Card>): List<Long>

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun update(card: Card)

    @Delete
    suspend fun delete(card: Card)

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: Long): Card?

    @Query("SELECT * FROM cards WHERE dueAt <= :now ORDER BY dueAt ASC, id ASC LIMIT :limit")
    suspend fun dueCards(now: Long, limit: Int): List<Card>

    @Query("SELECT * FROM cards ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<Card>>
}

@Database(entities = [Card::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Single shared instance: the app UI and the widget use the same database. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "leitner.db"
                ).build().also { instance = it }
            }
    }
}
