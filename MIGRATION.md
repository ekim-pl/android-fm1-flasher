# Implementation and verification — 0.9.2 / 23

Architecture of the complete Android release **0.9.2** on `main`. A future iOS port is planned; the Apple sources described here are development groundwork, not a released application. This document describes the existing code, not a roadmap or a guarantee of hardware support. For operation instructions see [README](README.md); for recorded build results see [build-23 QA](publication/qa/0.9.2.txt).

## Modules and entry points

| Module | Responsibilities |
| --- | --- |
| `shared` | Protocol, firmware integrity/parser, compatibility, catalog/source models, shared repository, flash session, reconnect gate, fleet/job models and policy, bounded MIDI log |
| `composeApp` | Shared Compose UI, three-language resource dictionary, Devices/Library/Tasks screens, source settings, About/privacy, portable iOS controller |
| `app` | Android `ComposeActivity`, `FlasherService`, MIDI/USB attachment tracking, persistent fleet store, SQLite library/sources, WorkManager, foreground execution and raw USB recovery |
| `iosApp` | Swift/Core MIDI/network/storage/lifecycle adapters for `PortableAppBackend`; no Android fleet/queue adapter |

Android starts `ComposeActivity`; notifications return there. `MainActivity` is legacy reference source and is not registered in the manifest. `fleetAvailable` selects the Android workspace; the portable host keeps the earlier single-instrument navigation. Normal Android writes enter the service through `QUEUE`, not the old `FLASH` action. Recovery uses separate `RECOVERY_*` actions.

The Android ID is `pl.ekim.fm1_flasher`; the Kotlin/Android namespace remains `pl.ekim.fm1_flasher`. The Swift bundle ID is `pl.ekim.fm1-flasher`. Changing the package namespace is not necessary to update the installed fork. Updates require a matching signing certificate.

## Identity and reconnection

`Endpoint` contains MIDI ID, USB address and an attachment generation. `AndroidMidi` retires removed IDs, supplies new generations, checks availability before/after opening and closes late open callbacks. Multiple port pairs or multiple MIDI endpoints on the same USB address are rejected before a write.

`ReconnectGate` snapshots siblings and, on Android, other USB paths. USB detach/MIDI callbacks and polling preserve lost-neighbor evidence. Once continuity is lost, the gate cannot become valid again just because the same address returns. A candidate requires the previous endpoint to be absent, unchanged siblings and one eligible new endpoint; Android also checks the physical USB candidate set. `FlasherService` installs the gate before the session can change modes and validates continuity before upgrade/data sends and final confirmation.

The automatic path requires no MIDI siblings and exactly one USB device/eligible return. Multiple instruments require inline owner pairing; no validated cross-mode serial profile is enabled. Observed serials can reject a contradictory return but do not authorize automatic pairing. Firmware identity, USB address, VID/PID and JEDEC are not physical unit identifiers. Public API topology is not presented as a hub socket map. Human substitution of indistinguishable units cannot be ruled out without stronger physical identity evidence.

`ComposeActivity` serializes USB grants. Immutable PendingIntents retain a unique nonce and URI, matched by the receiver's data-scheme filter. The callback checks nonce, current device, USB attachment epoch and actual `UsbManager.hasPermission`; it does not trust immutable fill-in grant extras. Reconnect approval retains the complete MIDI endpoint generation. Activity recreation discards its pending callback closures; retry is explicit. Real OEM permission/rotation behavior still needs hardware tests.

## Fleet and job storage

`FleetStore` stores an atomically replaced `fleet.json` in private app storage. Device records contain local UUID, alias/color, observed serial, last identity and selected chip. Attachments are session-local and deliberately absent from restored records. A same serial/address/version does not merge new records. Explicit pairing of an offline name with a newly read instrument resets chip selection.

`FlashJob` freezes device ID, alias, endpoint/generation, expected identity, chip, package key/hash/title/version and outcome. A device may have only one queued/running job. Pinning prevents device configuration changes and package replacement/removal while referenced. Each job stores phase, progress, update time and up to 64 stage events.

`FleetPolicy.runPlan` prepares every queued package before executing the first job. The service then rechecks each target and reloads/revalidates its bytes. Jobs run on the serialized worker with `MidiSessionGuard`, foreground notification and a per-job wake lock. `FlashSession` bounds the session to 15 minutes; Android bounds each reconnect wait to three minutes within that deadline. Stop-after-current prevents the next job from starting, without interrupting the current write/verification.

A failure stops the run and records an unconfirmed result. A previously RUNNING job becomes UNCONFIRMED on load; queued jobs retain their old attachment references and cannot become ready until rebuilt. `FlashJournal` remains additional interrupted-session evidence. Neither file is a command to resume a partially completed write. Failed atomic writes roll back the store's in-memory mutation; unreadable fleet storage blocks fleet use and preserves the original file for diagnosis.

