package com.lumifold.notihold

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class CleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.notificationDao()
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

        // 1. ゴミ箱（isDeleted = true）に移動してから「7日」経過した通知を物理削除
        val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        dao.deletePhysical(threshold = sevenDaysAgo)

        // 2. 通常の通知を受信してから「設定された日数（デフォルト30日）」経過した通知を物理削除
        val retentionDays = prefs.getInt("retention_days", 30)
        if (retentionDays > 0) {
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
            dao.deleteOldActive(cutoffTime)
        }

        return Result.success()
    }
}
