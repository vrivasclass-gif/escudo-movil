package app.escudo.game

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.escudo.core.AppCategory
import app.escudo.core.BudgetMode
import app.escudo.core.Categorizer
import app.escudo.core.FeedRules
import app.escudo.core.Game
import app.escudo.data.Prefs
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

/** Todo el estado del juego, guardado solo en el teléfono. */
class GameStore(ctx: Context) {
    private val p: SharedPreferences = ctx.applicationContext.getSharedPreferences("game", Context.MODE_PRIVATE)
    private fun today() = LocalDate.now().toString()

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()
    var name: String
        get() = p.getString("name", "") ?: ""
        set(v) = p.edit().putString("name", v).apply()
    var avatar: String
        get() = p.getString("avatar", "🐣") ?: "🐣"
        set(v) = p.edit().putString("avatar", v).apply()
    var goal: String
        get() = p.getString("goal", "") ?: ""
        set(v) = p.edit().putString("goal", v).apply()

    var xp: Int
        get() = p.getInt("xp", 0)
        set(v) = p.edit().putInt("xp", v.coerceAtLeast(0)).apply()

    var streak: Game.Streak
        get() = Game.Streak(p.getInt("streak", 0), p.getInt("best", 0), p.getInt("freezes", 1))
        set(v) = p.edit().putInt("streak", v.days).putInt("best", v.best).putInt("freezes", v.freezes).apply()

