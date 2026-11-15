# Multiplatform FM-1 Flasher

Independent firmware manager for **M-VAVE FM-1**, built with Kotlin Multiplatform and Compose Multiplatform. The main Android workflow manages several instruments through an OTG hub and writes firmware **one instrument at a time**.

This guide describes stable **0.9.2** on `main`. This version adds full-screen settings, provider adapters and source-scoped HTTP authentication. The [build report](publication/qa/0.9.2.txt) records verification and limits. The owner reported successful physical FM-1 tests of build 20; the new source/authentication features require their own acceptance.

[Polski](website/pl/documentation.md) · [简体中文](website/zh-CN/documentation.md) · [Implementation](MIGRATION.md) · [Privacy](PRIVACY_POLICY.md)

## Platform support

| Capability | Android | iOS host sources |
| --- | --- | --- |
| Runtime requirement | Android 14+, USB Host/OTG and MIDI | iOS 15 deployment target; no verified Apple binary |
| Main workflow | Devices · Library · Tasks | Previous single-instrument controller |
| Device registry, persistent queue and history | Implemented | Not ported |
| Catalog sources | Firmware Hub, optional Awesome FM-1, custom sources | Hub repository |
| Background catalog checks | Optional WorkManager task | No equivalent adapter |
| MIDI firmware transfer | Sequential jobs through native service | Shared session with manual reconnect; unverified on hardware |
| Raw USB chip detection and ROM recovery | Separate experimental tools | Unavailable |

The Android application ID is `pl.ekim.multiplatform_fm1_flasher`. It installs separately from the original `pl.ekim.fm1_flasher` app and does not import that app's private data. English, Polish and Simplified Chinese interfaces and light/dark/system themes are available.

## Getting started

1. Install the test APK on Android 14 or newer. The build-23 debug certificate matches earlier multiplatform builds: install it as an update without uninstalling to retain this fork's library and preferences.
2. Open **Library**, find firmware and download it from its detail screen. Downloading verifies the package; it does not start an installation.
3. Connect instruments with data-capable cables and an OTG hub. A separately powered hub is recommended. The app cannot measure or certify hub power quality.
4. In **Devices**, use **Read instrument** on each card and grant Android USB access when requested. Only a recognized FM-1 can be added to the plan.
5. Open **Details** to set a useful instrument name. Choose the memory type from known hardware information and a local package for that instrument. A package compatible with both supported memory types can be allowed even when memory type is unknown; incompatible or unconfirmed packages remain blocked.
6. Choose **Add to plan** for each instrument. Each device can have one pending/running job. Package assignments may differ between devices.
7. Open **Tasks**, review names, versions and hashes, then choose **Install on N instruments**. This is the plan approval; there is no second ordinary confirmation dialog.

Preserve power and USB connections until verification finishes. Use firmware-specific tools to back up instrument data when available; an OTA update is not an automatic backup. Firmware can change instrument settings.

## Library and sources

**Library** combines search, favorites and local downloads. **Only downloaded** and **Favorites** are filters, not separate Android navigation tabs. **Filters & sort** expands category, sort and source controls. When the downloaded filter is active, the package-update action is available.

Package details show versions, source/author links, compatibility and SHA-256. Selecting a saved package returns to Devices; it does not write firmware. Ordinary removal has **Undo** during the current app session for a valid package. A package referenced by a queued or running job cannot be deleted or replaced: remove its pending task first. Favorites survive package removal.

Open **Settings → Firmware sources**:

- **The Firmware Hub** starts enabled; **Awesome FM-1** starts disabled. [Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) is a community directory linking to firmware projects and their publishers.
- A custom source needs an HTTPS catalog in the supported Hub-style JSON format, a name, a 24–168 hour interval and approved package/artwork URL scopes. **Save** records the configuration; **Check now** is a separate action. Newly configured sources start disabled.
- Refresh and Check now respect the source interval, including failed attempts. Repeated tapping does not bypass the minimum 24-hour budget. The source screen shows errors and the next allowed check.
- Disabled sources make no automatic requests. An explicit Check now may check a disabled source. A failed source retains its cached snapshot; other sources can still update.
- Removing a custom subscription preserves packages, favorites, provenance and known withdrawals. Restoring defaults enables Hub and disables Awesome FM-1 without removing custom subscriptions.
- Optional background catalog checking starts off. It checks enabled catalogs when network conditions allow; it neither downloads packages nor installs firmware.

