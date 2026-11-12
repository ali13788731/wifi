package com.example.netspeed.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val finalUrl: String = url,
    val fileName: String,
    val mimeType: String? = null,
    val category: String = "other",
    val totalBytes: Long = -1,
    val doneBytes: Long = 0,
    val status: String = "QUEUED",
    val mode: String = "DUAL",
    val rangeVerified: Boolean = false,
    val etag: String? = null,
    val lastModified: String? = null,
    val tempPath: String = "",
    val destUri: String? = null,          // SAF tree uri chosen by the user
    val resultUri: String? = null,        // final document uri / path
    val userAgent: String? = null,
    val referer: String? = null,
    val cookies: String? = null,
    val headersJson: String? = null,
    val username: String? = null,
    val password: String? = null,
    val priority: Int = 0,
    val wifiBytes: Long = 0,
    val mobileBytes: Long = 0,
    val retryCount: Int = 0,
    val error: String? = null,
    val scheduledAt: Long = 0,            // 0 = start now
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0
)

/** One verifiable byte range of a download, with everything the spec asks us to record. */
@Entity(
    tableName = "segments",
    foreignKeys = [ForeignKey(
        entity = DownloadEntity::class, parentColumns = ["id"],
        childColumns = ["downloadId"], onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("downloadId")]
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long,
    val index: Int,
    val startByte: Long,
    val endByte: Long,                    // inclusive
    val downloadedBytes: Long = 0,
    val network: String? = null,          // WIFI / MOBILE that finished it
    val state: String = "PENDING",
    val startTime: Long = 0,
    val completionTime: Long = 0,
    val retryCount: Int = 0,
    val validated: Boolean = false
) {
    val length: Long get() = endByte - startByte + 1
}
