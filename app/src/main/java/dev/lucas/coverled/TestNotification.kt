package dev.lucas.coverled

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/** Our own test notification (developer screen + adb `--ei testnotif`). */
object TestNotification {
    private const val CHANNEL = "test"
    private const val ID = 4242

    fun post(context: Context, post: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!post) { nm.cancel(ID); return }
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.test_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_dot)
            .setContentTitle(context.getString(R.string.test_notif_title))
            .setContentText(context.getString(R.string.test_notif_text))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID, n) }
    }
}
