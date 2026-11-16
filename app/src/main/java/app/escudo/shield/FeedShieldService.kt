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
import app.escudo.core.AppCategory
import app.escudo.core.BudgetMode
import app.escudo.core.Game
import app.escudo.game.GameEngine
import app.escudo.game.GameStore

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

    // Estado de la sesión actual en primer plano (para recordatorios y presupuestos)
    private lateinit var game: GameStore
    private lateinit var overlay: Overlay
    private var shortSince = 0L            // desde cuándo estás en vídeo corto en esta sesión (0 = no)
    private var lastShortSeen = 0L         // última vez que vimos un feed de vídeo corto en pantalla
    private var shortLabel = ""            // "Reels de Instagram", "Shorts de YouTube", "TikTok"…
    private var nextCheckInMin = 0
    private var checkInCount = 0
    private val lastOverWarn = HashMap<AppCategory, Long>()
    private val labels = HashMap<String, String>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { flushUsage(); screenOn = false; endShort(); overlay.dismiss() }
                Intent.ACTION_SCREEN_ON -> { screenOn = true; foregroundSince = SystemClock.elapsedRealtime() }
            }
        }
    }

    /** Cada 15 s: tiempo de uso, límites, presupuestos, recordatorios, modo foco y cierre del día. */
    private val limitTick = object : Runnable {
        override fun run() {
            val ctx = this@FeedShieldService
            try {
                flushUsage()
                if (game.lastDay != java.time.LocalDate.now().toString()) {
                    GameEngine.rollover(ctx); prefs.cleanupOldUsage()
                }
                GameEngine.checkFocus(ctx)
                // Si llevas más de 45 s sin ver ningún feed de vídeo corto, la sesión de scroll termina
                if (shortSince != 0L && SystemClock.elapsedRealtime() - lastShortSeen > SHORT_GRACE) endShort()
                foregroundPkg?.let { pkg ->
                    if (screenOn && !enforceLimit(pkg) && !enforceBudget(pkg)) checkIn(pkg)
                }
                Optimizer.applyGraySchedule(ctx)
            } catch (_: Exception) {}
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        prefs = Prefs(this)
        game = GameStore(this)
        overlay = Overlay(this)
        GameEngine.rollover(this) // cierra el día anterior antes de borrar su uso
        prefs.cleanupOldUsage()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        })
        main.postDelayed(limitTick, TICK_MS)
        ShieldState.connected = true
    }

    override fun onDestroy() {
        ShieldState.connected = false
        main.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.dismiss()
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
        val now = SystemClock.elapsedRealtime()

        // Apps que son vídeo corto de principio a fin (TikTok…), según su categoría
        if (GameEngine.categoryOf(this, pkg) == AppCategory.SHORT_VIDEO) markShort(label(pkg), now)

        val pkgRules = currentRules()[pkg] ?: return
        if (now - lastAction < 1500) return
        // Recorrer el árbol de vistas cuesta: como mucho 2 veces por segundo
        if (now - lastScan < 500 && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val hit = pkgRules.firstOrNull { it.wholeApp || matches(root, it) } ?: return
        lastAction = now
        when (hit.action) {
            FeedAction.NOTICE -> {
                // Estás en vídeo corto: cuentan los recordatorios de tiempo (aunque los avisos estén apagados)
                markShort(hit.label, now)
                if (!prefs.shieldEnabled || System.currentTimeMillis() < prefs.shieldSnoozeUntil) return
                // Un aviso al entrar (como mucho cada 3 min); después hablan los recordatorios
                val key = hit.pkg + hit.label
                if (now - (lastNotice[key] ?: -NOTICE_EVERY) < NOTICE_EVERY) return
                lastNotice[key] = now
                prefs.feedsBlockedCount = prefs.feedsBlockedCount + 1
                toast(NOTICES[(prefs.feedsBlockedCount % NOTICES.size).toInt()].format(hit.label), long = true)
            }
            FeedAction.BACK, FeedAction.HOME -> {
                if (!prefs.shieldEnabled || System.currentTimeMillis() < prefs.shieldSnoozeUntil) return
                prefs.feedsBlockedCount = prefs.feedsBlockedCount + 1
                performGlobalAction(if (hit.action == FeedAction.HOME) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
                toast("Escudo: has salido de ${hit.label}")
            }
        }
    }

    private fun markShort(label: String, now: Long) {
        if (shortSince == 0L) {
            shortSince = now
            nextCheckInMin = game.checkInMinutes
        }
        lastShortSeen = now
        shortLabel = label
    }

    private fun endShort() { shortSince = 0; lastShortSeen = 0 }

    private fun onAppChanged(pkg: String) {
        flushUsage()
        foregroundPkg = pkg
        foregroundSince = SystemClock.elapsedRealtime()
        // Al volver al escritorio la tarjeta se queda (normalmente la acabamos de mostrar al sacarte)
        if (!isLauncher(pkg)) overlay.dismiss()
        val cat = GameEngine.categoryOf(this, pkg)
        if (cat.trap) {
            GameEngine.onTrapOpened(this)
            if (game.focusActive) { focusBlock(); return }
        }
        if (enforceLimit(pkg)) return
        if (enforceBudget(pkg)) return

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

    private fun label(pkg: String) = labels.getOrPut(pkg) {
        try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
    }

    private fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)

    private val launchers: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet()
    }

    private fun isLauncher(pkg: String) = pkg in launchers

    private fun focusBlock() {
        goHome()
        val left = ((game.focusUntil - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)
        overlay.show(
            "🎯 Modo foco: quedan $left min",
            "Esta app es de tus apps trampa. Aguanta y ganas ${game.focusLength + Game.Xp.FOCUS_BONUS} XP.\n" +
                "Idea: ${Game.ALTERNATIVES.random()}",
            listOf(
                Overlay.Action("Rendirme") { GameEngine.giveUpFocus(this) },
                Overlay.Action("¡Aguanto! 💪", primary = true) {},
            ),
        )
    }

    /**
     * Presupuesto diario por categoría. Devuelve true si ha cerrado la app.
     * Modo "avisar": avisa al 80 % y al agotarlo (cada 10 min). Modo "bloquear": cierra al agotarlo.
     */
    private fun enforceBudget(pkg: String): Boolean {
        val cat = GameEngine.categoryOf(this, pkg)
        val budget = game.budget(cat)
        val mode = game.mode(cat)
        if (budget <= 0 || mode == BudgetMode.COUNT) return false
        if (System.currentTimeMillis() < game.extraUntil(cat)) return false
        val used = GameEngine.minutesOfPackages(this, cat)
        val now = SystemClock.elapsedRealtime()

        if (used >= budget) {
            if (mode == BudgetMode.BLOCK) {
                goHome()
                val actions = mutableListOf(Overlay.Action("Vale 👍", primary = true) {})
                if (!game.extraUsedToday(cat) && game.xp >= Game.Xp.EXTRA_TIME_COST) {
                    actions.add(0, Overlay.Action("Comodín 5 min (−${Game.Xp.EXTRA_TIME_COST} XP)") {
                        if (GameEngine.buyExtraTime(this, cat)) {
                            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    })
                }
                overlay.show(
                    "🔒 ${cat.emoji} ${cat.label}: presupuesto agotado",
                    "Has usado tus $budget min de hoy. Mañana se recarga.\nIdea: ${Game.ALTERNATIVES.random()}",
                    actions,
                )
                return true
            }
            if (now - (lastOverWarn[cat] ?: -OVER_EVERY) >= OVER_EVERY && !overlay.showing) {
                lastOverWarn[cat] = now
                val streak = game.streak.days
                overlay.show(
                    "🚩 ${cat.emoji} Presupuesto de ${cat.label} gastado",
                    "Llevas $used de $budget min hoy. " +
                        (if (streak > 0) "Si sigues, hoy no sumará a tu racha 🔥$streak (salvo comodín ❄️). " else "") +
                        "¿Lo dejas aquí?",
                    listOf(
                        Overlay.Action("Sigo") {},
                        Overlay.Action("Salgo +${Game.Xp.BUDGET_EXIT} XP", primary = true) { goHome(); GameEngine.onBudgetExit(this, cat) },
                    ),
                )
            }
            return false
        }
        if (used * 10 >= budget * 8 && !game.warned("80_${cat.id}") && !overlay.showing) {
            game.setWarned("80_${cat.id}")
            overlay.show(
                "⚠️ Te quedan ${budget - used} min de ${cat.label}",
                "Ya has usado el 80 % de tu presupuesto de hoy. Úsalos en lo que más te importe.",
                listOf(Overlay.Action("Entendido", primary = true) {}),
            )
        }
        return false
    }

    /** Recordatorio cada pocos minutos mientras estás en vídeo corto. */
    private fun checkIn(pkg: String) {
        if (!game.checkInEnabled || shortSince == 0L || overlay.showing) return
        val minutes = ((SystemClock.elapsedRealtime() - shortSince) / 60_000).toInt()
        if (minutes < nextCheckInMin) return
        nextCheckInMin = minutes + game.checkInMinutes
        val app = shortLabel.ifBlank { label(pkg) }
        overlay.show(
            "⏱️ $minutes min en $app",
            Game.checkInText(checkInCount++, minutes, app, game.goal),
            listOf(
                Overlay.Action("Me aporta, sigo") {},
                Overlay.Action("Me salgo 💪 +${Game.Xp.REEL_EXIT} XP", primary = true) {
                    goHome(); endShort(); GameEngine.onReelExit(this)
                },
            ),
        )
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

    /**
     * ¿Está en pantalla alguna de las vistas de la regla? Primero la búsqueda
     * rápida de Android por id exacto; si no, un recorrido en anchura limitado
     * que busca fragmentos de id y pestañas seleccionadas.
     */
    private fun matches(root: AccessibilityNodeInfo, rule: FeedRule): Boolean {
        val frags = rule.fragments
        val tabs = rule.selectedTabs
        for (f in frags) {
            val exact = try { root.findAccessibilityNodeInfosByViewId("${rule.pkg}:id/$f") } catch (_: Exception) { null }
            if (exact?.any { it.isVisibleToUser } == true) return true
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val n = queue.removeFirst()
            visited++
            val id = n.viewIdResourceName
            if (id != null && n.isVisibleToUser && frags.any { id.contains(it) }) return true
            if (tabs.isNotEmpty() && n.isSelected) {
                val name = (n.contentDescription ?: n.text)?.toString()?.trim()
                if (name != null && tabs.any { name.equals(it, true) }) return true
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun toast(msg: String, long: Boolean = false) =
        main.post { Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }

    companion object {
        private const val MAX_NODES = 2500
        private const val SHORT_GRACE = 45_000L
        private const val NOTICE_EVERY = 3 * 60_000L
        private const val TICK_MS = 15_000L
        private const val OVER_EVERY = 10 * 60_000L

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
