package app.escudo.core

/**
 * Firmas de SDKs de rastreo y publicidad, basadas en los nombres de paquete
 * Java que usan sus componentes (servicios, receptores, actividades, providers).
 * Inspirado en la base de datos pública de Exodus Privacy. Todo funciona en local.
 */
data class Tracker(val name: String, val category: String, val prefixes: List<String>)

object TrackerSignatures {
    const val ADS = "Publicidad"
    const val ANALYTICS = "Analítica"
    const val PROFILING = "Perfilado"
    const val CRASH = "Informes de errores"
    const val LOCATION = "Localización"
    const val PUSH = "Notificaciones push"

    val ALL: List<Tracker> = listOf(
        Tracker("Google AdMob", ADS, listOf("com.google.android.gms.ads.", "com.google.ads.")),
        Tracker("Google Firebase Analytics", ANALYTICS, listOf("com.google.firebase.analytics.", "com.google.android.gms.measurement.")),
        Tracker("Google Analytics", ANALYTICS, listOf("com.google.android.gms.analytics.")),
        Tracker("Google Tag Manager", ANALYTICS, listOf("com.google.android.gms.tagmanager.", "com.google.tagmanager.")),
        Tracker("Firebase Crashlytics", CRASH, listOf("com.google.firebase.crashlytics.", "com.crashlytics.")),
        Tracker("Facebook SDK / Meta Analytics", PROFILING, listOf("com.facebook.appevents.", "com.facebook.FacebookContentProvider", "com.facebook.internal.", "com.facebook.CustomTabActivity", "com.facebook.FacebookActivity")),
        Tracker("Facebook Audience Network", ADS, listOf("com.facebook.ads.")),
        Tracker("AppsFlyer", PROFILING, listOf("com.appsflyer.")),
        Tracker("Adjust", PROFILING, listOf("com.adjust.sdk.")),
        Tracker("Branch", PROFILING, listOf("io.branch.")),
        Tracker("Kochava", PROFILING, listOf("com.kochava.")),
        Tracker("Singular", PROFILING, listOf("com.singular.sdk.")),
        Tracker("AppLovin", ADS, listOf("com.applovin.")),
        Tracker("Unity Ads", ADS, listOf("com.unity3d.ads.", "com.unity3d.services.")),
        Tracker("ironSource", ADS, listOf("com.ironsource.")),
        Tracker("Vungle / Liftoff", ADS, listOf("com.vungle.")),
        Tracker("Chartboost", ADS, listOf("com.chartboost.")),
        Tracker("InMobi", ADS, listOf("com.inmobi.")),
        Tracker("Mintegral", ADS, listOf("com.mbridge.", "com.mintegral.")),
        Tracker("Pangle (TikTok/ByteDance)", ADS, listOf("com.bytedance.sdk.openadsdk.", "com.pangle.")),
        Tracker("Digital Turbine / Fyber", ADS, listOf("com.fyber.", "com.inner_active.")),
        Tracker("Moloco", ADS, listOf("com.moloco.")),
        Tracker("Amazon Ads", ADS, listOf("com.amazon.device.ads.")),
        Tracker("Huawei Ads", ADS, listOf("com.huawei.hms.ads.")),
        Tracker("Mixpanel", ANALYTICS, listOf("com.mixpanel.")),
        Tracker("Amplitude", ANALYTICS, listOf("com.amplitude.")),
        Tracker("Segment", ANALYTICS, listOf("com.segment.analytics.")),
        Tracker("Flurry", ANALYTICS, listOf("com.flurry.")),
        Tracker("Microsoft App Center", ANALYTICS, listOf("com.microsoft.appcenter.")),
        Tracker("New Relic", ANALYTICS, listOf("com.newrelic.")),
        Tracker("Sentry", CRASH, listOf("io.sentry.")),
        Tracker("Bugsnag", CRASH, listOf("com.bugsnag.")),
        Tracker("Instabug", CRASH, listOf("com.instabug.")),
        Tracker("Braze", PROFILING, listOf("com.braze.", "com.appboy.")),
        Tracker("CleverTap", PROFILING, listOf("com.clevertap.")),
        Tracker("MoEngage", PROFILING, listOf("com.moengage.")),
        Tracker("Leanplum", PROFILING, listOf("com.leanplum.")),
        Tracker("OneSignal", PUSH, listOf("com.onesignal.")),
        Tracker("Batch", PUSH, listOf("com.batch.android.")),
        Tracker("Airship", PUSH, listOf("com.urbanairship.")),
        Tracker("Foursquare Pilgrim", LOCATION, listOf("com.foursquare.pilgrim.")),
        Tracker("Radar", LOCATION, listOf("io.radar.")),
        Tracker("Huq / Cuebiq / X-Mode", LOCATION, listOf("io.huq.", "com.cuebiq.", "io.mysdk.")),
        Tracker("Yandex AppMetrica", ANALYTICS, listOf("com.yandex.metrica.", "io.appmetrica.")),
        Tracker("Tencent / Bugly", ANALYTICS, listOf("com.tencent.bugly.", "com.tencent.stat.")),
        Tracker("Umeng", ANALYTICS, listOf("com.umeng.")),
        Tracker("Smaato", ADS, listOf("com.smaato.")),
        Tracker("Ogury", ADS, listOf("io.presage.", "co.ogury.")),
        Tracker("Criteo", ADS, listOf("com.criteo.")),
        Tracker("Taboola / Outbrain", ADS, listOf("com.taboola.", "com.outbrain.")),
    )

    /** Rastreadores detectados a partir de los nombres de componentes de una app. */
    fun detect(componentNames: Collection<String>): List<Tracker> =
        ALL.filter { t -> componentNames.any { c -> t.prefixes.any { p -> c.startsWith(p) } } }
}
