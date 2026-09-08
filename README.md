# LiveChat Android SDK

Drops the LiveChat conversation into an Android app.

Inside, the chat is the platform's own chat page rendered in a `WebView`. That is deliberate, and it is how comparable chat SDKs are built: one implementation of the conversation, the settings the merchant already configured in the dashboard, and no second chat UI to keep in step with the web one. What this SDK adds is the part an app cannot get from a URL on its own — a packaged entry point, message callbacks for an unread badge, sign-out, and the WebView plumbing that makes file attachments work.

## Install

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

`build.gradle.kts`:

```kotlin
implementation("com.github.taeyang960606:livechat-android:0.1.0")
```

Requires `minSdk` 21 and JDK 17 to build.

## Use

```kotlin
// Once, before showing the chat. baseUrl is where LiveChat is deployed.
LiveChat.initialize("https://chat.example.com", "01JMERCHANT", context)

// Opens the chat as its own screen.
LiveChat.getInstance().show()
```

The chat screen fills the display and has no close button of its own: navigation belongs to the app, and the system back button finishes the screen.

### Signed-in members

```kotlin
LiveChat.getInstance().setCustomerInfo(
    externalId = "member-123",
    name = "Member Customer",
    email = "member@example.test",
    customParams = mapOf("plan" to "vip"),
)
```

`externalId` is required. This platform treats it as the sole proof of identity, so a name and an email on their own cannot identify anybody — they are stored as attributes against the conversation instead.

Call it before or after `show()`; the value is remembered and applied to the chat whenever it opens.

### Sign-out

```kotlin
LiveChat.getInstance().signOutCustomer()
```

**Call this from the app's own sign-out.** The visitor id lives in the WebView's storage, which the next person to sign in on the device would otherwise inherit along with the previous conversation.

### Callbacks

```kotlin
LiveChat.newMessageListener = NewMessageListener { message, isChatShown ->
    // Every message the visitor did not write: operator, bot or system.
    // isChatShown is false when the chat screen was not in front, which is
    // the case an unread badge exists for.
}

LiveChat.errorListener = ErrorListener { error ->
    // error.kind: PAGE_LOAD when the chat page itself failed, WIDGET when the
    // chat loaded and then reported a problem.
}

LiveChat.urlHandler = UrlHandler { url ->
    // A link tapped inside the chat. Return true when the app handled it.
    // Default: hand it to the system browser.
    false
}

LiveChat.filePickerNotFoundListener = FilePickerActivityNotFoundListener {
    // The device has nothing that can pick a file.
}
```

Callbacks arrive on the WebView's JavaScript thread, not the main thread. Post to the UI yourself before touching a view.

Messages arriving while the app is in the background reach `newMessageListener` only while the process is alive. **There is no offline push**: a reply that arrives after the app is killed notifies nobody. That needs FCM, on the server and in the app, and it is not part of this SDK.

## What the app has to provide

- `INTERNET` permission comes from the SDK's manifest; nothing to add.
- Nothing else. JavaScript, DOM storage and the file chooser are configured inside the SDK — DOM storage in particular, because the visitor id is stored there and every visit would otherwise start as a new anonymous visitor.

## Sample

`sample/` is the smallest app that proves the SDK works: open the chat, identify a member, sign out, and count messages that arrived while the chat was not in front. Point `BASE_URL` and `MERCHANT_PUBLIC_ID` at your own deployment first — the default `10.0.2.2:8080` is how the Android emulator reaches a server on the host machine.

## Release

JitPack builds from a tag on this repository:

```
git tag 0.1.0 && git push origin 0.1.0
```

The version in `livechat/build.gradle.kts` and the tag have to match.
