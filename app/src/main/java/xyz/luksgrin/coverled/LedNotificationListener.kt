package xyz.luksgrin.coverled

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
    private lateinit var colors: AppColors
    private var coordinator: IndicatorCoordinator? = null

    override fun onCreate() {
        super.onCreate()
        state = NotificationState.get(this)
        colors = AppColors(this)
    }

    override fun onListenerConnected() {
        Log.i(TAG, "listener connected")
        instance = this
        coordinator = IndicatorCoordinator(this).also {
            it.start()
            it.setDndActive(isDnd(currentInterruptionFilter))
        }
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

    override fun onInterruptionFilterChanged(filter: Int) {
        coordinator?.setDndActive(isDnd(filter))
    }

    /** Anything other than "all notifications" counts as Do Not Disturb (priority only, alarms only, none). */
    private fun isDnd(filter: Int) = filter != INTERRUPTION_FILTER_ALL && filter != INTERRUPTION_FILTER_UNKNOWN

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!relevant(sbn)) return
        Log.i(TAG, "posted ${sbn.packageName}")
        colors.markSeen(sbn.packageName)
        colors.learnFromNotification(sbn.packageName, declaredLightColor(sbn), sbn.notification.color)
        state.add(sbn.packageName, sbn.key)
    }

    /**
     * What the Galaxy LED used. NotificationChannel.lightColor is not readable by third-party
     * listeners (getNotificationChannel is a system API), so only the legacy per-notification
     * ledARGB is available; the accent color and the icon cover the rest.
     */
    @Suppress("DEPRECATION")
    private fun declaredLightColor(sbn: StatusBarNotification): Int {
        val n = sbn.notification
        return if (n.flags and Notification.FLAG_SHOW_LIGHTS != 0) n.ledARGB else 0
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
        if (colors.isIgnored(sbn.packageName)) return false
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
