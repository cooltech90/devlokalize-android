package com.devlokalize.sdk

import java.io.File

/**
 * A minimal, read-only client for the DevLokalize translation API.
 *
 * ```kotlin
 * val client = DevLokalizeClient(cacheDir = context.cacheDir)
 * client.configure(projectSlug = "my-app", apiToken = "dl_xxx")
 * client.preload(language = "fr") // instant, cache-only
 * client.refreshAsync(language = "fr") { result -> /* ... */ }
 *
 * val title = client.string("hero.title", component = "homepage", default = "Welcome")
 * ```
 *
 * DevLokalize never writes back to your project — this SDK is read-only by
 * design. Use the REST API directly for anything that submits translations.
 *
 * Most apps on Android should use the [DevLokalize] singleton wrapper
 * instead of constructing this directly — it handles the `Context` ->
 * `cacheDir` plumbing for you.
 */
class DevLokalizeClient internal constructor(
    private val cacheDir: File,
    private val baseUrl: String,
    private val httpClient: HttpClient,
) {
    constructor(
        cacheDir: File,
        baseUrl: String = "https://devlokalize.com",
    ) : this(cacheDir, baseUrl, DefaultHttpClient())

    private var projectSlug: String? = null
    private var apiToken: String? = null

    @Volatile
    private var components: Map<String, Map<String, String>> = emptyMap()

    /** Call once, typically at app launch. */
    fun configure(projectSlug: String, apiToken: String) {
        this.projectSlug = projectSlug
        this.apiToken = apiToken
    }

    /**
     * Loads whatever was cached on disk from a previous [refresh] call,
     * synchronously and without a network request. Call this before your
     * first render to avoid a flash of missing strings while [refresh] (or
     * [refreshAsync]) is still in flight.
     */
    fun preload(language: String) {
        val slug = projectSlug ?: return
        val cached = TranslationCache(cacheDir, slug, language).load() ?: return
        components = cached
    }

    /**
     * Fetches the latest approved translations for [language] and updates
     * both the in-memory and on-disk cache. **Performs a blocking network
     * call — call this from a background thread**, or use [refreshAsync].
     *
     * Throws [DevLokalizeError] on failure; callers should treat that as
     * "keep showing whatever's cached" rather than a fatal condition.
     */
    @Throws(DevLokalizeError::class)
    fun refresh(language: String): Map<String, Map<String, String>> {
        val slug = projectSlug ?: throw DevLokalizeError.NotConfigured
        val token = apiToken ?: throw DevLokalizeError.NotConfigured

        val url = "$baseUrl/api/v1/projects/$slug/bundle/$language"
        val response = try {
            httpClient.get(url, mapOf("Authorization" to "Bearer $token"))
        } catch (e: Exception) {
            throw DevLokalizeError.Network(e)
        }

        if (response.status !in 200..299) {
            val message = try {
                MiniJson.parseObject(response.body)["error"] as? String
            } catch (e: Exception) {
                null
            } ?: "HTTP ${response.status}"
            throw DevLokalizeError.Server(response.status, message)
        }

        val parsed = try {
            MiniJson.parseObject(response.body)
        } catch (e: Exception) {
            throw DevLokalizeError.InvalidResponse
        }

        @Suppress("UNCHECKED_CAST")
        val rawComponents = parsed["components"] as? Map<String, Any?>
            ?: throw DevLokalizeError.InvalidResponse

        val result = LinkedHashMap<String, Map<String, String>>()
        for ((component, value) in rawComponents) {
            @Suppress("UNCHECKED_CAST")
            val inner = value as? Map<String, Any?> ?: continue
            val strings = LinkedHashMap<String, String>()
            for ((k, v) in inner) if (v is String) strings[k] = v
            result[component] = strings
        }

        components = result
        TranslationCache(cacheDir, slug, language).save(result)
        return result
    }

    /**
     * Runs [refresh] on a background thread and delivers the result via
     * [callback] on that same background thread — hop back to the main
     * thread yourself if you're touching views from [callback].
     */
    fun refreshAsync(language: String, callback: (Result<Map<String, Map<String, String>>>) -> Unit) {
        Thread {
            callback(
                try {
                    Result.success(refresh(language))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            )
        }.start()
    }

    /**
     * Looks up a translated string. Returns [default] (or `null`) if the key
     * has no approved translation cached — never throws, safe to call from
     * view code on every render.
     */
    fun string(key: String, component: String, default: String? = null): String? {
        return components[component]?.get(key) ?: default
    }

    /** Clears the in-memory cache. Mostly useful for tests. */
    fun reset() {
        components = emptyMap()
    }
}
