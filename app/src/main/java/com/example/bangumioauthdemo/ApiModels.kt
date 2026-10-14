package com.example.bangumioauthdemo

import android.content.Context
import org.json.JSONObject

data class ApiParameter(
    val name: String,
    val location: String,
    val required: Boolean,
    val description: String,
    val type: String,
    val example: String,
    val enumValues: List<String>
)

data class ApiRequestBody(
    val required: Boolean,
    val mediaType: String,
    val description: String,
    val example: String,
    val schema: String
)

data class ApiResponseSpec(
    val status: String,
    val description: String,
    val contentTypes: List<String>
)

data class ApiEndpoint(
    val id: String,
    val method: String,
    val path: String,
    val tag: String,
    val summary: String,
    val description: String,
    val operationId: String,
    val auth: String,
    val scopes: List<String>,
    val params: List<ApiParameter>,
    val requestBody: ApiRequestBody?,
    val responses: List<ApiResponseSpec>,
    val source: String,
    val deprecated: Boolean
) {
    val mutatesData: Boolean
        get() = method != "GET" && method != "HEAD" &&
            !(method == "POST" && path.startsWith("/v0/search/"))
}

data class ApiResult(
    val method: String,
    val url: String,
    val httpCode: Int,
    val durationMillis: Long,
    val contentType: String,
    val headers: String,
    val content: String,
    val byteCount: Int,
    val truncated: Boolean
)

data class ApiHistoryEntry(
    val label: String,
    val code: Int?,
    val duration: Long?,
    val detail: String
)

object ApiCatalog {
    fun load(context: Context): List<ApiEndpoint> {
        val source = context.assets.open("bangumi_api_catalog.json").bufferedReader().use { it.readText() }
        val data = JSONObject(source).getJSONArray("endpoints")
        return (0 until data.length()).map { idx ->
            val o = data.getJSONObject(idx)
            val paramsArray = o.getJSONArray("params")
            val parameters = (0 until paramsArray.length()).map { n ->
                val p = paramsArray.getJSONObject(n)
                ApiParameter(
                    name = p.getString("name"),
                    location = p.getString("location"),
                    required = p.optBoolean("required"),
                    description = p.optString("description"),
                    type = p.optString("type"),
                    example = p.optString("example"),
                    enumValues = p.getJSONArray("enum").toStringList()
                )
            }
            val rb = o.optJSONObject("requestBody")?.let { b ->
                ApiRequestBody(
                    required = b.optBoolean("required"),
                    mediaType = b.optString("mediaType"),
                    description = b.optString("description"),
                    example = b.optString("example"),
                    schema = b.optString("schema")
                )
            }
            val responseArray = o.getJSONArray("responses")
            val responses = (0 until responseArray.length()).map { n ->
                val item = responseArray.getJSONObject(n)
                ApiResponseSpec(
                    status = item.getString("status"),
                    description = item.optString("description"),
                    contentTypes = item.getJSONArray("contentTypes").toStringList()
                )
            }
            ApiEndpoint(
                id = o.getString("id"), method = o.getString("method"),
                path = o.getString("path"), tag = o.getString("tag"),
                summary = o.getString("summary"),
                description = o.optString("description"),
                operationId = o.optString("operationId"),
                auth = o.optString("auth"),
                scopes = o.getJSONArray("scopes").toStringList(),
                params = parameters,
                requestBody = rb,
                responses = responses,
                source = o.optString("source"),
                deprecated = o.optBoolean("deprecated")
            )
        }
    }

    private fun org.json.JSONArray.toStringList(): List<String> =
        (0 until length()).map { optString(it) }
}
