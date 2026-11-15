package app.escudo.shield

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import app.escudo.core.FeedAction
import app.escudo.core.FeedRule
import app.escudo.optimize.Optimizer
import app.escudo.core.FeedRules
import app.escudo.data.Prefs

/**
 * Escudo anti-algoritmo.
 *
 * Usa el servicio de accesibilidad de Android para saber qué pantalla tienes
 * delante (solo la estructura de vistas, nunca el texto que escribes) y:
 *  1. Te saca de los feeds infinitos (Reels, Shorts…) según las reglas.
 *  2. Muestra una "pausa consciente" antes de abrir las apps que elijas.
 *  3. Aplica límites de tiempo diarios por app.
 * No hace ninguna conexión de red: todo se decide en el teléfono.
 */
class FeedShieldService : AccessibilityService() {
    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())

    private var rules: Map<String, List<FeedRule>> = emptyMap()
    private var rulesText = ""
    private var foregroundPkg: String? = null
    private var foregroundSince = 0L
    private var screenOn = true
    private var lastScan = 0L
    private var lastAction = 0L
    private val lastNotice = HashMap<String, Long>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { flushUsage(); screenOn = false }
                Intent.ACTION_SCREEN_ON -> { screenOn = true; foregroundSince = SystemClock.elapsedRealtime() }
            }
        }
    }

    /** Revisa cada 30 s si la app en primer plano ha superado su límite. */
    private val limitTick = object : Runnable {
        override fun run() {
            flushUsage()
            foregroundPkg?.let { enforceLimit(it) }
            Optimizer.applyGraySchedule(this@FeedShieldService)
            main.postDelayed(this, 30_000)
        }
    }

    override fun onServiceConnected() {
        prefs = Prefs(this)
        prefs.cleanupOldUsage()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        })
        main.postDelayed(limitTick, 30_000)
        ShieldState.connected = true
    }

    override fun onDestroy() {
        ShieldState.connected = false
        main.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in IGNORED_PKGS) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != foregroundPkg) {
            onAppChanged(pkg)
        }
        if (!prefs.shieldEnabled || System.currentTimeMillis() < prefs.shieldSnoozeUntil) return

        val pkgRules = currentRules()[pkg] ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAction < 1500) return
        // Recorrer el árbol de vistas cuesta: como mucho 2 veces por segundo
        if (now - lastScan < 500 && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        val hit = pkgRules.firstOrNull { it.wholeApp || containsViewId(root, it.viewId) } ?: return
        lastAction = now
        when (hit.action) {
            FeedAction.NOTICE -> {
                // Un recordatorio como mucho cada 3 minutos por feed: informar, no molestar
                val key = hit.pkg + hit.label
                if (now - (lastNotice[key] ?: -NOTICE_EVERY) < NOTICE_EVERY) return
                lastNotice[key] = now
                prefs.feedsBlockedCount = prefs.feedsBlockedCount + 1
                toast(NOTICES[(prefs.feedsBlockedCount % NOTICES.size).toInt()].format(hit.label), long = true)
            }
            FeedAction.BACK, FeedAction.HOME -> {
                prefs.feedsBlockedCount = prefs.feedsBlockedCount + 1
                performGlobalAction(if (hit.action == FeedAction.HOME) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
                toast("Escudo: has salido de ${hit.label}")
            }
        }
    }

    private fun onAppChanged(pkg: String) {
        flushUsage()
        foregroundPkg = pkg
        foregroundSince = SystemClock.elapsedRealtime()
        if (enforceLimit(pkg)) return

        val pauseSecs = prefs.pauseApps[pkg] ?: return
        if (System.currentTimeMillis() < prefs.allowUntil(pkg)) return
        startActivity(
            Intent(this, PauseActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(PauseActivity.EXTRA_PKG, pkg)
                .putExtra(PauseActivity.EXTRA_SECONDS, pauseSecs)
        )
    }

    /** Devuelve true si la app ya superó su límite diario y la hemos cerrado. */
    private fun enforceLimit(pkg: String): Boolean {
        val limitMin = prefs.dailyLimits[pkg] ?: return false
        if (prefs.usageToday(pkg) < limitMin * 60_000L) return false
        performGlobalAction(GLOBAL_ACTION_HOME)
        toast("Límite diario de $limitMin min alcanzado")
        return true
    }

    private fun flushUsage() {
        val pkg = foregroundPkg ?: return
        if (!screenOn) return
        val now = SystemClock.elapsedRealtime()
        val delta = now - foregroundSince
        foregroundSince = now
        if (delta in 1..(10 * 60_000L)) prefs.addUsageToday(pkg, delta)
    }

    private fun currentRules(): Map<String, List<FeedRule>> {
        val t = prefs.feedRulesText
        if (t != rulesText) {
            rulesText = t
            rules = FeedRules.parse(t).groupBy { it.pkg }
        }
        return rules
    }

    /** Búsqueda en anchura del id de vista, limitada para no gastar batería. */
    private fun containsViewId(root: AccessibilityNodeInfo, fragment: String): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val n = queue.removeFirst()
            visited++
            val id = n.viewIdResourceName
            if (id != null && id.contains(fragment) && n.isVisibleToUser) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun toast(msg: String, long: Boolean = false) =
        main.post { Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }

    companion object {
        private const val MAX_NODES = 800
        private const val NOTICE_EVERY = 3 * 60_000L

        /** Recordatorios que explican qué está pasando, en lugar de cerrar la app. */
        private val NOTICES = listOf(
            "Estás en %s: cada segundo que miras aquí le enseña al algoritmo qué te engancha.",
            "%s: el vídeo siguiente está elegido para que no pares. Tú decides cuándo salir.",
            "%s está diseñado sin final. ¿Sigues aquí porque quieres?",
            "En %s, pasar rápido lo que no te aporta le da al algoritmo menos datos sobre ti.",
        )
        private val IGNORED_PKGS = setOf(
            "com.android.systemui", "android", "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard", "com.touchtype.swiftkey",
        )
    }
}

object ShieldState {
    @Volatile var connected = false
}
