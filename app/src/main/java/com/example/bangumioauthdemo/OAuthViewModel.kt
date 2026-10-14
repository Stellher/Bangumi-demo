package com.example.bangumioauthdemo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom

class OAuthViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("oauth_demo", 0)
    private val _output = MutableStateFlow("授权后将在这里显示 OAuth 返回的所有字段。")
    val output = _output.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    val clientSecret = OAuthSessionManager.secret
    val session = OAuthSessionManager.session
    val refreshState = OAuthSessionManager.refreshState
    private var pendingState: String? = prefs.getString("pending_state", null)

    init {
        OAuthSessionManager.initialize(app)
        if (OAuthSessionManager.session.value != null) {
            _output.value = "已从设备加密存储恢复 OAuth 授权。正在自动检查 Token…"
            viewModelScope.launch {
                OAuthSessionManager.ensureValidToken()
                val state = OAuthSessionManager.refreshState.value
                _output.value = "自动登录检查完成：${state.message}\n请打开「全部 API」继续使用。"
            }
        }
    }

    fun updateClientSecret(secret: String) {
        try {
            OAuthSessionManager.updateSecret(secret)
        } catch (_: Exception) {
            _output.value = "保存 App Secret 失败，请检查设备密钥与存储。"
        }
    }

    fun beginLogin(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val state = bytes.joinToString("") { "%02x".format(it) }
        pendingState = state
        prefs.edit().putString("pending_state", state).apply()
        _output.value = "正在等待 Bangumi 浏览器授权回调…"
        return OAuthRepository.authorizeUrl(state)
    }

    fun callback(uri: Uri?) {
        if (uri?.scheme != "bgmoauthdemo" || uri.host != "callback") return
        val error = uri.getQueryParameter("error")
        if (error != null) {
            pendingState = null
            prefs.edit().remove("pending_state").apply()
            _output.value = "授权失败：" + error
            return
        }
        val state = uri.getQueryParameter("state")
        if (pendingState == null || state != pendingState) {
            _output.value = "拒绝授权回调：state 不匹配，请重新登录。"
            return
        }
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            _output.value = "回调中没有 authorization code。"
            return
        }
        pendingState = null
        prefs.edit().remove("pending_state").apply()
        _output.value = "Authorization Code:\n" + code + "\n\n正在交换 Access Token…"
        launchOperation(
            action = { OAuthRepository.exchange(code, OAuthSessionManager.secret.value) },
            updatesTokens = true
        )
    }

    private fun launchOperation(
        action: suspend () -> JSONObject,
        updatesTokens: Boolean = false
    ) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val result = action()
                if (updatesTokens) {
                    OAuthSessionManager.acceptTokenResponse(result)
                    RefreshScheduler.schedule(app)
                }
                _output.value = result.toString(2)
            } catch (e: Exception) {
                _output.value = "请求失败：" + (e.message ?: e.javaClass.simpleName)
            } finally {
                _busy.value = false
            }
        }
    }

    fun refresh() {
        if (_busy.value) return
        if (session.value == null) {
            _output.value = "没有保存的 OAuth Token，请先登录"
            return
        }
        _busy.value = true
        viewModelScope.launch {
            try {
                val token = OAuthSessionManager.ensureValidToken(forceRefresh = true)
                _output.value = if (token != null && refreshState.value.kind == "REFRESHED") {
                    session.value?.raw.orEmpty()
                } else {
                    "刷新未成功：${refreshState.value.message}"
                }
            } catch (e: Exception) {
                _output.value = "刷新异常：" + (e.message ?: "未知错误")
            } finally {
                _busy.value = false
            }
        }
    }

    fun status() {
        launchOperation({
            val token = OAuthSessionManager.ensureValidToken()
                ?: error("无法获取有效的 Access Token，请检查授权状态")
            OAuthRepository.status(token)
        })
    }

    fun me() {
        launchOperation({
            val token = OAuthSessionManager.ensureValidToken()
                ?: error("无法获取有效的 Access Token，请检查授权状态")
            OAuthRepository.me(token)
        })
    }

    fun clear() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                OAuthSessionManager.clearAll()
                RefreshScheduler.cancel(app)
                pendingState = null
                prefs.edit().remove("pending_state").apply()
                _output.value = "已安全清除本机 Access Token、Refresh Token 与 App Secret。"
            } catch (_: Exception) {
                _output.value = "清除授权失败，请检查设备存储。"
            } finally {
                _busy.value = false
            }
        }
    }
}
