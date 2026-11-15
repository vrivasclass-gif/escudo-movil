package app.escudo.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Box
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
        item {
            Section(
                "Escudo anti-algoritmo",
                if (ShieldState.connected || tick < 0) "Servicio de accesibilidad activo" else "Servicio desactivado: actívalo en Accesibilidad",
            ) {
                SwitchRow("Bloquear feeds infinitos", "Te saca de Reels, Shorts y similares en cuanto entras.", enabled) {
                    enabled = it; prefs.shieldEnabled = it
                }
                val snoozed = snooze > System.currentTimeMillis()
                Text(
                    if (snoozed) "En pausa hasta las " + java.text.SimpleDateFormat("HH:mm").format(java.util.Date(snooze))
                    else "${prefs.feedsBlockedCount} feeds bloqueados hasta ahora",
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
            Section("Pausa consciente", "Cuenta atrás antes de abrir estas apps. Rompe el gesto automático.") {
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
            Section("Límite diario", "Al llegar al límite la app se cierra hasta mañana.") {
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
            Section("Reglas de feeds (avanzado)", "Si una app cambia su diseño y el escudo deja de detectarla, se corrige aquí sin actualizar la app.") {
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
    Section("Tu uso", "Datos que ya guarda Android; no salen del móvil") {
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
