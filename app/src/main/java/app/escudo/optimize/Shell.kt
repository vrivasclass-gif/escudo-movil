package app.escudo.optimize

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Puente con Shizuku: una app gratuita y de código abierto que da a Escudo los
 * mismos permisos que tendría un ordenador conectado por cable (ADB), sin root.
 * Con eso podemos hacer lo que Android normalmente reserva a los desarrolladores:
 * desactivar apps de fábrica, impedir que una app funcione en segundo plano o
 * quitar permisos que no aparecen en Ajustes. Todo es reversible.
 */
object Shell {
    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    const val REQUEST_CODE = 4242

    fun state(ctx: Context): State {
        val installed = try { ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0); true } catch (_: Exception) { false }
        val alive = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        return when {
            !alive && !installed -> State.NOT_INSTALLED
            !alive -> State.NOT_RUNNING
            Shizuku.isPreV11() -> State.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> State.NO_PERMISSION
            else -> State.READY
        }
    }

    fun requestPermission() {
        try { Shizuku.requestPermission(REQUEST_CODE) } catch (_: Throwable) {}
    }

    data class Result(val code: Int, val out: String) {
        val ok get() = code == 0
    }

    /** Ejecuta un comando con permisos de ADB a través de Shizuku. */
    fun run(cmd: String): Result = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
        Result(p.waitFor(), out.trim())
    } catch (t: Throwable) {
        Result(-1, t.cause?.message ?: t.message ?: t.javaClass.simpleName)
    }
}