    var unlocked: Set<String>
        get() = p.getStringSet("unlocked", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("unlocked", v).apply()

    fun total(key: String) = p.getInt("t_$key", 0)
    fun addTotal(key: String, n: Int = 1) = p.edit().putInt("t_$key", total(key) + n).apply()

    // Contadores del día
    fun day(key: String, date: String = today()) = p.getInt("d_${date}_$key", 0)
    fun addDay(key: String, n: Int = 1) = p.edit().putInt("d_${today()}_$key", day(key) + n).apply()
    fun firstTrapMinute(date: String = today()) = p.getInt("d_${date}_firsttrap", -1)
    fun setFirstTrapMinute(m: Int) { if (firstTrapMinute() < 0) p.edit().putInt("d_${today()}_firsttrap", m).apply() }

    var lastDay: String?
        get() = p.getString("last_day", null)
        set(v) = p.edit().putString("last_day", v).apply()

    // Reto del día
    var challengeId: String?
        get() = if (p.getString("ch_day", null) == today()) p.getString("ch_id", null) else null
        set(v) = p.edit().putString("ch_id", v).putString("ch_day", today()).apply()
    fun challengeIdFor(date: String) = if (p.getString("ch_day", null) == date) p.getString("ch_id", null) else null
    var challengeDone: Boolean
        get() = p.getString("ch_done", null) == today()
        set(v) = p.edit().putString("ch_done", if (v) today() else null).apply()
    fun challengeDoneOn(date: String) = p.getString("ch_done", null) == date

    // Presupuestos por categoría
    fun budget(c: AppCategory) = p.getInt("budget_${c.id}", c.defaultBudget)
    fun setBudget(c: AppCategory, m: Int) = p.edit().putInt("budget_${c.id}", m.coerceIn(0, 600)).apply()
    fun budgets() = AppCategory.entries.associateWith { budget(it) }
    fun mode(c: AppCategory) = BudgetMode.byId(p.getString("mode_${c.id}", if (c.trap) "warn" else "count"))
    fun setMode(c: AppCategory, m: BudgetMode) = p.edit().putString("mode_${c.id}", m.id).apply()

    /** Apps que el usuario ha movido de categoría: paquete → id de categoría. */
    var overrides: Map<String, String>
        get() = Prefs.parseStringMap(p.getString("overrides", "") ?: "")
        set(v) = p.edit().putString("overrides", v.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()

    // Recordatorios en vídeo corto
    var checkInEnabled: Boolean
        get() = p.getBoolean("checkin_on", true)
        set(v) = p.edit().putBoolean("checkin_on", v).apply()
    var checkInMinutes: Int
        get() = p.getInt("checkin_min", 5)
        set(v) = p.edit().putInt("checkin_min", v.coerceIn(2, 30)).apply()

    // Modo foco
    var focusUntil: Long
        get() = p.getLong("focus_until", 0)
        set(v) = p.edit().putLong("focus_until", v).apply()
    var focusLength: Int
        get() = p.getInt("focus_len", 25)
        set(v) = p.edit().putInt("focus_len", v).apply()
    val focusActive get() = System.currentTimeMillis() < focusUntil

    // Comodín de 5 minutos por categoría y día
    fun extraUntil(c: AppCategory) = p.getLong("extra_${c.id}", 0)
    fun extraUsedToday(c: AppCategory) = p.getString("extra_day_${c.id}", null) == today()
    fun grantExtra(c: AppCategory) = p.edit()
        .putLong("extra_${c.id}", System.currentTimeMillis() + 5 * 60_000)
        .putString("extra_day_${c.id}", today()).apply()

    // Avisos de presupuesto ya mostrados hoy
    fun warned(key: String) = p.getString("warn_$key", null) == today()
    fun setWarned(key: String) = p.edit().putString("warn_$key", today()).apply()

    // Historial: "date" → "cat=min,cat=min"
    fun saveHistory(date: String, minutes: Map<AppCategory, Int>) =
        p.edit().putString("hist_$date", minutes.entries.joinToString(",") { "${it.key.id}=${it.value}" }).apply()

    fun history(date: String): Map<AppCategory, Int> =
        (p.getString("hist_$date", "") ?: "").split(',').mapNotNull {
            val i = it.indexOf('='); if (i <= 0) null else (AppCategory.byId(it.substring(0, i)) ?: return@mapNotNull null) to (it.substring(i + 1).toIntOrNull() ?: 0)
        }.toMap()

    // Actividad reciente (para la pantalla Yo)
    fun log(text: String, xp: Int) {
        val lines = (p.getString("feed", "") ?: "").lines().filter { it.isNotBlank() }.takeLast(29)
        p.edit().putString("feed", (lines + "${System.currentTimeMillis()}|$xp|$text").joinToString("\n")).apply()
    }

    fun feed(): List<Triple<Long, Int, String>> = (p.getString("feed", "") ?: "").lines().mapNotNull {
        val parts = it.split('|', limit = 3); if (parts.size < 3) null else Triple(parts[0].toLongOrNull() ?: 0, parts[1].toIntOrNull() ?: 0, parts[2])
    }.reversed()

    fun cleanup(keepDays: Long = 28) {
        val cutoff = LocalDate.now().minusDays(keepDays).toString()
        val e = p.edit()
        p.all.keys.filter { (it.startsWith("hist_") && it.substring(5) < cutoff) || (it.startsWith("d_") && it.substring(2, 12) < LocalDate.now().minusDays(2).toString()) }
            .forEach { e.remove(it) }
        e.apply()
    }
}

/** Reglas del juego conectadas con Android: XP, retos, logros, rachas y cierre del día. */
object GameEngine {
    private val catCache = ConcurrentHashMap<String, AppCategory>()
    private val main = Handler(Looper.getMainLooper())

    fun categoryOf(ctx: Context, pkg: String): AppCategory {
        val overrides = GameStore(ctx).overrides
        overrides[pkg]?.let { AppCategory.byId(it)?.let { c -> return c } }
        return catCache.getOrPut(pkg) {
            val androidCat = try {
                val ai = ctx.packageManager.getApplicationInfo(pkg, 0)
                @Suppress("DEPRECATION")
                if (ai.flags and ApplicationInfo.FLAG_IS_GAME != 0) Categorizer.CAT_GAME else ai.category
            } catch (_: Exception) { -1 }
            Categorizer.categorize(pkg, androidCat)
        }
    }

    fun clearCache() = catCache.clear()

    /** Minutos por categoría en un día, a partir del tiempo que mide el escudo. */
    fun minutesByCategory(ctx: Context, date: String = LocalDate.now().toString()): Map<AppCategory, Int> {
        val totals = HashMap<AppCategory, Long>()
        Prefs(ctx).usageForDay(date).forEach { (pkg, ms) ->
            val c = categoryOf(ctx, pkg)
            totals[c] = (totals[c] ?: 0) + ms
        }
        return totals.mapValues { (it.value / 60_000).toInt() }
    }

    fun minutesOfPackages(ctx: Context, c: AppCategory): Int = minutesByCategory(ctx)[c] ?: 0

    fun todayStats(ctx: Context): Game.DayStats {
        val s = GameStore(ctx)
        return Game.DayStats(
            minutes = minutesByCategory(ctx),
            reelExits = s.day("exits"), pauseResists = s.day("pauses"),
            focusMinutes = s.day("focus"), firstTrapMinute = s.firstTrapMinute(),
        )
    }

    fun todayChallenge(ctx: Context): Game.Challenge {
        val s = GameStore(ctx)
        Game.challengeById(s.challengeId)?.let { return it }
        val active = AppCategory.entries.filter { it.trap && s.budget(it) > 0 }.toSet()
        val c = Game.pickChallenge(LocalDate.now().toString(), active, FeedRules.parse(Prefs(ctx).feedRulesText).isNotEmpty())
        s.challengeId = c.id
        return c
    }

    // ------------------------------------------------------------ eventos

    fun addXp(ctx: Context, amount: Int, reason: String, celebrate: Boolean = true) {
        val s = GameStore(ctx)
        val before = Game.levelFor(s.xp)
        s.xp = s.xp + amount
        s.log(reason, amount)
        val after = Game.levelFor(s.xp)
        val msgs = mutableListOf<String>()
        if (celebrate && amount > 0) msgs += "+$amount XP · $reason"
        if (after.number > before.number) {
            msgs += "🎉 ¡Nivel ${after.number}! Ahora eres ${after.emoji} ${after.title}"
            s.log("Subiste al nivel ${after.number}", 0)
        }
        msgs += checkAchievements(ctx)
        toast(ctx, msgs)
    }

    fun onReelExit(ctx: Context) {
        GameStore(ctx).run { addDay("exits"); addTotal("exits") }
        addXp(ctx, Game.Xp.REEL_EXIT, "Saliste de un feed infinito")
        checkChallengeEarly(ctx)
    }

    fun onBudgetExit(ctx: Context, c: AppCategory) =
        addXp(ctx, Game.Xp.BUDGET_EXIT, "Paraste al agotar ${c.label}")

    fun onPauseResist(ctx: Context) {
        GameStore(ctx).run { addDay("pauses"); addTotal("pauses") }
        addXp(ctx, Game.Xp.PAUSE_RESIST, "Elegiste \"mejor no\"")
        checkChallengeEarly(ctx)
    }

    fun onPlanStep(ctx: Context) = addXp(ctx, Game.Xp.PLAN_STEP, "Paso del plan anti-algoritmo")

    fun onTrapOpened(ctx: Context) {
        val t = LocalTime.now()
        GameStore(ctx).setFirstTrapMinute(t.hour * 60 + t.minute)
    }

    fun startFocus(ctx: Context, minutes: Int) {
        val s = GameStore(ctx)
        s.focusLength = minutes
        s.focusUntil = System.currentTimeMillis() + minutes * 60_000L
    }

    fun giveUpFocus(ctx: Context) {
        GameStore(ctx).focusUntil = 0
        GameStore(ctx).log("Modo foco interrumpido (no pasa nada, otra vez será)", 0)
    }

    /** Llamar periódicamente: si el modo foco terminó, da la recompensa. */
    fun checkFocus(ctx: Context) {
        val s = GameStore(ctx)
        if (s.focusUntil == 0L || System.currentTimeMillis() < s.focusUntil) return
        val len = s.focusLength
        s.focusUntil = 0
        s.addDay("focus", len); s.addTotal("focus", len)
        addXp(ctx, len * Game.Xp.FOCUS_PER_MIN + Game.Xp.FOCUS_BONUS, "Modo foco de $len min completado")
        checkChallengeEarly(ctx)
    }

    fun buyExtraTime(ctx: Context, c: AppCategory): Boolean {
        val s = GameStore(ctx)
        if (s.extraUsedToday(c) || s.xp < Game.Xp.EXTRA_TIME_COST) return false
        s.grantExtra(c)
        addXp(ctx, -Game.Xp.EXTRA_TIME_COST, "Comodín de 5 min en ${c.label}", celebrate = false)
        return true
    }

    private fun checkChallengeEarly(ctx: Context) {
        val s = GameStore(ctx)
        if (s.challengeDone) return
        val c = todayChallenge(ctx)
        if (c.completableEarly && Game.isDone(c, todayStats(ctx), dayClosed = false)) {
            s.challengeDone = true
            s.addTotal("challenges")
            addXp(ctx, c.xp, "Reto completado: ${c.title}")
        }
    }

    fun totals(ctx: Context): Game.Totals {
        val s = GameStore(ctx)
        return Game.Totals(
            reelExits = s.total("exits"), pauseResists = s.total("pauses"), focusMinutes = s.total("focus"),
            bestStreak = s.streak.best, goodDays = s.total("gooddays"), zeroShortDays = s.total("zeroshort"),
            challengesDone = s.total("challenges"), planSteps = Prefs(ctx).planDone.size, level = Game.levelFor(s.xp).number,
        )
    }

    private fun checkAchievements(ctx: Context): List<String> {
        val s = GameStore(ctx)
        val new = Game.newAchievements(totals(ctx), s.unlocked)
        if (new.isEmpty()) return emptyList()
        s.unlocked = s.unlocked + new.map { it.id }
        new.forEach { s.log("Logro: ${it.emoji} ${it.title}", 0) }
        return new.map { "🏅 Logro desbloqueado: ${it.emoji} ${it.title}" }
    }

    // ------------------------------------------------------------ cierre del día

    /**
     * Cierra el día anterior (si cambió la fecha): guarda el historial, da XP por
     * cada presupuesto cumplido, actualiza la racha y evalúa el reto.
     * Debe llamarse ANTES de borrar el uso de días anteriores.
     */
    fun rollover(ctx: Context) {
        val s = GameStore(ctx)
        val today = LocalDate.now().toString()
        val last = s.lastDay
        if (last == today) return
        s.lastDay = today
        if (last == null) return // primer arranque

        val minutes = minutesByCategory(ctx, last)
        s.saveHistory(last, minutes)
        val stats = Game.DayStats(minutes, s.day("exits", last), s.day("pauses", last), s.day("focus", last), s.firstTrapMinute(last))
        val budgets = s.budgets()

        var xp = 0
        val okCats = AppCategory.entries.filter { it.trap && (budgets[it] ?: 0) > 0 && stats.min(it) <= budgets[it]!! }
        xp += okCats.size * Game.Xp.CATEGORY_OK
        val ok = Game.dayOk(stats, budgets)
        if (ok) { xp += Game.Xp.PERFECT_DAY; s.addTotal("gooddays") }
        if (stats.min(AppCategory.SHORT_VIDEO) == 0) s.addTotal("zeroshort")

        val before = s.streak
        val after = Game.updateStreak(before, ok)
        s.streak = after
        when {
            after.usedFreeze -> s.log("❄️ Se usó un comodín y tu racha de ${after.days} días sigue viva", 0)
            !ok && before.days > 0 -> s.log("La racha vuelve a empezar. Lo importante es la tendencia, no la perfección 💪", 0)
            ok -> s.log("🔥 Racha: ${after.days} días", 0)
        }

        val ch = Game.challengeById(s.challengeIdFor(last))
        if (ch != null && !s.challengeDoneOn(last) && Game.isDone(ch, stats, dayClosed = true)) {
            xp += ch.xp; s.addTotal("challenges")
            s.log("Reto de ayer conseguido: ${ch.title}", ch.xp)
        }
        if (xp > 0) addXp(ctx, xp, "Resumen de ayer: ${okCats.size} presupuestos cumplidos", celebrate = false)
        s.cleanup()
    }

    // ------------------------------------------------------------ semana

    data class Week(val thisWeek: Int, val lastWeek: Int) {
        val saved get() = (lastWeek - thisWeek).coerceAtLeast(0)
        val changePct get() = if (lastWeek == 0) 0 else ((thisWeek - lastWeek) * 100 / lastWeek)
    }

    fun week(ctx: Context): Week {
        val s = GameStore(ctx)
        val today = LocalDate.now()
        fun trap(m: Map<AppCategory, Int>) = m.filterKeys { it.trap }.values.sum()
        val thisWeek = trap(minutesByCategory(ctx)) + (1..6).sumOf { trap(s.history(today.minusDays(it.toLong()).toString())) }
        val lastWeek = (7..13).sumOf { trap(s.history(today.minusDays(it.toLong()).toString())) }
        return Week(thisWeek, lastWeek)
    }

    private fun toast(ctx: Context, msgs: List<String>) {
        if (msgs.isEmpty()) return
        main.post { Toast.makeText(ctx.applicationContext, msgs.joinToString("\n"), Toast.LENGTH_LONG).show() }
    }
}
