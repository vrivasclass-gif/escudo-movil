package app.escudo.core

/**
 * Reglas del escudo anti-feeds, en texto plano editable desde la app:
 *
 *   paquete | fragmento del id de vista (o *) | accion | nombre
 *
 * accion: "atras" (pulsa Atrás para salir del feed) o "inicio" (vuelve al escritorio).
 * Con "*" la regla aplica a toda la app.
 * Así, si Instagram o YouTube cambian sus ids internos, se arregla editando
 * el texto, sin necesitar una actualización.
 */
data class FeedRule(val pkg: String, val viewId: String, val goHome: Boolean, val label: String) {
    val wholeApp: Boolean get() = viewId == "*"
}

object FeedRules {
    val DEFAULT_TEXT = """
        # paquete | id de vista (o *) | accion (atras/inicio) | nombre
        com.instagram.android | clips_viewer | atras | Reels de Instagram
        com.instagram.android | clips_tab | atras | Pestaña Reels de Instagram
        com.google.android.youtube | reel_player | atras | Shorts de YouTube
        com.google.android.youtube | reel_recycler | atras | Shorts de YouTube
        com.google.android.youtube | reel_watch | atras | Shorts de YouTube
        com.facebook.katana | reels_viewer | atras | Reels de Facebook
        com.facebook.katana | video_home | atras | Vídeos de Facebook
        com.snapchat.android | spotlight | atras | Spotlight de Snapchat
        # TikTok es un feed infinito de principio a fin: se bloquea entero.
        # Borra la '#' de estas líneas para activarlo.
        # com.zhiliaoapp.musically | * | inicio | TikTok
        # com.ss.android.ugc.trill | * | inicio | TikTok
    """.trimIndent()

    fun parse(text: String): List<FeedRule> = text.lineSequence().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
        val parts = line.split('|').map { it.trim() }
        if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return@mapNotNull null
        val action = parts.getOrNull(2)?.lowercase() ?: "atras"
        FeedRule(
            pkg = parts[0],
            viewId = parts[1],
            goHome = action == "inicio" || action == "home",
            label = parts.getOrNull(3)?.takeIf { it.isNotEmpty() } ?: parts[0],
        )
    }.toList()
}
