package app.escudo.core

/**
 * Lee listas de bloqueo en los formatos habituales:
 *  - hosts:     "0.0.0.0 tracker.com" / "127.0.0.1 tracker.com"
 *  - dominios:  "tracker.com"
 *  - adblock:   "||tracker.com^"  (las reglas de excepción "@@" se ignoran)
 *  - wildcard:  "*.tracker.com"
 * Ignora comentarios (#, !), líneas vacías y entradas tipo localhost.
 */
object ListParser {
    private val IGNORED = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
    )

    /** Devuelve el dominio de una línea, o null si la línea no aporta nada. */
    fun parseLine(rawLine: String): String? {
        var line = rawLine.trim()
        if (line.isEmpty() || line[0] == '#' || line[0] == '!' || line[0] == '[') return null
        if (line.startsWith("@@")) return null
        val hashIdx = line.indexOf('#')
        if (hashIdx > 0) line = line.substring(0, hashIdx).trim()

        if (line.startsWith("||")) {
            if (line.contains('$')) return null // reglas con modificadores ($third-party…): no son de DNS
            val end = line.indexOfFirst { it == '^' || it == '/' }
            line = if (end > 2) line.substring(2, end) else line.substring(2)
        } else {
            val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
            line = when {
                parts.size >= 2 && looksLikeIp(parts[0]) -> parts[1]
                parts.size == 1 -> parts[0]
                else -> return null
            }
        }
        val d = DomainMatcher.normalize(line) ?: return null
        if (d in IGNORED) return null
        return d
    }

    fun parseInto(lines: Sequence<String>, builder: DomainMatcher.Builder): Int {
        var n = 0
        for (l in lines) {
            val d = parseLine(l) ?: continue
            builder.block(d); n++
        }
        return n
    }

    private fun looksLikeIp(s: String): Boolean =
        s == "0.0.0.0" || s == "127.0.0.1" || s == "::" || s == "::1" || s == "0" ||
            s.all { it.isDigit() || it == '.' } || s.contains(':')
}
