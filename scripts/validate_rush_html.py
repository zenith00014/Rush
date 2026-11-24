#!/usr/bin/env python3
"""Validate Rush's modular WebView UI, native bridge and Android release settings."""

from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "app/src/main/assets"
HTML_PATH = ASSET_ROOT / "rush.html"
BRIDGE_PATH = ROOT / "app/src/main/java/com/rush/app/RushWebBridge.kt"
ACTIVITY_PATH = ROOT / "app/src/main/java/com/rush/app/MainActivity.kt"
SERVICE_PATH = ROOT / "app/src/main/java/com/rush/app/RushPlaybackService.kt"
MANIFEST_PATH = ROOT / "app/src/main/AndroidManifest.xml"
GRADLE_PATH = ROOT / "app/build.gradle.kts"


class RushHtmlParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids = Counter()
        self.app_containers = 0
        self.script_sources = []
        self.stylesheet_sources = []

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        element_id = values.get("id")
        if element_id:
            self.ids[element_id] += 1
        if tag == "div" and "app" in values.get("class", "").split():
            self.app_containers += 1
        if tag == "script" and values.get("src"):
            self.script_sources.append(values["src"])
        if tag == "link" and "stylesheet" in values.get("rel", "").split():
            self.stylesheet_sources.append(values.get("href", ""))


