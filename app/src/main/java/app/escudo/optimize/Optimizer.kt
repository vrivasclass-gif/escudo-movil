package app.escudo.optimize

import android.Manifest
import android.content.Context
import app.escudo.core.ShellErrors
import android.os.Build
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import app.escudo.data.Prefs
import java.time.LocalTime

/** Una app de fábrica que se puede desactivar sin romper el sistema. */
data class Bloat(val pkg: String, val name: String, val why: String)

/** Una medida de privacidad aplicable a una app concreta. */
data class Hardening(
    val id: String,
    val title: String,
    val why: String,
    val permissions: List<String> = emptyList(),   // permisos de Ajustes que se revocan
    val appOps: List<String> = emptyList(),        // permisos "ocultos" (AppOps) que se bloquean
    val breaks: String? = null,                    // qué deja de funcionar, si algo
    val recommended: Boolean = true,
)

object Optimizer {
    // ------------------------------------------------------------------ catálogo

    val BLOAT = listOf(
        Bloat("com.facebook.services", "Servicios de Facebook (preinstalado)", "Viene de fábrica aunque no uses Facebook y se comunica con Meta en segundo plano."),
        Bloat("com.facebook.system", "Facebook App Installer", "Instala y actualiza apps de Meta sin pedirte permiso."),
        Bloat("com.facebook.appmanager", "Facebook App Manager", "Gestiona en segundo plano las apps de Meta preinstaladas."),
        Bloat("com.miui.analytics", "Analytics de Xiaomi", "Envía a Xiaomi estadísticas de uso del teléfono."),
        Bloat("com.miui.msa.global", "MSA (anuncios de Xiaomi)", "El servicio que pone anuncios en las apps del sistema de Xiaomi."),
        Bloat("com.miui.daemon", "MIUI Daemon", "Recoge datos de uso y rendimiento para Xiaomi."),
        Bloat("com.xiaomi.joyose", "Joyose (Xiaomi)", "Telemetría y ajustes remotos de rendimiento."),
        Bloat("com.miui.yellowpage", "Páginas amarillas (Xiaomi)", "Identificación de llamadas con datos enviados a servidores."),
        Bloat("com.xiaomi.mipicks", "GetApps (Xiaomi)", "Tienda alternativa que muestra anuncios y sugerencias."),
        Bloat("com.miui.hybrid", "Quick Apps (Xiaomi)", "Mini-apps que se lanzan solas con contenido promocionado."),
        Bloat("com.samsung.android.app.spage", "Samsung Free", "Panel de noticias y contenido promocionado a la izquierda del escritorio."),
        Bloat("com.samsung.android.rubin.app", "Customization Service (Samsung)", "Analiza tus hábitos para personalizar contenido y anuncios de Samsung."),
        Bloat("com.samsung.android.bixby.agent", "Bixby Voice", "Asistente de Samsung; escucha y envía datos de voz."),
        Bloat("com.samsung.android.visionintelligence", "Bixby Vision", "Análisis de imágenes de la cámara en la nube de Samsung."),
        Bloat("com.samsung.android.app.tips", "Consejos (Samsung)", "Notificaciones promocionales."),
        Bloat("com.sec.android.app.chromecustomizations", "Personalización de Chrome (Samsung)", "Marcadores y ajustes impuestos por el fabricante."),
        Bloat("com.heytap.pictorial", "Revista de bloqueo (Oppo/Realme)", "Fondos de pantalla con anuncios en la pantalla de bloqueo."),
        Bloat("com.aura.oobe.samsung", "Aura / AppCloud", "Instala apps patrocinadas tras las actualizaciones."),
        Bloat("com.ironsource.appcloud.oobe", "AppCloud (ironSource)", "Instala apps patrocinadas tras las actualizaciones."),
        Bloat("com.dti.folderlauncher", "Digital Turbine", "Instalador de apps patrocinadas del operador."),
        Bloat("com.digitalturbine.ignite.cl", "DT Ignite", "Instalador de apps patrocinadas del operador."),
        Bloat("com.netflix.partner.activation", "Netflix Partner Activation", "Avisa a Netflix de la activación del móvil, aunque no tengas Netflix."),
        Bloat("com.amazon.appmanager", "Amazon App Manager", "Gestor de apps de Amazon preinstalado."),
        Bloat("com.google.android.feedback", "Feedback de Google", "Envía informes de fallos de apps a Google."),
    )

