package app.escudo.vpn

import android.content.Context
import android.util.Log
import app.escudo.core.DomainMatcher
import app.escudo.core.ListParser
import app.escudo.data.Blocklists
import app.escudo.data.Prefs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gestiona las listas de bloqueo guardadas en el teléfono:
 *  - una lista base incluida en la app (funciona sin internet desde el primer segundo)
 *  - las listas públicas que el usuario elija, descargadas y actualizadas en local
 *  - las reglas manuales del usuario (bloquear / permitir)
 */
class BlocklistRepository(private val context: Context) {
    private val prefs = Prefs(context)
    private val dir = File(context.filesDir, "lists").apply { mkdirs() }

    fun fileFor(id: String) = File(dir, "$id.txt")

    fun customUrls(): List<String> = prefs.customListUrls.lineSequence()
        .map { it.trim() }.filter { it.startsWith("https://") }.toList()

    private fun customId(url: String) = "custom_" + Integer.toHexString(url.hashCode())

    /** Construye el filtro con todo lo activado. Tarda ~1 s con 250.000 dominios. */
    fun buildMatcher(): DomainMatcher {
        val b = DomainMatcher.Builder()
        var total = 0
        context.assets.open("base_blocklist.txt").bufferedReader().useLines { total += ListParser.parseInto(it, b) }
        val ids = prefs.enabledLists + customUrls().map(::customId)
        for (id in ids) {
            val f = fileFor(id)
            if (f.exists()) f.bufferedReader().useLines { total += ListParser.parseInto(it, b) }
        }
        total += ListParser.parseInto(prefs.userBlocked.lineSequence(), b)
        prefs.userAllowed.lineSequence().forEach { b.allow(it) }
        // Nunca romper la propia actualización de listas ni la conectividad básica
        SAFE_DOMAINS.forEach { b.allow(it) }
        Log.i(TAG, "Filtro construido con $total entradas")
        return b.build()
    }

    /**
     * Descarga las listas activadas. Devuelve un resumen legible.
     * Se escribe a un archivo temporal y solo se sustituye si la descarga es válida.
     */
    fun updateAll(): String {
        val targets = Blocklists.ALL.filter { it.id in prefs.enabledLists }.map { it.id to it.url } +
            customUrls().map { customId(it) to it }
        var ok = 0
        val errors = mutableListOf<String>()
        for ((id, url) in targets) {
            try {
                download(url, fileFor(id)); ok++
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al descargar $url", e)
                errors += id
            }
        }
        if (ok > 0) prefs.listsUpdatedAt = System.currentTimeMillis()
        // Limpia listas desactivadas para no ocupar espacio
        val keep = targets.map { "${it.first}.txt" }.toSet()
        dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        return if (errors.isEmpty()) "$ok listas actualizadas" else "$ok actualizadas, fallaron: ${errors.joinToString()}"
    }

    private fun download(url: String, dest: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "Escudo")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val tmp = File(dest.path + ".tmp")
            var valid = 0
            conn.inputStream.bufferedReader().useLines { lines ->
                tmp.bufferedWriter().use { w ->
                    for (l in lines) {
                        val d = ListParser.parseLine(l) ?: continue
                        w.write(d); w.write('\n'.code); valid++
                    }
                }
            }
            if (valid < 10) { tmp.delete(); error("Lista vacía o no válida") }
            if (!tmp.renameTo(dest)) { dest.delete(); tmp.renameTo(dest) }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "Escudo/Lists"
        val SAFE_DOMAINS = listOf(
            "raw.githubusercontent.com", "github.com", "objects.githubusercontent.com",
            "connectivitycheck.gstatic.com", "connectivitycheck.android.com", "clients3.google.com",
        )
    }
}
