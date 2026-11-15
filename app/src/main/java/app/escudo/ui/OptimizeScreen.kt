package app.escudo.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.escudo.data.Prefs
import app.escudo.optimize.Hardening
import app.escudo.optimize.Optimizer
import app.escudo.optimize.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OptimizeScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val tick = rememberTicker(2000)
    val state = remember(tick) { Shell.state(ctx) }
    val ready = state == Shell.State.READY
    val canWrite = remember(tick) { Optimizer.canWriteSecure(ctx) }
    var refresh by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    fun bg(block: () -> String) = scope.launch {
        val msg = withContext(Dispatchers.IO) { block() }
        refresh++
        toast(msg)
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Section(
                "Optimizar el móvil",
                "Ajustes de sistema que normalmente solo puede tocar un desarrollador",
                explain = "Lo que NO hace Escudo: \"limpiar la RAM\" o \"acelerar\" con un botón. Android gestiona la memoria " +
                    "mejor solo, y las apps que prometen eso suelen empeorar la batería.\n\n" +
                    "Lo que SÍ da fluidez y privacidad de verdad: menos apps de fábrica funcionando sin que las uses, " +
                    "menos apps trabajando en segundo plano, animaciones más cortas y quitar permisos que no se ven en Ajustes.\n\n" +
                    "Para eso Escudo usa Shizuku. Todo lo que hagas aquí queda en el historial de abajo y se puede deshacer.",
            ) {}
        }
        item { ShizukuCard(state) }
        item {
            Section(
                "Permiso permanente para Escudo",
                if (canWrite) "Concedido ✓" else "Pendiente",
                explain = "Con un toque, Shizuku le da a Escudo permiso para cambiar ajustes del sistema (animaciones y escala de grises). " +
                    "Este permiso se mantiene aunque reinicies el móvil, así que el horario de escala de grises funcionará siempre, " +
                    "aunque Shizuku esté apagado. También activa el panel de uso de la pestaña Algoritmo.",
            ) {
                if (!canWrite) {
                    Button(enabled = ready, onClick = {
                        bg { val r = Optimizer.grantSelf(ctx); if (r.ok) "Permiso concedido" else "No se pudo: ${r.out}" }
                    }) { Text("Conceder con Shizuku") }
                } else Text("Escudo ya puede ajustar animaciones y colores por sí mismo.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { AnimationsCard(enabled = canWrite || ready, refreshKey = refresh) { refresh++ } }
        item { GrayscaleCard(prefs, enabled = canWrite || ready, refreshKey = refresh) }
        item {
            val installed = remember(refresh) { Optimizer.BLOAT.filter { Optimizer.isInstalled(ctx, it.pkg) } }
            Section(
                "Apps de fábrica (bloatware)",
                if (installed.isEmpty()) "No he encontrado bloatware conocido en tu móvil" else "${installed.size} encontradas en tu móvil",
                explain = "Son apps que el fabricante o el operador instalan y que no se pueden desinstalar. Muchas recogen datos o " +
                    "muestran anuncios aunque nunca las abras.\n\n\"Desactivar\" no borra nada: la app se congela, deja de " +
                    "funcionar y desaparece del cajón de apps. Puedes reactivarla cuando quieras. Solo aparecen las que sé " +
                    "que se pueden desactivar sin romper el sistema.",
            ) {
                installed.forEach { b ->
                    val enabled = remember(refresh) { Optimizer.isEnabled(ctx, b.pkg) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.bodyLarge)
                            Text(b.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = !enabled, enabled = ready, onCheckedChange = { off ->
                            bg {
                                val r = if (off) Optimizer.disableApp(ctx, b.pkg, b.name) else Optimizer.enableApp(ctx, b.pkg)
                                if (r.ok) (if (off) "${b.name} desactivada" else "${b.name} reactivada") else "Error: ${r.out}"
                            }
                        })
                    }
                }
                TextButton(enabled = ready, onClick = { picker = "disable" }) { Text("Desactivar otra app…") }
            }
        }
        item {
            Section(
                "Quitar permisos ocultos a una app",
                "Ubicación, contactos, portapapeles, segundo plano…",
                explain = "Además de los permisos que ves en Ajustes, Android tiene permisos \"ocultos\" (AppOps) como leer el " +
                    "portapapeles, escanear redes Wi-Fi o funcionar en segundo plano. Aquí puedes quitárselos a cualquier app, " +
                    "con una explicación de para qué los usa y qué dejaría de funcionar.",
            ) {
                TextButton(enabled = ready, onClick = { picker = "harden" }) { Text("Elegir app") }
            }
        }
        item {
            val actions = remember(refresh, tick / 5) { prefs.appliedActions }
            Section(
                "Historial y deshacer",
                if (actions.isEmpty()) "Nada aplicado todavía" else "${actions.size} cambios aplicados",
                explain = "Cada cambio que haces con Shizuku se apunta aquí con su forma de deshacerlo. " +
                    "Si algo deja de funcionar, deshazlo con un toque.",
            ) {
                actions.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.first, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = ready, onClick = {
                            bg { if (Optimizer.undo(ctx, a)) "Deshecho: ${a.first}" else "No se pudo deshacer" }
                        }) { Text("Deshacer") }
                    }
                }
                if (actions.size > 1) {
                    TextButton(enabled = ready, onClick = {
                        bg { val n = actions.count { Optimizer.undo(ctx, it) }; "$n cambios deshechos" }
                    }) { Text("Deshacer todo") }
                }
            }
        }
    }

    when (picker) {
        "disable" -> SingleAppPicker("¿Qué app quieres desactivar?", onDismiss = { picker = null }) { pkg, label ->
            picker = null
            bg { val r = Optimizer.disableApp(ctx, pkg, label); if (r.ok) "$label desactivada" else "Error: ${r.out}" }
        }
        "harden" -> {
            var target by remember { mutableStateOf<Pair<String, String>?>(null) }
            val t = target
            if (t == null) SingleAppPicker("¿A qué app?", onDismiss = { picker = null }) { pkg, label -> target = pkg to label }
            else HardeningDialog(t.first, t.second, onDismiss = { picker = null; refresh++ })
        }
    }
}

