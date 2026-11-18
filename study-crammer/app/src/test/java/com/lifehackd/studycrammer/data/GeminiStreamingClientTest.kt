package com.lifehackd.studycrammer.data

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Drives the real SSE client against a local server by rewriting the Gemini host. */
class GeminiStreamingClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GeminiStreamingClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val r = chain.request()
            chain.proceed(
                r.newBuilder()
                    .url(r.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build())
                    .header(GeminiStreamingClient.API_KEY_HEADER, "test-key")
                    .build(),
            )
        }.build()
        client = GeminiStreamingClient(http)
    }

    @After fun tearDown() = server.shutdown()

    private fun sse(vararg texts: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(texts.joinToString("") { "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"$it\"}]}}]}\r\n\r\n" })

    @Test fun streamsDeltasInOrderAndKeyNeverInUrl() = runBlocking {
        server.enqueue(sse("## Cheat", " Sheet\\n", "- one"))
        val parts = client.stream(GenerationRequest("sys", "prompt")).toList()
        assertEquals(listOf("## Cheat", " Sheet\n", "- one"), parts)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.startsWith("/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse"))
        assertFalse(recorded.path!!.contains("key="))
        assertEquals("test-key", recorded.getHeader(GeminiStreamingClient.API_KEY_HEADER))
        assertTrue(recorded.body.readUtf8().contains("\"systemInstruction\""))
    }

    @Test fun rateLimitBecomesFriendlyTypedError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}"""))
        try {
            client.collect(GenerationRequest("sys", "prompt"))
            fail("expected GeminiException")
        } catch (e: GeminiException) {
            assertEquals(GeminiException.Kind.RATE_LIMIT, e.kind)
        }
    }

    @Test fun verifyKeyHitsModelsListAndMapsInvalidKey() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"models":[]}"""))
        client.verifyKey("candidate")
        assertTrue(server.takeRequest().path!!.startsWith("/v1beta/models?pageSize=1"))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"API key not valid."}}"""))
        try {
            client.verifyKey("bad")
            fail("expected GeminiException")
        } catch (e: GeminiException) {
            assertEquals(GeminiException.Kind.INVALID_KEY, e.kind)
        }
    }
}
