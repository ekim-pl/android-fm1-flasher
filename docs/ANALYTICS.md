# Firebase Analytics — 0.10.1

Android version 0.10.1 uses versionCode 41.
The normal Android build and base iOS project contain no Firebase SDK and show no analytics prompt.

## Architecture and behavior

`shared/Analytics.kt` owns the closed event schema, the persistent preference stored in
`analytics-preference.json`, a platform lock, screen deduplication and operation measurements.
`AnalyticsSink` is injected at the platform boundary. No application strings, URLs,
exception messages, device identities or package identifiers can be supplied to event fields.
Android's singleton is shared by ComposeActivity, FlasherService and WorkManager;
iOS injects NativeAnalytics into PortableAppBackend from SwiftUI.

**Firebase-enabled builds collect by default from the first online launch.** There is no
consent prompt or accept/decline dialog. Settings → Usage statistics → Share usage statistics
is initially on and can be turned off even during an operation. This is an opt-out preference,
not evidence of explicit consent. SDK-free builds still contain no analytics SDK or switch.

An explicit disable is saved and survives restarts and notice-version changes. The earlier
`analytics-consent.json` format is read for compatibility: a stored DENIED remains disabled.
A new explicit choice writes `analytics-preference.json` and takes precedence over the old file.
A damaged/unreadable preference fails closed to avoid accidentally overriding an opt-out.
A failed disable write stops collection for the current process and reports the failure;
retry to persist the choice for future launches. A failed enable write leaves a saved disable
in effect. SDK failures are isolated from device operations. The local operation log is separate.

Firebase is initialized only after saved settings have loaded, the preference permits collection
and offline mode is off. Android's automatic initialization provider is removed. Both SDKs
start with collection disabled and analytics/ad storage denied, so a persisted opt-out or
offline preference can be applied before any initialization. The controller then enables
analytics storage and collection automatically for the default/enabled preference.
Advertising storage, advertising user data and personalization remain denied. No user ID is set.
Android removes AD_ID, AdServices attribution/ad-ID and install-referrer permissions; iOS uses
FirebaseAnalyticsCore without the advertising identity product and disables IDFV collection
and SKAdNetwork registration. SDK lifecycle events, app-instance IDs, technical metadata and
approximate location derived from IP still exist; these data are pseudonymous, not anonymous.

Offline mode disables collection and requests a reset of local SDK analytics data.
There is no app-owned replay queue. Operations crossing an off/on or offline/online transition
are discarded. Events during disabled periods are never replayed. Re-enabling collects only
new events. An SDK upload already in progress may finish; verify actual network behavior on
both platforms. Disabling/resetting local data does not delete previously uploaded server data.

## Android configuration

Create separate Firebase TEST and PRODUCTION projects, with Analytics enabled.
Register Android package `pl.ekim.fm1_flasher` in each. Place their downloaded configs at:

- `app/firebase/test/google-services.json`
- `app/firebase/production/google-services.json`

Those directories are ignored by Git. No service-account or admin key belongs in the app.
Gradle generates the four required Firebase resources from the selected Android client;
manual lazy initialization uses those resources, so a Google Services Gradle plugin is unnecessary.
SDK dependencies are present only with `-Pfm1Analytics=true`.

```sh
# SDK-free build
./gradlew :app:assembleDebug
# Test Firebase project
./gradlew -Pfm1Analytics=true -Pfm1AnalyticsEnvironment=test :app:assembleDebug
# Production proposal (requires signing environment configured as before)
./gradlew -Pfm1Analytics=true -Pfm1AnalyticsEnvironment=production :app:bundleRelease
```

Environment selection is mandatory when analytics is enabled. Android debug variants are disabled
for PRODUCTION; iOS debug builds decline the production environment. Debug uses TEST;
production signing/publication must use PRODUCTION. Review project IDs before release.
The CI fixture at `tools/fixtures/google-services.test.json` has no working Firebase project;
it is for compilation/manifest checks only, never a production config or DebugView verification.
Current pinned Android dependency: Firebase BoM 34.6.0, compatible with the existing Kotlin 2.2.21 compiler.
BoM 35.2.0 was checked but requires Kotlin 2.4 metadata; upgrading the compiler is outside this proposal. Dependency verification metadata
must cover both SDK-free and Firebase builds.

## iOS configuration (requires macOS and Xcode 16+)

Register `pl.ekim.fm1-flasher` in each Firebase project alongside Android.
Use two app streams in each environment's GA4 property. No cross-platform user matching.
Place configs at `iosApp/Firebase/test/GoogleService-Info.plist` and
`iosApp/Firebase/production/GoogleService-Info.plist`.

```sh
cd iosApp
# SDK-free
xcodegen generate --spec project.yml
# Optional Firebase project overlay; environment expansion selects the resource
FM1_ANALYTICS_ENVIRONMENT=test xcodegen generate --spec project.analytics.yml
# Then build the generated project with Xcode on macOS.
```

