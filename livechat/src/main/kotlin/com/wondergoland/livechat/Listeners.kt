package com.wondergoland.livechat

/** A message the visitor did not write: an operator, the bot, or the system. */
fun interface NewMessageListener {
    /**
     * @param isChatShown whether the chat screen was in front when it arrived.
     *   False is the case an unread badge exists for.
     */
    fun onNewMessage(message: ChatMessage, isChatShown: Boolean)
}

fun interface ErrorListener {
    fun onError(error: ChatError)
}

fun interface UrlHandler {
    /** Return true when the app handled the link itself. */
    fun handleUrl(url: String): Boolean
}

fun interface FilePickerActivityNotFoundListener {
    fun onFilePickerActivityNotFound()
}

data class ChatMessage(
    val id: Long,
    val body: String,
    val senderType: String,
    val senderName: String?,
    val createdAt: String?,
    val hasAttachment: Boolean,
)

data class ChatError(
    val kind: Kind,
    val description: String,
) {
    enum class Kind {
        /** The chat page itself could not be loaded. */
        PAGE_LOAD,

        /** The widget loaded and then reported a problem of its own. */
        WIDGET,
    }
}
