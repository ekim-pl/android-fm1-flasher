FM-1 Flasher 0.10.0 — milestone 1: Google Play preparation
Android versionCode: 31. Application ID: pl.ekim.fm1_flasher.
Publisher: Ekim (ekim-pl), ekim.pl@gmail.com.
Android 14+; target/compile SDK 36. Languages: English, Polish, Simplified Chinese.

STATUS
This milestone prepares documentation, localized app content, screenshots and a test
APK. It does not publish a GitHub release or submit the app to Google Play.
Current verification and artifact identity: qa/0.10.0.txt.
Earlier QA reports, release notes and changelogs are historical evidence only.

BUILD
  python3 tools/sync-resources.py --check
  ./gradlew :shared:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
The default debug APK contains no Firebase SDK. See ../docs/ANALYTICS.md for the
optional Firebase variant and analytics-0.10.0.json for its separate data inventory.
The test APK is signed with the local debug certificate, not a Play upload key.
A release AAB requires the configured upload key and the manual Google Play workflow.
See github-actions.txt. Do not infer Play readiness from a passing debug build.

DOCUMENTATION AND STORE ASSETS
README.md and docs/user-guide.md describe current use; about/app*.md are the concise
in-app About documents. MIT and third-party notices are bundled for offline reading.
PRIVACY_POLICY.md, translated website policies and in-app resources describe the
same local storage and optional analytics behavior.
fastlane/metadata/android/{en-US,pl-PL,zh-CN}/ contains store text and screenshots.
Version 31 changelogs describe this milestone; earlier changelogs remain historical.
Existing screenshots are historical and must not be submitted as 0.10.0 screenshots.
New capture is blocked by occupied device sessions; see qa/0.10.0.txt.
Build the public website with scripts/build_site.py; validate with scripts/check_site.py.
Branch changes do not update the live privacy URL until deployment.

BEFORE GOOGLE PLAY
Verify the final signed AAB, upload-key continuity, production analytics configuration
(if enabled), Data safety answers, retention, content rating, audience and store access.
Complete physical FM-1/hub acceptance and any connectedDevice service demonstration.
Automatic multi-device pairing remains disabled; physical ROM recovery is unverified.
No iOS binary is supplied. See test-checklist.txt and play-console.json.
