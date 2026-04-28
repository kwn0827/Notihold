package com.lumifold.notihold

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class NotiHoldWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.noti_hold_widget)

            // Launch app when widget is clicked
            val launchIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)

            // Simple widget UI without database access
            views.setTextViewText(R.id.widget_title, "NotiHold")
            views.setTextViewText(R.id.notification_1, "重要通知を確認")
            views.setTextViewText(R.id.notification_2, "タップして開く")
            views.setTextViewText(R.id.notification_3, "")
            views.setTextViewText(R.id.widget_subtitle, "通知管理アプリ")

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
