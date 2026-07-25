package com.devlokalize.sdk

import java.net.HttpURLConnection
import java.net.URL

internal data class HttpResponse(val status: Int, val body: String)

internal interface HttpClient {
    fun get(url: String, headers: Map<String, String>): HttpResponse
}

/** Plain `HttpURLConnection`-based client — no external HTTP dependency required. */
internal class DefaultHttpClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000,
) : HttpClient {
    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            HttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}
