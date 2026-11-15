import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// La versión sale de la etiqueta de Git (v1.2.3) cuando compila GitHub Actions
val versionTag: String = (System.getenv("VERSION_NAME") ?: "0.1.0").removePrefix("v")
val versionParts = versionTag.split(".").mapNotNull { it.toIntOrNull() } + listOf(0, 0, 0)
val computedCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]

// Firma: claves desde variables de entorno (secretos de GitHub) o keystore.properties local
val keyProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? = System.getenv(env) ?: keyProps.getProperty(prop)
val keystorePath = signingValue("ESCUDO_KEYSTORE_PATH", "storeFile")

android {
    namespace = "app.escudo"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.escudo"
        minSdk = 26
        targetSdk = 35
        versionCode = computedCode.coerceAtLeast(1)
        versionName = versionTag
    }

    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = signingValue("ESCUDO_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("ESCUDO_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("ESCUDO_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { abortOnError = false }
    dependenciesInfo {
        // Sin bloque de metadatos cifrado de Google: APK reproducible y apto para F-Droid
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
