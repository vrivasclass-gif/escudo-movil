package app.escudo.core

/**
 * Lectura y construcción de paquetes IPv4/UDP/DNS crudos tal como salen del
 * interfaz TUN de la VPN local. Código puro (sin APIs de Android) y testeado.
 */
class UdpPacket(
    val srcIp: ByteArray,   // 4 bytes
    val dstIp: ByteArray,   // 4 bytes
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray,
)

object IpPacket {
    const val PROTO_UDP = 17
    const val PROTO_TCP = 6

    /** Versión IP del paquete (4 o 6), o -1 si está vacío. */
    fun version(buf: ByteArray, len: Int): Int = if (len < 1) -1 else (buf[0].toInt() ushr 4) and 0xF

    fun protocol(buf: ByteArray, len: Int): Int = if (len < 20) -1 else buf[9].toInt() and 0xFF

    /** Extrae un datagrama UDP de un paquete IPv4, o null si no lo es o está roto. */
    fun parseUdp(buf: ByteArray, len: Int): UdpPacket? {
        if (len < 28 || version(buf, len) != 4 || protocol(buf, len) != PROTO_UDP) return null
        val ihl = (buf[0].toInt() and 0x0F) * 4
        if (ihl < 20 || len < ihl + 8) return null
        val totalLen = u16(buf, 2)
        // Fragmentos: no los manejamos (las consultas DNS nunca vienen fragmentadas)
        val flagsFrag = u16(buf, 6)
        if (flagsFrag and 0x1FFF != 0 || flagsFrag and 0x2000 != 0) return null
        val end = minOf(len, totalLen)
        val udpLen = u16(buf, ihl + 4)
        if (udpLen < 8 || ihl + udpLen > end) return null
        return UdpPacket(
            srcIp = buf.copyOfRange(12, 16),
            dstIp = buf.copyOfRange(16, 20),
            srcPort = u16(buf, ihl),
            dstPort = u16(buf, ihl + 2),
            payload = buf.copyOfRange(ihl + 8, ihl + udpLen),
        )
    }

    /** Construye la respuesta IPv4/UDP intercambiando origen y destino de [request]. */
    fun buildUdpReply(request: UdpPacket, payload: ByteArray): ByteArray {
        val total = 20 + 8 + payload.size
        val out = ByteArray(total)
        out[0] = 0x45                      // IPv4, cabecera de 20 bytes
        put16(out, 2, total)
        put16(out, 4, 0)                   // id
        put16(out, 6, 0x4000)              // Don't Fragment
        out[8] = 64                        // TTL
        out[9] = PROTO_UDP.toByte()
        System.arraycopy(request.dstIp, 0, out, 12, 4)
        System.arraycopy(request.srcIp, 0, out, 16, 4)
        put16(out, 10, checksum(out, 0, 20))
        put16(out, 20, request.dstPort)
        put16(out, 22, request.srcPort)
        put16(out, 24, 8 + payload.size)
        put16(out, 26, 0)                  // checksum UDP opcional en IPv4
        System.arraycopy(payload, 0, out, 28, payload.size)
        return out
    }

    fun checksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i < off + len - 1) { sum += u16(b, i); i += 2 }
        if (len % 2 == 1) sum += (b[off + len - 1].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    fun put16(b: ByteArray, o: Int, v: Int) { b[o] = (v ushr 8).toByte(); b[o + 1] = v.toByte() }
}

class DnsQuestion(val id: Int, val name: String, val type: Int, val questionEnd: Int)

object Dns {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val TYPE_HTTPS = 65
    const val RCODE_NXDOMAIN = 3

    /** Lee la primera pregunta de un mensaje DNS de consulta. */
    fun parseQuestion(msg: ByteArray): DnsQuestion? {
        if (msg.size < 12) return null
        val flags = IpPacket.u16(msg, 2)
        if (flags and 0x8000 != 0) return null           // es una respuesta, no una consulta
        if (IpPacket.u16(msg, 4) < 1) return null         // sin preguntas
        val sb = StringBuilder()
        var p = 12
        while (true) {
            if (p >= msg.size) return null
            val l = msg[p].toInt() and 0xFF
            if (l == 0) { p++; break }
            if (l and 0xC0 != 0) return null              // compresión en la pregunta: inválido
            if (p + 1 + l > msg.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..l) sb.append((msg[p + i].toInt() and 0xFF).toChar().lowercaseChar())
            p += 1 + l
            if (sb.length > 253) return null
        }
        if (p + 4 > msg.size) return null
        return DnsQuestion(IpPacket.u16(msg, 0), sb.toString(), IpPacket.u16(msg, p), p + 4)
    }

