package app.escudo.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.escudo.R
import app.escudo.data.Prefs
import app.escudo.shield.ShieldState
import app.escudo.vpn.EscudoVpnService
import app.escudo.vpn.ListUpdateWorker
import app.escudo.vpn.VpnState
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ListUpdateWorker.schedule(this)
        app.escudo.vpn.GrayscaleWorker.schedule(this)
        setContent { EscudoTheme { EscudoApp() } }
    }
}

private enum class Tab(val label: String, val icon: Int) {
    HOME("Inicio", R.drawable.ic_shield),
    NET("Red", R.drawable.ic_net),
    SHIELD("Algoritmo", R.drawable.ic_eye),
    OPTIMIZE("Optimizar", R.drawable.ic_tune),
    APPS("Apps", R.drawable.ic_apps),
}

@Composable
private fun EscudoApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (Tab.entries[tab]) {
                Tab.HOME -> HomeScreen()
                Tab.NET -> NetScreen()
                Tab.SHIELD -> ShieldScreen()
                Tab.OPTIMIZE -> OptimizeScreen()
                Tab.APPS -> AuditScreen()
            }
        }
    }
}

/** Reloj que obliga a refrescar cada segundo los datos en vivo. */
@Composable
fun rememberTicker(periodMs: Long = 1000): Long {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(periodMs); t++ } }
    return t
}

