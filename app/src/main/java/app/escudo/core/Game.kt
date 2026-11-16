package app.escudo.core

import kotlin.math.abs

/**
 * Motor de juego (código puro, testeado).
 *
 * Bases psicológicas del diseño:
 *  - Refuerzo positivo, nunca castigo ni vergüenza: se gana XP por decisiones
 *    conscientes (salir de un feed, aguantar una pausa), no se humilla al fallar.
 *  - Autonomía (teoría de la autodeterminación): el usuario elige sus apps
 *    trampa, sus presupuestos y si Escudo avisa o bloquea.
 *  - Competencia: niveles, retos alcanzables y progreso visible.
 *  - Rachas con comodines: perder un día no borra el esfuerzo (evita el efecto
 *    "ya da igual" que hace recaer).
 *  - Pequeñas victorias diarias en lugar de metas enormes.
 */
object Game {
    // ------------------------------------------------------------ niveles

    data class Level(val number: Int, val title: String, val emoji: String, val xpStart: Int, val xpNext: Int) {
        fun progress(xp: Int): Float = ((xp - xpStart).toFloat() / (xpNext - xpStart)).coerceIn(0f, 1f)
    }

    private val TITLES = listOf(
        "Novato" to "🐣", "Aprendiz del foco" to "🐥", "Rastreador de trampas" to "🦊",
        "Domador de feeds" to "🐺", "Guardián del tiempo" to "🦉", "Ninja anti-scroll" to "🥷",
        "Maestro del enfoque" to "🦅", "Hacker del algoritmo" to "🧠", "Leyenda del control" to "🐉",
        "Inmune al scroll" to "👑",
    )

    /** XP acumulada necesaria para llegar al nivel n (n ≥ 1): 0, 100, 300, 600, 1000… */
    fun xpForLevel(n: Int): Int = 50 * n * (n - 1)

    fun levelFor(xp: Int): Level {
        var n = 1
        while (xp >= xpForLevel(n + 1)) n++
        val (title, emoji) = TITLES[(n - 1).coerceAtMost(TITLES.lastIndex)]
        return Level(n, title, emoji, xpForLevel(n), xpForLevel(n + 1))
    }

    /** Avatares que se desbloquean al subir de nivel. */
    val AVATARS = listOf("🐣" to 1, "😎" to 1, "🐱" to 2, "🦊" to 3, "🐼" to 4, "🐺" to 5, "🥷" to 6, "🦅" to 7, "🤖" to 8, "🐉" to 9, "👑" to 10)

    fun avatarsFor(level: Int) = AVATARS.filter { it.second <= level }.map { it.first }

    // ------------------------------------------------------------ rachas

    data class Streak(val days: Int, val best: Int, val freezes: Int, val usedFreeze: Boolean = false)

    const val MAX_FREEZES = 2

    /**
     * Actualiza la racha al cerrar un día. Si el día no fue bueno pero hay un
     * comodín, se gasta y la racha sigue. Cada 7 días seguidos se gana un comodín.
     */
    fun updateStreak(s: Streak, dayOk: Boolean): Streak {
        if (!dayOk) {
            return if (s.freezes > 0) s.copy(freezes = s.freezes - 1, usedFreeze = true)
            else Streak(0, s.best, s.freezes)
        }
        val days = s.days + 1
        val freezes = if (days % 7 == 0) (s.freezes + 1).coerceAtMost(MAX_FREEZES) else s.freezes
        return Streak(days, maxOf(s.best, days), freezes)
    }

    // ------------------------------------------------------------ estadísticas del día

    data class DayStats(
        val minutes: Map<AppCategory, Int> = emptyMap(),
        val reelExits: Int = 0,
        val pauseResists: Int = 0,
        val focusMinutes: Int = 0,
        val firstTrapMinute: Int = -1, // minuto del día en que se abrió la primera app trampa; -1 = ninguna
    ) {
        fun min(c: AppCategory) = minutes[c] ?: 0
        val trapMinutes get() = minutes.filterKeys { it.trap }.values.sum()
    }

    /** Un día es bueno si ninguna categoría trampa con presupuesto lo ha superado. */
    fun dayOk(stats: DayStats, budgets: Map<AppCategory, Int>): Boolean =
        AppCategory.entries.filter { it.trap }.all { c ->
            val b = budgets[c] ?: 0
            b <= 0 || stats.min(c) <= b
        }

    // ------------------------------------------------------------ retos diarios

    enum class Kind { UNDER, ZERO, REEL_EXITS, FOCUS, PAUSE_RESISTS, LATE_START, TOTAL_UNDER }

