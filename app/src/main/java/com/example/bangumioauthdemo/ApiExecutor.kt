package com.example.bangumioauthdemo

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ApiExecutor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false) // Show image redirects exactly as returned by Bangumi.
        .build()

    suspend fun execute(
        endpoint: ApiEndpoint,
        values: Map<String, String>,
        bodyInput: String,
        accessToken: String?
    ): ApiResult = withContext(Dispatchers.IO) {
        if (endpoint.auth == "required" && accessToken.isNullOrBlank()) {
            error("该 API 要求 OAuth Bearer Token，请先登录授权。")
        }
        var path = endpoint.path
        endpoint.params.filter { it.location == "path" }.forEach { p ->
            val value = values[p.name]?.trim().orEmpty()
            if (value.isBlank()) error("缺少路径参数：${p.name}")
            if (value.contains("/") || value.contains("?") || value.contains("#")) {
                error("路径参数 ${p.name} 必须是单个路径片段")
            }
            path = path.replace("{${p.name}}", Uri.encode(value))
        }
        if (!(path.startsWith("/v0/") || path == "/calendar")) {
            error("请求路径不在 Bangumi 官方公共 API 范围内")
        }
        val urlBuilder = ("https://api.bgm.tv" + path).toHttpUrl().newBuilder()
        endpoint.params.filter { it.location == "query" }.forEach { p ->
            val value = values[p.name]?.trim().orEmpty()
            if (value.isNotEmpty()) {
                // Form-style OpenAPI query arrays default to repeated keys.
                val parts = if (p.type == "array") value.split(",") else listOf(value)
                parts.filter { it.isNotBlank() }.forEach { part ->
                    urlBuilder.addQueryParameter(p.name, part.trim())
                }
            } else if (p.required) {
                error("缺少查询参数：${p.name}")
            }
        }
        val finalUrl = urlBuilder.build()
        val req = Request.Builder()
            .url(finalUrl)
            .header("User-Agent", "BangumiOAuthDemo/2.0 (Android; github.com/Stellher/demo-test)")
            .header("Accept", "application/json, image/*;q=0.9, */*;q=0.5")
        if (!accessToken.isNullOrBlank()) req.header("Authorization", "Bearer " + accessToken)
        endpoint.params.filter { it.location == "header" }.forEach { p ->
            val value = values[p.name]?.trim().orEmpty()
            if (value.isNotBlank() && p.name.lowercase() !in setOf("authorization", "host", "user-agent")) {
                req.header(p.name, value)
            } else if (p.required && value.isBlank()) error("缺少请求头：${p.name}")
        }
        val method = endpoint.method
        val hasBody = endpoint.requestBody != null
        val requestBody = if (method in setOf("GET", "HEAD")) {
            null
        } else if (hasBody && bodyInput.isNotBlank()) {
            val mediaType = endpoint.requestBody!!.mediaType
            if (mediaType == "application/json") {
                try {
                    if (bodyInput.trim().startsWith("[")) JSONArray(bodyInput) else JSONObject(bodyInput)
                } catch (e: Exception) {
                    error("JSON 请求体格式不正确：" + e.message)
                }
            }
            val requestedMedia = mediaType.ifBlank { BangumiJsonRequestBodies.CONTENT_TYPE }
            if (requestedMedia == BangumiJsonRequestBodies.CONTENT_TYPE) {
                req.header("Content-Type", BangumiJsonRequestBodies.CONTENT_TYPE)
                BangumiJsonRequestBodies.encode(bodyInput)
            } else {
                bodyInput.toByteArray(Charsets.UTF_8).toRequestBody(requestedMedia.toMediaType())
            }
        } else if (hasBody && endpoint.requestBody!!.required) {
            error("该 API 的请求体是必填项")
        } else {
            ByteArray(0).toRequestBody(null)
        }
        req.method(method, requestBody)
        val request = req.build()
        val t0 = System.nanoTime()
        client.newCall(request).execute().use { response ->
            val millis = (System.nanoTime() - t0) / 1_000_000
            val contentType = response.header("Content-Type").orEmpty()
            val interestingHeaders = response.headers.names()
                .filter { it.lowercase() in setOf("location", "retry-after", "etag", "content-type", "content-length", "x-ratelimit-remaining") }
                .joinToString("\n") { name -> "$name: ${response.header(name).orEmpty()}" }
            val limit = 1024 * 1024
            val buffer = ByteArray(8192)
            val result = ByteArrayOutputStream()
            var truncated = false
            response.body?.byteStream()?.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (result.size() + read > limit) {
                        result.write(buffer, 0, limit - result.size())
                        truncated = true
                        break
                    }
                    result.write(buffer, 0, read)
                }
            }
            val bytes = result.toByteArray()
            val content = when {
                bytes.isEmpty() -> "(无响应体；可能是 204 或重定向，查看响应头)"
                contentType.startsWith("image/") || contentType.startsWith("application/octet-stream") ->
                    "(二进制内容：${bytes.size} 字节；图片和文件接口请查看响应 Content-Type)"
                else -> pretty(bytes.toString(Charsets.UTF_8))
            }
            ApiResult(
                method = method, url = finalUrl.toString(), httpCode = response.code,
                durationMillis = millis, contentType = contentType,
                headers = interestingHeaders, content = content,
                byteCount = bytes.size, truncated = truncated
            )
        }
    }

    private fun pretty(raw: String): String = try {
        when (raw.trim().firstOrNull()) {
            '{' -> JSONObject(raw).toString(2)
            '[' -> JSONArray(raw).toString(2)
            else -> raw
        }
    } catch (_: Exception) { raw }
}
