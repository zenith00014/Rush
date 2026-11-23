#!/usr/bin/env python3
"""Validate the embedded Rush WebView document and its JavaScript/native bridge."""

from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
import re
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
HTML_PATH = ROOT / "app/src/main/assets/rush.html"
BRIDGE_PATH = ROOT / "app/src/main/java/com/rush/app/RushWebBridge.kt"
ACTIVITY_PATH = ROOT / "app/src/main/java/com/rush/app/MainActivity.kt"
GRADLE_PATH = ROOT / "app/build.gradle.kts"


class RushHtmlParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids = Counter()
        self.app_containers = 0
        self.scripts = []
        self._inside_script = False
        self._script_parts = []

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        element_id = values.get("id")
        if element_id:
            self.ids[element_id] += 1
        if tag == "div" and "app" in values.get("class", "").split():
            self.app_containers += 1
        if tag == "script":
            self._inside_script = True
            self._script_parts = []

    def handle_data(self, data):
        if self._inside_script:
            self._script_parts.append(data)

    def handle_endtag(self, tag):
        if tag == "script" and self._inside_script:
            self.scripts.append("".join(self._script_parts))
            self._inside_script = False
            self._script_parts = []


def fail(message):
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main():
    if not HTML_PATH.is_file():
        fail(f"Missing WebView asset: {HTML_PATH}")
    if not BRIDGE_PATH.is_file():
        fail(f"Missing native bridge: {BRIDGE_PATH}")
    if not ACTIVITY_PATH.is_file():
        fail(f"Missing Android activity: {ACTIVITY_PATH}")
    if not GRADLE_PATH.is_file():
        fail(f"Missing app Gradle configuration: {GRADLE_PATH}")

    html = HTML_PATH.read_text(encoding="utf-8")
    bridge = BRIDGE_PATH.read_text(encoding="utf-8")
    activity = ACTIVITY_PATH.read_text(encoding="utf-8")
    gradle = GRADLE_PATH.read_text(encoding="utf-8")
    parser = RushHtmlParser()
    parser.feed(html)
    parser.close()

    if parser.app_containers != 1:
        fail(f"Expected one .app container, found {parser.app_containers}")

    required_ids = ("nav", "sc", "v", "mini")
    for element_id in required_ids:
        if parser.ids[element_id] != 1:
            fail(f"Expected exactly one #{element_id}, found {parser.ids[element_id]}")

    duplicates = {name: count for name, count in parser.ids.items() if count > 1}
    if duplicates:
        fail(f"Duplicate static HTML IDs: {duplicates}")

    if not parser.scripts:
        fail("No inline JavaScript was found")

    js = "\n".join(parser.scripts)
    native_methods = set()
    bridge_lines = bridge.splitlines()
    for index, line in enumerate(bridge_lines):
        if "@JavascriptInterface" not in line:
            continue
        for declaration in bridge_lines[index + 1:index + 5]:
            if "fun " in declaration:
                method_name = declaration.split("fun ", 1)[1].split("(", 1)[0].strip()
                if method_name:
                    native_methods.add(method_name)
                break

    called_methods = set(re.findall(
        "RushNative[.]([A-Za-z_][A-Za-z0-9_]*)[(]",
        js,
    ))
    missing = sorted(called_methods - native_methods)
    if missing:
        fail(f"JavaScript calls methods missing from RushWebBridge: {missing}")

    engine_path = ROOT / "app/src/main/java/com/rush/app/RushDiscoveryEngine.kt"
    prefs_path = ROOT / "app/src/main/java/com/rush/app/FeedPreferences.kt"
    if not engine_path.is_file() or not prefs_path.is_file():
        fail("Missing shared discovery engine or feed preferences")
    engine = engine_path.read_text(encoding="utf-8")
    prefs = prefs_path.read_text(encoding="utf-8")
    if "RushDiscoveryEngine.scoreFeed" not in bridge or "RushDiscoveryEngine.rankSearch" not in bridge:
        fail("Native bridge is not integrated with shared feed and search ranking")
    if "fun forYouChannels" not in prefs:
        fail("For You channel eligibility must use followed sources without a second opt-in")
    if "ADD TO FOR YOU" in html or "data-recommend-channel" in html:
        fail("Obsolete separate For You channel opt-in remains in the UI")
    if 'id="q"' not in html or "data-search-submit" not in js or 'id="searchSort"' not in html:
        fail("The single global Search page and sort controls are missing")
    if any(marker in js for marker in ("data-home-filter", "data-refresh-feed")):
        fail("Obsolete Home filter buttons or manual refresh button remain")
    if any(marker in js for marker in ("videoQuery", "channelQuery", "musicQuery", "newsQuery", "topicQuery")):
        fail("Per-page search controls must be removed in favour of global Search")
    if not all(marker in html for marker in ('id="refreshIndicator"', 'id="refreshLabel"')) or "setupNavigationBehavior();" not in js:
        fail("Home pull-to-refresh interaction is missing")
    if "function mixHomeFeedItems(" not in js or "homeFeedData=" not in js:
        fail("Home feed must interleave content types and retain data for rendering")
    if "videoDiscoveryQueries(profile.topics, following)" not in bridge or "fun videoDiscoveryQueries" not in engine:
        fail("For You feed must retrieve video candidates from selected topics")
    if "fonts.googleapis.com" in html or "fonts.gstatic.com" in html:
        fail("WebView must not require remote font downloads")

    ui_contracts = {
        "native in-app video docking target": 'id="videoPlayerHost"' in html and "getElementById('videoPlayerHost')" in activity and "nativeVideoOverlay" in activity,
        "dedicated music player screen": "function renderPlayerPage()" in js,
        "lyrics are inside player": 'id="playerLyricsPanel"' in html and 'id="lyricsPanel"' not in html,
        "title-first full thumbnails": "media-copy" in js and "mediaThumbnail(thumb,x.title)" in js and "object-fit:contain" in html and "aspect-ratio:16/9" in html,
        "explicit video resume button": "data-video-resume" in html and "RushNative.showCurrentVideo()" in js,
        "video remains docked or floating across page navigation": "RushNative.updateVideoPosition()" in js and "if (!nativeVideoRequested || nativeVideoFullscreen) return" in activity,
        "obsolete productive-channel copy removed": "selected productive channels" not in html and "productive channel selection" not in html,
        "conditional mini-player spacing": ".app.has-mini #sc" in html,
        "sharp brutalist controls": ".seg,.seg i" in html and "border-radius:0!important" in html and ".topic-tab,.topic-snooze,.topic-manage" in html,
        "selective glass navigation": "top-profile" in html and "backdrop-filter:blur(26px)" in html and ".nav{background:linear-gradient" in html,
        "glass top and bottom navigation": "backdrop-filter:blur(28px)" in html and "backdrop-filter:blur(30px)" in html,
        "active navigation icons stay visible": ".nav a .ic{display:block!important" in html and "visibility:visible!important" in html,
        "adaptive navigation hides on downward scroll": "nav-hidden .top" in html and "nav-hidden .nav" in html and "sc.addEventListener('scroll'" in js,
        "swipe navigation between pages": "Math.abs(dx)>72" in js and "location.hash='#'+NAV[next][0]" in js,
        "single global Search CTA removed": "data-unified-search" not in js and "SEARCH ALL SOURCES" not in html,
        "music now-playing placeholder removed": 'id="musicNowPlaying"' not in html,
        "search no longer reports result counts": "items.length+' results" not in js and "items.length+' videos found" not in js and "items.length+' tracks found" not in js and "' · '+items.length" not in js,
        "library uses library icon": "library:'M4 4h13v16H4z" in js and "['library','Library','follow']" not in js,
        "edge-to-edge root uses dark surface": "setBackgroundColor(android.graphics.Color.rgb(11,16,20))" in activity,
    }
    for contract, valid in ui_contracts.items():
        if not valid:
            fail(f"UI contract failed: {contract}")

    for native_method in ("fun musicDuration()", "fun seekPlaybackTo(positionMs: Long)", "fun skipPrevious()", "fun skipNext()"):
        if native_method not in bridge:
            fail(f"Native media control missing: {native_method}")
    gradle_config = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    if "robolectricSdkRuntime" not in gradle_config or "robolectric.offline" not in gradle_config:
        fail("Robolectric Android SDK must be prefetched by Gradle and loaded offline")
    if "Number((m[3]||'').padEnd(3,'0'))/1000" not in js:
        fail("LRC fractional timestamps must preserve 1-, 2-, and 3-digit precision")
    if "window.rushMusicLyricsLoading=()=>{" not in js:
        fail("Player lyrics loading feedback callback is missing")
    if "android.text.Html.fromHtml(" not in bridge or ".replace('\\u00A0', ' ')" not in bridge:
        fail("RSS headlines and summaries must decode HTML entities and normalize non-breaking spaces")
    if "if (!future.isDone || future.isCancelled) return null" not in bridge:
        fail("Playback position polling must never block waiting for the media service")
    if "robolectric.dependency.dir" not in gradle or 'robolectric.offline", "true"' not in gradle:
        fail("Robolectric tests must use the prefetched offline Android runtime")
    if "android-all-instrumented-15-robolectric-12650502-i7.jar" not in gradle:
        fail("Robolectric SDK prefetch must match the API 35 instrumented runtime")
    if '.put("state", "info")' not in bridge or "No next track is loaded." not in bridge:
        fail("Empty music queue must produce a persistent informational message")
    if "safeUrl(x.url)" not in js:
        fail("News cards must validate HTTPS article links before rendering")
    if "toggle.disabled=!isMusic" not in js or "lyrics.disabled=!isMusic" not in js:
        fail("Player controls must be disabled when no track is loaded")
    if 'data-player-prev aria-label="Previous track" disabled' not in html or 'data-player-toggle aria-label="Play or pause" disabled' not in html:
        fail("Player transport controls must start disabled before the first track is loaded")
    if "window._rushLyrics=[]" not in js or "root.replaceChildren()" not in js:
        fail("A new lyrics request must clear the previous track's lyrics")
    if "getElementById('videoPlayerHost')" not in activity or "nativeVideoOverlay" not in activity:
        fail("Native video must dock over the watch-page slot and float over other pages")
    if "nativeVideoView?.visibility = android.view.View.GONE" not in activity:
        fail("Closing native video must collapse the video surface cleanly")
    if "RushNative.updateVideoPosition()" not in js or "nativeVideoPositionPending" in js:
        fail("The native video window must reposition as the WebView scrolls")

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
        fail("Embedded JavaScript syntax check failed (node --check)")

    print(
        "Rush WebView validation passed: "
        f"{parser.app_containers} app shell, {len(parser.ids)} unique IDs, "
        f"{len(called_methods)} native bridge methods, JavaScript syntax valid."
    )


if __name__ == "__main__":
    main()
