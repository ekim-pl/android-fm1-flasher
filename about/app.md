# FM-1 Flasher 0.10.1

An independent firmware manager for **M-VAVE FM-1**. Requires Android 14 or newer, USB Host/OTG, MIDI, a data cable and stable power. A powered hub is recommended.

## Install firmware

1. Download a compatible package in [Library](app://library). Check its source and compatibility with the instrument's memory type.
2. Connect and read each instrument in [Devices](app://devices). Identify it physically, select its memory type and package, then add it to the plan.
3. Review names, versions and hashes in [Tasks](app://tasks), then start the plan. Firmware is written to one instrument at a time.

Keep power and USB connected until verification finishes. Firmware may change settings; an update does not automatically back up your instrument. **Stop after this instrument** finishes the active operation and leaves remaining tasks queued. After an interruption, an active task is unconfirmed and does not resume automatically.

## Multiple instruments

In **Devices → Details → Instrument color**, choose one of six colors, including for saved disconnected instruments. The accent identifies devices in lists, tasks and progress bars. Choosing a color remembers the device; restart and manual pairing preserve it. Queued tasks keep their original color in history. Editing is disabled while busy or while the device has a queued/running task. Colors do not prove hardware identity.

After a planned restart, pair the returning instrument on its task card. Other connections must remain continuous. Ambiguous devices or a lost connection stop further writing. Names and USB addresses do not prove physical identity. A successful result checks the returned firmware identity, not a complete flash-memory readback.

## Sources and tools

Firmware Hub is enabled by default; other sources can be configured in [Settings → Firmware sources](app://settings/sources). Offline mode uses saved packages and cannot discover new withdrawals. Hashes alone do not establish publisher authenticity. Optional source credentials are stored encrypted on the device.

Enable **Settings → Experimental features** for flash diagnostics, a sector map, full backups, backup import and MIDI logging. ROM operations require exactly one supported FM-1 in download mode. MBOOT diagnostics and backups require a selected MIDI device with a supported bootloader.

The five write operations install m-bootloader 2.4, restore the stock V15 bootloader, install compatible downloaded firmware over ROM, or restore a same-device backup with or without its bootloader. Choose the operation, **Prepare write plan**, **Export the pre-write backup**, then **Write reviewed plan** and confirm. Preparation alone never writes. Factory key/MAC data stay protected; a restored backup must match the device identity. The app verifies sectors and the complete flash, but does not verify boot. Newly enabled writes still need physical acceptance testing; a failed bootloader write may require hardware recovery.

In **Settings → Firmware sources → Source color**, choose one of eight colors or Automatic for each source. The dot beside the source name marks its firmware in the library, downloaded packages and package details. Colors are stored on this device and can be changed offline.

## Privacy and licenses

The app requires no account and contains no advertising. The F-Droid distribution contains no analytics SDK. Google Play builds with Google Analytics for Firebase collect usage statistics by default from the first online launch. You can turn statistics off at any time in Settings → Usage statistics. Library data, device names and task history are stored locally. Catalog and download servers receive connection metadata. See the [privacy policy](../PRIVACY_POLICY.md) and [MIT license](../LICENSE.md); firmware and third-party components retain their own licenses.

Optional analytics SDK terms: [Firebase notices](../third_party/firebase-analytics-NOTICE.md).

Publisher: **Ekim** · [Contact](mailto:ekim.pl@gmail.com) · [Third-party licenses](../third_party/README.md). Free and open source.

[CommonMark (BSD-2-Clause)](../third_party/commonmark-LICENSE.txt) · [Android (Apache 2.0)](../third_party/Apache-2.0.txt) · [m-flasher (MIT)](../third_party/m-flasher-LICENSE.txt)

## Acknowledgements

Thank you to **Julius Vitkauskas** for [M-Flasher](https://github.com/jvitkauskas/m-flasher) and [m-bootloader](https://github.com/jvitkauskas/m-bootloader). Their open-source code, protocol documentation and loaders made these flash diagnostics, bootloader and recovery tools possible. Adapted code and bundled binaries retain their MIT attribution; see the [component notice](../third_party/m-flasher-NOTICE.md) and [license](../third_party/m-flasher-LICENSE.txt).
