package com.devlokalize.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

private class FakeHttpClient(private val handler: (String, Map<String, String>) -> HttpResponse) : HttpClient {
    override fun get(url: String, headers: Map<String, String>): HttpResponse = handler(url, headers)
}

class DevLokalizeClientTest {

    private fun tempDir(): File = Files.createTempDirectory("devlokalize-test").toFile()

    @Test
    fun `string returns default when not configured`() {
        val client = DevLokalizeClient(tempDir(), "https://example.com", FakeHttpClient { _, _ -> HttpResponse(200, "{}") })
        assertNull(client.string("missing", component = "homepage"))
        assertEquals("fallback", client.string("missing", component = "homepage", default = "fallback"))
    }

    @Test
    fun `refresh decodes bundle and populates lookup`() {
        val json = """{"project":"demo","language":"fr","components":{"homepage":{"hero.title":"Bonjour"}}}"""
        val client = DevLokalizeClient(tempDir(), "https://example.com", FakeHttpClient { _, _ -> HttpResponse(200, json) })
        client.configure("demo", "dl_test")

        val result = client.refresh("fr")

        assertEquals("Bonjour", result["homepage"]?.get("hero.title"))
        assertEquals("Bonjour", client.string("hero.title", component = "homepage"))
        assertNull(client.string("nonexistent", component = "homepage"))
    }

    @Test
    fun `refresh throws on server error`() {
        val json = """{"error":"API access is a Pro feature for this project's owner."}"""
        val client = DevLokalizeClient(tempDir(), "https://example.com", FakeHttpClient { _, _ -> HttpResponse(403, json) })
        client.configure("demo", "dl_test")

        try {
            client.refresh("fr")
            fail("Expected refresh to throw")
        } catch (e: DevLokalizeError.Server) {
            assertEquals(403, e.status)
            assertTrue(e.serverMessage.contains("Pro"))
        }
    }

    @Test
    fun `refresh persists cache and preload reads it back on a fresh instance`() {
        val dir = tempDir()
        val json = """{"project":"demo","language":"fr","components":{"homepage":{"hero.title":"Bonjour"}}}"""

        val client1 = DevLokalizeClient(dir, "https://example.com", FakeHttpClient { _, _ -> HttpResponse(200, json) })
        client1.configure("demo", "dl_test")
        client1.refresh("fr")

        // Fresh instance, same cache dir — simulates a new app launch before
        // the network request completes.
        val client2 = DevLokalizeClient(dir, "https://example.com", FakeHttpClient { _, _ -> HttpResponse(500, "") })
        client2.configure("demo", "dl_test")
        client2.preload("fr")

        assertEquals("Bonjour", client2.string("hero.title", component = "homepage"))
    }

    @Test
    fun `refresh throws NotConfigured when configure was never called`() {
        val client = DevLokalizeClient(tempDir(), "https://example.com", FakeHttpClient { _, _ -> HttpResponse(200, "{}") })
        try {
            client.refresh("fr")
            fail("Expected refresh to throw")
        } catch (e: DevLokalizeError.NotConfigured) {
            // expected
        }
    }
}
