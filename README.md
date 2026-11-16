# Escudo 🛡️

App Android de código abierto que protege tu privacidad y te devuelve el control sobre tu atención.
Funciona **100 % en tu teléfono**: sin cuentas, sin servidores propios, sin publicidad y sin depender de nadie para funcionar ni actualizarse.

## Qué hace

### 0. Tu tiempo, como un juego (pensado para adolescentes)
Basado en psicología del cambio de hábitos: refuerzo positivo, autonomía y pasos pequeños. Sin vergüenza ni sermones.
- **Pantalla "Yo"**: avatar, nivel, XP, racha 🔥 con comodines ❄️, reto del día, logros, semana comparada y tiempo recuperado.
- **Tiempo por categorías** (vídeo corto, redes, juegos, vídeo, compras…): cada una con su presupuesto diario y su modo: *Contar*, *Avisar* o *Bloquear* (con un comodín de 5 min al día que cuesta XP). Cualquier app se puede mover de categoría.
- **Recordatorios en Reels/Shorts/TikTok**: cada pocos minutos (tú eliges cuántos) te dice cuánto llevas y te pregunta si te está aportando. Salir da XP.
- **Modo foco** de 15–60 min: las apps trampa se cierran solas mientras estudias o entrenas.
- **Configuración inicial** de un minuto: nombre, avatar, para qué quieres el tiempo y presupuestos (Suave / Equilibrado / Intenso).

### 1. Cortafuegos anti-rastreo (VPN local)
- Bloquea a nivel DNS los dominios de rastreo, publicidad, telemetría y malware de **todas las apps**, también las del fabricante.
- Usa listas públicas de primer nivel (HaGeZi Pro, StevenBlack, telemetría de Samsung/Xiaomi/TikTok…) guardadas en el móvil y actualizadas solas cada 3 días.
- Solo el tráfico DNS pasa por la app; el resto va directo, así que **no pierdes velocidad ni batería**. Además, la caché DNS hace que las apps abran más rápido.
- **Forzar DNS**: captura las apps que intentan saltarse el filtro usando su propio DNS (8.8.8.8, 1.1.1.1…).
- **Apps sin internet** (Android 10+): corta la conexión a las apps que elijas.
- **Registro en vivo**: ves qué app intenta contactar con qué rastreador, y lo bloqueas o permites con un toque.
- Servidor DNS a elegir: Quad9 (por defecto), Cloudflare, AdGuard o Mullvad.

### 2. Algoritmo: que sepa menos de ti y te enganche menos
La idea no es prohibirte las redes sociales, sino que dejen de ser tan eficaces controlando tu atención.
- **Plan anti-algoritmo** paso a paso para Instagram, TikTok, YouTube, Facebook y X. Cada paso explica por qué funciona: quitarle datos del móvil, apagar notificaciones, cortar el rastreo de Meta en otras apps, usar el feed cronológico, reiniciar lo que sabe de ti…
- **Avisos en vídeos cortos**: al entrar en Reels o Shorts te recuerda dónde estás, sin cerrarte la app (como mucho un aviso cada 3 minutos).
- **Pausa consciente**, **límites por app** y **panel de uso** (en la pestaña Tiempo).

### 3. Auditor de privacidad
- Detecta más de 50 SDKs de rastreo y publicidad dentro de tus apps (AppsFlyer, Meta, AdMob, Adjust…) y los permisos sensibles que tienen concedidos.
- Un toque te lleva a los ajustes de cada app para quitárselos.

### 4. Optimizar (con Shizuku, sin root)
- **Bloatware**: desactiva apps de fábrica que recogen datos o ponen anuncios (Facebook preinstalado, analytics de Xiaomi, Samsung Free…). Reversible.
- **Permisos ocultos**: quita a cualquier app ubicación, contactos, portapapeles, escaneo Wi-Fi/Bluetooth y el permiso de funcionar en segundo plano, con una explicación de cada uno.
- **Fluidez**: animaciones a 0,5x.
- **Escala de grises** ahora o por horario.
- **Historial con deshacer** para cada cambio.

### 5. Explicado en cada paso
Cada sección tiene un "¿Qué hace esto y por qué?" y el registro explica qué empresa hay detrás de cada dominio bloqueado y para qué lo usa.

## Instalación
Lee la [guía de instalación](docs/INSTALAR.md). En resumen: descarga el APK de [Releases](../../releases) o, mejor, instálalo con **Obtainium** para recibir las actualizaciones automáticamente.

## Límites honestos
Android no permite a una app normal (sin root) modificar otras apps ni el sistema. Por eso Escudo **no** es un "limpiador de RAM" ni un "booster": esas apps no funcionan. Lo que sí hace es cortar el rastreo en la red, que es donde más batería, datos y privacidad se pierden, y cambiar el entorno para que el algoritmo no decida por ti.
- Las apps que usan DNS cifrado propio (Chrome con "DNS seguro", Firefox) pueden saltarse el filtro: activa la lista *Anti-DoH/VPN/proxy* o desactiva el DNS seguro en el navegador.
- Solo puede haber una VPN activa a la vez en Android.
- La detección de Reels/Shorts depende del diseño interno de cada app; si cambia, se ajusta en *Escudo > Reglas de feeds*.

## Hoja de ruta
- Exportar/importar configuración.

## Compilar
Cada cambio en `main` se compila solo en GitHub Actions. Para publicar una versión: pestaña **Actions → Compilar APK → Run workflow**, escribe la versión (p. ej. `0.2.0`) y pulsa **Run**. Actions sube el APK firmado a Releases y Obtainium lo instala. (También vale crear una etiqueta `vX.Y.Z`.)

```bash
./gradlew assembleRelease   # requiere Android SDK
./gradlew testReleaseUnitTest
```

Licencia: GPL-3.0
