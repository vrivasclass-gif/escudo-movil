package app.escudo.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.escudo.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Actualiza las listas de bloqueo cada pocos días, sin intervención y sin servidores propios. */
class ListUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val msg = BlocklistRepository(applicationContext).updateAll()
        EscudoVpnService.reload(applicationContext)
        if (msg.contains("fallaron") && runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "list-update"

        fun schedule(ctx: Context) {
            val wifi = Prefs(ctx).updateOnlyOnWifi
            val req = PeriodicWorkRequestBuilder<ListUpdateWorker>(3, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                ).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueue(
                OneTimeWorkRequestBuilder<ListUpdateWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        }
    }
}

/** Vuelve a activar el cortafuegos al encender el móvil o al actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = Prefs(context)
        if (prefs.vpnWanted && VpnService.prepare(context) == null) {
            try { EscudoVpnService.start(context) } catch (_: Exception) {}
        }
        ListUpdateWorker.schedule(context)
        GrayscaleWorker.schedule(context)
        app.escudo.optimize.Optimizer.applyGraySchedule(context)
    }
}

/** Comprueba cada 15 minutos el horario de escala de grises (por si el servicio de accesibilidad está apagado). */
class GrayscaleWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        app.escudo.optimize.Optimizer.applyGraySchedule(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "grayscale", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<GrayscaleWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
