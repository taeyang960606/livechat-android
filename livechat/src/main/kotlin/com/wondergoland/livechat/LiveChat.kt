package com.wondergoland.livechat

import android.content.Context
import android.content.Intent

/**
 * Entry point of the SDK.
 *
 * The chat itself is the platform's own chat page rendered in a WebView, which
 * is how comparable chat SDKs are built: one implementation of the
 * conversation, one set of settings the merchant already configures in the
 * dashboard, and no second UI to keep in step with it. What this SDK adds is
 * the part an app cannot get from a URL alone -- a packaged entry point,
 * message callbacks for an unread badge, sign-out, and the WebView plumbing
 * for file attachments.
 *
 * ```
 * LiveChat.initialize("https://chat.example.com", "01JMERCHANT", context)
 * LiveChat.getInstance().show()
 * ```
 */
object LiveChat {

    private var configuration: LiveChatConfiguration? = null

    /**
     * Fires for every message the visitor did not write.
     *
     * Callbacks arrive on the WebView's JavaScript thread, not the main
     * thread: post to the UI yourself before touching a view.
     */
    var newMessageListener: NewMessageListener? = null

    /** Fires when the chat page cannot load or reports an error. */
    var errorListener: ErrorListener? = null

    /**
     * Called for links tapped inside the chat. Return true to say the app
     * handled it; the default sends it to the system browser, because a
     * merchant's link opening inside the chat window is a dead end.
     */
    var urlHandler: UrlHandler? = null

    /** Called when the device has nothing that can pick a file. */
    var filePickerNotFoundListener: FilePickerActivityNotFoundListener? = null

    /**
     * @param baseUrl where the platform is deployed, without a trailing path,
     *   e.g. `https://chat.example.com`. Unlike a single-tenant SaaS SDK this
     *   cannot be baked in: every deployment has its own host.
     * @param merchantPublicId the merchant's public id from the dashboard.
     */
    @JvmStatic
    fun initialize(baseUrl: String, merchantPublicId: String, context: Context) {
        configuration = LiveChatConfiguration(
            baseUrl = baseUrl.trimEnd('/'),
            merchantPublicId = merchantPublicId.trim(),
            applicationContext = context.applicationContext,
        )
    }

    @JvmStatic
    fun getInstance(): LiveChat = this

    /**
     * Starts the chat in the background, so messages -- and therefore an unread
     * badge -- reach [newMessageListener] before the visitor has ever opened
     * the chat. Call it from the main thread once the app knows it wants the
     * badge, typically right after [initialize].
     *
     * Without it the first message the app hears about is one that arrives
     * after the first [show], because nothing is listening until then.
     */
    fun preload() {
        requireConfiguration()
        ChatWindowBus.window()
    }

    /** Opens the chat as its own screen. */
    fun show() {
        val configuration = requireConfiguration()
        val intent = Intent(configuration.applicationContext, ChatWindowActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        configuration.applicationContext.startActivity(intent)
    }

    /**
     * Ties the conversation to a signed-in member. `externalId` is required:
     * this platform treats it as the sole proof of identity, so a name and an
     * email on their own cannot identify anybody -- they are stored as
     * attributes instead.
     */
    fun setCustomerInfo(
        externalId: String,
        name: String? = null,
        email: String? = null,
        customParams: Map<String, String> = emptyMap(),
    ) {
        val configuration = requireConfiguration()
        configuration.customer = LiveChatCustomer(externalId, name, email, customParams)
        ChatWindowBus.applyCustomer(configuration.customer)
    }

    /**
     * Call this from the app's sign-out. The visitor id lives in the WebView's
     * storage, which the next person to sign in on this device would otherwise
     * inherit along with the conversation.
     *
     * It works with no chat anywhere on screen, which is the usual case -- people
     * sign out from the app's own settings. The request is stored, so it also
     * survives the honest sequence: sign out, close the app, somebody else signs
     * in tomorrow.
     *
     * Does nothing if [initialize] has not run: sign-out is on the app's
     * critical path and a library has no business throwing there. Call
     * [initialize] at app startup so this always has somewhere to write.
     */
    fun signOutCustomer() {
        val configuration = configuration ?: return
        configuration.customer = null
        configuration.hasPendingSignOut = true
        ChatWindowBus.signOut()
    }

    /**
     * Releases the chat window and everything it was listening to. Messages
     * stop reaching [newMessageListener] until the next [preload] or [show].
     * Main thread only.
     */
    fun destroy() {
        ChatWindowBus.destroy()
    }

    /** Whether [initialize] has run in this process. */
    val isInitialized: Boolean
        get() = configuration != null

    internal fun configurationOrNull(): LiveChatConfiguration? = configuration

    internal fun hasPendingSignOut(): Boolean = configuration?.hasPendingSignOut == true

    internal fun consumePendingSignOut() {
        configuration?.hasPendingSignOut = false
    }

    internal fun requireConfiguration(): LiveChatConfiguration =
        configuration ?: error("LiveChat.initialize() has to be called before using the chat.")

    internal fun chatUrl(): String {
        val configuration = requireConfiguration()

        return chatUrlOf(configuration.baseUrl, configuration.merchantPublicId)
    }
}

/**
 * The URL is the whole contract between this SDK and the platform. Kept as a
 * function of its two inputs so it can be tested as itself, rather than
 * through a configuration that needs a Context.
 */
internal fun chatUrlOf(baseUrl: String, merchantPublicId: String): String =
    baseUrl.trimEnd('/') + "/widget/app/" + merchantPublicId.trim()

internal class LiveChatConfiguration(
    val baseUrl: String,
    val merchantPublicId: String,
    val applicationContext: Context,
) {
    var customer: LiveChatCustomer? = null

    /**
     * A sign-out waiting to be carried out against the widget. Stored rather
     * than held in memory: the chat is usually nowhere near the screen when a
     * user signs out, and the process is often gone by the time the next
     * person opens the chat.
     */
    var hasPendingSignOut: Boolean
        get() = preferences.getBoolean(KEY_PENDING_SIGN_OUT, false)
        set(value) {
            preferences.edit().putBoolean(KEY_PENDING_SIGN_OUT, value).apply()
        }

    private val preferences by lazy {
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private companion object {
        const val PREFERENCES_NAME = "com.wondergoland.livechat"
        const val KEY_PENDING_SIGN_OUT = "pending_sign_out"
    }
}

internal data class LiveChatCustomer(
    val externalId: String,
    val name: String?,
    val email: String?,
    val customParams: Map<String, String>,
)
