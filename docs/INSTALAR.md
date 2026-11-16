# Cómo instalar Escudo (sin ser programador)

## Opción recomendada: Obtainium (se actualiza solo)
Obtainium es una app libre que instala apps directamente desde GitHub y te avisa de las nuevas versiones.

1. En el móvil, abre **https://github.com/ImranR98/Obtainium/releases** y descarga el archivo `app-arm64-v8a-release.apk` de la última versión.
2. Ábrelo. Android te pedirá permitir "Instalar apps desconocidas" para tu navegador: acéptalo e instala.
3. Abre Obtainium → **Añadir app** → pega: `https://github.com/vrivasclass-gif/escudo-movil` → **Añadir**.
4. Pulsa **Instalar**. A partir de ahora, Obtainium instalará cada nueva versión de Escudo.

## Opción manual
1. Abre **https://github.com/vrivasclass-gif/escudo-movil/releases** en el móvil.
2. Descarga `Escudo-vX.Y.Z.apk` de la última versión y ábrelo para instalarlo.

## Primera configuración (5 minutos)
La app tiene 5 pestañas: **Yo · Tiempo · Algoritmo · Red · Más**.
1. Al abrirla por primera vez, sigue la **configuración inicial** (nombre, avatar, objetivo y presupuestos). Al final te pide:
   - **Accesibilidad** → *Apps instaladas* → **Escudo** → activar. Sirve para contar tu tiempo, los recordatorios y el modo foco.
   - En Android 13+ o Xiaomi puede salir **"Ajuste restringido"**: Ajustes → Apps → Escudo → menú **⋮** → **Permitir ajustes restringidos**, y vuelve a activarlo.
   - **Acceso a datos de uso** → Escudo → permitir.
2. **Tiempo**: ajusta el presupuesto y el modo (Contar / Avisar / Bloquear) de cada categoría, y si alguna app está mal clasificada, cámbiala de categoría.
3. **Red → Bloquear rastreadores**: acepta el permiso de VPN. Aparecerá una llave 🔑 en la barra de estado. Después pulsa **Actualizar ahora** para descargar las listas completas.
4. **Más → Ajustes**, haz al menos:
   - **VPN siempre activa** (así nunca se apaga la protección).
   - **Que Android no cierre Escudo** (quita la optimización de batería).
   - **Borrar el ID de publicidad**.
5. **Algoritmo → Plan anti-algoritmo**: sigue los pasos para Instagram (o la app que quieras). Cada paso da XP.

## Optimizar con Shizuku (opcional, sin root)
Shizuku es una app gratuita que da a Escudo permisos de "depuración" para desactivar bloatware, quitar permisos ocultos, acelerar animaciones y poner la pantalla en grises.
1. Instala **Shizuku** desde Google Play o https://github.com/RikkaApps/Shizuku/releases
2. Activa las **Opciones de desarrollador**: Ajustes → Información del teléfono → toca 7 veces "Número de compilación".
3. Abre Shizuku → **Iniciar mediante depuración inalámbrica** y sigue sus pasos (emparejar con el código que aparece en Ajustes → Opciones de desarrollador → Depuración inalámbrica).
4. En Escudo → **Más → Optimizar** → **Dar permiso a Escudo** y después **Conceder con Shizuku** (permiso permanente).
5. Tras reiniciar el móvil hay que volver a iniciar Shizuku, pero la escala de grises y las animaciones siguen funcionando sin él.

### Para los móviles Xiaomi, Huawei, Oppo, Realme, Vivo y Samsung
Estos fabricantes cierran apps en segundo plano de forma agresiva. Además del paso de batería:
- Xiaomi: Ajustes → Apps → Escudo → Ahorro de batería → **Sin restricciones**, y activa **Inicio automático**.
- Samsung: Ajustes → Batería → Límites de uso en segundo plano → añade Escudo a **Apps que nunca se suspenden**.
- Activa también la lista de telemetría de tu marca en **Red → Listas de bloqueo**.

## Si algo deja de funcionar
Alguna app o web puede fallar si depende de un dominio de rastreo.
1. Ve a **Red → Registro en vivo**, busca el dominio bloqueado justo cuando falla.
2. Tócalo → **Permitir siempre**.
Si no lo encuentras, prueba a desactivar el cortafuegos un momento para confirmar que es la causa.
