# FM-1 Flasher

![FM-1 Flasher — Your FM-1 setup. More possibilities.](https://raw.githubusercontent.com/ekim-pl/android-fm1-flasher/main/assets/readme-cover.png)

[![Android CI](https://github.com/ekim-pl/android-fm1-flasher/actions/workflows/android-ci.yml/badge.svg?branch=main)](https://github.com/ekim-pl/android-fm1-flasher/actions/workflows/android-ci.yml)
![Android 14+](https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE.md)

Browse, save and install firmware for your **M-VAVE FM-1** from an Android phone. Keep a local firmware library and update one instrument or several in sequence over USB OTG.

**Version 0.10.0** — preparation for Google Play. [Available APKs](https://github.com/ekim-pl/android-fm1-flasher/releases) · [Build and verification](publication/qa/0.10.0.txt).

## Project manifesto

FM-1 Flasher is an independent initiative and will always remain independent, open source, transparent and free of charge. Its source code and development decisions will remain open to public review, and the application will remain free to use.

## Your firmware, available offline

- **Local firmware files:** download packages to your phone for later installation. Downloading never starts flashing.
- **Local library database:** saved catalog information, favorites and download records remain available between sessions. Previously downloaded packages can be installed without internet access.
- **Offline mode:** pause catalog checks and downloads while continuing to use your saved library. New releases and newly withdrawn firmware cannot be discovered offline.
- **Several FM-1s, one queue:** connect through an OTG hub, name each instrument and assign its own firmware. Installation runs **one instrument at a time**, with individual results. Multiple-device updates require manual pairing after restarts; automatic matching is disabled pending hardware tests.
- **Your interface:** English, Polish and Simplified Chinese, with light, dark or system appearance.

## Getting started

1. Install an APK on **Android 14 or newer**, with USB Host/OTG support. Use a data cable; for several instruments, a powered hub is recommended.
2. In **Library**, choose firmware and download it.
3. Connect your FM-1, open **Devices** and read the instrument. Confirm its memory type and choose a compatible downloaded package.
4. Add it to **Tasks**, review the plan and start installation. Repeat for additional instruments before starting the queue.

Keep power and USB connections stable throughout installation. A normal firmware update does not create a device backup and may change instrument settings.

## Firmware sources — third-party, untrusted content

- **[The Firmware Hub](https://thefirmwarehub.com):** the built-in catalog, enabled by default.
- **[Awesome FM-1](https://github.com/ekim-pl/awesome-fm1):** a community directory of firmware projects, available as an optional source.
- **Your own sources:** add supported HTTPS catalogs, GitHub repositories with firmware releases or direct `.fwsc` links in **Settings → Firmware sources**. Private sources can use authentication.

Treat every source and firmware package as **untrusted third-party content**, including built-in catalogs. Inclusion is not an endorsement or a guarantee of safety. File-integrity checks do not prove an author's identity or firmware quality; check the publisher and hardware compatibility before installing. Unsafe or incompatible firmware can erase data or leave the instrument unable to boot. Read the [third-party firmware risk notice](docs/firmware-safety.md). Firmware retains its authors' licenses; The Firmware Hub catalog is credited under CC BY 4.0.

## Backups and experimental tools

Extra tools are **hidden by default**. Enable **Settings → Experimental features** to access full-flash backup/export, flash-chip detection, MIDI logging, operation-log export and limited ROM recovery.

The experimental backup tool reads the **entire 1 MiB flash memory** of supported chips in USB ROM/download mode, compares two complete reads and stores the copy locally. You can export it as a ZIP archive. This is separate from downloading a firmware package.

**Full-backup creation does not imply full-backup restoration.** Recovery currently writes only the supported stock V15 application region, preserves the bootloader and requires recognized hardware and boot conditions. Physical recovery remains unverified; these tools are intended for experienced users.

## Privacy and more information

No app account or advertising. The library, device names and task history stay on your phone. Firebase-enabled builds collect usage statistics by default; turn them off in **Settings → Usage statistics**. External firmware services receive connection metadata. [Privacy policy](PRIVACY_POLICY.md).

[Detailed user guide](docs/user-guide.md) · [Project website](https://ekim-pl.github.io/android-fm1-flasher/) · [Polski](website/pl/documentation.md) · [简体中文](website/zh-CN/documentation.md) · [Development and tests](MIGRATION.md) · [Dependency licenses](third_party/README.md)

Questions, feedback or beta testing: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).

## Distribution builds

- **Google Play:** `.github/workflows/google-play.yml` always includes Firebase with the production project and builds a signed AAB. Manual dispatch; uploading to internal testing is off by default.
- **F-Droid:** `.github/workflows/fdroid.yml` explicitly disables Firebase and builds an unsigned release APK without Firebase or Google Play services dependencies. Manual dispatch; no store upload.

Local Google Play test APKs use the test Firebase project. F-Droid test APKs use `-Pfm1Analytics=false`. Both use the same application ID; moving between store distributions requires compatible signing certificates. The F-Droid workflow is build preparation, not confirmation of F-Droid acceptance.