    /**
     * Respuesta para un dominio bloqueado. Para A/AAAA devuelve 0.0.0.0 / ::
     * (las apps fallan al instante, sin reintentos); para el resto NXDOMAIN.
     */
    fun blockedResponse(query: ByteArray, q: DnsQuestion): ByteArray {
        val answer: ByteArray? = when (q.type) {
            TYPE_A -> ByteArray(4)
            TYPE_AAAA -> ByteArray(16)
            else -> null
        }
        val qLen = q.questionEnd - 12
        val size = 12 + qLen + (if (answer != null) 12 + answer.size else 0)
        val out = ByteArray(size)
        IpPacket.put16(out, 0, q.id)
        val rd = IpPacket.u16(query, 2) and 0x0100
        val rcode = if (answer != null) 0 else RCODE_NXDOMAIN
        IpPacket.put16(out, 2, 0x8000 or 0x0080 or rd or rcode) // QR | RA | RD | rcode
        IpPacket.put16(out, 4, 1)
        IpPacket.put16(out, 6, if (answer != null) 1 else 0)
        System.arraycopy(query, 12, out, 12, qLen)
        if (answer != null) {
            var p = 12 + qLen
            IpPacket.put16(out, p, 0xC00C); p += 2        // puntero al nombre de la pregunta
            IpPacket.put16(out, p, q.type); p += 2
            IpPacket.put16(out, p, 1); p += 2             // clase IN
            out[p + 3] = 60; p += 4                       // TTL 60 s
            IpPacket.put16(out, p, answer.size); p += 2
            // rdata ya está a ceros
        }
        return out
    }

    /** Mensaje de error SERVFAIL para cuando el servidor DNS de subida no responde. */
    fun servfail(query: ByteArray, q: DnsQuestion): ByteArray {
        val qLen = q.questionEnd - 12
        val out = ByteArray(12 + qLen)
        IpPacket.put16(out, 0, q.id)
        IpPacket.put16(out, 2, 0x8000 or 0x0080 or (IpPacket.u16(query, 2) and 0x0100) or 2)
        IpPacket.put16(out, 4, 1)
        System.arraycopy(query, 12, out, 12, qLen)
        return out
    }

    /**
     * TTL mínimo de las respuestas de un mensaje DNS (en segundos), para la caché.
     * Devuelve null si el mensaje no es cacheable (error, truncado o mal formado).
     */
    fun minTtl(msg: ByteArray): Int? {
        if (msg.size < 12) return null
        val flags = IpPacket.u16(msg, 2)
        if (flags and 0x0200 != 0) return null            // truncado
        val rcode = flags and 0xF
        if (rcode != 0 && rcode != RCODE_NXDOMAIN) return null
        val qd = IpPacket.u16(msg, 4)
        val an = IpPacket.u16(msg, 6)
        val ns = IpPacket.u16(msg, 8)
        var p = 12
        repeat(qd) { p = skipName(msg, p) ?: return null; p += 4 }
        var min = Int.MAX_VALUE
        repeat(an + ns) {
            p = skipName(msg, p) ?: return null
            if (p + 10 > msg.size) return null
            val ttl = ((msg[p + 4].toInt() and 0xFF) shl 24) or ((msg[p + 5].toInt() and 0xFF) shl 16) or
                ((msg[p + 6].toInt() and 0xFF) shl 8) or (msg[p + 7].toInt() and 0xFF)
            val rdLen = IpPacket.u16(msg, p + 8)
            if (ttl in 0 until min) min = ttl
            p += 10 + rdLen
            if (p > msg.size) return null
        }
        return if (min == Int.MAX_VALUE) 30 else min
    }

    private fun skipName(msg: ByteArray, start: Int): Int? {
        var p = start
        while (p < msg.size) {
            val l = msg[p].toInt() and 0xFF
            when {
                l == 0 -> return p + 1
                l and 0xC0 == 0xC0 -> return if (p + 2 <= msg.size) p + 2 else null
                else -> p += 1 + l
            }
        }
        return null
    }

    /** Copia de [msg] con otro identificador de transacción (para servir desde caché). */
    fun withId(msg: ByteArray, id: Int): ByteArray = msg.copyOf().also { IpPacket.put16(it, 0, id) }
}