    data class Challenge(
        val id: String, val emoji: String, val title: String, val xp: Int,
        val kind: Kind, val cat: AppCategory? = null, val n: Int = 0,
    ) {
        /** Los retos de "hacer algo" se pueden completar durante el día; los de "no pasarse" al cerrarlo. */
        val completableEarly get() = kind == Kind.REEL_EXITS || kind == Kind.FOCUS || kind == Kind.PAUSE_RESISTS
    }

    val CHALLENGES = listOf(
        Challenge("short20", "📱", "Menos de 20 min de vídeo corto hoy", 40, Kind.UNDER, AppCategory.SHORT_VIDEO, 20),
        Challenge("short0", "🧘", "Día sin vídeo corto", 80, Kind.ZERO, AppCategory.SHORT_VIDEO),
        Challenge("social45", "💬", "Redes sociales por debajo de 45 min", 40, Kind.UNDER, AppCategory.SOCIAL, 45),
        Challenge("games45", "🎮", "Juegos por debajo de 45 min", 35, Kind.UNDER, AppCategory.GAMES, 45),
        Challenge("exit3", "🚪", "Sal 3 veces de un feed cuando Escudo te pregunte", 40, Kind.REEL_EXITS, n = 3),
        Challenge("focus25", "🎯", "Completa 25 min de modo foco", 40, Kind.FOCUS, n = 25),
        Challenge("focus50", "🔥", "Completa 50 min de modo foco", 70, Kind.FOCUS, n = 50),
        Challenge("pause2", "✋", "Elige \"mejor no\" en 2 pausas conscientes", 35, Kind.PAUSE_RESISTS, n = 2),
        Challenge("late12", "🌅", "No abras apps trampa antes de las 12:00", 50, Kind.LATE_START, n = 12 * 60),
        Challenge("total90", "⏳", "Menos de 90 min en total en apps trampa", 50, Kind.TOTAL_UNDER, n = 90),
    )

    fun challengeById(id: String?) = CHALLENGES.firstOrNull { it.id == id }

    /** Reto del día: cambia cada día, de forma estable, entre los que encajan con las apps del usuario. */
    fun pickChallenge(dateKey: String, activeCategories: Set<AppCategory>, hasRules: Boolean): Challenge {
        val pool = CHALLENGES.filter { c ->
            when {
                c.cat != null -> c.cat in activeCategories
                c.kind == Kind.REEL_EXITS -> hasRules
                else -> true
            }
        }.ifEmpty { CHALLENGES.filter { it.cat == null } }
        return pool[abs(dateKey.hashCode()) % pool.size]
    }

    /** ¿Está cumplido? [dayClosed] = false durante el día (los de "no pasarse" aún no se dan por buenos). */
    fun isDone(c: Challenge, s: DayStats, dayClosed: Boolean): Boolean = when (c.kind) {
        Kind.REEL_EXITS -> s.reelExits >= c.n
        Kind.FOCUS -> s.focusMinutes >= c.n
        Kind.PAUSE_RESISTS -> s.pauseResists >= c.n
        Kind.UNDER -> dayClosed && s.min(c.cat!!) < c.n
        Kind.ZERO -> dayClosed && s.min(c.cat!!) == 0
        Kind.LATE_START -> dayClosed && (s.firstTrapMinute < 0 || s.firstTrapMinute >= c.n)
        Kind.TOTAL_UNDER -> dayClosed && s.trapMinutes < c.n
    }

    /** Texto de progreso para la tarjeta del reto. */
    fun progressText(c: Challenge, s: DayStats): String = when (c.kind) {
        Kind.REEL_EXITS -> "${s.reelExits.coerceAtMost(c.n)}/${c.n} salidas"
        Kind.FOCUS -> "${s.focusMinutes.coerceAtMost(c.n)}/${c.n} min"
        Kind.PAUSE_RESISTS -> "${s.pauseResists.coerceAtMost(c.n)}/${c.n}"
        Kind.UNDER -> "${s.min(c.cat!!)}/${c.n} min usados"
        Kind.ZERO -> if (s.min(c.cat!!) == 0) "¡Vas perfecto!" else "Hoy ya has usado ${s.min(c.cat)} min"
        Kind.LATE_START -> if (s.firstTrapMinute in 0 until c.n) "Hoy no ha podido ser" else "Aguanta hasta las 12:00"
        Kind.TOTAL_UNDER -> "${s.trapMinutes}/${c.n} min"
    }

    // ------------------------------------------------------------ logros

    data class Totals(
        val reelExits: Int = 0, val pauseResists: Int = 0, val focusMinutes: Int = 0,
        val bestStreak: Int = 0, val goodDays: Int = 0, val zeroShortDays: Int = 0,
        val challengesDone: Int = 0, val planSteps: Int = 0, val level: Int = 1,
    )

    data class Achievement(val id: String, val emoji: String, val title: String, val desc: String, val test: (Totals) -> Boolean)

