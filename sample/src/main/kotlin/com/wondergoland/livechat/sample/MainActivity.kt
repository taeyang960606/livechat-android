package com.wondergoland.livechat.sample

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.wondergoland.livechat.LiveChat
import com.wondergoland.livechat.NewMessageListener

/**
 * The smallest thing that proves the SDK works: open the chat, sign out, and
 * count messages that arrived while the chat was not in front.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LiveChat.initialize(BASE_URL, MERCHANT_PUBLIC_ID, this)
        // Starts the chat in the background. Without it the counter below stays
        // at zero until the chat has been opened once, because nothing is
        // listening -- which is the wrong half of the unread problem.
        LiveChat.getInstance().preload()

        val unread = TextView(this).apply { text = "Unread: 0" }
        var unreadCount = 0

        LiveChat.newMessageListener = NewMessageListener { message, isChatShown ->
            if (!isChatShown) {
                unreadCount += 1
                runOnUiThread { unread.text = "Unread: $unreadCount (${message.senderType})" }
            }
        }

        val open = Button(this).apply {
            text = "Open chat"
            setOnClickListener {
                unreadCount = 0
                unread.text = "Unread: 0"
                LiveChat.getInstance().show()
            }
        }

        val signOut = Button(this).apply {
            text = "Sign out"
            setOnClickListener { LiveChat.getInstance().signOutCustomer() }
        }

        val identify = Button(this).apply {
            text = "Identify member"
            setOnClickListener {
                LiveChat.getInstance().setCustomerInfo(
                    externalId = "member-123",
                    name = "Sample Member",
                    email = "member@example.test",
                    customParams = mapOf("plan" to "vip"),
                )
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 96, 48, 48)
                addView(unread)
                addView(open)
                addView(identify)
                addView(signOut)
            },
        )
    }

    private companion object {
        // Point these at your own deployment before running the sample.
        const val BASE_URL = "http://10.0.2.2:8080"
        const val MERCHANT_PUBLIC_ID = "01JMERCHANT"
    }
}
