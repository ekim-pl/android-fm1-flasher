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
ERASE_SECTOR (FB01), and WRITE_FLASH (FB04). Recovery only writes the verified
V15 application region; the chip-detection operation remains read-only for flash.
Loader assets are SHA-256 checked before upload. Recovery policy and known stock
hashes were cross-checked against Quixotic7/MvaveFM1Unbricker (MIT):
https://github.com/Quixotic7/MvaveFM1Unbricker/blob/main/fm1_unbrick.py