def fail(message):
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_required(path):
    if not path.is_file():
        fail(f"Missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def main():
    html = read_required(HTML_PATH)
    bridge = read_required(BRIDGE_PATH)
    activity = read_required(ACTIVITY_PATH)
    service = read_required(SERVICE_PATH)
    manifest = read_required(MANIFEST_PATH)
    gradle = read_required(GRADLE_PATH)

    parser = RushHtmlParser()
    parser.feed(html)
    parser.close()

    if parser.app_containers != 1:
        fail(f"Expected one .app container, found {parser.app_containers}")

    for element_id in ("nav", "sc", "v", "mini", "videoMiniDock", "notificationsSheet"):
        if parser.ids[element_id] != 1:
            fail(f"Expected exactly one #{element_id}, found {parser.ids[element_id]}")

    duplicates = {name: count for name, count in parser.ids.items() if count > 1}
    if duplicates:
        fail(f"Duplicate static HTML IDs: {duplicates}")

    expected_scripts = [
        "scripts/app-core.js",
        "scripts/four-page-experience.js",
        "scripts/profile-settings.js",
        "scripts/bootstrap.js",
    ]
    if parser.script_sources != expected_scripts:
        fail(f"WebView script order mismatch: {parser.script_sources}")

    expected_styles = ["styles/rush-base.css", "styles/four-page.css"]
    if parser.stylesheet_sources != expected_styles:
        fail(f"WebView stylesheet order mismatch: {parser.stylesheet_sources}")

    scripts = []
    for source in parser.script_sources:
        asset = (ASSET_ROOT / source).resolve()
        if not asset.is_relative_to(ASSET_ROOT.resolve()):
            fail(f"Script path escapes the asset root: {source}")
        scripts.append(read_required(asset))
    css = []
    for source in parser.stylesheet_sources:
        asset = (ASSET_ROOT / source).resolve()
        if not asset.is_relative_to(ASSET_ROOT.resolve()):
            fail(f"Stylesheet path escapes the asset root: {source}")
        css.append(read_required(asset))

    js = "\n".join(scripts)
    styles = "\n".join(css)

    native_methods = set()
    bridge_lines = bridge.splitlines()
    for index, line in enumerate(bridge_lines):
        if "@JavascriptInterface" not in line:
            continue
        for declaration in bridge_lines[index + 1:index + 8]:
            match = re.search(r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(", declaration)
            if match:
                native_methods.add(match.group(1))
                break

    called_methods = set(re.findall(r"RushNative\.([A-Za-z_][A-Za-z0-9_]*)\s*\(", js))
    missing = sorted(called_methods - native_methods)
    if missing:
        fail(f"JavaScript calls methods missing from RushWebBridge: {missing}")

    required_files = [
        ROOT / "app/src/main/java/com/rush/app/RushDiscoveryEngine.kt",
        ROOT / "app/src/main/java/com/rush/app/FeedPreferences.kt",
        ROOT / "app/src/main/java/com/rush/app/profile/RushProfileStore.kt",
        ROOT / "app/src/main/java/com/rush/app/permissions/RushPermissionManager.kt",
        ROOT / "app/proguard-rules.pro",
        ROOT / "docs/PERFORMANCE.md",
    ]
    for path in required_files:
        read_required(path)

    preferences = (ROOT / "app/src/main/java/com/rush/app/FeedPreferences.kt").read_text(encoding="utf-8")
    profile_store = (ROOT / "app/src/main/java/com/rush/app/profile/RushProfileStore.kt").read_text(encoding="utf-8")
    permission_manager = (ROOT / "app/src/main/java/com/rush/app/permissions/RushPermissionManager.kt").read_text(encoding="utf-8")

    contracts = {
        "four primary destinations": all(
            item in js for item in (
                "['explore','Explore','home']",
                "['search','Search','search']",
                "['subscribed','Subscribed','follow']",
                "['library','Library','library']",
            )
        ),
        "five Explore filters": "const filters=['All','Videos','Music','News','Movies']" in js,
        "horizontal category shelves": "function shelf(" in js and "scroll-snap-type:x proximity" in styles,
        "distinct media artwork ratios": all(value in styles for value in ("aspect-ratio:16/9", "aspect-ratio:1", "aspect-ratio:2/3")),
        "compact landscape news cards": ".news-shelf-card" in styles and "data-news-url" in js,
        "creator avatars can use followed channel artwork": "data-source-thumbnail" in js and "safeUrl(followed&&followed.thumbnail)" in js,
        "Search auto-focus and local history": "input.focus()" in js and "rush_search_history_v1" in js,
        "Search can dismiss the keyboard with Back": "window.rushHandleBack=function()" in js and 'document.activeElement?.id==="q"' in js,
        "Subscribed page has a source rail and combined feed": "source-rail subscriptions" in js and "function renderSubscribedFeed(data)" in js,
        "Library has watchlist and liked collections": "'watchlist','Watchlist'" in js and "'liked','Liked'" in js,
        "local-first profile onboarding": "function renderProfileSetup()" in js and "fun save(context: Context" in profile_store and "startRush();" in (ASSET_ROOT / "scripts/bootstrap.js").read_text(encoding="utf-8"),
        "profile is editable from Library": "function renderEditProfilePage()" in js and "data-open-profile" in js,
        "notification permission controls exist": "getPermissionStatus()" in bridge and "requestNotificationPermission()" in bridge,
        "Android app and PiP settings links exist": "openApplicationSettings()" in bridge and "openPictureInPictureSettings()" in bridge,
        "no unnecessary dangerous permissions are requested": all(token not in manifest for token in ("CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE")),
        "permission manager reports notification state": "POST_NOTIFICATIONS" in permission_manager and "cameraPermissionRequired" in permission_manager,
        "music player uses a rotating CD": "deck-disc" in js and "disc-spin" in styles,
        "music sleep timer is wired to native playback": "data-sleep-timer" in js and "fun setSleepTimer(minutes: Int)" in bridge,
        "video watch page has an inline native player": 'id="videoPlayerHost"' in js and "showNativeVideo(controller, fullscreen = false)" in bridge,
        "video in-app mini-player has a separate host": 'id="videoMiniHost"' in html and "RushNative.updateVideoPosition(r.left,r.top,r.width,r.height,true)" in js,
        "video speed quality and captions are connected": all(token in bridge for token in ("fun setPlaybackSpeed", "fun setVideoQuality", "fun setVideoCaptionsEnabled")),
        "video buffering UI and native state exist": 'id="videoBuffering"' in js and "window.rushVideoBuffering=function" in js and "setShowBuffering" in activity,
        "audio buffering UI exists": 'id="musicBuffering"' in js and "rushMusicBuffering" in bridge,
        "loading skeletons and reduced-motion support exist": "skeleton-shimmer" in styles and "prefers-reduced-motion:reduce" in styles,
        "notification sheet can be opened": 'id="notificationsSheet"' in html and "renderNotifications()" in js,
        "WebView local assets use WebViewAssetLoader": "WebViewAssetLoader" in activity and "shouldInterceptRequest" in activity and "loadUrl(\"https://rush.local/assets/rush.html\")" in activity,
        "no remote web fonts are needed": "fonts.googleapis.com" not in html + js + styles and "fonts.gstatic.com" not in html + js + styles,
        "four-page shell stays inside app viewport": "height:100dvh" in styles or "height:100dvh" in (ASSET_ROOT / "styles/rush-base.css").read_text(encoding="utf-8"),
        "Explore skips DOM rebuilds when item signatures are unchanged": "lastExploreMarkupKey" in js and "markupKey===lastExploreMarkupKey" in js,
        "feed failure preserves cache and provides retry": "data-feed-retry" in js and "cachedCount" in js,
        "WebView renderer loss restores the previous route": "onRenderProcessGone" in activity and "rush.local/assets/rush.html#$route" in activity,
        "WebView rendering is hardware accelerated": 'android:hardwareAccelerated="true"' in manifest and "RENDERER_PRIORITY_IMPORTANT" in activity,
        "media buffering uses separate streaming and local profiles": "setBufferDurationsMsForStreaming(15_000, 60_000, 1_500, 3_000)" in service and "setBufferDurationsMsForLocalPlayback(3_000, 20_000, 500, 1_000)" in service,
        "first-load shimmer is not an infinite feed animation": "animation:skeleton-shimmer 1.3s ease-out 1" in styles,
        "reduced-motion disables decorative animation": "prefers-reduced-motion:reduce" in styles,
        "lyrics ticker updates only the active row": "lastActiveLyricIndex" in js and "while(low<=high)" in js and "querySelectorAll('.lyric-line')" not in js,
        "downloads polling avoids rapid repeated refresh": "},5000);" in js,
    }
    for name, passed in contracts.items():
        if not passed:
            fail(f"UI contract failed: {name}")

    if "RushDiscoveryEngine.scoreFeed" not in bridge or "RushDiscoveryEngine.rankSearch" not in bridge:
        fail("Native bridge must use the shared feed and search-ranking engine")
    if "fun forYouChannels" not in preferences:
        fail("Followed YouTube channels must be eligible as For You candidates")
    if "fun updateVideoPosition(left: Double" not in bridge:
        fail("Native inline video positioning bridge is missing")
    if "RushNative.updateVideoPosition()" in js or "nativeVideoPositionPending" in js:
        fail("Obsolete native video position API remains")
    if "HOME_FEED_CACHE_TTL_MS" not in bridge or "rush_home_feed_v4_" not in bridge:
        fail("Home feed must render a cached payload before network refresh")
    if "Semaphore(5)" not in bridge or "awaitAll()" not in bridge:
        fail("Home feed requests must use bounded concurrent fetching")
    if "NEWS_RSS_CLIENT" not in bridge or "NEWS_ARTICLE_CLIENT" not in bridge:
        fail("News requests must reuse configured HTTP clients")
    if "imageFromRssMarkup(description)" not in bridge:
        fail("RSS thumbnail extraction must inspect embedded image markup")
    if "og:image:secure_url" not in bridge or "twitter:image" not in bridge:
        fail("Publisher thumbnail extraction must support Open Graph and Twitter metadata")
    if "NEWS_THUMBNAIL_CACHE_KEY" not in bridge or "enrichNewsThumbnails" not in bridge:
        fail("Publisher thumbnails must be cached and reused")
    if "resolvePlaybackMedia(videoId, audioOnly = true" not in bridge:
        fail("Music playback must request audio-only media")
    if "override fun onTaskRemoved(rootIntent: android.content.Intent?)" not in service:
        fail("Background playback service task-removal policy is missing")
    if "webView.pauseTimers()" not in activity or "webView.resumeTimers()" not in activity:
        fail("WebView timers must pause in the background and resume in foreground")
    if "rushWebBridge.release()" not in activity:
        fail("The WebView bridge controller must be released when the Activity stops")
    if "supportsPictureInPicture=\"true\"" not in manifest:
        fail("Android Picture-in-Picture must be declared in the manifest")
    if "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" not in manifest:
        fail("Media playback foreground-service permission is missing")
    if "android.permission.POST_NOTIFICATIONS" not in manifest:
        fail("Notification runtime permission must be declared for Android 13+")
    if "targetSdk = 36" not in gradle:
        fail("New Play submissions must target API 36 or higher")
    if 'androidx.webkit:webkit:1.18.0' not in gradle:
        fail("WebViewAssetLoader dependency is missing")
    if "robolectricSdkRuntime" not in gradle or "robolectric.offline" not in gradle:
        fail("Robolectric tests must use the prefetched offline Android runtime")
    if "android-all-instrumented-15-robolectric-12650502-i7.jar" not in gradle:
        fail("Robolectric SDK prefetch must match the API 35 instrumented runtime")
    if "RushProfileStore.save" not in bridge or "RushPermissionManager.notificationState" not in bridge:
        fail("Native local profile and permission settings must be exposed to the WebView")
    if "fun setVideoFullscreen(enabled: Boolean)" not in bridge or "activity.setNativeVideoFullscreen(enabled)" not in bridge:
        fail("Fullscreen video handoff is missing")
    if "android:usesCleartextTraffic=\"false\"" not in manifest:
        fail("Cleartext traffic must remain disabled")
    if "minBufferMs" in service:
        fail("Keep Media3 load-control configuration compatible with the pinned Java/Kotlin API")

    with tempfile.NamedTemporaryFile("w", suffix=".js", encoding="utf-8") as handle:
        handle.write(js)
        handle.flush()
        result = subprocess.run(
            ["node", "--check", handle.name],
            text=True,
            capture_output=True,
            check=False,
        )
    if result.returncode != 0:
        print(result.stdout, file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        fail("Combined JavaScript syntax or global binding check failed")

    print(
        "Rush validation passed: "
        f"{parser.app_containers} app shell, {len(parser.ids)} unique HTML IDs, "
        f"{len(parser.script_sources)} JS modules, {len(parser.stylesheet_sources)} CSS modules, "
        f"{len(called_methods)} native bridge methods, and Android API 36 configuration."
    )


if __name__ == "__main__":
    main()
