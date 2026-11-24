# Google Play release readiness

**Status: platform setup is in progress; the current media-source implementation is not cleared for Play submission.**

## Platform and packaging

- [x] compileSdk 36 and targetSdk 36 are configured. New Android phone apps and updates must target API 36 or higher from 31 August 2026.
- [x] Android App Bundle (.aab) release output is part of CI.
- [x] Release shrinking uses dedicated ProGuard rules for the WebView bridge and Android entry points.
- [x] The app uses WebViewAssetLoader to serve bundled HTML, CSS and JavaScript under an HTTPS origin.
- [x] Network cleartext is disabled.
- [x] Background media uses a Media3 MediaSessionService and the mediaPlayback foreground-service type.
- [x] Picture-in-Picture is declared in the manifest.
- [x] Notification permission is requested contextually on Android 13+.
- [x] No camera, microphone, contacts, location or shared-storage permission is declared.
- [ ] Configure an upload keystore in GitHub Actions secrets and verify the signed release bundle.
- [ ] Enrol the app in Play App Signing and complete the first Play Console upload.
- [ ] Increment versionCode for every published release.

### CI signing secrets

Configure these repository secrets before a Play upload:

- RUSH_UPLOAD_KEYSTORE_BASE64: base64-encoded upload .jks file.
- RUSH_UPLOAD_STORE_PASSWORD: keystore password.
- RUSH_UPLOAD_KEY_ALIAS: key alias.
- RUSH_UPLOAD_KEY_PASSWORD: key password.

The workflow can build an unsigned release bundle without the secrets, but an unsigned bundle cannot be uploaded to Play Console. Do not commit the keystore or passwords.

## Required policy and store-console work

- [ ] Publish a public, non-geofenced HTTPS privacy-policy page. The in-app Privacy information view is a draft, not the final policy.
- [ ] Complete the Play Console Data safety form based on the final network requests, bundled libraries and SDK behaviour.
- [ ] Confirm the app's developer/legal name and privacy contact details.
- [ ] Complete content rating, target-audience, ads, app-access and store-listing declarations.
- [ ] Provide accurate screenshots and describe the current functionality; do not claim a licensed movie catalogue.
- [ ] Declare and justify foreground-service use where Play Console asks for it.
- [ ] Test install, upgrade, notification denial, playback, PiP, downloads, offline transitions and data-reset behaviour on physical devices.
- [ ] Review the Play pre-launch report and Android vitals before production rollout.

## Content-rights blocker: YouTube playback and downloads

The current build uses NewPipeExtractor/LibreTube-derived code to resolve YouTube stream URLs directly. It also exposes offline video/audio and MP3 conversion features. This is not the same as using the official YouTube embedded player or an authorised content API.

YouTube's Terms restrict accessing, reproducing, downloading or otherwise using YouTube content except as expressly authorised by YouTube or with prior written permission. YouTube's API Services Developer Policies separately prohibit downloading, storing or making audiovisual content available offline without prior written approval. Google Play also prohibits apps that infringe intellectual property or encourage infringement.

**Do not treat a successful Android build as proof that this implementation is authorised for Google Play.** Before submission, obtain the relevant rights/approvals or replace the unofficial extraction and download path with provider-approved playback and licensed content sources. In particular, review background audio, offline copies, MP3 extraction, caching and the use of third-party content thumbnails. Keep a record of licences/permissions for all content sources.

The current movie shelf uses long-form YouTube search results as discovery candidates. It does not have a licensed film catalogue, reliable film metadata, or Netflix-style playback rights. Its UI should be described accurately until a permitted movie provider is integrated.

## Third-party licence review

The FFmpeg SmartException dependency added for the Android release build is `com.arthenica:smart-exception-java:0.2.1`, with its `smart-exception-common` transitive dependency; Maven Central declares both under the 3-Clause BSD licence. Record both in the bundled notices and final licence inventory. [Maven Central metadata](https://central.sonatype.com/artifact/com.arthenica/smart-exception-java) [Common dependency metadata](https://central.sonatype.com/artifact/com.arthenica/smart-exception-common).


The repository identifies the LibreTube/NewPipeExtractor-derived player as GPL-3.0-or-later, its FFmpegKit-compatible audio package as LGPL-3.0, and the SmartException Java/Android helper as BSD-3-Clause. Keep the applicable source and notices available and confirm distribution obligations for the exact versions shipped. This is separate from YouTube content rights.

## Official references

- [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [Google Play user data and privacy policy requirements](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [Android App Bundle signing](https://developer.android.com/studio/publish/app-signing)
- [YouTube Terms of Service](https://youtube.co/t/terms)
- [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies)
- [Google Play intellectual property policy](https://support.google.com/googleplay/android-developer/answer/18265346)
