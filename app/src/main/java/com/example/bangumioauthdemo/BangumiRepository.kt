package com.example.bangumioauthdemo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Real Bangumi public API gateway. All UI content is parsed from api.bgm.tv responses.
 * OAuth tokens are obtained from the shared, encrypted OAuthSessionManager.
 */
class BangumiRepository {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val userAgent = "BangumiGlass/1.0 (Android; github.com/Stellher/Bangumi-demo)"

    private suspend fun request(
        path: String,
        method: String = "GET",
        params: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        requiresAuth: Boolean = false,
        allowRetry: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.startsWith("//"))
        val url = ("https://api.bgm.tv" + path).toHttpUrl().newBuilder().apply {
            params.forEach { (key, value) ->
                if (value.isNotBlank()) addQueryParameter(key, value)
            }
        }.build()
        val token = OAuthSessionManager.ensureValidToken()
        if (requiresAuth && token.isNullOrBlank()) {
            error("此操作需要登录 Bangumi，请在「我的」页面授权。")
        }
        fun execute(accessToken: String?): Pair<Int, String> {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
            if (!accessToken.isNullOrBlank()) requestBuilder
                .header("Authorization", "Bearer " + accessToken)
            val requestBody = if (method == "GET" || method == "HEAD") {
                null
            } else {
                BangumiJsonRequestBodies.encode(body?.toString() ?: "")
            }
            if (requestBody != null) {
                // The API checks the exact Content-Type; do not append charset=utf-8.
                requestBuilder.header("Content-Type", BangumiJsonRequestBodies.CONTENT_TYPE)
            }
            requestBuilder.method(method, requestBody)
            return http.newCall(requestBuilder.build()).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
        }
        var (status, response) = execute(token)
        // A read-only request can be repeated safely after an expired-token response.
        if (status == 401 && token != null && allowRetry &&
            (method == "GET" || path.startsWith("/v0/search/"))) {
            val renewed = OAuthSessionManager.ensureValidToken(
                forceRefresh = true, failedToken = token
            )
            if (!renewed.isNullOrBlank() && renewed != token) {
                val second = execute(renewed)
                status = second.first
                response = second.second
            }
        }
        if (status !in 200..299) {
            val errorHint = try {
                JSONObject(response).s("description")
                    .ifBlank { JSONObject(response).s("title") }
                    .ifBlank { JSONObject(response).s("message") }
            } catch (_: Exception) { "" }
            error("Bangumi HTTP " + status + (if (errorHint.isBlank()) "" else "："
                + errorHint.take(180)))
        }
        response
    }

    private suspend fun objectResponse(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(request(path, params = params))

    suspend fun browse(type: Int = 2, sort: String = "rank", offset: Int = 0, limit: Int = 24): List<AnimeCard> =
        objectResponse("/v0/subjects", mapOf(
            "type" to type.toString(), "sort" to sort,
            "offset" to offset.toString(), "limit" to limit.toString()
        )).pageData().map(JSONObject::card).filter { it.id > 0 }

    /**
     * The /v0/subjects API accepts type/year/month but can return unrelated
     * entries when the catalogue is incomplete. Never trust request filters:
     * validate each result's type AND actual release date.
     *
     * This list is a release-quarter catalogue, not an editorially curated
     * TV-only new-anime schedule (movies/OVAs may still be included).
     */
    suspend fun season(year: Int, quarter: Int, animeCategory: Int = 1): List<AnimeCard> {
        val selected = quarter.coerceIn(1, 4)
        val startMonth = (selected - 1) * 3 + 1
        val collected = LinkedHashMap<Int, AnimeCard>()
        var success = 0
        var firstFailure: Exception? = null

        for (month in startMonth until startMonth + 3) {
            var offset = 0
            // Bounded pagination; do not silently present other quarters as new anime.
            var pageIndex = 0
            while (pageIndex < 4) {
                try {
                    val page = objectResponse("/v0/subjects", buildMap {
                        put("type", "2")
                        if (animeCategory != 0) put("cat", animeCategory.toString())
                        put("year", year.toString())
                        put("month", month.toString())
                        put("sort", "date")
                        put("limit", "60")
                        put("offset", offset.toString())
                    }).pageData()
                    success++
                    page.forEach { raw ->
                        // Check the raw API type before mapping, since card()
                        // supplies type=2 as a fallback when it is absent.
                        if (raw.optInt("type", -1) != 2) return@forEach
                        val item = raw.card()
                        if (item.id > 0 && SeasonMatcher.matches(
                                subjectType = raw.optInt("type", -1),
                                airDate = item.date,
                                platform = raw.optString("platform", ""),
                                year = year,
                                quarter = selected,
                                category = animeCategory
                            )
                        ) {
                            collected[item.id] = item
                        }
                    }
                    offset += page.size
                    pageIndex++
                    if (page.size < 60) break
                } catch (e: Exception) {
                    if (firstFailure == null) firstFailure = e
                    break
                }
            }
        }
        if (success == 0) throw (firstFailure ?: IllegalStateException("季度数据获取失败"))
        return collected.values.sortedWith(
            compareBy<AnimeCard> { it.date }.thenBy { it.title }
        )
    }

    suspend fun search(keyword: String, subjectType: Int? = null, offset: Int = 0): List<AnimeCard> {
        val clean = keyword.trim()
        require(clean.isNotEmpty()) { "请输入搜索关键词" }
        val body = JSONObject()
            .put("keyword", clean)
            .put("sort", "match")
        if (subjectType != null) {
            body.put("filter", JSONObject().put("type", JSONArray().put(subjectType)))
        }
        val res = JSONObject(request(
            path = "/v0/search/subjects",
            method = "POST",
            params = mapOf("limit" to "30", "offset" to offset.toString()),
            body = body
        ))
        return res.pageData().map(JSONObject::card).filter { it.id > 0 }
    }

    /** Full-text search over Bangumi character and person indexes. */
    suspend fun searchPeople(keyword: String, characters: Boolean): List<PersonCard> {
        val clean = keyword.trim()
        require(clean.isNotEmpty()) { "请输入角色或人物关键词" }
        val endpoint = if (characters) "/v0/search/characters" else "/v0/search/persons"
        val result = JSONObject(request(
            path = endpoint,
            method = "POST",
            params = mapOf("limit" to "30", "offset" to "0"),
            body = JSONObject().put("keyword", clean)
        ))
        return result.pageData().map { it.person(if (characters) "角色" else "人物") }
            .filter { it.id > 0 }
    }

    suspend fun calendar(): List<BroadcastDay> {
        val result = JSONArray(request("/calendar"))
        return result.jsonItems().map { day ->
            val weekday = day.obj("weekday")
            BroadcastDay(
                day = weekday.optInt("id"),
                title = weekday.s("cn").ifBlank { "星期" + weekday.optInt("id") },
                entries = day.arr("items").jsonItems().map(JSONObject::card).filter { it.id > 0 }
            )
        }.sortedBy { it.day }
    }

    suspend fun detail(id: Int): JSONObject = objectResponse("/v0/subjects/" + id)
    /** Read every episode page; a single /v0/episodes request is capped at 100 items. */
    suspend fun episodes(id: Int): List<EpisodeCard> {
        require(id > 0) { "条目 ID 无效" }
        val limit = 100
        val found = LinkedHashMap<Int, EpisodeCard>()
        var offset = 0
        while (offset < 10000) {
            val result = objectResponse("/v0/episodes", mapOf(
                "subject_id" to id.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString()
            ))
            val page = result.pageData()
            if (page.isEmpty()) break
            val before = found.size
            page.map(JSONObject::episode).filter { it.id > 0 }.forEach { found[it.id] = it }
            offset += page.size
            val total = result.optInt("total", -1)
            if (page.size < limit || (total >= 0 && offset >= total)) break
            // A server ignoring 'offset' must not trap the client in an endless loop.
            if (found.size == before) error("Bangumi 章节分页未前进")
        }
        if (offset >= 10000) error("章节数量超出客户端安全上限")
        return found.values.toList()
    }

    suspend fun characters(id: Int): List<PersonCard> =
        JSONArray(request("/v0/subjects/" + id + "/characters"))
            .jsonItems().map { it.person("角色") }.filter { it.id > 0 }

    suspend fun staff(id: Int): List<PersonCard> =
        JSONArray(request("/v0/subjects/" + id + "/persons"))
            .jsonItems().map { it.person("制作") }.filter { it.id > 0 }

    suspend fun related(id: Int): List<AnimeCard> =
        JSONArray(request("/v0/subjects/" + id + "/subjects"))
            .jsonItems().map { item ->
                item.optJSONObject("subject")?.card() ?: item.card()
            }.filter { it.id > 0 }

    suspend fun person(id: Int, isCharacter: Boolean): JSONObject =
        objectResponse("/v0/" + (if (isCharacter) "characters/" else "persons/") + id)

    suspend fun account(): AccountCard {
        return JSONObject(request("/v0/me", requiresAuth = true)).account()
    }

    suspend fun collections(
        username: String,
        type: Int? = null,
        offset: Int = 0,
        subjectType: Int? = null
    ): List<CollectionCard> {
        require(username.isNotBlank())
        return actualCollections(username, type, offset, subjectType)
    }

    private suspend fun actualCollections(
        username: String,
        type: Int?, offset: Int, subjectType: Int?
    ): List<CollectionCard> {
        val user = okhttp3.HttpUrl.Builder().scheme("https").host("api.bgm.tv")
            .addPathSegment(username).build().encodedPath.removePrefix("/")
        return objectResponse("/v0/users/" + user + "/collections", buildMap {
            if (type != null) put("type", type.toString())
            if (subjectType != null) put("subject_type", subjectType.toString())
            put("limit", "30")
            put("offset", offset.toString())
        }).pageData().map(JSONObject::collectionCard).filter { it.subject.id > 0 }
    }

    suspend fun userCollection(username: String, id: Int): JSONObject? {
        val user = okhttp3.HttpUrl.Builder().scheme("https").host("api.bgm.tv")
            .addPathSegment(username).build().encodedPath.removePrefix("/")
        return try {
            objectResponse("/v0/users/" + user + "/collections/" + id)
        } catch (e: Exception) {
            if (e.message?.contains("HTTP 404") == true) null else throw e
        }
    }

    suspend fun setCollection(subjectId: Int, edit: CollectionEdit) {
        request(
            path = "/v0/users/-/collections/" + subjectId,
            method = "POST",
            body = edit.payload(),
            requiresAuth = true,
            allowRetry = false
        )
    }

    suspend fun setEpisodeCompleted(episodeId: Int, completed: Boolean) {
        request(
            path = "/v0/users/-/collections/-/episodes/" + episodeId,
            method = "PUT",
            body = JSONObject().put("type", if (completed) 2 else 0),
            requiresAuth = true,
            allowRetry = false
        )
    }
}
