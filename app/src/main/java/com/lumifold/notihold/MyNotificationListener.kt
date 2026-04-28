package com.lumifold.notihold

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MyNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val appNameCache = ConcurrentHashMap<String, String>()
    
    // 重複保存対策: パッケージ名 + タイトル + 本文 をキーにして、直近の通知を記憶
    private val lastProcessedNotifications = ConcurrentHashMap<String, Long>()
    private val DUPLICATE_TIMEOUT_MS = 2000L // 2秒以内の全く同じ通知は無視

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        appNameCache.clear()
        lastProcessedNotifications.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName ?: return
        
        scope.launch {
            try {
                processNotification(sbn)
            } catch (e: Exception) {
                Log.e("Notihold", "通知処理エラー", e)
            }
        }
    }

    private suspend fun processNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        
        // 【重複保存対策】
        val duplicateKey = "${sbn.packageName}|$title|$text"
        val currentTime = System.currentTimeMillis()
        val lastTime = lastProcessedNotifications[duplicateKey] ?: 0L
        
        if (currentTime - lastTime < DUPLICATE_TIMEOUT_MS) {
            Log.d("Notihold", "重複通知をスキップしました: $title")
            return
        }
        lastProcessedNotifications[duplicateKey] = currentTime

        // 古いキャッシュを定期的に掃除（簡易的）
        if (lastProcessedNotifications.size > 100) {
            val iterator = lastProcessedNotifications.entries.iterator()
            while (iterator.hasNext()) {
                if (currentTime - iterator.next().value > DUPLICATE_TIMEOUT_MS * 5) {
                    iterator.remove()
                }
            }
        }

        // 1. システム系通知のフィルタリング
        val isSystem = sbn.packageName.startsWith("android") || 
                       sbn.packageName.startsWith("com.android") || 
                       sbn.packageName.startsWith("com.google.android")
        
        if (isSystem && !prefs.getBoolean("filter_system", false)) {
            return
        }

        // 2. 自身の通知は常に除外
        if (sbn.packageName == this.packageName) {
            return
        }

        // 3. 進行中（Ongoing）の通知のフィルタリング
        val isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        if (isOngoing && !prefs.getBoolean("filter_ongoing", false)) {
            return
        }

        // 4. メディア再生コントロールのフィルタリング
        val isMedia = notification.extras?.get(Notification.EXTRA_MEDIA_SESSION) != null
        if (isMedia && !prefs.getBoolean("filter_media", false)) {
            return
        }

        if (title.isEmpty() && text.isEmpty()) return

        val appName = getAppName(sbn.packageName)
        val label = generateSmartLabel(sbn.packageName, title, text)

        // 3段階ロジック & 強制リサイズ
        val cachedIconPath = try {
            saveNotificationIcon(sbn)
        } catch (e: Exception) {
            Log.e("Notihold", "Icon save failed", e)
            null
        }

        val entity = NotificationEntity(
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = System.currentTimeMillis(),
            isStarred = false,
            isDeleted = false,
            label = label,
            isRead = false,
            cachedIconPath = cachedIconPath
        )

        val db = AppDatabase.getDatabase(applicationContext)
        db.notificationDao().insert(entity)
        Log.d("Notihold", "保存完了: $title")
    }

    private fun saveNotificationIcon(sbn: StatusBarNotification): String? {
        val context = applicationContext
        val pm = context.packageManager
        
        var drawable = sbn.notification.smallIcon?.loadDrawable(context)
        
        if (drawable == null) {
            try {
                drawable = pm.getApplicationIcon(sbn.packageName)
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (drawable == null) {
            return null
        }

        val size = AppIconFetcher.calculateOptimalSize(context.resources.displayMetrics)
        val bitmap = AppIconFetcher.optimizeIconStatic(context, drawable, size).let {
            it.toBitmap(size, size, Bitmap.Config.ARGB_8888)
        }

        val fileName = "icon_${UUID.randomUUID()}.png"
        val file = File(context.cacheDir, "icons").apply { if (!exists()) mkdirs() }.let { File(it, fileName) }
        
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        
        return file.absolutePath
    }

    private fun getAppName(packageName: String): String {
        return appNameCache.getOrPut(packageName) {
            try {
                val pm = applicationContext.packageManager
                val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
        }
    }

    private fun generateSmartLabel(packageName: String, title: String, text: String): String? {
        val combinedText = "$title $text".lowercase()
        return when {
            combinedText.contains("緊急") || combinedText.contains("重要") -> "緊急・重要"
            combinedText.contains("銀行") || combinedText.contains("振込") || combinedText.contains("bank") -> "金融・銀行"
            combinedText.contains("メッセージ") || combinedText.contains("chat") || combinedText.contains("line") -> "SNS・メッセージ"
            combinedText.contains("配送") || combinedText.contains("発送") || combinedText.contains("delivery") -> "ショッピング・EC"
            combinedText.contains("予約") || combinedText.contains("予定") || combinedText.contains("カレンダー") -> "予約・予定"
            combinedText.contains("セール") || combinedText.contains("クーポン") || combinedText.contains("sale") -> "割引・クーポン"
            combinedText.contains("ニュース") || combinedText.contains("news") || combinedText.contains("速報") -> "ニュース・情報"
            combinedText.contains("会議") || combinedText.contains("仕事") || combinedText.contains("work") -> "ビジネス・仕事"
            else -> null
        }
    }
}
