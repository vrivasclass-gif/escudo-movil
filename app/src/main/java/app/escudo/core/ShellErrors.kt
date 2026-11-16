package app.escudo.core

/**
 * Traduce los errores que devuelve Android al ejecutar comandos con Shizuku a
 * explicaciones con la solución concreta. Puro para poder probarlo.
 */
object ShellErrors {
    fun explain(out: String, xiaomi: Boolean): String {
        val o = out.lowercase()
        return when {
            o.contains("cannot change component state") || o.contains("user_restricted") ||
                o.contains("securityexception") || o.contains("not allowed") || o.contains("permission denial") ->
                if (xiaomi) "Xiaomi bloquea este cambio. Ve a Ajustes → Ajustes adicionales → Opciones de desarrollador y activa " +
                    "\"Depuración USB (ajustes de seguridad)\" (pide tarjeta SIM y cuenta Xiaomi). Después reinicia Shizuku y vuelve a probar."
                else "Android ha denegado el cambio. Comprueba en Opciones de desarrollador que la depuración sigue activa y reinicia Shizuku."
            o.contains("unknown package") || o.contains("not installed") || o.contains("doesn't exist") ->
                "Esa app ya no está en este móvil."
            o.contains("protected") || o.contains("cannot disable") || o.contains("system app") ->
                "El sistema protege esta app y no deja desactivarla. No pasa nada: no es imprescindible quitarla."
            o.contains("binder") || o.contains("deadobject") || o.contains("shizuku") || o.contains("not running") ->
                "Shizuku se ha desconectado (pasa al reiniciar o tras un rato con algunos fabricantes). Ábrelo y pulsa \"Iniciar\"."
            o.isBlank() -> "Android no ha respondido. Reinicia Shizuku y vuelve a probar."
            else -> "Android ha respondido con un error."
        }
    }
}
