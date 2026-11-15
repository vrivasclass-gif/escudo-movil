package app.escudo.ui

/**
 * Plan anti-algoritmo: no se trata de prohibirte una app, sino de que sepa
 * menos de ti y tenga menos herramientas para engancharte. Cada paso explica
 * qué hace y por qué funciona.
 */
enum class PlanAction { HARDEN, BLOCK_TRACKING, NOTIFICATIONS, OPEN_APP, AD_ID, FRICTION, NONE }

data class PlanStep(
    val id: String,
    val title: String,
    val why: String,
    val how: String? = null,
    val action: PlanAction = PlanAction.NONE,
    val actionLabel: String? = null,
)

data class PlanTarget(val label: String, val packages: List<String>, val steps: List<PlanStep>) {
    val pkg get() = packages.first()
}

object AlgoPlan {
    private fun dataStep(app: String) = PlanStep(
        "data", "Quítale datos de tu móvil",
        "El algoritmo no solo aprende de lo que ves en $app: también usa tu ubicación, tu agenda, las redes Wi-Fi y " +
            "Bluetooth de tu alrededor y lo que copias. Con todo eso deduce dónde vives, con quién estás y qué te preocupa. " +
            "Sin esos datos su perfil de ti es más pobre y sus recomendaciones aciertan menos.\n\n" +
            "Además, sin permiso para funcionar en segundo plano, solo trabaja mientras la miras: no precarga el feed " +
            "ni envía datos cuando no la usas.",
        action = PlanAction.HARDEN, actionLabel = "Elegir qué quitarle (Shizuku)",
    )

    private val notifications = PlanStep(
        "notif", "Apaga sus notificaciones",
        "Las notificaciones no son neutras: el algoritmo decide cuál enviarte y en qué momento para que vuelvas justo " +
            "cuando es más probable que te quedes. Apagarlas significa que entras tú cuando quieres, no cuando ella decide.",
        how = "Deja como mucho los mensajes directos si los necesitas.",
        action = PlanAction.NOTIFICATIONS, actionLabel = "Abrir sus notificaciones",
    )

    private fun trackingStep(meta: Boolean) = PlanStep(
        "tracking", "Corta su rastreo fuera de la app",
        if (meta) "Lo que más valor tiene para Meta no es lo que haces en Instagram, sino lo que haces en el resto de apps " +
            "y webs: compras, búsquedas, apps de salud o citas. Miles de apps llevan dentro el SDK de Facebook y se lo " +
            "cuentan. Escudo puede cortar ese canal en todo el móvil.\n\nLímite honesto: lo que haces DENTRO de " +
            "Instagram (qué miras y cuánto tiempo) viaja por los mismos servidores que las fotos, así que no se puede " +
            "bloquear sin romper la app. Para eso están los pasos siguientes."
        else "Esta empresa también recoge datos desde otras apps y del propio sistema. Escudo bloquea esa telemetría " +
            "con sus listas sin afectar al funcionamiento normal de la app.",
        action = PlanAction.BLOCK_TRACKING, actionLabel = "Activar bloqueo",
    )

    private fun noAlgoFeed(how: String) = PlanStep(
        "feed", "Usa el feed sin algoritmo",
        "El feed recomendado está optimizado para el tiempo que pasas, no para lo que te interesa. Los feeds " +
            "cronológicos o de \"Siguiendo\" solo muestran lo que tú elegiste seguir y tienen final: cuando lo has visto, " +
            "se acaba.",
        how = how, action = PlanAction.OPEN_APP, actionLabel = "Abrir la app",
    )

    private fun resetStep(how: String) = PlanStep(
        "reset", "Borra lo que ya sabe de ti",
        "El algoritmo acumula años de señales. Reiniciarlo y cortar los datos que recibe de otras empresas hace que " +
            "vuelva a empezar sin saber qué te engancha.",
        how = how, action = PlanAction.OPEN_APP, actionLabel = "Abrir la app",
    )

