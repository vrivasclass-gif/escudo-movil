package app.escudo.audit

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import app.escudo.core.Tracker
import app.escudo.core.TrackerSignatures

data class AppReport(
    val pkg: String,
    val label: String,
    val system: Boolean,
    val trackers: List<Tracker>,
    val sensitivePerms: List<String>,
) {
    /** Puntuación de riesgo sencilla para ordenar la lista. */
    val score: Int get() = trackers.size * 2 + sensitivePerms.size
}

data class UsageRow(val pkg: String, val label: String, val minutes: Long)

/** Analiza las apps instaladas sin conexión: rastreadores embebidos y permisos sensibles concedidos. */
object PrivacyAuditor {
    private val SENSITIVE = linkedMapOf(
        "android.permission.ACCESS_FINE_LOCATION" to "Ubicación precisa",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "Ubicación en segundo plano",
        "android.permission.CAMERA" to "Cámara",
        "android.permission.RECORD_AUDIO" to "Micrófono",
        "android.permission.READ_CONTACTS" to "Contactos",
        "android.permission.READ_CALL_LOG" to "Registro de llamadas",
        "android.permission.READ_SMS" to "SMS",
        "android.permission.READ_MEDIA_IMAGES" to "Fotos",
        "android.permission.READ_EXTERNAL_STORAGE" to "Almacenamiento",
        "android.permission.BODY_SENSORS" to "Sensores corporales",
        "android.permission.ACTIVITY_RECOGNITION" to "Actividad física",
        "android.permission.NEARBY_WIFI_DEVICES" to "Dispositivos cercanos",
        "android.permission.BLUETOOTH_SCAN" to "Escaneo Bluetooth",
        "android.permission.READ_PHONE_STATE" to "Estado del teléfono",
    )

    fun scan(context: Context, includeSystem: Boolean): List<AppReport> {
        val pm = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS
        val pkgs: List<PackageInfo> = installed(pm, 0)
        return pkgs.mapNotNull { basic ->
            val ai = basic.applicationInfo ?: return@mapNotNull null
            val system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 && ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            if (system && !includeSystem) return@mapNotNull null
            if (basic.packageName == context.packageName) return@mapNotNull null
            // Pedimos los detalles app a app: con todas a la vez Android puede superar el límite del binder
            val info = try { packageInfo(pm, basic.packageName, flags) } catch (_: Exception) { basic }
            val components = buildList {
                info.activities?.forEach { add(it.name) }
                info.services?.forEach { add(it.name) }
                info.receivers?.forEach { add(it.name) }
                info.providers?.forEach { add(it.name) }
            }
            val granted = info.requestedPermissions.orEmpty().filterIndexed { i, p ->
                val f = info.requestedPermissionsFlags?.getOrNull(i) ?: 0
                p in SENSITIVE && f and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            }.mapNotNull { SENSITIVE[it] }
            AppReport(
                pkg = basic.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                system = system,
                trackers = TrackerSignatures.detect(components),
                sensitivePerms = granted,
            )
        }.sortedByDescending { it.score }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Minutos de uso por app en los últimos [days] días (requiere acceso de uso). */
    fun usage(context: Context, days: Int): List<UsageRow> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val start = if (days <= 1) {
            java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else end - days * 86_400_000L
        val pm = context.packageManager
        return usm.queryAndAggregateUsageStats(start, end).values
            .filter { it.totalTimeInForeground > 60_000 && it.packageName != context.packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString() } catch (_: Exception) { it.packageName }
                UsageRow(it.packageName, label, it.totalTimeInForeground / 60_000)
            }
            .sortedByDescending { it.minutes }
    }

    /** Apps que el usuario puede abrir (para elegir límites, pausas y cortes de internet). */
    fun launchableApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        return installed(pm, 0).mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            if (p.packageName == context.packageName || pm.getLaunchIntentForPackage(p.packageName) == null) return@mapNotNull null
            p.packageName to pm.getApplicationLabel(ai).toString()
        }.sortedBy { it.second.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun installed(pm: PackageManager, flags: Int): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getInstalledPackages(flags)

    @Suppress("DEPRECATION")
    private fun packageInfo(pm: PackageManager, pkg: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getPackageInfo(pkg, flags)
}
