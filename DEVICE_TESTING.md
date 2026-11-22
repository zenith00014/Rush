# Rush device validation

The CI pipeline checks the WebView/bridge contract, runs Robolectric unit tests and builds the debug APK. The following integration checks still require a physical Android device or Android emulator with network access.

## Install

Install `app-debug.apk` from the CI artifact. The app uses application ID `com.rush.app`, targets Android API 36 and supports Android API 26 and later.

## Playback

- Search for a normal video and start playback from the result.
- Confirm video and audio both play, and test pause, resume, seek and quality changes.
- Open a music result and confirm audio starts; test background playback with the screen off and the notification/media controls.
- Try a second video that offers adaptive formats. Confirm playback recovers if the initial direct stream is unavailable.

## Search and channels

- Search for a broad topic rather than an exact title.
- Search public YouTube playlists, open one, inspect its videos, queue the first 50 tracks, and play them from Library → Queue.
- Search for a channel, open its details, confirm description/subscriber/upload information where available, and open a recent upload.
- Subscribe to the channel, then refresh the subscriptions feed.

## Offline downloads

- Download one video in its source-native format and one music item.
- Confirm progress, cancellation, resume and deletion.
- Try MP3 conversion for an audio item.
- Enable airplane mode and confirm completed files still play offline.
- Open Library → Downloads from the notification and confirm the app opens the Downloads view.
- Verify the notification permission prompt appears only when a download is first requested; downloads must still work if notifications are denied.

## Local library and layout

- Save a video and a music result, then confirm both appear under Library → Saved.
- Confirm playback adds the item to Library → History, and clear/remove actions update it without clearing Saved.
- Create a local playlist, add saved items, remove an item, then use Play All and confirm the queue order.
- Add, reorder, remove and clear queue items; confirm the queue persists after restarting Rush.
- Confirm Library → Downloads shows progress updates while a WorkManager download runs.
- Open Search and the player with the keyboard visible; confirm inputs and controls remain above the IME and system bars on Android 15/16.
- Rotate the device during playback and verify the native video surface stays aligned; test immersive fullscreen and Picture-in-Picture.

## Diagnostics

When something fails, record the exact search term or video URL, the action taken, and the on-screen error/status. With USB debugging enabled, capture relevant log messages with:

```bash
adb logcat -d -v threadtime | grep -iE "Rush|Sabr|NewPipe|ExoPlayer|MediaCodec"
```

Streaming depends on unofficial YouTube endpoints and extractor compatibility. Device testing is required before treating playback, SABR negotiation, background operation or downloads as production-verified.
