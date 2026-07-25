package com.devlokalize.sdk

import java.io.File

/**
 * Persists the last successfully fetched bundle to disk so translations are
 * available immediately on the next launch, before the network call
 * completes (or if it fails, e.g. offline).
 */
internal class TranslationCache(cacheDir: File, projectSlug: String, language: String) {
    private val file: File = run {
        val dir = File(cacheDir, "devlokalize")
        dir.mkdirs()
        File(dir, "$projectSlug-$language.json")
    }

    fun load(): Map<String, Map<String, String>>? {
        if (!file.exists()) return null
        return try {
            MiniJson.parseComponentsCache(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun save(components: Map<String, Map<String, String>>) {
        try {
            file.writeText(MiniJson.stringify(components))
        } catch (e: Exception) {
            // Best-effort: a failed cache write shouldn't break the caller.
        }
    }
}
