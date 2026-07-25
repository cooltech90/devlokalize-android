package com.devlokalize.sdk

/** Thrown by [DevLokalizeClient] when a request fails. */
sealed class DevLokalizeError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** `configure()` hasn't been called yet. */
    object NotConfigured : DevLokalizeError("configure(projectSlug, apiToken) hasn't been called yet.")

    /** The server returned a response that couldn't be parsed as a bundle. */
    object InvalidResponse : DevLokalizeError("The server returned a response DevLokalize couldn't parse.")

    /** The server responded with a non-2xx status. */
    class Server(val status: Int, val serverMessage: String) :
        DevLokalizeError("DevLokalize server error ($status): $serverMessage")

    /** The request failed before a response was received (offline, timeout, DNS, etc.). */
    class Network(cause: Throwable) :
        DevLokalizeError("DevLokalize network error: ${cause.message}", cause)
}
