# FM-1 Flasher

An independent firmware manager for **M-VAVE FM-1**. Android organizes several instruments connected through an OTG hub into **Devices · Library · Tasks**, with one firmware write at a time.

## Your instruments

Read each instrument in **Devices**, give it a name in **Details**, choose its memory type from known hardware information and select a downloaded package. **Add to plan** keeps that device and package together. Names and colors help organize the workspace; they are not proof of physical identity.

## One installation plan

In **Tasks**, review device names, versions and package hashes, then choose **Install on N instruments**. This approves the plan without another ordinary confirmation popup. Each job is checked before transfer. **Stop after this instrument** finishes the active operation and keeps the remaining tasks queued.

History records each job’s result and stages. After process interruption, an active task is unconfirmed; writing never resumes automatically from a saved percentage. Keep USB and power connected until verification finishes. Firmware may change instrument settings; OTA does not create a backup automatically.

## Returning through a hub

With several instruments, pair the returning unit **on the task card** after its planned restart. Other connections must remain continuous and only one candidate may return. A hub reset, lost neighbor, contradictory serial or ambiguous MIDI ports blocks further writing.

USB addresses are not physical hub socket numbers. Firmware model/version is not a unique instrument identifier, and USB serial uniqueness across modes has not been hardware-verified. Automatic multi-device pairing remains disabled. The conservative automatic path only applies when Android sees one eligible MIDI endpoint and one USB device, with no MIDI siblings. System USB permission can be needed again after a restart.

Saved names require explicit pairing after a lost connection or app-process restart. Read and physically identify the instrument, remove affected pending tasks, then assign its saved name; select the memory type again. A successful result verifies the returned firmware identity, not a full flash-memory readback. Real FM-1/hub acceptance is still required.

## Shared firmware library

**Library** combines search, favorites and downloads. **Filters & sort** reveals category, order and source choices. Saved firmware can be used offline. A task pins its package against deletion/replacement. Removing an ordinary valid package offers Undo during the current app session.

In **Settings → Firmware sources**, Firmware Hub starts enabled and Awesome FM-1 disabled. Custom HTTPS catalogs use the supported JSON format, approved package/image URL scopes and a 24–168 hour interval. Save configuration first; **Check now** is separate. Checks respect the interval even after failure; a failed source retains its previous cache. An explicit check is possible on a disabled source, while automatic requests are not.

Background catalog checks are optional and initially off. They do not download or install firmware. Offline mode stops network fetching and background checks; it cannot discover withdrawals that have not reached the local catalog. Catalog hashes do not independently authenticate publishers.

## Awesome FM-1

[Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) is a community directory of FM-1 firmware projects, descriptions and links to their publishers. Firmware files remain with their authors. Enable its preconfigured source in Settings; no URL needs to be entered. Report outdated entries or contribute projects in the repository.

## Tools and platforms

Settings provides English, Polish and Simplified Chinese, light/dark/system themes, offline mode, About and privacy. Experimental features start off. Enabling them exposes MIDI Logger and recovery tools. The receive-only logger pauses for connection/flashing.

Chip detection concerns a separate ROM/download-mode device and is never automatically assigned to a MIDI instrument. ROM recovery is a separate approved backup/write workflow restricted to the recognized stock V15 application region; it does not write the bootloader or automatically follow a failed OTA update. Physical recovery is unverified.

Android requires **14+**, USB Host/OTG, MIDI, a data cable and stable power. A powered hub is recommended. A port to iOS is planned for the future. There is no iOS release or announced release date.

## Privacy and licenses

The app requires no account and contains no advertising. Standard builds contain no analytics SDK. Builds configured with Google Analytics for Firebase collect usage statistics by default from the first online launch. You can turn statistics off at any time in Settings → Usage statistics. Device names, observed serials and task history stay locally with the library. External catalog/download servers receive connection metadata, not instrument identities attached by the app. Read the full [privacy policy](../PRIVACY_POLICY.md).

Catalog attribution: [The Firmware Hub](https://thefirmwarehub.com), CC BY 4.0; descriptions, artwork and firmware retain their authors’ rights. App: [MIT](../LICENSE.md). Markdown: [CommonMark, BSD-2-Clause](../third_party/commonmark-LICENSE.txt). Networking/background libraries: [Apache 2.0](../third_party/Apache-2.0.txt), [notices](../third_party/android-libraries-NOTICE.txt).

Contact: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).

## Diagnostics and storage cleanup

The app always records a bounded local operation log (256 KiB, oldest entries removed). It survives app restarts and includes timestamps, actions, USB/MIDI port changes, firmware-transfer stages and caught errors. Raw MIDI traffic and firmware bytes are not recorded. Open **Settings → Experimental features** to access **Export operation log** (JSON Lines, `.jsonl`) and **Clear operation log**. Export uses the system file picker; no log is uploaded automatically. Clearing requires one confirmation and does not delete copies you already exported. New events continue to be logged. The separate MIDI Logger remains a live raw-MIDI diagnostic tool.

In **Settings → Clear downloaded firmware**, confirm once to delete all downloaded package files and metadata, including damaged/orphan files. The selected package and single-package Undo are cleared. Favorites, catalog sources, device names, task history and recovery backups remain. Remove queued tasks first; cleanup is blocked during work and while any queued/running job owns a package. Offline installation requires downloading packages again.

**Devices → Modes and flash chip** provides MIDI selection/readout, download-mode entry and chip detection in separate cards. Successful chip detection opens a result popup. Power-cycle back to MIDI afterwards. Default disconnected device entries are temporary; custom names and queued targets are retained. Delayed USB detach events for a retired stage no longer invalidate the next stage of the same update. Unrelated device/hub loss still blocks writes.

## Full-screen settings

**Settings** is a full screen with Interface, Firmware library, Experimental features and Application cards. Language and theme use inline radio choices. Sources, source editing, per-package compatibility and privacy open as full pages. The header and system Back return one level; the original Library/Devices/Tasks tab and non-secret source drafts survive activity recreation. Password/token inputs are intentionally not saved in activity state. Destructive confirmations and the requested chip-result popup remain dialogs.


Android catalog sources use thefirmwarehub, awesome-fm1, GitHub releases (repo-url) or a direct FWSC file (pkg-url). Source editing supports Basic, Bearer, API-key headers and OAuth 2.0 (browser PKCE or Client Credentials). Credentials are encrypted locally. Manual package compatibility applies only to its SHA-256. These new network/authentication adapters are Android-specific; the Apple backend retains its prior Hub workflow.
