# Servicios declarados en el manifiesto: R8 ya los conserva. Nada especial que añadir.
-dontwarn org.jetbrains.annotations.**

# Shizuku: Escudo llama por reflexión a Shizuku.newProcess
-keep class rikka.shizuku.Shizuku { *; }
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }
