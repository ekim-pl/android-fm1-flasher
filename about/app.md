# FM-1 Flasher

An independent firmware manager for **M-VAVE FM-1**. Requires Android 14 or newer, USB Host/OTG, MIDI, a data cable and stable power. A powered hub is recommended.

## Install firmware

1. Download a compatible package in [Library](app://library). Check its source and compatibility with the instrument's memory type.
2. Connect and read each instrument in [Devices](app://devices). Identify it physically, select its memory type and package, then add it to the plan.
3. Review names, versions and hashes in [Tasks](app://tasks), then start the plan. Firmware is written to one instrument at a time.

Keep power and USB connected until verification finishes. Firmware may change settings; an update does not automatically back up your instrument. **Stop after this instrument** finishes the active operation and leaves remaining tasks queued. After an interruption, an active task is unconfirmed and does not resume automatically.

## Multiple instruments

After a planned restart, pair the returning instrument on its task card. Other connections must remain continuous. Ambiguous devices or a lost connection stop further writing. Names and USB addresses do not prove physical identity. A successful result checks the returned firmware identity, not a complete flash-memory readback.

## Sources and tools

Firmware Hub is enabled by default; other sources can be configured in [Settings → Firmware sources](app://settings/sources). Offline mode uses saved packages and cannot discover new withdrawals. Hashes alone do not establish publisher authenticity. Optional source credentials are stored encrypted on the device.

Experimental features include a MIDI logger and separate recovery tools. Physical ROM recovery remains unverified; it is not an automatic fallback for an unsuccessful update.

## Privacy and licenses

The app has no advertising or analytics. Library data, device names and task history are stored locally. Catalog and download servers receive connection metadata. See the [privacy policy](../PRIVACY_POLICY.md) and [MIT license](../LICENSE.md); firmware and third-party components retain their own licenses.
