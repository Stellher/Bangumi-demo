package com.example.bangumioauthdemo

import org.json.JSONObject

data class OAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val scope: String,
    val userId: String,
    val expiresIn: Long,
    val expiresAtMillis: Long,
    val raw: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("access_token", accessToken)
        .put("refresh_token", refreshToken)
        .put("token_type", tokenType)
        .put("scope", scope)
        .put("user_id", userId)
        .put("expires_in", expiresIn)
        .put("expires_at_millis", expiresAtMillis)
        .put("raw", raw)

    companion object {
        fun fromJson(o: JSONObject): OAuthSession? {
            val access = o.optString("access_token")
            if (access.isBlank()) return null
            return OAuthSession(
                accessToken = access,
                refreshToken = o.optString("refresh_token"),
                tokenType = o.optString("token_type", "Bearer"),
                scope = o.optString("scope", "null"),
                userId = o.optString("user_id"),
                expiresIn = o.optLong("expires_in", 0),
                expiresAtMillis = o.optLong("expires_at_millis", 0),
                raw = o.optString("raw")
            )
        }

        fun fromResponse(json: JSONObject, previous: OAuthSession? = null): OAuthSession {
            val token = json.optString("access_token")
            require(token.isNotBlank()) { "OAuth 接口未返回 access_token" }
            val expires = json.optLong("expires_in", 0)
            require(expires > 0) { "OAuth 接口未返回有效的 expires_in" }
            val refresh = json.optString("refresh_token").takeUnless { it.isBlank() || it == "null" }
                ?: previous?.refreshToken.orEmpty()
            return OAuthSession(
                accessToken = token,
                refreshToken = refresh,
                tokenType = json.optString("token_type", "Bearer"),
                scope = json.optString("scope", "null"),
                userId = json.optString("user_id").takeUnless { it.isBlank() || it == "null" }
                    ?: previous?.userId.orEmpty(),
                expiresIn = expires,
                expiresAtMillis = System.currentTimeMillis() + expires * 1000L,
                raw = json.toString(2)
            )
        }
    }
}
