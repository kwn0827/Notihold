package com.lumifold.notihold

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    var isStarred: Boolean = false,
    var isRead: Boolean = false,
    var isDeleted: Boolean = false,
    var deletedTimestamp: Long? = null,
    var labelId: Int? = null,
    var label: String? = null,
    var cachedIconPath: String? = null // アイコンのキャッシュパスを追加
)
