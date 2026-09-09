package com.wondergoland.livechat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.MutableContextWrapper
import android.view.ViewGroup
import java.lang.ref.WeakReference

/**
 * The one chat window in this process.
 *
 * It deliberately outlives the screen that shows it. Messages reach the app
 * only while this WebView is alive, and an unread badge is for the times the
 * chat is *not* on screen -- so tearing the window down with the activity
 * would leave the badge working in the one case nobody needs it. Sign-out has
 * the same shape: people sign out from the app's own settings, with no chat
 * anywhere near the screen.
 */
internal object ChatWindowBus {

    // Lint is right that this is a view in a static field, and wrong that it is
    // a leak: what a static view usually leaks is the activity behind its
    // context, and [detach] points the context wrapper back at the application
    // before the activity finishes. What is held is one WebView for the life of
    // the process, on purpose, and `LiveChat.destroy()` releases it.
    @SuppressLint("StaticFieldLeak")
    private var window: ChatWindowView? = null

    // Weak, so a host that goes away without detaching -- a process the system
    // tore down mid-transition -- cannot be held here by this object.
    private var host: WeakReference<Activity>? = null

    /**
     * The window, created and loading if this is the first call. Main thread
     * only: it builds a WebView.
     *
     * The context is a [MutableContextWrapper] rather than the application
     * context. A WebView needs an activity to put a `<select>` dropdown or a
     * dialog on screen -- the widget's phone field has one -- and the
     * application context has no window token to hang it on. The wrapper lets
     * one long-lived WebView point at whichever activity is showing it, and
     * back at the application when none is.
     */
    fun window(): ChatWindowView {
        window?.let { return it }

        val created = ChatWindowView(
            MutableContextWrapper(LiveChat.requireConfiguration().applicationContext),
        )
        window = created
        created.load()

        return created
    }

    fun attach(
        container: ViewGroup,
        fileChooserRequest: (intent: Intent) -> Boolean,
    ): ChatWindowView {
        val view = window()

        // The previous host may still be finishing, so take the view off it
        // before handing it to this one.
        (view.parent as? ViewGroup)?.removeView(view)
        (view.context as? MutableContextWrapper)?.baseContext = container.context
        host = (container.context as? Activity)?.let(::WeakReference)
        view.fileChooserRequest = fileChooserRequest
        container.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        return view
    }

    fun detach(view: ChatWindowView) {
        if (window !== view) {
            return
        }

        view.fileChooserRequest = null
        view.onShown(false)
        host = null
        (view.parent as? ViewGroup)?.removeView(view)
        // Back to the application, or the wrapper holds the finished activity
        // alive for as long as the window lives.
        (view.context as? MutableContextWrapper)?.baseContext =
            LiveChat.requireConfiguration().applicationContext
    }

    fun applyCustomer(customer: LiveChatCustomer?) {
        customer?.let { window?.applyCustomer(it) }
    }

    fun signOut() {
        window?.signOutVisitor()
    }

    fun finishHost() {
        host?.get()?.finish()
        host = null
    }

    fun destroy() {
        window?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroyChat()
        }
        window = null
    }
}