Offline mode suppresses catalog, package and artwork network requests and pauses background checking. Saved packages can be flashed without a network request. Offline use cannot discover a withdrawal that has not yet reached the local catalog. Catalog declarations and hashes do not by themselves establish publisher authenticity.

## Multiple FM1 devices on a hub

A device card has a local record, name/color, observed firmware identity, memory selection and current attachment. **USB addresses are not physical hub socket numbers.** Model/version, VID/PID and JEDEC chip type do not uniquely identify an instrument. Observed USB serials are retained locally but have not been validated as unique across FM-1 operating modes.

Only one job changes modes at a time. After a planned restart, Android tracks USB/MIDI events, excludes unchanged neighbors and requires a single eligible return. All other observed attachments must remain continuous. With several instruments, use the **inline pairing action in Tasks** only for the instrument being updated. There is no override to guess among multiple candidates. Do not swap indistinguishable devices during a transition.

Automatic multi-device pairing remains disabled pending physical tests. The older conservative automatic path is available only when there are no MIDI siblings and Android reports one eligible MIDI endpoint and one USB device. An observed serial never independently authorizes automatic matching. System USB permission may be required again after disconnect/re-enumeration.

A lost neighbor, hub reset, changed attachment generation, contradictory serial or ambiguous port arrangement stops further writing. Multiple MIDI port pairs or multiple MIDI endpoints on one USB device are blocked. A confirmed result means the return and expected firmware model/version were checked; it is not a complete flash-memory readback.

Saved names survive restart, but live bindings do not survive process death or an untrusted disconnect. Read and physically identify the instrument again, remove affected pending jobs, then use **Saved instruments → Assign this saved name**. Pairing resets the old memory-type selection; choose it again before rebuilding the plan.

## Tasks, stopping and failures

Jobs freeze the target record/attachment, expected identity, memory selection, package key and hash. All plan packages are checked before the first write; each job is checked again before its transfer. Changing the UI's selected package cannot redirect an active job.

- **Stop after this instrument** finishes the current write and return verification. Remaining jobs stay queued. There is no button that abruptly cancels a live firmware write.
- A failed job stops the run. Inspect the instrument and the task's details before a new attempt; the app does not silently move its package to another device.
- Progress and results remain in Tasks. Details show the full package hash and recorded stage times. Persistent stage history is bounded to 64 entries per job.
- After process interruption, an active job becomes **unconfirmed**. No transfer resumes automatically from a saved percentage. Pending jobs remain visible but their old live bindings must be rebuilt.
- If the plan no longer matches current connections/packages, remove affected tasks, read the instruments and add fresh tasks. A rejected USB request does not authorize writing.

The Android foreground service and time-limited wake lock support an active transfer while the screen is off or another app is foregrounded. They cannot guarantee USB power or prevent every system/process interruption. Return to the app when an inline pairing or system grant is needed.

## Settings and experimental tools

Settings contains language/theme, offline mode, sources, About and privacy. **Experimental features** is off by default. Enabling it exposes MIDI Logger and the recovery tools; it cannot be changed during a busy operation. Regular Android installation uses Devices and Tasks.

The receive-only MIDI logger has bounded buffers and selected inputs. It stops automatically when connecting/flashing; simultaneous logging and flashing are not promised.

JEDEC detection uses a separate USB ROM/download-mode device. The result is never automatically assigned to a MIDI instrument. ROM recovery is a separate, explicitly approved operation with backup/export and a prepared write plan. It is restricted to the hash-pinned stock V15 application region and recognized boot/chip conditions, and does not write the bootloader. It is not a general-purpose unbricking tool or an automatic fallback after OTA failure. Physical recovery remains unverified.

