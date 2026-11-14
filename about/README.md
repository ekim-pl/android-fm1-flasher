# FM‑1 Flasher

Enable **Settings → Experimental features** to show MIDI Logger, chip detection and recovery. This persistent setting is off by default. Disabling it stops logging and clears the recovery plan; it cannot change during an operation. Recovery still needs physical hardware validation.

An independent, experimental app for browsing firmware and updating an **M‑VAVE FM‑1** over USB MIDI.

## Features

- Search the catalog, keep favorites and use downloaded packages offline.
- Select downloaded firmware in the catalog or directly in the Flasher tab.
- Verify package integrity and compatibility before installation.
- Reconnect after planned restarts automatically when one USB device and one eligible MIDI port are present.
- The experimental debug build also includes a receive-only MIDI logger, flash-chip detection and narrowly scoped ROM recovery; recovery has not been validated on physical hardware.
- Choose a light, dark or system theme and an English, Polish or Simplified Chinese interface.

## Firmware sources

**Settings → Firmware sources** manages independent catalog subscriptions. The Firmware Hub is enabled by default; [Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) is disabled by default. Add an HTTPS catalog, check its contents and configure approved package/artwork URLs. Disabled sources make no automatic requests, including for images. Check now also works as a deliberate one-time check of a disabled source and respects the 24-hour minimum.

The SQLite library keeps downloaded packages, favorites, selection and source provenance. Removing a subscription retains files and known withdrawals. Each source has its own freshness, errors and download policy; one failed source does not prevent another from updating. The source filter applies to browsing. Background catalog checks are optional and off by default; they never download or install firmware.

These catalogs have no verified publisher signature. SHA-256 and package validation remain mandatory; unknown compatibility, including the initial SHRUB entry, blocks flashing. The same product ID does not identify the same project. Offline flashing uses local files without networking and cannot discover withdrawals published since the last successful check.

Catalog attribution: [The Firmware Hub](https://thefirmwarehub.com), CC BY 4.0; descriptions and images belong to their authors. Additional catalogs declare their own licenses. Firmware is downloaded from authors, not the Hub package mirror.

Networking and background work: OkHttp/Okio and AndroidX WorkManager, [Apache 2.0](../third_party/Apache-2.0.txt); see [dependency notices](../third_party/android-libraries-NOTICE.txt).

## Before updating

Use an Android device with USB Host/OTG and MIDI support. Back up instrument data, provide stable USB power and confirm the correct instrument and package.

## Privacy and licenses

No account, advertising or analytics. See the [privacy policy](../PRIVACY_POLICY.md).

Contact: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).

App: [MIT license](../LICENSE.md). Markdown parser: [CommonMark, BSD-2-Clause](../third_party/commonmark-LICENSE.txt).
