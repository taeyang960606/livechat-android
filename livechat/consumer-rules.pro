# The chat page reaches this bridge by name from injected JavaScript, so R8
# sees no reference to it and would be free to rename or drop the method.
# AGP's default rules happen to cover @JavascriptInterface today; this does not
# depend on that staying true in the host app's configuration.
-keepclassmembers class com.wondergoland.livechat.** {
    @android.webkit.JavascriptInterface <methods>;
}
