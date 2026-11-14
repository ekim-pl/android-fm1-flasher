# FM‑1 Flasher for Android

## Experimental features in 0.7.0

Open **Settings → Experimental features** to enable MIDI Logger, chip detection and ROM recovery. The switch is **off by default**, persists across restarts, and applies to debug and release builds. Disabling it hides these controls, stops the logger and invalidates the prepared recovery plan. It cannot change during an operation. The service also checks the switch before starting experimental operations. Physical USB recovery remains experimental.

Chip detection reports the ROM device and JEDEC ID without changing the selected MIDI target. Reconnect the same physical unit in MIDI mode and confirm its chip type manually. Flashing now requires OTA identity after the loader restart. Download and artwork requests have a total time budget; artwork is limited to the Hub origin and a disposable 24 MiB disk cache. Corrupt library settings are recovered conservatively in offline mode.

The application source code is available under the MIT license; see [LICENSE.md](LICENSE.md).
Data handling is described in the [privacy policy](PRIVACY_POLICY.md).

An experimental Kotlin/Android 14+ app for selecting one M‑VAVE FM‑1 on a USB OTG hub, downloading firmware from a catalog and installing it over USB MIDI SysEx. A user has successfully flashed a physical FM‑1 with an earlier build. Automatic reconnection still needs further hardware testing.

## Language and appearance

Open **Settings** in the top bar to choose Polish, English, Simplified Chinese or the system language. The same choice is available in Android’s per-app language settings. All three languages are bundled in both APK and AAB files and can be switched without internet access. Warnings, errors, logs and notifications are also translated; descriptions supplied by firmware authors remain in their original language.

Choose a light, dark or system theme. Settings also includes an offline privacy policy.

In landscape, at widths of at least 600 dp, navigation moves to a rail on the left and catalog filters appear beside the results. Flasher shows the device and local package side by side; firmware details use a smaller preview and a separate action column. Rotation preserves the visible catalog position and open package details.

## About the app

The **About** tab uses short, independent Markdown files in [about](about): [Polish](about/README.pl.md), [English](about/README.md) and [Simplified Chinese](about/README.zh-Hans.md). They are bundled automatically during the build and work offline. Content follows the app's language and theme. This README remains the project's technical documentation and is not bundled with the app.

The app description links to the license and privacy policy. Internet links open the appropriate system app.

## Project website and online documentation

