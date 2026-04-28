package com.lumifold.notihold

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlin.system.exitProcess

class MyApplication : Application(), ImageLoaderFactory {
    
    override fun onCreate() {
        super.onCreate()
        
        // グローバル例外ハンドラを設定
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            exception.printStackTrace()
            // クラッシュログを保存（必要に応じて実装）
            exitProcess(1)
        }
    }
    
    override fun newImageLoader(): ImageLoader {
        return try {
            ImageLoader.Builder(this)
                .components {
                    add(AppIconFetcher.Factory(this@MyApplication))
                }
                .crossfade(true)
                .build()
        } catch (e: Exception) {
            // ImageLoader初期化失敗時のフォールバック
            ImageLoader.Builder(this).build()
        }
    }
}
