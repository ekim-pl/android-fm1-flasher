# Firmware at your fingertips.

FM‑1 Flasher is an independent, experimental **Android 14+** app for browsing firmware and updating **M‑VAVE FM‑1 over USB MIDI**.

[Read the guide](../README.md) · [About the app](../about/README.md) · [Download a test APK](https://github.com/ekim-pl/android-fm1-flasher/releases)

## From catalog to instrument

- **Find firmware.** Browse The Firmware Hub and optional [Awesome FM-1](https://github.com/ekim-pl/awesome-fm1) catalog, search and save favorites.
- **Prepare your library.** Download packages for offline use. The app checks integrity and compatibility before writing.
- **Choose your FM‑1.** Select a specific USB MIDI port, including on a hub with several instruments.
- **Update carefully.** Check the selected instrument and package before confirming installation.
- **Experimental tools.** Enable Settings → Experimental features for a receive-only MIDI logger, flash-chip detection and narrowly scoped ROM recovery. Physical ROM recovery has not been validated.

## Before you start

You need a phone with USB Host/OTG and MIDI support, a data cable and stable instrument power. Back up your data using the firmware's own tools.

**The app is experimental.** Power loss or an unsuitable package can require hardware recovery. Automatic reconnection after restarts and use with several devices still need further hardware testing.

## Documentation

- [Installation, firmware updates and offline use](../README.md#getting-started)
- [Multiple instruments on a USB hub](../README.md#multiple-fm1-devices-on-a-hub)
- [Manage firmware sources](../README.md#catalog-sources-and-sqlite)
- [Building and testing the app](../README.md#building-and-testing)

## Privacy and open source

No account, advertising or analytics. The library, favorites and settings are stored locally. Downloading the catalog and firmware connects to external services.

[Privacy policy](../PRIVACY_POLICY.md) · [MIT license](../LICENSE.md) · [App Markdown parser license](../third_party/commonmark-LICENSE.txt)

Contact: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).