Public source: [GitHub](https://github.com/ekim-pl/android-fm1-flasher). The [project website](https://ekim-pl.github.io/android-fm1-flasher/) provides the complete website and documentation in [English](https://ekim-pl.github.io/android-fm1-flasher/en/), [Polish](https://ekim-pl.github.io/android-fm1-flasher/pl/) and [Simplified Chinese](https://ekim-pl.github.io/android-fm1-flasher/zh-CN/).

The [GitHub Pages workflow](.github/workflows/pages.yml) builds the website, technical guide, About pages, publishing guides, licenses and privacy policies. Language links keep the current page. Pull requests and branches check the build; changes to the default branch deploy automatically. In a new GitHub repository, select **Settings → Pages → Source → GitHub Actions**. See the [setup, local build and editing guide](website/pages.md).

Repository documentation is maintained in English. Website translations live in `website/pl/` and `website/zh-CN/`; app and store translations remain in their existing locale directories.

## Publication materials

The [publication guide](publication/README.txt) explains the material pack and remaining steps. [fastlane/metadata/android](fastlane/metadata/android) contains descriptions, changelogs, icons, feature graphics and screenshots in three languages, in a format useful for Google Play and F-Droid. [Static privacy pages](publication/privacy/index.html) can be hosted on a public HTTPS site. Raw screenshots and store-form materials are in [publication](publication).

<a id="uruchomienie"></a>

## Getting started

Download a test APK from [GitHub Releases](https://github.com/ekim-pl/android-fm1-flasher/releases). It is signed with a debug key and intended for testing, not store distribution. Local working files are `dist/FM1-Flasher-debug.apk` and `dist/FM1-Flasher-publication.zip`; `dist/` is not part of the repository.

1. Install the APK on a phone with USB Host/OTG and MIDI support. Connect a powered hub and instruments using data cables.
2. In **Flasher**, select a port and press **Connect to selected port**. After Android grants USB permission, the app reads only that device's identity. The MIDI name is not the selection criterion: the USB address and MIDI ID distinguish identically named devices.
3. Leave the flash-chip type unknown unless you know this unit's actual JEDEC ID. The selection applies to the current connection and resets when you change instruments.
4. Open firmware details in the catalog and choose **Download to device**. After downloading, choose **Use for flashing** or select the package from the list in **Flasher**. Both places use the same saved selection. Downloading is separate from writing. The author's website or repository is available in the details.
5. Back up your data with the firmware's own tools, check the target port and package in the confirmation dialog, then start the update.
6. After each restart, the new port is detected automatically if Android sees exactly one USB device and one new eligible MIDI port. Grant Android USB permission if needed. With multiple devices or ports, select and confirm **the same FM‑1's new port**. After the final restart, the app checks the installed firmware's identity.

<a id="kilka-fm1-na-hubie"></a>

## Multiple FM‑1 devices on a hub

- No port is selected by default. Requests and data go only to the explicitly selected device. Only one update runs at a time.
- After a restart, the previous MIDI ID must disappear. The reconnection list excludes earlier MIDI IDs and the USB addresses of other devices present before that stage.
- Automatic reconnection runs only after the previous MIDI ID disappears, with exactly one USB device and one eligible MIDI port. Multiple devices, including non-MIDI devices, or multiple ports require user selection. A MIDI name does not identify a physical hub socket.
- If several instruments change addresses at once, or another device is connected, multiple candidates may appear. Do not guess. The app does not automatically switch to another available FM‑1.
- A USB address identifies the current connection, not the instrument permanently. Identify individual units by connecting them one at a time before updating. Do not disconnect a device during writing.

## Library, favorites and offline use

- **Catalog, Favorites, Downloads and Flasher** are separate tabs. Cards show a preview, short description, author, category, version and local-package status. Author-supplied artwork is displayed and cached for offline use; otherwise a category illustration is used. Full descriptions and technical information are in the details.
- Categories include synthesizers, grooveboxes, drums and samplers, trackers, chords, utilities, original M‑VAVE and other firmware. Combine search with a category, **Downloaded only** and **Favorites**, including directly in the catalog.
- Sort by name A–Z, newest releases, author A–Z or recently downloaded. Favorites and browsing preferences are local and survive app restarts.
- Firmware is kept in persistent private app storage, not the Downloads folder. Packages survive app closure and updates; uninstalling or clearing app data removes the library.
- After downloading, enable **Offline** and use **Downloads** or **Downloaded only**. Selecting a local package and starting a flash does not make network requests. An expired catalog does not block an already verified local package. The file on disk is checked again with SHA‑256 and CRC before writing.
- The catalog is cached persistently and shown immediately at launch. Each launch and return to the app checks internet availability; outside offline mode, an expired or missing catalog is refreshed automatically. Network loss or a download error leaves the previous list available.
- **Pull down from the very top of the list** and release after the message to check the catalog manually. The gesture does not take over scrolling in the middle of a list or horizontal scrolling; **Check** remains available as an alternative. Both methods honor the source's once-per-day limit: a fresh cache does not trigger another HTTP request.
- An **Update** label indicates a changed file. Update one package in its details or all packages in **Downloads**. Changes are detected by SHA‑256, even if the author keeps the same version name.
- A new package replaces the old one only after downloading and validation complete. Failed updates leave the previous package intact. Updating the library does not start flashing; if the selected file changes, select a package again. **Download again** can repair a local file. Removing a local package does not remove its favorite or change the instrument's firmware.
- If the last known catalog has removed a firmware entry, writing that package is blocked. Older saved versions use compatibility information for their own file, not the newer version. Offline use cannot discover changes or withdrawals published after the last catalog download.

## Catalog sources and SQLite

Open **Settings → Firmware sources** to manage independent subscriptions. **The Firmware Hub is enabled by default**. **awesome-fm1 is disabled by default**, using `https://raw.githubusercontent.com/ekim-pl/awesome-fm1/main/catalog.json`. It supplements the Hub; it does not mirror its database or firmware files.

- Add or edit a public HTTPS catalog URL, name, interval (24–168 hours), and approved package/artwork URLs. A trailing `/` grants a directory; otherwise the rule is an exact path. Changing download policies invalidates their previous freshness/validators and requires another check, which still respects the saved minimum interval. GitHub release resolution verifies the declared repository and asset. Catalog redirects must remain at the configured endpoint; package/artwork redirects must stay within their approved scopes.
- **Save and check** persists a new subscription disabled before making its explicit request, so even failed checks retain the request budget. Review the referenced hosts in Details, then configure approved URLs and enable the source. Plain **Save settings** makes no request. In offline mode, checking waits until you go online and choose **Check now**.
- **Check now** is also available for a disabled source as a one-time user request. Neither it nor pull-to-refresh bypasses the minimum interval. Hub attempts, including errors and `304`, are limited to one every 24 hours. Re-enabling, deleting/re-adding, migrating and restoring defaults retain that budget.
- Disabled sources make no automatic catalog, package or artwork requests. Their offers disappear from Catalog; downloaded and favorite records remain with source information. Removing a custom subscription retains provenance, files, favorites and known withdrawals. Restoring defaults enables Hub and disables awesome-fm1 without deleting custom subscriptions.
- Each source has independent freshness, conditional requests, errors and a transactional snapshot. A valid empty snapshot withdraws its offers; failed or invalid responses preserve the previous snapshot. Downloads and batch updates use the selected source's freshness and report unavailable sources separately. Conflicting compatibility claims for identical bytes block use.
- **Filter by source** limits the catalog, favorites or downloads. Details show source, release date, last successful check and reasons for blocked downloads. Source details also show the publisher's date, URL policy, license and unsigned status.
- **Check catalogs in the background** is optional and off by default. One WorkManager job checks enabled catalogs approximately daily when a network is available. It shares the foreground operation lock, pauses offline, respects request limits and never downloads or installs firmware. Android controls the exact execution time.

The platform `SQLiteOpenHelper` database is the app's source of catalog, source-policy, downloaded-package metadata, favorites, selection, offline and browsing state. Each offer is identified by `(source_id, id)`; product names and hashes do not merge projects. Firmware bytes remain in `files/library/packages/<sha256>.fwsc`, with one downloaded version per offer and reference-aware deletion. Theme/language and experimental-feature settings remain Android preferences.

On upgrade, one transaction imports legacy catalog JSON, request timestamps, package records, favorites, selection, offline and browsing settings. Existing package bytes are revalidated and retained; original JSON files remain as recovery copies. A corrupt individual record produces a warning without deleting its files; a database write failure rolls back migration. The app never silently replaces a failed database with an empty library.

Initial catalogs are **unsigned**. SHA-256/CRC and chip checks remain independent of catalog delivery. **SHRUB is downloadable from its developer but remains blocked for flashing with `chip: unknown`**. Its package parser has been checked; hardware compatibility has not. Publisher automation, detached signatures/key rotation and physical SHRUB tests are separate future stages, not completed by this application change. See the [shared implementation plan](https://github.com/ekim-pl/awesome-fm1/blob/main/docs/implementation-plan.pl.md).

## Protocol and compatibility

- Catalog data: [The Firmware Hub](https://thefirmwarehub.com), [catalog usage terms](https://thefirmwarehub.com/kb/). Downloads are limited to once per day, with an identifying User-Agent and local cache. New downloads require a current catalog; previously verified local packages also work offline.
- Hub downloads require a specific GitHub release tag and asset ID, except for the pinned official package. Additional catalogs can use a direct HTTPS `pkg` in an explicitly approved URL scope. Redirects, public DNS addresses, byte limits and deadlines are checked before accepting data. A catalog cannot grant itself new URL permissions. The app does not download packages from the Hub mirror.
- Before use, the app checks the catalog's SHA‑256, the `FM-1_<number>` identity, the AC791N processor, UFW header and file-list CRCs, file ranges, and the CRCs of `flash.bin` and `ota.bin`. Auxiliary encrypted manufacturer files carry plaintext CRCs; the full-package SHA‑256 protects them. Their CRCs are not checked against encrypted bytes.
- Unknown/new flash chip: only packages marked `chip: new` by the catalog are allowed. `chip: old` requires explicitly selecting the older chip. Unknown compatibility blocks writing. The pinned original V15 allows only the older-chip choice because the catalog does not declare new-chip support. A catalog label does not replace testing that firmware.
- Two-stage updates check frame checksums, SysEx fragments, read ranges and timeouts. Success requires an acknowledgment of the updater's final signal and a matching identity after restart.
- A foreground service and wake lock keep the session active when you switch apps. The system can still terminate the process. There is no stop-writing button.

A successful identity check confirms a device response, not a readback of all flash memory. Different firmware can share an identity. Power loss, an unsuitable package or selecting the wrong instrument after a restart may require hardware recovery. Automatic reconnection covers planned restarts during an update, not arbitrary connection loss while writing.

The regular MIDI update flow does not support Bluetooth updates, batch flashing, preset/memory backups, bootloader rewriting, hardware USB_KEY generation, persistent hub-socket mapping, importing unverified local files or packages with other identities such as `FM1_OFFICIAL_OPEN`. Chip detection and ROM recovery are separate opt-in experimental features described below; they do not expand normal firmware compatibility.

<a id="budowanie-i-testy"></a>

## Building and testing

Requires JDK 17, Android SDK Platform 36 and Build Tools 35.0.0. Set `ANDROID_HOME` or `sdk.dir` in an untracked `local.properties` file.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Actual SQLite, transactions and migrations are tested on Android, not through the JVM Android stubs:

```sh
./gradlew :app:connectedDebugAndroidTest
```

CI runs platform tests on API 34 and 36. Unit tests cover the shared importer, dates, source policies, response validation, retry budgets, redirect/size/deadline protection and source-aware browsing. Instrumentation covers initial defaults, migration/restart/rollback, corrupt legacy records, source removal, conditional responses, partial failures, late responses and shared package files. Tests use synthetic packages; real catalog/package fixtures can be supplied locally.
AAB: `./gradlew :app:bundleRelease`, output `app/build/outputs/bundle/release/app-release.aab`. It is unsigned without key configuration; GitHub Actions signs it with the upload key from secrets.

The JVM tests cover the protocol and UFW packages, catalog, categories, combined filters, sorting, library and preference persistence, SHA‑256 updates, refresh gestures and cancellation, automatic-refresh conditions, retaining an old package after failure, file corruption, older-version compatibility, withdrawn packages, neighboring-port exclusion, three-device sessions, MIDI logging and simulated ROM recovery. Four fixture-dependent tests read real files from disk without bundling firmware in the repository:

```sh
FM1_TEST_PACKAGES=/path/stock-v15.fwsc:/path/stock-v16.fwsc \
FM1_TEST_CATALOG=/path/catalog.json \
./gradlew :app:testDebugUnitTest
```

Catalog-source validation (November 14, 2026): 108 JVM tests passed with the saved Hub catalog and real SHRUB package; two V15 recovery fixture tests were skipped because `FM1_RECOVERY_V15` was not supplied. All 22 instrumentation tests passed on Android 16, including real SQLite migrations/rollback, WorkManager scheduling and source-specific package selection. `lintDebug` and `assembleDebug` passed (lint retains existing warnings). Manual emulator checks covered EN/PL/zh-Hans screens, disabled defaults, explicit custom-source checks, direct SHRUB downloading/validation, favorites, source filtering, subscription removal and persistence after restart. The Hub fixture was reused without another catalog request. No physical SHRUB flash was attempted; unknown-chip compatibility remains blocked.

The optional repository-wide OSV audit reports 101 advisory matches in previously pinned dependencies (the audit also includes build/test, website and publication tools). The 40 dependency versions added by this change returned no advisory matches. Updating the existing build toolchain is separate from catalog integration.

Previous release validation: all 84 JVM tests passed with real V15/V16 package fixtures and simulated recovery on both chip types; lint and the debug APK build passed. Physical USB recovery and multi-port MIDI reception still need hardware testing. The Android 16 emulator checks the UI and networking, not physical USB transport.

Emulator checks covered app launch, reading 202 catalog entries, downloading and persisting Hortator 1.0.1 with SHA‑256/CRC validation, saving favorites, combining **Downloaded only** and **Favorites** with a category, sorting, the offline library and the actual pull-to-refresh gesture. Installation stays disabled without a USB device.

A user confirmed a successful update of a physical FM‑1. Automatic restarts and multiple devices on a hub still need verification: first detect several instruments and select one, then perform a controlled update with both restarts confirmed and check that other instruments' firmware is unchanged.

## GitHub Actions and Google Play

The [setup guide](publication/github-actions.txt) covers the first upload, signing key, Google service account and GitHub secrets.

Automatic Google Play publishing is disabled in repository settings until the store and keys are configured. The release-workflow behavior below applies after it is enabled again.

- **Android CI**: pushes to branches and pull requests run JVM tests, lint and a debug APK build. APK and reports are available as artifacts.
- **Google Play release**: pushing a `v<versionName>` tag runs tests, release lint, a signed AAB build and upload to `internal` with changelogs. A manual run with `publish` disabled only builds the AAB for the first Play Console upload.
- **Google Play production**: a manual run with a chosen `version_code` promotes the tested package from `internal` to `production` without rebuilding or uploading the AAB again.

Before every new release, increase `versionCode`, set `versionName` in `app/build.gradle` and add `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`. The tag must match `versionName`. GitHub does not replace initial store setup, required testing or Google Play verification.

## Technical sources and licenses

Protocol: [AL‑255/FM‑1‑RE](https://github.com/AL-255/FM-1-RE/blob/main/docs/io/11-ota-protocol.md) and its `tools/fm1_ota.py` client (WTFPL), [package format](https://sbegueria.github.io/fm1-dev-wiki/Firmware-Package-Format.html), [Android MIDI API](https://developer.android.com/reference/android/media/midi/MidiManager). The Android implementation is in `app/src/main/java/pl/ekim/fm1_flasher`.

Catalog compilation: The Firmware Hub, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The app filters the list to supported identities, selects the required fields and adds a pinned stock entry. Descriptions belong to their authors; each entry shows its author and repository link. Firmware licenses remain with their projects; no firmware is bundled in the APK or application source. Gradle Wrapper: [Apache 2.0](https://github.com/gradle/gradle/blob/v8.13.0/LICENSE).

Networking and background checks use OkHttp/Okio and AndroidX WorkManager ([Apache 2.0](third_party/Apache-2.0.txt), [notices](third_party/android-libraries-NOTICE.txt)), with pinned and SHA-256-verified dependencies.

Markdown rendering: [CommonMark Java](https://github.com/commonmark/commonmark-java), [BSD-2-Clause license](third_party/commonmark-LICENSE.txt), also bundled with the app.

### Flash chip detection

Use **Detect chip (download mode)** with exactly one instrument connected in
JieLi USB download mode (`4C4A:8057`). Like
[m-flasher](https://github.com/jvitkauskas/m-flasher/blob/620c0f4c582d4864e1ae23e6fc4560c64441b138/src/jl.rs),
the app uploads a loader to RAM and reads JEDEC ID with command `FC0A`; it
never erases or programs flash during detection. `856014` is the older FM-1
chip; `B36014` is the newer chip. Other IDs remain unknown.

After reading the ID, power cycle back into normal MIDI mode, connect in the
app and select the reported chip type. Detection is not inferred from the
firmware version or transferred automatically to a different USB connection.
This USB path still needs validation on physical hardware.


## Experimental multi-port MIDI logger

The experimental build uses the original name **FM-1 Flasher** and package ID `pl.ekim.fm1_flasher` (version `0.7.0-experimental`). Android 14+ is required. Data from the separate `.experimental` app is not migrated.

Open **MIDI Logger**, choose one or more instrument **outputs**, grant USB access for each instrument and press **Start**. The logger only receives: it never identifies devices, opens their MIDI inputs or sends MIDI. Multiple ports on a device and multiple USB devices are supported, including devices without MIDI inputs. No firmware change is required; only data actually transmitted by the instrument can appear.

The log shows receive time, device/path/port, channel, decoded MIDI and HEX. Long-press an entry for full HEX. Filters select source/channel/type and can hide Clock/Active Sensing. Pausing scrolling keeps reception active. Clear removes buffered entries; Export saves the entire current buffer as UTF-8 text through Android’s file picker, regardless of filters. Protocol names and diagnostic annotations use technical English. Running status is expanded into complete messages; the export is not a byte-exact recording of callback boundaries.

Memory is bounded to 5000 entries and 4 MiB of raw message data, plus a bounded input queue; a single SysEx is limited to 64 KiB. Evictions, input losses and truncated SysEx are marked. The logger cannot detect messages lost before Android delivers them. It stops when the app enters the background, including when opening the export picker. Rotation retains a running session; reconnecting devices requires selecting their new ports. Flashing, recovery, download-mode requests or chip detection stop the logger first; restart it manually afterward.

Validation for this experiment: parser/buffer unit tests and the existing JVM suite, lint and debug APK build. Physical multi-port USB reception and a 30-minute hardware load test remain to be done. Installation succeeded on the Android 16 emulator; UI automation was blocked by another session holding that emulator.
## Experimental ROM recovery

The experimental build uses application ID `pl.ekim.fm1_flasher` and version
`0.7.0-experimental`. Physical USB recovery has **not** been validated.

Open **Flasher → Recovery and backups**. A responding FM-1 can be asked to enter
ROM download mode; a device already in that mode needs no MIDI connection.
Connect exactly one download device (`4C4A:8057`, `WL82 UBOOT1.00`) and grant USB
permission. Download mode is not a filesystem: never format it.

- **Diagnose and back up flash** checks JEDEC ID and chip key, reads the complete
  1 MiB twice, compares both reads, and syncs the backup and metadata to storage.
  Both `856014` and `B36014` are accepted with chip key `980F`. An unknown boot
  area still permits backup, but blocks recovery writing.
- **Prepare V15 recovery** uses the selected downloaded package. Only official
  `FM-1_015`, package SHA-256
  `db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a`, is allowed.
  Its extracted firmware region is independently hash-checked. A recognized
  stock V15/V16 boot-area hash is required; m-bootloader and damaged boot areas
  are deliberately refused in this first version.
- **Review prepared recovery** shows the device, chip, changed sector count,
  protected ranges and backup checksum, then asks before writing. The complete
  flash is compared to the saved backup again before erasing anything. Only
  changed 4 KiB sectors in `[0x4000, 0x93000)` are erased/programmed. Each is read
  back, followed by a full-flash comparison including untouched areas. A failed
  transfer stops the operation; there is no blind retry or automatic resume.
- **Export a backup** saves a ZIP through Android's document picker. It contains
  the full flash, metadata/SHA-256, and an operation result when available.
  Backups remain available after app/process restarts. Uninstalling the app or
  clearing its data deletes internal copies; export before proceeding.

Successful verification means bytes match, not that the instrument has booted.
Power cycle and reconnect MIDI to check startup. Existing custom-firmware or V16
settings/presets may not suit V15; this operation does not repair them. No
manufacturer firmware is bundled in the APK. The recovery path uses its own
hash-pinned compatibility policy for both chips, without relaxing MIDI OTA rules.

Devices absent from USB need external USB_KEY hardware or a second instrument
running [M-Unbricker](https://github.com/jvitkauskas/m-unbricker); this build does
not implement either. Bootloader repair, arbitrary raw-backup restore and V16
recovery are excluded.

Recovery tests use a simulated flash with interrupted/corrupt writes, short and
unstable reads, protected regions, altered backups and changed devices. To also
exercise the real V15 package parser and complete recovery workflow on simulated
old/new chips (without redistributing firmware):

```sh
FM1_RECOVERY_V15=/path/stock-v15.fwsc ./gradlew :app:testDebugUnitTest
```

Protocol reference: [M-Flasher](https://github.com/jvitkauskas/m-flasher), MIT.
V15 hashes and conservative recovery layout:
[MvaveFM1Unbricker](https://github.com/Quixotic7/MvaveFM1Unbricker).
