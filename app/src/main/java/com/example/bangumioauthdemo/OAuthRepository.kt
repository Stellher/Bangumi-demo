package com.example.bangumioauthdemo

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OAuthHttpException(
    val statusCode: Int,
    safeReason: String
) : RuntimeException("Bangumi OAuth HTTP $statusCode: $safeReason")

object OAuthRepository {
    const val REDIRECT = "bgmoauthdemo://callback"
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun authorizeUrl(state: String): String = Uri.parse("https://bgm.tv/oauth/authorize")
        .buildUpon()
        .appendQueryParameter("client_id", BuildConfig.BGM_CLIENT_ID)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("redirect_uri", REDIRECT)
        .appendQueryParameter("state", state)
        .build().toString()

    private suspend fun post(fields: Map<String, String>, path: String): JSONObject =
        withContext(Dispatchers.IO) {
            val form = FormBody.Builder().apply {
                fields.forEach { (key, value) -> add(key, value) }
            }.build()
            val request = Request.Builder()
                .url("https://bgm.tv/oauth/" + path)
                .post(form)
                .header("User-Agent", "BangumiOAuthDemo/3.0 (Android; local-demo)")
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Do not echo raw server response: it might contain credentials.
                    val reason = try {
                        val json = JSONObject(body)
                        json.optString("error_description")
                            .ifBlank { json.optString("error") }.take(160)
                    } catch (_: Exception) { "请求被服务器拒绝" }
                    throw OAuthHttpException(response.code, reason)
                }
                JSONObject(body)
            }
        }

    /**
     * Unauthenticated, read-only HTTP probe of the first OAuth redirect.
     * This is NOT browser traffic sniffing and cannot capture a signed-in
     * session: browser cookies/passwords deliberately remain isolated.
     *
     * Reports only HTTP status, content type and the next redirect host/path.
     * Never returns Set-Cookie, a query string, a password or an OAuth code.
     */
    suspend fun probeAuthorizationEntry(): JSONObject = withContext(Dispatchers.IO) {
        val probe = http.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .build()
        val target = authorizeUrl("diagnostic-" + java.util.UUID.randomUUID())
        val request = Request.Builder()
            .url(target)
            .get()
            .header("User-Agent", "BangumiGlass/2.2 (Android; OAuth diagnostic)")
            .build()
        probe.newCall(request).execute().use { response ->
            val next = response.header("Location")?.let { request.url.resolve(it) }
            JSONObject()
                .put("endpoint", "GET https://bgm.tv/oauth/authorize")
                .put("http_status", response.code)
                .put("content_type", response.header("Content-Type").orEmpty().substringBefore(";"))
                .put("redirect_host", next?.host.orEmpty())
                .put("redirect_path", next?.encodedPath.orEmpty())
                .put("cookies_sent", false)
                .put("redirects_followed", false)
                .put("note", "仅查看未登录时的公开 HTTP 跳转，完整账号登录及授权需通过系统浏览器。")
        }
    }

    suspend fun exchange(code: String, secret: String): JSONObject = post(mapOf(
        "grant_type" to "authorization_code",
        "client_id" to BuildConfig.BGM_CLIENT_ID,
        "client_secret" to secret,
        "code" to code,
        "redirect_uri" to REDIRECT
    ), "access_token")

    suspend fun refresh(refreshToken: String, secret: String): JSONObject = post(mapOf(
        "grant_type" to "refresh_token",
        "client_id" to BuildConfig.BGM_CLIENT_ID,
        "client_secret" to secret,
        "refresh_token" to refreshToken,
        "redirect_uri" to REDIRECT
    ), "access_token")

    suspend fun status(token: String): JSONObject =
        post(mapOf("access_token" to token), "token_status")

    suspend fun me(token: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.bgm.tv/v0/me")
            .header("Authorization", "Bearer " + token)
            .header("User-Agent", "BangumiOAuthDemo/3.0 (Android; local-demo)")
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Bangumi API HTTP ${response.code}")
            }
            JSONObject(body)
        }
    }
}
