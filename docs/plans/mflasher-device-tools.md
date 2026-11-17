# Plan: M-Flasher features in FM-1 Flasher

Status: write operations enabled in the current development source at the owner's
request after their report that the available features work. Build 40 shipped the
read-only previews; its empty qualification allowlist has now been removed.
All five validated plans use the existing bounded executor on supported Puya/UCUN
chips. Experimental opt-in, backup export, explicit confirmation, session checks,
protected sectors and full readback remain required. This enables physical testing;
it does not claim physical acceptance of previously disabled writes.
The milestones below record the original rollout plan and acceptance checklist.
Date: 2026-11-16.
Branch: `feature/mflasher-device-tools`, based on `main` at `8087258`.

## Outcome and scope

Bring M-Flasher's useful device-management features into the Android app: device/chip diagnostics, a flash-region map, verified backups, bootloader installation and stock restoration, broader ROM firmware recovery, and backup restoration. Keep the existing firmware library, MIDI updater, fleet workflow and Compose UI.

Implement in three releases:

1. **Inspect:** structured ROM diagnostics, flash map, richer backups, then supported m-bootloader MIDI diagnostics/reads.
2. **Install bootloader:** validated preview, bundled FM-1 m-bootloader installation and stock bootloader restoration through Android USB ROM mode.
3. **Recover:** broader `.fwsc` firmware installation over ROM and guarded same-device backup restoration.

Each release stays under Experimental features until its own hardware checks pass. Read-only features can ship while write features remain disabled. ROM operations initially require exactly one connected recovery device and do not enter the fleet queue. Support FM-1 only; sharing a processor, key or JEDEC ID is not permission to operate on another model.

Reuse the existing transport, service, backup store and firmware parser. Port only necessary pure algorithms from Rust to Kotlin; no Rust/JNI bridge, new flashing framework or desktop GUI port. Keep platform-independent parsing in `shared`, Android USB/storage in `app`, and presentation in `composeApp`.

## What is already available

- USB ROM detection, download-mode entry, hash-checked RAM loaders, flash reads and bounded writes: `ChipDetector.kt`, `UsbBootConnection.kt`.
- Both known FM-1 flash IDs: `0x856014` (old/Puya), `0xB36014` (new/UCUN).
- Chip-key reading via `FC09`, currently retained internally and in backup metadata rather than displayed by the detection UI.
- Full 1 MiB backup with two matching reads, local persistence, hashes and ZIP export: `Recovery.kt` / `RecoveryStore`.
- Verified V15 application recovery, deliberately restricted to `[0x4000, 0x93000)` and recognized stock boot areas.
- `.fwsc` validation/extraction, MIDI update protocol, foreground service, wake lock, operation logging and MIDI exclusivity.

Do not broaden the existing V15 recovery action into a generic writer. New actions have separate planners and explicit permissions; its current behavior remains covered by regression tests.

## Features to adopt

- **Device information:** flash type/ID, chip key, capacity, meaningful flash UID when available, RAM loader version, installed bootloader/version, firmware health and model evidence. Distinguish measured values from estimates and absent values.
- **Flash map:** actual image-derived regions, addresses/sizes, protected regions, current/target layouts and changed/written/verified sectors. Offline inspection of backups is supported.
- **Backups:** richer metadata, view/export/import, backward-compatible reading of existing archives, and later M-Flasher `.bin` + `.json` interoperability.
- **Bootloader management:** install a pinned FM-1 m-bootloader; restore a complete validated stock boot area from an approved official package.
- **ROM firmware installation:** validate compatible FM-1 packages and install their application bytes while preserving the bootloader and factory data.
- **Backup restoration:** restore firmware and user data to the same instrument; preserve the current bootloader by default, with an explicitly validated option to restore the backup's bootloader.
- **MIDI support:** capability-checked diagnostics and raw backup/map reads from a supported m-bootloader, plus validation of the existing updater against its two-pass installation protocol.
- **Later refinements:** on-device CRC32 verification, measured throughput, instrument-screen progress and explicit restart, once basic operations work on hardware.

Excluded from this branch: arbitrary RAM programs/opcodes, raw-address erase/write UI, wipe/reset-by-region, desktop CLI/admin helpers, SMK-37/MiniFM cross-flashing, cross-device backup restoration, factory-data repair and bootloader installation via the special M-UPGRADE package. These are separate features, not prerequisites for the requested app functionality.

