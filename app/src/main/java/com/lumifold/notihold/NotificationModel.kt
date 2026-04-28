package com.lumifold.notihold

// UI表示用の基底クラス
sealed class NotificationListItem {
    data class Header(val title: String) : NotificationListItem()
    data class DateHeader(val date: String, val timestamp: Long) : NotificationListItem()  // 日付ヘッダー
    data class Item(val model: NotificationDisplayModel) : NotificationListItem()
}

// 構造改革: UIスレッドで1ミリ秒も止めないための、表示確定済みデータクラス
data class NotificationDisplayModel(
    val id: Long,
    val iconPath: String?,         // キャッシュされたアイコンのパス
    val packageNameUri: String,    // "appicon:[packageName]" 形式 (フォールバック用)
    val appName: String,
    val title: String,
    val text: String,
    val formattedTime: String,     // 計算済み時刻
    val isBold: Boolean,           // スタイル確定済み
    val isTrashMode: Boolean,      // モード確定済み
    val starIconRes: Int,          // 確定済みアイコンID
    val starTint: Int,             // 確定済み色
    val label: String?,
    val isStarred: Boolean,
    val targetSize: Int? = null   // DPIに基づいた最適なサイズ
)
