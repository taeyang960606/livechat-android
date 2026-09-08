package com.wondergoland.livechat

/**
 * The chat screen comes and goes; SDK calls do not wait for it. This holds the
 * live screen, so `setCustomerInfo` before `show()` is remembered and the same
 * call after `show()` reaches the WebView that is already up.
 */
internal object ChatWindowBus {

    private var window: ChatWindowView? = null

    fun attach(view: ChatWindowView) {
        window = view
        LiveChat.requireConfiguration().customer?.let(view::applyCustomer)
    }

    fun detach(view: ChatWindowView) {
        if (window === view) {
            window = null
        }
    }

    fun applyCustomer(customer: LiveChatCustomer?) {
        customer?.let { window?.applyCustomer(it) }
    }

    fun reset() {
        window?.resetVisitor()
    }
}
