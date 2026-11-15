package app.escudo.data

import android.content.Context
import android.content.SharedPreferences
import app.escudo.core.FeedRules
import java.time.LocalDate

/** Ajustes persistentes de la app. Todo se guarda solo en el teléfono. */
class Prefs(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("escudo", Context.MODE_PRIVATE)

    // ---------- Cortafuegos ----------
    var vpnWanted: Boolean
        get() = p.getBoolean("vpn_wanted", false)
        set(v) = p.edit().putBoolean("vpn_wanted", v).apply()

    var upstreamDns: String
        get() = p.getString("upstream", Upstreams.QUAD9.id) ?: Upstreams.QUAD9.id
        set(v) = p.edit().putString("upstream", v).apply()

    /** Captura también el DNS "a pelo" que algunas apps usan (8.8.8.8, 1.1.1.1…). */
    var forceDns: Boolean
        get() = p.getBoolean("force_dns", true)
        set(v) = p.edit().putBoolean("force_dns", v).apply()

    var enabledLists: Set<String>
        get() = p.getStringSet("lists", Blocklists.DEFAULT_ENABLED) ?: Blocklists.DEFAULT_ENABLED
        set(v) = p.edit().putStringSet("lists", v).apply()

    /** URLs de listas extra añadidas por el usuario, una por línea. */
    var customListUrls: String
        get() = p.getString("custom_urls", "") ?: ""
        set(v) = p.edit().putString("custom_urls", v).apply()

    /** Dominios bloqueados a mano, uno por línea. */
    var userBlocked: String
        get() = p.getString("user_blocked", "") ?: ""
        set(v) = p.edit().putString("user_blocked", v).apply()

    /** Dominios que nunca se bloquean, uno por línea. */
    var userAllowed: String
        get() = p.getString("user_allowed", "") ?: ""
        set(v) = p.edit().putString("user_allowed", v).apply()

    /** Apps a las que se corta internet por completo. */
    var cutApps: Set<String>
        get() = p.getStringSet("cut_apps", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("cut_apps", v).apply()

    var listsUpdatedAt: Long
        get() = p.getLong("lists_updated", 0)
        set(v) = p.edit().putLong("lists_updated", v).apply()

    var updateOnlyOnWifi: Boolean
        get() = p.getBoolean("wifi_only", true)
        set(v) = p.edit().putBoolean("wifi_only", v).apply()

    var totalBlocked: Long
        get() = p.getLong("total_blocked", 0)
        set(v) = p.edit().putLong("total_blocked", v).apply()

    // ---------- Escudo anti-algoritmo ----------
    var feedRulesText: String
        get() = p.getString("feed_rules", FeedRules.DEFAULT_TEXT) ?: FeedRules.DEFAULT_TEXT
        set(v) = p.edit().putString("feed_rules", v).apply()

    var shieldEnabled: Boolean
        get() = p.getBoolean("shield_on", true)
        set(v) = p.edit().putBoolean("shield_on", v).apply()

    /** Pausa del escudo hasta esta hora (ms). Para cuando de verdad lo necesitas. */
    var shieldSnoozeUntil: Long
        get() = p.getLong("shield_snooze", 0)
        set(v) = p.edit().putLong("shield_snooze", v).apply()

    /** Límites diarios: "paquete=minutos" separados por línea. */
    var dailyLimits: Map<String, Int>
        get() = parseMap(p.getString("limits", "") ?: "")
        set(v) = p.edit().putString("limits", formatMap(v)).apply()

    /** Pausa consciente antes de abrir: "paquete=segundos". */
    var pauseApps: Map<String, Int>
        get() = parseMap(p.getString("pauses", DEFAULT_PAUSES) ?: DEFAULT_PAUSES)
        set(v) = p.edit().putString("pauses", formatMap(v)).apply()

    /** Contador de feeds bloqueados por el escudo. */
    var feedsBlockedCount: Long
        get() = p.getLong("feeds_blocked", 0)
        set(v) = p.edit().putLong("feeds_blocked", v).apply()

    // ---------- Uso diario (lo escribe el escudo) ----------
    fun usageToday(pkg: String): Long = p.getLong(usageKey(pkg), 0)

    fun addUsageToday(pkg: String, ms: Long) {
        val k = usageKey(pkg)
        p.edit().putLong(k, p.getLong(k, 0) + ms).apply()
    }

    /** Permiso temporal tras pasar la pausa consciente. */
    fun allowUntil(pkg: String): Long = p.getLong("allow_$pkg", 0)
    fun setAllowUntil(pkg: String, t: Long) = p.edit().putLong("allow_$pkg", t).apply()

    fun cleanupOldUsage() {
        val today = LocalDate.now().toString()
        val e = p.edit()
        p.all.keys.filter { it.startsWith("use_") && !it.startsWith("use_$today") }.forEach { e.remove(it) }
        e.apply()
    }

    private fun usageKey(pkg: String) = "use_${LocalDate.now()}_$pkg"

    companion object {
        const val DEFAULT_PAUSES = "com.instagram.android=10\ncom.zhiliaoapp.musically=15\n" +
            "com.ss.android.ugc.trill=15\ncom.facebook.katana=10\ncom.twitter.android=10"

        fun parseMap(s: String): Map<String, Int> = s.lineSequence().mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).trim() to (it.substring(i + 1).trim().toIntOrNull() ?: return@mapNotNull null)
        }.toMap()

        fun formatMap(m: Map<String, Int>) = m.entries.joinToString("\n") { "${it.key}=${it.value}" }
    }
}

