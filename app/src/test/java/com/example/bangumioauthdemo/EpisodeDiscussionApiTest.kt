package com.example.bangumioauthdemo

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeDiscussionApiTest {
    @Test
    fun parsesTopLevelCommentsAndReplies() {
        val comments = parseEpisodeComments(
            """
            [
              {
                "id": 40, "createdAt": 1761800000, "content": "这一集很精彩",
                "user": {
                  "username": "user01", "nickname": "观众",
                  "avatar": {"medium": "https://lain.bgm.tv/avatar.png"}
                },
                "replies": [
                  {
                    "id": 41, "createdAt": 1761800100, "content": "同意",
                    "user": {"username": "user02", "nickname": "另一位观众"}
                  }
                ]
              }
            ]
            """.trimIndent()
        )
        assertEquals(1, comments.size)
        assertEquals(40L, comments.single().id)
        assertEquals("观众", comments.single().author)
        assertEquals("这一集很精彩", comments.single().content)
        assertEquals("https://lain.bgm.tv/avatar.png", comments.single().avatar)
        assertEquals(1, comments.single().replies.size)
        assertEquals("另一位观众", comments.single().replies.single().author)
    }

    @Test
    fun ignoresEntriesWithoutPositiveIds() {
        assertTrue(parseEpisodeComments("""[{"id":0},{"content":"broken"}]""").isEmpty())
    }

    @Test
    fun readsPublicCommentsWithoutForwardingOAuthCredentials() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":5,"content":"hello","createdAt":1761800000,"user":{"nickname":"Alice"},"replies":[]}]"""))
            val client = EpisodeDiscussionApi(baseUrl = server.url("/"))
            val comments = client.comments(12345)
            assertEquals(1, comments.size)
            assertEquals("Alice", comments.single().author)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/p1/episodes/12345/comments", request.path)
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun preservesHttpErrorsInsteadOfShowingAnEmptyDiscussion() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("Unavailable"))
            val client = EpisodeDiscussionApi(baseUrl = server.url("/"))
            val error = runCatching { client.comments(9) }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("503"))
        } finally {
            server.shutdown()
        }
    }
}