## Building and testing

Use JDK 17, Android SDK platform 36 and Build Tools 35.0.0. The project pins Gradle through its wrapper, AGP 8.13.2, Kotlin 2.2.21 and Compose Multiplatform 1.9.3. Configure the SDK through the local environment or Android tooling.

```sh
python3 tools/sync-resources.py --check
./gradlew :shared:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. `dist/` is ignored by Git; the prepared build-19 copy is local, not a guaranteed file in a fresh clone.

Android XML under `app/src/main/res/values*` is authoritative for UI translations. Run `python3 tools/sync-resources.py` after changing it. App About documents are `about/README*.md`; Android bundles them with license/third-party documents through `:app:bundleProjectDocs`. The root README is not the in-app About document.

See [implementation and reproducible test commands](MIGRATION.md), [acceptance checklist](tests/android14-acceptance.txt) and [website build instructions](website/pages.md). Publishing is a separate step; [the inherited Google Play workflows do not currently target this fork's application ID](publication/github-actions.txt).

## iOS development status

The Swift host and shared metadata are present, but no Xcode build or physical iPhone update has been validated. The fleet store/queue and Android multi-source scheduler are not ported to iOS. The host retains the previous single-instrument workflow with manual reconnect. Keep it foregrounded; finite background time is not equivalent to Android's service.

The configured Kotlin targets are `iosArm64` and `iosSimulatorArm64`, not an Intel simulator. `iosApp/project.yml` defines the XcodeGen project, bundle ID `pl.ekim.multiplatform-fm1-flasher`, version 0.9.2/23 and deployment target iOS 15. The macOS/Xcode steps are in [MIGRATION.md](MIGRATION.md); Linux metadata compilation is not an iOS app build.

## Verification and limits

Current verification is recorded in the [0.9.2 / 23 report](publication/qa/0.9.2.txt). Build 20 was reported working on physical instruments by the owner. ROM recovery, all chip/phone/hub combinations and an Apple binary still need separate verification.

No new physical FM-1/hub test or iOS binary was independently verified for build 23. Power behavior, real mode/descriptor transitions, serial uniqueness and full hardware flash success remain acceptance gates. [The build report](publication/qa/0.9.2.txt) records the scope and artifact identity.

## Privacy and attribution

No application account, advertising or analytics; optional source authentication uses the account or service credentials you configure. The library, names, observed serials, memory selections and task history are local. External catalog/artwork/download servers receive connection metadata; instrument identities are not attached to those requests. See the [privacy policy](PRIVACY_POLICY.md).

App code: [MIT](LICENSE.md). Catalog attribution: [The Firmware Hub](https://thefirmwarehub.com), CC BY 4.0; firmware remains under its authors' licenses. [Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) is an optional directory. Third-party notices are in [third_party](third_party/). Contact: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).

## Diagnostics and storage cleanup

The app always records a bounded local operation log (256 KiB, oldest entries removed). It survives app restarts and includes timestamps, actions, USB/MIDI port changes, firmware-transfer stages and caught errors. Raw MIDI traffic and firmware bytes are not recorded. Open **Settings → Experimental features** to access **Export operation log** (JSON Lines, `.jsonl`) and **Clear operation log**. Export uses the system file picker; no log is uploaded automatically. Clearing requires one confirmation and does not delete copies you already exported. New events continue to be logged. The separate MIDI Logger remains a live raw-MIDI diagnostic tool.

In **Settings → Clear downloaded firmware**, confirm once to delete all downloaded package files and metadata, including damaged/orphan files. The selected package and single-package Undo are cleared. Favorites, catalog sources, device names, task history and recovery backups remain. Remove queued tasks first; cleanup is blocked during work and while any queued/running job owns a package. Offline installation requires downloading packages again.

**Devices → Modes and flash chip** provides MIDI selection/readout, download-mode entry and chip detection in separate cards. Successful chip detection opens a result popup. Power-cycle back to MIDI afterwards. Default disconnected device entries are temporary; custom names and queued targets are retained. Delayed USB detach events for a retired stage no longer invalidate the next stage of the same update. Unrelated device/hub loss still blocks writes.

## Full-screen settings (0.9.2)

**Settings** is a full screen with Interface, Firmware library, Experimental features and Application cards. Language and theme use inline radio choices. Sources, source editing, per-package compatibility and privacy open as full pages. The header and system Back return one level; the original Library/Devices/Tasks tab and non-secret source drafts survive activity recreation. Password/token inputs are intentionally not saved in activity state. Destructive confirmations and the requested chip-result popup remain dialogs.

## Catalog providers and authentication

Android has adapters behind the shared `CatalogProvider` interface:

| Provider | Behavior |
| --- | --- |
| `thefirmwarehub` | Built-in Firmware Hub JSON catalog and its existing package policy |
| `awesome-fm1` | Built-in Awesome FM-1 JSON catalog and approved URL scopes |
| `repo-url` | GitHub.com repository URL, published releases/prereleases and `.fwsc` assets; drafts are excluded |
| `pkg-url` | A direct HTTPS `.fwsc` URL, identified and hashed from the actual package |

The `catalog-json` adapter preserves existing custom JSON subscriptions. Adding providers does not discard old source IDs, favorites, local packages or history. Save a manual source, use **Check now**, and enable it to make its offers available. Checks are bounded; a failed/incomplete import preserves the previous snapshot. GitHub pagination and asset responses respect retry delays. Large repositories may need authentication to fit GitHub rate limits.

For repository assets, GitHub SHA-256 digests are used when available; the app reads a package prefix for its product identity. Older assets without a digest require fetching and validating the package to compute its hash. A direct package check also fetches and validates the package. These checks may transfer binary bytes without marking a package as downloaded for offline use. **Download** remains the action that stores it in the firmware library; no installation is automatic.

Manual providers do not infer supported flash chips from filenames. After checking a source, open **Edit → Package compatibility**, confirm the publisher information for each SHA-256, return and save. The declaration updates metadata for matching bytes, including a matching local copy; it does not apply to another binary at the same URL or another release. Unknown compatibility blocks installation. An unknown declaration does not contradict known metadata; contradictory explicit identities/chip declarations still block use.

The source form offers None, HTTP Basic, Bearer, API key in a chosen safe header, and OAuth 2.0. Basic takes username/password; Bearer takes the token without the `Bearer` prefix. API key sends the entered header value literally (including an explicit prefix if using `Authorization`). Passwords, tokens and client secrets are encrypted with Android Keystore, separate from SQLite source metadata, and are not logged. Empty secret fields keep a saved secret only for unchanged source/provider/authentication identity; a different URL cannot inherit it. Source deletion and restoring built-in defaults remove the relevant saved secrets.

OAuth supports browser Authorization Code with **PKCE S256** and service **Client Credentials**. Configure your issuer, client ID, scope, token endpoint and, for browser sign-in, authorization endpoint. Register `pl.ekim.multiplatform-fm1-flasher://oauth/callback` with the issuer. Client Credentials needs a client secret; a public PKCE client does not. Select `client_secret_basic` or `client_secret_post` as required by the issuer. Save, then choose **Authorize OAuth 2.0** on the source card. Tokens are refreshed when expired if the server supplies a refresh token; Client Credentials can obtain another token. Browser state is one-use, source/revision-bound and expires after ten minutes. No universal OAuth client ID or secret is bundled.

Authenticated requests are limited to the configured source origin (GitHub API origin for `repo-url`). Authorization is stripped on cross-origin redirects, including CDNs; token POST redirects are refused. HTTPS and existing public-address/URL-scope restrictions remain. Source checks normally follow the 24–168 hour schedule. Explicitly replacing credentials or completing authorization permits a new check so a previous authentication failure does not force a full-day wait.

These provider and authentication adapters are implemented in Android. The iOS host retains its previous single-Hub repository; the common interface/UI compile as metadata, but an Apple binary and native credential adapter have not been verified.
