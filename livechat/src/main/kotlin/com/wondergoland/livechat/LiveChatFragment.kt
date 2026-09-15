package com.wondergoland.livechat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

/**
 * Embeds the chat inside the app's own layout, as a Fragment, for apps that
 * want the conversation on a screen of their own -- a tab, a pane -- rather
 * than the full-screen [LiveChat.show].
 *
 * It shares the one chat window with every other host (see [ChatWindowBus]), so
 * a conversation opened here and one opened by `show()` are the same
 * conversation, and an unread badge keeps working when this fragment is off
 * screen.
 *
 * Unlike the full-screen activity this does not inset itself for the status or
 * navigation bars: a fragment lives inside the host's layout, so insetting is
 * the host's to do.
 *
 * `LiveChat.initialize(...)` must have run before this fragment is shown; until
 * it has, the fragment shows nothing.
 *
 * Add it in code, or in XML with a
 * `androidx.fragment.app.FragmentContainerView` whose `android:name` is
 * `com.wondergoland.livechat.LiveChatFragment`.
 */
class LiveChatFragment : Fragment() {

    private var chatWindow: ChatWindowView? = null

    // Registered here so it is ready before the fragment is started. The
    // widget's attachment button is a file input the WebView cannot service on
    // its own -- the host has to open the picker and hand the result back.
    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            chatWindow?.onFileChooserResult(
                if (result.resultCode == Activity.RESULT_OK) {
                    WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
                } else {
                    null
                },
            )
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val host = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        // A fragment can be recreated after the process was killed, before the
        // app has re-run initialize(). Nothing to show until it has.
        if (!LiveChat.isInitialized) {
            return host
        }

        chatWindow = ChatWindowBus.attach(host) { intent ->
            runCatching { fileChooserLauncher.launch(intent) }.isSuccess
        }
        return host
    }

    override fun onResume() {
        super.onResume()
        chatWindow?.onShown(true)
    }

    override fun onPause() {
        chatWindow?.onShown(false)
        super.onPause()
    }

    override fun onDestroyView() {
        chatWindow?.let(ChatWindowBus::detach)
        chatWindow = null
        super.onDestroyView()
    }
}
