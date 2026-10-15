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

    @Query("SELECT * FROM moods ORDER BY epochDay")
    fun observeMoods(): Flow<List<MoodEntity>>

    @Upsert
    suspend fun upsertMood(mood: MoodEntity)
}

@Database(
    entities = [
        SettingsEntity::class,
        ItemEntity::class,
        CycleEntity::class,
        CheckInEntity::class,
        ItemRatingEntity::class,
        MoodEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BetterYouDatabase : RoomDatabase() {
    abstract fun dao(): BetterYouDao

    companion object {
        fun create(context: Context): BetterYouDatabase =
            Room.databaseBuilder(context, BetterYouDatabase::class.java, "betteryou.db").build()
    }
}
