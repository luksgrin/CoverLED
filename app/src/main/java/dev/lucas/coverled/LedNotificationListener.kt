package dev.lucas.coverled

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * NotificationListenerService (spec §6.1). Only package + key are recorded; content is never read.
 * Hosts the IndicatorCoordinator because this is the one long-lived, system-bound component we have.
 */
class LedNotificationListener : NotificationListenerService() {

    private lateinit var state: NotificationState
    private var coordinator: IndicatorCoordinator? = null

    override fun onCreate() {
        super.onCreate()
        state = NotificationState.get(this)
    }

    override fun onListenerConnected() {
        Log.i(TAG, "listener connected")
        instance = this
        coordinator = IndicatorCoordinator(this).also { it.start() }
        // Seed from what is already in the shade so we don't miss anything posted while we were off.
        val current = runCatching { activeNotifications.toList() }.getOrDefault(emptyList())
            .filter { relevant(it) }
            .map { it.packageName to it.key }
        state.replaceAll(current)
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "listener disconnected")
        instance = null
        coordinator?.stop(); coordinator = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!relevant(sbn)) return
        Log.i(TAG, "posted ${sbn.packageName}")
        state.add(sbn.packageName, sbn.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        Log.i(TAG, "removed ${sbn.packageName} reason=$reason")
        state.remove(sbn.packageName, sbn.key)
    }

    /**
     * "Pending" = a user-facing, dismissible notification (spec §8). Excludes ongoing/foreground
     * service notifications (music players, VPN, charging…), group summaries and low-importance noise.
     */
    private fun relevant(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (sbn.isOngoing) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
        if (sbn.packageName == "android" || sbn.packageName == "com.android.systemui") return false
        return true
    }

    companion object {
        private const val TAG = "CoverLED"
        @Volatile private var instance: LedNotificationListener? = null

        /** Debug only: dismiss every non-ongoing notification we consider pending. */
        fun debugClearAll() {
            val l = instance ?: run { Log.w(TAG, "clearAll: listener not connected"); return }
            l.activeNotifications.filter { l.relevant(it) }.forEach { l.cancelNotification(it.key) }
        }
    }
}
