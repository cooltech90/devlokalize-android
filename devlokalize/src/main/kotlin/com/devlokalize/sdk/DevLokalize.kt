package com.devlokalize.sdk

import android.content.Context

/**
 * App-wide singleton wrapper around [DevLokalizeClient]. Call [init] once,
 * typically in [android.app.Application.onCreate], then use [shared]
 * everywhere else.
 *
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         DevLokalize.init(this)
 *         DevLokalize.shared.configure(projectSlug = "my-app", apiToken = "dl_xxx")
 *         DevLokalize.shared.preload(language = "fr")
 *         DevLokalize.shared.refreshAsync(language = "fr") { /* ... */ }
 *     }
 * }
 * ```
 */
object DevLokalize {
    @Volatile
    private var instance: DevLokalizeClient? = null

    val shared: DevLokalizeClient
        get() = instance
            ?: error("DevLokalize.init(context) must be called before accessing DevLokalize.shared.")

    fun init(context: Context, baseUrl: String = "https://devlokalize.com") {
        instance = DevLokalizeClient(cacheDir = context.applicationContext.cacheDir, baseUrl = baseUrl)
    }
}