## User flow

Extend **Devices → Experimental tools** with four sections:

1. **Device info:** connection mode, measured identifiers, firmware/bootloader state, Refresh and Copy diagnostics. Explain when ROM mode or m-bootloader MIDI mode is required.
2. **Flash map:** read/backup action, region legend, tappable sector/range details and a text list for accessibility. Clearly identify live-session snapshots versus saved backups and estimated layouts.
3. **Backups:** date, device evidence, image hash, validation status, inspect/export/import, then Restore once available. Import never starts a device operation.
4. **Install/recover:** separate actions for m-bootloader, stock bootloader, ROM firmware and backup restore. Each creates a preview before enabling its explicit confirmation.

Every write preview shows the selected device, operation, artifact/source, changed sectors, preserved regions and backup. Progress separates reading, planning, writing and verifying. Success says “write verified; boot not checked” until a subsequent boot/identity check establishes more. Changed inputs or reconnection invalidate confirmation.

Keep EN/PL/zh-CN translations and text alternatives for every map color. Hide unavailable actions on portable backends; do not infer MIDI capability merely from an `ota-` identity.

## Milestone 1 — ROM diagnostics and backup metadata

Deliver a useful read-only release increment before adding any new write action.

1. Change chip detection to return structured information rather than only `Long` flash ID or formatted text. Reuse `UsbBootConnection`'s existing key read; add optional loader version/capacity/UID where the pinned loader supports them.
2. Preserve the full-reply convention: Android keeps the two-byte opcode echo, whereas upstream host helpers return payload bytes. Parse and test offsets explicitly.
3. Show `0x980F` only when actually read. Chip key is shared across observed FM-1/SMK-37 devices and is not a serial number; JEDEC identifies a flash part, not an instrument. Reject all-zero/all-FF UID as unavailable. UID is supporting evidence, not permission to bypass reconnect checks.
4. Keep live diagnostics tied to the current USB session. Clear them on detach/replacement; never silently assign a ROM result to the previously selected MIDI fleet device.
5. Port the bounded loader-start health check: one RAM-loader restart if it failed to detect flash, then an explicit error with observed ID. No automatic flash-write retry.
6. Version backup metadata, adding optional UID, capacity, tool/loader versions, acquisition transport and integrity method. Read existing backups without migration or deleting data. Keep new backups private locally; identifiers/dumps do not enter analytics.
7. Audit backup durability: image and metadata become a usable record only after complete synced writes and successful reload. Interrupted directories are not advertised as valid backups. Keep export tied to the exact backup hash.

Touch: `ChipDetector.kt`, `UsbBootConnection.kt`, `Recovery.kt`, `FlasherService.kt`, `ComposeActivity.kt`, `AppBackend.kt`, `DeviceToolsScreen.kt`, strings and notices.

Acceptance: reference reply vectors decode correctly; unknown IDs/keys remain readable but cannot authorize writes; short/invalid replies fail; detach does not reuse stale values; no erase/program command is sent. Legacy backups still load/export. A failed save is never presented as verified.

Reference vector from m-bootloader SPEC §10.2: `FC 09 00 00 00 00 3C 20 00 00 00 00 00 00 00 00` decodes to key `0x980F` after reversing reply bytes 6/7 and applying the stream transform.

## Milestone 2 — image analysis and flash map

Depends on Milestone 1 for live acquisition; parser/UI work can use offline fixtures.