data class Upstream(val id: String, val label: String, val desc: String, val servers: List<String>)

object Upstreams {
    val QUAD9 = Upstream("quad9", "Quad9", "Suiza, sin registros, bloquea malware. Recomendado.", listOf("9.9.9.9", "149.112.112.112"))
    val CLOUDFLARE = Upstream("cloudflare", "Cloudflare", "El más rápido en la mayoría de países.", listOf("1.1.1.1", "1.0.0.1"))
    val ADGUARD = Upstream("adguard", "AdGuard DNS", "Bloquea publicidad también en el servidor.", listOf("94.140.14.14", "94.140.15.15"))
    val MULLVAD = Upstream("mullvad", "Mullvad", "Suecia, enfocado en privacidad, bloquea rastreo.", listOf("194.242.2.3", "194.242.2.2"))
    val ALL = listOf(QUAD9, CLOUDFLARE, ADGUARD, MULLVAD)
    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: QUAD9
}

data class Blocklist(val id: String, val label: String, val desc: String, val url: String)

object Blocklists {
    private const val H = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/"
    val ALL = listOf(
        Blocklist("hagezi_pro", "HaGeZi Pro", "Publicidad, rastreo, telemetría, malware. La mejor relación protección/roturas.", H + "pro.txt"),
        Blocklist("hagezi_light", "HaGeZi Light", "Versión ligera: casi nunca rompe nada.", H + "light.txt"),
        Blocklist("hagezi_bypass", "Anti-DoH/VPN/proxy", "Impide que apps y navegadores salten el filtro con DNS cifrado propio.", H + "doh-vpn-proxy-bypass.txt"),
        Blocklist("hagezi_tiktok", "Telemetría de TikTok", "Bloquea el rastreo nativo de TikTok/ByteDance.", H + "native.tiktok.txt"),
        Blocklist("hagezi_samsung", "Telemetría Samsung", "Rastreo del sistema en móviles Samsung.", H + "native.samsung.txt"),
        Blocklist("hagezi_xiaomi", "Telemetría Xiaomi", "Rastreo del sistema en Xiaomi/Redmi/POCO.", H + "native.xiaomi.txt"),
        Blocklist("hagezi_huawei", "Telemetría Huawei", "Rastreo del sistema en Huawei/Honor.", H + "native.huawei.txt"),
        Blocklist("hagezi_oppo", "Telemetría Oppo/Realme/OnePlus", "Rastreo del sistema en Oppo, Realme y OnePlus.", H + "native.oppo-realme.txt"),
        Blocklist("hagezi_vivo", "Telemetría Vivo", "Rastreo del sistema en Vivo/iQOO.", H + "native.vivo.txt"),
        Blocklist("stevenblack", "StevenBlack hosts", "Lista clásica de publicidad y malware.", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
    )
    val DEFAULT_ENABLED = setOf("hagezi_pro", "hagezi_tiktok")
}