@Composable
private fun ShizukuCard(state: Shell.State) {
    val ctx = LocalContext.current
    val (status, help) = when (state) {
        Shell.State.READY -> "Conectado ✓" to null
        Shell.State.NO_PERMISSION -> "Shizuku funciona, falta darle permiso a Escudo" to null
        Shell.State.NOT_RUNNING -> "Shizuku está instalado pero apagado" to
            "Abre Shizuku y pulsa \"Iniciar\" con depuración inalámbrica (Android 11+). La propia app te guía. " +
            "Tras reiniciar el móvil hay que volver a iniciarlo."
        Shell.State.NOT_INSTALLED -> "Shizuku no está instalado" to
            "Instala Shizuku (gratis y de código abierto), ábrelo y sigue sus pasos para iniciarlo con depuración inalámbrica."
    }
    Section(
        "Shizuku", status,
        explain = "Shizuku es una app gratuita y de código abierto que presta a otras apps los permisos de \"depuración\" " +
            "que tendría un ordenador conectado por cable. No es root: no modifica el sistema ni anula la garantía, " +
            "y puedes apagarlo cuando quieras. Escudo solo lo usa cuando pulsas un botón de esta pestaña.",
    ) {
        help?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row {
            when (state) {
                Shell.State.NOT_INSTALLED -> {
                    TextButton(onClick = {
                        open(
                            ctx,
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${Shell.SHIZUKU_PKG}")),
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases")),
                        )
                    }) { Text("Instalar Shizuku") }
                }
                Shell.State.NOT_RUNNING -> TextButton(onClick = {
                    ctx.packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PKG)?.let { open(ctx, it) }
                }) { Text("Abrir Shizuku") }
                Shell.State.NO_PERMISSION -> Button(onClick = { Shell.requestPermission() }) { Text("Dar permiso a Escudo") }
                Shell.State.READY -> {}
            }
        }
    }
}

