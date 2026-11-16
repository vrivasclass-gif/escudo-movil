package app.escudo.shield

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.escudo.data.Prefs
import app.escudo.game.GameEngine
import app.escudo.ui.EscudoTheme
import kotlinx.coroutines.delay

/**
 * Pausa consciente: rompe el gesto automático de abrir una red social.
 * Una cuenta atrás obliga a decidir de forma deliberada si de verdad quieres entrar.
 */
class PauseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: run { finish(); return }
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 10)
        val prefs = Prefs(this)
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }
        val usedMin = prefs.usageToday(pkg) / 60_000

        setContent {
            EscudoTheme {
                var left by remember { mutableIntStateOf(seconds) }
                LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
                BackHandler { goHome() }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Respira.", fontSize = 40.sp, style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Vas a abrir $label.\n¿Para qué? ¿Es algo que tú decides o un impulso?",
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Hoy llevas $usedMin min en esta app.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(40.dp))
                        Button(onClick = { GameEngine.onPauseResist(this@PauseActivity); goHome() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Mejor no 💪 (+10 XP)")
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                prefs.setAllowUntil(pkg, System.currentTimeMillis() + ALLOW_MINUTES * 60_000L)
                                packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                                finish()
                            },
                            enabled = left == 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (left > 0) "Espera $left s…" else "Abrir durante $ALLOW_MINUTES min")
                        }
                    }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_SECONDS = "secs"
        const val ALLOW_MINUTES = 15
    }
}