1. Add pure Kotlin flash-table and boot/app analysis, porting the needed portions of upstream `formats.rs`, `boards.rs` and `sectormap.rs`. Reuse existing CRC primitives where equivalent. Do not confuse keyed app decryption with package-envelope decryption.
2. Parse boot header/records, app records and CRCs. Application file addresses are relative to `0x4000`; VM/BTIF/USR/SEQ64 descriptors are absolute. PRCT describes a protection extent starting at zero, not a file to erase. Distinguish intentional container/protection overlap from conflicting data ranges.
3. Bound all lengths/offset arithmetic before allocation or slicing. Invalid CRCs, overflowing/truncated records and conflicting ranges produce an invalid/unknown analysis, not a guessed safe layout.
4. Derive regions from the installed image: boot, firmware, settings, pairings, sounds/sequences and factory key/MAC. Preserve `[0xFF000, 0x100000)` as protected regardless of corrupt descriptors. Unlisted or erased space is not necessarily disposable.
5. Render the known 1 MiB flash as 256 sectors of 4 KiB with a text range list. Show source confidence and boot/app health. Redraw from a verified snapshot rather than rereading USB for UI changes.
6. Allow a labelled stock-layout estimate when parsing fails, for explanation only. V15 reference: boot `[0,0x4000)`, app `[0x4000,0x93000)`, VM `[0x93000,0xE9000)`, BTIF `[0xE9000,0xEA000)`, USR `[0xEA000,0xFC000)`. V16/custom layouts differ. Estimates never authorize writes.
7. Add current/target and changed-sector overlays. Initially preview existing V15 recovery; later operations supply their own validated targets. Separate planned, written, verified and failed states.

Touch: new focused `shared` format/layout models and tests; existing `FirmwarePackage.kt` only where genuinely reusable; Compose map/details; service snapshot mapping.

Acceptance: V15, V16, custom, blank, truncated, wrong-key and corrupt fixtures; overflow/conflicting-range cases; factory protection; exact region and sector boundaries. Compare valid fixtures with the pinned upstream algorithms and independent expected bytes. UI works without color and clearly labels estimates.

## Milestone 3 — m-bootloader MIDI inspection and update compatibility

Depends on structured diagnostics; backups/maps reuse Milestones 1–2. This milestone must not block ROM bootloader development if release capability verification takes longer.

1. Verify that the selected released m-bootloader binary implements SPEC §11.6, rather than assuming main-branch docs describe v2.4 exactly. Probe command `0x12` and require a valid `MBOOT` response with recognized protocol/kind, bounded fields and version.
2. Only after that handshake, support fixed command `0x23` (raw reads, 1–512 bytes) and `0x24` (range CRC32), with exact frame/provider/address/count checks. Feed the same diagnostic and backup models used by ROM.
3. Never probe generic erase/program commands on stock firmware. The spec warns that stock `0x21` can erase without checks and the checksum excludes the opcode. Use fixed constructors, not user-provided commands; MBOOT handshake failure means these tools are unavailable.
4. Keep double-read backup verification initially. CRC32 may add a check; it does not replace SHA-256 artifact integrity or become an excuse to skip validation. Store actual transport/integrity provenance.
5. Test `FlashSession` against `ota-FM-1_900`: it requests the image twice over one connection, can repeat/non-monotonically request bytes, and finishes with `0xF0000000`; there is no required intermediate VERIFIED/reconnect. Validate identity queries and reconnect only after completion.
6. Fix progress semantics: unique bytes served can reach 99% during the first validation pass. Show transfer/validation activity without claiming write progress the protocol has not reported; only final completion/verification can indicate success.
7. Keep Android as the first validated platform. Shared MIDI code may later enable iOS inspection through existing MIDI ports; raw USB ROM capabilities remain Android-only. No automatic RAM-stage installation.

Touch: `Protocol.kt`, `FlashSession.kt`, a small MBOOT capability/read helper over `MidiConnection`, `FlasherService.kt`, `AppBackend.kt`, `PortableAppBackend.kt` capability handling, protocol/session tests.

Acceptance: valid/refused/unsupported capability responses, truncation, wrong ranges, reconnect invalidation, two-pass/repeated requests, timeouts and wrong final identity. Confirm diagnostics/backup and firmware installation with the pinned bootloader on a physical instrument. MIDI firmware install does not install the bootloader itself.

## Milestone 4 — bootloader planning and installation

Depends on Milestones 1–2. Implement as two reviewable changes: pure planner first, hardware executor second.

### Validated inputs and target construction

