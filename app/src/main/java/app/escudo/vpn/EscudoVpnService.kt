package app.escudo.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import app.escudo.R
import app.escudo.core.DnsCache
import app.escudo.core.Dns
import app.escudo.core.DomainMatcher
import app.escudo.core.IpPacket
import app.escudo.core.UdpPacket
import app.escudo.data.Prefs
import app.escudo.data.Upstreams
import app.escudo.ui.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cortafuegos DNS local.
 *
 * Crea una VPN que NO sale del teléfono: solo se enruta por ella el tráfico DNS
 * (el resto de tu tráfico va directo, sin pasar por la app, así que no hay
 * pérdida de velocidad ni de batería). Cada consulta DNS se compara con las
 * listas de bloqueo; si es un rastreador se responde 0.0.0.0 al instante y la
 * conexión nunca llega a producirse. Lo demás se reenvía al DNS elegido.
 */
class EscudoVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var output: FileOutputStream? = null
    private var reader: Thread? = null
    private var workers: ExecutorService? = null
    private val running = AtomicBoolean(false)

    @Volatile private var matcher: DomainMatcher = DomainMatcher.EMPTY
    @Volatile private var cutUids: Set<Int> = emptySet()
    @Volatile private var upstream: List<InetAddress> = emptyList()
    private val cache = DnsCache()
    private val appLabels = ConcurrentHashMap<Int, String>()
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { prefs.vpnWanted = false; shutdown(); stopSelf(); return START_NOT_STICKY }
            ACTION_RELOAD -> { if (running.get()) reloadRules() else start(); return START_STICKY }
            else -> start() // también cubre "VPN siempre activa" y el reinicio tras arrancar el móvil
        }
        return START_STICKY
    }

    private fun start() {
        if (running.get()) return
        startForegroundCompat()
        prefs.vpnWanted = true
        VpnState.setRunning(true, "Cargando listas…")
        Thread({
            try {
                reloadRules()
                establish()
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo iniciar", e)
                VpnState.setRunning(false, "Error: ${e.message}")
                shutdown(); stopSelf()
            }
        }, "escudo-start").start()
    }

    private fun reloadRules() {
        val m = BlocklistRepository(this).buildMatcher()
        matcher = m
        VpnState.setListSize(m.size)
        upstream = Upstreams.byId(prefs.upstreamDns).servers.map { InetAddress.getByName(it) }
        cutUids = prefs.cutApps.mapNotNull { pkg ->
            try { packageManager.getApplicationInfo(pkg, 0).uid } catch (_: PackageManager.NameNotFoundException) { null }
        }.toSet()
        cache.clear()
        if (running.get()) VpnState.setStatus(statusText())
    }

    private fun statusText() = "Protegiendo · ${"%,d".format(matcher.size)} dominios en lista"

    private fun establish() {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(1500)
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)
            .setBlocking(true)
            .setConfigureIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
        if (prefs.forceDns) PUBLIC_DNS.forEach { builder.addRoute(it, 32) }
        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val fd = builder.establish() ?: error("Permiso de VPN no concedido")
        tun = fd
        output = FileOutputStream(fd.fileDescriptor)
        workers = Executors.newFixedThreadPool(8)
        running.set(true)
        VpnState.setRunning(true, statusText())
        reader = Thread({ readLoop(FileInputStream(fd.fileDescriptor)) }, "escudo-tun").also { it.start() }
    }

    private fun readLoop(input: FileInputStream) {
        val buf = ByteArray(32767)
        while (running.get()) {
            val n = try { input.read(buf) } catch (e: Exception) { if (running.get()) Log.w(TAG, "tun read", e); break }
            if (n <= 0) { if (n < 0) break else continue }
            val udp = IpPacket.parseUdp(buf, n) ?: continue      // TCP a DNS públicos (DoT/DoH): se descarta
            if (udp.dstPort != 53) continue
            try { workers?.execute { handleDns(udp) } } catch (_: Exception) {}
        }
    }

    private fun handleDns(req: UdpPacket) {
        val q = Dns.parseQuestion(req.payload) ?: return
        val uid = ownerUid(req)
        val app = if (uid >= 0) labelFor(uid) else "?"

        val reason = when {
            uid >= 0 && uid in cutUids -> "app sin internet"
            matcher.isBlocked(q.name) -> "lista de bloqueo"
            else -> null
        }
        if (reason != null) {
            write(req, Dns.blockedResponse(req.payload, q))
            VpnState.record(q.name, app, true, reason)
            prefs.totalBlocked = prefs.totalBlocked + 1
            return
        }
        VpnState.record(q.name, app, false, "")
        cache.get(q.name, q.type, q.id)?.let { write(req, it); return }

        val resp = forward(req.payload) ?: Dns.servfail(req.payload, q)
        cache.put(q.name, q.type, resp)
        write(req, resp)
    }

    private fun forward(query: ByteArray): ByteArray? {
        for (server in upstream) {
            try {
                DatagramSocket().use { s ->
                    protect(s) // este socket sale por la red real, no vuelve a la VPN
                    s.soTimeout = 3000
                    s.send(DatagramPacket(query, query.size, server, 53))
                    val rb = ByteArray(4096)
                    val rp = DatagramPacket(rb, rb.size)
                    s.receive(rp)
                    return rb.copyOf(rp.length)
                }
            } catch (e: Exception) {
                Log.d(TAG, "DNS $server sin respuesta: ${e.message}")
            }
        }
        return null
    }

    private fun write(req: UdpPacket, payload: ByteArray) {
        val pkt = IpPacket.buildUdpReply(req, payload)
        val out = output ?: return
        try { synchronized(out) { out.write(pkt) } } catch (e: Exception) { Log.d(TAG, "tun write: ${e.message}") }
    }

    /** UID de la app que hizo la consulta (Android 10+). */
    private fun ownerUid(req: UdpPacket): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        return try {
            val cm = getSystemService(ConnectivityManager::class.java)
            cm.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(req.srcIp), req.srcPort),
                InetSocketAddress(InetAddress.getByAddress(req.dstIp), req.dstPort),
            )
        } catch (_: Exception) { -1 }
    }

    private fun labelFor(uid: Int): String = appLabels.getOrPut(uid) {
        val pkgs = packageManager.getPackagesForUid(uid)
        if (pkgs.isNullOrEmpty()) {
            if (uid == 1000) "Sistema Android" else "uid $uid"
        } else try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkgs[0], 0)).toString()
        } catch (_: Exception) { pkgs[0] }
    }

    private fun shutdown() {
        running.set(false)
        try { tun?.close() } catch (_: Exception) {}
        tun = null; output = null
        reader?.interrupt(); reader = null
        workers?.shutdownNow(); workers = null
        VpnState.setRunning(false, "Detenido")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() {
        // Otra VPN tomó el control o el usuario la quitó desde Ajustes
        prefs.vpnWanted = false
        shutdown(); stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_vpn), NotificationManager.IMPORTANCE_MIN)
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, EscudoVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.notif_vpn_title))
            .setContentText(getString(R.string.notif_vpn_text))
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val TAG = "Escudo/VPN"
        const val ACTION_STOP = "app.escudo.STOP"
        const val ACTION_RELOAD = "app.escudo.RELOAD"
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 1
        private const val TUN_ADDRESS = "10.111.222.1"
        private const val FAKE_DNS = "10.111.222.53"

        /** DNS públicos que algunas apps usan saltándose el del sistema. */
        private val PUBLIC_DNS = listOf(
            "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112",
            "208.67.222.222", "208.67.220.220", "94.140.14.14", "94.140.15.15",
            "185.228.168.9", "185.228.169.9", "76.76.2.0", "76.76.10.0", "194.242.2.2", "194.242.2.3",
        )

        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, EscudoVpnService::class.java))
        fun stop(ctx: Context) = ctx.startService(Intent(ctx, EscudoVpnService::class.java).setAction(ACTION_STOP))
        fun reload(ctx: Context) {
            if (VpnState.running.value) ctx.startService(Intent(ctx, EscudoVpnService::class.java).setAction(ACTION_RELOAD))
        }
    }
}
