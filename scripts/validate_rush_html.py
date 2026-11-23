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

    html = HTML_PATH.read_text(encoding="utf-8")
    bridge = BRIDGE_PATH.read_text(encoding="utf-8")
    activity = ACTIVITY_PATH.read_text(encoding="utf-8")
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
    if "topicQuery" not in html or "function filterTopicNames" not in js:
        fail("Personalisation topic search is missing")
    if "fonts.googleapis.com" in html or "fonts.gstatic.com" in html:
        fail("WebView must not require remote font downloads")

    ui_contracts = {
        "no preloaded fixed video player": 'id="videoPlayerHost"' not in html,
        "dedicated music player screen": "function renderPlayerPage()" in js,
        "lyrics are inside player": 'id="playerLyricsPanel"' in html and 'id="lyricsPanel"' not in html,
        "responsive media row grid": "grid-template-columns:64px minmax(0,1fr) auto!important" in html,
        "explicit video resume button": "data-video-resume" in html and "RushNative.showCurrentVideo()" in js,
        "no automatic video popup on tab navigation": "if(page!=='videos')RushNative.hideVideo();" in js,
        "obsolete productive-channel copy removed": "selected productive channels" not in html and "productive channel selection" not in html,
        "conditional mini-player spacing": ".app.has-mini #sc" in html,
        "library uses library icon": "library:'M4 4h13v16H4z" in js and "['library','Library','follow']" not in js,
        "edge-to-edge root uses dark surface": "setBackgroundColor(android.graphics.Color.rgb(11,16,20))" in activity,
    }
    for contract, valid in ui_contracts.items():
        if not valid:
            fail(f"UI contract failed: {contract}")

    for native_method in ("fun musicDuration()", "fun seekPlaybackTo(positionMs: Long)", "fun skipPrevious()", "fun skipNext()"):
        if native_method not in bridge:
            fail(f"Native media control missing: {native_method}")
    if "if (!future.isDone || future.isCancelled) return null" not in bridge:
        fail("Playback position polling must never block waiting for the media service")

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
