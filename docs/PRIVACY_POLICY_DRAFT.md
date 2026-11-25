# Rush privacy policy — draft for review

**This is a draft. Do not submit it to Google Play or publish it until the developer identity, contact details, data flows and final dependency audit have been completed.**

- App: Rush for Android
- Developer/legal entity: [insert legal developer or company name]
- Privacy contact: [insert monitored email address]
- Effective date: [insert date]
- Public policy URL: [insert globally accessible HTTPS URL]

## Information stored on the device

Rush currently stores the following information locally for app features:

- Profile display name and optional handle.
- Selected interests and followed channels, publishers and musicians.
- Recent search terms.
- Saved videos/music metadata, playlists, queue and playback history.
- App-private downloaded files and media preferences.
- Notification and playback preferences.

The first-run profile is a local profile; it does not create a Rush online account. Local information is stored in Android app-private preferences/storage and WebView local storage. It is removed when app data is cleared or the app is uninstalled, subject to Android backup and device behaviour. Rush currently disables Android app backup in its manifest.

## Information sent to content providers

When a user searches, refreshes a feed, requests a video/music result or opens a news item, Rush sends the relevant search term, topic or content identifier to the network service needed to return that result. The current implementation uses unofficial YouTube extraction/search components and Google News RSS/publisher article requests. Network providers may receive the user's IP address, request headers, user-agent information, timestamps and the requested query or content identifier.

Images and article metadata may be requested from publisher websites or image hosts. The app does not control how those providers process their own requests. Users should review the applicable third-party privacy policies.

## Permissions

Rush uses internet access for discovery and playback. Android notification permission is optional and is requested on Android 13+ when a user chooses notification functionality. Picture-in-Picture is controlled by Android system settings.

The current app does not require camera, microphone, contacts, precise location or shared-storage access for its declared features. Downloads are stored in app-private storage.

## Sharing and analytics

The current Gradle dependency list does not declare an advertising or analytics SDK. Content searches and media requests are nevertheless sent to the external providers described above. Re-check the complete dependency graph, transitive SDKs, crash reporting and any future integrations before publishing this policy or completing the Play Data safety form.

## Retention and deletion

Local profile, preferences, searches, library metadata and local media are retained on the device until the user removes them, clears app data or uninstalls Rush. Any server-side data retention is governed by the third-party provider receiving the request; Rush does not control those provider systems.

## Children's privacy

[Insert the developer's intended age rating and child-directed use statement after completing the Play Console target-audience and content-rating questionnaires. Do not claim the app is designed for children unless it is.]

## Security

Rush uses HTTPS for remote content requests, disables cleartext network traffic and serves local WebView assets through AndroidX WebViewAssetLoader. No security measure is perfect; users should keep Android and Rush updated.

## Contact

For privacy questions or data concerns, contact: [insert monitored privacy contact email and legal developer address].
