package com.betteryou.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface BetterYouDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettingsIfMissing(settings: SettingsEntity)

    @Upsert
    suspend fun upsertSettings(settings: SettingsEntity)

    @Query("SELECT * FROM items ORDER BY createdAt")
    fun observeItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY createdAt")
    suspend fun getItems(): List<ItemEntity>

    @Insert
    suspend fun insertItem(item: ItemEntity)

    @Query("UPDATE items SET name = :name WHERE id = :id")
    suspend fun renameItem(id: String, name: String)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("SELECT * FROM cycles WHERE completedAt IS NULL ORDER BY startDate DESC")
    fun observeActiveCycles(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles WHERE completedAt IS NULL ORDER BY startDate DESC")
    suspend fun getActiveCycles(): List<CycleEntity>

    @Query("SELECT * FROM cycles WHERE id = :id")
    suspend fun getCycle(id: String): CycleEntity?

    @Insert
    suspend fun insertCycle(cycle: CycleEntity)

    @Update
    suspend fun updateCycle(cycle: CycleEntity)

    @Transaction
    @Query("SELECT * FROM check_ins ORDER BY date")
    fun observeCheckIns(): Flow<List<CheckInWithRatings>>

    @Insert
    suspend fun insertCheckIn(checkIn: CheckInEntity)

    @Insert
    suspend fun insertRatings(ratings: List<ItemRatingEntity>)

    @Query("UPDATE check_ins SET note = :note WHERE id = :id")
    suspend fun updateCheckInNote(id: String, note: String)

    @Query("DELETE FROM item_ratings WHERE checkInId = :checkInId")
    suspend fun deleteRatings(checkInId: String)

    @Query("DELETE FROM check_ins WHERE id = :id")
    suspend fun deleteCheckIn(id: String)

    @Query("SELECT * FROM moods ORDER BY epochDay")
    fun observeMoods(): Flow<List<MoodEntity>>

    @Upsert
    suspend fun upsertMood(mood: MoodEntity)

    @Query("SELECT COUNT(*) FROM moods WHERE epochDay = :epochDay")
    suspend fun moodCount(epochDay: Long): Int

    @Query("DELETE FROM moods WHERE epochDay = :epochDay")
    suspend fun deleteMood(epochDay: Long)

    @Query("SELECT * FROM habit_logs")
    fun observeHabitLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE itemId = :itemId")
    suspend fun getHabitLogs(itemId: String): List<HabitLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHabitLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE epochDay = :epochDay AND itemId = :itemId")
    suspend fun deleteHabitLog(epochDay: Long, itemId: String)
}

@Database(
    entities = [
        SettingsEntity::class,
        ItemEntity::class,
        CycleEntity::class,
        CheckInEntity::class,
        ItemRatingEntity::class,
        MoodEntity::class,
        HabitLogEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class BetterYouDatabase : RoomDatabase() {
    abstract fun dao(): BetterYouDao

    companion object {
        /** Adds the reset checklist and per-rating reset stats. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_logs` (`epochDay` INTEGER NOT NULL, " +
                        "`itemId` TEXT NOT NULL, PRIMARY KEY(`epochDay`, `itemId`))",
                )
                db.execSQL("ALTER TABLE `item_ratings` ADD COLUMN `resetDaysKept` INTEGER")
                db.execSQL("ALTER TABLE `item_ratings` ADD COLUMN `resetDaysTotal` INTEGER")
            }
        }

        fun create(context: Context): BetterYouDatabase =
            Room.databaseBuilder(context, BetterYouDatabase::class.java, "betteryou.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
