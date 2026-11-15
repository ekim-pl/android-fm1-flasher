# Optional Analytics — 0.9.5 release proposal

This branch is a release candidate proposal, not a published release. Android version is
`0.9.5-experimental.26` (code 26); iOS marketing version is 0.9.5/build 26.
The normal Android build and base iOS project contain no Firebase SDK and show no analytics prompt.

## Architecture and behavior

`shared/Analytics.kt` owns the closed event schema, versioned consent stored in
`analytics-consent.json`, a platform lock, screen deduplication and operation measurements.
`AnalyticsSink` is injected at the platform boundary. No application strings, URLs,
exception messages, device identities or package identifiers can be supplied to event fields.
Android's singleton is shared by ComposeActivity, FlasherService and WorkManager;
iOS injects NativeAnalytics into PortableAppBackend from SwiftUI.

Consent defaults to unknown and collection is disabled. Refusal is saved and not prompted
again. Both choices are equally accessible; the card does not block using the app.
Settings allows withdrawal even during an operation. Consent notice version changes require
a fresh choice. A failed consent write never enables collection; a failed withdrawal write
stops collection for the current process and reports the failure (retry to persist it).
SDK failures are isolated from all device operations. This is separate from the local operation log.

Firebase is initialized lazily only after saved settings are loaded, consent is granted
and offline mode is off. Automatic Android provider initialization is removed. Both SDKs
start with analytics/ad consent denied and automatic screen reporting disabled.
No user ID or advertising consent is set; Android removes AD_ID, AdServices attribution/ad-ID and install-referrer permissions; iOS uses
FirebaseAnalyticsCore without the advertising identity product and disables IDFV collection and SKAdNetwork registration.
SDK-generated lifecycle events, app-instance IDs and technical metadata still exist;
these measurements are pseudonymous, not anonymous.

Offline mode disables collection and requests a reset of local SDK analytics data.
There is no app-owned replay queue. An operation crossing a consent/offline transition
is discarded, so enabling later cannot replay events from before permission.
An SDK upload already in progress may finish. Do not promise network cancellation or
server-side erasure; capture actual traffic on both platforms before publication.
Withdrawal resets local data; it does not delete previously uploaded server data.

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

Operation events contain only result, error_category, stage, initiator, duration_ms and
schema_version. Durations/schema are numeric in Firebase. Result is success/failure/cancelled;
error and stage values are fixed enums. Unknown exceptions map to other. Stage tracking
covers write, verify and reconnection. No progress-per-packet events, search text, OAuth
activity, firmware names/hashes or device identifiers are sent.
A stopped Android queue prevents future jobs; it does not cancel an active write.
A process killed during flashing cannot emit a completion event; do not infer that every
missing completion is a confirmed hardware failure.

## Console and publication checklist

These are account/device actions; code changes do not perform them:

1. Register real TEST/PRODUCTION apps, add configs and verify each project's destination.
2. Set user/event-data retention to **2 months**; turn off Google signals, ad personalization,
   advertising sharing/integrations and unnecessary granular collection. Aggregates can outlive
   user/event retention. Restrict console access to maintainers.
3. Verify the SDK's actual automatic data (including identifiers, device/app metadata and any
   derived approximate location). Complete Google Play Data safety and Apple App Privacy based
   on the chosen binary and enabled console features; this is not a declaration of no collection.
   `publication/analytics-0.9.5.json` is a draft inventory, not a submitted store form.
4. Verify startup, refusal, grant, restart, offline, withdrawal, in-flight operations and SDK
   buffering/network behavior with real test configs on Android and iOS.
5. Use Firebase DebugView in TEST to validate event names, parameters, deduplication and outcomes.
   Confirm no production events originate from debug builds. Debug mode is not enabled in code.
6. Register event-scoped dimensions result, error_category, stage, initiator and schema_version;
   use built-in screen dimensions and register duration_ms as an event metric. Create reports for
   downloads to flash starts, completion rates, errors by platform/app version and operation times.
   These reports cover consenting installations; there is no account-level cross-device funnel.
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

```sh
python3 tools/sync-resources.py --check
./gradlew :shared:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s scripts -p 'test_*.py'
# Compile Firebase against the deliberately invalid CI fixture:
mkdir -p app/firebase/test
cp tools/fixtures/google-services.test.json app/firebase/test/google-services.json
./gradlew -Pfm1Analytics=true -Pfm1AnalyticsEnvironment=test :app:assembleDebug :app:lintDebug
```

Common tests verify no pre-consent collection, persistent refusal/grant, offline restart,
no retroactive completion, withdrawal, deduplication, closed payloads, obsolete/corrupt consent,
and isolation of storage/SDK failures. Instrumentation/network and iOS build results must be
recorded separately; a Linux JVM/Android build does not establish iOS runtime correctness.
