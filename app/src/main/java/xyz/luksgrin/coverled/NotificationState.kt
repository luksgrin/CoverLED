package xyz.luksgrin.coverled

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Notification State Manager (spec §6.1). Tracks, per package, the set of notification keys
 * currently posted and not yet removed. Stores only package names + keys — never content.
 */
class NotificationState private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("notif_state", Context.MODE_PRIVATE)
    private val pending = HashMap<String, MutableSet<String>>()   // pkg -> notification keys
    private val firstSeen = HashMap<String, Long>()               // pkg -> when it first became pending
    private val order = context.getSharedPreferences("notif_order", Context.MODE_PRIVATE)

    private val _snapshot = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** pkg -> count, only packages with ≥1 pending notification. */
    val snapshot: StateFlow<Map<String, Int>> = _snapshot

    init {
        prefs.all.forEach { (pkg, v) ->
            @Suppress("UNCHECKED_CAST")
            (v as? Set<String>)?.let { if (it.isNotEmpty()) pending[pkg] = it.toMutableSet() }
        }
        order.all.forEach { (pkg, v) -> (v as? Long)?.let { if (pkg in pending) firstSeen[pkg] = it } }
        publish()
    }

    /** Order in which packages became pending (oldest first). */
    fun firstSeenOrder(): Map<String, Long> = synchronized(this) { firstSeen.toMap() }

    private fun touchSeen(pkg: String) {
        if (pkg !in firstSeen) { val t = System.currentTimeMillis(); firstSeen[pkg] = t; order.edit().putLong(pkg, t).apply() }
    }
    private fun forgetSeen(pkg: String) { firstSeen.remove(pkg); order.edit().remove(pkg).apply() }

    @Synchronized
    fun add(pkg: String, key: String) {
        if (pending.getOrPut(pkg) { HashSet() }.add(key)) { touchSeen(pkg); persist(pkg); publish() }
    }

    @Synchronized
    fun remove(pkg: String, key: String) {
        val set = pending[pkg] ?: return
        if (set.remove(key)) {
            if (set.isEmpty()) { pending.remove(pkg); forgetSeen(pkg) }
            persist(pkg); publish()
        }
    }

    /** Replace everything with what the listener currently sees (on connect / rank update). */
    @Synchronized
    fun replaceAll(entries: List<Pair<String, String>>) {
        pending.clear()
        entries.forEach { (pkg, key) -> pending.getOrPut(pkg) { HashSet() }.add(key) }
        prefs.edit().clear().also { e -> pending.forEach { (p, s) -> e.putStringSet(p, s) } }.apply()
        firstSeen.keys.retainAll(pending.keys); pending.keys.forEach { touchSeen(it) }
        order.edit().clear().also { e -> firstSeen.forEach { (p, t) -> e.putLong(p, t) } }.apply()
        publish()
    }

    @Synchronized
    fun clear() { pending.clear(); firstSeen.clear(); prefs.edit().clear().apply(); order.edit().clear().apply(); publish() }

    private fun persist(pkg: String) {
        prefs.edit().apply {
            val set = pending[pkg]
            if (set.isNullOrEmpty()) remove(pkg) else putStringSet(pkg, set)
        }.apply()
    }

    private fun publish() {
        val snap = pending.mapValues { it.value.size }
        _snapshot.value = snap
        Log.i(TAG, "state: ${if (snap.isEmpty()) "(none)" else snap.entries.joinToString { "${it.key}=${it.value}" }}")
    }

    companion object {
        private const val TAG = "CoverLED"
        @Volatile private var instance: NotificationState? = null
        fun get(context: Context): NotificationState =
            instance ?: synchronized(this) {
                instance ?: NotificationState(context.applicationContext).also { instance = it }
            }
    }
}
