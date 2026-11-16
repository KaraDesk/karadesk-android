# KaraDesk for Android

Add the [KaraDesk](https://karadesk.com) messenger to your Android app. Your users can chat with your team, get answers from your help center, and pick up their conversations on any device, all inside your app.

- **Native**: built with Kotlin, Jetpack Compose and Material 3, with system back gestures, the system photo picker and edge-to-edge layouts.
- **On brand**: uses the colors, logo, greeting and home cards you set in KaraDesk, in light and dark mode.
- **Live**: new replies, typing indicators and unread counts update in real time.
- **Complete**: conversations with photos and files, satisfaction ratings, your full help center, and push notifications.

## Requirements

- Android 8.0 (API 26) or later
- Kotlin 2.0 or later
- Android Gradle Plugin 8 or later, with compileSdk 35 or later

## Installation

KaraDesk is published on Maven Central. Add it to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.karadesk:karadesk-android:1.0.1")
}
```

Make sure `mavenCentral()` is in your repositories. It is in every new Android Studio project.

## Quick start

### 1. Initialize

Find your space ID and Android app ID in KaraDesk under **Settings → Apps**, then initialize KaraDesk once in your `Application`:

```kotlin
import com.karadesk.KaraDesk

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KaraDesk.initialize(this, spaceId = "YOUR_SPACE_ID", appId = "YOUR_APP_ID")
    }
}
```

Register it in your `AndroidManifest.xml` with `android:name=".MyApplication"` if you don't have an `Application` class yet.

### 2. Open the messenger

```kotlin
KaraDesk.present(context)
```

That's it. Until someone signs in, people are treated as anonymous visitors, and anything they send moves to their account when they do.

## Signing users in

When someone signs in to your app, tell KaraDesk who they are so their conversations follow them across devices:

```kotlin
KaraDesk.login(KaraDeskUser(id = user.id, email = user.email, name = user.fullName))
```

`login` is a suspending function, so call it from a coroutine, for example `lifecycleScope.launch { ... }`. Provide at least an `id` or an `email`. You can also set `phone`, `avatarUrl` and `signedUpAt`. Call `login` again on each launch while the user stays signed in.

When the user's details change, update just what changed. Everything you leave out stays the same:

```kotlin
KaraDesk.updateUser(KaraDeskUser(name = "Evangelina Ruiz", avatarUrl = newAvatarUrl))
```

When they sign out, start a fresh anonymous visitor so the next person on the device can't see their conversations:

```kotlin
KaraDesk.logout()
```

### Identity verification

Identity verification stops anyone from impersonating your users. Turn it on for your app in KaraDesk, then have **your server** compute an HMAC-SHA256 of the user's id (or their email if they have no id) with the identity verification secret, and pass it as `userHash`:

```kotlin
KaraDesk.login(KaraDeskUser(id = user.id, email = user.email, userHash = hashFromYourServer))
```

> [!IMPORTANT]
> Never ship your identity verification secret inside your app. Compute the hash on your server.

## Presenting the messenger

`present` opens the messenger on its home screen, just like your help button on the web. You can also open it somewhere specific:

```kotlin
KaraDesk.present(context, KaraDesk.Destination.Messages)
KaraDesk.present(context, KaraDesk.Destination.NewConversation("I need help with order #1042"))
KaraDesk.present(context, KaraDesk.Destination.Conversation(1234))
KaraDesk.present(context, KaraDesk.Destination.HelpCenter)
KaraDesk.present(context, KaraDesk.Destination.Article(56))

KaraDesk.dismiss()
```

If the messenger is already open, `present` moves it to the new destination. The system back gesture moves back through the messenger, and closes it from the top level.

### Standalone

By default the messenger opens with its Home, Messages and Help tabs. To show only one part of it, present it standalone:

```kotlin
KaraDesk.present(context, KaraDesk.Destination.HelpCenter, KaraDesk.PresentationStyle.STANDALONE)
KaraDesk.present(context, KaraDesk.Destination.Article(56), KaraDesk.PresentationStyle.STANDALONE)
```

A standalone help center still lets people browse and search articles. A standalone article or conversation opens on its own, with nothing to navigate back to.

## Unread conversations

Show a badge when the team has replied. `unreadConversationCount` is a `StateFlow`:

```kotlin
val unread by KaraDesk.unreadConversationCount.collectAsState()

BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
    IconButton(onClick = { KaraDesk.present(context) }) { Icon(Icons.Outlined.HelpOutline, "Help") }
}
```

Outside Compose, collect it from a lifecycle-aware coroutine.

## In-app message previews

When your team replies while the messenger is closed, a preview card slides up from the bottom of your app. Tapping it opens the conversation. To turn it off:

```kotlin
KaraDesk.showsInAppMessages = false
```

## Push notifications

KaraDesk can send a push notification when your team replies while the user is away from your app. Tapping it opens the conversation. KaraDesk uses your app's own Firebase project, so add Firebase Cloud Messaging to your app first.

### 1. Add your Firebase service account to KaraDesk

In the Firebase console, open **Project settings → Service accounts** and generate a new private key. In KaraDesk, open **Settings → Apps**, choose your Android app and upload the JSON file under **Push notifications** with your app's package name. KaraDesk checks it with Firebase before saving it.

### 2. Pass the token and messages to KaraDesk

```kotlin
class MyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        KaraDesk.setDeviceToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (KaraDesk.handlePush(this, message.data)) {
            return
        }

        // Handle your own messages.
    }
}
```

Also pass the current token on launch, for example after `KaraDesk.initialize`:

```kotlin
FirebaseMessaging.getInstance().token.addOnSuccessListener(KaraDesk::setDeviceToken)
```

KaraDesk keeps the token linked to whoever is signed in. It moves to the new user after `login`, and stops after `logout`.

### 3. That's it

When your app is in the background, Android shows KaraDesk notifications for you. Tapping one opens your app and KaraDesk opens the conversation automatically. While your app is open, `handlePush` shows the reply as an in-app preview, or nothing if the messenger is already on screen.

KaraDesk never asks for notification permission. On Android 13 and later, request `POST_NOTIFICATIONS` when it makes sense in your app.

Notifications KaraDesk posts itself use your app icon. To use a monochrome notification icon instead, add this to your manifest's `<application>`:

```xml
<meta-data
    android:name="com.karadesk.notification_icon"
    android:resource="@drawable/ic_notification" />
```

If you'd rather handle taps yourself, set `KaraDesk.opensConversationsFromNotifications = false` before calling `initialize`, then call `KaraDesk.handleNotificationTap(context, intent)` from your activity. Use `KaraDesk.isKaraDeskPush(data)` anywhere you need to tell KaraDesk messages apart from your own.

## Errors

`login` and `updateUser` throw a `KaraDeskException`:

| Error | Meaning |
| --- | --- |
| `NotInitialized` | `KaraDesk.initialize` hasn't been called yet. |
| `NotLoggedIn` | `updateUser` was called before `login`. |
| `IdentityVerificationFailed` | The user hash is missing or doesn't match. |
| `Unavailable` | The messenger is turned off for this audience, or the KaraDesk subscription is inactive. |
| `Network` | The device couldn't reach KaraDesk. |
| `Server` | KaraDesk returned an error. |

## Privacy

KaraDesk doesn't track users across apps, doesn't use the advertising ID, and only collects what's needed to provide support. Use this list for your Google Play data safety form:

- The name, email, phone and user id you pass to `login`
- Messages, photos and files your users send
- Device model, Android version, app version, language and time zone
- The Firebase messaging token, if you pass it to `setDeviceToken`

Picking photos and files uses the system photo picker and document picker, so your app doesn't need storage or media permissions. KaraDesk only adds the `INTERNET` and `ACCESS_NETWORK_STATE` permissions.

## Support

Questions or problems? Reach us through the messenger at [karadesk.com](https://karadesk.com).

## License

The KaraDesk Android SDK is available under the [Apache License 2.0](LICENSE). Copyright 2026 Boothby Lab LLC.

KaraDesk and the KaraDesk logo are trademarks of Boothby Lab LLC.
