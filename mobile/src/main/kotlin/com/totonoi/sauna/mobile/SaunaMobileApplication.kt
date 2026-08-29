package com.totonoi.sauna.mobile

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.totonoi.sauna.mobile.sync.SessionImportWorker
import java.util.concurrent.TimeUnit

class SaunaMobileApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Bluetooth再接続直後のonDataChangedが届かない場合の保険として、15分毎(WorkManagerの最短間隔)に
        // Data Layerの未取り込みセッションをポーリングする。アプリを開かなくても実行される。
        val request = PeriodicWorkRequestBuilder<SessionImportWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "session-import-fallback",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
