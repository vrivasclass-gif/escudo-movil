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
1. **Inicio → Bloquear rastreadores**: acepta el permiso de VPN y el de notificaciones. Aparecerá una llave 🔑 en la barra de estado.
2. **Red → Actualizar ahora**: descarga las listas completas (unos 5 MB). Después se actualizan solas.
3. **Escudo anti-algoritmo**: Inicio → *Accesibilidad* → *Apps instaladas* → **Escudo anti-algoritmo** → activar.
   - En Android 13 o superior puede salir **"Ajuste restringido"**. Solución: Inicio → *Info de la app* → menú **⋮** (arriba a la derecha) → **Permitir ajustes restringidos**, y vuelve a activarlo.
4. **Escudo → Tu uso → Conceder acceso**: activa Escudo en "Acceso a datos de uso" para ver tu panel.
5. En **Inicio → Ajustes recomendados**, haz al menos:
   - **VPN siempre activa** (así nunca se apaga la protección).
   - **Que Android no cierre Escudo** (quita la optimización de batería).
   - **Borrar el ID de publicidad**.

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
