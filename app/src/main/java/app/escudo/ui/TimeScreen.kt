package app.escudo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.escudo.audit.PrivacyAuditor
import app.escudo.core.AppCategory
import app.escudo.core.BudgetMode
import app.escudo.data.Prefs
import app.escudo.game.GameEngine
import app.escudo.game.GameStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pestaña "Tiempo": presupuestos por categoría, recordatorios en vídeos cortos,
 * pausa consciente, límites por app y estadísticas de uso. Todo configurable
 * para que cada persona lo adapte a las apps que más le cuestan.
 */
@Composable
fun TimeScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val store = remember { GameStore(ctx) }
    var pauses by remember { mutableStateOf(prefs.pauseApps) }
    var limits by remember { mutableStateOf(prefs.dailyLimits) }
    var picker by remember { mutableStateOf<String?>(null) } // "pause" | "limit"
    var assign by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) } // fuerza a recomponer tras cambiar ajustes
    val tick = rememberTicker(10_000)
    val used = remember(tick, version) { GameEngine.minutesByCategory(ctx) }

    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Section(
                "⏳ Tiempo por categorías",
                "Tu presupuesto diario para cada tipo de app",
                explain = "En vez de pelearte app por app, Escudo agrupa tus apps en categorías (vídeo corto, redes, juegos…) " +
                    "y tú decides cuánto tiempo al día quieres darle a cada una. Cumplir los presupuestos de las apps " +
                    "trampa mantiene tu racha 🔥 y te da XP.\n\nElige qué pasa al agotarlo:\n" +
                    "• Contar: solo lo apunta.\n• Avisar: te avisa al 80 % y al llegar, pero decides tú.\n" +
                    "• Bloquear: la app se cierra hasta mañana, con un comodín de 5 minutos al día que cuesta XP.\n\n" +
                    "Consejo: empieza con presupuestos que puedas cumplir y bájalos poco a poco. Ganar pequeñas " +
                    "batallas construye el hábito; fallar retos imposibles lo destruye.",
            ) {
                AppCategory.entries.forEach { c ->
                    key(c, version) { CategoryRow(store, c, used[c] ?: 0) { version++ } }
                }
                TextButton(onClick = { assign = true }) { Text("Cambiar la categoría de una app") }
            }
        }
        item {
            Section(
                "🔔 Recordatorios en Reels y Shorts",
                if (store.checkInEnabled) "Cada ${store.checkInMinutes} min te pregunta si te está aportando algo" else "Desactivados",
                explain = "Los vídeos cortos borran la noción del tiempo: cada vídeo dura segundos y no hay ningún final que te " +
                    "haga parar. Un recordatorio cada pocos minutos te devuelve el control: te dice cuánto llevas y te " +
                    "pregunta si de verdad te aporta. Si decides salir, ganas ${app.escudo.core.Game.Xp.REEL_EXIT} XP.\n\n" +
                    "No es para regañarte: a veces sí te aporta, y está bien seguir. Lo importante es que sea una decisión.",
            ) {
                key(version) {
                    SwitchRow("Recordarme el tiempo que llevo", null, store.checkInEnabled) { store.checkInEnabled = it; version++ }
                    if (store.checkInEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Cada", Modifier.weight(1f))
                            TextButton(onClick = { store.checkInMinutes = store.checkInMinutes - 1; version++ }) { Text("−") }
                            Text("${store.checkInMinutes} min", fontWeight = FontWeight.Bold)
                            TextButton(onClick = { store.checkInMinutes = store.checkInMinutes + 1; version++ }) { Text("+") }
                        }
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
    }

    if (assign) CategoryAssignDialog(store) { assign = false; GameEngine.clearCache(); version++ }

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
private fun CategoryRow(store: GameStore, c: AppCategory, used: Int, onChange: () -> Unit) {
    val budget = store.budget(c)
    val mode = store.mode(c)
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${c.emoji} ${c.label}", fontWeight = FontWeight.Bold)
                Text(
                    "Hoy: $used min" + if (budget > 0) " de $budget" else " · sin límite",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { store.setBudget(c, (budget - if (budget > 60) 15 else 5).coerceAtLeast(0)); onChange() }) { Text("−") }
            Text(if (budget > 0) "$budget min" else "∞", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { store.setBudget(c, budget + if (budget >= 60) 15 else 5); onChange() }) { Text("+") }
        }
        if (budget > 0) Row(Modifier.horizontalScroll(rememberScrollState())) {
            BudgetMode.entries.forEach { m ->
                FilterChip(selected = m == mode, onClick = { store.setMode(c, m); onChange() }, label = { Text(m.label) })
                Spacer(Modifier.width(6.dp))
            }
        }
        if (budget > 0) Text(mode.desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Lista de apps con su categoría; toca una para moverla. */
@Composable
private fun CategoryAssignDialog(store: GameStore, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var overrides by remember { mutableStateOf(store.overrides) }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { PrivacyAuditor.launchableApps(ctx) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿En qué categoría va cada app?") },
        text = {
            Column {
                Text(
                    "Si una app te cuesta controlarla, muévela a una categoría con presupuesto. Si una app de estudio " +
                        "está mal clasificada, pásala a \"Otras\".",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Buscar") }, singleLine = true)
                val list = apps
                if (list == null) Text("Cargando apps…", Modifier.padding(16.dp))
                else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list.filter { query.isBlank() || it.second.contains(query, true) }, key = { it.first }) { (pkg, label) ->
                        val cat = remember(overrides) { GameEngine.categoryOf(ctx, pkg) }
                        Column(Modifier.fillMaxWidth().clickable { expanded = if (expanded == pkg) null else pkg }.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${cat.emoji} ${cat.label}", style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            if (expanded == pkg) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
                                AppCategory.entries.forEach { c ->
                                    FilterChip(selected = c == cat, onClick = {
                                        overrides = overrides + (pkg to c.id); store.overrides = overrides; expanded = null
                                    }, label = { Text("${c.emoji} ${c.label}") })
                                    Spacer(Modifier.width(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
        dismissButton = {
            TextButton(onClick = { overrides = emptyMap(); store.overrides = overrides }) { Text("Restablecer") }
        },
    )
}
