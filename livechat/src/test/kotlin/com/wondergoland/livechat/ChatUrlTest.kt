package com.wondergoland.livechat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The URL is the whole contract between this SDK and the platform, and it is
 * the one piece of logic here that runs without a device.
 */
class ChatUrlTest {

    @Test
    fun `builds the chat url from the deployment and the merchant`() {
        assertEquals(
            "https://chat.example.com/widget/app/01JMERCHANT",
            chatUrlFor("https://chat.example.com", "01JMERCHANT"),
        )
    }

    @Test
    fun `tolerates a trailing slash and padded input`() {
        assertEquals(
            "https://chat.example.com/widget/app/01JMERCHANT",
            chatUrlFor("https://chat.example.com/", " 01JMERCHANT "),
        )
    }

    private fun chatUrlFor(baseUrl: String, merchantPublicId: String): String =
        baseUrl.trimEnd('/') + "/widget/app/" + merchantPublicId.trim()
}
