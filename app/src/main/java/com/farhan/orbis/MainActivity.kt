package com.farhan.orbis

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Orbis's interface is written in HTML/CSS/JS (assets/index.html) and
 * shown in a full-screen WebView. The page itself is bundled with the
 * APK and works offline; the INTERNET permission exists only so the
 * OrbisNet bridge below can call user-configured AI endpoints natively.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    // One worker thread for bridge requests: calls are serialised, which
    // is fine for one-user chat traffic and keeps ordering predictable.
    private val netExecutor = Executors.newSingleThreadExecutor()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            addJavascriptInterface(OrbisNetBridge(), "OrbisNet")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)

        // Back walks the WebView's own history first; only when the page
        // cannot go back any further does Back leave the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onDestroy() {
        netExecutor.shutdownNow()
        super.onDestroy()
    }

    /**
     * Native HTTP bridge for the web app, exposed as `window.OrbisNet`.
     *
     * The page's own fetch() is bound by CORS, so OpenAI-compatible
     * endpoints that don't send CORS headers (OpenAI itself, strict
     * providers, local servers) refuse it. Native HTTP has no CORS, so
     * when this bridge exists the page routes its API calls through it.
     * Contract: see ORBISNET.md in the project root.
     */
    inner class OrbisNetBridge {

        /**
         * Performs one HTTP request off the UI thread and reports back
         * by evaluating `window.__orbisNetResult(id, ok, status, text)`
         * in the page. Exactly one callback per call, always — including
         * for malformed arguments and network failures (ok=false,
         * status=0, text=human-readable error).
         */
        @JavascriptInterface
        fun fetchJson(id: String, method: String, url: String, headersJson: String, body: String) {
            netExecutor.execute {
                var ok = false
                var status = 0
                var text = ""
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = method.uppercase()
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 60_000
                    connection.instanceFollowRedirects = true

                    val headers = JSONObject(headersJson)
                    val names = headers.keys()
                    while (names.hasNext()) {
                        val name = names.next()
                        connection.setRequestProperty(name, headers.getString(name))
                    }

                    if (body.isNotEmpty()) {
                        connection.doOutput = true
                        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }

                    status = connection.responseCode
                    // HTTP error statuses still carry the provider's JSON
                    // (error.message etc.), so both streams are success
                    // for our purposes; the page reads status itself.
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    ok = true
                    connection.disconnect()
                } catch (e: Exception) {
                    ok = false
                    status = 0
                    text = e.message ?: e.javaClass.simpleName
                }
                deliver(id, ok, status, text)
            }
        }

        private fun deliver(id: String, ok: Boolean, status: Int, text: String) {
            // JSONObject.quote() produces a valid, fully-escaped JS
            // string literal for arbitrary response text.
            val script = "window.__orbisNetResult(" +
                JSONObject.quote(id) + ", " + ok + ", " + status + ", " +
                JSONObject.quote(text) + ")"
            webView.post { webView.evaluateJavascript(script, null) }
        }
    }
}
