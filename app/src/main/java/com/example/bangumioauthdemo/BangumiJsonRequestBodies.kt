package com.example.bangumioauthdemo

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Bangumi's v0 mutation routes require the exact media type "application/json".
 *
 * Important: OkHttp's String.toRequestBody(MediaType) automatically appends
 * "; charset=utf-8" when the media type has no charset. Some Bangumi endpoints
 * reject that parameter with HTTP 415. Encoding to bytes first preserves
 * UTF-8 payloads while keeping the wire Content-Type exactly application/json.
 *
 * Shared by collection creation/editing, episode progress, and search POST.
 */
internal object BangumiJsonRequestBodies {
    const val CONTENT_TYPE = "application/json"

    private val mediaType = CONTENT_TYPE.toMediaType()

    fun encode(json: String): RequestBody =
        json.toByteArray(Charsets.UTF_8).toRequestBody(mediaType)
}
