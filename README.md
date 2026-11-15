# Escudo 🛡️

App Android de código abierto que protege tu privacidad y te devuelve el control sobre tu atención.
Funciona **100 % en tu teléfono**: sin cuentas, sin servidores propios, sin publicidad y sin depender de nadie para funcionar ni actualizarse.

## Qué hace

### 1. Cortafuegos anti-rastreo (VPN local)
- Bloquea a nivel DNS los dominios de rastreo, publicidad, telemetría y malware de **todas las apps**, también las del fabricante.
- Usa listas públicas de primer nivel (HaGeZi Pro, StevenBlack, telemetría de Samsung/Xiaomi/TikTok…) guardadas en el móvil y actualizadas solas cada 3 días.
- Solo el tráfico DNS pasa por la app; el resto va directo, así que **no pierdes velocidad ni batería**. Además, la caché DNS hace que las apps abran más rápido.
- **Forzar DNS**: captura las apps que intentan saltarse el filtro usando su propio DNS (8.8.8.8, 1.1.1.1…).
- **Apps sin internet** (Android 10+): corta la conexión a las apps que elijas.
- **Registro en vivo**: ves qué app intenta contactar con qué rastreador, y lo bloqueas o permites con un toque.
- Servidor DNS a elegir: Quad9 (por defecto), Cloudflare, AdGuard o Mullvad.

### 2. Escudo anti-algoritmo
- Te saca automáticamente de los **feeds infinitos**: Reels de Instagram y Facebook, Shorts de YouTube, Spotlight de Snapchat y, si quieres, TikTok entero.
- **Pausa consciente**: una cuenta atrás antes de abrir las apps que elijas, para que abrirlas sea una decisión y no un impulso.
- **Límites diarios** por app: al llegar al límite, la app se cierra hasta mañana.
- **Panel de uso** diario y semanal.
- Las reglas son texto editable: si una red social cambia su diseño, se arregla desde la propia app.

### 3. Auditor de privacidad
- Detecta más de 50 SDKs de rastreo y publicidad dentro de tus apps (AppsFlyer, Meta, AdMob, Adjust…) y los permisos sensibles que tienen concedidos.
- Un toque te lleva a los ajustes de cada app para quitárselos.

### 4. Ajustes del sistema recomendados
Accesos directos para: VPN siempre activa, borrar el ID de publicidad, DNS privado, animaciones a 0,5x (más fluidez) y quitar la optimización de batería a Escudo.

## Instalación
Lee la [guía de instalación](docs/INSTALAR.md). En resumen: descarga el APK de [Releases](../../releases) o, mejor, instálalo con **Obtainium** para recibir las actualizaciones automáticamente.

## Límites honestos
Android no permite a una app normal (sin root) modificar otras apps ni el sistema. Por eso Escudo **no** es un "limpiador de RAM" ni un "booster": esas apps no funcionan. Lo que sí hace es cortar el rastreo en la red, que es donde más batería, datos y privacidad se pierden, y cambiar el entorno para que el algoritmo no decida por ti.
- Las apps que usan DNS cifrado propio (Chrome con "DNS seguro", Firefox) pueden saltarse el filtro: activa la lista *Anti-DoH/VPN/proxy* o desactiva el DNS seguro en el navegador.
- Solo puede haber una VPN activa a la vez en Android.
- La detección de Reels/Shorts depende del diseño interno de cada app; si cambia, se ajusta en *Escudo > Reglas de feeds*.

## Hoja de ruta
- Módulo Shizuku (sin root): desactivar bloatware, congelar apps en segundo plano, escala de grises programada, revocar permisos ocultos.
- Exportar/importar configuración.

## Compilar
Cada cambio en `main` se compila solo en GitHub Actions. Para publicar una versión: pestaña **Actions → Compilar APK → Run workflow**, escribe la versión (p. ej. `0.2.0`) y pulsa **Run**. Actions sube el APK firmado a Releases y Obtainium lo instala. (También vale crear una etiqueta `vX.Y.Z`.)

```bash
./gradlew assembleRelease   # requiere Android SDK
./gradlew testReleaseUnitTest
```

Licencia: GPL-3.0