- Bundle one FM-1 m-bootloader artifact, initially v2.4 after checking release SHA-256, provenance/license and exact hardware evidence. Keep the existing v2.3 RAM loader assets unless separately validated. Permanent bootloader and RAM loader are different artifacts.
- Port BANKCB/container CRC validation, boot table/identity checks, key/configuration validation and target boot-area assembly. Reject wrong board, placeholder/corrupt images, damaged key records, oversized/overlapping data and insufficient model evidence.
- Current short configuration is 96 bytes at `0x3FA0`: preserved 34-byte chip-key record/CRC plus five compatibility settings. v2.0 used 34 bytes at `0x3FDC`. Fixture-test both layouts; do not fabricate a missing/damaged key record.
- Restore stock from a separately approved official package, initially the app's pinned V15 package. Validate the complete boot area/configuration and key compatibility; copying only `uboot.boot` over a shortened configuration is unsafe. Add V16 only with its own verified artifact/fixtures. Existing corrupt boot metadata may allow a validated stock repair but must not enable unsafe m-bootloader construction.
- Require consistent FM-1 evidence; reject conflicting or insufficient identity. A matching chip key and JEDEC ID alone are insufficient.

### Plan and execute

- Produce an immutable plan containing operation kind, device/session evidence, input/backup/target hashes, allowed ranges, exact changed sectors and warnings. Allowed boot write range is `[0,0x4000)` only. Inputs that change require a fresh plan and confirmation.
- Preserve `Recovery.checkSector()`'s V15 contract. Refactor transport writes only enough to bind each connection/writer to a trusted operation-specific policy. Both planner and lowest write boundary enforce it; there is no UI-provided address range or `allowUnsafe` flag.
- Before erase, require a new durable double-read backup and successful external export for the initial bootloader flow. This export gate is a proposed app policy, not an upstream requirement. Record success only after the provider stream closes successfully; cancellation/failure leaves writing disabled and retryable.
- Recheck permission, same-session device evidence, artifact hashes and current full flash against the preview immediately before writing. Reject stale plans after detach, substitution or process restart.
- Write only changed aligned sectors, using existing bounded page writes; verify each sector and finally compare the entire flash against the expected target. No-op plans send no writes. Stop on failure and retain the backup/incomplete result; no automatic reboot or blind erase retry.
- Reuse the foreground connected-device service, `MidiSessionGuard` and existing wake lock. Check the 12-minute transport deadline against the full operation; do not hold a USB connection open while awaiting export/confirmation. Writing cannot overlap logger, fleet queue or another device operation.
- Allow cancellation before writing. Once writing starts, avoid a user cancellation path that deliberately interrupts a sector erase/program/verify sequence; show the current state honestly. Never auto-resume after process death.

Acceptance: planner targets match independently checked bytes; all data outside `[0,0x4000)` is unchanged; existing V15 protections remain; invalid/stale plans cause zero erases. Inject failure at erase/program/readback stages, USB loss, permission revocation, timeout, storage/export failure and service restart. Verify each result and preserved backup.

Release gate: validate stock → m-bootloader → stock, cold boot, recovery keys, MIDI update and ROM re-entry on each supported flash/artifact combination. New UCUN hardware remains gated until explicitly validated; see evidence caveats below.

## Milestone 5 — broader ROM firmware installation

Depends on image analysis and the bounded planner/executor from Milestone 4. Leave the original V15 recovery shortcut intact.

1. Add a separate “Install firmware in ROM mode” action consuming downloaded/local FM-1 packages already validated by `FirmwarePackage`. Check known hardware compatibility, product, capacity, decrypted app table/files and CRCs using the actual device key.
2. Construct the target by overlaying only package-supplied application bytes, keeping boot and fixed factory data unchanged. Reject images crossing protected ranges. For partial-sector boundaries, compose the full sector from the original snapshot and validated bytes; preserve bytes outside the logical write range and preview the actual changed sectors.
3. Do not assume `Recovery.END` is every firmware's end. Derive a bounded range from validated image content and known device capacity. Do not erase unmentioned settings or fill the rest of flash with FF.
4. Show differences between installed/target data layouts and explain possible effects on settings, sounds and downgrade behavior. Retained user bytes can still be changed by firmware after it boots. V16 → V15 does not recover overwritten stock sounds; a suitable backup is needed.
5. Require a recognized usable installed bootloader, or an explicitly completed bootloader-repair step first. Do not silently install the package's boot area with firmware.
6. Reuse full backup, preview, stale-plan rejection, changed-sector writes and full readback. Importing a package or selecting a catalog item never starts a ROM write.

