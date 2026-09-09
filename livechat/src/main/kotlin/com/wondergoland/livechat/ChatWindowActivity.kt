package com.wondergoland.livechat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * Hosts the chat as a full screen of its own. The chat page has no close button
 * of its own -- navigation belongs to the app -- so back finishes this screen.
 *
 * The chat window itself belongs to the process, not to this activity: this is
 * only the container it is put into while it is on screen.
 */
class ChatWindowActivity : AppCompatActivity() {

    private var chatWindow: ChatWindowView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android can rebuild this screen after the process was killed, long
        // after the app's own initialize() ran. Closing is the only honest
        // thing to do: the merchant and the deployment are gone with it.
        if (!LiveChat.isInitialized) {
            finish()
            return
        }

        val container = FrameLayout(this)
        setContentView(container)

        chatWindow = ChatWindowBus.attach(container) { intent ->
            runCatching {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST)
            }.isSuccess
        }
    }

    override fun onResume() {
        super.onResume()
        chatWindow?.onShown(true)
    }

    override fun onPause() {
        chatWindow?.onShown(false)
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != FILE_CHOOSER_REQUEST) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }

        chatWindow?.onFileChooserResult(
            if (resultCode == Activity.RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            } else {
                null
            },
        )
    }

    /**
     * Detached, not destroyed. Messages reach the app only while the WebView is
     * alive, and an unread badge is for the times the chat is not on screen --
     * so leaving this screen must not take the window down with it.
     * `LiveChat.destroy()` is what tears it down.
     */
    override fun onDestroy() {
        chatWindow?.let(ChatWindowBus::detach)
        chatWindow = null
        super.onDestroy()
    }

    private companion object {
        const val FILE_CHOOSER_REQUEST = 4321
    }
}