@Composable
private fun HomeScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val running by VpnState.running.collectAsState()
    val status by VpnState.status.collectAsState()
    val tick = rememberTicker()

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (VpnService.prepare(ctx) == null) EscudoVpnService.start(ctx)
        else Toast.makeText(ctx, "Sin permiso de VPN no puedo filtrar", Toast.LENGTH_LONG).show()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun startVpn() {
        val prep = VpnService.prepare(ctx)
        if (prep != null) vpnLauncher.launch(prep) else EscudoVpnService.start(ctx)
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Text(
                "Escudo", style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        item {
            Section(
                "Cómo te protege Escudo",
                "Tres capas que trabajan dentro de tu móvil",
            ) {
                Text(
                    "1. Red: corta las conexiones de rastreo y publicidad de todas las apps antes de que salgan del móvil.\n" +
                        "2. Algoritmo: le quita datos a las redes sociales y te ayuda a usarlas cuando tú decides.\n" +
                        "3. Optimizar: desactiva apps de fábrica, frena el segundo plano y acelera las animaciones.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Explain(
                    "Las redes sociales ganan dinero con tu atención. Para captarla necesitan dos cosas: saber mucho de ti " +
                        "(datos) y poder llamarte cuando más les conviene (notificaciones, feeds sin fin, colores llamativos). " +
                        "Escudo trabaja sobre las dos: reduce los datos que salen de tu móvil y añade consciencia y fricción " +
                        "donde las apps están diseñadas para quitártelas.\n\nTodo ocurre en tu teléfono. Escudo no tiene " +
                        "servidores, no tiene cuenta y no envía nada a nadie. Su código es público.",
                    label = "¿Por qué funciona?",
                )
            }
        }
        item {
            Section(
                "Cortafuegos anti-rastreo", status,
                explain = "Cada vez que una app quiere conectarse a un servidor, primero pregunta su dirección (consulta DNS). " +
                    "Escudo responde a esas preguntas dentro del móvil: si el servidor es de rastreo o publicidad, contesta " +
                    "\"no existe\" y la conexión nunca se produce, así que tus datos no salen.\n\nAndroid lo muestra como una " +
                    "\"VPN\" (verás una llave 🔑), pero tu tráfico NO va a ningún servidor externo: solo pasan por Escudo las " +
                    "preguntas DNS; el resto va directo. Por eso no pierdes velocidad, y además ahorras datos y batería.",
            ) {
                SwitchRow(
                    "Bloquear rastreadores y publicidad",
                    "Filtra en el propio móvil. Tu tráfico no pasa por ningún servidor.",
                    running,
                ) { on -> if (on) startVpn() else EscudoVpnService.stop(ctx) }
                key(tick) {
                    Text(
                        "Esta sesión: ${VpnState.sessionBlocked} bloqueados de ${VpnState.sessionQueries} consultas · " +
                            "Total histórico: ${prefs.totalBlocked}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val top = VpnState.topOffenders()
                    if (top.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Apps que más intentan rastrearte:", style = MaterialTheme.typography.labelLarge)
                        top.forEach { (app, n) -> Text("• $app: $n intentos", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        item {
            key(tick / 3) {
                val connected = ShieldState.connected
                Section(
                    "Avisos y pausas (accesibilidad)",
                    if (connected) "Activo · ${prefs.feedsBlockedCount} avisos mostrados" else "Desactivado",
                    explain = "Para avisarte cuando entras en Reels o Shorts, mostrar la pausa consciente y contar tu tiempo, " +
                        "Escudo necesita saber qué pantalla tienes delante. Android solo lo permite mediante el servicio de " +
                        "accesibilidad. Escudo lo usa únicamente para eso: no lee lo que escribes, no hace capturas y no se " +
                        "conecta a internet. El plan completo está en la pestaña Algoritmo.",
                ) {
                    if (!connected) {
                        Text(
                            "Para que pueda sacarte de Reels, Shorts y TikTok, activa \"Escudo\" en Ajustes > Accesibilidad. " +
                                "En Android 13+ si aparece \"Ajuste restringido\": Info de la app > ⋮ > Permitir ajustes restringidos.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = { open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accesibilidad") }
                            TextButton(onClick = { openAppDetails(ctx, ctx.packageName) }) { Text("Info de la app") }
                        }
                    } else {
                        Text("Funcionando. Sigue el plan en la pestaña Algoritmo.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { SystemTips() }
    }
}

/** Ajustes del sistema que una app sin root no puede cambiar sola, pero sí abrirte directamente. */
@Composable
private fun SystemTips() {
    val ctx = LocalContext.current
    Section(
        "Ajustes recomendados del móvil", "Un toque te lleva al sitio exacto",
        explain = "Hay ajustes que ninguna app puede cambiar por ti sin permisos especiales. Escudo te lleva directo a cada " +
            "pantalla y te dice qué tocar. Los que necesitan Shizuku están en la pestaña Optimizar.",
    ) {
        Tip("VPN siempre activa", "Ajustes > VPN > Escudo (⚙) > activa \"VPN siempre activa\". Así nunca se queda sin protección.") {
            open(ctx, Intent(Settings.ACTION_VPN_SETTINGS))
        }
        Tip("Borrar el ID de publicidad", "Elimina el identificador que usan las apps para cruzar tus datos entre sí.") {
            open(ctx, Intent("com.google.android.gms.settings.ADS_PRIVACY"), Intent(Settings.ACTION_PRIVACY_SETTINGS))
        }
        Tip("DNS privado en \"Desactivado\" o \"Automático\"", "Si tienes un proveedor fijo (dns.google…), salta el filtro de Escudo.") {
            open(ctx, Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
        Tip("Animaciones a 0,5x (más fluidez)", "Opciones de desarrollador > Escala de animación de ventana, transición y duración → 0,5x.") {
            open(ctx, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
        Tip("Que Android no cierre Escudo", "Quita la optimización de batería para que el filtro no se detenga.") {
            requestIgnoreBattery(ctx)
        }
        Tip("Permisos de ubicación", "Revisa qué apps acceden a tu ubicación y déjalas en \"Solo mientras se usa\".") {
            open(ctx, Intent("android.intent.action.MANAGE_PERMISSIONS"), Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }
}

@Composable
private fun Tip(title: String, desc: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text("Abrir") }
    }
}

/** Abre el primer Intent que exista en este móvil (cada fabricante cambia los ajustes de sitio). */
fun open(ctx: Context, vararg intents: Intent) {
    for (i in intents) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: ActivityNotFoundException) {} catch (_: SecurityException) {}
    }
    try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

fun openAppDetails(ctx: Context, pkg: String) =
    open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

@SuppressLint("BatteryLife")
private fun requestIgnoreBattery(ctx: Context) {
    val pm = ctx.getSystemService(PowerManager::class.java)
    if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
        Toast.makeText(ctx, "Ya está sin restricciones de batería", Toast.LENGTH_SHORT).show(); return
    }
    open(
        ctx,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )
}
