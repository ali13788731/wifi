package com.example.netspeed.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert suspend fun insert(d: DownloadEntity): Long
    @Update suspend fun update(d: DownloadEntity)
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun get(id: Long): DownloadEntity?
    @Query("SELECT * FROM downloads ORDER BY priority DESC, createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','PROBING','RUNNING','VERIFYING') ORDER BY priority DESC, createdAt")
    suspend fun unfinished(): List<DownloadEntity>
    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY priority DESC, createdAt")
    suspend fun queued(): List<DownloadEntity>
    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' AND scheduledAt > 0 AND scheduledAt <= :now")
    suspend fun dueScheduled(now: Long): List<DownloadEntity>
    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String? = null)
    @Query("UPDATE downloads SET doneBytes = :done, wifiBytes = :wifi, mobileBytes = :mobile WHERE id = :id")
    suspend fun setProgress(id: Long, done: Long, wifi: Long, mobile: Long)
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED','FAILED','CANCELED')")
    suspend fun clearFinished()
    /** Mobile bytes used since [since], for the monthly mobile-data limit. */
    @Query("SELECT COALESCE(SUM(mobileBytes),0) FROM downloads WHERE createdAt >= :since")
    suspend fun mobileBytesSince(since: Long): Long
}

@Dao
interface SegmentDao {
    @Insert suspend fun insertAll(list: List<SegmentEntity>)
    @Update suspend fun update(s: SegmentEntity)
    @Query("SELECT * FROM segments WHERE downloadId = :id ORDER BY `index`")
    suspend fun forDownload(id: Long): List<SegmentEntity>
    @Query("DELETE FROM segments WHERE downloadId = :id") suspend fun deleteFor(id: Long)
}

@Database(entities = [DownloadEntity::class, SegmentEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
    abstract fun segments(): SegmentDao
}
