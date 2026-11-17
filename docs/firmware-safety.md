# Third-party firmware: risks and trust

Firmware offered through The Firmware Hub, Awesome FM-1, custom catalogs, GitHub releases or direct links is external content. Treat every package as **untrusted third-party firmware**, even when its source is included or enabled by default in FM-1 Flasher. Catalog inclusion is not a security review, endorsement or guarantee of compatibility.

## What can go wrong

Incorrect, incompatible, defective or malicious firmware can change or erase instrument settings and stored data, cause unexpected behavior or leave the FM-1 unable to boot. Recovery may require separate tools or hardware access and is not guaranteed by this app.

The app checks package integrity, product information and available compatibility declarations before installation. These checks cannot establish that the firmware is safe or that its publisher is authentic. A matching SHA-256 means the file matches the expected bytes; a source can publish a hash for unsafe firmware too.

## Before installing

- Check the original author's release notes, project history and known issues. Do not rely on a catalog listing alone.
- Confirm support for your FM-1's flash-memory variant. A version number or filename alone does not prove compatibility. Forcing unknown compatibility accepts additional risk; it does not make a package compatible.
- Back up important instrument data using tools supported by that firmware. Downloading an installation package is not a backup of your FM-1, and a normal update does not create one automatically.
- Keep power and USB connections stable throughout the update.

The experimental tools save and export full-flash backups and can restore a verified backup to the same instrument. UID, chip identity and factory data must match; arbitrary cross-device restoration is not supported. See [backup and recovery limitations](user-guide.md#full-flash-backups).

## Offline copies

A local copy remains third-party content. Offline mode cannot discover newly reported defects or firmware withdrawals. Previously known withdrawal and conflict checks still apply, but keeping a file locally does not certify its safety.

[User guide](user-guide.md) · [Project README](../README.md)

### Experimental flash writes in 0.10.1

Bootloader installation/restoration, compatible ROM firmware installation and
same-device backup restoration are enabled on the supported Puya/UCUN chips.
Prepare a plan, export its pre-write backup, then explicitly confirm writing.
Preparation reads and saves a verified backup without erasing or programming.
Device changes invalidate the plan; unknown hardware or invalid source layouts
cannot authorize writes. Factory key/MAC bytes remain protected. Each written sector
and the entire result are read back. A verified write does not prove successful boot.
Newly enabled operations still require physical acceptance testing. Bootloader write
failure may require hardware recovery; a backup does not guarantee recoverability.
Read-only MBOOT commands require a validated capability handshake. Imported hashes
establish integrity against supplied metadata, not publisher authenticity.
