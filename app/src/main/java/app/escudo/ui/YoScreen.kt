package app.escudo.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.escudo.audit.PrivacyAuditor
import app.escudo.core.AppCategory
import app.escudo.core.Game
import app.escudo.game.GameEngine
import app.escudo.game.GameStore
import app.escudo.shield.ShieldState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Violet = Color(0xFF5B2EFF)
private val Teal2 = Color(0xFF0F8A7E)
private val HeroBrush = Brush.linearGradient(listOf(Violet, Color(0xFF8E2DE2), Teal2))

@Composable
fun YoScreen() {
    val ctx = LocalContext.current
    val store = remember { GameStore(ctx) }
    var onboarded by remember { mutableStateOf(store.onboarded) }
    if (!onboarded) {
        Onboarding(store) { onboarded = true }
        return
    }
    val tick = rememberTicker(3000)
    LaunchedEffect(tick / 10) { GameEngine.rollover(ctx); GameEngine.checkFocus(ctx) }

    // Valores recalculados con el reloj, para que todo esté en vivo
    val xp = remember(tick) { store.xp }
    val level = Game.levelFor(xp)
    val streak = remember(tick) { store.streak }
    val stats = remember(tick / 5) { GameEngine.todayStats(ctx) }
    val challenge = remember(tick / 20) { GameEngine.todayChallenge(ctx) }
    val challengeDone = remember(tick) { store.challengeDone }
    val week = remember(tick / 20) { GameEngine.week(ctx) }
    val unlocked = remember(tick) { store.unlocked }
    val feed = remember(tick) { store.feed().take(6) }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Hero(store, level, xp, streak) }
        if (!ShieldState.connected) item { SetupCard() }
        item {
            GameCard("🎲 Reto de hoy", if (challengeDone) "¡Conseguido! +${challenge.xp} XP" else "+${challenge.xp} XP") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(challenge.emoji, fontSize = 34.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(challenge.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (challengeDone) "✅ Hecho. Mañana, otro." else Game.progressText(challenge, stats),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { TodayCategories(store, stats) }
        item { FocusCard(store, tick) }
        item {
            GameCard("📈 Tu semana", "Apps trampa: ${formatMinutes(week.thisWeek.toLong())}") {
                if (week.lastWeek == 0) {
                    Text("La semana que viene podrás compararte con esta. Aquí no compites con nadie más que contigo.", style = MaterialTheme.typography.bodySmall)
                } else {
                    val better = week.thisWeek <= week.lastWeek
                    Text(
                        if (better) "⬇️ ${-week.changePct}% menos que la semana pasada" else "⬆️ ${week.changePct}% más que la semana pasada",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (better) Color(0xFF1E9E5A) else MaterialTheme.colorScheme.error,
                    )
                    if (week.saved > 0) {
                        Text("Tiempo recuperado: ${formatMinutes(week.saved.toLong())}", style = MaterialTheme.typography.bodyMedium)
                        Text(Game.equivalent(week.saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Una semana peor no borra lo conseguido. Elige un reto pequeño para mañana.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Achievements(unlocked) }
        if (feed.isNotEmpty()) item {
            GameCard("⚡ Actividad", null) {
                val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                feed.forEach { (t, x, text) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(
                            (if (x > 0) "+$x" else if (x < 0) "$x" else "") + "  " + fmt.format(Date(t)),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Explain(
                "Escudo está diseñado con lo que sabemos sobre cómo se forman y se cambian los hábitos:\n\n" +
                    "• Las apps trampa usan recompensas impredecibles (como una tragaperras): nunca sabes si el siguiente vídeo será bueno. " +
                    "Por eso cuesta tanto parar. No es falta de voluntad: están diseñadas así.\n" +
                    "• Aquí ganas puntos por decisiones conscientes (salir, aguantar una pausa, concentrarte), no por sufrir.\n" +
                    "• Tú eliges tus presupuestos y si Escudo avisa o bloquea. Las reglas que pones tú se cumplen mejor.\n" +
                    "• Las rachas tienen comodines ❄️: un mal día no tira a la basura todo lo que llevas.\n" +
                    "• Cuando sientas ganas de volver a abrir algo, obsérvalas sin actuar: suben y bajan en pocos minutos (\"surfear la ola\").\n\n" +
                    "Todo se queda en tu móvil. Nadie más ve tus datos ni tu progreso.",
                label = "¿Por qué funciona esto?",
            )
        }
    }
}

@Composable
private fun Hero(store: GameStore, level: Game.Level, xp: Int, streak: Game.Streak) {
    var pickAvatar by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(28.dp)).background(HeroBrush).padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).clickable { pickAvatar = !pickAvatar },
                    contentAlignment = Alignment.Center,
                ) { Text(store.avatar, fontSize = 40.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (store.name.isBlank()) "¡Hola!" else "¡Hola, ${store.name}!",
                        color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    )
                    Text("Nivel ${level.number} · ${level.emoji} ${level.title}", color = Color.White.copy(alpha = 0.9f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥 ${streak.days}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("racha", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    Text("❄️ ${streak.freezes}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { level.progress(xp) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = Color.White, trackColor = Color.White.copy(alpha = 0.25f),
            )
            Text(
                "$xp / ${level.xpNext} XP para el nivel ${level.number + 1} · récord de racha: ${streak.best} días",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (pickAvatar) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Game.AVATARS.forEach { (a, lvl) ->
                        val ok = level.number >= lvl
                        Box(
                            Modifier.padding(4.dp).size(44.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = if (ok) 0.25f else 0.08f))
                                .clickable(enabled = ok) { store.avatar = a; pickAvatar = false },
                            contentAlignment = Alignment.Center,
                        ) { Text(if (ok) a else "🔒", fontSize = 22.sp) }
                    }
                }
                Text("Sube de nivel para desbloquear más avatares", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun GameCard(title: String, badge: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (badge != null) {
                    Text(
                        badge, style = MaterialTheme.typography.labelMedium, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Violet).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SetupCard() {
    val ctx = LocalContext.current
    GameCard("⚙️ Falta un paso", null) {
        Text(
            "Activa \"Escudo\" en Accesibilidad para que cuente tu tiempo, te recuerde cuánto llevas en Reels y te dé puntos. " +
                "Solo mira qué app tienes abierta: no lee lo que escribes ni envía nada.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row {
            TextButton(onClick = { open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Activar") }
            TextButton(onClick = { openAppDetails(ctx, ctx.packageName) }) { Text("¿Sale \"restringido\"?") }
        }
    }
}

@Composable
private fun TodayCategories(store: GameStore, stats: Game.DayStats) {
    val cats = AppCategory.entries.filter { store.budget(it) > 0 || stats.min(it) > 0 }
    GameCard("⏳ Hoy", "${formatMinutes(stats.trapMinutes.toLong())} en apps trampa") {
        if (cats.isEmpty()) Text("Aún no has usado apps trampa hoy. ¡Bien! 🙌", style = MaterialTheme.typography.bodySmall)
        cats.forEach { c ->
            val used = stats.min(c)
            val budget = store.budget(c)
            val ratio = if (budget > 0) used.toFloat() / budget else 0f
            val color = when {
                budget == 0 -> MaterialTheme.colorScheme.outline
                ratio >= 1f -> Color(0xFFE5484D)
                ratio >= 0.8f -> Color(0xFFF5A524)
                else -> Color(0xFF1E9E5A)
            }
            Column(Modifier.padding(vertical = 5.dp)) {
                Row {
                    Text("${c.emoji} ${c.label}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(if (budget > 0) "$used / $budget min" else "$used min", style = MaterialTheme.typography.bodySmall)
                }
                if (budget > 0) LinearProgressIndicator(
                    progress = { ratio.coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = color,
                )
            }
        }
        Text("Cámbialos en la pestaña Tiempo.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FocusCard(store: GameStore, tick: Long) {
    val ctx = LocalContext.current
    var minutes by remember { mutableIntStateOf(store.focusLength) }
    val active = remember(tick) { store.focusActive }
    GameCard("🎯 Modo foco", if (active) "En marcha" else "+1 XP/min") {
        if (active) {
            val left = ((store.focusUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            Text(
                "%02d:%02d".format(left / 60, left % 60), fontSize = 44.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
            Text(
                "Tus apps trampa están en pausa. Si abres una, Escudo te devuelve aquí.",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { GameEngine.giveUpFocus(ctx) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Rendirme") }
        } else {
            Text(
                "Estudia, entrena o crea sin interrupciones: durante el modo foco las apps trampa se cierran solas. " +
                    "Al terminar ganas 1 XP por minuto + ${Game.Xp.FOCUS_BONUS} de bonus.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.padding(vertical = 8.dp)) {
                listOf(15, 25, 45, 60).forEach { m ->
                    FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") })
                    Spacer(Modifier.width(6.dp))
                }
            }
            Button(onClick = { GameEngine.startFocus(ctx, minutes) }, modifier = Modifier.fillMaxWidth()) { Text("Empezar 🚀") }
            if (!ShieldState.connected) {
                Text("Necesita el servicio de accesibilidad activo para cerrar las apps.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Achievements(unlocked: Set<String>) {
    var detail by remember { mutableStateOf<Game.Achievement?>(null) }
    GameCard("🏅 Logros", "${unlocked.size}/${Game.ACHIEVEMENTS.size}") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Game.ACHIEVEMENTS.forEach { a ->
                val got = a.id in unlocked
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (got) Violet.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { detail = a },
                    contentAlignment = Alignment.Center,
                ) { Text(if (got) a.emoji else "❔", fontSize = 26.sp) }
            }
        }
        detail?.let { a ->
            Text(
                "${if (a.id in unlocked) a.emoji else "🔒"} ${a.title}: ${a.desc}",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ configuración inicial

private val GOALS = listOf("⚽ Deporte", "🎸 Música", "🎮 Crear cosas", "📚 Estudiar", "🫂 Amigos", "😴 Dormir más", "🎨 Arte", "🧘 Estar tranquilo")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Onboarding(store: GameStore, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(store.name) }
    var avatar by remember { mutableStateOf("🐣") }
    var goal by remember { mutableStateOf("") }
    var preset by remember { mutableIntStateOf(1) }

    Column(
        Modifier.fillMaxSize().background(HeroBrush).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val white = Color.White
        when (page) {
            0 -> {
                Text("🛡️", fontSize = 64.sp)
                Text("Recupera tu tiempo", color = white, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "TikTok, Reels o Shorts están diseñados por equipos enormes para que no puedas parar. " +
                        "Si te cuesta dejarlos, no es culpa tuya.\n\nEscudo es tu entrenador: tú pones las reglas, " +
                        "ganas XP por cada decisión consciente, subes de nivel y ves cuánto tiempo recuperas. " +
                        "Sin juicios y sin que nadie vea tus datos.",
                    color = white.copy(alpha = 0.92f), fontSize = 16.sp,
                )
            }
            1 -> {
                Text("¿Cómo te llamas?", color = white, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it.take(20) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Tu nombre o apodo") })
                Spacer(Modifier.height(16.dp))
                Text("Elige tu avatar (desbloquearás más)", color = white)
                Row {
                    Game.avatarsFor(1).forEach { a ->
                        Box(
                            Modifier.padding(6.dp).size(64.dp).clip(CircleShape)
                                .background(white.copy(alpha = if (a == avatar) 0.35f else 0.12f)).clickable { avatar = a },
                            contentAlignment = Alignment.Center,
                        ) { Text(a, fontSize = 34.sp) }
                    }
                }
            }
            2 -> {
                Text("¿Para qué quieres más tiempo?", color = white, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Escudo te lo recordará cuando lleves un rato de scroll.", color = white.copy(alpha = 0.85f))
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GOALS.forEach { g ->
                        val clean = g.substringAfter(' ').lowercase()
                        FilterChip(selected = goal == clean, onClick = { goal = clean }, label = { Text(g, color = white) })
                    }
                }
            }
            3 -> {
                Text("Tus presupuestos diarios", color = white, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Empieza fácil: es mejor cumplir un objetivo suave que fallar uno duro. Luego lo ajustas en Tiempo.", color = white.copy(alpha = 0.85f))
                Spacer(Modifier.height(12.dp))
                listOf("🌱 Suave" to 1.5, "⚖️ Equilibrado" to 1.0, "🔥 Intenso" to 0.5).forEachIndexed { i, (l, f) ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { preset = i },
                        colors = CardDefaults.cardColors(containerColor = white.copy(alpha = if (preset == i) 0.3f else 0.12f)),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(l, color = white, fontWeight = FontWeight.Bold)
                            Text(
                                AppCategory.entries.filter { it.trap }.joinToString(" · ") { "${it.emoji} ${(it.defaultBudget * f).toInt()} min" },
                                color = white.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            else -> {
                Text("Último paso", color = white, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Para contar tu tiempo y avisarte en Reels, activa \"Escudo\" en Accesibilidad. Y para ver tu historial, " +
                        "dale \"Acceso a datos de uso\". Todo se queda en tu móvil.",
                    color = white.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ShieldState.connected) "Accesibilidad ✓" else "1. Activar accesibilidad", color = white)
                }
                OutlinedButton(onClick = { open(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (PrivacyAuditor.hasUsageAccess(ctx)) "Datos de uso ✓" else "2. Dar acceso a datos de uso", color = white)
                }
                Text(
                    "En Xiaomi o Android 13+ puede salir \"Ajuste restringido\": Info de la app → ⋮ → Permitir ajustes restringidos.",
                    color = white.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${page + 1}/5", color = white.copy(alpha = 0.7f))
            Row {
                if (page > 0) TextButton(onClick = { page-- }) { Text("Atrás", color = white) }
                Button(onClick = {
                    if (page < 4) page++ else {
                        store.name = name.trim(); store.avatar = avatar; store.goal = goal
                        val f = listOf(1.5, 1.0, 0.5)[preset]
                        AppCategory.entries.filter { it.trap }.forEach { store.setBudget(it, (it.defaultBudget * f).toInt()) }
                        store.onboarded = true
                        store.lastDay = java.time.LocalDate.now().toString()
                        GameEngine.addXp(ctx, 50, "¡Bienvenida a Escudo!")
                        onDone()
                    }
                }) { Text(if (page < 4) "Siguiente" else "¡Empezar! 🚀") }
            }
        }
    }
}
