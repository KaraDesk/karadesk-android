# Changelog

All notable changes to the KaraDesk Android SDK are documented here. This project follows [Semantic Versioning](https://semver.org).

## 1.0.1

- Works in more apps. The SDK now builds with Android Gradle Plugin 8 and Kotlin 2.0 or later and compileSdk 35 or later, instead of requiring Android Gradle Plugin 9.1, Kotlin 2.4 and compileSdk 37. It depends on the oldest library versions it needs (Compose BOM 2024.12, OkHttp 4.12, Coil 3.0), so your app's newer versions are used when you have them.

## 1.0.0

- Errors are explained instead of leaving the messenger spinning. Requests fail right away when the device is offline, and every request now has an overall time limit, so sending a message never hangs.
- A "No internet connection" banner shows at the top of the messenger while the device is offline. When the connection returns, the messenger starts again if it couldn't before, or refreshes and reconnects if it was already open.
- Screens that fail to load (starting the messenger, the message list, a conversation, the help center, a collection or an article) explain what went wrong and offer Try again when retrying can help. They no longer show "No messages", "There are no articles yet" or "No articles match your search" when the real problem was a failed request.
- When the messenger is turned off for your space or audience, or your KaraDesk subscription is inactive, users see "Messaging isn't available" instead of a technical message, and the reason is written to Logcat for you. If that happens while the messenger is open, it switches to the same screen.
- A message that fails to send stays in the composer with a "Not sent" notice explaining why, which can be dismissed. Failed attachment uploads can be retried.
- Server errors show "Something went wrong on our end. Please try again." instead of the server's raw error text.
- Conversations show a spinner while their history loads.
- `KaraDeskException` adds `isOffline` and `isRetryable`, and `KaraDeskException.Network` adds `offline` and `timedOut`, so you can tell whether the device is offline, the request timed out, or KaraDesk couldn't be reached.

## 0.1.1

- Your messages now line up on the right edge of the conversation, however short they are.
- Tighter spacing between the teammate's name and their status or reply time at the top of a conversation.
- The project builds with Android Studio's bundled JDK.

## 0.1.0

- The KaraDesk messenger for Android, with the same features as the iOS SDK: a branded home screen with your greeting, logo, team and home cards, conversations with photos and files, satisfaction ratings, and your full help center with search.
- Sign users in with `login`, update them with `updateUser` and start a fresh visitor with `logout`, including identity verification.
- Open the messenger anywhere with `present`, with or without tabs.
- Live replies, typing indicators and an `unreadConversationCount` flow for badges.
- In-app previews of new replies while the messenger is closed.
- Push notifications through your Firebase project, with conversations opening automatically when a notification is tapped.
- Light and dark mode, edge-to-edge layouts and the system back gesture.
