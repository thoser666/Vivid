package com.vivid.feature.streaming.ui

import android.content.Context
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class BrowserWidgetSecurityTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun request(url: String, mainFrame: Boolean = true): WebResourceRequest = mockk {
        every { this@mockk.url } returns Uri.parse(url)
        every { isForMainFrame } returns mainFrame
    }

    @Test fun `live scripts keep local content and file access disabled`() {
        val view = createBrowserWidgetWebView(context)
        try {
            assertTrue(view.settings.javaScriptEnabled)
            assertTrue(view.settings.domStorageEnabled)
            assertFalse(view.settings.allowContentAccess)
            assertFalse(view.settings.allowFileAccess)
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, view.settings.mixedContentMode)
            assertFalse(view.settings.javaScriptCanOpenWindowsAutomatically)
            assertFalse(view.settings.supportMultipleWindows())
            val permission = mockk<PermissionRequest>(relaxed = true)
            view.webChromeClient!!.onPermissionRequest(permission)
            verify(exactly = 1) { permission.deny() }
        } finally { view.destroy() }
    }

    @Test fun `page updates restore local data restrictions even when URL is unchanged`() {
        val view = createBrowserWidgetWebView(context)
        try {
            for (url in listOf(OVERLAY_URL, OVERLAY_URL, "https://overlay.example/updated")) {
                view.settings.allowContentAccess = true
                view.settings.allowFileAccess = true
                updateBrowserWidgetPage(view, url)
                assertFalse(view.settings.allowContentAccess)
                assertFalse(view.settings.allowFileAccess)
                assertEquals(url, org.robolectric.Shadows.shadowOf(view).lastLoadedUrl)
                assertTrue(view.settings.javaScriptEnabled)
            }
        } finally { view.destroy() }
    }

    @Test fun `top level navigation stays on configured HTTPS origin`() {
        val client = BrowserWidgetWebViewClient(OVERLAY_URL)
        val view = mockk<WebView>()
        for (url in listOf("https://overlay.example/next?token=1", "https://OVERLAY.example:443/next")) {
            assertFalse(url, client.shouldOverrideUrlLoading(view, request(url)))
        }
        for (url in listOf("https://other.example/", "https://overlay.example:8443/", "http://overlay.example/",
            "content://com.vivid/private", "file:///data/data/com.vivid/secret", "javascript:alert(1)")) {
            assertTrue(url, client.shouldOverrideUrlLoading(view, request(url)))
        }
    }

    @Test fun `CDN resources remain available but local and cleartext resources are denied`() {
        val client = BrowserWidgetWebViewClient(OVERLAY_URL)
        val view = mockk<WebView>()
        assertNull(client.shouldInterceptRequest(view, request("https://cdn.example/script.js")))
        assertFalse(client.shouldOverrideUrlLoading(view, request("https://embed.example/widget", mainFrame = false)))
        assertTrue(client.shouldOverrideUrlLoading(view, request("http://embed.example/widget", mainFrame = false)))
        for (url in listOf("content://com.vivid/private", "file:///data/data/com.vivid/secret", "http://cdn.example/script.js")) {
            assertEquals(url, 403, client.shouldInterceptRequest(view, request(url))!!.statusCode)
        }
    }
}

private const val OVERLAY_URL = "https://overlay.example/widget"
