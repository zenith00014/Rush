# Rush performance and playback strategy

## Runtime split

Rush uses Kotlin + AndroidX Media3 for latency-sensitive Android work, and the Android System WebView's Chromium/V8 runtime for the local UI. This is intentional: the browser runtime is optimised for DOM/CSS layout and interaction, while Media3 owns stream selection, decoding, buffering and the native audio/video surface.

Do not introduce another UI runtime, a large JavaScript framework, Rust UI code or a second media pipeline without a measured bottleneck and a benchmark proving the change helps target Android devices. Native platform code should remain the default for Android platform responsibilities.

## Startup and feed

- Use WebViewAssetLoader to load every first-party HTML, CSS and JS module from packaged app assets on the local HTTPS origin.
- Keep all first-party UI code offline-capable and local; do not download fonts, JavaScript frameworks or CSS at app launch.
- Load the four small UI modules in dependency order. Keep `rush.html` as a shell, not a script/CSS dump.
- Read the local profile and feed preferences synchronously from app-private storage before making optional network calls.
- Render the last cached feed before refreshing it. A refresh must not blank or re-create visible cards when the item signature has not changed.
- Keep remote source fan-out bounded with one shared five-request budget across both For You and Following, rather than a separate budget per feed. Enrich publisher images after first content is visible.
- Load the first visible Explore artwork with high priority; keep the remaining media artwork lazy. Do not request the optional movie shelf until the user opens Movies.
- Coalesce identical in-flight Home feed requests by profile and mode, and coalesce repeat YouTube searches with a short in-memory cache.
- Set media artwork to lazy-load and decode asynchronously, reserving dimensions/aspect ratios to avoid layout shifts. Eager-load at most the first image on the visible Explore surface.
- Avoid parsing the same markup and rebuilding the same DOM on every refresh. Explore compares a compact item signature and leaves unchanged DOM intact.

## Playback

The native player uses `DefaultLoadControl` with separate streaming and local playback policies:

| Buffer parameter | Streaming | Local media |
|---|---:|---:|
| Minimum buffer | 15 s | 3 s |
| Maximum buffer | 60 s | 20 s |
| Initial start / seek | 1.5 s | 0.5 s |
| Resume after rebuffer | 3 s | 1 s |
| Back buffer | 5 s | 5 s |

These are starting configuration values, not a promise of a fixed startup time or no buffering. Media3 defines these values as desired buffered durations; actual playback still depends on source availability, network throughput, selected resolution, device decoding and the source server. Test on real devices and real networks before tuning further.

- Keep adaptive track selection enabled rather than forcing the highest bitrate.
- Use the hardware-backed native PlayerView surface for video; do not render video frames in canvas or DOM.
- Use `PlayerView.SHOW_BUFFERING_WHEN_PLAYING` and JS status overlays for connecting, buffering and recoverable errors.
- Preserve a visible last frame during media transitions where appropriate.
- Keep a working retry action on recoverable media failures.
- Cache only content permitted by its provider's terms and the relevant user permissions.

## Rendering and motion

- Keep main navigation and key player controls immediately responsive.
- Keep CSS motion short and transform/opacity-based; avoid animating large layout dimensions.
- Use `prefers-reduced-motion` to turn off decorative motion. Loading indicators may remain visible but should not animate when reduced motion is requested.
- One-shot skeleton shimmer is allowed during first load; do not run an infinite shimmer across the whole feed.
- Use touch targets of at least 48dp where possible, semantic labels and system font scaling.
- Hardware acceleration is enabled for the Android host and WebView.
- When Android reports renderer process loss, recreate the WebView and restore its current route instead of crashing the app.

## Validation and device benchmarks

CI validates file/module structure, bridge references, the Android configuration and JavaScript syntax. It cannot establish smoothness or playback quality on physical devices.

Before any release candidate:
1. Test first launch with empty cache, then repeat with warm cache.
2. Test 4G with strong/weak signal, Wi-Fi, network switching, packet loss, airplane mode and reconnect.
3. Test 360p, 480p, 720p and automatic track selection; test seek and resume after buffer depletion.
4. Test app background/foreground, notification controls, PiP, WebView process recreation and screen rotation.
5. Record time-to-first-frame, rebuffer count/time, dropped frames, app startup and scroll jank on a low/mid-range Android device and a higher-end device.
6. Compare measurements before changing buffer thresholds or introducing new dependencies.

Never label Rush "zero-buffer" or "glitch-free" based only on a successful compilation or CI run.
