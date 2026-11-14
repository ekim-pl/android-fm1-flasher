# Multiplatform implementation and validation

## Application entry point

Android now starts `ComposeActivity` directly into the shared catalog. There is
no diagnostic launcher, prototype screen or button to switch to a second app UI.
Notifications also return to Compose. The legacy Views source is retained for
reference, but its Activity is no longer registered or reachable from the app.

This build is 0.8.1, Android versionCode 18. The functionality baseline
is published android-fm1-flasher tag `v0.8.1`, commit `3fa9d67`. The original Android app remains on the main branch;
this implementation is published on `task/multiplatform-rework`. The fork keeps `pl.ekim.multiplatform_fm1_flasher`, so it installs
alongside the original Android app. Its existing private downloads and preferences
are preserved; it does not import another application's private data.

## Build 18 About presentation

The shared About screen groups the bundled guide into themed section cards, with
proper paragraph/list spacing, bold emphasis and inline links. A dedicated privacy
card summarizes local storage and network metadata and opens the complete bundled
policy offline. English, Polish and Simplified Chinese have matching content.
Awesome FM-1 is described as a repository without singling out a firmware entry.

## Build 17 refresh crash fix

A real refresh of the cached catalog crashed in Material3 ChildSemanticsNode with
`visitAncestors called on an unattached node`. The favorite button's semantics
lambda read the delegated whole-app State value. Busy/status changes invalidated
that observer even after a lazy-list node detached. Compute the favorite label
during composition and capture only the resulting string in semantics.

The new regression test keeps busy state visible across frames, activates the
actual refresh button and refreshes while scrolling a 40-entry list. It reproduced
the crash on build 16 and passed after the fix. Earlier source-dialog tests did not
cover this frame sequence. This change does not modify database or firmware bytes.

## Refresh from published 0.8.1

The Android host incorporates 0.8.0/0.8.1 multi-source SQLite catalogs, native
WorkManager scheduling, scoped source identities, conditional requests, per-source
request budgets and locally approved download/artwork policies. Shared Compose adds
source filtering, source labels, source configuration/status/removal/defaults and
the optional background-check setting. Firmware Hub is enabled by default; Awesome
FM-1 and background checks are disabled on a new installation. Updated About guides
in three languages and privacy/license resources are bundled.

Updating the dedicated emulator from build 14 migrated its 214 Hub entries, Hortator
package, favorite, selection and Hub timestamps to SQLite. The actual .fwsc bytes
retained SHA-256 ac81af292e035ee0c4b8b7ab3a78b942e4f03deee31ae213a2613c743cd9110f.
The migration also accepts the previous Compose boolean browsing preferences,
where the original Android Views stored strings.
Awesome FM-1 was explicitly enabled for a UI test and a package was
downloaded from its approved publisher endpoint. SHA-256/CRC validation succeeded;
its `unknown` chip compatibility remains non-flashable. A regression test exercises
same-ID offers from different sources while source settings are open. List keys,
details and download actions use the scoped source/firmware identity.

The native SQLite/source scheduler is Android-specific. The existing iOS host
retains the shared Hub repository and manual MIDI-session controller; its
multi-source persistence/conditional-network adapter and background scheduler have
not been ported. No iOS 0.8.1 feature parity or Apple build is claimed.

## Implemented

`shared` contains protocol framing/assembly, UFW/SHA-256/CRC validation, flash-session
logic, chip compatibility, catalog models/parser, category/filter/sort rules,
repository rate limiting, verified immutable package storage, settings repair,
withdrawal checks and MIDI stream decoding/log buffers. Android now uses the upstream transactional SQLite repository with migration from
the prior shared JSON library. The common legacy repository remains in the iOS path.

`composeApp` provides the shared application screens:

- Catalog search, categories, sorting, combined downloaded/favorites filters,
  pull-to-refresh and a refresh button honoring the daily request budget.
- Firmware details, author links, artwork caching, package metadata, downloads,
  verification, favorites, removal, package updates and offline use.
- Flasher with explicit target selection, identity checks, local-package selection,
  chip compatibility, confirmation, progress, logs and restart-port confirmation.
- Experimental MIDI Logger with selected outputs, source/channel/type filters,
  timing-message filtering, paused autoscroll, bounded logs and export.
