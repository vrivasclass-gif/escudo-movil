package app.escudo.core

/**
 * Categorías de apps con presupuesto diario. Las "trampa" son las diseñadas
 * para engancharte (feeds infinitos, recompensas variables); son las que cuentan
 * para la racha.
 */
enum class AppCategory(
    val id: String,
    val label: String,
    val emoji: String,
    val trap: Boolean,
    val defaultBudget: Int, // minutos al día; 0 = sin límite
) {
    SHORT_VIDEO("short", "Vídeo corto", "📱", true, 30),
    SOCIAL("social", "Redes sociales", "💬", true, 60),
    GAMES("games", "Juegos", "🎮", true, 60),
    VIDEO("video", "Vídeo y streaming", "📺", true, 90),
    SHOPPING("shopping", "Compras", "🛍️", true, 20),
    MESSAGING("messaging", "Mensajería", "✉️", false, 0),
    OTHER("other", "Otras", "🧩", false, 0);

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** Qué hace Escudo cuando se agota el presupuesto de una categoría. */
enum class BudgetMode(val id: String, val label: String, val desc: String) {
    COUNT("count", "Solo contar", "Lo apunta para tus estadísticas, sin avisos."),
    WARN("warn", "Avisar", "Te avisa al 80 % y al agotarlo, pero tú decides."),
    BLOCK("block", "Bloquear", "Al agotarlo, la app se cierra hasta mañana (con un comodín de 5 min al día).");

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id } ?: WARN
    }
}

object Categorizer {
    // Constantes de ApplicationInfo.category (API 26+), copiadas para que este código sea puro
    const val CAT_GAME = 0
    const val CAT_AUDIO = 1
    const val CAT_VIDEO = 2
    const val CAT_SOCIAL = 4

    private val KNOWN: Map<String, AppCategory> = buildMap {
        listOf(
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme",
            "com.kwai.video", "video.like", "com.triller.android",
        ).forEach { put(it, AppCategory.SHORT_VIDEO) }
        listOf(
            "com.instagram.android", "com.facebook.katana", "com.facebook.lite", "com.snapchat.android",
            "com.twitter.android", "com.instagram.barcelona", "com.pinterest", "com.reddit.frontpage",
            "com.bereal.ft", "com.tumblr", "com.discord",
        ).forEach { put(it, AppCategory.SOCIAL) }
        listOf(
            "com.google.android.youtube", "com.netflix.mediaclient", "tv.twitch.android.app",
            "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient", "com.hbo.hbonow", "com.wbd.stream",
            "com.google.android.apps.youtube.kids", "com.crunchyroll.crunchyroid",
        ).forEach { put(it, AppCategory.VIDEO) }
        listOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.facebook.orca",
            "org.thoughtcrime.securesms", "com.google.android.apps.messaging", "com.samsung.android.messaging",
        ).forEach { put(it, AppCategory.MESSAGING) }
        listOf(
            "com.zzkko", "com.einnovation.temu", "com.alibaba.aliexpresshd", "com.amazon.mShop.android.shopping",
            "com.wallapop", "fr.vinted", "com.shein",
        ).forEach { put(it, AppCategory.SHOPPING) }
    }

    /**
     * Categoría de una app: primero lo que eligió el usuario, luego nuestra lista
     * de apps conocidas y por último la categoría que declara la propia app.
     */
    fun categorize(pkg: String, androidCategory: Int, userOverrides: Map<String, String> = emptyMap()): AppCategory {
        AppCategory.byId(userOverrides[pkg])?.let { return it }
        KNOWN[pkg]?.let { return it }
        return when (androidCategory) {
            CAT_GAME -> AppCategory.GAMES
            CAT_SOCIAL -> AppCategory.SOCIAL
            CAT_VIDEO -> AppCategory.VIDEO
            else -> AppCategory.OTHER
        }
    }
}
