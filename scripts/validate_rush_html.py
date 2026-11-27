#!/usr/bin/env python3
"""Validate the YouTube-only Rush WebView shell and native bridge."""

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
SEARCH_PATH = ROOT / "app/src/main/java/com/rush/app/LiveYouTubeSearch.kt"
PREFS_PATH = ROOT / "app/src/main/java/com/rush/app/FeedPreferences.kt"
MANIFEST_PATH = ROOT / "app/src/main/AndroidManifest.xml"
GRADLE_PATH = ROOT / "app/build.gradle.kts"


class RushParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids = Counter()
        self.shells = 0
        self.scripts = []
        self.external_scripts = []
        self.iframes = 0
        self.in_script = False
        self.script_parts = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get("id"):
            self.ids[attrs["id"]] += 1
        if tag == "div" and attrs.get("id") == "appRoot":
            self.shells += 1
        if tag == "script":
            self.in_script = True
            self.script_parts = []
            if attrs.get("src"):
                self.external_scripts.append(attrs["src"])
        if tag == "iframe":
            self.iframes += 1

    def handle_data(self, data):
        if self.in_script:
            self.script_parts.append(data)

    def handle_endtag(self, tag):
        if tag == "script" and self.in_script:
            self.scripts.append("".join(self.script_parts))
            self.in_script = False
            self.script_parts = []


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
    search = read_required(SEARCH_PATH)
    prefs = read_required(PREFS_PATH)
    manifest = read_required(MANIFEST_PATH)
    gradle = read_required(GRADLE_PATH)

    parser = RushParser()
    parser.feed(html)
    parser.close()

    if parser.shells != 1:
        fail(f"Expected one #appRoot shell, found {parser.shells}")
    required_ids = ("content", "sidebar", "miniPlayer", "topicDialog",
                    "playlistDialog", "globalSearch")
    for element_id in required_ids:
        if parser.ids[element_id] != 1:
            fail(f"Expected exactly one #{element_id}, found {parser.ids[element_id]}")
    duplicates = {name: count for name, count in parser.ids.items() if count > 1}
    if duplicates:
        fail(f"Duplicate static HTML IDs: {duplicates}")
    if not parser.scripts:
        fail("No inline JavaScript was found")
    if parser.external_scripts or parser.iframes:
        fail("The local WebView must not load remote scripts or iframe content")

    js = "\n".join(parser.scripts)
    annotated = set(re.findall(
        r"@JavascriptInterface\s+fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(",
        bridge,
    ))
    called = set(re.findall(r"RushNative\.([A-Za-z_][A-Za-z0-9_]*)\s*\(", js))
    missing = sorted(called - annotated)
    if missing:
        fail(f"JavaScript calls methods missing from the annotated native bridge: {missing}")

    required_methods = {
        "getFeedPreferences", "saveFeedTopics", "followSource", "unfollowSource",
        "loadHomeFeed", "searchVideos", "searchChannels", "searchPlaylists",
        "playVideo", "playVideoInPictureInPicture", "showCurrentVideo", "enterPictureInPicture", "togglePlayback",
        "loadLibrary", "saveLibraryItem", "queueLibraryItem", "downloadMedia",
        "loadDownloads", "browsePublicPlaylist", "queuePublicPlaylist",
    }
    missing_required = sorted(required_methods - annotated)
    if missing_required:
        fail(f"Required YouTube features are missing from the bridge: {missing_required}")

    if "Shorts" in html or "shorts" in html.lower():
        fail("The YouTube-only UI must not expose Shorts or Shorts navigation")
    if '"Shorts"' in prefs or "kind != \"channel\"" not in prefs:
        fail("Feed preferences must exclude Shorts topics and non-channel follows")
    if "fun isShortForm" not in search or "filterVideoPage" not in search:
        fail("YouTube search must implement Shorts filtering")
    if "LiveYouTubeSearch.isShortForm" not in bridge:
        fail("The native feed and playlist paths must apply the Shorts filter")
    if "RushNative.searchMusic(" in js or "RushNative.loadNews(" in js:
        fail("The interface must not expose separate Music or News product areas")
    if "supportsPictureInPicture=\"true\"" not in manifest:
        fail("Android Picture-in-Picture must be enabled in the application manifest")
    if "play-services-ads" in gradle.lower() or "audience-network-sdk" in gradle.lower():
        fail("An advertising SDK is present in Gradle dependencies")
    if "adsbygoogle" in html.lower() or "googlesyndication" in html.lower():
        fail("An advertising placement is present in the WebView document")

    required_ui = (
        "data-page=\"home\"", "data-page=\"explore\"",
        "data-page=\"subscriptions\"", "data-page=\"library\"",
        "data-watch-pip", "data-watch-save", "data-watch-download",
        "data-watch-playlist", "data-home-mode=\"following\"",
    )
    for marker in required_ui:
        if marker not in html:
            fail(f"Missing expected YouTube UI feature: {marker}")

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
        "Rush YouTube UI validation passed: "
        f"{parser.shells} app shell, {len(parser.ids)} unique IDs, "
        f"{len(called)} native bridge calls, Shorts filter present, JavaScript valid."
    )


if __name__ == "__main__":
    main()
