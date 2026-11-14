# Android 版 FM‑1 Flasher

应用源代码采用 MIT 许可证，详见 [LICENSE.md](../../LICENSE.md)。数据处理方式见[隐私政策](../../PRIVACY_POLICY.md)。

这是一个实验性的 Kotlin 应用，支持 Android 14 及以上版本。它可在 USB OTG 集线器上选择一台 M‑VAVE FM‑1，从目录下载固件，并通过 USB MIDI SysEx 安装。用户已使用较早的构建版本成功更新过实体 FM‑1；自动重新连接仍需进一步硬件测试。

## 语言与外观

在顶部栏打开**设置**，可选择波兰语、英语、简体中文或跟随系统。Android 的系统应用语言设置也提供相同选项。APK 和 AAB 均包含这三种语言，无需联网即可切换。警告、错误、日志和通知也已翻译；固件作者提供的说明保留原始语言。

可选择浅色、深色或系统主题。设置中还提供离线隐私政策。

横屏且宽度至少为 600 dp 时，导航移至左侧栏，目录筛选条件与结果并排显示。刷写页面并排显示设备与本地固件包；固件详情使用较小的预览图和单独的操作列。旋转屏幕后会保留目录中的可见位置和已打开的固件详情。

## 关于应用

**关于应用**页面使用 [about](../../about) 中简短、独立的 Markdown 文件：[波兰语](../../about/README.pl.md)、[英语](../../about/README.md)和[简体中文](../../about/README.zh-Hans.md)。构建时会自动将它们打包，离线也可阅读。内容随应用语言和主题切换。本 README 是项目技术文档，不会打包进应用。

应用简介提供许可证和隐私政策链接。互联网链接由相应的系统应用打开。

## 项目网站与在线文档

