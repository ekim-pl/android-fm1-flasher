Chip detection and ROM recovery protocol adapted from https://github.com/jvitkauskas/m-flasher
revision 620c0f4c582d4864e1ae23e6fc4560c64441b138 (src/jl.rs, src/usb.rs).

mflr-loader-a.bin and mflr-loader-b.bin are bundled unmodified from its data/
directory. Built by https://github.com/jvitkauskas/m-bootloader, release v2.3.
Copyright (c) 2026 Julius Vitkauskas, MIT; see m-flasher-LICENSE.txt.

Detection uploads the loader into RAM and reads ONLINE_DEVICE (0xFC0A).
It never sends flash erase or flash program commands. Power cycle afterwards
before returning to MIDI firmware updates. Unknown IDs retain conservative
firmware compatibility rules. Hardware validation is still required.

UsbBootConnection additionally implements READ_KEY (FC09), READ_FLASH (FD05),
ERASE_SECTOR (FB01), and WRITE_FLASH (FB04). The separate limited Recovery action only writes the verified
V15 application region; the chip-detection operation remains read-only for flash.
Loader assets are SHA-256 checked before upload. Recovery policy and known stock
hashes were cross-checked against Quixotic7/MvaveFM1Unbricker (MIT):
https://github.com/Quixotic7/MvaveFM1Unbricker/blob/main/fm1_unbrick.py

Experimental device tools additionally adapt bounded table parsing, boot-area
validation/construction, sector maps and operation planning from m-flasher
revision 8fbece7348d758ac284325eed455781675ac95ff (formats.rs, boards.rs,
sectormap.rs, ops.rs). Kotlin ports retain independent bounds and write-policy
checks; this is not a claim of hardware validation.

fm1-uboot-2.4.boot is unmodified fm1-uboot.boot from m-bootloader v2.4
(2.4-395e6cc), MIT, Copyright (c) 2026 Julius Vitkauskas.
SHA-256: 677ae52a11721b1811b2688b45f6f0cfae51882810e0a4f4b409da08ce5b49e4
Verified against the release SHA256SUMS. The existing v2.3 RAM loaders remain
unchanged. MIDI read extensions and the READ_KEY reference vector follow
m-bootloader docs/SPEC.md at 3ac7651a019fa44daba061b05654278496477b64.

In FM-1 Flasher 0.10.1 the five experimental bootloader, ROM firmware and same-device
backup-restore operations are enabled through validated plans. Preparing a plan only
reads flash. Writing requires an exported pre-write backup and explicit confirmation,
then verifies each changed sector and the complete image. Factory key/MAC is protected.
Physical acceptance of newly enabled writes is still outstanding.

Thanks to Julius Vitkauskas for M-Flasher and m-bootloader, their open-source code,
loaders and protocol documentation:
https://github.com/jvitkauskas/m-flasher
https://github.com/jvitkauskas/m-bootloader