Acceptance: valid V15/V16/custom packages, wrong model/key/chip, oversized images, malformed/placeholder boot areas that must remain unwritten, layout changes, partial sectors and interrupted writes. Assert boot/factory bytes and all bytes outside the explicit payload remain unchanged. Hardware-test each allowed package/chip combination before expanding eligibility.

## Milestone 6 — backup import and same-device restore

Depends on the shared analysis/planner/executor. Import/inspect can ship before restore.

1. Import the app's ZIP format and M-Flasher raw `.bin` plus JSON sidecar through Android's document picker. Bound file and decompressed sizes, reject duplicate/ambiguous entries and path traversal, parse metadata strictly and verify exact image length/hash. Preserve original provenance; computing a hash now does not prove an old dump was originally correct.
2. Keep older app backups readable and exportable. Restore eligibility is a separate status: new backups carry device evidence; legacy/metadata-free images can be inspected but need evidence policy before being used for a destructive restore.
3. First restore release accepts only same-device backups with meaningful matching UID and consistent FM-1/key/layout evidence. If reliable UID is unavailable or identity conflicts, inspection/export remain available; do not treat matching JEDEC/key, a filename or blank factory bytes as proof. This is deliberately narrower than upstream's restore heuristics.
4. Default to restoring application/user data while keeping the installed bootloader. Offer restoration of the backup's boot area only when its boot/configuration validations pass and the operation is explicitly selected. Warn/refuse when retaining a damaged bootloader would leave the unit unable to boot.
5. Always preserve the connected device's fixed factory key/MAC area in this release. If it differs from the backup, surface that fact and stop for unresolved identity conflict; do not import another unit's factory data or claim a byte-identical full restore. Repair of blank/corrupt factory data is separate work.
6. Create a fresh pre-restore backup, show every changed/preserved range, then apply the same verified-write rules. No selective user-chosen raw region restoration. A checksum is integrity evidence, not publisher authenticity or guaranteed bootability.

Acceptance: app/M-Flasher round-trip fixtures, existing schema support, malformed/oversized/traversal ZIPs, missing/mismatched hashes, UID mismatch/absence, corrupt boot area, protected factory data, no-op plans and interrupted restoration. Hardware-test restoring firmware plus user data and both bootloader choices on the same unit.

## Shared implementation boundaries

Proposed small domain types, added only as used: `DeviceDiagnostics`, `FlashLayout`, `FlashWritePlan` and a versioned backup manifest. Keep pure plan creation separate from transport execution. A plan carries explicit allowed ranges; the executor validates them again. Share changed-sector comparison and verification between new operations once duplication exists, not through an upfront plugin framework.

Integration points:

- `shared/src/commonMain/...`: protocol/format parsing, analysis and pure target planning. Extend `FirmwarePackage` only where required without weakening existing OTA checks.
- `app/src/main/...`: `UsbBootConnection`, `RecoveryStore`, `FlasherService`, `ComposeActivity`, document import/export and foreground operation lifecycle. Reuse existing logs; avoid full dumps/UIDs in telemetry.
- `composeApp/src/commonMain/...`: `AppBackend` snapshot/actions, `DeviceToolsScreen`, map/backups/review views, `FleetScreen` entry points and `PortableAppBackend` capability rejection.
- `CoreStrings.kt`, Android resources and `tools/sync-resources.py`: EN/PL/zh-CN and generated resource consistency.
- `ComposeActivity` is the manifest launcher. Do not build a duplicate feature UI in legacy `MainActivity`.
- `third_party/m-flasher-NOTICE.md`, licenses, user guide, firmware-safety guide and README: attribution, pinned artifacts and accurate limits per released milestone.

## Testing and release gates

For each milestone, add tests of its behavioral contract, not copies of implementation logic:

- Pure tests: protocol vectors, app/boot CRCs and decryption, malformed range handling, region analysis, plan bounds, unchanged protected bytes, schema/import validation.
- Fake transport tests: diagnostic operations issue no writes; invalid plans issue no erase; failure on every destructive stage leaves an accurate incomplete result. Existing `RecoveryTest`, `ChipDetectorTest`, `ProtocolTest`, `FirmwarePackageTest` and `FlashSessionTest` remain green.
- Android UI/service tests: experimental gating, permissions, offline inspection, review invalidation, export failure, operation exclusivity, disconnect/rotation and lifecycle recovery. API 34 and 36; emulators establish UI behavior only.
- Physical tests: both known FM-1 JEDEC variants, direct OTG data cable first, V15/V16 and selected custom firmware, power cycle, recovery keys, MIDI/ROM transitions, cold boot and preservation of user/factory bytes. Record phone/Android, device/flash ID, artifact hashes and logs. Destructive fault injection only on a recoverable development unit with a proven ROM-entry method.