A CONFIRMED job records the expected firmware identity after return. It does not prove every flash byte was read back. Persistent history is local and does not include any cloud sync.

## Catalog, packages and documents

Android uses `CatalogDatabase`/`CatalogRepository`, immutable local blobs and migration from older library records. Sources use scoped identities, approved URL ranges, conditional requests and persisted 24–168 hour attempt intervals. The built-in Hub source starts enabled; Awesome FM-1 and background checking start disabled. Source configuration is saved separately from Check now. A failed source retains its previous snapshot. The worker checks catalogs only; installation always requires a user-approved plan.

Package pinning is enforced in the service for removal and replacement. Undo retains the most recently removed valid package in the current service session; it is not a durable recycle bin. Known withdrawals/conflicts and compatibility are rechecked when loading packages. Offline state prevents network work but cannot reveal later withdrawals.

Android XML resources are authoritative for EN/PL/zh-Hans UI text. `tools/sync-resources.py` generates `ResourceKeys.kt` and `CoreStrings.kt`. `:app:bundleProjectDocs` copies `about/*.md`, `LICENSE.md`, `PRIVACY_POLICY.md` and third-party notices into generated assets. About version text is currently a literal in shared Compose; it must be kept aligned with `app/build.gradle` and `iosApp/project.yml` when changing versions.

The refresh crash regression keeps busy state visible while recycling catalog nodes. Favorite accessibility text is computed during composition, rather than observing the whole app state from a detached semantics node. Instrumentation also covers multilingual queue approval and invalidated targets.

## Experimental transports

The logger is bounded and stops before connection/flash work. ROM chip detection does not bind a JEDEC result to a selected MIDI unit. Recovery remains separate, opt-in and hash-restricted to stock V15. It checks known boot/chip conditions, creates/verifies a backup and uses a tokenized prepared plan; only the allowed application sectors may be changed. There is no automatic OTA-to-ROM fallback. No physical recovery success is claimed.

## Android build and test commands

JDK 17, Android SDK platform 36, Build Tools 35.0.0. Application minSdk is 34 even though the reusable library modules have lower minimums. Use the checked-in Gradle wrapper.

