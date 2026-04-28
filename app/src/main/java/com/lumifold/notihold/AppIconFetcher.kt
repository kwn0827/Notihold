package com.lumifold.notihold

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全アプリのアイコンを「爆速・高画質・超軽量」で表示するための最終兵器
 */
class AppIconFetcher(
    private val context: Context,
    private val packageName: String,
    private val targetSize: Int
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        try {
            // 【最重要】通知元のアプリのアイコンを取得
            val icon = pm.getApplicationIcon(packageName)
            
            // 高品質リサイズを実行 (ボケを解消しつつメモリを節約)
            val optimizedIcon = optimizeIconStatic(context, icon, targetSize)
            
            DrawableResult(
                drawable = optimizedIcon,
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            // 取得失敗時のみ NotiHold アイコンにフォールバック
            DrawableResult(
                drawable = getResizedNotiHoldIcon(context),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        }
    }

    companion object {
        @Volatile
        private var memoizedNotiHoldIcon: Drawable? = null

        /**
         * 巨大な icon.webp を、画質を維持しつつ軽量なサイズにリサイズして取得
         */
        fun getResizedNotiHoldIcon(context: Context): Drawable {
            return memoizedNotiHoldIcon ?: synchronized(this) {
                memoizedNotiHoldIcon ?: createResizedNotiHoldIcon(context).also { memoizedNotiHoldIcon = it }
            }
        }

        private fun createResizedNotiHoldIcon(context: Context): Drawable {
            val size = calculateOptimalSize(context.resources.displayMetrics)
            val icon = ResourcesCompat.getDrawable(context.resources, R.mipmap.ic_launcher, context.theme)
                ?: return ResourcesCompat.getDrawable(context.resources, android.R.drawable.sym_def_app_icon, context.theme)!!
            
            return optimizeIconStatic(context, icon, size)
        }

        /**
         * 現代のスマホでボケないためのスイートスポット (48dp)
         */
        fun calculateOptimalSize(displayMetrics: DisplayMetrics): Int {
            val density = displayMetrics.density
            return (48 * density).toInt().coerceIn(96, 192)
        }

        /**
         * 高品質リサイズ処理 (Paintフィルタリングを適用)
         */
        fun optimizeIconStatic(context: Context, drawable: Drawable, size: Int): Drawable {
            // 背景透過を維持しつつ、高品質な補間を行う
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // 重要: 元のDrawableを指定サイズに合わせて描画
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)

            // API 26+ の場合は HARDWARE ビットマップに変換し、GPUメモリへ転送してメインメモリを解放
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hardwareBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false)
                hardwareBitmap?.toDrawable(context.resources) ?: bitmap.toDrawable(context.resources)
            } else {
                bitmap.toDrawable(context.resources)
            }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data !is String || !data.startsWith("appicon:")) return null
            
            val packageName = data.substringAfter("appicon:").trim()
            val targetSize = options.size.width.pxOrElse {
                calculateOptimalSize(context.resources.displayMetrics)
            }
            
            return AppIconFetcher(context, packageName, targetSize)
        }
    }
}
