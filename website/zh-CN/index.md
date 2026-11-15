# 多台 FM-1，一个安装计划。

**FM-1 Flasher 0.9.2** 是稳定版本，提供全屏设置、共享提供程序接口和 Android 来源的 HTTP/OAuth 认证。多台 FM-1 的 OTG 更新仍依次执行。

[当前指南](documentation.md) · [关于应用](../../about/README.zh-Hans.md) · [版本历史](https://github.com/ekim-pl/android-fm1-flasher/releases)

## 设备 · 固件库 · 任务

- **设备：**逐台读取乐器，命名并分别指定已下载固件。
- **固件库：**搜索多个来源，保存收藏，离线使用已验证的本地包。
- **任务：**批准一个计划，顺序安装并保留独立结果。在当前乐器完成后停止，不中断其写入。

## 重启后的返回

多设备需要在任务卡片中确认唯一合格的返回设备。邻居丢失、返回不明确或集线器重置会阻止后续写入。USB 地址不是物理端口；完成实机测试前，多设备自动匹配保持关闭。中断的写入不会自动恢复。

请使用数据线和稳定供电，建议独立供电的集线器。应用不能认证电源质量。未来计划推出 iOS 移植版。目前没有 iOS 版本，也未公布发布日期。

## 文档

- [安装与日常使用](documentation.md#getting-started)
- [集线器上的多台乐器](documentation.md#multiple-fm1-devices-on-a-hub)
- [构建与测试](documentation.md#building-and-testing)
- [架构与平台边界](../../MIGRATION.md)
- [版本验证报告](../../publication/qa/0.9.2.txt)
- [发布状态与工作流限制](publication.txt)
- [构建网站](pages.md)

## 隐私与开源

无需账户，无广告或分析。固件库、乐器名称、已读取序列号和任务历史保存在本地。外部目录/下载服务会收到连接元数据。

[隐私政策](privacy.md) · [MIT 许可证](license.md) · [CommonMark 许可证](commonmark-license.md)

联系：[ekim.pl@gmail.com](mailto:ekim.pl@gmail.com)。
