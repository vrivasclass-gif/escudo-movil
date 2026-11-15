package app.escudo.core

/**
 * Caché LRU de respuestas DNS. Respetamos el TTL real (entre 10 s y 1 h) para
 * que las apps abran más rápido sin servir datos caducados.
 */
class DnsCache(private val maxEntries: Int = 2048, private val clock: () -> Long = System::currentTimeMillis) {
    private class Entry(val response: ByteArray, val expiresAt: Long)

    private val map = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    @Synchronized
    fun get(name: String, type: Int, id: Int): ByteArray? {
        val key = "$type/$name"
        val e = map[key] ?: return null
        if (clock() >= e.expiresAt) { map.remove(key); return null }
        return Dns.withId(e.response, id)
    }

    @Synchronized
    fun put(name: String, type: Int, response: ByteArray) {
        val ttl = Dns.minTtl(response) ?: return
        val secs = ttl.coerceIn(10, 3600)
        map["$type/$name"] = Entry(response.copyOf(), clock() + secs * 1000L)
    }

    @Synchronized
    fun clear() = map.clear()

    @get:Synchronized
    val size: Int get() = map.size
}
