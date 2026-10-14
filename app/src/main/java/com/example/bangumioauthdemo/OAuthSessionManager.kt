package com.example.bangumioauthdemo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

data class RefreshState(
    val kind: String,
    val message: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Single coordinator for UI, API calls and WorkManager.
 * Mutex serializes refresh-token rotation to prevent refresh-token reuse.
 */
object OAuthSessionManager {
    private val mutex = Mutex()
    @Volatile private var store: SecureSessionStore? = null
    private val _session = MutableStateFlow<OAuthSession?>(null)
    val session = _session.asStateFlow()
    private val _secret = MutableStateFlow("")
    val secret = _secret.asStateFlow()
    private val _refreshState = MutableStateFlow(RefreshState("NONE", "尚未授权"))
    val refreshState = _refreshState.asStateFlow()

    const val REFRESH_BEFORE_EXPIRATION_MS = 24L * 60L * 60L * 1000L

    @Synchronized
    fun initialize(context: Context) {
        if (store != null) return
        try {
            val ready = SecureSessionStore(context.applicationContext)
            ready.migratePreviousPlaintextSecret()
            _session.value = ready.session()
            _secret.value = ready.secret()
            _refreshState.value = if (_session.value == null) {
                RefreshState("NONE", "尚未授权")
            } else {
                RefreshState("RESTORED", "已从加密存储恢复授权，自动检查有效期")
            }
            store = ready
        } catch (e: Exception) {
            _refreshState.value = RefreshState(
                "STORAGE_ERROR", "读取本地加密凭据失败。可能是设备密钥发生了变化；请检查后重新授权。"
            )
        }
    }

    private fun requireStore(): SecureSessionStore =
        store ?: error("OAuth 加密存储未初始化；请重新启动应用")

    fun updateSecret(value: String) {
        requireStore().setSecret(value)
        _secret.value = value
    }

    suspend fun acceptTokenResponse(json: org.json.JSONObject): OAuthSession {
        mutex.lock()
        try {
            val result = OAuthSession.fromResponse(json, _session.value)
            requireStore().setSession(result) // Persist NEW rotated refresh_token before exposing it.
            _session.value = result
            _refreshState.value = RefreshState("REFRESHED", "授权凭据已更新并加密保存")
            return result
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Refresh only near expiration, except for a forced refresh after API HTTP 401.
     * A network failure never clears stored credentials.
     *
     * @param failedToken If another request already rotated that token, reuse the new one.
     */
    suspend fun ensureValidToken(
        forceRefresh: Boolean = false,
        failedToken: String? = null
    ): String? {
        mutex.lock()
        try {
            val old = _session.value ?: return null
            val now = System.currentTimeMillis()
            if (failedToken != null && old.accessToken != failedToken) {
                return old.accessToken
            }
            val remainingMs = old.expiresAtMillis - now
            val advanceMs = minOf(
                REFRESH_BEFORE_EXPIRATION_MS,
                maxOf(60_000L, old.expiresIn * 1000L / 4L)
            )
            if (!forceRefresh && remainingMs > advanceMs &&
                _refreshState.value.kind != "REAUTH_REQUIRED") {
                if (_refreshState.value.kind != "VALID") {
                    _refreshState.value = RefreshState("VALID", "已登录，Token 有效；到期前自动刷新")
                }
                return old.accessToken
            }
            if (_refreshState.value.kind == "REAUTH_REQUIRED" && !forceRefresh) {
                return null
            }
            val refresh = old.refreshToken
            val secret = _secret.value
            if (refresh.isBlank() || secret.isBlank()) {
                _refreshState.value = RefreshState(
                    "REAUTH_REQUIRED", "缺少 Refresh Token 或 App Secret，需要重新授权"
                )
                return if (!forceRefresh && remainingMs > 0 && secret.isNotBlank()) old.accessToken else null
            }
            _refreshState.value = RefreshState("REFRESHING", "正在通过 Bangumi 刷新并轮换 Token…")
            try {
                val json = OAuthRepository.refresh(refresh, secret)
                val updated = OAuthSession.fromResponse(json, old)
                requireStore().setSession(updated) // Commit before changing the in-memory tokens.
                _session.value = updated
                _refreshState.value = RefreshState("REFRESHED", "Token 已自动刷新并安全保存")
                return updated.accessToken
            } catch (e: OAuthHttpException) {
                if (e.statusCode == 400 || e.statusCode == 401 || e.statusCode == 403) {
                    _refreshState.value = RefreshState(
                        "REAUTH_REQUIRED",
                        "Bangumi 拒绝刷新凭据（HTTP ${e.statusCode}）；请重新授权或核对 App Secret"
                    )
                    return null
                }
                _refreshState.value = RefreshState(
                    "NETWORK_ERROR", "刷新遇到 HTTP ${e.statusCode}，保留原 Token 后重试"
                )
                return if (remainingMs > 0) old.accessToken else null
            } catch (e: Exception) {
                _refreshState.value = RefreshState(
                    "NETWORK_ERROR", "网络或本地存储暂时不可用，保留原 Token 后重试"
                )
                return if (remainingMs > 0) old.accessToken else null
            }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun clearAll() {
        mutex.lock()
        try {
            requireStore().clear()
            _session.value = null
            _secret.value = ""
            _refreshState.value = RefreshState("NONE", "本机授权及 App Secret 已清除")
        } finally {
            mutex.unlock()
        }
    }
}
