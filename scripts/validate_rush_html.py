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
SERVICE_PATH = ROOT / "app/src/main/java/com/rush/app/RushPlaybackService.kt"
OFFLINE_PATH = ROOT / "app/src/main/java/com/rush/app/OfflineDownloads.kt"
SEARCH_PATH = ROOT / "app/src/main/java/com/rush/app/LiveYouTubeSearch.kt"
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
    if not SERVICE_PATH.is_file():
        fail(f"Missing playback service: {SERVICE_PATH}")
    if not OFFLINE_PATH.is_file() or not SEARCH_PATH.is_file():
        fail("Missing offline download worker or YouTube search metadata")
    if not GRADLE_PATH.is_file():
        fail(f"Missing app Gradle configuration: {GRADLE_PATH}")

    html = HTML_PATH.read_text(encoding="utf-8")
    bridge = BRIDGE_PATH.read_text(encoding="utf-8")
    activity = ACTIVITY_PATH.read_text(encoding="utf-8")
    service = SERVICE_PATH.read_text(encoding="utf-8")
    offline = OFFLINE_PATH.read_text(encoding="utf-8")
    search = SEARCH_PATH.read_text(encoding="utf-8")
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
    playback_engine_path = ROOT / "app/src/main/java/com/rush/app/RushPlaybackEngine.kt"
    quality_test_path = ROOT / "app/src/test/java/com/rush/app/RushPlaybackEngineTest.kt"
    if not playback_engine_path.is_file() or not quality_test_path.is_file():
        fail("Video quality resolver or its unit tests are missing")
    playback_engine = playback_engine_path.read_text(encoding="utf-8")
    quality_tests = quality_test_path.read_text(encoding="utf-8")
    feed_preferences_test_path = ROOT / "app/src/test/java/com/rush/app/FeedPreferencesTest.kt"
    if not feed_preferences_test_path.is_file():
        fail("Feed preference tests are missing")
    feed_preferences_tests = feed_preferences_test_path.read_text(encoding="utf-8")
    design_path = ROOT / "DESIGN.md"
    if not design_path.is_file():
        fail("Rush design system documentation is missing")
    design = design_path.read_text(encoding="utf-8")
    download_function = bridge.split("fun downloadMedia(", 1)[1].split("fun loadDownloads(", 1)[0]
    mobile_js = js[js.find("const rushOriginalGo=go;"):]
    playback_methods = []
    for marker in ("fun queueVideo(", "fun queueVideos(", "fun playVideo("):
        start = bridge.index(marker)
        end = bridge.index("\n    @JavascriptInterface", start)
        playback_methods.append(bridge[start:end])
    library_queue = bridge.split("fun playLibraryQueue()", 1)[1].split("@JavascriptInterface", 1)[0]
    mobile_card = js.split("function mobileVideoCard", 1)[1].split("function mobileMusicCard", 1)[0]
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
        "inline native player host is rendered on the watch screen": 'id="videoPlayerHost"' in html,
        "dedicated music player screen": "function renderPlayerPage()" in js,
        "music player offers explicit video mode": 'id="playerModeToggle"' in html and "data-player-watch-video" in js and "data-player-listen-audio" in js,
        "video mode keeps current track details visible": "VIDEO MODE · PLAYING" in js and "const hasMedia=isMusic||isVideo;" in js,
        "music playback resolves audio only": "resolvePlaybackMedia(videoId, audioOnly = true" in bridge,
        "video dismissed with app task": "override fun onTaskRemoved(rootIntent: android.content.Intent?)" in service and "pauseAllPlayersAndStopSelf()" in service,
        "video stops whenever app leaves foreground": "enforceBackgroundPlaybackPolicy()" in service and "RushPlaybackService.activeInstance?.enforceBackgroundPlaybackPolicy()" in activity and "!isInPictureInPictureMode" in activity,
        "background task queue excludes video": "queuedIsAudioOnly" in service and "player.removeMediaItem(index)" in service,
        "WebView timers pause while app is backgrounded": "webView.pauseTimers()" in activity and "webView.resumeTimers()" in activity,
        "background Activity releases its controller": "rushWebBridge.release()" in activity and "override fun onStop()" in activity,
        "playback re-sync does not start an idle service": "activeInstance?.hasCurrentMediaItem()==true" in activity and "fun hasCurrentMediaItem()" in service,
        "lyrics are inside player": 'id="playerLyricsPanel"' in html and 'id="lyricsPanel"' not in html,
        "mobile top navigation is removed while bottom tabs remain": '<header class="top yt-topbar">' not in html and 'id="menuShortcut"' not in html and "const mobileNav=[['home','Home','home'],['search','Search','search'],['subs','Subs','follow'],['library','Library','library']]" in js,
        "mobile Explore and News routes removed": "delete R.explore;delete R.news;" in js,
        "home feed has topic-derived sources": "topicQueries" in bridge and '"$topic explained"' in bridge,
        "feed preference API supports custom topics and snoozing": "fun addFeedTopic(" in bridge and "fun snoozeFeedTopic(" in bridge and "snoozedTopics" in prefs,
        "playback feedback influences recommendation profile": "fun rateVideo(" in bridge and "likedTerms" in engine and "dislikedTerms" in engine,
        "Media3 emits real buffering state": "rushPlaybackBufferingState" in bridge and "setShowBuffering" in activity,
        "skeletons exist with reduced-motion fallback": "yt-skeleton-thumb" in html and "prefers-reduced-motion:reduce" in html,
        "local profile and onboarding are device-only": "fun getLocalProfile()" in bridge and "fun completeLocalProfile(" in bridge and "rush_profile_setup_complete" in bridge and "No sign-in, email, password or cloud sync is used." in html,
        "first-run onboarding and local profile routes are wired": "R.onboarding=renderOnboardingPage" in js and "R.profile=renderProfilePage" in js and "location.replace('#onboarding')" in js,
        "every mobile video card offers a download action": 'data-download-media' in mobile_card,
        "video watch page offers a download action": "data-download-media" in js[js.rfind("function renderVideoPage"):],
        "Ultra HD and 8K quality caps are selectable": "4K Ultra HD" in html and "[4320,'8K']" in js and "data-video-quality" in js,
        "download shelf shows quality and progress with safe actions": "yt-download-progress" in html and "item.quality" in js and "data-cancel-download" in js and "data-resume-download" in js,
        "buffer indicator animates and respects reduced motion": "is-buffering::before" in html and "rush-buffer-spin" in html and "prefers-reduced-motion:reduce" in html,
        "Library profile action routes to profile": "b.dataset.mobileRoute==='profile'" in js,
        "Library page does not duplicate top-bar actions": "yt-home-actions" not in js[js.index("function renderLibraryPage()"):js.index("function renderLibraryContent()")],
        "Home uses a creator rail and one category row": "home-topic-filter" in js and "data-tab=\"fo\"" not in js[js.index("function renderHome()"):js.index("function renderExplorePage()")],
                "media actions are grouped behind an accessible overflow menu": "function mediaActions(x,isMusic)" in js and '<details class="result-more">' in js and ".result-more[open] .result-actions" in html,
        "empty feed message is not duplicated in status and empty state": "if(status)status.textContent=count?" in js,
        "Home feed uses a single vertical column on mobile": ".home-page .home-bento{grid-template-columns:minmax(0,1fr);gap:14px}" in html,
        "top-bar uses the actual Rush lightning launcher mark": 'viewBox="0 0 48 48"' in html and 'M28 5 10 26h11l-2 17 19-24H27z' in html,
        "mobile header is limited to Rush branding and notifications": 'class="mobile-topbar"' in html and 'class="mobile-brand"' in html and 'id="mobileNotifications"' in html and html.count('class="mobile-top-actions"') == 1,
        "profile/settings action is hidden outside Library": 'id="mobileLibraryProfile"' in html and 'hidden>' in html[html.index('id="mobileLibraryProfile"'):html.index('id="mobileLibraryProfile"') + 220] and "libraryProfile.hidden=page!=='library'" in js,
        "notification action reports its current unavailable state": "b.id==='mobileNotifications'" in js and "Notifications are not available yet." in js,
        "mobile mini-player uses SVG open control and compact layout": 'data-act="open" aria-label="Open player"><svg class="ic"' in html and ".mini{grid-template-columns:48px minmax(0,1fr) 40px 40px!important" in html,
        "responsive media row grid": "grid-template-columns:64px minmax(0,1fr) auto!important" in html,
        "explicit video resume button": "data-video-resume" in html and "RushNative.showCurrentVideo()" in js,
        "inline video remains anchored when navigating tabs": "if(!mediaState.isVideo)RushNative.hideVideo();else RushNative.updateVideoPosition();" in js,
        "obsolete productive-channel copy removed": "selected productive channels" not in html and "productive channel selection" not in html,
        "conditional mini-player spacing": ".app.has-mini #sc" in html,
        "rounded selected feed segment": ".seg button{border-radius:14px!important}" in html and ".seg i{border-radius:14px!important}" in html,
        "solid feed segment without glass": ".seg{border-radius:18px!important;background:#131b21!important" in html,
        "library uses library icon": "library:'M4 4h13v16H4z" in js and "['library','Library','follow']" not in js,
        "edge-to-edge root uses dark surface": "setBackgroundColor(android.graphics.Color.rgb(11,16,20))" in activity,
        "onboarding accepts defaults and existing custom interests without weakening normal validation": "FeedPreferences.saveOnboardingTopics" in bridge and "fun saveOnboardingTopics(" in prefs and "map(::resolveCatalogTopic)" in prefs and "onboardingCanSaveTheDefaultLocalInterestSelection" in feed_preferences_tests and "onboardingAcceptsDefaultTopicsAndPreviouslySavedCustomInterests" in feed_preferences_tests and "regularTopicSaveStillRejectsUnknownPhrases" in feed_preferences_tests and "onboardingRejectsCustomInterestUntilItIsSavedLocally" in feed_preferences_tests,
        "onboarding shows a saving state and restores the action after failure": "form.setAttribute('aria-busy','true')" in js and "window.rushLocalProfileError" in js and "submit.disabled=false" in js,
        "topic picker uses the same selectable chips as Home": "yt-topic-picker-grid" in html and "data-save-topics" in html and "RushNative.saveSelectedFeedTopics" in js and "fun saveSelectedFeedTopics(" in bridge,
        "home topic rail has an All reset and filters by feed tags": 'data-home-topic="all"' in js and "item.topicTags" in js and '.put("topicTags", JSONArray(candidate.topicTags.sorted()))' in bridge,
        "video cards receive channel avatar metadata": "channelAvatarUrl" in search and '.put("avatar", item.channelAvatarUrl)' in bridge and "item.channelAvatarUrl" in js,
        "watch screen keeps the bottom navigation visible": ".app.video-detail .nav{display:grid!important}" in html,
        "dynamic mobile controls use the shared SVG helper": "const IC={" in js and "ic('refresh')" in js and "ic('search')" in js,
        "mobile tabs remain interactive without a top drawer": "updateMobileNavigation()" in mobile_js and 'data-mobile-route="profile"' in html,
        "design system documents the adapted source guidance": "Source basis" in design and "Consistent iconography" in design and "prefers-reduced-motion" in design,
    }
    if "preferDash = false" not in download_function or "allowDashFallback = false" not in download_function:
        fail("Offline video downloads must resolve a direct playable stream, not a DASH/HLS manifest")
    if "forceRefresh = true" not in download_function or offline.count("forceRefresh = true") < 3:
        fail("Download recovery must refresh expired stream URLs instead of reusing cached URLs")
    if any("download_mp4" in method or "preferDash = true" not in method or "allowDashFallback = true" not in method for method in playback_methods):
        fail("Video playback and queued videos must use adaptive formats independently of the download MP4 preference")
    if "download_mp4" in library_queue or "preferDash = !isMusic" not in library_queue:
        fail("Library queue video playback must use adaptive formats independently of download preferences")
    if "val preferMp4 = false" not in service or "val preferDash = !audioOnly" not in service:
        fail("Playback recovery must use adaptive video streams rather than the download format preference")
    if "selectVideoHeight" not in playback_engine or "Ultra HD" not in html:
        fail("High-quality selection must fall back to available downloadable formats")
    if "setMaxVideoSize(Int.MAX_VALUE, qualityCap)" not in service:
        fail("Saved quality cap is not applied when Media3 starts")
    if "completedController() ?: return@launch" not in bridge:
        fail("Changing a saved quality preference must not start an idle playback service")
    if "RushNative.getLocalProfile()" not in js or "RushNative.completeLocalProfile(" not in js:
        fail("Local profile UI is not connected to its on-device bridge methods")
    if mobile_js.count("window.rushVideoSearchError=") != 1:
        fail("Mobile video search must have exactly one error callback so loading state is cleared")
    if "quality cap" not in html.lower() or "directly downloadable" not in html:
        fail("Quality UI must explain that actual offline quality depends on available source formats")
    if "selectVideoHeight" not in quality_tests or "ultraHdCapUsesHighestDirectFormat" not in quality_tests:
        fail("Video quality selection must include a unit test for high-quality source fallback")

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
    if "HOME_FEED_CACHE_TTL_MS" not in bridge or "rush_home_feed_v5_" not in bridge:
        fail("Home feed must render a cached payload before refetching")
    if "Semaphore(5)" not in bridge or "awaitAll()" not in bridge:
        fail("Home feed source lookups must run concurrently with bounded parallelism")
    if "NEWS_RSS_CLIENT" not in bridge or "NEWS_ARTICLE_CLIENT" not in bridge:
        fail("News feed and article thumbnail requests must reuse configured HTTP clients")
    if "imageFromRssMarkup(description)" not in bridge:
        fail("RSS image extraction must inspect embedded article markup")
    if "og:image:secure_url" not in bridge or "twitter:image" not in bridge:
        fail("Article thumbnail extraction must include publisher social metadata")
    if "NEWS_THUMBNAIL_CACHE_KEY" not in bridge or "enrichNewsThumbnails" not in bridge:
        fail("Resolved article thumbnails must be cached and reused")
    if "data.mode!==expectedMode" not in js:
        fail("Home must ignore responses from a previously selected feed tab")
    if "toggle.disabled=!isMusic" not in js or "lyrics.disabled=!isMusic" not in js:
        fail("Player controls must be disabled when no track is loaded")
    if 'data-player-prev aria-label="Previous track" disabled' not in html or 'data-player-toggle aria-label="Play or pause" disabled' not in html:
        fail("Player transport controls must start disabled before the first track is loaded")
    if "window._rushLyrics=[]" not in js or "root.replaceChildren()" not in js:
        fail("A new lyrics request must clear the previous track's lyrics")
    if "getElementById('videoPlayerHost')" not in activity or "window.devicePixelRatio" not in activity:
        fail("Native video positioning must use the watch host and WebView pixel scale")
    if "nativeVideoView?.visibility=android.view.View.GONE" not in activity:
        fail("Exiting native fullscreen must collapse the video surface cleanly")
    if "RushNative.updateVideoPosition()" not in js or "nativeVideoPositionPending" in js:
        fail("The native inline video surface must follow the WebView player host")
    if "mobileScrollContainer.addEventListener('scroll',mobileSyncPlayer" not in js or "window.addEventListener('resize',mobileSyncPlayer" not in js:
        fail("Inline video placement must follow the mobile scroll container and screen resizing")

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
