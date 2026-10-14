package com.example.bangumioauthdemo

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiJsonRequestBodiesTest {

    @Test
    fun exactJsonMediaTypeWithoutCharset() {
        val payload = """{"type":3,"rate":8,"comment":"航海王 / ONE PIECE"}"""
        val body = BangumiJsonRequestBodies.encode(payload)
        assertEquals("application/json", body.contentType().toString())
        val buffer = Buffer()
        body.writeTo(buffer)
        assertEquals(payload, buffer.readUtf8())
    }

    @Test
    fun wireLevelPostUsesExactContentType() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(204))
            val json = """{"type":3,"rate":8,"comment":"航海王⭐","tags":["热血"]}"""
            val request = Request.Builder()
                .url(server.url("/v0/users/-/collections/975"))
                .header("Accept", "application/json")
                .header("Content-Type", BangumiJsonRequestBodies.CONTENT_TYPE)
                .post(BangumiJsonRequestBodies.encode(json))
                .build()
            OkHttpClient().newCall(request).execute().use { response ->
                assertEquals(204, response.code)
            }
            val received = server.takeRequest()
            assertEquals("POST", received.method)
            assertEquals("/v0/users/-/collections/975", received.path)
            assertEquals("application/json", received.getHeader("Content-Type"))
            assertEquals(json, received.body.readUtf8())
            assertTrue(received.getHeader("Content-Length")!!.toInt() > 0)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun wireLevelEpisodePutUsesExactContentType() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(204))
            val request = Request.Builder()
                .url(server.url("/v0/users/-/collections/-/episodes/1"))
                .put(BangumiJsonRequestBodies.encode("""{"type":2}"""))
                .build()
            OkHttpClient().newCall(request).execute().close()
            val received = server.takeRequest()
            assertEquals("PUT", received.method)
            assertEquals("application/json", received.getHeader("Content-Type"))
            assertEquals("""{"type":2}""", received.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }
}
