# 多台 FM-1，一个安装计划。

FM-1 Flasher **0.10.2** 提供本地固件库和顺序安装任务。通过 **设备 → MIDI 与备份** 可备份和恢复音色组、设置、序列及预设。实验性工具支持引导程序管理、ROM 固件安装及同设备闪存恢复。


[当前指南](documentation.md) · [关于应用](../../about/README.zh-Hans.md) · [版本历史](https://github.com/ekim-pl/android-fm1-flasher/releases)

## 开始使用

[安装与日常使用](documentation.md#getting-started)

## 设备 · 固件库 · 任务

- **设备：**逐台读取乐器，命名并分别指定已下载固件。
- **固件库：**搜索多个来源，保存收藏，离线使用已验证的本地包。
- **任务：**批准一个计划，顺序安装并保留独立结果。在当前乐器完成后停止，不中断其写入。

<!-- end-quick-start -->

## 重启后的返回

多设备需要在任务卡片中确认唯一合格的返回设备。邻居丢失、返回不明确或集线器重置会阻止后续写入。USB 地址不是物理端口；完成实机测试前，多设备自动匹配保持关闭。中断的写入不会自动恢复。

请使用数据线和稳定供电，建议独立供电的集线器。应用不能认证电源质量。未来计划推出 iOS 移植版。目前没有 iOS 版本，也未公布发布日期。

## 开发者

正在为 M-VAVE FM-1 开发固件？欢迎将项目提交到 Awesome FM-1 社区目录，也可以通过自己的来源直接向指定用户分发固件。

加入 Awesome FM-1 时，先 Fork 仓库，添加简短的项目提案，再提交拉取请求（PR）。私有分发时，将 `.fwsc` 文件作为 GitHub Release 附件发布，然后在**设置 → 固件来源**中使用自己的访问令牌添加仓库。每位接收者都需要自己的仓库访问权和令牌。

[向 Awesome FM-1 提交项目](https://github.com/ekim-pl/awesome-fm1#contributing) · [开发者指南](developers.md)

## 文档

- [集线器上的多台乐器](documentation.md#multiple-fm1-devices-on-a-hub)
- [构建与测试](documentation.md#building-and-testing)
- [架构与平台边界](../../MIGRATION.md)
- [0.10.2 发布说明及 APK](https://github.com/ekim-pl/android-fm1-flasher/releases/tag/v0.10.2)
- [发布状态与工作流限制](publication.txt)
- [构建网站](pages.md)

## 隐私与开源

无需账户且无广告。F-Droid 版本不含分析 SDK；Google Play 构建默认收集使用统计，可在“设置 → 使用统计”中关闭。固件库、乐器名称、已读取序列号和任务历史保存在本地。外部目录/下载服务会收到连接元数据。

[隐私政策](privacy.md) · [MIT 许可证](license.md) · [CommonMark 许可证](commonmark-license.md)

联系：[ekim.pl@gmail.com](mailto:ekim.pl@gmail.com)。

## 最新视频

<iframe src="https://www.youtube-nocookie.com/embed/VHEe8KECsPo" title="最新视频" loading="lazy" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen style="width: 100%; max-width: 720px; aspect-ratio: 16 / 9; border: 0;"></iframe>

## Google Play

即将上架 Google Play！我正在招募 Beta 测试者，欢迎[联系我](mailto:ekim.pl@gmail.com)参与测试。

## F-Droid

[![Get it on F-Droid](https://img.shields.io/badge/Get%20it%20on-F--Droid-1976D2?style=for-the-badge&logo=fdroid&logoColor=white)](https://ekim-pl.github.io/android-fm1-flasher/fdroid/repo/)
![Google Play — coming soon](https://img.shields.io/badge/Google%20Play-Coming%20soon-777777?style=for-the-badge&logo=googleplay&logoColor=cccccc)

将[项目仓库](https://ekim-pl.github.io/android-fm1-flasher/fdroid/repo/)添加到 F-Droid 客户端，即可安装和更新应用。APK 也可从 [GitHub 发行版](https://github.com/ekim-pl/android-fm1-flasher/releases)获取。此仓库独立于官方 F-Droid 目录；切换发行渠道需要兼容的签名证书。
