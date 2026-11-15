package app.escudo.core

/**
 * Bloqueo de dominios ultracompacto.
 *
 * En lugar de guardar cientos de miles de cadenas (30-40 MB), guarda un hash
 * FNV-1a de 64 bits por dominio en un LongArray ordenado: ~8 bytes por dominio
 * (235.000 dominios ≈ 1,9 MB) y búsqueda binaria en microsegundos.
 *
 * Un dominio está bloqueado si él o cualquiera de sus dominios padre está en la
 * lista ("a.b.tracker.com" se bloquea si está "tracker.com"), salvo que él o un
 * padre esté en la lista de permitidos, que siempre gana.
 */
class DomainMatcher private constructor(
    private val blocked: LongArray,
    private val allowed: LongArray,
) {
    val size: Int get() = blocked.size

    fun isBlocked(domain: String): Boolean {
        val d = normalize(domain) ?: return false
        if (matchesAnySuffix(d, allowed)) return false
        return matchesAnySuffix(d, blocked)
    }

    private fun matchesAnySuffix(domain: String, table: LongArray): Boolean {
        if (table.isEmpty()) return false
        var start = 0
        while (true) {
            if (java.util.Arrays.binarySearch(table, hash(domain, start)) >= 0) return true
            val dot = domain.indexOf('.', start)
            if (dot < 0 || dot == domain.length - 1) return false
            start = dot + 1
            // No comprobamos TLDs sueltos ("com"): nunca están en listas serias
            if (domain.indexOf('.', start) < 0) return false
        }
    }

    class Builder {
        private var blocked = LongArray(1024)
        private var blockedCount = 0
        private val allowed = ArrayList<Long>()

        fun block(domain: String): Builder {
            val d = normalize(domain) ?: return this
            if (blockedCount == blocked.size) blocked = blocked.copyOf(blocked.size * 2)
            blocked[blockedCount++] = hash(d, 0)
            return this
        }

        fun allow(domain: String): Builder {
            normalize(domain)?.let { allowed.add(hash(it, 0)) }
            return this
        }

        fun build(): DomainMatcher {
            val b = sortedUnique(blocked.copyOf(blockedCount))
            val a = sortedUnique(allowed.toLongArray())
            return DomainMatcher(b, a)
        }

        private fun sortedUnique(arr: LongArray): LongArray {
            if (arr.isEmpty()) return arr
            arr.sort()
            var w = 1
            for (i in 1 until arr.size) if (arr[i] != arr[w - 1]) arr[w++] = arr[i]
            return arr.copyOf(w)
        }
    }

    companion object {
        val EMPTY = Builder().build()

        /** Pasa a minúsculas, quita el punto final y descarta basura. */
        fun normalize(raw: String): String? {
            var s = raw.trim().lowercase()
            if (s.endsWith('.')) s = s.dropLast(1)
            if (s.startsWith("*.")) s = s.substring(2)
            if (s.isEmpty() || s.length > 253 || !s.contains('.')) return null
            for (c in s) {
                val ok = c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_'
                if (!ok) return null
            }
            return s
        }

        /** FNV-1a 64 bits del sufijo domain[start..]. Sin crear subcadenas. */
        fun hash(domain: String, start: Int): Long {
            var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (i in start until domain.length) {
                h = h xor domain[i].code.toLong()
                h *= 0x100000001b3L
            }
            return h
        }
    }
}