    val HARDENING = listOf(
        Hardening(
            "location", "Ubicación",
            "Saber dónde estás, dónde duermes y por dónde te mueves es de lo más valioso para perfilarte y para sugerirte gente y anuncios cercanos.",
            permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, "android.permission.ACCESS_BACKGROUND_LOCATION"),
            appOps = listOf("WIFI_SCAN"),
            breaks = "Etiquetar ubicación en publicaciones.",
        ),
        Hardening(
            "contacts", "Contactos y cuentas",
            "Con tu agenda reconstruyen tu red social real: a quién conoces, para sugerirte cuentas y deducir datos de ti a partir de ellos.",
            permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.GET_ACCOUNTS),
            breaks = "Buscar amigos por la agenda.",
        ),
        Hardening(
            "clipboard", "Portapapeles",
            "Algunas apps leen lo que copias (enlaces, textos, contraseñas) aunque no lo pegues en ellas.",
            appOps = listOf("READ_CLIPBOARD"),
            breaks = "Pegar texto copiado de otra app (tendrás que pegarlo a mano desde el teclado).",
        ),
        Hardening(
            "nearby", "Dispositivos cercanos",
            "Los escaneos Bluetooth y Wi-Fi revelan con quién estás y en qué sitio, incluso sin GPS.",
            permissions = listOf("android.permission.BLUETOOTH_SCAN", "android.permission.NEARBY_WIFI_DEVICES", "android.permission.BLUETOOTH_CONNECT"),
            appOps = listOf("BLUETOOTH_SCAN"),
        ),
        Hardening(
            "phone", "Teléfono e identificadores",
            "Tu número y el estado del teléfono sirven para enlazar tu cuenta con otras bases de datos.",
            permissions = listOf(Manifest.permission.READ_PHONE_STATE, "android.permission.READ_PHONE_NUMBERS", Manifest.permission.READ_CALL_LOG),
        ),
        Hardening(
            "sensors", "Actividad física y sensores",
            "Detectar si caminas, conduces o estás tumbado ayuda a decidir cuándo eres más vulnerable a seguir mirando.",
            permissions = listOf("android.permission.ACTIVITY_RECOGNITION", Manifest.permission.BODY_SENSORS),
        ),
        Hardening(
            "background", "Funcionar en segundo plano",
            "Cuando no la usas, la app precarga el feed, envía telemetría y prepara notificaciones para que vuelvas. Sin segundo plano, solo trabaja mientras la miras.",
            appOps = listOf("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND"),
            breaks = "Las notificaciones pueden llegar con retraso.",
        ),
        Hardening(
            "notifications", "Notificaciones",
            "El algoritmo elige qué notificación enviarte y cuándo, para recuperar tu atención en el momento justo.",
            permissions = listOf("android.permission.POST_NOTIFICATIONS"),
            breaks = "No verás avisos de mensajes directos.",
            recommended = false,
        ),
        Hardening(
            "microphone", "Micrófono",
            "Solo lo necesita mientras grabas. Quitándolo te aseguras de que no escucha nunca; Android te lo volverá a pedir cuando grabes.",
            permissions = listOf(Manifest.permission.RECORD_AUDIO),
            breaks = "Grabar vídeos o notas de voz (lo pedirá de nuevo).",
            recommended = false,
        ),
        Hardening(
            "photos", "Todas tus fotos",
            "Con acceso total puede analizar todo tu carrete: caras, lugares y fechas. Mejor elegir fotos sueltas cuando quieras publicar.",
            permissions = listOf("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", Manifest.permission.READ_EXTERNAL_STORAGE),
            breaks = "Tendrás que elegir las fotos una a una al publicar.",
            recommended = false,
        ),
    )

    // ------------------------------------------------------------------ permisos para Escudo

    fun canWriteSecure(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Una sola vez con Shizuku: después Escudo puede ajustar animaciones y escala de grises por sí mismo. */
    fun grantSelf(ctx: Context): Shell.Result {
        val pkg = ctx.packageName
        val r = Shell.run("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS && appops set $pkg GET_USAGE_STATS allow")
        return r
    }

    // ------------------------------------------------------------------ animaciones

    private val ANIM_KEYS = listOf(
        Settings.Global.WINDOW_ANIMATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.ANIMATOR_DURATION_SCALE,
    )

    fun animationScale(ctx: Context): Float =
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    fun setAnimationScale(ctx: Context, scale: Float): Boolean {
        if (canWriteSecure(ctx)) {
            return try { ANIM_KEYS.forEach { Settings.Global.putFloat(ctx.contentResolver, it, scale) }; true } catch (_: Exception) { false }
        }
        return Shell.run(ANIM_KEYS.joinToString(" && ") { "settings put global $it $scale" }).ok
    }

    // ------------------------------------------------------------------ escala de grises

    private const val DALTONIZER_ON = "accessibility_display_daltonizer_enabled"
    private const val DALTONIZER_MODE = "accessibility_display_daltonizer"

    fun isGrayscale(ctx: Context) = try {
        Settings.Secure.getInt(ctx.contentResolver, DALTONIZER_ON, 0) == 1 &&
            Settings.Secure.getInt(ctx.contentResolver, DALTONIZER_MODE, -1) == 0
    } catch (_: Exception) { false }

    fun setGrayscale(ctx: Context, on: Boolean): Boolean {
        if (canWriteSecure(ctx)) {
            return try {
                Settings.Secure.putInt(ctx.contentResolver, DALTONIZER_MODE, 0)
                Settings.Secure.putInt(ctx.contentResolver, DALTONIZER_ON, if (on) 1 else 0)
                true
            } catch (_: Exception) { false }
        }
        return Shell.run("settings put secure $DALTONIZER_MODE 0 && settings put secure $DALTONIZER_ON ${if (on) 1 else 0}").ok
    }

    fun inGrayWindow(hour: Int, start: Int, end: Int): Boolean =
        if (start <= end) hour in start until end else hour >= start || hour < end

    /**
     * Aplica el horario de escala de grises. Solo actúa en los cambios de franja,
     * así que si tú lo cambias a mano a mitad de la franja, se respeta.
     */
    fun applyGraySchedule(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.grayscaleScheduled || !canWriteSecure(ctx)) return
        val want = inGrayWindow(LocalTime.now().hour, prefs.grayscaleStart, prefs.grayscaleEnd)
        val sp = ctx.getSharedPreferences("escudo", Context.MODE_PRIVATE)
        val last = if (sp.contains("gray_last")) sp.getBoolean("gray_last", false) else null
        if (last != want) {
            setGrayscale(ctx, want)
            sp.edit().putBoolean("gray_last", want).apply()
        }
    }

    // ------------------------------------------------------------------ apps de fábrica

    // MATCH_UNINSTALLED_PACKAGES: también encuentra las apps quitadas solo para este usuario
    @Suppress("DEPRECATION")
    private val ANY_STATE = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES

    fun isInstalled(ctx: Context, pkg: String) = try {
        ctx.packageManager.getApplicationInfo(pkg, ANY_STATE); true
    } catch (_: Exception) { false }

    /** Activa = instalada para este usuario y no desactivada. */
    fun isEnabled(ctx: Context, pkg: String) = try {
        val ai = ctx.packageManager.getApplicationInfo(pkg, ANY_STATE)
        ai.enabled && (ai.flags and ApplicationInfo.FLAG_INSTALLED) != 0
    } catch (_: Exception) { false }

    val isXiaomi get() = Build.MANUFACTURER.equals("Xiaomi", true) || Build.BRAND.lowercase() in setOf("xiaomi", "redmi", "poco")

    /**
     * Desactiva una app de fábrica. Primero la congela (pm disable-user); si el
     * fabricante no lo permite (Xiaomi suele bloquearlo), la quita solo para tu
     * usuario (pm uninstall -k --user 0), que también se puede deshacer porque la
     * app sigue guardada en el sistema. Se comprueba el resultado real, no solo lo
     * que responde el comando.
     */
    fun disableApp(ctx: Context, pkg: String, label: String): Shell.Result {
        val a = Shell.run("pm disable-user --user 0 $pkg")
        if (!isEnabled(ctx, pkg)) {
            record(ctx, "Desactivada: $label", "pm enable --user 0 $pkg")
            return Shell.Result(0, "$label desactivada")
        }
        val b = Shell.run("pm uninstall -k --user 0 $pkg")
        if (!isEnabled(ctx, pkg)) {
            record(ctx, "Quitada para tu usuario: $label", "cmd package install-existing --user 0 $pkg ; pm enable --user 0 $pkg")
            return Shell.Result(0, "$label quitada (se puede recuperar desde el historial)")
        }
        val raw = listOf(a.out, b.out).filter { it.isNotBlank() }.joinToString("\n")
        return Shell.Result(1, ShellErrors.explain(raw, isXiaomi) + "\n\nDetalle técnico:\n" + raw.take(600))
    }

    fun enableApp(ctx: Context, pkg: String): Shell.Result {
        Shell.run("cmd package install-existing --user 0 $pkg")
        val r = Shell.run("pm enable --user 0 $pkg")
        if (isEnabled(ctx, pkg)) {
            val p = Prefs(ctx)
            p.appliedActions = p.appliedActions.filterNot { it.second.contains(" $pkg") && (it.second.startsWith("pm enable") || it.second.startsWith("cmd package install-existing")) }
            return Shell.Result(0, "Reactivada")
        }
        return Shell.Result(1, ShellErrors.explain(r.out, isXiaomi) + "\n\nDetalle técnico:\n" + r.out.take(600))
    }

    /** Diagnóstico para la tarjeta de Shizuku: qué usuario usa y si el móvil bloquea cambios. */
    fun diagnose(ctx: Context): String {
        val sb = StringBuilder()
        val id = Shell.run("id")
        if (!id.ok) return "❌ Shizuku no responde: ${id.out}\n\n" + ShellErrors.explain(id.out, isXiaomi)
        sb.append(if (id.out.contains("uid=2000")) "✅ Shizuku funciona (modo depuración)\n" else if (id.out.contains("uid=0")) "✅ Shizuku funciona (modo root)\n" else "⚠️ Usuario inesperado: ${id.out.take(80)}\n")
        sb.append("📱 ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n")
        if (isXiaomi) {
            val sec = Shell.run("getprop persist.security.adbinput").out.trim()
            sb.append(
                if (sec == "1") "✅ \"Depuración USB (ajustes de seguridad)\" activada\n"
                else "❌ \"Depuración USB (ajustes de seguridad)\" desactivada. Sin ella Xiaomi no deja quitar apps ni permisos. " +
                    "Actívala en Opciones de desarrollador (pide SIM y cuenta Xiaomi) y reinicia Shizuku.\n",
            )
        }
        val found = BLOAT.count { isInstalled(ctx, it.pkg) }
        val on = BLOAT.count { isEnabled(ctx, it.pkg) }
        sb.append("🧹 Bloatware conocido: $found encontradas, ${found - on} ya quitadas")
        return sb.toString()
    }

    // ------------------------------------------------------------------ privacidad por app

    /** Aplica las medidas elegidas. Devuelve un resumen legible de lo que se hizo. */
    fun harden(ctx: Context, pkg: String, label: String, measures: List<Hardening>): String {
        val pm = ctx.packageManager
        val uid = try { pm.getApplicationInfo(pkg, 0).uid } catch (_: Exception) { return "La app no está instalada" }
        val done = mutableListOf<String>()
        for (h in measures) {
            val cmds = mutableListOf<String>()
            val undo = mutableListOf<String>()
            h.permissions.filter { pm.checkPermission(it, pkg) == PackageManager.PERMISSION_GRANTED }.forEach {
                cmds += "pm revoke $pkg $it"; undo += "pm grant $pkg $it"
            }
            h.appOps.forEach { cmds += "appops set $pkg $it ignore"; undo += "appops set $pkg $it default" }
            if (h.id == "background") {
                cmds += "am set-standby-bucket $pkg restricted"; undo += "am set-standby-bucket $pkg active"
                cmds += "cmd netpolicy add restrict-background-blacklist $uid"
                undo += "cmd netpolicy remove restrict-background-blacklist $uid"
            }
            if (cmds.isEmpty()) continue
            // Cada comando por separado: si uno no existe en esta versión de Android, los demás siguen
            val ok = cmds.map { Shell.run(it) }.count { it.ok }
            if (ok > 0) {
                done += h.title
                record(ctx, "$label: ${h.title}", undo.joinToString(" ; "))
            }
        }
        return if (done.isEmpty()) "No había nada que quitar: $label ya no tenía esos permisos."
        else "Aplicado en $label: ${done.joinToString(", ")}"
    }

    // ------------------------------------------------------------------ historial y deshacer

    private fun record(ctx: Context, label: String, undo: String) {
        val p = Prefs(ctx)
        p.appliedActions = p.appliedActions.filterNot { it.first == label } + (label to undo)
    }

    private fun forget(ctx: Context, undo: String) {
        val p = Prefs(ctx)
        p.appliedActions = p.appliedActions.filterNot { it.second == undo }
    }

    fun undo(ctx: Context, action: Pair<String, String>): Boolean {
        val ok = action.second.split(" ; ").map { Shell.run(it) }.any { it.ok }
        if (ok) Prefs(ctx).appliedActions = Prefs(ctx).appliedActions.filterNot { it == action }
        return ok
    }
}