Checks matching current CI:

```sh
python3 tools/sync-resources.py --check
./gradlew --no-daemon :shared:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Use synthetic or redistributable fixtures. Do not commit private dumps or vendor firmware to the repository. Artifact hashes and CRCs do not prove firmware safety; keep existing source/chip compatibility checks.

Implementation order: **1 → 2 → 3**, then **4 → 5 → 6**; Milestone 4 can proceed after 2 if MIDI work is blocked. Split Milestone 4 into planner and executor changes. Complete documentation/localization and relevant tests within each milestone; release inspection independently of the bootloader hardware gate. No calendar estimate until the protocol fixtures and physical-device availability are confirmed.

## Evidence, limitations and deferred routes

Sources pinned for this plan:

- [M-Flasher `8fbece7`](https://github.com/jvitkauskas/m-flasher/tree/8fbece7348d758ac284325eed455781675ac95ff): `src/jl.rs`, `formats.rs`, `boards.rs`, `sectormap.rs`, `ops.rs`, `docs/usage.md`, `docs/safety.md` and `data/NOTICE.md`.
- [m-bootloader `3ac7651`](https://github.com/jvitkauskas/m-bootloader/tree/3ac7651a019fa44daba061b05654278496477b64): README, SPEC §§5.3–5.5, 10.2 and 11.6–11.7. Main-branch protocol documentation must be checked against the exact release binary.
- The app's existing adaptation is pinned to `620c0f4c582d4864e1ae23e6fc4560c64441b138`. M-Flasher bundles v2.4 permanent bootloaders but retains v2.3 RAM loaders. Preserve MIT copyright/license and precise provenance for ports/assets; the code license does not license proprietary stock firmware.
- Requested [video](https://www.youtube.com/watch?v=VNFZqDnTFYs): metadata/description retrieved, playback failed HTTP 403 and captions HTTP 429. This plan is source-based; it makes no claims about the video's demonstrations.

**New-chip evidence:** M-Flasher's `flash_tested()` includes UCUN `0xB36014`, but m-bootloader's README still calls it little tested: one failure on 1.1/1.2, another owner's successful 1.3/2.0 report. Do not advertise broad v2.4 compatibility from that predicate. Keep writes gated by tested chip/artifact combinations. Unknown hardware remains read-only where transport acquisition is supported.

**Bootloader persistence:** official M-VAVE updates and Baud Girl's installer can restore stock bootloader/configuration. Refresh diagnostics after an update; do not promise m-bootloader stays installed. REC at power-on enters ROM, PLAY opens recovery menu; a broken bootloader may still require USB_KEY hardware. A backup cannot guarantee plain-cable recovery.

**MIDI bootloader installation, deferred:** `fm1-uboot.fwsc` is an installer-only version-099 package, without normal `flash.bin`. M-UPGRADE times out after the installer restarts the instrument. Our parser requires a firmware image and our updater expects normal completion; supporting it requires a separate artifact/operation contract, not a parser exception or treating timeout as success. The README's version-specific M-UPGRADE test statements conflict, so exact release evidence must be resolved first.

**RAM stage, deferred:** launching through the firmware update route stores/erases staging bytes. A later backup is not an untouched pre-operation snapshot. Stock and Felucca-family firmware also need different package forms. No automatic staging just to enable diagnostics.

**Optional refinements after Milestone 6:** add capability-checked ROM CRC32 and instrument-screen progress, explicit restart after successful verification, and measured transfer tuning. Keep raw readback as the initial verification method and never copy desktop speed claims to Android. Restart/progress capabilities must not become prerequisites for recovery.

Implementation validation and prerelease artifact provenance: see `publication/qa/0.10.0-experimental.40.txt`. Physical hardware and iOS acceptance remain outstanding.