```sh
python3 tools/sync-resources.py --check
./gradlew :shared:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

For one known, selected test device, replace `DEVICE_SERIAL` with its actual adb serial:

```sh
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w -e class pl.ekim.fm1_flasher.FleetUiTest pl.ekim.fm1_flasher.test/android.test.InstrumentationTestRunner
```

Run the explicit classes separately: `ExperimentalFeaturesTest`, `CatalogDatabaseTest`, `BackgroundCatalogTest`, `FirmwareSelectionTest`, `UsbPermissionIntentTest`, `MainActivityLaunchTest`, `ComposeCatalogUpdatesTest`, `FleetUiTest`, `OperationLogServiceTest`, `SettingsUiTest`, `SourceAuthenticationTest` (all in `pl.ekim.fm1_flasher`). This avoids the legacy runner's unbounded discovery startup timeout and isolates service/preferences fixtures. The test runner's exit alone is insufficient: inspect the reported OK/failure result.

Build 23 passed 62 shared JVM tests and 129 Android JVM tests (four external-fixture cases skipped), plus 44 Android instrumentation tests. Debug APK/test APK, lint, generated resources and iOS Kotlin metadata compilation passed. The [current report](publication/qa/0.9.2.txt) records the artifact. Historical build reports remain unchanged.

## iOS build boundary

The portable controller supports the prior Hub repository, single selected MIDI instrument, shared transfer engine, explicit reconnect, local journal, preferences and logger. It does not implement Android's fleet queue, multi-source SQLite scheduler or raw USB ROM transport. It requests finite background time and disables automatic screen sleep during a session; keep the app foregrounded.

On a suitable macOS/Xcode development host with JDK 17, Android SDK and XcodeGen installed:

```sh
cd iosApp
xcodegen generate
open FM1Flasher.xcodeproj
```

Choose scheme `FM1Flasher`; configure a signing team for a physical device. The build phase invokes `:composeApp:embedAndSignAppleFrameworkForXcode` for `FM1Compose`. Configured targets are `iosArm64` and `iosSimulatorArm64`; no Intel simulator target is configured. Deployment target is iOS 15.0. These are project configuration facts, not verification that an older Mac or Xcode installation can build the project.

```sh
./gradlew :composeApp:compileIosMainKotlinMetadata
# On a compatible macOS host, separately validate native tests/builds:
./gradlew :shared:iosSimulatorArm64Test
```

Linux metadata compilation does not compile Swift, link/sign an iOS application or produce an IPA. No Xcode/native iOS result is available for build 23.

## Release boundaries

0.9.2/23 is a prepared experimental test artifact with regenerated documentation; preparing it does not publish a GitHub Release or deploy Pages. Debug signature continuity with build 18 was checked. A future rebuild after document changes has different APK bytes and needs a new checksum and packaging verification. Do not replace the historical report's checksum with one from a different build.

Android CI tests/builds every branch. Pages builds every branch; deployment is allowed from the default branch or manual dispatch on task/multiplatform-rework. The inherited Google Play release and production workflows still target the original package; the F-Droid recipe and older store material also concern the original application. See [publication notes](publication/README.txt). None of these artifacts establishes a store release of this fork.

Physical acceptance requires multiple FM-1 units, the intended powered/passive hubs and real phones; test mode descriptors, serial behavior, grant denial, missed/out-of-order events, safe reconnect faults, interruption and recovery separately. Emulator assertions establish software behavior, not electrical stability or hardware flash success.

## Operation diagnostics and cleanup (0.9.2 / 23)

`OperationLog` stores bounded JSON Lines in `diagnostics/operations.jsonl` (256 KiB, atomic replacement, complete oldest records pruned first). Callers serialize I/O: Android uses a dedicated executor, portable uses a coroutine dispatcher limited to one task. Diagnostic write failures never abort a transfer. Events use epoch-millisecond timestamps, level, stable event keys and bounded messages; URL queries/fragments are removed. Android records service lifecycle, dispatched actions (excluding high-frequency logger polling), USB/MIDI topology changes, status/transfer phases and caught failures. Portable records actions, statuses and task failures. This is an operational trace, not raw MIDI capture or an OS crash dump.

Experimental settings expose export through the native document picker/share UI and clearing with confirmation. Both Android service methods independently enforce experimental mode and idle state. Clear removes the stored log and recent in-memory display; new activity resumes logging. Exported copies are independent.

Android bulk cleanup clears SQLite download rows/selection and private library package/record files, including damaged/orphan files, then refreshes UI. It refuses all queued/running jobs before deletion. Favorites, source configuration, fleet/history, diagnostic logs and recovery backups use separate storage and are retained. Portable repository offers the corresponding package/record cleanup. Bulk removal deliberately has no Undo; ordinary single-package removal keeps its existing Undo. Partial filesystem failures are reported; retrying cleanup removes remaining files.

The owner confirmed successful physical-device testing of build 20. This does not replace acceptance of the new diagnostics/cleanup paths or of ROM recovery on each supported chip.


## Full-screen settings and source adapters (0.9.2)

`SettingsScreen` and `SettingsPage` present settings, source editing, per-package compatibility and the settings privacy policy as full pages with hierarchical Back handling. Draft nonsecret fields and the previous workspace tab survive Activity recreation. Secret input fields are deliberately excluded from saved instance state. Destructive confirmations and the chip-detection result remain dialogs.

The public `shared.CatalogProvider` interface separates endpoint/response decoding from Android `CatalogRepository` transport, scheduling and storage. `CatalogProviders` registers `thefirmwarehub`, `awesome-fm1`, `repo-url` and `pkg-url`, retaining `catalog-json` for older custom sources. GitHub repositories import published releases/prereleases and `.fwsc` assets, bounded to 20 pages/5000 packages. Digest-less assets require full validation; digest-bearing assets need a header read, with full hash/product validation before local storage/installation. Manual compatibility is keyed by SHA-256; a new binary never inherits a previous file's declaration. Unknown compatibility blocks installation, while an unknown duplicate does not contradict another known declaration.

Source configuration stores nonsecret authentication metadata and an authentication revision. `SourceCredentialStore` encrypts credentials, tokens and pending PKCE state with Android Keystore AES-GCM and source-ID associated data. HTTP Basic, Bearer, custom API-key headers and OAuth 2.0 are supported. OAuth supports S256 browser PKCE and Client Credentials, client_secret_basic/client_secret_post, refresh tokens, single-use state with a ten-minute expiry, and the exact callback `pl.ekim.multiplatform-fm1-flasher://oauth/callback`. Token POSTs refuse redirects. Resource authorization is confined to the configured source origin; GitHub uses api.github.com, and CDN redirects receive no source credentials. Background/catalog/artwork token refresh is serialized; compare-and-write prevents stale exchanges from restoring deleted or replaced credentials. Error bodies and callback codes are not copied into logs. A singleton Android operation logger serializes foreground/background diagnostic writes.

The shared contracts and settings compile as iOS Kotlin metadata. The new provider transport, Keystore authentication and OAuth callback adapters are Android implementations; the existing portable Apple backend still uses its prior Hub path. Native iOS provider/authentication parity and a real OAuth issuer are not claimed by emulator fixture tests.

Protocol references: [GitHub releases](https://docs.github.com/en/rest/releases/releases), [GitHub assets](https://docs.github.com/en/rest/releases/assets), [OAuth 2.0](https://www.rfc-editor.org/rfc/rfc6749), [PKCE](https://www.rfc-editor.org/rfc/rfc7636).
