package com.example.bangumioauthdemo

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the OAuth refresh token, access token and client secret in AndroidKeyStore AES-256-GCM
 * encrypted private app storage. The AndroidKeystore key is never exported.
 *
 * All saves are synchronous/committed to make refresh-token rotation durable before
 * the old token is discarded. Android backups are disabled in AndroidManifest.
 */
class SecureSessionStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("bangumi_secure_oauth_v3", Context.MODE_PRIVATE)
    private val legacy = app.getSharedPreferences("oauth_demo", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "bangumi_oauth_aes_256_v3"
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun decrypt(): JSONObject {
        val encoded = prefs.getString("encrypted_auth", null) ?: return JSONObject()
        val allBytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(allBytes.size > 12) { "认证存储格式异常" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, allBytes.copyOfRange(0, 12)))
        val data = cipher.doFinal(allBytes.copyOfRange(12, allBytes.size))
        return JSONObject(data.toString(Charsets.UTF_8))
    }

    private fun save(data: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val result = cipher.iv + cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(result, Base64.NO_WRAP)
        check(prefs.edit().putString("encrypted_auth", encoded).commit()) {
            "无法保存 OAuth Token 到本地存储"
        }
    }

    @Synchronized
    fun session(): OAuthSession? = decrypt().optJSONObject("session")?.let(OAuthSession::fromJson)

    @Synchronized
    fun secret(): String = decrypt().optString("client_secret").takeUnless { it == "null" }.orEmpty()

    @Synchronized
    fun setSecret(value: String) {
        val data = decrypt()
        data.put("client_secret", value)
        save(data)
    }

    @Synchronized
    fun setSession(value: OAuthSession) {
        val data = decrypt()
        data.put("session", value.toJson())
        save(data)
    }

    @Synchronized
    fun clear() {
        check(prefs.edit().clear().commit()) { "无法清除 OAuth 存储" }
        legacy.edit().remove("client_secret").remove("pending_state").apply()
    }

    @Synchronized
    fun migratePreviousPlaintextSecret() {
        val oldValue = legacy.getString("client_secret", null)
        if (!oldValue.isNullOrEmpty()) {
            val data = decrypt()
            if (data.optString("client_secret").isBlank()) {
                data.put("client_secret", oldValue)
                save(data)
            }
            check(legacy.edit().remove("client_secret").commit()) {
                "无法清理上一版本明文存储的密钥"
            }
        }
    }
}
