# FM‑1 Flasher

## Several instruments, one workspace

On Android 0.9.0, use **Devices** to read instruments connected through an OTG hub, name them and select a local firmware package for each one. **Tasks** shows the complete plan and its history. One approval starts sequential installation; stopping the queue lets the current operation finish. **Library** keeps catalogs, downloads and favorites together.

After a restart in a multi-device session, pair the returning instrument on its task card. Loss of another instrument blocks further writes. Automatic multi-device pairing awaits physical hardware validation; USB addresses are not hub socket numbers. Saved names require explicit pairing after a lost connection.

An independent, experimental app for browsing firmware and updating an **M‑VAVE FM‑1** over USB MIDI.

## Features

- Browse firmware from multiple catalogs, search, keep favorites and use downloaded packages offline.
- Manage catalog sources and filter the library by source.
- Select downloaded firmware in the catalog or directly in the Flasher tab.
- Verify package integrity and compatibility before installation.
- Reconnect after planned restarts automatically when one USB device and one eligible MIDI port are present.
- Optional experimental features include a receive-only MIDI logger, flash-chip detection and narrowly scoped ROM recovery; recovery has not been validated on physical hardware.
- Choose a light, dark or system theme and an English, Polish or Simplified Chinese interface.

Enable **Settings → Experimental features** to show MIDI Logger, chip detection and recovery. This persistent setting is off by default. Disabling it stops logging and clears the recovery plan; it cannot change during an operation. Recovery still needs physical hardware validation.

## Managing firmware sources

Open **Settings → Firmware sources** to choose the catalogs used by the app. **The Firmware Hub is enabled by default**; **Awesome FM-1 is disabled by default**.

- Enable or disable a source, view its status and last successful check, and see when another check is allowed.
- Use **Add catalog source** for an HTTPS catalog in the same JSON format as Firmware Hub. Give it a name, set its check interval and configure approved package and artwork URLs. **Save and check** lets you inspect a new catalog before enabling it.
- Open **Details and configuration** to edit a custom source or use **Check now**. Checks respect each source's interval, with a minimum of 24 hours between attempts, including failures. A failed source does not stop the others from updating.
- Use **Filter by source** to browse a particular catalog, including its favorites and downloaded packages. Removing a custom subscription keeps downloaded files, favorites and known withdrawals.
- **Restore default source settings** enables Firmware Hub and disables Awesome FM-1 while keeping your custom subscriptions and downloaded packages.

Disabled sources make no automatic requests, including for images. You can deliberately use **Check now** on a disabled source without enabling it. **Check catalogs in the background** is optional and off by default; it checks enabled catalogs when a network is available, respects their intervals and never downloads or installs firmware. Offline mode pauses these checks.

The local library remembers downloaded packages, favorites, selection and each package's source. Flashing uses saved files without network access. Offline mode cannot discover withdrawals published since the last successful catalog check.

## Awesome FM-1

[Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) is a companion community directory of firmware projects for the **M-VAVE FM-1**. It supplements Firmware Hub with additional catalogs linking to developers' websites, direct package downloads and project releases on GitHub. The repository stores descriptions, release information and download links; firmware files stay with their publishers.

The source is already configured in the app. To use it, go online, open **Settings → Firmware sources** and enable **Awesome FM-1**. Its entries become available after a successful check, subject to the source's refresh interval. No catalog URL needs to be entered manually.

You can help expand the directory: open an issue to report an outdated entry, or submit a pull request with a firmware project or package URL and a short description. See the contribution guide in the [Awesome FM-1 repository](https://github.com/ekim-pl/awesome-fm1#contributing).

## Sources and attribution

Catalog attribution: [The Firmware Hub](https://thefirmwarehub.com), CC BY 4.0; descriptions and images belong to their authors. Additional catalogs declare their own licenses. Firmware is downloaded from authors, not the Hub package mirror.

Networking and background work: OkHttp/Okio and AndroidX WorkManager, [Apache 2.0](../third_party/Apache-2.0.txt); see [dependency notices](../third_party/android-libraries-NOTICE.txt).

## Before updating

Use an Android device with USB Host/OTG and MIDI support. Back up instrument data, provide stable USB power and confirm the correct instrument and package.

## Privacy and licenses

No account, advertising or analytics. See the [privacy policy](../PRIVACY_POLICY.md).

Contact: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).

App: [MIT license](../LICENSE.md). Markdown parser: [CommonMark, BSD-2-Clause](../third_party/commonmark-LICENSE.txt).
