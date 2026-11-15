# Several FM-1 instruments. One installation plan.

FM-1 Flasher **0.10.0** is being prepared for Google Play. It includes a local firmware library, source colors, full-screen settings and sequential installation tasks. Store publication is pending.


[Read the current guide](../docs/user-guide.md) · [About](../about/README.md) · [Release history](https://github.com/ekim-pl/android-fm1-flasher/releases)

## Devices · Library · Tasks

- **Devices:** read each instrument, name it and assign its own downloaded firmware.
- **Library:** search multiple sources, keep favorites and use verified local packages offline.
- **Tasks:** review one plan, install sequentially and keep individual results. Stop after the current instrument without interrupting its write.

## Returning after a restart

Multiple instruments require inline pairing of the unique returning unit. A lost neighbor, ambiguous return or hub reset blocks further writing. USB addresses are not physical socket numbers, and automatic multi-device matching is disabled pending hardware tests. Interrupted writes never resume automatically.

Use data cables and stable power; a powered hub is recommended. The app cannot certify power quality. A port to iOS is planned for the future. There is no iOS release or announced release date.

## For developers

Building firmware for the M-VAVE FM-1? Submit your project to the Awesome FM-1 community catalog or share your own source directly with selected users.

To join Awesome FM-1, fork the repository, add a short project proposal and open a pull request. For private distribution, publish `.fwsc` files as GitHub Release assets and add the repository in **Settings → Firmware sources** using your own access token. Each recipient needs their own repository access and token.

[Submit a project to Awesome FM-1](https://github.com/ekim-pl/awesome-fm1#contributing) · [Developer guide](developers.md)

## Documentation

- [Installation and daily use](../docs/user-guide.md#getting-started)
- [Several instruments through a hub](../docs/user-guide.md#multiple-fm1-devices-on-a-hub)
- [Build and tests](../docs/user-guide.md#building-and-testing)
- [Architecture and platform boundaries](../MIGRATION.md)
- [Release verification](../publication/qa/0.10.0.txt)
- [Publication status and workflow limitations](../publication/README.txt)
- [Building this website](pages.md)

## Privacy and open source

No account or advertising. The F-Droid distribution has no analytics SDK; Google Play builds collect usage statistics by default; you can turn them off in Settings → Usage statistics. The library, instrument names, observed serials and task history remain local. External catalog/download services receive connection metadata.

[Privacy policy](../PRIVACY_POLICY.md) · [MIT license](../LICENSE.md) · [CommonMark license](../third_party/commonmark-LICENSE.txt)

Contact: [ekim.pl@gmail.com](mailto:ekim.pl@gmail.com).

## Google Play

Coming soon to Google Play! I'm looking for beta testers — [get in touch](mailto:ekim.pl@gmail.com) if you'd like to join.

## F-Droid

A dedicated build and an F-Droid repository are planned.
