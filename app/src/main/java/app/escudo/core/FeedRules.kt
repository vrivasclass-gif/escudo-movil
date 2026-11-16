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
 * Con "*" la regla aplica a toda la app. Se pueden poner varios fragmentos
 * separados por comas (basta con que aparezca uno). "sel:Texto" detecta una
 * pestaña seleccionada con ese nombre (por ejemplo la pestaña Shorts).
 * Si una red social cambia su diseño interno, se arregla editando el texto.
 */
enum class FeedAction { NOTICE, BACK, HOME }

data class FeedRule(val pkg: String, val viewId: String, val action: FeedAction, val label: String) {
    val wholeApp: Boolean get() = viewId == "*"
    /** Fragmentos de id de vista; basta con que uno esté en pantalla. */
    val fragments: List<String> get() = viewId.split(',').map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("sel:") }
    /** Pestañas que, si están seleccionadas, indican que estás en el feed (p. ej. "Shorts"). */
    val selectedTabs: List<String> get() = viewId.split(',').map { it.trim() }.filter { it.startsWith("sel:") }.map { it.removePrefix("sel:").trim() }
}

object FeedRules {
    val DEFAULT_TEXT = """
        # paquete | ids de vista separados por comas (o *) | accion (aviso/atras/inicio) | nombre
        # "aviso" no cierra nada: solo te recuerda que estás en un feed diseñado para engancharte.
        com.instagram.android | clips_viewer,clips_video_container,clips_ufi,clips_media | aviso | Reels de Instagram
        com.google.android.youtube | reel_player,reel_recycler,reel_watch,reel_time_bar,reel_progress,shorts_player,sel:Shorts | aviso | Shorts de YouTube
        com.facebook.katana | reels_viewer,reel_viewer,video_home_reels | aviso | Reels de Facebook
        com.snapchat.android | spotlight | aviso | Spotlight de Snapchat
        # TikTok es un feed infinito de principio a fin
        com.zhiliaoapp.musically | * | aviso | TikTok
        com.ss.android.ugc.trill | * | aviso | TikTok
        com.ss.android.ugc.aweme | * | aviso | TikTok
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
