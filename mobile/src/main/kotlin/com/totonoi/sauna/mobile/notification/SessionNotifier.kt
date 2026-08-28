package com.totonoi.sauna.mobile.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.totonoi.sauna.mobile.MainActivity
import com.totonoi.sauna.shared.model.SaunaSession

/** 時計から新しいセッションが届いたときにスマホ側で通知するためのヘルパー。 */
class SessionNotifier(private val context: Context) {

    companion object {
        // 旧チャンネルID。importanceを後からコードで変更しても既存インストールには反映されないため、
        // 一度作成済みのチャンネルは破棄しIDを変えて作り直す。
        private const val OLD_CHANNEL_ID = "session_received"
        private const val CHANNEL_ID = "session_received_v2"
        private const val NOTIFICATION_ID_BASE = 2000
    }

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ととのい記録の受信通知",
            // 見落とされないよう、通常のバナー通知(ヘッドアップ)として表示させる
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }

    fun notifyNewSession(session: SaunaSession) {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("ととのい記録が届きました")
            .setContentText("ととのい値 ${session.totonoiScore.toInt()} / ${session.cycleCount}セット")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        // POST_NOTIFICATIONS未許可の場合はSecurityExceptionを避けるためcompat経由でチェック付き送信する
        NotificationManagerCompat.from(context).apply {
            if (areNotificationsEnabled()) {
                notify(NOTIFICATION_ID_BASE + session.id.hashCode(), notification)
            }
        }
    }
}
