PROPOSAL: 0.9.5-experimental.26 / Android code 26 / iOS build 26.
Optional analytics implementation: ../docs/ANALYTICS.md. Not published.
Current default-on analytics verification: qa/0.9.5-default-on.txt.
Historical release records below remain unchanged.

FM-1 Flasher — publication documentation
Stable release: 0.9.3 on main. Release tag: v0.9.3.
Public assets use version-only names; Android versionCode is 24.

CURRENT APPLICATION
Android ID: pl.ekim.fm1_flasher.
Android 14+ / minSdk 34; target and compile SDK 36.
A port to iOS is planned for the future. There is no iOS release or announced release date.
Languages: English, Polish, Simplified Chinese. Publisher/contact: ekim.pl@gmail.com.
The Android workspace is Devices / Library / Tasks with a persistent sequential queue.

RELEASE STATUS
0.9.2 is the stable GitHub Release with updated documentation.
qa/0.9.2.txt identifies that exact APK, checksum, signature and test scope.
The local dist/0.9.2-test23 directory is ignored by Git and is not part of a fresh clone.
github-release.txt is a draft description, not evidence that a release exists.
Documentation changes do not modify the previously supplied APK. A rebuilt APK needs
its own checksum and packaging verification before publication; preserve old QA records.

BUILD AND HANDOFF
Run from the repository root:
  python3 tools/sync-resources.py --check
  ./gradlew :shared:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
Debug APK: app/build/outputs/apk/debug/app-debug.apk.
Verify with apksigner, record application ID/version/minSdk and SHA-256, and check
update-signature continuity. Give the owner the test APK and report before publishing.
A local release bundle can be built with :app:bundleRelease; it is unsigned unless
ANDROID_KEYSTORE_FILE, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and
ANDROID_KEY_PASSWORD are configured. A debug APK is not a signed Play release bundle.

DOCUMENTS AND LISTING TEXT
../README.md: current user/developer guide; ../MIGRATION.md: implementation map.
../about/README*.md: localized in-app guides; Android bundles these with licenses.
../fastlane/metadata/android/{en-US,pl-PL,zh-CN}/: localized draft listing text and
historical changelogs. Version 19 describes this implementation; older changelogs
remain historical. Updating text does not publish it to a store.
play-console.json is a current factual draft, not a submitted or approved declaration.

HISTORICAL ASSETS AND WORKFLOWS
Existing store screenshots and graphics document earlier UI. They do not demonstrate
the 0.9.2 fleet workflow; capture fresh images before a store submission. The graphics
script only frames/generates assets and does not refresh live screenshots.
validation.json records the original app's earlier build and is explicitly historical.
fdroid/pl.ekim.fm1_flasher.yml is an archival original-app recipe, not a recipe for this fork.
The Google Play workflows target pl.ekim.fm1_flasher; verify signing and store configuration before publication.
Read github-actions.txt before any workflow action; Play workflows require manual dispatch.
No workflow configuration, store account, release tag or publishing action is changed
by regenerating this documentation.

PRIVACY AND WEBSITE
../PRIVACY_POLICY.md is the English policy. UI resources, website translations and
privacy/{en,pl,zh-CN}/index.html describe local instrument names, observed serials,
chip choices and job history as well as library and network behavior.
Public site address: https://ekim-pl.github.io/android-fm1-flasher/
The checked-out branch may differ from the deployed default branch. A local build
of these documents does not update the public site. Build using scripts/build_site.py
and validate using scripts/check_site.py; see ../website/pages.md.
Do not assume a deployed privacy URL contains branch-only changes before deployment.

ACCEPTANCE
See test-checklist.txt, ../tests/android14-acceptance.txt and the dated QA report.
Physical multi-FM-1/hub mode changes, power, serial behavior and USB grants remain
to be verified. Automatic multi-device pairing is disabled. ROM recovery is separate
and unverified on hardware. iOS fleet parity and Xcode validation remain outstanding.
foreground-service.txt describes the implemented connectedDevice use case and a
physical demonstration to record; it does not assert such a recording exists.
