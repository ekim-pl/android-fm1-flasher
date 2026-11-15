# Privacy Policy — FM‑1 Flasher

## Publisher and contact

ekim.pl@gmail.com. Effective date: November 15, 2026. This policy describes how FM-1 Flasher processes data.

## Local information

The app requires no account and includes no advertising, analytics or tracking SDKs. Favorites, browsing preferences, theme and language choices, cached catalogs, preview images, firmware packages and download metadata are stored in private app storage. A bounded operation log (up to 256 KiB) is saved in private app storage and survives restarts. It records timestamps, operations, USB/MIDI port events, transfer stages and error messages; it does not record raw MIDI traffic or firmware contents. URL query strings and fragments are removed from logged messages. In experimental settings you can export it as JSON Lines or clear it. Logs are never uploaded automatically. Exported files remain at the destination you choose and may contain device names, USB paths and technical error details; review them before sharing. Instrument names, observed USB serials, memory-type selections, last known firmware identity and task history (targets, package hashes, stages and results) are stored locally. Serial numbers are not included in the ordinary connection log. The most recently removed valid package can be restored while the app session remains open. These data are not uploaded to the app publisher.

## Network connections

When online, the app checks enabled catalog sources and may fetch their permitted preview images. The Firmware Hub is enabled by default; awesome-fm1 on raw.githubusercontent.com and custom sources require opt-in. Disabled sources make no automatic requests; Check now is an explicit one-time request even for a disabled source. Firmware downloads contact GitHub release services or approved author URLs only after user action. Each catalog is requested at most once every 24 hours, including failed attempts. Optional background catalog checks use WorkManager and are off by default; offline mode pauses them. All requests use HTTPS. No firmware is installed automatically.

## Information visible to external services

Operators of those servers receive the requested URL, the connection IP address and standard HTTPS connection metadata. The app sends a generic technical User-Agent that does not identify an individual user or device. The app does not attach favorites, settings, instrument memory contents, MIDI identity or an account identifier to these requests. Server operators handle technical logs under their own privacy policies; the app publisher does not control their retention or processing.

## USB MIDI

The app reads the identity of the FM-1 explicitly selected by the user and transfers a selected firmware package over USB MIDI after the user confirms an update. USB device details and MIDI responses are used to manage that local session and are not sent to network services or the app publisher.

## Storage, deletion and offline use

Local data remain on your device until removed in the app, cleared in Android app settings, or deleted by uninstalling the app. Removing a package leaves favorites intact. Catalogs, source policies and local-package metadata use SQLite. Removing a subscription retains provenance, known withdrawals, packages and favorites. Clearing app storage or uninstalling removes the library and preferences. Android app data backup is disabled. There is no server account or publisher-held user profile to delete. In offline mode, the app does not refresh the catalog or download packages or preview images; previously saved content remains available.

## Changes and questions

Updated policies will be included with relevant app updates and in the project repository. For privacy questions, contact ekim.pl@gmail.com.
