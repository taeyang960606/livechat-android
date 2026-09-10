package com.wondergoland.livechat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject

/**
 * The chat, as a view. One of these exists per process and it is handed from
 * screen to screen -- see [ChatWindowBus] for why it outlives its host.
 *
 * Not part of the public API. Putting the chat inside a screen of the app's own
 * means the host has to forward file-chooser results back to it, so exposing
 * the view without that plumbing would offer an embedding path where the
 * attachment button silently does nothing. `LiveChat.show()` is the supported
 * entry point; a real embedding API can be added when somebody needs one.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class ChatWindowView(context: Context) : FrameLayout(context) {

    internal var fileChooserRequest: ((intent: Intent) -> Boolean)? = null

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var isChatShown: Boolean = false

    /**
     * The widget has mounted and its controls are bound. Before that point a
     * `reset()` reaches the SDK object but not the stored visitor id, and an
     * `identify()` has nothing holding it -- so both wait for this.
     *
     * Written from the WebView's JavaScript thread, read from the main thread
     * whenever the app identifies a member or signs one out.
     */
    @Volatile
    private var isWidgetReady: Boolean = false

    /**
     * When the page was last loaded, on the monotonic clock. The widget reads
     * its configuration once per load -- whether operators are online, the
     * business hours, the merchant's settings -- and this window now lives as
     * long as the process does, so without a way to notice the age of it the
     * chat goes on answering with what was true whenever it happened to load.
     */
    @Volatile
    private var loadedAtMillis: Long = 0L

    private val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        // Without this the visitor id cannot be stored and every visit starts
        // as a new anonymous visitor with no history.
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        setBackgroundColor(0xFFFFFFFF.toInt())
    }

    init {
        addView(
            webView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        webView.addJavascriptInterface(Bridge(), BRIDGE_NAME)
        webView.webViewClient = ChatWebViewClient()
        webView.webChromeClient = ChatWebChromeClient()
    }

    fun load() {
        loadedAtMillis = SystemClock.elapsedRealtime()
        webView.loadUrl(LiveChat.chatUrl())
    }

    /**
     * Reloads when the page has been sitting here longer than [maxAgeMillis].
     * Called as the chat goes on screen, because that is the moment the visitor
     * starts acting on what it says.
     */
    internal fun reloadIfStale(maxAgeMillis: Long) {
        if (SystemClock.elapsedRealtime() - loadedAtMillis < maxAgeMillis) {
            return
        }

        loadedAtMillis = SystemClock.elapsedRealtime()
        isWidgetReady = false
        webView.reload()
    }

    fun onShown(shown: Boolean) {
        isChatShown = shown
    }

    internal fun applyCustomer(customer: LiveChatCustomer) {
        if (!isWidgetReady) {
            // Applied from the ready event instead, which reads the same
            // stored customer back.
            return
        }

        val identity = JSONObject().apply {
            put("externalId", customer.externalId)
            customer.name?.let { put("name", it) }
            customer.email?.let { put("email", it) }
        }
        evaluate(call("identify", identity.toString()))

        if (customer.customParams.isNotEmpty()) {
            val attributes = JSONObject(customer.customParams.toMap())
            evaluate(call("setAttributes", attributes.toString()))
        }
    }

    /**
     * The visitor id and the stored conversation live in this WebView's
     * storage, and the widget's own `reset()` is what clears them. A sign-out
     * that lands before the widget mounted is remembered by [LiveChat] and
     * replayed from the ready event -- clearing storage from here instead
     * would empty Web Storage for every WebView in the host app.
     */
    internal fun signOutVisitor() {
        if (!isWidgetReady) {
            return
        }

        evaluate(call("reset"))
        LiveChat.consumePendingSignOut()
    }

    fun destroyChat() {
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.destroy()
    }

    internal fun onFileChooserResult(uris: Array<Uri>?) {
        pendingFileCallback?.onReceiveValue(uris)
        pendingFileCallback = null
    }

    private fun onWidgetReady() {
        isWidgetReady = true

        // A sign-out that happened while no chat was on screen -- the usual
        // case, since people sign out from the app's own settings -- is
        // replayed here, before the visitor sees anything.
        if (LiveChat.hasPendingSignOut()) {
            evaluate(call("reset"))
            LiveChat.consumePendingSignOut()
        }

        // After the reset, never before: reset() drops the member identity, so
        // identifying first would leave the new member anonymous.
        LiveChat.configurationOrNull()?.customer?.let(::applyCustomer)
    }

    private fun evaluate(script: String) {
        post { webView.evaluateJavascript(script, null) }
    }

    /**
     * Every call goes through the widget's own queue rather than straight at
     * the API. The page loads its bundle asynchronously, so a call made right
     * after the page finished would otherwise hit a `window.LiveChat` that is
     * still the loader's stub -- silently doing nothing.
     */
    private fun call(method: String, argument: String? = null): String {
        val args = if (argument == null) "" else argument

        return "window.__livechatCall('$method', [$args]);"
    }

    private inner class Bridge {
        /**
         * Called by the script injected on page load. The payload is JSON so
         * this contract does not grow a positional-argument problem later.
         */
        @JavascriptInterface
        fun postEvent(payload: String) {
            val event = runCatching { JSONObject(payload) }.getOrNull() ?: return

            when (event.optString("type")) {
                "ready" -> onWidgetReady()

                "message" -> {
                    val message = event.optJSONObject("message") ?: return
                    LiveChat.newMessageListener?.onNewMessage(
                        ChatMessage(
                            id = message.optLong("id"),
                            body = message.optString("body"),
                            senderType = message.optString("senderType"),
                            senderName = message.optString("senderName").ifEmpty { null },
                            createdAt = message.optString("createdAt").ifEmpty { null },
                            hasAttachment = message.optBoolean("hasAttachment"),
                        ),
                        // The widget reports whether its panel was open; on this
                        // screen the panel is always open, so what the app
                        // actually wants to know is whether the screen is in
                        // front of the user.
                        isChatShown,
                    )
                }

                "error" -> LiveChat.errorListener?.onError(
                    ChatError(ChatError.Kind.WIDGET, event.optString("description")),
                )
            }
        }
    }

    private inner class ChatWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            // A reload starts over: the bridge and the widget are both gone
            // until the new page announces itself.
            isWidgetReady = false
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            // The customer is applied from the ready event rather than here:
            // a pending sign-out has to reset the visitor first.
            view?.evaluateJavascript(BOOTSTRAP_SCRIPT, null)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            val url = request?.url?.toString() ?: return false

            if (url == LiveChat.chatUrl()) {
                return false
            }

            // A link tapped inside the chat is the merchant's content, not part
            // of the conversation: opening it in this WebView would strand the
            // visitor on a page with no way back to the chat.
            return LiveChat.urlHandler?.handleUrl(url) ?: openExternally(url)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            if (request?.isForMainFrame != true) {
                return
            }

            LiveChat.errorListener?.onError(
                ChatError(
                    ChatError.Kind.PAGE_LOAD,
                    error?.description?.toString() ?: "The chat page failed to load.",
                ),
            )
        }

        private fun openExternally(url: String): Boolean {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            return runCatching { context.startActivity(intent) }.isSuccess
        }
    }

    // Console messages are deliberately not swallowed: the widget's own
    // errors show up there, and this is the only window into a chat that
    // misbehaves on somebody else's device.
    private inner class ChatWebChromeClient : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            // Attachments are a plain file input inside the page, and a WebView
            // does nothing with it unless the host opens the picker. Handling
            // it here is the difference between attachments working and the
            // button doing nothing.
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = filePathCallback

            val intent = fileChooserParams?.createIntent()
            val launched = intent != null && fileChooserRequest?.invoke(intent) == true

            if (!launched) {
                pendingFileCallback = null
                filePathCallback?.onReceiveValue(null)
                LiveChat.filePickerNotFoundListener?.onFilePickerActivityNotFound()
            }

            return launched
        }
    }

    private companion object {
        const val BRIDGE_NAME = "LiveChatAndroid"

        /**
         * Subscribes through the widget's own SDK rather than scraping the DOM,
         * and through its queue rather than its API: the page loads the bundle
         * asynchronously, so at the moment this runs `window.LiveChat` may
         * still be the loader's stub. Anything pushed onto the queue is
         * replayed once the real SDK installs itself, which makes the order of
         * the two irrelevant.
         */
        val BOOTSTRAP_SCRIPT = """
            (function () {
                if (window.__livechatAndroidBridge) {
                    return;
                }

                window.__livechatAndroidBridge = true;

                window.__livechatCall = function (method, args) {
                    var sdk = window.LiveChat = window.LiveChat || { q: [] };

                    if (typeof sdk[method] === 'function') {
                        sdk[method].apply(sdk, args || []);
                        return;
                    }

                    sdk.q = sdk.q || [];
                    sdk.q.push([method, args || []]);
                };

                // The widget replays 'ready' for a late subscriber, so this
                // cannot miss a widget that mounted before the bridge landed.
                window.__livechatCall('on', ['ready', function () {
                    LiveChatAndroid.postEvent(JSON.stringify({ type: 'ready' }));
                }]);

                window.__livechatCall('on', ['message', function (payload) {
                    LiveChatAndroid.postEvent(JSON.stringify({
                        type: 'message',
                        message: payload.message
                    }));
                }]);

                window.__livechatCall('on', ['error', function (payload) {
                    LiveChatAndroid.postEvent(JSON.stringify({
                        type: 'error',
                        description: (payload && payload.message) ? String(payload.message) : 'widget error'
                    }));
                }]);
            })();
        """.trimIndent()
    }
}
