# Several FM-1 instruments. One installation plan.

**Multiplatform FM-1 Flasher 0.9.1 / 21** is an Android 14+ prerelease for several FM-1 instruments through an OTG hub. It adds persistent local diagnostics, experimental log export/clear and safe bulk firmware cleanup. The owner reported successful physical-device tests of build 20.

[Read the current guide](../README.md) · [About](../about/README.md) · [Release history](https://github.com/ekim-pl/android-fm1-flasher/releases)

## Devices · Library · Tasks

- **Devices:** read each instrument, name it and assign its own downloaded firmware.
- **Library:** search multiple sources, keep favorites and use verified local packages offline.
- **Tasks:** review one plan, install sequentially and keep individual results. Stop after the current instrument without interrupting its write.

## Returning after a restart

Multiple instruments require inline pairing of the unique returning unit. A lost neighbor, ambiguous return or hub reset blocks further writing. USB addresses are not physical socket numbers, and automatic multi-device matching is disabled pending hardware tests. Interrupted writes never resume automatically.

Use data cables and stable power; a powered hub is recommended. The app cannot certify power quality. No physical FM-1/hub or iOS binary has been validated for this build. The iOS host retains its earlier single-instrument controller without the Android queue.

## Documentation

- [Installation and daily use](../README.md#getting-started)
- [Several instruments through a hub](../README.md#multiple-fm1-devices-on-a-hub)
- [Build and tests](../README.md#building-and-testing)
- [Architecture and platform boundaries](../MIGRATION.md)
- [Test-build evidence](../publication/qa/0.9.1-test21.txt)
- [Publication status and workflow limitations](../publication/README.txt)
- [Building this website](pages.md)

## Privacy and open source

No account, advertising or analytics. The library, instrument names, observed serials and task history remain local. External catalog/download services receive connection metadata.

[Privacy policy](../PRIVACY_POLICY.md) · [MIT license](../LICENSE.md) · [CommonMark license](../third_party/commonmark-LICENSE.txt)

Contact: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).