@Composable
private fun AnimationsCard(enabled: Boolean, refreshKey: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val current = remember(refreshKey) { Optimizer.animationScale(ctx) }
    Section(
        "Fluidez: velocidad de animaciones",
        "Ahora: ${if (current == 0f) "sin animaciones" else "${current}x"}",
        explain = "Cada vez que abres una app o vuelves atrás, Android reproduce una animación. A 0,5x duran la mitad: " +
            "el móvil es igual de potente, pero responde antes y se siente mucho más rápido. Es el ajuste que más se nota " +
            "y no gasta más batería. \"Sin animaciones\" es aún más rápido, pero brusco.",
    ) {
        Row {
            listOf(1f to "Normal", 0.5f to "0,5x (recomendado)", 0f to "Sin").forEach { (v, l) ->
                FilterChip(
                    selected = current == v, enabled = enabled,
                    onClick = {
                        val ok = Optimizer.setAnimationScale(ctx, v)
                        Toast.makeText(ctx, if (ok) "Animaciones: $l" else "No se pudo aplicar", Toast.LENGTH_SHORT).show()
                        onChanged()
                    },
                    label = { Text(l) },
                )
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun GrayscaleCard(prefs: Prefs, enabled: Boolean, refreshKey: Int) {
    val ctx = LocalContext.current
    var now by remember(refreshKey) { mutableStateOf(Optimizer.isGrayscale(ctx)) }
    var sched by remember { mutableStateOf(prefs.grayscaleScheduled) }
    var start by remember { mutableIntStateOf(prefs.grayscaleStart) }
    var end by remember { mutableIntStateOf(prefs.grayscaleEnd) }
    Section(
        "Pantalla en escala de grises",
        if (sched) "Programada de $start:00 a $end:00" else "Sin horario",
        explain = "Los colores vivos (notificaciones rojas, miniaturas saturadas) son señales de recompensa que hacen " +
            "que el cerebro quiera seguir mirando. En blanco y negro, Instagram o TikTok pierden buena parte de su atractivo " +
            "y es más fácil dejar el móvil, sobre todo por la noche. Es una de las medidas con más evidencia para reducir " +
            "el uso compulsivo. Puedes seguir usándolo todo con normalidad.",
    ) {
        SwitchRow("Escala de grises ahora", null, now) {
            if (enabled && Optimizer.setGrayscale(ctx, it)) now = it
            else Toast.makeText(ctx, "Necesita Shizuku o el permiso permanente", Toast.LENGTH_SHORT).show()
        }
        SwitchRow(
            "Activar por horario",
            if (Optimizer.canWriteSecure(ctx)) "Se aplica sola cada día." else "Requiere el permiso permanente de arriba.",
            sched,
        ) { sched = it; prefs.grayscaleScheduled = it; Optimizer.applyGraySchedule(ctx) }
        if (sched) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Desde", Modifier.width(56.dp))
                TextButton(onClick = { start = (start + 23) % 24; prefs.grayscaleStart = start }) { Text("−") }
                Text("$start:00")
                TextButton(onClick = { start = (start + 1) % 24; prefs.grayscaleStart = start }) { Text("+") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hasta", Modifier.width(56.dp))
                TextButton(onClick = { end = (end + 23) % 24; prefs.grayscaleEnd = end }) { Text("−") }
                Text("$end:00")
                TextButton(onClick = { end = (end + 1) % 24; prefs.grayscaleEnd = end }) { Text("+") }
            }
        }
    }
}

/** Diálogo con cada medida de privacidad explicada, para aplicarlas a una app. */
@Composable
fun HardeningDialog(pkg: String, label: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(Optimizer.HARDENING.filter { it.recommended }.map { it.id }.toSet()) }
    var busy by remember { mutableStateOf(false) }
    val ready = Shell.state(ctx) == Shell.State.READY
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quitar datos a $label") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (!ready) Text("Necesitas Shizuku conectado (pestaña Optimizar).", color = MaterialTheme.colorScheme.error)
                Text("Marca lo que quieres quitarle. Lo recomendado ya está marcado.", style = MaterialTheme.typography.bodySmall)
                Optimizer.HARDENING.forEach { h -> HardeningRow(h, h.id in chosen) { on -> chosen = if (on) chosen + h.id else chosen - h.id } }
            }
        },
        confirmButton = {
            TextButton(enabled = ready && !busy && chosen.isNotEmpty(), onClick = {
                busy = true
                scope.launch {
                    val msg = withContext(Dispatchers.IO) {
                        Optimizer.harden(ctx, pkg, label, Optimizer.HARDENING.filter { it.id in chosen })
                    }
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    onDismiss()
                }
            }) { Text(if (busy) "Aplicando…" else "Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun HardeningRow(h: Hardening, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp)) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(h.title, style = MaterialTheme.typography.bodyLarge)
            Text(h.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            h.breaks?.let { Text("Deja de funcionar: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

/** Selector de una sola app. */
@Composable
fun SingleAppPicker(title: String, onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var query by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { app.escudo.audit.PrivacyAuditor.launchableApps(ctx) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Buscar") }, singleLine = true)
                val list = apps
                if (list == null) Text("Cargando apps…", Modifier.padding(16.dp))
                else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    val filtered = list.filter { query.isBlank() || it.second.contains(query, true) || it.first.contains(query, true) }
                    items(filtered.size) { i ->
                        val (pkg, label) = filtered[i]
                        Column(Modifier.fillMaxWidth().clickable { onPick(pkg, label) }.padding(vertical = 8.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
