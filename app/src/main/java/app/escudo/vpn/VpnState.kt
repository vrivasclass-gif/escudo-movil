package app.escudo.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Estado en vivo del cortafuegos, compartido con la interfaz. */
object VpnState {
    data class Entry(val time: Long, val domain: String, val app: String, val blocked: Boolean, val reason: String)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _status = MutableStateFlow("Detenido")
    val status: StateFlow<String> = _status

    @Volatile
    var sessionQueries = 0L
        private set

    @Volatile
    var sessionBlocked = 0L
        private set

    @Volatile
    var listSize = 0
        private set

    private val log = ArrayDeque<Entry>()
    private const val MAX_LOG = 400

    fun setRunning(r: Boolean, status: String) { _running.value = r; _status.value = status }
    fun setStatus(s: String) { _status.value = s }
    fun setListSize(n: Int) { listSize = n }

    @Synchronized
    fun record(domain: String, app: String, blocked: Boolean, reason: String) {
        sessionQueries++
        if (blocked) sessionBlocked++
        if (log.size >= MAX_LOG) log.removeFirst()
        log.addLast(Entry(System.currentTimeMillis(), domain, app, blocked, reason))
    }

    @Synchronized
    fun recent(onlyBlocked: Boolean): List<Entry> =
        log.reversed().let { l -> if (onlyBlocked) l.filter { it.blocked } else l }

    /** Ranking de apps que más intentan contactar con rastreadores en esta sesión. */
    @Synchronized
    fun topOffenders(): List<Pair<String, Int>> =
        log.filter { it.blocked }.groupingBy { it.app }.eachCount().entries
            .sortedByDescending { it.value }.take(8).map { it.key to it.value }

    @Synchronized
    fun resetSession() { sessionQueries = 0; sessionBlocked = 0; log.clear() }
}
