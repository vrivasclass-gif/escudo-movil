package app.escudo.ui

import android.os.Build
import android.text.format.DateUtils
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.escudo.audit.PrivacyAuditor
import app.escudo.core.DomainInfo
import app.escudo.data.Blocklists
import app.escudo.data.Prefs
import app.escudo.data.Upstreams
import app.escudo.vpn.BlocklistRepository
import app.escudo.vpn.EscudoVpnService
import app.escudo.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var upstream by remember { mutableStateOf(prefs.upstreamDns) }
    var forceDns by remember { mutableStateOf(prefs.forceDns) }
    var lists by remember { mutableStateOf(prefs.enabledLists) }
    var wifiOnly by remember { mutableStateOf(prefs.updateOnlyOnWifi) }
    var updating by remember { mutableStateOf(false) }
    var updatedAt by remember { mutableStateOf(prefs.listsUpdatedAt) }
    var cut by remember { mutableStateOf(prefs.cutApps) }
    var showPicker by remember { mutableStateOf(false) }
    var onlyBlocked by remember { mutableStateOf(true) }
    val tick = rememberTicker(1500)
    val running by VpnState.running.collectAsState()
    val entries = remember(tick, onlyBlocked) { VpnState.recent(onlyBlocked).take(150) }

    fun reload() = EscudoVpnService.reload(ctx)

    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Section(
                "Listas de bloqueo", "Se guardan en el móvil y se actualizan solas cada 3 días",
                explain = "Son listas públicas de dominios de rastreo, publicidad y malware que mantienen voluntarios y " +
                    "revisan miles de personas. Escudo las descarga directamente de su fuente (GitHub) y las guarda en tu " +
                    "móvil: no pasan por ningún servidor de Escudo. HaGeZi Pro es la recomendada porque bloquea mucho y " +
                    "casi nunca rompe nada. Si algo deja de funcionar, mira el registro en vivo y permite ese dominio.",
            ) {
                Blocklists.ALL.forEach { bl ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            lists = if (bl.id in lists) lists - bl.id else lists + bl.id
                            prefs.enabledLists = lists
                        }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = bl.id in lists, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(bl.label, style = MaterialTheme.typography.bodyLarge)
                            Text(bl.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    if (updatedAt == 0L) "Aún no se han descargado. Pulsa Actualizar (solo la lista base está activa)."
                    else "Última actualización: " + DateUtils.getRelativeTimeSpanString(updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                )
                SwitchRow("Actualizar solo con Wi-Fi", null, wifiOnly) {
                    wifiOnly = it; prefs.updateOnlyOnWifi = it
                    app.escudo.vpn.ListUpdateWorker.schedule(ctx)
                }
                TextButton(enabled = !updating, onClick = {
                    updating = true
                    scope.launch {
                        val msg = withContext(Dispatchers.IO) { BlocklistRepository(ctx).updateAll() }
                        updatedAt = prefs.listsUpdatedAt
                        updating = false
                        reload()
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                }) { Text(if (updating) "Descargando…" else "Actualizar ahora") }
            }
        }
        item {
            Section(
                "Servidor DNS", "Adónde se envían las consultas que no son rastreadores",
                explain = "Las preguntas DNS permitidas tienen que resolverse en algún sitio. Por defecto las resolvería tu " +
                    "operador, que puede registrar todas las webs que visitas. Quad9 es una fundación suiza sin ánimo de " +
                    "lucro que no guarda tu IP y además bloquea webs de malware.",
            ) {
                Upstreams.ALL.forEach { u ->
                    Row(
                        Modifier.fillMaxWidth().clickable { upstream = u.id; prefs.upstreamDns = u.id; reload() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = upstream == u.id, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(u.label, style = MaterialTheme.typography.bodyLarge)
                            Text(u.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                SwitchRow(
                    "Forzar el DNS de Escudo",
                    "Captura también las apps que intentan usar su propio DNS (8.8.8.8, 1.1.1.1…) para saltarse el filtro.",
                    forceDns,
                ) { forceDns = it; prefs.forceDns = it; Toast.makeText(ctx, "Se aplicará al reiniciar el cortafuegos", Toast.LENGTH_SHORT).show() }
            }
        }
        item {
            Section(
                explain = "Muchas apps no necesitan internet para hacer su trabajo (linternas, calculadoras, juegos sin " +
                    "conexión, teclados) y aun así envían datos. Escudo deja sin respuesta todas sus consultas DNS, así que " +
                    "no pueden conectarse a nada. Necesita Android 10 o superior para saber qué app hace cada consulta.",
                title = "Apps sin internet",
                subtitle =
                if (Build.VERSION.SDK_INT >= 29) "Estas apps no podrán conectarse a nada (ideal para juegos, linternas, teclados…)"
                else "Requiere Android 10 o superior",
            ) {
                if (cut.isEmpty()) Text("Ninguna", style = MaterialTheme.typography.bodySmall)
                cut.sorted().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                TextButton(enabled = Build.VERSION.SDK_INT >= 29, onClick = { showPicker = true }) { Text("Elegir apps") }
            }
        }
        item { ManualRules(prefs) { reload() } }
        item {
            Section(
                "Registro en vivo", "Toca un dominio para saber qué es y bloquearlo o permitirlo",
                explain = "Aquí ves en tiempo real qué app intenta conectarse a qué servidor. Es la forma más directa de " +
                    "entender cuánto te rastrean: muchas apps contactan con decenas de empresas nada más abrirlas.",
            ) {
                Row {
                    FilterChip(selected = onlyBlocked, onClick = { onlyBlocked = true }, label = { Text("Bloqueados") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !onlyBlocked, onClick = { onlyBlocked = false }, label = { Text("Todo") })
                }
            }
        }
        if (!running) {
            item { Text("El cortafuegos está apagado.", Modifier.padding(20.dp)) }
        }
        items(entries) { e ->
            LogRow(e, prefs) { reload() }
        }
    }

    if (showPicker) {
        AppPicker(
            title = "Cortar internet a…",
            initial = cut,
            onDismiss = { showPicker = false },
            onConfirm = { sel -> cut = sel; prefs.cutApps = sel; showPicker = false; reload() },
        )
    }
}

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun LogRow(e: VpnState.Entry, prefs: Prefs, onChange: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { menu = true }.padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (e.blocked) "⛔" else "✓", Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(e.domain, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${timeFmt.format(Date(e.time))} · ${e.app}" + if (e.blocked) " · ${e.reason}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text(e.domain) },
            text = {
                val info = DomainInfo.explain(e.domain)
                Column {
                    Text("Lo pidió: ${e.app}", style = MaterialTheme.typography.bodyMedium)
                    info.owner?.let { Text("Empresa: $it", style = MaterialTheme.typography.bodyMedium) }
                    Text(info.purpose, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        if (e.blocked) "Escudo lo ha bloqueado (${e.reason}): la app no ha podido enviarle datos. Si algo de la app dejó de funcionar, puedes permitirlo."
                        else "Se ha permitido. Si no te fías de este servidor, puedes bloquearlo para todas las apps.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (e.blocked) prefs.userAllowed = (prefs.userAllowed + "\n" + e.domain).trim()
                    else prefs.userBlocked = (prefs.userBlocked + "\n" + e.domain).trim()
                    menu = false; onChange()
                    Toast.makeText(ctx, if (e.blocked) "Permitido" else "Bloqueado", Toast.LENGTH_SHORT).show()
                }) { Text(if (e.blocked) "Permitir siempre" else "Bloquear siempre") }
            },
            dismissButton = { TextButton(onClick = { menu = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ManualRules(prefs: Prefs, onSave: () -> Unit) {
    val ctx = LocalContext.current
    var blocked by remember { mutableStateOf(prefs.userBlocked) }
    var allowed by remember { mutableStateOf(prefs.userAllowed) }
    var custom by remember { mutableStateOf(prefs.customListUrls) }
    Section("Reglas propias", "Un dominio o URL por línea") {
        OutlinedTextField(blocked, { blocked = it }, Modifier.fillMaxWidth().heightIn(min = 80.dp),
            label = { Text("Bloquear siempre") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        OutlinedTextField(allowed, { allowed = it }, Modifier.fillMaxWidth().heightIn(min = 80.dp),
            label = { Text("Permitir siempre (si algo deja de funcionar)") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth().heightIn(min = 80.dp),
            label = { Text("Listas extra (URLs https://)") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        TextButton(onClick = {
            prefs.userBlocked = blocked; prefs.userAllowed = allowed; prefs.customListUrls = custom
            onSave()
            Toast.makeText(ctx, "Guardado", Toast.LENGTH_SHORT).show()
        }) { Text("Guardar reglas") }
    }
}

/** Selector de apps con búsqueda y selección múltiple. */
@Composable
fun AppPicker(title: String, initial: Set<String>, onDismiss: () -> Unit, onConfirm: (Set<String>) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var selected by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { PrivacyAuditor.launchableApps(ctx) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Buscar") }, singleLine = true)
                val list = apps
                if (list == null) Text("Cargando apps…", Modifier.padding(16.dp))
                else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list.filter { query.isBlank() || it.second.contains(query, true) || it.first.contains(query, true) }, key = { it.first }) { (pkg, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = if (pkg in selected) selected - pkg else selected + pkg },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = pkg in selected, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
