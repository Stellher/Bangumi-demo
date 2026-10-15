package com.example.bangumioauthdemo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URI

/**
 * Launches Bangumi's official consent UI via Android's installed browser,
 * NOT WebView. Credentials, CAPTCHA and login cookies stay inside the browser.
 *
 * OAuth itself cannot be completed with OkHttp alone: Bangumi publicly
 * documents only the authorization_code user-consent grant. OkHttp handles
 * code exchange / token refresh separately in OAuthRepository.
 */
object OAuthBrowserLauncher {

    /**
     * Reject untrusted authorization URLs even if a future caller accidentally
     * passes a user-controlled link. Unit-tested with the JVM URI parser.
     */
    fun isOfficialAuthorizationUrl(raw: String): Boolean = try {
        val uri = URI(raw)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("bgm.tv", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.rawUserInfo == null &&
            uri.rawFragment == null &&
            uri.path == "/oauth/authorize" &&
            uri.rawQuery != null
    } catch (_: Exception) {
        false
    }

    fun launch(activity: Activity, authorizeUrl: String) {
        require(isOfficialAuthorizationUrl(authorizeUrl)) {
            "仅允许打开 Bangumi 官方 OAuth 授权地址"
        }
        val url = Uri.parse(authorizeUrl)

        val colors = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Color.rgb(247, 249, 255))
            .setNavigationBarColor(Color.rgb(247, 249, 255))
            .build()

        val customTabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setDefaultColorSchemeParams(colors)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()

        try {
            customTabs.launchUrl(activity, url)
        } catch (_: ActivityNotFoundException) {
            // An installed browser without Custom Tabs can still complete OAuth.
            // This still does not create a WebView or collect user passwords.
            try {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, url)
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                )
            } catch (_: ActivityNotFoundException) {
                throw IllegalStateException(
                    "手机上未找到可用浏览器，请先安装支持 HTTPS 的浏览器。"
                )
            }
        }
    }
}
