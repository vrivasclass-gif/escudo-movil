package app.escudo.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.escudo.audit.PrivacyAuditor
import app.escudo.audit.UsageRow
import app.escudo.core.FeedRules
import app.escudo.data.Prefs
import app.escudo.optimize.Optimizer
import app.escudo.shield.ShieldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ShieldScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var enabled by remember { mutableStateOf(prefs.shieldEnabled) }
    var snooze by remember { mutableStateOf(prefs.shieldSnoozeUntil) }
    var rules by remember { mutableStateOf(prefs.feedRulesText) }
    var pauses by remember { mutableStateOf(prefs.pauseApps) }
    var limits by remember { mutableStateOf(prefs.dailyLimits) }
    var picker by remember { mutableStateOf<String?>(null) } // "pause" | "limit"
    val tick = rememberTicker(5000)

    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
        item { PlanSection(prefs) }
        item {
            Section(
                "Avisos en vídeos cortos",
                if (ShieldState.connected || tick < 0) "Servicio de accesibilidad activo" else "Servicio desactivado: actívalo en Accesibilidad",
                explain = "Cuando entras en Reels, Shorts o Spotlight, Escudo te muestra un recordatorio breve de dónde estás " +
                    "y por qué ese feed no tiene final. No cierra nada ni te saca de la app: la idea es que seas consciente " +
                    "y decidas tú. Como mucho verás un aviso cada 3 minutos.\n\nPara reconocer la pantalla usa el servicio " +
                    "de accesibilidad de Android: solo mira el nombre interno de las vistas (por ejemplo \"clips_viewer\"), " +
                    "nunca el texto que escribes, y no envía nada a internet. Si prefieres que te saque, cambia \"aviso\" " +
                    "por \"atras\" en las reglas avanzadas.",
            ) {
                SwitchRow("Avisarme en feeds infinitos", null, enabled) {
                    enabled = it; prefs.shieldEnabled = it
                }
                val snoozed = snooze > System.currentTimeMillis()
                Text(
                    if (snoozed) "En pausa hasta las " + java.text.SimpleDateFormat("HH:mm").format(java.util.Date(snooze))
                    else "${prefs.feedsBlockedCount} avisos mostrados hasta ahora",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    TextButton(onClick = {
                        snooze = if (snoozed) 0 else System.currentTimeMillis() + 15 * 60_000
                        prefs.shieldSnoozeUntil = snooze
                    }) { Text(if (snoozed) "Reanudar ya" else "Pausar 15 min") }
                    if (!ShieldState.connected) {
                        TextButton(onClick = { open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accesibilidad") }
                    }
                }
            }
        }
        item {
            Section(
                "Pausa consciente", "Cuenta atrás antes de abrir estas apps",
                explain = "Abrir Instagram suele ser un acto reflejo: desbloqueas y el pulgar va solo. Una pantalla de " +
                    "10 segundos con la pregunta \"¿para qué?\" interrumpe ese reflejo y te da tiempo a decidir. " +
                    "Si decides entrar, tienes 15 minutos sin más interrupciones. No bloquea nada.",
            ) {
                pauses.toSortedMap().forEach { (pkg, secs) ->
                    StepperRow(appLabel(ctx, pkg), "$secs s",
                        onMinus = { pauses = pauses + (pkg to (secs - 5).coerceAtLeast(5)); prefs.pauseApps = pauses },
                        onPlus = { pauses = pauses + (pkg to (secs + 5).coerceAtMost(120)); prefs.pauseApps = pauses },
                        onRemove = { pauses = pauses - pkg; prefs.pauseApps = pauses })
                }
                TextButton(onClick = { picker = "pause" }) { Text("Añadir apps") }
            }
        }
        item {
            Section(
                "Límite diario", "Al llegar al límite la app se cierra hasta mañana",
                explain = "Este sí cierra la app, pero solo cuando llegas al tiempo que TÚ has decidido de antemano, " +
                    "con la cabeza fría. Es opcional: no hay ningún límite puesto por defecto.",
            ) {
                if (limits.isEmpty()) Text("Sin límites", style = MaterialTheme.typography.bodySmall)
                limits.toSortedMap().forEach { (pkg, min) ->
                    val used = prefs.usageToday(pkg) / 60_000
                    StepperRow(appLabel(ctx, pkg), "$used / $min min",
                        onMinus = { limits = limits + (pkg to (min - 15).coerceAtLeast(5)); prefs.dailyLimits = limits },
                        onPlus = { limits = limits + (pkg to (min + 15).coerceAtMost(600)); prefs.dailyLimits = limits },
                        onRemove = { limits = limits - pkg; prefs.dailyLimits = limits })
                }
                TextButton(onClick = { picker = "limit" }) { Text("Añadir apps") }
            }
        }
        item { UsageSection() }
        item {
            Section(
                "Reglas de feeds (avanzado)", "Qué pantallas se detectan y qué se hace en cada una",
                explain = "Cada línea es: app | nombre interno de la pantalla | acción | nombre. Acciones: \"aviso\" (solo " +
                    "recuerda), \"atras\" (te saca de esa pantalla) o \"inicio\" (vuelve al escritorio). Si Instagram " +
                    "cambia su diseño y deja de detectarse, se arregla aquí sin actualizar Escudo.",
            ) {
                OutlinedTextField(
                    rules, { rules = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Row {
                    TextButton(onClick = {
                        prefs.feedRulesText = rules
                        Toast.makeText(ctx, "${FeedRules.parse(rules).size} reglas guardadas", Toast.LENGTH_SHORT).show()
                    }) { Text("Guardar") }
                    TextButton(onClick = { rules = FeedRules.DEFAULT_TEXT; prefs.feedRulesText = rules }) { Text("Restaurar") }
                }
            }
        }
    }

    picker?.let { kind ->
        val current = if (kind == "pause") pauses.keys else limits.keys
        AppPicker(
            title = if (kind == "pause") "Pausa antes de abrir…" else "Limitar tiempo de…",
            initial = current,
            onDismiss = { picker = null },
            onConfirm = { sel ->
                if (kind == "pause") {
                    pauses = sel.associateWith { pauses[it] ?: 10 }; prefs.pauseApps = pauses
                } else {
                    limits = sel.associateWith { limits[it] ?: 30 }; prefs.dailyLimits = limits
                }
                picker = null
            },
        )
    }
}

@Composable
private fun PlanSection(prefs: Prefs) {
    val ctx = LocalContext.current
    val installed = remember { AlgoPlan.TARGETS.filter { t -> t.packages.any { Optimizer.isInstalled(ctx, it) } } }
    val targets = installed.ifEmpty { AlgoPlan.TARGETS.take(1) }
    var sel by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(prefs.planDone) }
    var harden by remember { mutableStateOf(false) }
    val target = targets[sel.coerceIn(0, targets.lastIndex)]
    val pkg = target.packages.firstOrNull { Optimizer.isInstalled(ctx, it) } ?: target.pkg
    val count = target.steps.count { "${target.pkg}:${it.id}" in done }

    Section(
        "Plan anti-algoritmo",
        "${target.label}: $count de ${target.steps.size} pasos hechos",
        explain = "El objetivo no es prohibirte la app, sino que deje de ser tan eficaz controlando tu atención. " +
            "Un algoritmo de recomendación funciona con dos cosas: datos sobre ti y oportunidades para engancharte. " +
            "Cada paso le quita una de las dos. Toca un paso para ver por qué funciona y márcalo cuando lo tengas.",
    ) {
        if (targets.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                targets.forEachIndexed { i, t ->
                    FilterChip(selected = i == sel, onClick = { sel = i }, label = { Text(t.label) })
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
        LinearProgressIndicator(
            progress = { count.toFloat() / target.steps.size },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
        )
        target.steps.forEachIndexed { i, step ->
            val key = "${target.pkg}:${step.id}"
            PlanStepRow(i + 1, step, key in done, onToggle = { on ->
                done = if (on) done + key else done - key; prefs.planDone = done
            }) {
                when (step.action) {
                    PlanAction.HARDEN -> harden = true
                    PlanAction.NOTIFICATIONS -> open(
                        ctx,
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg")),
                    )
                    PlanAction.BLOCK_TRACKING -> {
                        val ids = if (target.label == "Instagram" || target.label == "Facebook") setOf("meta_sdk") else setOf("hagezi_tiktok")
                        prefs.enabledLists = prefs.enabledLists + ids + "hagezi_pro"
                        app.escudo.vpn.EscudoVpnService.reload(ctx)
                        Toast.makeText(
                            ctx,
                            if (app.escudo.vpn.VpnState.running.value) "Bloqueo activado en el cortafuegos"
                            else "Bloqueo preparado: enciende el cortafuegos en Inicio para que funcione",
                            Toast.LENGTH_LONG,
                        ).show()
                        done = done + key; prefs.planDone = done
                    }
                    PlanAction.OPEN_APP -> ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { open(ctx, it) }
                    PlanAction.AD_ID -> open(ctx, Intent("com.google.android.gms.settings.ADS_PRIVACY"), Intent(Settings.ACTION_PRIVACY_SETTINGS))
                    PlanAction.FRICTION -> {
                        prefs.pauseApps = prefs.pauseApps + (pkg to (prefs.pauseApps[pkg] ?: 10))
                        Toast.makeText(ctx, "Pausa de 10 s añadida a ${target.label}", Toast.LENGTH_SHORT).show()
                        done = done + key; prefs.planDone = done
                    }
                    PlanAction.NONE -> {}
                }
            }
        }
    }
    if (harden) HardeningDialog(pkg, target.label) {
        harden = false
        done = done + "${target.pkg}:data"; prefs.planDone = done
    }
}

@Composable
private fun PlanStepRow(n: Int, step: PlanStep, checked: Boolean, onToggle: (Boolean) -> Unit, onAction: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onToggle)
            Text(
                "$n. ${step.title}", Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(if (open) "▾" else "▸", color = MaterialTheme.colorScheme.primary)
        }
        if (open) {
            Column(Modifier.padding(start = 48.dp, end = 4.dp)) {
                Text("Por qué funciona", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(step.why, style = MaterialTheme.typography.bodySmall)
                step.how?.let {
                    Text("Cómo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                step.actionLabel?.let { TextButton(onClick = onAction) { Text(it) } }
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onMinus) { Text("−") }
        Text(value, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onPlus) { Text("+") }
        TextButton(onClick = onRemove) { Text("✕") }
    }
}

@Composable
private fun UsageSection() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(PrivacyAuditor.hasUsageAccess(ctx)) }
    var today by remember { mutableStateOf<List<UsageRow>>(emptyList()) }
    var week by remember { mutableStateOf<List<UsageRow>>(emptyList()) }
    val tick = rememberTicker(4000)
    LaunchedEffect(tick / 15) {
        granted = PrivacyAuditor.hasUsageAccess(ctx)
        if (granted) withContext(Dispatchers.IO) {
            today = PrivacyAuditor.usage(ctx, 1).take(8)
            week = PrivacyAuditor.usage(ctx, 7).take(8)
        }
    }
    Section(
        "Tu uso", "Datos que ya guarda Android; no salen del móvil",
        explain = "Ver el tiempo real que pasas en cada app suele sorprender: solemos calcular la mitad. Es el punto de " +
            "partida para decidir con datos qué cambiar.",
    ) {
        if (!granted) {
            Text("Concede \"Acceso a datos de uso\" a Escudo para ver tu panel semanal.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Conceder acceso") }
        } else {
            Text("Hoy · ${today.sumOf { it.minutes }} min en tus apps más usadas", style = MaterialTheme.typography.labelLarge)
            UsageBars(today)
            Text("Últimos 7 días · media ${week.sumOf { it.minutes } / 7} min/día", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            UsageBars(week)
        }
    }
}

@Composable
private fun UsageBars(rows: List<UsageRow>) {
    val max = (rows.maxOfOrNull { it.minutes } ?: 1L).coerceAtLeast(1L)
    rows.forEach { r ->
        Column(Modifier.padding(vertical = 3.dp)) {
            Row {
                Text(r.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatMinutes(r.minutes), style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { r.minutes.toFloat() / max },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

fun formatMinutes(m: Long) = if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"

fun appLabel(ctx: android.content.Context, pkg: String): String = try {
    ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
} catch (_: Exception) { pkg }
