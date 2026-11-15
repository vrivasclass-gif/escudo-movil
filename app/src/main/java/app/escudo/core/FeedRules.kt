package app.escudo.core

/**
 * Reglas del detector de feeds, en texto plano editable desde la app:
 *
 *   paquete | fragmento del id de vista (o *) | accion | nombre
 *
 * accion:
 *   aviso  → solo te recuerda dónde estás (no cierra nada). Es el modo por defecto.
 *   atras  → pulsa Atrás para sacarte de esa pantalla.
 *   inicio → vuelve al escritorio.
 * Con "*" la regla aplica a toda la app.
 * Si una red social cambia su diseño interno, se arregla editando el texto.
 */
enum class FeedAction { NOTICE, BACK, HOME }

data class FeedRule(val pkg: String, val viewId: String, val action: FeedAction, val label: String) {
    val wholeApp: Boolean get() = viewId == "*"
}

object FeedRules {
    val DEFAULT_TEXT = """
        # paquete | id de vista (o *) | accion (aviso/atras/inicio) | nombre
        # "aviso" no cierra nada: solo te recuerda que estás en un feed diseñado para engancharte.
        com.instagram.android | clips_viewer | aviso | Reels de Instagram
        com.google.android.youtube | reel_player | aviso | Shorts de YouTube
        com.google.android.youtube | reel_recycler | aviso | Shorts de YouTube
        com.facebook.katana | reels_viewer | aviso | Reels de Facebook
        com.snapchat.android | spotlight | aviso | Spotlight de Snapchat
        # TikTok es un feed infinito de principio a fin. Borra la '#' para activarlo.
        # com.zhiliaoapp.musically | * | aviso | TikTok
        # com.ss.android.ugc.trill | * | aviso | TikTok
    """.trimIndent()

    fun parse(text: String): List<FeedRule> = text.lineSequence().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
        val parts = line.split('|').map { it.trim() }
        if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return@mapNotNull null
        val action = when (parts.getOrNull(2)?.lowercase()) {
            "atras", "atrás", "back" -> FeedAction.BACK
            "inicio", "home" -> FeedAction.HOME
            else -> FeedAction.NOTICE
        }
        FeedRule(
            pkg = parts[0],
            viewId = parts[1],
            action = action,
            label = parts.getOrNull(3)?.takeIf { it.isNotEmpty() } ?: parts[0],
        )
    }.toList()

}