公开源代码：[GitHub](https://github.com/ekim-pl/android-fm1-flasher)。[项目网站](https://ekim-pl.github.io/android-fm1-flasher/zh-CN/)提供完整的英语、波兰语和简体中文网站与文档。语言链接会切换到当前页面的对应译本。

[GitHub Pages 工作流](../../.github/workflows/pages.yml)构建网站、技术指南、应用简介、发布指南、许可证和隐私政策。拉取请求和分支会检查构建；默认分支的更改会自动部署。在新仓库中选择 **Settings → Pages → Source → GitHub Actions**。详见[设置、本地构建与编辑指南](pages.md)。

仓库文档以英语维护。网站译本位于 `website/pl/` 和 `website/zh-CN/`；应用与商店材料的译本保留在原有语言目录中。

## 发布材料

[发布指南](publication.txt)介绍材料包及后续步骤。[fastlane/metadata/android](../../fastlane/metadata/android) 包含三种语言的说明、更新日志、图标、宣传图和截图，格式适用于 Google Play 和 F-Droid。[静态隐私政策页面](../../publication/privacy/index.html)可托管在公开的 HTTPS 网站上。原始截图和商店表单材料位于 [publication](../../publication)。

<a id="getting-started"></a>

## 开始使用

从 [GitHub Releases](https://github.com/ekim-pl/android-fm1-flasher/releases) 下载测试 APK。它使用调试密钥签名，仅用于测试，不是商店发行版。本地工作文件为 `dist/FM1-Flasher-debug.apk` 和 `dist/FM1-Flasher-publication.zip`；`dist/` 不属于仓库内容。

1. 在支持 USB Host/OTG 和 MIDI 的手机上安装 APK。使用数据线连接带电源的集线器和设备。
2. 在**刷写**页面选择端口，点击**连接所选端口**。获得 Android USB 权限后，应用仅读取该设备的标识。MIDI 名称不是选择依据：USB 地址和 MIDI ID 用于区分同名设备。
3. 除非知道该设备实际的 JEDEC ID，否则将闪存芯片类型保留为未知。选择仅适用于当前连接，更换设备后会重置。
4. 在目录中打开固件详情，选择**下载到设备**。下载后选择**用于刷写**，或直接在刷写页面的列表中选择固件包。两处使用同一个已保存的选择。下载和写入是独立操作。详情中可打开作者仓库。
5. 使用相应固件自身的工具备份数据，在确认对话框中检查目标端口和固件包，然后开始更新。
6. 每次重启后，如果 Android 只检测到一台 USB 设备和一个新的符合条件的 MIDI 端口，就会自动检测并连接新端口。需要时授予 Android USB 权限。若有多台设备或多个端口，请选择并确认**同一台 FM‑1 的新端口**。最后一次重启后，应用会检查已安装固件的标识。

<a id="multiple-fm1-devices-on-a-hub"></a>

## 集线器上的多台 FM‑1

- 默认不选择任何端口。请求和数据只发送到用户明确选择的设备。同一时间只进行一次更新。
- 重启后，旧 MIDI ID 必须消失。重新连接列表会排除先前的 MIDI ID，以及本阶段开始前已存在的其他设备的 USB 地址。
- 仅在旧 MIDI ID 消失、恰有一台 USB 设备和一个符合条件的 MIDI 端口时自动重新连接。多台设备（包括非 MIDI 设备）或多个端口都需要用户选择。MIDI 名称不能识别集线器上的物理插口。
- 若多台设备同时改变地址，或用户接入了其他设备，可能出现多个候选项。请勿猜测。应用不会自动切换到另一台可用的 FM‑1。
- USB 地址标识当前连接，并非设备的永久标识。更新前可依次单独连接设备，确认各台设备的身份。写入过程中不要断开连接。

## 固件库、收藏与离线使用

- **目录、收藏、下载和刷写**是独立页面。卡片显示预览、简短说明、作者、类别、版本及本地固件包状态。若作者提供图片，应用会显示并缓存以供离线使用；否则使用类别插图。完整说明和技术信息位于详情页。
- 类别包括合成器、Groovebox、鼓机与采样器、音序追踪器、和弦、工具、M‑VAVE 原厂及其他固件。搜索可与类别、**仅已下载**和**收藏**组合使用，目录中也可直接筛选。
- 可按名称 A–Z、最新发布、作者 A–Z 或最近下载排序。收藏和浏览偏好保存在本地，应用重启后仍保留。
- 固件保存在持久的应用私有存储中，而非系统 Downloads 文件夹。关闭或更新应用不会删除固件包；卸载应用或清除其数据会移除固件库。
- 下载后可打开**离线**模式，使用**下载**页面或**仅已下载**筛选。选择本地固件包并开始刷写不会发起网络请求。目录过期不会阻止使用已验证的本地固件包。写入设备前，应用会再次检查磁盘文件的 SHA‑256 和 CRC。
- 目录会持久缓存，启动时立即显示。每次打开或返回应用时都会检查网络；未启用离线模式时，会自动刷新过期或缺失的目录。网络中断或下载失败时保留原有列表。
- **在列表最顶部下拉**，出现提示后松手，可手动检查目录。该手势不会接管列表中部滚动或横向滚动，也可使用**检查**按钮。两种方式都遵守来源每天一次的限制：缓存仍有效时不会再次发送 HTTP 请求。
- **更新**标签表示文件已更改。可在详情中更新单个固件包，也可在**下载**页面更新全部。即使作者未更改版本名称，也会根据 SHA‑256 检测文件变化。
- 只有下载和验证全部完成后，新包才会替换旧包。更新失败会保留旧包。更新固件库不会开始刷写；若所选文件发生变化，需要重新选择固件包。**重新下载**可修复本地文件。删除本地包不会移除收藏，也不会改变设备固件。
- 若最近一次已知目录移除了某项固件，应用会禁止写入该包。较早的已保存版本使用其自身文件的兼容性信息，而非新版本的信息。离线时无法得知上次下载目录之后的变化或撤回。

## 协议与兼容性

- 目录数据来自 [The Firmware Hub](https://thefirmwarehub.com)，参见[目录使用条款](https://thefirmwarehub.com/kb/)。每天最多下载一次，使用带标识的 User-Agent 和本地缓存。新下载需要有效目录；已验证的本地包也可离线使用。
- 自动下载要求明确的 GitHub 资源 ID 和发布标签；固定的 M‑VAVE 官方包除外，它来自厂商仓库。其他条目提供作者链接。应用不从 Hub 镜像下载固件包。
- 使用前会检查目录中的 SHA‑256、`FM-1_<number>` 标识、AC791N 处理器、UFW 头及文件列表 CRC、文件范围，以及 `flash.bin` 和 `ota.bin` 的 CRC。厂商的辅助加密文件记录的是明文 CRC，因此由完整包的 SHA‑256 保护，不对密文字节验证其 CRC。
- 对未知或新闪存芯片，仅允许目录标记为 `chip: new` 的包。`chip: old` 要求明确选择旧芯片。不明兼容性会阻止写入。固定的原厂 V15 只允许旧芯片选项，因为目录未声明支持新芯片。目录标记不能替代对固件本身的测试。
- 两阶段更新会检查帧校验和、SysEx 分片、读取范围和超时。成功必须收到更新程序最终信号的确认，并在重启后获得匹配的标识。
- 前台服务和唤醒锁使切换应用时仍能保持会话；系统仍可能终止进程。应用没有停止写入按钮。

标识检查成功只确认设备的回应，不等于读回并校验整个闪存。不同固件可能共用标识。断电、固件包不适用或重启后选错设备，都可能需要硬件恢复。自动重新连接仅用于更新中的计划内重启，不适用于写入期间任意的连接中断。

常规 MIDI 更新不支持蓝牙更新、批量刷写、预设或内存备份、改写引导程序、生成硬件 USB_KEY、固定映射集线器插口、导入未经验证的本地文件，以及使用其他标识（如 `FM1_OFFICIAL_OPEN`）的固件包。芯片检测和 ROM 恢复是下文介绍的独立实验性调试版本功能，不会扩大常规更新的兼容范围。

<a id="building-and-testing"></a>

## 构建与测试

需要 JDK 17、Android SDK Platform 36 和 Build Tools 35.0.0。设置 `ANDROID_HOME`，或在不跟踪的 `local.properties` 中设置 `sdk.dir`。

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。
AAB：运行 `./gradlew :app:bundleRelease`，输出为 `app/build/outputs/bundle/release/app-release.aab`。未配置密钥时不会签名；GitHub Actions 使用 secrets 中的上传密钥签名。

84 个 JVM 测试覆盖协议和 UFW 包、目录、类别、组合筛选、排序、固件库与偏好持久化、SHA‑256 更新、刷新手势及取消、自动刷新条件、失败时保留旧包、文件损坏、旧版本兼容性、撤回的包、相邻端口排除、三设备会话、MIDI 记录和 ROM 恢复模拟。两个可选测试从磁盘读取真实文件，不将固件放入仓库：

```sh
FM1_TEST_PACKAGES=/path/stock-v15.fwsc:/path/stock-v16.fwsc \
FM1_TEST_CATALOG=/path/catalog.json \
./gradlew :app:testDebugUnitTest
```

最近一次发行验证中，84 个 JVM 测试全部通过，使用了真实 V15/V16 固件包，并在两种芯片上模拟恢复；lint 和调试 APK 构建也通过。实体 USB 恢复和多端口 MIDI 接收仍需硬件测试。Android 16 模拟器用于检查界面和网络，不验证实体 USB 传输。

模拟器检查涵盖启动应用、读取 202 个目录条目、下载并持久保存 Hortator 1.0.1（含 SHA‑256/CRC 验证）、收藏、将**仅已下载**及**收藏**与类别组合、排序、离线固件库，以及实际的下拉刷新手势。没有 USB 目标设备时，安装按钮保持禁用。

用户确认曾成功更新实体 FM‑1。自动重启及集线器上的多设备仍需验证：先检测多台设备并选择其中一台，再进行受控更新，确认两次重启，并检查其他设备的固件未被改变。

## GitHub Actions 与 Google Play

[设置指南](github-actions.txt)介绍首次上传、签名密钥、Google 服务账号和 GitHub secrets。

仓库设置中的 Google Play 自动发布已禁用，直到商店和密钥配置完成。以下发布工作流行为仅适用于重新启用之后。

- **Android CI**：分支推送和拉取请求会运行 JVM 测试、lint 和调试 APK 构建。APK 及报告作为构建产物提供。
- **Google Play release**：推送 `v<versionName>` 标签会运行测试、发行版 lint、已签名 AAB 构建，并连同更新日志上传到 `internal`。手动运行时关闭 `publish`，只构建用于首次 Play Console 上传的 AAB。
- **Google Play production**：手动指定 `version_code`，将已测试的包从 `internal` 提升到 `production`，无需重新构建或上传 AAB。

每次新发布前，提高 `versionCode`，在 `app/build.gradle` 设置 `versionName`，并添加 `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`。标签必须匹配 `versionName`。GitHub 不能替代首次商店设置、必要测试和 Google Play 验证。

## 技术来源与许可证

协议：[AL‑255/FM‑1‑RE](https://github.com/AL-255/FM-1-RE/blob/main/docs/io/11-ota-protocol.md) 及其 `tools/fm1_ota.py` 客户端（WTFPL）、[固件包格式](https://sbegueria.github.io/fm1-dev-wiki/Firmware-Package-Format.html)、[Android MIDI API](https://developer.android.com/reference/android/media/midi/MidiManager)。Android 实现位于 `app/src/main/java/pl/ekim/fm1_flasher`。

目录汇编：The Firmware Hub，[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)。应用仅保留支持的标识、选取需要的字段，并添加固定的原厂条目。说明归各作者所有；每项都会显示作者和仓库链接。固件许可证由各项目保留，APK 和应用源代码均不附带固件。Gradle Wrapper：[Apache 2.0](https://github.com/gradle/gradle/blob/v8.13.0/LICENSE)。

Markdown 渲染：[CommonMark Java](https://github.com/commonmark/commonmark-java)，采用 [BSD-2-Clause 许可证](../../third_party/commonmark-LICENSE.txt)，许可证也随应用提供。

### 闪存芯片检测

连接恰好一台处于 JieLi USB download 模式（`4C4A:8057`）的设备，然后使用**检测芯片（download 模式）**。应用将 loader 上传到 RAM，并通过命令 `FC0A` 读取 JEDEC ID；检测过程中不会擦除或写入闪存。`856014` 是 FM‑1 旧芯片，`B36014` 是新芯片，其他 ID 保持未知。

读取后请断电重启设备，进入普通 MIDI 模式，在应用中连接并选择检测到的芯片类型。该结果不会根据固件版本推断，也不会转移到其他 USB 连接。此 USB 流程仍需在实体设备上验证。

## 实验性多端口 MIDI 记录器

调试版本恢复原名称 **FM-1 Flasher** 和包名 `pl.ekim.fm1_flasher`（版本 `0.6.4-experimental`），需要 Android 14 及以上版本。独立的 `.experimental` 应用的数据不会迁移。在 **MIDI Logger** 中选择一个或多个乐器输出端口，授予各设备的 USB 权限，然后点击 **Start**。记录器只接收数据：不会识别设备、打开 MIDI 输入端口或发送 MIDI。支持同一设备上的多个端口及多个 USB 设备，包括没有 MIDI 输入的设备。

日志包含接收时间、设备/路径/端口、通道、解码后的 MIDI 和 HEX。长按条目可查看完整 HEX。筛选器可按来源、通道和类型过滤，也可隐藏 Clock/Active Sensing。导出通过 Android 文件选择器保存当前完整缓冲区的 UTF‑8 文本，不受筛选条件影响。缓冲区最多保存 5000 条和 4 MiB 原始消息数据；单条 SysEx 上限为 64 KiB。被清除的条目、输入丢失和截断的 SysEx 会标记出来，但记录器无法检测 Android 回调之前发生的数据丢失。应用进入后台时记录停止，包括打开导出选择器时。实体设备上的多端口接收和 30 分钟负载测试仍待验证。

## 实验性 ROM 恢复

此功能仅在 `0.6.4-experimental` 调试版本中提供，尚未在实体设备上验证 USB 恢复。打开 **Flasher → Recovery and backups**。可请求响应中的 FM‑1 进入 ROM download 模式；若设备已在该模式，则不需要 MIDI 连接。连接恰好一台 download 设备（`4C4A:8057`，`WL82 UBOOT1.00`）并授予 USB 权限。download 模式不是文件系统，切勿格式化。

- 诊断会检查 JEDEC ID 和芯片密钥，将完整 1 MiB 闪存读取两次并比较，然后把备份和元数据同步到存储。芯片 ID `856014` 和 `B36014` 均需密钥 `980F`。未知引导区仍可备份，但会阻止恢复写入。
- 准备恢复仅接受官方 `FM-1_015` 包，SHA‑256 为 `db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a`，并单独校验解出的固件区域。必须识别到原厂 V15/V16 引导区；m-bootloader 和损坏的引导区会被拒绝。
- 写入前会显示设备、芯片、变更扇区数、受保护范围及备份校验和，并再次将整片闪存与备份比较。仅擦写 `[0x4000, 0x93000)` 内变更的 4 KiB 扇区；逐扇区回读，最后比较整片闪存。传输失败会停止操作，不会盲目重试或自动续传。
- 可通过 Android 文件选择器将备份导出为 ZIP，其中包含完整闪存、元数据/SHA‑256 和可用的操作结果。卸载应用或清除数据会删除内部备份，请提前导出。

字节校验成功不代表设备已能启动。操作后请断电重启并检查 MIDI 连接。自定义固件或 V16 的设置/预设可能不适用于 V15。此功能不修复引导程序，也不支持任意备份恢复或 V16 恢复。USB 无法识别的设备需要外部 USB_KEY 硬件，或另一台运行 [M-Unbricker](https://github.com/jvitkauskas/m-unbricker) 的设备。
