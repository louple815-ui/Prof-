package com.example.data.local

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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloaded_resources")
data class DownloadedResourceEntity(
    @PrimaryKey val resourceId: String,
    val title: String,
    val description: String,
    val resourceType: String,
    val subject: String,
    val level: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val contentBody: String,
    val authorName: String,
    val downloadedAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface DownloadedResourceDao {
    @Query("SELECT * FROM downloaded_resources ORDER BY downloadedAtMillis DESC")
    fun observeAll(): Flow<List<DownloadedResourceEntity>>

    @Query("SELECT * FROM downloaded_resources WHERE resourceId = :id LIMIT 1")
    suspend fun getById(id: String): DownloadedResourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedResourceEntity)

    @Query("DELETE FROM downloaded_resources WHERE resourceId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloaded_resources")
    suspend fun clearAll()
}

@Database(entities = [DownloadedResourceEntity::class], version = 1, exportSchema = false)
abstract class ProfPlusDatabase : RoomDatabase() {
    abstract fun downloadedResourceDao(): DownloadedResourceDao

    companion object {
        @Volatile
        private var INSTANCE: ProfPlusDatabase? = null

        fun getInstance(context: Context): ProfPlusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProfPlusDatabase::class.java,
                    "profplus_offline_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
