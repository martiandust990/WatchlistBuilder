package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watchlists")
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "scrips")
data class ScripEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val watchlistId: Int,
    val scripName: String,
    val addedAt: Long = System.currentTimeMillis()
)

data class WatchlistWithScrips(
    val watchlist: WatchlistEntity,
    val scrips: List<ScripEntity>
)

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlists")
    fun getAllWatchlists(): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWatchlist(watchlist: WatchlistEntity): Long

    @Query("DELETE FROM watchlists WHERE id = :id")
    suspend fun deleteWatchlist(id: Int)

    @Query("SELECT * FROM scrips WHERE watchlistId = :watchlistId ORDER BY addedAt DESC")
    fun getScripsForWatchlist(watchlistId: Int): Flow<List<ScripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScrip(scrip: ScripEntity): Long

    @Query("DELETE FROM scrips WHERE id = :id")
    suspend fun deleteScrip(id: Int)

    @Query("DELETE FROM scrips WHERE watchlistId = :watchlistId AND scripName = :scripName")
    suspend fun deleteScripsByName(watchlistId: Int, scripName: String)

    @Query("SELECT COUNT(*) FROM watchlists")
    suspend fun getWatchlistCount(): Int
}

@Database(entities = [WatchlistEntity::class, ScripEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "watchlist_database"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Prepopulate default watchlists
                        db.execSQL("INSERT INTO watchlists (id, name) VALUES (1, 'Default')")
                        db.execSQL("INSERT INTO watchlists (id, name) VALUES (2, 'Long Term')")
                        db.execSQL("INSERT INTO watchlists (id, name) VALUES (3, 'Daily Triggers')")

                        // Prepopulate seed stock scrips
                        db.execSQL("INSERT INTO scrips (watchlistId, scripName, addedAt) VALUES (1, 'RELIANCE', 1719991000000)")
                        db.execSQL("INSERT INTO scrips (watchlistId, scripName, addedAt) VALUES (1, 'TCS', 1719992000000)")
                        db.execSQL("INSERT INTO scrips (watchlistId, scripName, addedAt) VALUES (1, 'HDFCBANK', 1719993000000)")
                        db.execSQL("INSERT INTO scrips (watchlistId, scripName, addedAt) VALUES (2, 'INFY', 1719994000000)")
                        db.execSQL("INSERT INTO scrips (watchlistId, scripName, addedAt) VALUES (2, 'SBIN', 1719995000000)")
                        db.execSQL("INSERT INTO scrips (watchlistId, scripName, addedAt) VALUES (3, 'TATAMOTORS', 1719996000000)")
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
