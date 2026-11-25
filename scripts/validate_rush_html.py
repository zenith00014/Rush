#!/usr/bin/env python3
"""Validate the Figma-led Rush WebView surface and Android playback configuration."""

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
SEARCH_PATH = ROOT / "app/src/main/java/com/rush/app/LiveYouTubeSearch.kt"
MANIFEST_PATH = ROOT / "app/src/main/AndroidManifest.xml"
GRADLE_PATH = ROOT / "app/build.gradle.kts"


class RushHtmlParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids = Counter()
        self.app_containers = 0
        self.script_sources = []
        self.stylesheet_sources = []
        self.lang = ""
        self.title = False
        self.landmarks = set()

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if values.get("id"):
            self.ids[values["id"]] += 1
        if tag == "div" and "app" in values.get("class", "").split():
            self.app_containers += 1
        if tag == "script" and values.get("src"):
            self.script_sources.append(values["src"])
        if tag == "link" and "stylesheet" in values.get("rel", "").split():
            self.stylesheet_sources.append(values.get("href", ""))
        if tag == "html":
            self.lang = values.get("lang", "")
        if tag in {"header", "nav", "main"}:
            self.landmarks.add(tag)

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)

    def handle_data(self, data):
        if getattr(self, "_in_title", False) and data.strip():
            self.title = True

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)

    def handle_endtag(self, tag):
        if tag == "title":
            self._in_title = False


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
    download_worker = read_required(ROOT / "app/src/main/java/com/rush/app/OfflineDownloads.kt")
    search_client = read_required(SEARCH_PATH)
    manifest = read_required(MANIFEST_PATH)
    gradle = read_required(GRADLE_PATH)
    read_required(ROOT / "DESIGN.md")

    parser = RushHtmlParser()
    parser.feed(html)
    parser.close()

    if parser.app_containers != 1:
        fail(f"Expected one .app container, found {parser.app_containers}")
    for element_id in (
        "topAppBar", "menuToggle", "searchForm", "globalSearch", "nav", "sidebar",
        "layoutBackdrop", "sc", "v", "videoMiniDock", "videoMiniHost",
        "notificationsPopover", "userPopover", "dialogRoot",
    ):
        if parser.ids[element_id] != 1:
            fail(f"Expected exactly one #{element_id}, found {parser.ids[element_id]}")
    duplicates = {name: count for name, count in parser.ids.items() if count > 1}
    if duplicates:
        fail(f"Duplicate static HTML IDs: {duplicates}")
    if not parser.lang or not parser.landmarks.issuperset({"header", "nav", "main"}):
        fail("The app shell needs a language and header, nav and main landmarks.")
    if "<title>Rush</title>" not in html:
        fail("The app shell must declare its Rush document title.")

    expected_scripts = ["scripts/figma-experience.js"]
    expected_styles = ["styles/figma-redesign.css"]
    if parser.script_sources != expected_scripts:
        fail(f"Unexpected active WebView scripts: {parser.script_sources}")
    if parser.stylesheet_sources != expected_styles:
        fail(f"Unexpected active WebView stylesheets: {parser.stylesheet_sources}")

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
    lines = bridge.splitlines()
    for index, line in enumerate(lines):
        if "@JavascriptInterface" not in line:
            continue
        for declaration in lines[index + 1:index + 8]:
            match = re.search(r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(", declaration)
            if match:
                native_methods.add(match.group(1))
                break
    called_methods = set(re.findall(r"RushNative\.([A-Za-z_][A-Za-z0-9_]*)\s*\(", js))
    missing = sorted(called_methods - native_methods)
    if missing:
        fail(f"JavaScript calls methods missing from RushWebBridge: {missing}")

    required_methods = {
        "loadVideoFeed", "loadCurrentPlayback", "recordSearchQuery", "searchVideos",
        "searchChannels", "saveFeedTopics", "loadUpNext", "saveLibraryItem",
        "removeSavedLibraryItem", "setRecommendationFeedback", "followSource",
        "unfollowSource", "loadChannelUploads", "loadLibrary", "playVideo",
        "playLibraryItem", "clearPlaybackHistory", "createLibraryPlaylist",
        "deleteLibraryPlaylist", "playLibraryPlaylist", "setVideoQuality",
        "setVideoCaptionsEnabled", "setVideoFullscreen", "getPermissionStatus",
        "openApplicationSettings", "requestNotificationPermission",
        "openPictureInPictureSettings", "updateVideoPosition", "closeVideo",
    }
    if not required_methods.issubset(called_methods):
        fail(f"Missing expected video and library bridge actions: {sorted(required_methods - called_methods)}")

    navigation_routes = {
        "home", "explore", "shorts", "tv", "history", "watch-later",
        "liked", "playlists", "collections", "subscriptions",
    }
    for route in navigation_routes:
        if not re.search(r"id:\s*'" + re.escape(route) + r"'", js):
            fail(f"Figma sidebar destination is missing: {route}")
    for token in (
        "In Case You Missed", "Videos for you", "Shorts", "Your topics",
        "Search videos and channels", "Watch Later", "Liked Videos",
        "Subscriptions", "History", "Playlists", "Collections", "TV Mode",
    ):
        if token not in js and token not in html:
            fail(f"Figma reference surface is missing: {token}")
    for token in (
        "--yt-bg:#111111", "--yt-panel:#1f1f1f", "--yt-red:#ff0033",
        "aspect-ratio:16/9", "aspect-ratio:9/16", ".sidebar.collapsed",
        ".sidebar.mobile-open", "@media(max-width:760px)",
        "prefers-reduced-motion:reduce", ":focus-visible",
    ):
        if token not in styles:
            fail(f"Figma design or responsive contract is missing: {token}")
    if "height:100dvh" not in styles:
        fail("The app layout must fit the visible app viewport.")
    if "fonts.googleapis.com" in html + js + styles or "fonts.gstatic.com" in html + js + styles:
        fail("The native WebView must not depend on remote web fonts.")
    if any(token in html + js for token in ("data-news", "News", "newsCard", "renderNews")):
        fail("News is outside the Figma scope and must not be present in the active UI.")
    if any(token in html + js for token in ("renderMusicPage", "deck-disc", "data-music-search", "Movies")):
        fail("Music and movie features are outside the Figma scope and must not be present in the active UI.")
    if any(token in html for token in ("scripts/app-core.js", "scripts/four-page-experience.js",
                                       "scripts/profile-settings.js", "scripts/bootstrap.js")):
        fail("The previous prototype modules must not be loaded by the rebuilt app.")

    if "loadVideoFeed(mode: String)" not in bridge or "publish(\"rushVideoFeedResults\"" not in bridge:
        fail("The video-only native feed bridge is missing.")
    if "RushDiscoveryEngine.scoreFeed" not in bridge or "RushDiscoveryEngine.rankSearch" not in bridge:
        fail("Native feed and search paths must use the shared discovery engine.")
    if "fun forYouChannels" not in read_required(ROOT / "app/src/main/java/com/rush/app/FeedPreferences.kt"):
        fail("Followed YouTube channels must be eligible for recommendations.")
    if "HOME_FEED_NETWORK_SLOTS = Semaphore(5)" not in bridge:
        fail("The native feed must use the bounded shared network budget.")
    if "searchResultCache" not in search_client or "inFlightSearches" not in search_client:
        fail("Repeated searches must use the in-memory search cache and request coalescer.")

    if "WebViewAssetLoader" not in activity or "shouldInterceptRequest" not in activity:
        fail("WebView assets must be served through WebViewAssetLoader.")
    if 'loadUrl("https://rush.local/assets/rush.html")' not in activity:
        fail("The native Activity must load the local Rush app shell.")
    if "window.rushHandleBack" not in activity or "window.rushAppForeground" not in activity:
        fail("Native Back and foreground lifecycle callbacks must be supported by the web shell.")
    if "window.rushUpdateVideoPosition" not in activity:
        fail("Native inline video positioning callback is missing.")
    if "showVideoPlaybackError" not in activity or 'setPositiveButton("Retry")' not in activity:
        fail("Native video playback errors must offer a retry.")
    if "rushWebBridge.release()" not in activity:
        fail("The WebView bridge controller must be released when the Activity stops.")
    if "webView.pauseTimers()" not in activity or "webView.resumeTimers()" not in activity:
        fail("WebView timers must pause in the background and resume in foreground.")
    if "settings.textZoom=(resources.configuration.fontScale*100f).toInt().coerceAtLeast(50)" not in activity:
        fail("Android text scaling must be passed to the WebView.")
    if "settings.setSupportZoom(true)" not in activity or "settings.builtInZoomControls=true" not in activity:
        fail("WebView pinch zoom must remain available.")

    if any(token in manifest for token in (
        "CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION",
        "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
    )):
        fail("The app manifest contains permissions not required by the current UI.")
    if "android:hardwareAccelerated=\"true\"" not in manifest:
        fail("Android hardware acceleration must remain enabled.")
    if "supportsPictureInPicture=\"true\"" not in manifest:
        fail("Android Picture-in-Picture must be declared.")
    if "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" not in manifest:
        fail("Media playback foreground-service permission is missing.")
    if "android.permission.POST_NOTIFICATIONS" not in manifest:
        fail("Notification permission must be declared for Android 13+.")
    if "android:usesCleartextTraffic=\"false\"" not in manifest:
        fail("Cleartext traffic must remain disabled.")
    if "targetSdk = 36" not in gradle:
        fail("The Android release target must be API 36.")
    if "androidx.webkit:webkit:1.18.0" not in gradle:
        fail("WebViewAssetLoader dependency is missing.")
    if "androidx.work:work-runtime-ktx:2.12.0" not in gradle:
        fail("WorkManager must use the pinned 2.12 stable release.")
    if "FOREGROUND_SERVICE_MEDIA_PROCESSING" not in manifest or "dataSync|mediaProcessing" not in manifest:
        fail("WorkManager foreground service types are incomplete.")
    if "FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING" not in download_worker:
        fail("Media conversion must use the mediaProcessing foreground type.")
    if "setBufferDurationsMsForStreaming(15_000, 60_000, 1_500, 3_000)" not in service:
        fail("Media streaming must keep the configured buffer profile.")
    if "setBufferDurationsMsForLocalPlayback(3_000, 20_000, 500, 1_000)" not in service:
        fail("Local playback must keep the configured buffer profile.")
    if "override fun onTaskRemoved(rootIntent: android.content.Intent?)" not in service:
        fail("The background playback service task-removal policy is missing.")
    if "-dontwarn java.beans.**" not in read_required(ROOT / "app/proguard-rules.pro"):
        fail("The narrow JVM adapter R8 rule is missing.")
    if "minBufferMs" in service:
        fail("Media3 load-control configuration must match the pinned API.")

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
        fail("The active JavaScript failed the Node syntax check.")

    print(
        "Rush Figma validation passed: "
        f"{parser.app_containers} app shell, {len(parser.ids)} unique HTML IDs, "
        f"{len(parser.script_sources)} active JS module, {len(parser.stylesheet_sources)} active stylesheet, "
        f"{len(called_methods)} native bridge actions, video-only discovery, and Android API 36 configuration."
    )


if __name__ == "__main__":
    main()
