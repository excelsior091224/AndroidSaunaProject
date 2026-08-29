package com.totonoi.sauna.mobile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.totonoi.sauna.mobile.notification.SessionNotifier

/**
 * BIND_LISTENERの即時コールバックが届かなかった場合の保険として、
 * 定期的にData Layer上の未取り込みセッションを取り込み通知する。
 */
class SessionImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val imported = SessionDataLayerImporter(applicationContext).importPendingSessions()
        if (imported.isNotEmpty()) {
            val notifier = SessionNotifier(applicationContext)
            imported.forEach { notifier.notifyNewSession(it) }
        }
        return Result.success()
    }
}
