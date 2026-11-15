package app.escudo.core

/**
 * Explica en lenguaje sencillo qué es un dominio bloqueado y para qué sirve,
 * para que entiendas qué estás cortando en cada caso.
 */
data class DomainExplanation(val owner: String?, val purpose: String)

object DomainInfo {
    private val KNOWN: List<Pair<String, DomainExplanation>> = listOf(
        "app-measurement.com" to DomainExplanation("Google (Firebase Analytics)", "Mide qué haces dentro de la app: pantallas, toques, tiempo de uso y compras."),
        "google-analytics.com" to DomainExplanation("Google Analytics", "Estadísticas de comportamiento que se cruzan con tu perfil publicitario de Google."),
        "doubleclick.net" to DomainExplanation("Google (DoubleClick)", "Publicidad personalizada y seguimiento de qué anuncios ves y pulsas."),
        "googlesyndication.com" to DomainExplanation("Google AdSense/AdMob", "Sirve anuncios y registra tus impresiones."),
        "googleadservices.com" to DomainExplanation("Google Ads", "Atribución: sabe si instalaste o compraste algo tras ver un anuncio."),
        "crashlytics.com" to DomainExplanation("Google (Crashlytics)", "Informes de errores; incluyen datos del dispositivo."),
        "graph.facebook.com" to DomainExplanation("Meta (SDK de Facebook)", "Muchas apps que no son de Meta le cuentan aquí lo que haces dentro de ellas. Es una de las principales fuentes de datos de los anuncios de Instagram."),
        "connect.facebook.net" to DomainExplanation("Meta (Píxel)", "Seguimiento de tu actividad en webs y apps para los anuncios de Facebook e Instagram."),
        "an.facebook.com" to DomainExplanation("Meta Audience Network", "Red publicitaria de Meta dentro de otras apps."),
        "pixel.facebook.com" to DomainExplanation("Meta (Píxel)", "Registra visitas y compras para los anuncios de Meta."),
        "appsflyer.com" to DomainExplanation("AppsFlyer", "Atribución publicitaria: une lo que haces en distintas apps con el anuncio que te trajo."),
        "adjust.com" to DomainExplanation("Adjust", "Atribución publicitaria y medición de campañas entre apps."),
        "branch.io" to DomainExplanation("Branch", "Enlaces de seguimiento que identifican tu dispositivo entre apps y webs."),
        "applovin.com" to DomainExplanation("AppLovin", "Red de anuncios móviles; perfila tus intereses para pujar por tu atención."),
        "unity3d.com" to DomainExplanation("Unity Ads", "Anuncios en juegos y medición de comportamiento."),
        "tiktok.com" to DomainExplanation("TikTok / ByteDance", "Publicidad o telemetría de TikTok."),
        "byteoversea.com" to DomainExplanation("ByteDance", "Telemetría de TikTok y sus SDKs."),
        "samsungads.com" to DomainExplanation("Samsung", "Publicidad del sistema de Samsung."),
        "xiaomi.com" to DomainExplanation("Xiaomi", "Telemetría o anuncios del sistema MIUI/HyperOS."),
        "amazon-adsystem.com" to DomainExplanation("Amazon Ads", "Publicidad de Amazon dentro de apps."),
        "mixpanel.com" to DomainExplanation("Mixpanel", "Analítica detallada de cada acción que haces en la app."),
        "amplitude.com" to DomainExplanation("Amplitude", "Analítica de comportamiento del usuario."),
        "sentry.io" to DomainExplanation("Sentry", "Informes de errores de la app."),
        "onesignal.com" to DomainExplanation("OneSignal", "Notificaciones push y medición de cuándo las abres."),
    )

    fun explain(domain: String): DomainExplanation {
        val d = domain.lowercase().trimEnd('.')
        KNOWN.firstOrNull { (k, _) -> d == k || d.endsWith(".$k") }?.let { return it.second }
        val purpose = when {
            Regex("(^|[.-])(ads?|adserver|adservice|adsystem|banner|doubleclick)([.-]|\\d|$)").containsMatchIn(d) ->
                "Publicidad: sirve anuncios y registra cuáles ves."
            Regex("analytic|metric|stats|telemetry|measure|insight|beacon").containsMatchIn(d) ->
                "Analítica o telemetría: envía datos de cómo usas la app o el móvil."
            Regex("track|pixel|collect|tag|click").containsMatchIn(d) ->
                "Rastreo: sigue tu actividad para crear un perfil."
            Regex("crash|bugsnag|error").containsMatchIn(d) ->
                "Informes de errores: suelen incluir datos del dispositivo."
            Regex("(^|\\.)log(s|ging)?[.-]").containsMatchIn(d) ->
                "Registro de actividad que la app envía a sus servidores."
            else -> "Aparece en una lista pública de rastreo, publicidad o malware."
        }
        return DomainExplanation(null, purpose)
    }
}