    val ACHIEVEMENTS = listOf(
        Achievement("first_day", "🌱", "Primer día", "Un día entero dentro de tus presupuestos") { it.goodDays >= 1 },
        Achievement("streak3", "🔥", "En llamas", "Racha de 3 días") { it.bestStreak >= 3 },
        Achievement("streak7", "⚡", "Semana imparable", "Racha de 7 días") { it.bestStreak >= 7 },
        Achievement("streak30", "🏆", "Mes legendario", "Racha de 30 días") { it.bestStreak >= 30 },
        Achievement("exit1", "🚪", "La primera salida", "Saliste de un feed cuando te lo preguntamos") { it.reelExits >= 1 },
        Achievement("exit25", "🛡️", "Escapista", "25 salidas de feeds infinitos") { it.reelExits >= 25 },
        Achievement("pause10", "✋", "Freno de mano", "Elegiste \"mejor no\" 10 veces") { it.pauseResists >= 10 },
        Achievement("focus60", "🎯", "Una hora de foco", "60 min en modo foco") { it.focusMinutes >= 60 },
        Achievement("focus600", "🧠", "Cerebro de acero", "10 h en modo foco") { it.focusMinutes >= 600 },
        Achievement("zero_short", "🧘", "Detox", "Un día entero sin vídeo corto") { it.zeroShortDays >= 1 },
        Achievement("challenges10", "🎲", "Retador", "10 retos diarios completados") { it.challengesDone >= 10 },
        Achievement("plan", "🕵️", "Anti-algoritmo", "Completaste 5 pasos del plan anti-algoritmo") { it.planSteps >= 5 },
        Achievement("level5", "🦉", "Guardián", "Llegaste al nivel 5") { it.level >= 5 },
    )

    fun newAchievements(t: Totals, unlocked: Set<String>): List<Achievement> =
        ACHIEVEMENTS.filter { it.id !in unlocked && it.test(t) }

    // ------------------------------------------------------------ XP

    object Xp {
        const val CATEGORY_OK = 20
        const val PERFECT_DAY = 30
        const val REEL_EXIT = 15
        const val BUDGET_EXIT = 10
        const val PAUSE_RESIST = 10
        const val FOCUS_PER_MIN = 1
        const val FOCUS_BONUS = 15
        const val PLAN_STEP = 25
        const val EXTRA_TIME_COST = 20
    }

    // ------------------------------------------------------------ tiempo recuperado

    /** Traduce minutos a algo tangible para un adolescente. */
    fun equivalent(minutes: Int): String = when {
        minutes >= 600 -> "≈ ${minutes / 120} pelis o un finde entero de planes"
        minutes >= 240 -> "≈ ${minutes / 45} entrenamientos o partidos"
        minutes >= 120 -> "≈ ${minutes / 110} peli${if (minutes >= 220) "s" else ""} con tus amigos"
        minutes >= 45 -> "≈ ${minutes / 15} canciones aprendidas o un buen rato de deporte"
        minutes > 0 -> "≈ $minutes min para lo que de verdad te gusta"
        else -> "—"
    }

    // ------------------------------------------------------------ mensajes de las interrupciones

    /** Recordatorios durante el vídeo corto. {m}=minutos, {app}=app, {goal}=objetivo personal. */
    val CHECK_INS = listOf(
        "⏱️ Llevas {m} min en {app}. ¿Te está aportando algo o solo pasa el tiempo?",
        "🧠 En {m} min habrás visto unos {v} vídeos, cada uno elegido para que no pares. ¿Sigues porque quieres?",
        "🌊 Si notas ganas de seguir, obsérvalas: suben y bajan en 2-3 minutos. ¿Lo pruebas?",
        "🎯 Querías tiempo para {goal}. Llevas {m} min aquí. ¿Qué eliges?",
        "🔋 {m} min de scroll. ¿Cómo te sientes ahora: mejor o peor que cuando entraste?",
    )

    fun checkInText(index: Int, minutes: Int, app: String, goal: String): String =
        CHECK_INS[index % CHECK_INS.size]
            .replace("{m}", minutes.toString())
            .replace("{v}", (minutes * 6).toString())
            .replace("{app}", app)
            .replace("{goal}", goal.ifBlank { "tus cosas" })

    /** Ideas para cuando quiere salir pero no sabe qué hacer: reemplazar el hábito, no solo cortarlo. */
    val ALTERNATIVES = listOf(
        "Pon una canción que te guste y no hagas nada más",
        "Escribe a alguien con quien quieras quedar",
        "10 flexiones o un paseo de 5 minutos",
        "Bebe agua y mira por la ventana 1 minuto",
        "Dibuja, toca un instrumento o escribe algo",
        "Prepara algo para mañana y quítatelo de encima",
    )
}
