package com.totonoi.sauna.mobile.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.totonoi.sauna.mobile.MainActivity
import com.totonoi.sauna.shared.model.SaunaSession

/** 時計から新しいセッションが届いたときにスマホ側で通知するためのヘルパー。 */
class SessionNotifier(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "session_received"
        private const val NOTIFICATION_ID_BASE = 2000
    }

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ととのい記録の受信通知",
            NotificationManager.IMPORTANCE_DEFAULT,
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
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_BASE + session.id.hashCode(), notification)
    }
}