    private val teachLess = PlanStep(
        "teach", "Enséñale menos con tu forma de usarla",
        "La señal más fuerte no es el \"me gusta\": es el tiempo que te quedas mirando. Cada segundo de más en un vídeo le " +
            "dice que eso te atrapa.\n\n• Pasa rápido lo que no te aporta, sin detenerte.\n• Usa \"No me interesa\" en lo que " +
            "te hace sentir mal o te atrapa sin querer.\n• Busca activamente lo que sí quieres ver en lugar de esperar a que " +
            "te lo sirva.\n• Escudo te recordará dónde estás cuando entres en los vídeos cortos (sin cerrarte la app).",
    )

    private val adId = PlanStep(
        "adid", "Borra tu ID de publicidad",
        "Es un número único de tu móvil que todas las apps pueden leer para unir tus datos entre ellas. Al borrarlo, " +
            "las apps reciben ceros y no pueden cruzar tu perfil.",
        action = PlanAction.AD_ID, actionLabel = "Abrir ajuste",
    )

    private val friction = PlanStep(
        "friction", "Añade un poco de fricción",
        "Abrir una red social suele ser un gesto automático, no una decisión. Una pausa de 10 segundos antes de entrar " +
            "basta para que tu parte racional decida si de verdad quieres. No te prohíbe nada.",
        action = PlanAction.FRICTION, actionLabel = "Añadir pausa consciente",
    )

    val TARGETS = listOf(
        PlanTarget(
            "Instagram", listOf("com.instagram.android"),
            listOf(
                dataStep("Instagram"), notifications, trackingStep(meta = true),
                noAlgoFeed("Toca el logo de Instagram (arriba a la izquierda) y elige \"Siguiendo\". Verás solo las cuentas que sigues, en orden cronológico. Repítelo cada vez que abras la app."),
                resetStep("1) Tu perfil → ☰ → Contenido sugerido → Restablecer contenido sugerido.\n2) ☰ → Centro de cuentas → Tu información y permisos → Tu actividad fuera de las tecnologías de Meta → Administrar actividad futura → Desconectar.\n3) Centro de cuentas → Preferencias de anuncios → Información de anuncios → Actividad de socios de anuncios → desactívala."),
                teachLess, adId, friction,
            ),
        ),
        PlanTarget(
            "TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            listOf(
                dataStep("TikTok"), notifications, trackingStep(meta = false),
                noAlgoFeed("Usa la pestaña \"Siguiendo\" en lugar de \"Para ti\"."),
                resetStep("Perfil → ☰ → Ajustes y privacidad → Preferencias de contenido → Actualizar tu feed \"Para ti\". En Privacidad → Anuncios, desactiva los anuncios personalizados y los datos de terceros."),
                teachLess, adId, friction,
            ),
        ),
        PlanTarget(
            "YouTube", listOf("com.google.android.youtube"),
            listOf(
                dataStep("YouTube"), notifications, trackingStep(meta = false),
                noAlgoFeed("Truco muy eficaz: Tu perfil → Ajustes → Gestionar todo el historial → desactiva el historial de YouTube. Sin historial, YouTube deja la página de inicio vacía y no muestra recomendaciones: solo encuentras lo que buscas y tus suscripciones."),
                resetStep("Tu perfil → Ajustes → Gestionar todo el historial → Eliminar todo. En myadcenter.google.com desactiva los anuncios personalizados."),
                teachLess, adId, friction,
            ),
        ),
        PlanTarget(
            "Facebook", listOf("com.facebook.katana"),
            listOf(
                dataStep("Facebook"), notifications, trackingStep(meta = true),
                noAlgoFeed("Menú ☰ → Feeds → \"Amigos\" o \"Favoritos\": solo contenido de quien tú elijas, en orden."),
                resetStep("Menú → Configuración → Centro de cuentas → Tu información y permisos → Tu actividad fuera de las tecnologías de Meta → Desconectar actividad futura."),
                teachLess, adId, friction,
            ),
        ),
        PlanTarget(
            "X / Twitter", listOf("com.twitter.android"),
            listOf(
                dataStep("X"), notifications, trackingStep(meta = false),
                noAlgoFeed("Usa la pestaña \"Siguiendo\" en lugar de \"Para ti\"."),
                resetStep("Configuración → Privacidad y seguridad → Compartir datos y personalización: desactiva todo. En Anuncios, desactiva los anuncios personalizados."),
                teachLess, adId, friction,
            ),
        ),
    )
}
