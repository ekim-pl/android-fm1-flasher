# Several FM-1 instruments. One installation plan.

**FM-1 Flasher 0.9.2** is the stable release with full-screen settings, shared provider interfaces and HTTP/OAuth authentication for Android sources. The existing multi-instrument OTG workflow remains sequential.

[Read the current guide](../README.md) · [About](../about/README.md) · [Release history](https://github.com/ekim-pl/android-fm1-flasher/releases)

## Devices · Library · Tasks

- **Devices:** read each instrument, name it and assign its own downloaded firmware.
- **Library:** search multiple sources, keep favorites and use verified local packages offline.
- **Tasks:** review one plan, install sequentially and keep individual results. Stop after the current instrument without interrupting its write.

## Returning after a restart

Multiple instruments require inline pairing of the unique returning unit. A lost neighbor, ambiguous return or hub reset blocks further writing. USB addresses are not physical socket numbers, and automatic multi-device matching is disabled pending hardware tests. Interrupted writes never resume automatically.

Use data cables and stable power; a powered hub is recommended. The app cannot certify power quality. A port to iOS is planned for the future. There is no iOS release or announced release date.

## Documentation

- [Installation and daily use](../README.md#getting-started)
- [Several instruments through a hub](../README.md#multiple-fm1-devices-on-a-hub)
- [Build and tests](../README.md#building-and-testing)
- [Architecture and platform boundaries](../MIGRATION.md)
- [Release verification](../publication/qa/0.9.2.txt)
- [Publication status and workflow limitations](../publication/README.txt)
- [Building this website](pages.md)

## Privacy and open source

No account or advertising. Standard builds have no analytics SDK; optional Firebase builds collect usage statistics only after consent. The library, instrument names, observed serials and task history remain local. External catalog/download services receive connection metadata.

[Privacy policy](../PRIVACY_POLICY.md) · [MIT license](../LICENSE.md) · [CommonMark license](../third_party/commonmark-LICENSE.txt)

Contact: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).
