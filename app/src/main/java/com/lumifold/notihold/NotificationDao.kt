package com.lumifold.notihold

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllActivePaging(): PagingSource<Int, NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isStarred = 1 AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getStarredPaging(): PagingSource<Int, NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isDeleted = 1 ORDER BY timestamp DESC")
    fun getDeletedPaging(): PagingSource<Int, NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR text LIKE '%' || :query || '%' OR appName LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchActivePaging(query: String): PagingSource<Int, NotificationEntity>

    // 【日付グループ化】日付ごとに通知をグループ化して取得
    @Query("SELECT * FROM notifications WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllActiveGroupedByDatePaging(): PagingSource<Int, NotificationEntity>

    // 【日付検索】特定の日付の通知を検索
    @Query("SELECT * FROM notifications WHERE isDeleted = 0 AND timestamp >= :targetDate AND timestamp < :targetDate + 86400000 ORDER BY timestamp DESC")
    fun searchByDatePaging(targetDate: Long): PagingSource<Int, NotificationEntity>

    // 【日付範囲検索】指定された日付範囲の通知を検索
    @Query("SELECT * FROM notifications WHERE isDeleted = 0 AND timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    fun searchByDateRangePaging(startDate: Long, endDate: Long): PagingSource<Int, NotificationEntity>

    // 【日付とテキストの複合検索】特定の日付かつテキストに一致する通知を検索
    @Query("SELECT * FROM notifications WHERE isDeleted = 0 AND date(timestamp/1000, 'unixepoch') = date(:targetDate/1000, 'unixepoch') AND (title LIKE '%' || :query || '%' OR text LIKE '%' || :query || '%' OR appName LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchByDateAndTextPaging(targetDate: Long, query: String): PagingSource<Int, NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun getAllActive(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isDeleted = 1 AND deletedTimestamp < :threshold")
    suspend fun getOldDeleted(threshold: Long): List<NotificationEntity>

    // スター付きは自動削除の対象外にするため isStarred = 0 を追加
    @Query("DELETE FROM notifications WHERE isDeleted = 1 AND isStarred = 0 AND (id IN (:ids) OR deletedTimestamp < :threshold)")
    suspend fun deletePhysical(ids: List<Int> = emptyList(), threshold: Long = 0)

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun getById(id: Int): NotificationEntity?

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM notifications WHERE isDeleted = 1")
    suspend fun clearTrash()

    // スター付きは自動削除の対象外にする
    @Query("DELETE FROM notifications WHERE isDeleted = 0 AND isStarred = 0 AND timestamp < :cutoffTime")
    suspend fun deleteOldActive(cutoffTime: Long)

    @Insert
    suspend fun insert(notification: NotificationEntity)

    @Update
    suspend fun update(notification: NotificationEntity)

    @Delete
    suspend fun delete(notification: NotificationEntity)

    @Query("UPDATE notifications SET isStarred = :isStarred WHERE id = :id")
    suspend fun updateStarStatus(id: Int, isStarred: Boolean)

    @Query("UPDATE notifications SET isRead = :isRead WHERE id = :id")
    suspend fun updateReadStatus(id: Int, isRead: Boolean)

    @Query("UPDATE notifications SET isDeleted = 1, deletedTimestamp = :timestamp WHERE id = :id")
    suspend fun softDelete(id: Int, timestamp: Long)

    // 全ての通知をゴミ箱へ移動（スター付きを除く）
    @Query("UPDATE notifications SET isDeleted = 1, deletedTimestamp = :timestamp WHERE isDeleted = 0 AND isStarred = 0")
    suspend fun moveAllToTrashExceptStarred(timestamp: Long)

    // 全ての通知をゴミ箱へ移動
    @Query("UPDATE notifications SET isDeleted = 1, deletedTimestamp = :timestamp WHERE isDeleted = 0")
    suspend fun moveAllToTrash(timestamp: Long)

    @Query("UPDATE notifications SET isDeleted = 0, deletedTimestamp = NULL WHERE id = :id")
    suspend fun restore(id: Int)

    @Query("UPDATE notifications SET labelId = :labelId, label = :labelName WHERE id = :notificationId")
    suspend fun updateLabel(notificationId: Int, labelId: Int?, labelName: String?)

    @Query("UPDATE notifications SET labelId = NULL, label = NULL WHERE labelId = :oldLabelId")
    suspend fun clearLabelsForLabelId(oldLabelId: Int)
}
