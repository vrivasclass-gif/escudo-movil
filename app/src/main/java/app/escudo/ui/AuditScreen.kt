package app.escudo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.escudo.audit.AppReport
import app.escudo.audit.PrivacyAuditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuditScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<AppReport>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var includeSystem by remember { mutableStateOf(false) }

    fun scan() {
        loading = true
        scope.launch {
            reports = withContext(Dispatchers.Default) { PrivacyAuditor.scan(ctx, includeSystem) }
            loading = false
        }
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Section(
                "Auditor de privacidad",
                "Busca SDKs de rastreo y publicidad dentro de tus apps y los permisos sensibles que tienen concedidos. Sin internet.",
            ) {
                SwitchRow("Incluir apps del sistema", "Útil para ver la telemetría del fabricante.", includeSystem) { includeSystem = it }
                TextButton(enabled = !loading, onClick = { scan() }) { Text(if (loading) "Analizando…" else "Analizar mis apps") }
                reports?.let { r ->
                    val withTrackers = r.count { it.trackers.isNotEmpty() }
                    Text(
                        "$withTrackers de ${r.size} apps llevan rastreadores · ${r.sumOf { it.trackers.size }} en total",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Qué hacer: quita permisos que no necesiten, corta internet a las que no lo usen (pestaña Red) o desinstálalas.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(reports.orEmpty(), key = { it.pkg }) { ReportCard(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportCard(r: AppReport) {
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { expanded = !expanded }) {
        Column(Modifier.padding(14.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(r.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${r.trackers.size} rastreadores · ${r.sensitivePerms.size} permisos sensibles",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.trackers.size >= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded) {
                if (r.trackers.isNotEmpty()) {
                    Text("Rastreadores:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.trackers.forEach { t -> AssistChip(onClick = {}, label = { Text("${t.name} · ${t.category}") }) }
                    }
                }
                if (r.sensitivePerms.isNotEmpty()) {
                    Text("Permisos concedidos:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Text(r.sensitivePerms.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { openAppDetails(ctx, r.pkg) }) { Text("Abrir ajustes de la app") }
            }
        }
    }
}
