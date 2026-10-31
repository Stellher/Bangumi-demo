package com.example.bangumioauthdemo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A chapter comment and its direct replies from Bangumi's next-site discussion API. */
data class EpisodeComment(
    val id: Long,
    val author: String,
    val avatar: String,
    val content: String,
    val createdAt: Long,
    val replies: List<EpisodeComment> = emptyList()
)

/**
 * The next-site discussion GET route is public. OAuth authentication is NOT required
 * to read public comments; no app token, account cookie or password is forwarded here.
 *
 * Posting is intentionally not performed by this client: the private write route
 * additionally requires a real Cloudflare Turnstile challenge token. The app links
 * to the official episode page for interactive posting instead.
 */
internal class EpisodeDiscussionApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: HttpUrl = "https://next.bgm.tv/".toHttpUrl()
) {
    suspend fun comments(episodeId: Int): List<EpisodeComment> = withContext(Dispatchers.IO) {
        require(episodeId > 0) { "章节 ID 无效" }
        val url = baseUrl.newBuilder()
            .addPathSegments("p1/episodes/" + episodeId + "/comments")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "BangumiGlass/1.0 (Android; github.com/Stellher/Bangumi-demo)")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("读取单集吐槽失败：HTTP " + response.code)
            }
            parseEpisodeComments(body)
        }
    }
}

internal fun parseEpisodeComments(raw: String): List<EpisodeComment> =
    parseEpisodeComments(JSONArray(raw))

internal fun parseEpisodeComments(items: JSONArray): List<EpisodeComment> =
    items.jsonItems().mapNotNull { item ->
        val id = item.optLong("id")
        if (id <= 0) return@mapNotNull null
        val user = item.obj("user")
        val avatar = user.obj("avatar")
        EpisodeComment(
            id = id,
            author = user.s("nickname").ifBlank { user.s("username") }.ifBlank { "Bangumi 用户" },
            avatar = avatar.s("medium").ifBlank { avatar.s("small") },
            content = item.s("content"),
            createdAt = item.optLong("createdAt"),
            replies = item.optJSONArray("replies")?.let(::parseEpisodeComments) ?: emptyList()
        )
    }
