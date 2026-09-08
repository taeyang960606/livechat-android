package com.wondergoland.livechat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import androidx.appcompat.app.AppCompatActivity

/**
 * Hosts the chat as a full screen of its own. The chat page has no close button
 * of its own -- navigation belongs to the app -- so back finishes this screen.
 */
class ChatWindowActivity : AppCompatActivity() {

    private lateinit var chatWindow: ChatWindowView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android can rebuild this screen after the process was killed, long
        // after the app's own initialize() ran. Closing is the only honest
        // thing to do: the merchant and the deployment are gone with it.
        if (!LiveChat.isInitialized) {
            finish()
            return
        }

        chatWindow = ChatWindowView(this)
        chatWindow.fileChooserRequest = { intent ->
            runCatching {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST)
            }.isSuccess
        }
        setContentView(chatWindow)

        ChatWindowBus.attach(chatWindow)
        chatWindow.load()
    }

    override fun onResume() {
        super.onResume()

        if (this::chatWindow.isInitialized) {
            chatWindow.onShown(true)
        }
    }

    override fun onPause() {
        if (this::chatWindow.isInitialized) {
            chatWindow.onShown(false)
        }

        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != FILE_CHOOSER_REQUEST) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }

        if (!this::chatWindow.isInitialized) {
            return
        }

        chatWindow.onFileChooserResult(
            if (resultCode == Activity.RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            } else {
                null
            },
        )
    }

    override fun onDestroy() {
        if (this::chatWindow.isInitialized) {
            chatWindow.destroyChat()
        }

        super.onDestroy()
    }

    private companion object {
        const val FILE_CHOOSER_REQUEST = 4321
    }
}
