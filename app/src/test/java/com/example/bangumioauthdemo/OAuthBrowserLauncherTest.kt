package com.example.bangumioauthdemo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * URI validation does not load a browser or access an Android account.
 * All test states are artificial; no credentials are ever used.
 */
class OAuthBrowserLauncherTest {

    private val valid =
        "https://bgm.tv/oauth/authorize?client_id=bgm72976ace318d39b25" +
            "&response_type=code&redirect_uri=bgmoauthdemo%3A%2F%2Fcallback&state=random123"

    @Test
    fun acceptsOnlyTheRegisteredOfficialOAuthEntry() {
        assertTrue(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid))
    }

    @Test
    fun refusesPhishingOriginsAndUnexpectedSchemes() {
        assertFalse(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid.replace("https:", "http:")))
        assertFalse(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid.replace("bgm.tv/", "bgm.tv.evil.example/")))
        assertFalse(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid.replace("bgm.tv/", "bgm.tv@evil.example/")))
        assertFalse(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid.replace("bgm.tv/", "bgm.tv:8443/")))
        assertFalse(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid + "#fragment"))
    }

    @Test
    fun refusesWrongGrantCallbackOrDuplicateParameters() {
        assertFalse(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid.replace("response_type=code", "response_type=token")))
        assertFalse(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid.replace("bgmoauthdemo%3A%2F%2Fcallback", "https%3A%2F%2Fevil.example")))
        assertFalse(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid.replace("state=random123", "state=")))
        assertFalse(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid + "&state=another"))
        assertFalse(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid.replace("oauth/authorize", "oauth/access_token")))
        assertFalse(OAuthBrowserLauncher.isOfficialAuthorizationUrl(valid.replace("bgm72976ace318d39b25", "other-app")))
    }
}