- Persistent language (Polish/English/Simplified Chinese), theme, offline and
  experimental-feature settings; bundled About/license/privacy content.
- Android ROM chip detection, verified backup/export and protected V15 recovery,
  including the prepared-plan confirmation. Experimental tools default to off.

The Android host preserves its native foreground service, wake lock, USB grants,
conservative automatic reconnection, session journal, document pickers and protected
ROM transport. Manual confirmation binds the action to the displayed package hash
and endpoint before service admission; the worker rechecks identity and bytes.

The Swift host uses the same Compose screens and `PortableAppBackend`, with native
Core MIDI, bounded HTTPS requests, atomic private storage, document sharing and
lifecycle handling. It includes catalog/download/library operations, identity and
full two-stage flash orchestration, a passive multi-output MIDI logger, persisted
settings, a flash journal and both explicit reconnections. The common controller
is integration-tested with simulated native endpoints; this is not a claim of
physical iPhone flashing success.

## Platform differences

On iOS, reconnection always requires confirmation of the same instrument's new
port. Earlier endpoint IDs are excluded, and names never authorize automatic
selection. An unchanged or ambiguously reused endpoint ID can therefore require
restarting the operation rather than guessing the instrument.

USB ROM recovery and JEDEC detection remain Android-only. The iOS UI does not show
these controls: Core MIDI does not provide the raw USB ROM transport. This was an
explicit constraint of the original migration plan.

During iOS flashing the host disables automatic screen sleep and requests finite
background time. Keep the application open; iOS can suspend it after background
time expires. The journal does not mark an interrupted operation as confirmed.
There is no cancel-writing button.

## Verification

On Linux / Android 16:

- 108 Android JVM tests passed; four optional external firmware/catalog fixture
  tests were skipped. This includes a full offline common-controller session with
  both explicit reconnections, no HTTP requests and a confirmed journal.
- 41 shared JVM tests passed. Nine new repository tests cover rate-limit persistence,
  cache corruption, package revalidation, metadata compatibility, failed replacement,
  settings repair, missing blobs, withdrawals and asset resolution.
- Debug APK, instrumentation APK and Android lint passed. Common and Kotlin/iOS
  source-set metadata compilation also passed; this does not compile Swift or link
  the Apple application.
- 26 instrumentation tests passed, including opt-in/service guards, SQLite imports/migrations, source
  policy and background-work guards, Compose launch and accessibility regression,
  scoped package selection and USB PendingIntent delivery. See the build validation
  logs in dist for this run.
- Emulator checks exercised the real catalog (214 entries), Hortator 1.0.1 download
  with integrity validation, favorites and selection of the saved package. Writing
  remained disabled without a MIDI device.

No physical FM-1, USB recovery or iOS binary was tested. This machine has no
macOS/Xcode host; Swift/native framework compilation and iPhone hardware validation
remain required before distributing an iOS release. No production-readiness or
cross-platform hardware parity is inferred from passing Android tests.

## Build Android

JDK 17, Android SDK 36 and Build Tools 35.0.0:

```sh
python3 tools/sync-resources.py --check
./gradlew :shared:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. A copy and checksum are in `dist/`.
The project pins Kotlin 2.2.21, Compose Multiplatform 1.9.3, AGP 8.13.2 and JSON
serialization 1.9.0. Android XML remains the translation source; regenerate the
shared dictionaries after adding or changing a resource.

## Build iOS on macOS

Install JDK 17, Xcode, the Android SDK (the root Gradle project includes Android)
and XcodeGen. Then:

```sh
cd iosApp
xcodegen generate
open FM1Flasher.xcodeproj
```

Select the FM1Flasher scheme and an Apple Silicon simulator or physical iPhone;
select your signing team for hardware. The build phase embeds `FM1Compose` using
`:composeApp:embedAndSignAppleFrameworkForXcode`. Supported targets are iosArm64
and iosSimulatorArm64; deployment target is iOS 15.0. Bundled About/license files
are included by the XcodeGen resource entries.

```sh
./gradlew :shared:iosSimulatorArm64Test
```

Before accepting iOS hardware support, test identity, stock/community firmware,
both restarts, ambiguous hubs, disconnects, screen locking and background expiry.
On Android, qualify both flash-chip types and ROM recovery with verified backups.
