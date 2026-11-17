FM-1 Flasher 0.10.1 — versionCode 41
Application ID: pl.ekim.fm1_flasher. Android 14+; target/compile SDK 36.
Languages: English, Polish, Simplified Chinese. Publisher: Ekim (ekim-pl).

RELEASE CONTENT
Flash diagnostics and maps, full-flash backup/import, m-bootloader 2.4 installation,
stock V15 boot restoration, compatible ROM firmware installation and same-device
backup restoration. Experimental tools remain opt-in. Writes require a validated
plan, exported pre-write backup and confirmation; factory key/MAC are protected.
Notes: github-release-0.10.1.txt. Local validation: qa/0.10.1.txt.
Historical reports describe only the artifacts and checks recorded at their time.

DISTRIBUTIONS
- Debug: signed developer APK, test Firebase and persistent developer certificate.
- F-Droid: signed release APK without Firebase/Google Play services. The APK is
  published on GitHub; this does not mean inclusion in the official F-Droid catalog.
  Pages can publish a signed index when configured; see fdroid/repository.md.
- Google Play: signed AAB with production Firebase; upload to internal testing and
  promotion to production use separate workflows and existing bundle provenance.
Firebase-enabled builds default to usage statistics on, with a Settings opt-out.
All distributions use the same application ID; switching requires compatible signing.
There is no iOS binary in this release. Matching iOS metadata is development groundwork.

BUILD AND PUBLICATION
Use JDK 17, Android SDK 36, Build Tools 35.0.0 and the checked-in Gradle wrapper.
  python3 tools/sync-resources.py --check
  ./gradlew :shared:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
Production builds also run the pinned V15/V16 integration tests. See MIGRATION.md.
Merge the reviewed source, wait for trusted API34/36 CI, and create immutable tag
v0.10.1 on that commit. Then dispatch from main:
  gh workflow run android-release.yml --ref main -f tag=v0.10.1
The combined workflow builds all three variants and publishes the debug/F-Droid
APKs with manifests and SHA256SUMS. The Play AAB remains in the Actions artifact.
Upload/promote that same verified AAB with google-play-upload.yml and
promote-production.yml. Detailed inputs/recovery: github-actions.txt.
Publication status is established by GitHub/Play results, not by this source file.

DOCUMENTATION AND STORE MATERIALS
README.md, docs/user-guide.md and the translated website guides describe 0.10.1.
about/app*.md are bundled offline About documents; about/README*.md serve the website.
Privacy text is synchronized across Markdown, Android resources and static HTML.
fastlane/metadata/android/{en-US,pl-PL,zh-CN}/ contains listings and changelog 41.
Existing screenshots show 0.10.0; they are historical illustrations, not new captures.
Build and check the website with scripts/build_site.py and scripts/check_site.py
using website/requirements.txt. Deployment occurs from main.

VALIDATION BOUNDARIES
The owner reports that previously available features work. Newly enabled write
operations are covered by simulated flash tests and UI checks; physical write/boot
acceptance is still outstanding. Verify each chip/bootloader combination separately.
Store forms, signing continuity and any required physical connectedDevice service
video remain separate from CI. See test-checklist.txt and play-console.json.