The overlay pins Firebase Apple SDK 12.6.0 (supports iOS 15, SwiftPM tools 6.0).
NativeAnalytics conditionally imports FirebaseAnalytics and validates the bundle identifier.
The base target has no Firebase package dependency. Review/commit the generated SwiftPM
Package.resolved when resolving on macOS. No ATT prompt is added: the proposed integration
does not use advertising identity or cross-company tracking. Verify the actual binary,
SDK privacy manifests, required-reason APIs and App Store disclosures before submission.
Do not upgrade the minimum iOS version just to add analytics.

## Event schema v1

| Event | Emission |
| --- | --- |
| screen_view | Logical destination changes; screen_name is a fixed enum |
| catalog_refresh_completed | Completed catalog check (success follows catalog freshness) |
| firmware_download_completed | One package download attempt, including update-all items |
| device_connection_completed | Actual identity/connection attempt |
| flash_started | Admitted backend flash operation/job |
| flash_completed | Backend outcome, including unconfirmed failure |
| recovery_completed | Android recovery attempt after validation; iOS recovery is unavailable |

Operation completion events contain only result, error_category, stage, initiator, duration_ms and
schema_version. Durations/schema are numeric in Firebase. Result is success/failure/cancelled;
error and stage values are fixed enums. Unknown exceptions map to other. Stage tracking
covers write, verify and reconnection. No progress-per-packet events, search text, OAuth
activity, firmware names/hashes or device identifiers are sent.
A stopped Android queue prevents future jobs; it does not cancel an active write.
A process killed during flashing cannot emit a completion event; do not infer that every
missing completion is a confirmed hardware failure.

## Console and store-publication checklist

These are account/device actions; code changes do not perform them:

1. Register real TEST/PRODUCTION apps, add configs and verify each project's destination.
2. Set and verify user/event-data retention to **2 months** (not verified in the provided projects); turn off Google signals, ad personalization,
   advertising sharing/integrations and unnecessary granular collection. Aggregates can outlive
   user/event retention. Restrict console access to maintainers.
3. Verify the SDK's actual automatic data (including identifiers, device/app metadata and any
   derived approximate location). Complete Google Play Data safety and Apple App Privacy based
   on the chosen binary and enabled console features; this is not a declaration of no collection.
   `publication/analytics-0.10.1.json` is a draft inventory, not a submitted store form.
4. Verify default-on startup, persistent disable, re-enable, restart, offline, in-flight operations and SDK
   buffering/network behavior with real test configs on Android and iOS.
5. Use Firebase DebugView in TEST to validate event names, parameters, deduplication and outcomes.
   Confirm no production events originate from debug builds. Debug mode is not enabled in code.
6. Register event-scoped dimensions result, error_category, stage, initiator and schema_version;
   use built-in screen dimensions and register duration_ms as an event metric. Create reports for
   downloads to flash starts, completion rates, errors by platform/app version and operation times.
   These reports cover installations with statistics enabled; there is no account-level cross-device funnel.
7. Check merged Android manifest for removed auto-init/AD_ID, inspect iOS linked products/manifests,
   compare the final SDK-free and Firebase binaries, update license notices if required, and sign
   only after hardware, privacy and store validation. Keep historical publication records unchanged.

The Google Play workflow defaults to the SDK-free variant. Its optional `analytics` input
requires protected environment secret `FIREBASE_PRODUCTION_ANDROID_JSON` containing the Android
production config and completed store/privacy review. It lints the production Firebase variant
and passes the explicit flags to bundleRelease. Debug tests run with the SDK-free variant;
CI separately compiles/lints the Firebase TEST variant with a nonworking fixture.
The archival F-Droid recipe is untouched; the base SDK-free build remains available.
Crashlytics, Remote Config, ads, BigQuery export and a custom telemetry server are outside this release.

## Verification

Historical behavior verification: [default-on behavior and documentation review](../publication/qa/0.9.5-default-on.txt).

```sh
python3 tools/sync-resources.py --check
./gradlew :shared:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s scripts -p 'test_*.py'
# Compile Firebase against the deliberately invalid CI fixture:
mkdir -p app/firebase/test
test -f app/firebase/test/google-services.json || cp tools/fixtures/google-services.test.json app/firebase/test/google-services.json
./gradlew -Pfm1Analytics=true -Pfm1AnalyticsEnvironment=test :app:assembleDebug :app:lintDebug
```

Common tests verify default-on startup, persistent disable/re-enable, legacy opt-out migration,
notice-version changes, offline restart, no replay, deduplication, closed payloads, damaged preferences,
and isolation of storage/SDK failures. Instrumentation/network and iOS build results must be
recorded separately; a Linux JVM/Android build does not establish iOS runtime correctness.

## Distribution builds

- **Google Play:** `.github/workflows/google-play.yml` always includes Firebase with the production project and builds a signed AAB. Manual dispatch; uploading to internal testing is off by default.
- **F-Droid:** `.github/workflows/fdroid.yml` explicitly disables Firebase and builds an unsigned release APK without Firebase or Google Play services dependencies. Manual dispatch; no store upload.

Local Google Play test APKs use the test Firebase project. F-Droid test APKs use `-Pfm1Analytics=false`. Both use the same application ID; moving between store distributions requires compatible signing certificates. The F-Droid workflow is build preparation, not confirmation of F-Droid acceptance.
